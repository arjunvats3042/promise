from django.core.management.base import BaseCommand

from apps.outbox.consumer import run_goal_consumer


class Command(BaseCommand):
    help = "Consume goal events from Kafka."

    def add_arguments(self, parser):
        parser.add_argument("--max-messages", type=int, default=None)
        parser.add_argument("--timeout", type=float, default=None)

    def handle(self, *args, **options):
        result = run_goal_consumer(
            max_messages=options["max_messages"],
            timeout_seconds=options["timeout"],
        )
        self.stdout.write(
            "processed={processed} duplicates={duplicates} "
            "skipped={skipped} retried={retried}".format(**result)
        )
