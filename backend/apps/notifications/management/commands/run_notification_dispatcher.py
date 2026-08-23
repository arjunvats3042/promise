import logging
import signal
import time

from django.core.management.base import BaseCommand

from apps.notifications.dispatcher import dispatch_due_reminders
from apps.notifications.services import sync_all_active_reminders

logger = logging.getLogger("promise")


class Command(BaseCommand):
    help = "Dispatches due reminders and push notifications via FCM."

    def add_arguments(self, parser):
        parser.add_argument(
            "--continuous",
            action="store_true",
            default=False,
            help="Run continuously in a polling loop until stopped.",
        )
        parser.add_argument(
            "--interval",
            type=float,
            default=5.0,
            help="Seconds to sleep between dispatch cycles in continuous mode (default: 5.0s).",
        )
        parser.add_argument(
            "--once",
            action="store_true",
            default=False,
            help="Run a single dispatch cycle and exit (default behavior unless --continuous is passed).",
        )

    def handle(self, *args, **options):
        continuous = options["continuous"] and not options["once"]
        interval = max(0.1, options["interval"])

        stop_requested = False

        def sig_handler(signum, frame):
            nonlocal stop_requested
            logger.info("Notification dispatcher received signal %s, initiating graceful shutdown...", signum)
            stop_requested = True

        signal.signal(signal.SIGINT, sig_handler)
        signal.signal(signal.SIGTERM, sig_handler)

        logger.info("Starting notification dispatcher (continuous=%s, interval=%.1fs)", continuous, interval)

        # Initial sync of all active commitment and goal reminders
        try:
            sync_res = sync_all_active_reminders()
            logger.info("Initial reminder sync: commitments_synced=%d goals_synced=%d", sync_res["commitments_synced"], sync_res["goals_synced"])
        except Exception as exc:
            logger.warning("Initial reminder sync error: %s", exc)

        last_sync_time = time.monotonic()

        while not stop_requested:
            try:
                # Periodic reminder sync every 60 seconds in continuous mode
                if time.monotonic() - last_sync_time >= 60.0:
                    sync_all_active_reminders()
                    last_sync_time = time.monotonic()

                result = dispatch_due_reminders()
                self.stdout.write(
                    f"dispatched={result.dispatched} suppressed={result.suppressed} "
                    f"cancelled={result.cancelled} retried={result.retried} failed={result.failed}"
                )
            except Exception as exc:
                logger.error("Error during notification dispatch: %s", exc)

            if not continuous:
                break

            # Sleep in short increments to respond quickly to SIGTERM/SIGINT
            sleep_chunks = int(interval / 0.5)
            for _ in range(max(1, sleep_chunks)):
                if stop_requested:
                    break
                time.sleep(min(0.5, interval))

        logger.info("Notification dispatcher stopped cleanly.")
