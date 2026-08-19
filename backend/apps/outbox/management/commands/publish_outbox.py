from django.core.management.base import BaseCommand

from apps.outbox.publisher import publish_due_outbox_events


class Command(BaseCommand):
    help = "Publish due outbox events to Kafka."

    def handle(self, *args, **options):
        result = publish_due_outbox_events()
        self.stdout.write(
            f"published={result.published} failed={result.failed}"
        )
