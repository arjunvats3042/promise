import uuid

from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


def backfill_owner_participants(apps, schema_editor):
    Goal = apps.get_model("goals", "Goal")
    GoalParticipant = apps.get_model("goals", "GoalParticipant")
    for goal in Goal.objects.all().iterator():
        GoalParticipant.objects.get_or_create(
            goal_id=goal.id,
            user_id=goal.created_by_id,
            defaults={
                "id": uuid.uuid4(),
                "role": "OWNER",
                "status": "ACTIVE",
                "invited_at": None,
                "joined_at": goal.created_at,
                "left_at": None,
            },
        )


def noop_reverse(apps, schema_editor):
    pass


class Migration(migrations.Migration):

    dependencies = [
        ("goals", "0002_remove_goal_goal_recurrence_n_per_period_and_more"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="GoalParticipant",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                    ),
                ),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                (
                    "role",
                    models.CharField(
                        choices=[("OWNER", "Owner"), ("PARTICIPANT", "Participant")],
                        max_length=16,
                    ),
                ),
                (
                    "status",
                    models.CharField(
                        choices=[
                            ("INVITED", "Invited"),
                            ("ACTIVE", "Active"),
                            ("DECLINED", "Declined"),
                            ("LEFT", "Left"),
                            ("REMOVED", "Removed"),
                        ],
                        default="ACTIVE",
                        max_length=16,
                    ),
                ),
                ("invited_at", models.DateTimeField(blank=True, null=True)),
                ("joined_at", models.DateTimeField(blank=True, null=True)),
                ("left_at", models.DateTimeField(blank=True, null=True)),
                (
                    "goal",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="participants",
                        to="goals.goal",
                    ),
                ),
                (
                    "user",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.PROTECT,
                        related_name="goal_participations",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "db_table": "goal_participants",
            },
        ),
        migrations.AddIndex(
            model_name="goalparticipant",
            index=models.Index(
                fields=["user", "status"],
                name="goals_participant_user_status",
            ),
        ),
        migrations.AddConstraint(
            model_name="goalparticipant",
            constraint=models.UniqueConstraint(
                fields=("goal", "user"),
                name="uniq_goal_participant_user",
            ),
        ),
        migrations.RunPython(backfill_owner_participants, noop_reverse),
    ]
