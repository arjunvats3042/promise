import django.db.models.deletion
from django.db import migrations, models


class Migration(migrations.Migration):
    """Align check-in→participant deletion with Goal CASCADE.

    PROTECT blocked Goal.delete() when check-ins existed because Django
    collects cascading GoalParticipant rows before GoalCheckIn rows.
    Membership exit remains soft (status LEFT/REMOVED); hard-delete of a
    participant row cascades that participant's check-ins.
    """

    dependencies = [
        ("goals", "0005_goalevent_participant_types"),
    ]

    operations = [
        migrations.AlterField(
            model_name="goalcheckin",
            name="participant",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="check_ins",
                to="goals.goalparticipant",
            ),
        ),
    ]
