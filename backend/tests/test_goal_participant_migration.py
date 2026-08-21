"""Schema checks for Shared Goals storage (Phase 10.2)."""

import pytest
from django.db import connection


@pytest.mark.django_db
def test_goal_participants_table_and_constraints_exist():
    table_names = set(connection.introspection.table_names())
    assert "goal_participants" in table_names

    with connection.cursor() as cursor:
        constraints = connection.introspection.get_constraints(cursor, "goal_participants")
        check_ins = connection.introspection.get_constraints(cursor, "goal_check_ins")

    unique_goal_user = [
        name
        for name, meta in constraints.items()
        if meta.get("unique") and set(meta.get("columns") or []) == {"goal_id", "user_id"}
    ]
    assert unique_goal_user

    index_user_status = [
        name
        for name, meta in constraints.items()
        if meta.get("index")
        and not meta.get("unique")
        and list(meta.get("columns") or [])[:2] == ["user_id", "status"]
    ]
    assert index_user_status or any(
        "user" in (meta.get("columns") or []) and "status" in (meta.get("columns") or [])
        for meta in constraints.values()
        if meta.get("index")
    )

    assert "uniq_goal_check_in_period" not in check_ins
    participant_period = [
        name
        for name, meta in check_ins.items()
        if meta.get("unique")
        and set(meta.get("columns") or [])
        == {"goal_id", "participant_id", "period_date"}
    ]
    assert participant_period or any(
        name == "uniq_goal_check_in_participant_period" for name in check_ins
    )
