import logging
import signal
import time

from django.core.management.base import BaseCommand

from apps.outbox.publisher import publish_due_outbox_events

logger = logging.getLogger("promise")


class Command(BaseCommand):
    help = "Publish due outbox events to Kafka."

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
            default=1.0,
            help="Seconds to sleep between outbox publish cycles in continuous mode (default: 1.0s).",
        )
        parser.add_argument(
            "--once",
            action="store_true",
            default=False,
            help="Run a single publish cycle and exit (default behavior unless --continuous is passed).",
        )

    def handle(self, *args, **options):
        continuous = options.get("continuous", False) and not options.get("once", False)
        interval = max(0.1, options.get("interval", 1.0))

        stop_requested = False

        def sig_handler(signum, frame):
            nonlocal stop_requested
            logger.info("Outbox publisher received signal %s, initiating graceful shutdown...", signum)
            stop_requested = True

        signal.signal(signal.SIGINT, sig_handler)
        signal.signal(signal.SIGTERM, sig_handler)

        logger.info("Starting outbox publisher (continuous=%s, interval=%.1fs)", continuous, interval)

        while not stop_requested:
            try:
                result = publish_due_outbox_events()
                if result.published > 0 or result.failed > 0 or not continuous:
                    self.stdout.write(
                        f"published={result.published} failed={result.failed}"
                    )
            except Exception as exc:
                logger.error("Error publishing outbox events: %s", exc)

            if not continuous:
                break

            sleep_chunks = int(interval / 0.2)
            for _ in range(max(1, sleep_chunks)):
                if stop_requested:
                    break
                time.sleep(min(0.2, interval))

        logger.info("Outbox publisher stopped cleanly.")
