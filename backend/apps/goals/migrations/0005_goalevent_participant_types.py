from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("goals", "0004_goalcheckin_participant"),
    ]

    operations = [
        migrations.AlterField(
            model_name="goalevent",
            name="event_type",
            field=models.CharField(
                choices=[
                    ("CREATED", "Created"),
                    ("UPDATED", "Updated"),
                    ("PAUSED", "Paused"),
                    ("RESUMED", "Resumed"),
                    ("COMPLETED", "Completed"),
                    ("CANCELLED", "Cancelled"),
                    ("CHECKIN_RECORDED", "Check-in recorded"),
                    ("CHECKIN_UPDATED", "Check-in updated"),
                    ("PARTICIPANT_INVITED", "Participant invited"),
                    ("PARTICIPANT_JOINED", "Participant joined"),
                    ("PARTICIPANT_DECLINED", "Participant declined"),
                    ("PARTICIPANT_LEFT", "Participant left"),
                    ("PARTICIPANT_REMOVED", "Participant removed"),
                ],
                max_length=32,
            ),
        ),
    ]
