import django.db.models.deletion
from django.db import migrations, models


def backfill_checkin_participants(apps, schema_editor):
    GoalCheckIn = apps.get_model("goals", "GoalCheckIn")
    GoalParticipant = apps.get_model("goals", "GoalParticipant")
    for check_in in GoalCheckIn.objects.filter(participant_id__isnull=True).iterator():
        owner = (
            GoalParticipant.objects.filter(
                goal_id=check_in.goal_id,
                role="OWNER",
                status="ACTIVE",
            )
            .order_by("created_at")
            .first()
        )
        if owner is None:
            owner = GoalParticipant.objects.filter(goal_id=check_in.goal_id).first()
        if owner is None:
            raise RuntimeError(
                f"Missing owner participant for goal {check_in.goal_id} "
                f"while backfilling check-in {check_in.id}"
            )
        check_in.participant_id = owner.id
        check_in.save(update_fields=["participant_id"])


def noop_reverse(apps, schema_editor):
    pass


class Migration(migrations.Migration):

    dependencies = [
        ("goals", "0003_goalparticipant"),
    ]

    operations = [
        migrations.AddField(
            model_name="goalcheckin",
            name="participant",
            field=models.ForeignKey(
                null=True,
                on_delete=django.db.models.deletion.CASCADE,
                related_name="check_ins",
                to="goals.goalparticipant",
            ),
        ),
        migrations.RunPython(backfill_checkin_participants, noop_reverse),
        migrations.AlterField(
            model_name="goalcheckin",
            name="participant",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="check_ins",
                to="goals.goalparticipant",
            ),
        ),
        migrations.RemoveConstraint(
            model_name="goalcheckin",
            name="uniq_goal_check_in_period",
        ),
        migrations.AddConstraint(
            model_name="goalcheckin",
            constraint=models.UniqueConstraint(
                fields=("goal", "participant", "period_date"),
                name="uniq_goal_check_in_participant_period",
            ),
        ),
    ]
