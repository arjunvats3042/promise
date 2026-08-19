from django.core.management.base import BaseCommand

from apps.outbox.consumer import run_commitment_consumer


class Command(BaseCommand):
    help = "Consume commitment events from Kafka."

    def add_arguments(self, parser):
        parser.add_argument("--max-messages", type=int, default=None)
        parser.add_argument("--timeout", type=float, default=None)

    def handle(self, *args, **options):
        result = run_commitment_consumer(
            max_messages=options["max_messages"],
            timeout_seconds=options["timeout"],
        )
        self.stdout.write(
            "processed={processed} duplicates={duplicates} "
            "skipped={skipped} retried={retried}".format(**result)
        )
