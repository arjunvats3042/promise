from datetime import timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from django.db import IntegrityError, transaction
from django.db.models import Max
from django.utils import timezone

from apps.goals.exceptions import (
    GoalInvalidCheckInError,
    GoalInvalidTransitionError,
    GoalNotFoundError,
    GoalScheduleLockedError,
    GoalTimezoneLockedError,
    GoalValidationError,
)
from apps.goals.models import Goal, GoalCheckIn, GoalEvent
from apps.outbox.services import record_outbox_event

_UNSET = object()
_TERMINAL_STATUSES = (Goal.Status.COMPLETED, Goal.Status.CANCELLED)


def can_view(user, goal):
    return goal.created_by_id == user.id


def get_visible_goal(*, viewer, goal_id):
    goal = (
        Goal.objects.filter(id=goal_id)
        .prefetch_related("check_ins", "events")
        .first()
    )
    if goal is None or not can_view(viewer, goal):
        raise GoalNotFoundError()
    return goal


def list_visible_goals(
    *,
    viewer,
    status=None,
    recurrence_kind=None,
    tracking_kind=None,
):
    queryset = Goal.objects.filter(created_by=viewer).order_by("-created_at")
    if status is not None:
        queryset = queryset.filter(status=status)
    else:
        queryset = queryset.exclude(status__in=_TERMINAL_STATUSES)
    if recurrence_kind is not None:
        queryset = queryset.filter(recurrence_kind=recurrence_kind)
    if tracking_kind is not None:
        queryset = queryset.filter(tracking_kind=tracking_kind)
    return queryset.prefetch_related("check_ins", "events")


def list_goal_check_ins(
    *,
    viewer,
    goal_id,
    start_date=None,
    end_date=None,
    status=None,
):
    goal = get_visible_goal(viewer=viewer, goal_id=goal_id)
    queryset = goal.check_ins.order_by("-period_date", "-created_at")
    if start_date is not None:
        queryset = queryset.filter(period_date__gte=start_date)
    if end_date is not None:
        queryset = queryset.filter(period_date__lte=end_date)
    if status is not None:
        queryset = queryset.filter(status=status)
    return queryset


def create_goal(
    *,
    creator,
    title,
    description="",
    timezone=None,
    start_date=None,
    end_date=None,
    recurrence_kind,
    weekdays=None,
    period_unit=None,
    times_per_period=None,
    tracking_kind=Goal.TrackingKind.BINARY,
    target_value=None,
    target_unit="",
    source=Goal.Source.MANUAL,
):
    title = title.strip() if title else title
    if not title:
        raise GoalValidationError()

    timezone_name = _resolve_timezone(timezone, creator)
    start_date = _resolve_start_date(start_date, timezone_name)
    _validate_end_date(start_date, end_date)
    recurrence_kind, weekdays, period_unit, times_per_period = _normalize_recurrence(
        recurrence_kind,
        weekdays,
        period_unit,
        times_per_period,
    )
    tracking_kind, target_value, target_unit = _normalize_tracking(
        tracking_kind,
        target_value,
        target_unit,
    )

    with transaction.atomic():
        goal = Goal.objects.create(
            created_by=creator,
            title=title,
            description=description or "",
            status=Goal.Status.ACTIVE,
            timezone=timezone_name,
            start_date=start_date,
            end_date=end_date,
            recurrence_kind=recurrence_kind,
            weekdays=weekdays,
            period_unit=period_unit,
            times_per_period=times_per_period,
            tracking_kind=tracking_kind,
            target_value=target_value,
            target_unit=target_unit,
            source=source,
        )
        _add_event(
            goal,
            actor=creator,
            event_type=GoalEvent.EventType.CREATED,
        )
        return goal


def update_goal(
    *,
    actor,
    goal_id,
    title=_UNSET,
    description=_UNSET,
    timezone=_UNSET,
    start_date=_UNSET,
    end_date=_UNSET,
    recurrence_kind=_UNSET,
    weekdays=_UNSET,
    period_unit=_UNSET,
    times_per_period=_UNSET,
    tracking_kind=_UNSET,
    target_value=_UNSET,
    target_unit=_UNSET,
):
    with transaction.atomic():
        goal = _lock_for_owner(actor, goal_id)
        if goal.status in _TERMINAL_STATUSES:
            raise GoalInvalidTransitionError()

        next_title = goal.title
        next_description = goal.description
        next_timezone = goal.timezone
        next_start_date = goal.start_date
        next_end_date = goal.end_date
        next_recurrence_kind = goal.recurrence_kind
        next_weekdays = list(goal.weekdays)
        next_period_unit = goal.period_unit
        next_times_per_period = goal.times_per_period
        next_tracking_kind = goal.tracking_kind
        next_target_value = goal.target_value
        next_target_unit = goal.target_unit

        if title is not _UNSET:
            next_title = title.strip() if title else title
            if not next_title:
                raise GoalValidationError()
        if description is not _UNSET:
            next_description = description or ""
        if timezone is not _UNSET:
            next_timezone = _resolve_timezone(timezone, goal.created_by)
        if start_date is not _UNSET:
            if start_date is None:
                raise GoalValidationError()
            next_start_date = start_date
        if end_date is not _UNSET:
            next_end_date = end_date
        if recurrence_kind is not _UNSET:
            next_recurrence_kind = recurrence_kind
            if recurrence_kind != goal.recurrence_kind:
                if recurrence_kind == Goal.RecurrenceKind.DAILY:
                    if weekdays is _UNSET:
                        next_weekdays = []
                    if period_unit is _UNSET:
                        next_period_unit = None
                    if times_per_period is _UNSET:
                        next_times_per_period = None
                elif recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS:
                    if period_unit is _UNSET:
                        next_period_unit = None
                    if times_per_period is _UNSET:
                        next_times_per_period = None
                elif recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
                    if weekdays is _UNSET:
                        next_weekdays = []
        if weekdays is not _UNSET:
            next_weekdays = weekdays
        if period_unit is not _UNSET:
            next_period_unit = period_unit
        if times_per_period is not _UNSET:
            next_times_per_period = times_per_period
        if tracking_kind is not _UNSET:
            next_tracking_kind = tracking_kind
        if target_value is not _UNSET:
            next_target_value = target_value
        if target_unit is not _UNSET:
            next_target_unit = target_unit

        (
            next_recurrence_kind,
            next_weekdays,
            next_period_unit,
            next_times_per_period,
        ) = _normalize_recurrence(
            next_recurrence_kind,
            next_weekdays,
            next_period_unit,
            next_times_per_period,
        )
        next_tracking_kind, next_target_value, next_target_unit = _normalize_tracking(
            next_tracking_kind,
            next_target_value,
            next_target_unit,
        )
        _validate_end_date(next_start_date, next_end_date)

        has_check_ins = goal.check_ins.exists()
        timezone_changed = next_timezone != goal.timezone
        schedule_changed = (
            next_recurrence_kind != goal.recurrence_kind
            or next_weekdays != list(goal.weekdays)
            or next_period_unit != goal.period_unit
            or next_times_per_period != goal.times_per_period
            or next_tracking_kind != goal.tracking_kind
            or next_target_value != goal.target_value
            or next_target_unit != goal.target_unit
            or next_start_date != goal.start_date
        )
        if has_check_ins and timezone_changed:
            raise GoalTimezoneLockedError()
        if has_check_ins and schedule_changed:
            raise GoalScheduleLockedError()
        if next_end_date is not None:
            latest_period = goal.check_ins.aggregate(Max("period_date"))[
                "period_date__max"
            ]
            if latest_period is not None and next_end_date < latest_period:
                raise GoalValidationError()

        changed = []
        if next_title != goal.title:
            goal.title = next_title
            changed.append("title")
        if next_description != goal.description:
            goal.description = next_description
            changed.append("description")
        if timezone_changed:
            goal.timezone = next_timezone
            changed.append("timezone")
        if next_start_date != goal.start_date:
            goal.start_date = next_start_date
            changed.append("start_date")
        if next_end_date != goal.end_date:
            goal.end_date = next_end_date
            changed.append("end_date")
        if next_recurrence_kind != goal.recurrence_kind:
            goal.recurrence_kind = next_recurrence_kind
            changed.append("recurrence_kind")
        if next_weekdays != list(goal.weekdays):
            goal.weekdays = next_weekdays
            changed.append("weekdays")
        if next_period_unit != goal.period_unit:
            goal.period_unit = next_period_unit
            changed.append("period_unit")
        if next_times_per_period != goal.times_per_period:
            goal.times_per_period = next_times_per_period
            changed.append("times_per_period")
        if next_tracking_kind != goal.tracking_kind:
            goal.tracking_kind = next_tracking_kind
            changed.append("tracking_kind")
        if next_target_value != goal.target_value:
            goal.target_value = next_target_value
            changed.append("target_value")
        if next_target_unit != goal.target_unit:
            goal.target_unit = next_target_unit
            changed.append("target_unit")
        if not changed:
            return goal

        goal.save(update_fields=[*changed, "updated_at"])
        _add_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.UPDATED,
            metadata={"fields": changed},
        )
        return goal


def pause_goal(*, actor, goal_id):
    with transaction.atomic():
        goal = _lock_for_owner(actor, goal_id)
        if goal.status == Goal.Status.PAUSED:
            return goal
        if goal.status != Goal.Status.ACTIVE:
            raise GoalInvalidTransitionError()
        now = timezone.now()
        goal.status = Goal.Status.PAUSED
        goal.paused_at = now
        goal.save(update_fields=["status", "paused_at", "updated_at"])
        _add_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PAUSED,
        )
        return goal


def resume_goal(*, actor, goal_id):
    with transaction.atomic():
        goal = _lock_for_owner(actor, goal_id)
        if goal.status == Goal.Status.ACTIVE:
            return goal
        if goal.status != Goal.Status.PAUSED:
            raise GoalInvalidTransitionError()
        goal.status = Goal.Status.ACTIVE
        goal.paused_at = None
        goal.save(update_fields=["status", "paused_at", "updated_at"])
        _add_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.RESUMED,
        )
        return goal


def complete_goal(*, actor, goal_id):
    with transaction.atomic():
        goal = _lock_for_owner(actor, goal_id)
        if goal.status == Goal.Status.COMPLETED:
            return goal
        if goal.status in (Goal.Status.CANCELLED,):
            raise GoalInvalidTransitionError()
        if goal.status not in (Goal.Status.ACTIVE, Goal.Status.PAUSED):
            raise GoalInvalidTransitionError()
        now = timezone.now()
        goal.status = Goal.Status.COMPLETED
        goal.completed_at = now
        goal.paused_at = None
        goal.save(
            update_fields=["status", "completed_at", "paused_at", "updated_at"]
        )
        _add_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.COMPLETED,
        )
        return goal


def cancel_goal(*, actor, goal_id):
    with transaction.atomic():
        goal = _lock_for_owner(actor, goal_id)
        if goal.status == Goal.Status.CANCELLED:
            return goal
        if goal.status in (Goal.Status.COMPLETED,):
            raise GoalInvalidTransitionError()
        if goal.status not in (Goal.Status.ACTIVE, Goal.Status.PAUSED):
            raise GoalInvalidTransitionError()
        now = timezone.now()
        goal.status = Goal.Status.CANCELLED
        goal.cancelled_at = now
        goal.paused_at = None
        goal.save(
            update_fields=["status", "cancelled_at", "paused_at", "updated_at"]
        )
        _add_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.CANCELLED,
        )
        return goal


def record_check_in(
    *,
    actor,
    goal_id,
    period_date,
    status,
    value=None,
    note="",
    at=None,
):
    return _upsert_check_in(
        actor=actor,
        goal_id=goal_id,
        period_date=period_date,
        status=status,
        value=value,
        note=note,
        at=at,
        require_existing=False,
    )


def update_check_in(
    *,
    actor,
    goal_id,
    period_date,
    status,
    value=None,
    note="",
    at=None,
):
    return _upsert_check_in(
        actor=actor,
        goal_id=goal_id,
        period_date=period_date,
        status=status,
        value=value,
        note=note,
        at=at,
        require_existing=True,
    )


def is_period_expected(goal, period_date, *, at=None):
    return _is_period_expected(
        goal,
        period_date,
        paused_ranges=_paused_date_ranges(goal, at=at),
    )


def goal_progress(goal, *, at=None):
    today = _local_today(goal, at)
    paused_ranges = _paused_date_ranges(goal, at=at)
    check_ins = _check_ins_by_date(goal)
    current = _current_period_progress(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
    )
    week = _week_progress(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
    )
    return {
        "current_period": current,
        "week_progress": week,
        "consistency_percent": _consistency_percent(
            goal,
            today=today,
            paused_ranges=paused_ranges,
            check_ins=check_ins,
        ),
    }


def goal_streak(goal, *, at=None):
    today = _local_today(goal, at)
    paused_ranges = _paused_date_ranges(goal, at=at)
    check_ins = _check_ins_by_date(goal)
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        return _n_per_period_streak(
            goal,
            today=today,
            paused_ranges=paused_ranges,
            check_ins=check_ins,
        )
    return _daily_weekday_streak(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
    )


def _upsert_check_in(
    *,
    actor,
    goal_id,
    period_date,
    status,
    value,
    note,
    at,
    require_existing,
):
    note = note or ""
    with transaction.atomic():
        goal = _lock_for_owner(actor, goal_id)
        if goal.status != Goal.Status.ACTIVE:
            raise GoalInvalidTransitionError()
        _validate_check_in_payload(goal, period_date, status, value, at=at)

        existing = (
            GoalCheckIn.objects.select_for_update()
            .filter(goal=goal, period_date=period_date)
            .first()
        )
        if existing is None:
            if require_existing:
                raise GoalInvalidCheckInError()
            try:
                check_in = GoalCheckIn.objects.create(
                    goal=goal,
                    created_by=actor,
                    period_date=period_date,
                    status=status,
                    value=value,
                    note=note,
                    checked_at=timezone.now(),
                )
            except IntegrityError:
                check_in = GoalCheckIn.objects.select_for_update().get(
                    goal=goal,
                    period_date=period_date,
                )
                return _apply_check_in_update(
                    goal,
                    actor=actor,
                    check_in=check_in,
                    status=status,
                    value=value,
                    note=note,
                )
            _add_event(
                goal,
                actor=actor,
                event_type=GoalEvent.EventType.CHECKIN_RECORDED,
                metadata={
                    "checkin_id": str(check_in.id),
                    "period_date": period_date.isoformat(),
                },
                check_in=check_in,
            )
            return check_in

        return _apply_check_in_update(
            goal,
            actor=actor,
            check_in=existing,
            status=status,
            value=value,
            note=note,
        )


def _apply_check_in_update(goal, *, actor, check_in, status, value, note):
    if (
        check_in.status == status
        and check_in.value == value
        and check_in.note == note
    ):
        return check_in
    check_in.status = status
    check_in.value = value
    check_in.note = note
    check_in.checked_at = timezone.now()
    check_in.save(
        update_fields=["status", "value", "note", "checked_at", "updated_at"]
    )
    _add_event(
        goal,
        actor=actor,
        event_type=GoalEvent.EventType.CHECKIN_UPDATED,
        metadata={
            "checkin_id": str(check_in.id),
            "period_date": check_in.period_date.isoformat(),
        },
        check_in=check_in,
    )
    return check_in


def _validate_check_in_payload(goal, period_date, status, value, *, at=None):
    if status not in GoalCheckIn.Status.values:
        raise GoalInvalidCheckInError()
    today = _local_today(goal, at)
    if period_date < goal.start_date:
        raise GoalInvalidCheckInError()
    if period_date > today:
        raise GoalInvalidCheckInError()
    if goal.end_date is not None and period_date > goal.end_date:
        raise GoalInvalidCheckInError()
    if goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS:
        if period_date.isoweekday() not in goal.weekdays:
            raise GoalInvalidCheckInError()
    if status == GoalCheckIn.Status.SKIPPED:
        if value is not None:
            raise GoalInvalidCheckInError()
        return
    if goal.tracking_kind == Goal.TrackingKind.BINARY:
        if value is not None:
            raise GoalInvalidCheckInError()
        return
    if not _is_non_negative_int(value):
        raise GoalInvalidCheckInError()


def _normalize_recurrence(recurrence_kind, weekdays, period_unit, times_per_period):
    if recurrence_kind not in Goal.RecurrenceKind.values:
        raise GoalValidationError()
    normalized_weekdays = _normalize_weekdays(weekdays)

    if recurrence_kind == Goal.RecurrenceKind.DAILY:
        if normalized_weekdays:
            raise GoalValidationError()
        if period_unit is not None or times_per_period is not None:
            raise GoalValidationError()
        return recurrence_kind, [], None, None

    if recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS:
        if not normalized_weekdays:
            raise GoalValidationError()
        if period_unit is not None or times_per_period is not None:
            raise GoalValidationError()
        return recurrence_kind, normalized_weekdays, None, None

    if period_unit != Goal.PeriodUnit.WEEK:
        raise GoalValidationError()
    if not _is_positive_int(times_per_period) or times_per_period > 7:
        raise GoalValidationError()
    if normalized_weekdays:
        raise GoalValidationError()
    return recurrence_kind, [], period_unit, times_per_period


def _normalize_tracking(tracking_kind, target_value, target_unit):
    if tracking_kind not in Goal.TrackingKind.values:
        raise GoalValidationError()
    target_unit = target_unit or ""
    if tracking_kind == Goal.TrackingKind.BINARY:
        if target_value is not None:
            raise GoalValidationError()
        return tracking_kind, None, target_unit
    if not _is_positive_int(target_value):
        raise GoalValidationError()
    return tracking_kind, target_value, target_unit


def _normalize_weekdays(weekdays):
    if not weekdays:
        return []
    if not isinstance(weekdays, (list, tuple)):
        raise GoalValidationError()
    normalized = []
    seen = set()
    for item in weekdays:
        if not _is_positive_int(item) or item < 1 or item > 7:
            raise GoalValidationError()
        if item not in seen:
            seen.add(item)
            normalized.append(item)
    normalized.sort()
    return normalized


def _resolve_timezone(value, creator):
    timezone_name = value if value else creator.timezone
    if not timezone_name:
        raise GoalValidationError()
    try:
        ZoneInfo(timezone_name)
    except (ZoneInfoNotFoundError, KeyError):
        raise GoalValidationError()
    return timezone_name


def _resolve_start_date(start_date, timezone_name):
    if start_date is not None:
        return start_date
    return timezone.now().astimezone(ZoneInfo(timezone_name)).date()


def _validate_end_date(start_date, end_date):
    if end_date is not None and end_date < start_date:
        raise GoalValidationError()


def _lock_for_owner(actor, goal_id):
    goal = Goal.objects.select_for_update().filter(id=goal_id).first()
    if goal is None or not can_view(actor, goal):
        raise GoalNotFoundError()
    return goal


def _now(at=None):
    if at is None:
        return timezone.now()
    return at


def _local_today(goal, at=None):
    return _now(at).astimezone(ZoneInfo(goal.timezone)).date()


def _iso_week_start(day):
    return day - timedelta(days=day.isoweekday() - 1)


def _check_ins_by_date(goal):
    return {row.period_date: row for row in goal.check_ins.all()}


def _paused_date_ranges(goal, *, at=None):
    pause_types = (GoalEvent.EventType.PAUSED, GoalEvent.EventType.RESUMED)
    events = [
        event
        for event in goal.events.all()
        if event.event_type in pause_types
        and (at is None or event.created_at <= at)
    ]
    events.sort(key=lambda event: event.created_at)
    zone = ZoneInfo(goal.timezone)
    ranges = []
    pause_start = None
    for event in events:
        local_date = event.created_at.astimezone(zone).date()
        if event.event_type == GoalEvent.EventType.PAUSED:
            if pause_start is None:
                pause_start = local_date
            continue
        if pause_start is not None and pause_start < local_date:
            ranges.append((pause_start, local_date - timedelta(days=1)))
        pause_start = None
    if pause_start is not None:
        today = _local_today(goal, at)
        if pause_start <= today:
            ranges.append((pause_start, today))
    return ranges


def _is_paused_date(paused_ranges, period_date):
    for start, end in paused_ranges:
        if start <= period_date <= end:
            return True
    return False


def _is_period_expected(goal, period_date, *, paused_ranges):
    if period_date < goal.start_date:
        return False
    if goal.end_date is not None and period_date > goal.end_date:
        return False
    if _is_paused_date(paused_ranges, period_date):
        return False
    if goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS:
        return period_date.isoweekday() in goal.weekdays
    return True


def _is_successful(goal, check_in):
    if check_in is None or check_in.status != GoalCheckIn.Status.COMPLETED:
        return False
    if goal.tracking_kind == Goal.TrackingKind.BINARY:
        return True
    return check_in.value is not None and check_in.value >= goal.target_value


def _current_period_progress(goal, *, today, paused_ranges, check_ins):
    today_expected = _is_period_expected(
        goal, today, paused_ranges=paused_ranges
    )
    today_row = check_ins.get(today)
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        week_start = _iso_week_start(today)
        required = (
            goal.times_per_period
            if _week_has_expected_day(goal, week_start, paused_ranges)
            else 0
        )
        current = {
            "required": required,
            "completed": _week_successful_count(
                goal,
                week_start,
                paused_ranges=paused_ranges,
                check_ins=check_ins,
            ),
        }
    else:
        current = {
            "required": 1 if today_expected else 0,
            "completed": (
                1 if today_expected and _is_successful(goal, today_row) else 0
            ),
        }
    if goal.tracking_kind == Goal.TrackingKind.COUNT and today_row is not None:
        current["value"] = today_row.value
        current["target_value"] = goal.target_value
    return current


def _week_progress(goal, *, today, paused_ranges, check_ins):
    week_start = _iso_week_start(today)
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        required = (
            goal.times_per_period
            if _week_has_expected_day(goal, week_start, paused_ranges)
            else 0
        )
        return {
            "required": required,
            "completed": _week_successful_count(
                goal,
                week_start,
                paused_ranges=paused_ranges,
                check_ins=check_ins,
            ),
        }
    required = 0
    completed = 0
    cursor = week_start
    while cursor <= today:
        if _is_period_expected(goal, cursor, paused_ranges=paused_ranges):
            required += 1
            if _is_successful(goal, check_ins.get(cursor)):
                completed += 1
        cursor += timedelta(days=1)
    return {"required": required, "completed": completed}


def _consistency_percent(goal, *, today, paused_ranges, check_ins):
    window_end = today - timedelta(days=1)
    window_start = max(goal.start_date, today - timedelta(days=27))
    if window_end < window_start:
        return 0
    expected = 0
    successful = 0
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        week = _iso_week_start(window_start)
        last_week = _iso_week_start(window_end)
        while week <= last_week:
            week_end = week + timedelta(days=6)
            if week_end < goal.start_date:
                week += timedelta(days=7)
                continue
            if week > window_end:
                break
            finished = week_end <= window_end
            if not finished:
                week += timedelta(days=7)
                continue
            if not _week_has_expected_day(goal, week, paused_ranges):
                week += timedelta(days=7)
                continue
            expected += goal.times_per_period
            completed = _week_successful_count(
                goal,
                week,
                paused_ranges=paused_ranges,
                check_ins=check_ins,
            )
            successful += min(completed, goal.times_per_period)
            week += timedelta(days=7)
    else:
        cursor = window_start
        while cursor <= window_end:
            if _is_period_expected(goal, cursor, paused_ranges=paused_ranges):
                expected += 1
                if _is_successful(goal, check_ins.get(cursor)):
                    successful += 1
            cursor += timedelta(days=1)
    if expected == 0:
        return 0
    return successful * 100 // expected


def _week_has_expected_day(goal, week_start, paused_ranges):
    for offset in range(7):
        day = week_start + timedelta(days=offset)
        if _is_period_expected(goal, day, paused_ranges=paused_ranges):
            return True
    return False


def _week_successful_count(goal, week_start, *, paused_ranges, check_ins):
    completed = 0
    for offset in range(7):
        day = week_start + timedelta(days=offset)
        if day < goal.start_date:
            continue
        if goal.end_date is not None and day > goal.end_date:
            continue
        if _is_successful(goal, check_ins.get(day)):
            completed += 1
    return completed


def _daily_weekday_streak(goal, *, today, paused_ranges, check_ins):
    cursor = today
    if not (
        _is_period_expected(goal, today, paused_ranges=paused_ranges)
        and _is_successful(goal, check_ins.get(today))
    ):
        cursor = today - timedelta(days=1)
    streak = 0
    while cursor >= goal.start_date:
        if not _is_period_expected(goal, cursor, paused_ranges=paused_ranges):
            cursor -= timedelta(days=1)
            continue
        if not _is_successful(goal, check_ins.get(cursor)):
            break
        streak += 1
        cursor -= timedelta(days=1)
    return streak


def _n_per_period_streak(goal, *, today, paused_ranges, check_ins):
    yesterday = today - timedelta(days=1)
    current_week = _iso_week_start(today)
    current_completed = _week_successful_count(
        goal,
        current_week,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
    )
    if current_completed >= goal.times_per_period:
        week = current_week
    elif yesterday.isoweekday() == 7:
        week = _iso_week_start(yesterday)
    else:
        week = _iso_week_start(yesterday) - timedelta(days=7)

    streak = 0
    while True:
        week_end = week + timedelta(days=6)
        if week_end < goal.start_date:
            break
        if not _week_has_expected_day(goal, week, paused_ranges):
            week -= timedelta(days=7)
            continue
        completed = _week_successful_count(
            goal,
            week,
            paused_ranges=paused_ranges,
            check_ins=check_ins,
        )
        if completed >= goal.times_per_period:
            streak += 1
            week -= timedelta(days=7)
            continue
        break
    return streak


def _is_positive_int(value):
    return isinstance(value, int) and not isinstance(value, bool) and value >= 1


def _is_non_negative_int(value):
    return isinstance(value, int) and not isinstance(value, bool) and value >= 0


_OUTBOX_EVENT_TYPES = {
    GoalEvent.EventType.CREATED: "goal.created",
    GoalEvent.EventType.UPDATED: "goal.updated",
    GoalEvent.EventType.PAUSED: "goal.paused",
    GoalEvent.EventType.RESUMED: "goal.resumed",
    GoalEvent.EventType.COMPLETED: "goal.completed",
    GoalEvent.EventType.CANCELLED: "goal.cancelled",
    GoalEvent.EventType.CHECKIN_RECORDED: "goal.checkin.created",
    GoalEvent.EventType.CHECKIN_UPDATED: "goal.checkin.updated",
}


def _iso(value):
    if value is None:
        return None
    return value.isoformat()


def _goal_outbox_payload(goal, domain_event, check_in=None):
    payload = {
        "goal_event_id": str(domain_event.id),
        "goal_id": str(goal.id),
        "created_by_user_id": str(goal.created_by_id),
        "actor_user_id": (
            str(domain_event.actor_id) if domain_event.actor_id else None
        ),
        "status": goal.status,
    }
    event_type = domain_event.event_type
    if event_type == GoalEvent.EventType.CREATED:
        payload["recurrence_kind"] = goal.recurrence_kind
        payload["tracking_kind"] = goal.tracking_kind
        payload["source"] = goal.source
        payload["start_date"] = _iso(goal.start_date)
    elif event_type == GoalEvent.EventType.UPDATED:
        if "fields" in domain_event.metadata:
            payload["fields"] = domain_event.metadata["fields"]
    elif event_type == GoalEvent.EventType.PAUSED:
        payload["paused_at"] = _iso(goal.paused_at)
    elif event_type == GoalEvent.EventType.COMPLETED:
        payload["completed_at"] = _iso(goal.completed_at)
    elif event_type == GoalEvent.EventType.CANCELLED:
        payload["cancelled_at"] = _iso(goal.cancelled_at)
    elif event_type in (
        GoalEvent.EventType.CHECKIN_RECORDED,
        GoalEvent.EventType.CHECKIN_UPDATED,
    ):
        payload["period_date"] = _iso(check_in.period_date) if check_in else None
        payload["checkin_status"] = check_in.status if check_in else None
        payload["tracking_kind"] = goal.tracking_kind
        payload["value"] = check_in.value if check_in else None
        if check_in is not None:
            payload["checkin_id"] = str(check_in.id)
    return payload


def _add_event(goal, *, actor, event_type, metadata=None, check_in=None):
    domain_event = GoalEvent.objects.create(
        goal=goal,
        actor=actor,
        event_type=event_type,
        metadata=metadata or {},
    )
    record_outbox_event(
        aggregate_type="goal",
        aggregate_id=goal.id,
        event_type=_OUTBOX_EVENT_TYPES[event_type],
        payload=_goal_outbox_payload(goal, domain_event, check_in=check_in),
        occurred_at=domain_event.created_at,
    )
    return domain_event
