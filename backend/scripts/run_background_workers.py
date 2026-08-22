#!/usr/bin/env python3
"""
Background worker supervisor for Promise.

Starts both long-running background workers as child processes:

  A. Outbox Publisher   — publishes OutboxEvents to Kafka
  B. Notification Dispatcher — dispatches due FCM push notifications

Signal Handling:
  SIGTERM / SIGINT → forward to both children → wait for clean exit → exit.

Failure Handling:
  If either child exits unexpectedly (non-zero or without being asked to stop),
  the supervisor sends SIGTERM to the remaining child and exits with a
  non-zero exit code so Railway can restart the service.

Usage:
  python scripts/run_background_workers.py

Railway start command (backend Root Directory):
  python scripts/run_background_workers.py
"""

import logging
import os
import signal
import subprocess
import sys
import time

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("background_worker")

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

MANAGE_PY = os.path.join(os.path.dirname(__file__), "..", "manage.py")

WORKERS = [
    {
        "name": "outbox",
        "cmd": [sys.executable, MANAGE_PY, "publish_outbox", "--continuous"],
    },
    {
        "name": "notification",
        "cmd": [sys.executable, MANAGE_PY, "run_notification_dispatcher", "--continuous"],
    },
]

# How long (seconds) to wait for a child to exit cleanly after SIGTERM before force-killing it
SHUTDOWN_TIMEOUT = 30


# ---------------------------------------------------------------------------
# Supervisor
# ---------------------------------------------------------------------------

def run():
    logger.info("background_worker component=supervisor status=starting workers=%d", len(WORKERS))

    procs: dict[str, subprocess.Popen] = {}

    # Start all workers
    for worker in WORKERS:
        name = worker["name"]
        cmd = worker["cmd"]
        logger.info("background_worker component=%s status=started", name)
        proc = subprocess.Popen(cmd, stdout=sys.stdout, stderr=sys.stderr)
        procs[name] = proc

    # -----------------------------------------------------------------------
    # Signal handler: forward SIGTERM / SIGINT to all children
    # -----------------------------------------------------------------------
    shutdown_requested = False

    def _request_shutdown(signum, frame):
        nonlocal shutdown_requested
        if shutdown_requested:
            return  # already shutting down
        shutdown_requested = True
        sig_name = "SIGTERM" if signum == signal.SIGTERM else "SIGINT"
        logger.info("background_worker component=supervisor received=%s forwarding_to_children", sig_name)
        for name, proc in procs.items():
            if proc.poll() is None:  # still running
                try:
                    proc.send_signal(signal.SIGTERM)
                    logger.info("background_worker component=%s status=sigterm_sent", name)
                except ProcessLookupError:
                    pass

    signal.signal(signal.SIGTERM, _request_shutdown)
    signal.signal(signal.SIGINT, _request_shutdown)

    # -----------------------------------------------------------------------
    # Monitoring loop
    # -----------------------------------------------------------------------
    exit_code = 0

    while True:
        time.sleep(0.5)

        for name, proc in list(procs.items()):
            rc = proc.poll()
            if rc is None:
                continue  # still running normally

            # Child exited
            if shutdown_requested:
                # Intentional stop: expected
                logger.info(
                    "background_worker component=%s status=stopped exit_code=%d",
                    name,
                    rc,
                )
            else:
                # Unexpected exit — propagate failure
                logger.error(
                    "background_worker component=%s status=crashed exit_code=%d initiating_shutdown",
                    name,
                    rc,
                )
                exit_code = rc if rc != 0 else 1
                shutdown_requested = True

                # Terminate remaining children
                for other_name, other_proc in procs.items():
                    if other_name != name and other_proc.poll() is None:
                        logger.info(
                            "background_worker component=%s status=sigterm_sent reason=peer_crashed",
                            other_name,
                        )
                        try:
                            other_proc.send_signal(signal.SIGTERM)
                        except ProcessLookupError:
                            pass

        # Check if all children have finished
        if all(proc.poll() is not None for proc in procs.values()):
            break

        # During requested shutdown: enforce bounded wait
        if shutdown_requested:
            # Check if we've waited long enough and need to force kill
            for name, proc in procs.items():
                if proc.poll() is None:
                    # Still running — force kill after timeout handled below
                    pass

    # -----------------------------------------------------------------------
    # Force-kill any still-running children after timeout
    # -----------------------------------------------------------------------
    deadline = time.monotonic() + SHUTDOWN_TIMEOUT
    while any(proc.poll() is None for proc in procs.values()):
        if time.monotonic() > deadline:
            for name, proc in procs.items():
                if proc.poll() is None:
                    logger.warning(
                        "background_worker component=%s status=force_killed reason=shutdown_timeout",
                        name,
                    )
                    proc.kill()
            break
        time.sleep(0.5)

    # Final join to collect all child exit codes
    for name, proc in procs.items():
        rc = proc.wait()
        if not shutdown_requested and rc not in (0, -signal.SIGTERM):
            exit_code = max(exit_code, abs(rc))
        logger.info("background_worker component=%s status=stopped exit_code=%d", name, rc)

    logger.info("background_worker component=supervisor status=stopped exit_code=%d", exit_code)
    sys.exit(exit_code)


if __name__ == "__main__":
    run()
