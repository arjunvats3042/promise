import uuid
from datetime import timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from django.contrib.auth import get_user_model
from django.db import IntegrityError, models, transaction
from django.db.models import Exists, Max, OuterRef, Q
from django.utils import timezone

from apps.goals.exceptions import (
    GoalAlreadyParticipantError,
    GoalCannotRemoveOwnerError,
    GoalCannotTransferOwnershipToSelfError,
    GoalChatForbiddenError,
    GoalChatInvalidCursorError,
    GoalChatInvalidMessageError,
    GoalInvalidCheckInError,
    GoalInvalidParticipantStateError,
    GoalInvalidTransitionError,
    GoalInviteExpiredError,
    GoalInviteRevokedError,
    GoalNotFoundError,
    GoalOwnerCannotLeaveError,
    GoalOwnerRequiredError,
    GoalParticipantLimitReachedError,
    GoalScheduleLockedError,
    GoalTimezoneLockedError,
    GoalValidationError,
)
from apps.goals.models import (
    ChatMessage,
    Goal,
    GoalCheckIn,
    GoalChatReadState,
    GoalEvent,
    GoalParticipant,
)
from apps.outbox.services import record_outbox_event

_UNSET = object()
_TERMINAL_STATUSES = (Goal.Status.COMPLETED, Goal.Status.CANCELLED)
_INVITE_TTL = timedelta(days=7)
MAX_SHARED_GOAL_PARTICIPANTS = 10
_VIEWABLE_PARTICIPANT_STATUSES = (
    GoalParticipant.Status.ACTIVE,
    GoalParticipant.Status.INVITED,
)
_ENDED_MEMBERSHIP_STATUSES = (
    GoalParticipant.Status.LEFT,
    GoalParticipant.Status.REMOVED,
)


def get_participant(goal, user):
    if goal is None or user is None:
        return None
    if hasattr(goal, "_prefetched_objects_cache") and "participants" in goal._prefetched_objects_cache:
        user_id = getattr(user, "id", user)
        for p in goal.participants.all():
            if p.user_id == user_id:
                return p
        return None
    return GoalParticipant.objects.filter(goal=goal, user=user).first()


def get_goal_membership(user, goal):
    return get_participant(goal, user)


def can_view_goal_for_user(user, goal):
    participant = get_participant(goal, user)
    if participant is None:
        return False
    return participant.status in _VIEWABLE_PARTICIPANT_STATUSES


def can_view_goal(user, goal):
    return can_view_goal_for_user(user, goal)


def can_view(user, goal):
    return can_view_goal_for_user(user, goal)


def can_manage_goal(user, goal):
    participant = get_participant(goal, user)
    return (
        participant is not None
        and participant.role == GoalParticipant.Role.OWNER
        and participant.status == GoalParticipant.Status.ACTIVE
        and goal.created_by_id == user.id
    )


def can_edit_goal(user, goal):
    return can_manage_goal(user, goal)


def can_manage_members(user, goal):
    return can_manage_goal(user, goal)


def can_check_in(user, goal):
    participant = get_participant(goal, user)
    return (
        participant is not None
        and participant.status == GoalParticipant.Status.ACTIVE
    )


def can_leave(user, goal):
    participant = get_participant(goal, user)
    return (
        participant is not None
        and participant.role == GoalParticipant.Role.PARTICIPANT
        and participant.status == GoalParticipant.Status.ACTIVE
        and goal.status not in _TERMINAL_STATUSES
    )


def can_invite(user, goal):
    return can_manage_goal(user, goal) and goal.status not in _TERMINAL_STATUSES


def can_remove_participant(user, goal):
    return can_manage_goal(user, goal) and goal.status not in _TERMINAL_STATUSES


def is_invite_preview(user, goal):
    participant = get_participant(goal, user)
    return (
        participant is not None
        and participant.status == GoalParticipant.Status.INVITED
    )


def is_shared_goal(goal):
    if goal.is_shared:
        return True
    if hasattr(goal, "_prefetched_objects_cache") and "participants" in goal._prefetched_objects_cache:
        return any(p.role != GoalParticipant.Role.OWNER for p in goal.participants.all())
    return goal.participants.exclude(role=GoalParticipant.Role.OWNER).exists()


def can_view_chat(user, goal):
    if goal is None or not is_shared_goal(goal):
        return False
    return can_check_in(user, goal)


def can_send_chat(user, goal):
    if goal is None or goal.status in _TERMINAL_STATUSES:
        return False
    return can_view_chat(user, goal)


def can_update_read_state(user, goal):
    return can_view_chat(user, goal)


def invite_expires_at(participant):
    if participant is None:
        return None
    if participant.invitation_expires_at is not None:
        return participant.invitation_expires_at
    if participant.invited_at is not None:
        return participant.invited_at + _INVITE_TTL
    return None


def is_invite_expired(participant):
    if participant is None or participant.status != GoalParticipant.Status.INVITED:
        return False
    expires = invite_expires_at(participant)
    if expires is None:
        return False
    return timezone.now() > expires


def list_active_participants(*, viewer, goal_id):
    goal = get_visible_goal(viewer=viewer, goal_id=goal_id)
    membership = get_participant(goal, viewer)
    if membership is None or membership.status != GoalParticipant.Status.ACTIVE:
        raise GoalNotFoundError()
    return list(
        goal.participants.filter(status=GoalParticipant.Status.ACTIVE)
        .select_related("user")
        .order_by("joined_at", "created_at")
    )


def _lookup_goal(goal_id):
    if isinstance(goal_id, int) or (isinstance(goal_id, str) and str(goal_id).isdigit()):
        return (
            Goal.objects.filter(numeric_id=int(goal_id))
            .prefetch_related("check_ins", "events", "participants", "participants__user")
            .first()
        )
    try:
        val = uuid.UUID(str(goal_id))
        return (
            Goal.objects.filter(id=val)
            .prefetch_related("check_ins", "events", "participants", "participants__user")
            .first()
        )
    except (ValueError, AttributeError):
        return None


def get_visible_goal(*, viewer, goal_id):
    goal = _lookup_goal(goal_id)
    if goal is None or not can_view_goal_for_user(viewer, goal):
        raise GoalNotFoundError()
    return goal


def list_visible_goals(
    *,
    viewer,
    status=None,
    recurrence_kind=None,
    tracking_kind=None,
):
    membership = GoalParticipant.objects.filter(
        goal_id=OuterRef("pk"),
        user=viewer,
        status__in=_VIEWABLE_PARTICIPANT_STATUSES,
    )
    queryset = Goal.objects.filter(Exists(membership)).order_by("-created_at")
    if status is not None:
        queryset = queryset.filter(status=status)
    else:
        queryset = queryset.exclude(status__in=_TERMINAL_STATUSES)
    if recurrence_kind is not None:
        queryset = queryset.filter(recurrence_kind=recurrence_kind)
    if tracking_kind is not None:
        queryset = queryset.filter(tracking_kind=tracking_kind)
    return queryset.prefetch_related("check_ins", "events", "participants", "participants__user")


def list_goal_check_ins(
    *,
    viewer,
    goal_id,
    start_date=None,
    end_date=None,
    status=None,
):
    goal = get_visible_goal(viewer=viewer, goal_id=goal_id)
    participant = get_participant(goal, viewer)
    if participant is None or participant.status != GoalParticipant.Status.ACTIVE:
        raise GoalNotFoundError()
    queryset = goal.check_ins.order_by("-period_date", "-created_at")
    if not can_manage_goal(viewer, goal):
        queryset = queryset.filter(participant=participant)
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
    is_shared=False,
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
            is_shared=is_shared,
        )
        GoalParticipant.objects.create(
            goal=goal,
            user=creator,
            role=GoalParticipant.Role.OWNER,
            status=GoalParticipant.Status.ACTIVE,
            joined_at=goal.created_at,
        )
        _add_event(
            goal,
            actor=creator,
            event_type=GoalEvent.EventType.CREATED,
        )
        return goal


def create_shared_goal(**kwargs):
    kwargs["is_shared"] = True
    return create_goal(**kwargs)


def invite_participant(*, actor, goal_id, user_id):
    with transaction.atomic():
        goal = _lock_for_manager(actor, goal_id)
        if goal.status in _TERMINAL_STATUSES:
            raise GoalInvalidTransitionError()
        if not goal.is_shared:
            goal.is_shared = True
            goal.save(update_fields=["is_shared", "updated_at"])

        # Lock all active participant rows before counting to prevent phantom reads
        # under concurrent invites racing against the capacity limit.
        active_count = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, status=GoalParticipant.Status.ACTIVE)
            .count()
        )
        if active_count >= MAX_SHARED_GOAL_PARTICIPANTS:
            raise GoalParticipantLimitReachedError()

        target = get_user_model().objects.filter(id=user_id).first()
        if target is None:
            raise GoalValidationError()
        if target.id == actor.id:
            raise GoalInvalidParticipantStateError()

        participant = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, user=target)
            .first()
        )
        now = timezone.now()
        if participant is not None:
            if participant.status == GoalParticipant.Status.ACTIVE:
                raise GoalAlreadyParticipantError()
            if participant.status == GoalParticipant.Status.INVITED and not _invite_is_expired(participant):
                return participant
            if participant.status not in (
                GoalParticipant.Status.DECLINED,
                GoalParticipant.Status.LEFT,
                GoalParticipant.Status.REMOVED,
                GoalParticipant.Status.INVITED,
            ):
                raise GoalInvalidParticipantStateError()

            participant.role = GoalParticipant.Role.PARTICIPANT
            participant.status = GoalParticipant.Status.INVITED
            participant.invited_at = now
            participant.invitation_expires_at = now + _INVITE_TTL
            participant.joined_at = None
            participant.left_at = None
            participant.save(
                update_fields=[
                    "role",
                    "status",
                    "invited_at",
                    "invitation_expires_at",
                    "joined_at",
                    "left_at",
                    "updated_at",
                ]
            )
            _add_participant_event(
                goal,
                actor=actor,
                event_type=GoalEvent.EventType.PARTICIPANT_INVITED,
                participant=participant,
            )
            return participant

        try:
            participant = GoalParticipant.objects.create(
                goal=goal,
                user=target,
                role=GoalParticipant.Role.PARTICIPANT,
                status=GoalParticipant.Status.INVITED,
                invited_at=now,
                invitation_expires_at=now + _INVITE_TTL,
            )
        except IntegrityError:
            participant = (
                GoalParticipant.objects.select_for_update()
                .get(goal=goal, user=target)
            )
            if participant.status == GoalParticipant.Status.ACTIVE:
                raise GoalAlreadyParticipantError()
            if participant.status == GoalParticipant.Status.INVITED:
                return participant
            raise GoalInvalidParticipantStateError()
        _add_participant_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PARTICIPANT_INVITED,
            participant=participant,
        )
        return participant


def reinvite_participant(*, actor, goal_id, participant_id=None, user_id=None):
    with transaction.atomic():
        goal = _lock_for_manager(actor, goal_id)
        if goal.status in _TERMINAL_STATUSES:
            raise GoalInvalidTransitionError()
        if not goal.is_shared:
            raise GoalNotFoundError()

        # Lock all active participant rows before counting (same pattern as invite_participant).
        active_count = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, status=GoalParticipant.Status.ACTIVE)
            .count()
        )
        if active_count >= MAX_SHARED_GOAL_PARTICIPANTS:
            raise GoalParticipantLimitReachedError()

        query = GoalParticipant.objects.select_for_update().filter(goal=goal)
        if participant_id is not None:
            participant = query.filter(Q(id=participant_id) | Q(numeric_id=participant_id) if str(participant_id).isdigit() else Q(id=participant_id)).first()
        elif user_id is not None:
            participant = query.filter(Q(user_id=user_id) | Q(user__numeric_id=user_id) if str(user_id).isdigit() else Q(user_id=user_id)).first()
        else:
            raise GoalValidationError()

        if participant is None:
            raise GoalNotFoundError()
        if participant.status == GoalParticipant.Status.ACTIVE:
            raise GoalAlreadyParticipantError()
        if participant.status == GoalParticipant.Status.INVITED and not _invite_is_expired(participant):
            return participant

        now = timezone.now()
        participant.role = GoalParticipant.Role.PARTICIPANT
        participant.status = GoalParticipant.Status.INVITED
        participant.invited_at = now
        participant.invitation_expires_at = now + _INVITE_TTL
        participant.joined_at = None
        participant.left_at = None
        participant.save(
            update_fields=[
                "role",
                "status",
                "invited_at",
                "invitation_expires_at",
                "joined_at",
                "left_at",
                "updated_at",
            ]
        )
        _add_participant_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PARTICIPANT_REINVITED,
            participant=participant,
        )
        return participant


def accept_invitation(*, actor, goal_id):
    with transaction.atomic():
        goal = Goal.objects.select_for_update().filter(id=goal_id).first()
        if goal is None or not goal.is_shared:
            raise GoalNotFoundError()
        if goal.status != Goal.Status.ACTIVE:
            raise GoalInvalidTransitionError()

        participant = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, user=actor)
            .first()
        )
        if participant is None:
            raise GoalNotFoundError()
        if participant.status == GoalParticipant.Status.ACTIVE:
            return participant
        if participant.status != GoalParticipant.Status.INVITED:
            raise GoalInviteRevokedError()
        if _invite_is_expired(participant):
            raise GoalInviteExpiredError()

        # Lock all active participant rows before counting.  The goal row lock
        # serialises concurrent accepts, but locking the participant rows as
        # well prevents phantom reads if rows are inserted by another
        # transaction that holds a different lock order.
        active_count = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, status=GoalParticipant.Status.ACTIVE)
            .count()
        )
        if active_count >= MAX_SHARED_GOAL_PARTICIPANTS:
            raise GoalParticipantLimitReachedError()

        now = timezone.now()
        participant.status = GoalParticipant.Status.ACTIVE
        participant.joined_at = now
        participant.left_at = None
        participant.save(update_fields=["status", "joined_at", "left_at", "updated_at"])
        _add_participant_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PARTICIPANT_JOINED,
            participant=participant,
        )
        return participant


def transfer_goal_ownership(*, actor, goal_id, target_participant_id=None, target_user_id=None):
    with transaction.atomic():
        goal = Goal.objects.select_for_update().filter(id=goal_id).first()
        if goal is None or not goal.is_shared:
            raise GoalNotFoundError()
        if goal.status != Goal.Status.ACTIVE:
            raise GoalInvalidTransitionError()

        current_owner = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, user=actor, role=GoalParticipant.Role.OWNER, status=GoalParticipant.Status.ACTIVE)
            .first()
        )
        if current_owner is None:
            raise GoalOwnerRequiredError()

        query = GoalParticipant.objects.select_for_update().filter(goal=goal)
        if target_participant_id is not None:
            target = query.filter(Q(id=target_participant_id) | Q(numeric_id=target_participant_id) if str(target_participant_id).isdigit() else Q(id=target_participant_id)).first()
        elif target_user_id is not None:
            target = query.filter(Q(user_id=target_user_id) | Q(user__numeric_id=target_user_id) if str(target_user_id).isdigit() else Q(user_id=target_user_id)).first()
        else:
            raise GoalValidationError()

        if target is None:
            raise GoalNotFoundError()
        if target.user_id == actor.id:
            raise GoalCannotTransferOwnershipToSelfError()
        if target.status != GoalParticipant.Status.ACTIVE:
            raise GoalInvalidParticipantStateError("Target must be an active participant.")

        now = timezone.now()
        current_owner.role = GoalParticipant.Role.PARTICIPANT
        current_owner.save(update_fields=["role", "updated_at"])

        target.role = GoalParticipant.Role.OWNER
        target.save(update_fields=["role", "updated_at"])

        goal.created_by = target.user
        goal.save(update_fields=["created_by", "updated_at"])

        _add_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.GOAL_OWNERSHIP_TRANSFERRED,
            metadata={
                "previous_owner_id": str(actor.id),
                "new_owner_id": str(target.user_id),
                "new_owner_name": target.user.name,
            },
        )
        return goal


def decline_invitation(*, actor, goal_id):
    with transaction.atomic():
        goal = Goal.objects.select_for_update().filter(id=goal_id).first()
        if goal is None:
            raise GoalNotFoundError()
        participant = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, user=actor)
            .first()
        )
        if participant is None:
            raise GoalNotFoundError()
        if participant.status == GoalParticipant.Status.DECLINED:
            return participant
        if participant.status != GoalParticipant.Status.INVITED:
            raise GoalInvalidParticipantStateError()
        if _invite_is_expired(participant):
            raise GoalInviteExpiredError()

        participant.status = GoalParticipant.Status.DECLINED
        participant.left_at = timezone.now()
        participant.save(update_fields=["status", "left_at", "updated_at"])
        _add_participant_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PARTICIPANT_DECLINED,
            participant=participant,
        )
        return participant


def revoke_invitation(*, actor, goal_id, user_id):
    with transaction.atomic():
        goal = _lock_for_manager(actor, goal_id)
        if goal.status in _TERMINAL_STATUSES:
            raise GoalInvalidTransitionError()
        participant = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, user_id=user_id)
            .first()
        )
        if participant is None:
            raise GoalNotFoundError()
        if participant.status == GoalParticipant.Status.REMOVED:
            return participant
        if participant.status != GoalParticipant.Status.INVITED:
            raise GoalInvalidParticipantStateError()

        participant.status = GoalParticipant.Status.REMOVED
        participant.left_at = timezone.now()
        participant.save(update_fields=["status", "left_at", "updated_at"])
        _add_participant_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PARTICIPANT_REMOVED,
            participant=participant,
        )
        return participant


def remove_participant(*, actor, goal_id, user_id=None, participant_id=None):
    with transaction.atomic():
        goal = _lock_for_manager(actor, goal_id)
        if goal.status in _TERMINAL_STATUSES:
            raise GoalInvalidTransitionError()
        query = GoalParticipant.objects.select_for_update().filter(goal=goal)
        if participant_id is not None:
            participant = query.filter(Q(id=participant_id) | Q(user_id=participant_id)).first()
        elif user_id is not None:
            participant = query.filter(Q(id=user_id) | Q(user_id=user_id)).first()
        else:
            raise GoalNotFoundError()
        if participant is None:
            raise GoalNotFoundError()
        if participant.role == GoalParticipant.Role.OWNER:
            raise GoalCannotRemoveOwnerError()
        if participant.status == GoalParticipant.Status.REMOVED:
            return participant
        if participant.status not in (
            GoalParticipant.Status.ACTIVE,
            GoalParticipant.Status.INVITED,
        ):
            raise GoalInvalidParticipantStateError()

        participant.status = GoalParticipant.Status.REMOVED
        participant.left_at = timezone.now()
        participant.save(update_fields=["status", "left_at", "updated_at"])
        _add_participant_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PARTICIPANT_REMOVED,
            participant=participant,
        )

        revoked_user_id = str(participant.user_id)
        goal_id_str = str(goal.id)

        def broadcast_revocation():
            try:
                from asgiref.sync import async_to_sync
                from channels.layers import get_channel_layer

                channel_layer = get_channel_layer()
                if channel_layer:
                    async_to_sync(channel_layer.group_send)(
                        f"goal_chat_{goal_id_str}",
                        {
                            "type": "chat_participant_revoked",
                            "user_id": revoked_user_id,
                        },
                    )
            except Exception as e:
                logger.warning("Failed to broadcast revocation via channel layer: %s", e)

        transaction.on_commit(broadcast_revocation)
        return participant


def leave_goal(*, actor, goal_id):
    with transaction.atomic():
        goal = Goal.objects.select_for_update().filter(id=goal_id).first()
        if goal is None:
            raise GoalNotFoundError()
        participant = (
            GoalParticipant.objects.select_for_update()
            .filter(goal=goal, user=actor)
            .first()
        )
        if participant is None:
            raise GoalNotFoundError()
        if participant.role == GoalParticipant.Role.OWNER:
            raise GoalOwnerCannotLeaveError()
        if participant.status == GoalParticipant.Status.LEFT:
            return participant
        if participant.status != GoalParticipant.Status.ACTIVE:
            raise GoalInvalidParticipantStateError()
        if goal.status in _TERMINAL_STATUSES:
            raise GoalInvalidTransitionError()

        participant.status = GoalParticipant.Status.LEFT
        participant.left_at = timezone.now()
        participant.save(update_fields=["status", "left_at", "updated_at"])
        _add_participant_event(
            goal,
            actor=actor,
            event_type=GoalEvent.EventType.PARTICIPANT_LEFT,
            participant=participant,
        )

        left_user_id = str(participant.user_id)
        goal_id_str = str(goal.id)

        def broadcast_leave():
            try:
                from asgiref.sync import async_to_sync
                from channels.layers import get_channel_layer

                channel_layer = get_channel_layer()
                if channel_layer:
                    async_to_sync(channel_layer.group_send)(
                        f"goal_chat_{goal_id_str}",
                        {
                            "type": "chat_participant_revoked",
                            "user_id": left_user_id,
                        },
                    )
            except Exception as e:
                logger.warning("Failed to broadcast leave revocation via channel layer: %s", e)

        transaction.on_commit(broadcast_leave)
        return participant


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
    is_shared=_UNSET,
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
        next_is_shared = goal.is_shared

        if is_shared is not _UNSET:
            if not is_shared and goal.is_shared:
                raise GoalValidationError("Shared goals cannot be converted back to individual goals.")
            next_is_shared = is_shared

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
        if next_is_shared != goal.is_shared:
            goal.is_shared = next_is_shared
            changed.append("is_shared")
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


def convert_goal_to_shared(*, actor, goal_id):
    """Converts an individual goal to a shared goal.

    This is a one-way operation. An already shared goal remains shared.
    """
    with transaction.atomic():
        goal = _lock_for_owner(actor, goal_id)
        if goal.status in _TERMINAL_STATUSES:
            raise GoalInvalidTransitionError()
        if not goal.is_shared:
            goal.is_shared = True
            goal.save(update_fields=["is_shared", "updated_at"])
            _add_event(
                goal,
                actor=actor,
                event_type=GoalEvent.EventType.UPDATED,
                metadata={"fields": ["is_shared"], "converted_to_shared": True},
            )
            try:
                from apps.analytics.events import EVENT_SHARED_GOAL_CREATED
                from apps.analytics.services import record_analytics_event
                record_analytics_event(event_name=EVENT_SHARED_GOAL_CREATED, user=actor)
            except Exception:
                pass
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


def goal_progress(goal, *, at=None, participant=None):
    participant = participant or _owner_participant(goal)
    today = _local_today(goal, at)
    paused_ranges = _paused_date_ranges(goal, at=at)
    check_ins = _check_ins_by_date(goal, participant=participant)
    current = _current_period_progress(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
        participant=participant,
    )
    week = _week_progress(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
        participant=participant,
    )
    return {
        "current_period": current,
        "week_progress": week,
        "consistency_percent": _consistency_percent(
            goal,
            today=today,
            paused_ranges=paused_ranges,
            check_ins=check_ins,
            participant=participant,
        ),
    }


def individual_progress(goal, participant, *, at=None):
    return goal_progress(goal, at=at, participant=participant)


def collective_progress(goal, *, at=None):
    today = _local_today(goal, at)
    paused_ranges = _paused_date_ranges(goal, at=at)
    members = list(goal.participants.all())
    current = _collective_current_period(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        members=members,
    )
    week = _collective_week_progress(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        members=members,
    )
    return {
        "current_period": current,
        "week_progress": week,
    }


def goal_streak(goal, *, at=None, participant=None):
    participant = participant or _owner_participant(goal)
    effective_at = _streak_effective_at(participant, at=at)
    today = _local_today(goal, effective_at)
    paused_ranges = _paused_date_ranges(goal, at=effective_at)
    check_ins = _check_ins_by_date(goal, participant=participant)
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        return _n_per_period_streak(
            goal,
            today=today,
            paused_ranges=paused_ranges,
            check_ins=check_ins,
            participant=participant,
        )
    return _daily_weekday_streak(
        goal,
        today=today,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
        participant=participant,
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
        goal = _lock_for_check_in(actor, goal_id)
        if goal.status != Goal.Status.ACTIVE:
            raise GoalInvalidTransitionError()
        _validate_check_in_payload(goal, period_date, status, value, at=at)

        participant = _active_participant_for_actor(goal, actor)
        existing = (
            GoalCheckIn.objects.select_for_update()
            .filter(goal=goal, participant=participant, period_date=period_date)
            .first()
        )
        if existing is None:
            if require_existing:
                raise GoalInvalidCheckInError()
            try:
                check_in = GoalCheckIn.objects.create(
                    goal=goal,
                    participant=participant,
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
                    participant=participant,
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
                    "participant_id": str(participant.id),
                    "status": check_in.status,
                    "value": check_in.value,
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
            "participant_id": str(check_in.participant_id),
            "status": check_in.status,
            "value": check_in.value,
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


def _invite_is_expired(participant, *, at=None):
    if participant is None:
        return True
    now = _now(at)
    if participant.invitation_expires_at is not None:
        return now > participant.invitation_expires_at
    if participant.invited_at is not None:
        return now > (participant.invited_at + _INVITE_TTL)
    return True


def _owner_participant(goal):
    if hasattr(goal, "_prefetched_objects_cache") and "participants" in goal._prefetched_objects_cache:
        active_owners = [
            p for p in goal.participants.all()
            if p.role == GoalParticipant.Role.OWNER and p.status == GoalParticipant.Status.ACTIVE
        ]
        if active_owners:
            return sorted(active_owners, key=lambda p: p.created_at)[0]
        for p in goal.participants.all():
            if p.user_id == goal.created_by_id:
                return p
        return None
    owner = (
        GoalParticipant.objects.filter(
            goal=goal,
            role=GoalParticipant.Role.OWNER,
            status=GoalParticipant.Status.ACTIVE,
        )
        .order_by("created_at")
        .first()
    )
    if owner is None:
        owner = GoalParticipant.objects.filter(
            goal=goal,
            user_id=goal.created_by_id,
        ).first()
    return owner


def _active_participant_for_actor(goal, actor):
    participant = (
        GoalParticipant.objects.select_for_update()
        .filter(
            goal=goal,
            user=actor,
            status=GoalParticipant.Status.ACTIVE,
        )
        .first()
    )
    if participant is None:
        raise GoalNotFoundError()
    if participant.user_id != actor.id:
        raise GoalNotFoundError()
    return participant


def _lookup_goal_for_update(goal_id):
    if isinstance(goal_id, int) or (isinstance(goal_id, str) and str(goal_id).isdigit()):
        return Goal.objects.select_for_update().filter(numeric_id=int(goal_id)).first()
    try:
        val = uuid.UUID(str(goal_id))
        return Goal.objects.select_for_update().filter(id=val).first()
    except (ValueError, AttributeError):
        return None


def _lock_for_owner(actor, goal_id):
    return _lock_for_manager(actor, goal_id)


def _lock_for_manager(actor, goal_id):
    goal = _lookup_goal_for_update(goal_id)
    if goal is None or not can_manage_goal(actor, goal):
        raise GoalNotFoundError()
    return goal


def _lock_for_check_in(actor, goal_id):
    goal = _lookup_goal_for_update(goal_id)
    if goal is None:
        raise GoalNotFoundError()
    participant = (
        GoalParticipant.objects.select_for_update()
        .filter(
            goal=goal,
            user=actor,
            status=GoalParticipant.Status.ACTIVE,
        )
        .first()
    )
    if participant is None:
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


def _local_date(goal, instant):
    if instant is None:
        return None
    return instant.astimezone(ZoneInfo(goal.timezone)).date()


def _membership_start_date(goal, participant):
    if participant.role == GoalParticipant.Role.OWNER:
        return goal.start_date
    if participant.joined_at is not None:
        return _local_date(goal, participant.joined_at)
    if participant.invited_at is not None:
        return _local_date(goal, participant.invited_at)
    return goal.start_date


def _membership_end_date(goal, participant):
    if participant.status in _ENDED_MEMBERSHIP_STATUSES and participant.left_at:
        return _local_date(goal, participant.left_at)
    return None


def _participant_covers_period(goal, participant, period_date):
    if participant is None:
        return True
    if participant.status == GoalParticipant.Status.INVITED:
        return False
    if participant.status == GoalParticipant.Status.DECLINED:
        return False
    start = _membership_start_date(goal, participant)
    if start is not None and period_date < start:
        return False
    end = _membership_end_date(goal, participant)
    if end is not None and period_date > end:
        return False
    if participant.status == GoalParticipant.Status.ACTIVE:
        return True
    if participant.status in _ENDED_MEMBERSHIP_STATUSES:
        return True
    return False


def _is_member_expected_on(goal, participant, period_date, *, paused_ranges):
    if not _is_period_expected(goal, period_date, paused_ranges=paused_ranges):
        return False
    return _participant_covers_period(goal, participant, period_date)


def _streak_effective_at(participant, *, at=None):
    if participant is None:
        return at
    if (
        participant.status in _ENDED_MEMBERSHIP_STATUSES
        and participant.left_at is not None
    ):
        if at is None or participant.left_at < at:
            return participant.left_at
    return at


def _check_ins_by_date(goal, participant=None):
    rows = goal.check_ins.all()
    if participant is not None:
        rows = [row for row in rows if row.participant_id == participant.id]
    return {row.period_date: row for row in rows}


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


def _current_period_progress(
    goal, *, today, paused_ranges, check_ins, participant=None
):
    today_expected = _is_member_expected_on(
        goal, participant, today, paused_ranges=paused_ranges
    )
    today_row = check_ins.get(today)
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        week_start = _iso_week_start(today)
        required = (
            goal.times_per_period
            if _week_has_expected_day_for_member(
                goal, week_start, paused_ranges, participant
            )
            else 0
        )
        current = {
            "required": required,
            "completed": _week_successful_count(
                goal,
                week_start,
                paused_ranges=paused_ranges,
                check_ins=check_ins,
                participant=participant,
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


def _week_progress(goal, *, today, paused_ranges, check_ins, participant=None):
    week_start = _iso_week_start(today)
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        required = (
            goal.times_per_period
            if _week_has_expected_day_for_member(
                goal, week_start, paused_ranges, participant
            )
            else 0
        )
        return {
            "required": required,
            "completed": _week_successful_count(
                goal,
                week_start,
                paused_ranges=paused_ranges,
                check_ins=check_ins,
                participant=participant,
            ),
        }
    required = 0
    completed = 0
    cursor = week_start
    while cursor <= today:
        if _is_member_expected_on(
            goal, participant, cursor, paused_ranges=paused_ranges
        ):
            required += 1
            if _is_successful(goal, check_ins.get(cursor)):
                completed += 1
        cursor += timedelta(days=1)
    return {"required": required, "completed": completed}


def _consistency_percent(
    goal, *, today, paused_ranges, check_ins, participant=None
):
    window_end = today - timedelta(days=1)
    window_start = max(goal.start_date, today - timedelta(days=27))
    if participant is not None:
        membership_start = _membership_start_date(goal, participant)
        if membership_start is not None:
            window_start = max(window_start, membership_start)
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
            if not _week_has_expected_day_for_member(
                goal, week, paused_ranges, participant
            ):
                week += timedelta(days=7)
                continue
            expected += goal.times_per_period
            completed = _week_successful_count(
                goal,
                week,
                paused_ranges=paused_ranges,
                check_ins=check_ins,
                participant=participant,
            )
            successful += min(completed, goal.times_per_period)
            week += timedelta(days=7)
    else:
        cursor = window_start
        while cursor <= window_end:
            if _is_member_expected_on(
                goal, participant, cursor, paused_ranges=paused_ranges
            ):
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


def _week_has_expected_day_for_member(goal, week_start, paused_ranges, participant):
    for offset in range(7):
        day = week_start + timedelta(days=offset)
        if _is_member_expected_on(
            goal, participant, day, paused_ranges=paused_ranges
        ):
            return True
    return False


def _week_successful_count(
    goal, week_start, *, paused_ranges, check_ins, participant=None
):
    completed = 0
    for offset in range(7):
        day = week_start + timedelta(days=offset)
        if day < goal.start_date:
            continue
        if goal.end_date is not None and day > goal.end_date:
            continue
        if participant is not None and not _participant_covers_period(
            goal, participant, day
        ):
            continue
        if _is_successful(goal, check_ins.get(day)):
            completed += 1
    return completed


def _daily_weekday_streak(
    goal, *, today, paused_ranges, check_ins, participant=None
):
    cursor = today
    if not (
        _is_member_expected_on(goal, participant, today, paused_ranges=paused_ranges)
        and _is_successful(goal, check_ins.get(today))
    ):
        cursor = today - timedelta(days=1)
    streak = 0
    while cursor >= goal.start_date:
        if not _is_member_expected_on(
            goal, participant, cursor, paused_ranges=paused_ranges
        ):
            cursor -= timedelta(days=1)
            continue
        if not _is_successful(goal, check_ins.get(cursor)):
            break
        streak += 1
        cursor -= timedelta(days=1)
    return streak


def _n_per_period_streak(
    goal, *, today, paused_ranges, check_ins, participant=None
):
    yesterday = today - timedelta(days=1)
    current_week = _iso_week_start(today)
    current_completed = _week_successful_count(
        goal,
        current_week,
        paused_ranges=paused_ranges,
        check_ins=check_ins,
        participant=participant,
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
        if not _week_has_expected_day_for_member(
            goal, week, paused_ranges, participant
        ):
            week -= timedelta(days=7)
            continue
        completed = _week_successful_count(
            goal,
            week,
            paused_ranges=paused_ranges,
            check_ins=check_ins,
            participant=participant,
        )
        if completed >= goal.times_per_period:
            streak += 1
            week -= timedelta(days=7)
            continue
        break
    return streak


def _expected_members_for_period(goal, period_date, *, paused_ranges, members):
    return [
        member
        for member in members
        if _is_member_expected_on(
            goal, member, period_date, paused_ranges=paused_ranges
        )
    ]


def _member_check_in(goal, member, period_date):
    return next(
        (
            row
            for row in goal.check_ins.all()
            if row.participant_id == member.id and row.period_date == period_date
        ),
        None,
    )


def _collective_current_period(goal, *, today, paused_ranges, members):
    expected = _expected_members_for_period(
        goal, today, paused_ranges=paused_ranges, members=members
    )
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        week_start = _iso_week_start(today)
        expected_count = sum(
            1
            for member in members
            if _week_has_expected_day_for_member(
                goal, week_start, paused_ranges, member
            )
        )
        required = goal.times_per_period * expected_count
        completed = 0
        value_sum = 0
        for member in members:
            if not _week_has_expected_day_for_member(
                goal, week_start, paused_ranges, member
            ):
                continue
            check_ins = _check_ins_by_date(goal, participant=member)
            completed += _week_successful_count(
                goal,
                week_start,
                paused_ranges=paused_ranges,
                check_ins=check_ins,
                participant=member,
            )
            if goal.tracking_kind == Goal.TrackingKind.COUNT:
                for offset in range(7):
                    day = week_start + timedelta(days=offset)
                    row = check_ins.get(day)
                    if row is not None and row.value is not None:
                        value_sum += row.value
        result = {
            "required": required,
            "completed": completed,
            "required_participants": expected_count,
            "completed_participants": None,
        }
        if goal.tracking_kind == Goal.TrackingKind.COUNT:
            result["value_sum"] = value_sum
            result["target_sum"] = (goal.target_value or 0) * expected_count
        return result

    completed_members = [
        member
        for member in expected
        if _is_successful(goal, _member_check_in(goal, member, today))
    ]
    result = {
        "required": len(expected),
        "completed": len(completed_members),
        "required_participants": len(expected),
        "completed_participants": len(completed_members),
    }
    if goal.tracking_kind == Goal.TrackingKind.COUNT:
        value_sum = 0
        for member in expected:
            row = _member_check_in(goal, member, today)
            if row is not None and _is_successful(goal, row) and row.value is not None:
                value_sum += row.value
        result["value_sum"] = value_sum
        result["target_sum"] = (goal.target_value or 0) * len(expected)
        result["required"] = (goal.target_value or 0) * len(expected)
        result["completed"] = value_sum
    return result


def _collective_week_progress(goal, *, today, paused_ranges, members):
    week_start = _iso_week_start(today)
    if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD:
        return _collective_current_period(
            goal,
            today=today,
            paused_ranges=paused_ranges,
            members=members,
        )

    required = 0
    completed = 0
    cursor = week_start
    while cursor <= today:
        expected = _expected_members_for_period(
            goal, cursor, paused_ranges=paused_ranges, members=members
        )
        required += len(expected)
        completed += sum(
            1
            for member in expected
            if _is_successful(goal, _member_check_in(goal, member, cursor))
        )
        cursor += timedelta(days=1)
    return {
        "required": required,
        "completed": completed,
        "required_participants": required,
        "completed_participants": completed,
    }


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
    GoalEvent.EventType.PARTICIPANT_INVITED: "goal.participant.invited",
    GoalEvent.EventType.PARTICIPANT_JOINED: "goal.participant.joined",
    GoalEvent.EventType.PARTICIPANT_DECLINED: "goal.participant.declined",
    GoalEvent.EventType.PARTICIPANT_LEFT: "goal.participant.left",
    GoalEvent.EventType.PARTICIPANT_REMOVED: "goal.participant.removed",
    GoalEvent.EventType.GOAL_OWNERSHIP_TRANSFERRED: "goal.ownership_transferred",
    GoalEvent.EventType.PARTICIPANT_REINVITED: "goal.participant.reinvited",
}


def _iso(value):
    if value is None:
        return None
    return value.isoformat()


def _goal_outbox_payload(goal, domain_event, check_in=None, participant=None):
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
            payload["participant_id"] = str(check_in.participant_id)
    elif event_type in (
        GoalEvent.EventType.PARTICIPANT_INVITED,
        GoalEvent.EventType.PARTICIPANT_JOINED,
        GoalEvent.EventType.PARTICIPANT_DECLINED,
        GoalEvent.EventType.PARTICIPANT_LEFT,
        GoalEvent.EventType.PARTICIPANT_REMOVED,
    ):
        row = participant
        payload["participant_id"] = str(row.id) if row is not None else None
        payload["target_user_id"] = (
            str(row.user_id) if row is not None else None
        )
        payload["participant_role"] = row.role if row is not None else None
        payload["participant_status"] = row.status if row is not None else None
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

    try:
        from apps.notifications.services import sync_goal_reminders
        sync_goal_reminders(goal)
    except Exception as notif_exc:
        logging.getLogger("promise").warning("Failed to sync goal reminders: %s", notif_exc)

    # Server-Side Authoritative Analytics Event Emission
    try:
        from apps.analytics.events import (
            EVENT_FIRST_CHECKIN_COMPLETED,
            EVENT_FIRST_GOAL_CREATED,
            EVENT_FIRST_SHARED_GOAL_CREATED,
            EVENT_GOAL_CANCELLED,
            EVENT_GOAL_CHECKED_IN,
            EVENT_GOAL_COMPLETED,
            EVENT_GOAL_CREATED,
            EVENT_GOAL_PAUSED,
            EVENT_GOAL_RESUMED,
            EVENT_OWNERSHIP_TRANSFERRED,
            EVENT_SHARED_GOAL_CREATED,
        )
        from apps.analytics.services import record_analytics_event

        analytics_map = {
            GoalEvent.EventType.CREATED: EVENT_GOAL_CREATED,
            GoalEvent.EventType.CHECKIN_RECORDED: EVENT_GOAL_CHECKED_IN,
            GoalEvent.EventType.COMPLETED: EVENT_GOAL_COMPLETED,
            GoalEvent.EventType.PAUSED: EVENT_GOAL_PAUSED,
            GoalEvent.EventType.RESUMED: EVENT_GOAL_RESUMED,
            GoalEvent.EventType.CANCELLED: EVENT_GOAL_CANCELLED,
            GoalEvent.EventType.GOAL_OWNERSHIP_TRANSFERRED: EVENT_OWNERSHIP_TRANSFERRED,
        }
        an_event = analytics_map.get(event_type)
        if an_event:
            props = {"is_shared": goal.is_shared, "recurrence_kind": goal.recurrence_kind}
            record_analytics_event(event_name=an_event, user=actor, properties=props)

            # Milestones & Special Funnels
            if event_type == GoalEvent.EventType.CREATED:
                if goal.is_shared:
                    record_analytics_event(event_name=EVENT_SHARED_GOAL_CREATED, user=actor, properties=props)
                    if Goal.objects.filter(created_by=actor, is_shared=True).count() == 1:
                        record_analytics_event(event_name=EVENT_FIRST_SHARED_GOAL_CREATED, user=actor, properties=props)
                if Goal.objects.filter(created_by=actor).count() == 1:
                    record_analytics_event(event_name=EVENT_FIRST_GOAL_CREATED, user=actor, properties=props)
            elif event_type == GoalEvent.EventType.CHECKIN_RECORDED:
                if GoalCheckIn.objects.filter(created_by=actor).count() == 1:
                    record_analytics_event(event_name=EVENT_FIRST_CHECKIN_COMPLETED, user=actor)
    except Exception as exc:  # noqa: BLE001
        logging.getLogger("promise").warning("Failed to emit analytics event for goal event %s: %s", event_type, exc)

    return domain_event


def _add_participant_event(goal, *, actor, event_type, participant):
    metadata = {
        "participant_id": str(participant.id),
        "target_user_id": str(participant.user_id),
        "role": participant.role,
        "status": participant.status,
    }
    domain_event = GoalEvent.objects.create(
        goal=goal,
        actor=actor,
        event_type=event_type,
        metadata=metadata,
    )
    record_outbox_event(
        aggregate_type="goal",
        aggregate_id=goal.id,
        event_type=_OUTBOX_EVENT_TYPES[event_type],
        payload=_goal_outbox_payload(
            goal, domain_event, participant=participant
        ),
        occurred_at=domain_event.created_at,
    )

    # Server-Side Authoritative Analytics Event Emission
    try:
        from apps.analytics.events import (
            EVENT_FIRST_INVITATION_ACCEPTED,
            EVENT_FIRST_INVITATION_SENT,
            EVENT_PARTICIPANT_INVITED,
            EVENT_PARTICIPANT_JOINED,
            EVENT_PARTICIPANT_LEFT,
            EVENT_PARTICIPANT_REMOVED,
        )
        from apps.analytics.services import record_analytics_event

        part_map = {
            GoalEvent.EventType.PARTICIPANT_INVITED: EVENT_PARTICIPANT_INVITED,
            GoalEvent.EventType.PARTICIPANT_JOINED: EVENT_PARTICIPANT_JOINED,
            GoalEvent.EventType.PARTICIPANT_LEFT: EVENT_PARTICIPANT_LEFT,
            GoalEvent.EventType.PARTICIPANT_REMOVED: EVENT_PARTICIPANT_REMOVED,
        }
        an_event = part_map.get(event_type)
        if an_event:
            record_analytics_event(event_name=an_event, user=actor, properties={"role": participant.role})

            if event_type == GoalEvent.EventType.PARTICIPANT_INVITED:
                if GoalEvent.objects.filter(actor=actor, event_type=GoalEvent.EventType.PARTICIPANT_INVITED).count() == 1:
                    record_analytics_event(event_name=EVENT_FIRST_INVITATION_SENT, user=actor)
            elif event_type == GoalEvent.EventType.PARTICIPANT_JOINED:
                if GoalEvent.objects.filter(actor=actor, event_type=GoalEvent.EventType.PARTICIPANT_JOINED).count() == 1:
                    record_analytics_event(event_name=EVENT_FIRST_INVITATION_ACCEPTED, user=actor)
    except Exception as exc:  # noqa: BLE001
        logging.getLogger("promise").warning("Failed to emit analytics event for participant event %s: %s", event_type, exc)

    return domain_event


def calculate_collective_progress(goal, *, as_of_date=None):
    today = as_of_date or timezone.now().astimezone(ZoneInfo(goal.timezone)).date()
    paused_ranges = _paused_date_ranges(goal)
    members = list(goal.participants.filter(status=GoalParticipant.Status.ACTIVE))
    return {
        "current_period": _collective_current_period(
            goal,
            today=today,
            paused_ranges=paused_ranges,
            members=members,
        ),
        "week_progress": _collective_week_progress(
            goal,
            today=today,
            paused_ranges=paused_ranges,
            members=members,
        ),
    }


def send_chat_message(*, sender, goal_id, body):
    goal = Goal.objects.filter(id=goal_id).first()
    if goal is None or not can_send_chat(sender, goal):
        raise GoalNotFoundError()

    clean_body = body.strip() if body else ""
    if not clean_body or len(clean_body) > 2000:
        raise GoalChatInvalidMessageError()

    with transaction.atomic():
        msg = ChatMessage.objects.create(
            goal=goal,
            sender=sender,
            body=clean_body,
        )
        now = msg.created_at
        read_state, created = GoalChatReadState.objects.select_for_update().get_or_create(
            goal=goal,
            user=sender,
            defaults={
                "last_read_message": msg,
                "last_read_at": now,
            },
        )
        if not created:
            read_state.last_read_message = msg
            read_state.last_read_at = now
            read_state.save(update_fields=["last_read_message", "last_read_at", "updated_at"])

        record_outbox_event(
            aggregate_type="goal",
            aggregate_id=goal.id,
            event_type="goal.chat.message_created",
            payload={
                "message_id": str(msg.id),
                "goal_id": str(goal.id),
                "sender_id": str(sender.id),
                "created_at": msg.created_at.isoformat(),
            },
            occurred_at=msg.created_at,
        )

        msg_payload = {
            "id": str(msg.id),
            "sender": {
                "id": str(sender.id),
                "name": sender.name or sender.email.split("@")[0],
            },
            "body": msg.body,
            "created_at": msg.created_at.isoformat(),
        }
        goal_id_str = str(goal.id)

        def broadcast_message():
            try:
                from asgiref.sync import async_to_sync
                from channels.layers import get_channel_layer

                channel_layer = get_channel_layer()
                if channel_layer:
                    async_to_sync(channel_layer.group_send)(
                        f"goal_chat_{goal_id_str}",
                        {
                            "type": "chat_message_created",
                            "message": msg_payload,
                        },
                    )
            except Exception as e:
                logger.warning("Failed to broadcast chat message via channel layer: %s", e)

        transaction.on_commit(broadcast_message)
        return msg


def list_chat_messages(*, viewer, goal_id, limit=50, before_id=None, before_created_at=None):
    goal = Goal.objects.filter(id=goal_id).first()
    if goal is None or not can_view_chat(viewer, goal):
        raise GoalNotFoundError()

    queryset = goal.chat_messages.select_related("sender").order_by("created_at", "id")
    if before_created_at is not None and before_id is not None:
        queryset = queryset.filter(
            models.Q(created_at__lt=before_created_at)
            | models.Q(created_at=before_created_at, id__lt=before_id)
        )
    return list(queryset[:limit])


def search_chat_messages(*, viewer, goal_id, query, limit=20):
    goal = Goal.objects.filter(id=goal_id).first()
    if goal is None or not can_view_chat(viewer, goal):
        raise GoalNotFoundError()

    clean_query = " ".join(query.strip().split())
    if not clean_query:
        return []

    queryset = (
        goal.chat_messages.filter(body__icontains=clean_query)
        .select_related("sender")
        .order_by("-created_at")[:limit]
    )
    return list(queryset)


def mark_chat_read(*, viewer, goal_id, last_read_message_id):
    goal = Goal.objects.filter(id=goal_id).first()
    if goal is None or not can_update_read_state(viewer, goal):
        raise GoalNotFoundError()

    target_message = ChatMessage.objects.filter(id=last_read_message_id, goal=goal).first()
    if target_message is None:
        raise GoalChatInvalidCursorError()

    with transaction.atomic():
        now = timezone.now()
        read_state = (
            GoalChatReadState.objects.select_for_update()
            .filter(goal=goal, user=viewer)
            .first()
        )
        if read_state is not None and read_state.last_read_message is not None:
            existing_msg = read_state.last_read_message
            if (target_message.created_at, target_message.id) < (existing_msg.created_at, existing_msg.id):
                return read_state
        if read_state is None:
            read_state = GoalChatReadState.objects.create(
                goal=goal,
                user=viewer,
                last_read_message=target_message,
                last_read_at=now,
            )
        else:
            read_state.last_read_message = target_message
            read_state.last_read_at = now
            read_state.save(update_fields=["last_read_message", "last_read_at", "updated_at"])
        return read_state


def get_chat_summary(*, viewer, goal_id):
    goal = Goal.objects.filter(id=goal_id).first()
    if goal is None or not can_view_chat(viewer, goal):
        raise GoalNotFoundError()

    read_state = GoalChatReadState.objects.filter(goal=goal, user=viewer).first()
    messages_query = goal.chat_messages.exclude(sender=viewer)
    if read_state is not None and read_state.last_read_message is not None:
        last_msg = read_state.last_read_message
        unread_count = messages_query.filter(
            models.Q(created_at__gt=last_msg.created_at)
            | models.Q(created_at=last_msg.created_at, id__gt=last_msg.id)
        ).count()
    else:
        unread_count = messages_query.count()

    latest_message = (
        goal.chat_messages.select_related("sender").order_by("-created_at", "-id").first()
    )
    return {
        "unread_count": unread_count,
        "latest_message": latest_message,
    }


ACTIVITY_EVENT_TYPES = [
    GoalEvent.EventType.CREATED,
    GoalEvent.EventType.PAUSED,
    GoalEvent.EventType.RESUMED,
    GoalEvent.EventType.COMPLETED,
    GoalEvent.EventType.CANCELLED,
    GoalEvent.EventType.CHECKIN_RECORDED,
    GoalEvent.EventType.CHECKIN_UPDATED,
    GoalEvent.EventType.PARTICIPANT_JOINED,
    GoalEvent.EventType.PARTICIPANT_LEFT,
    GoalEvent.EventType.PARTICIPANT_REMOVED,
    GoalEvent.EventType.GOAL_OWNERSHIP_TRANSFERRED,
    GoalEvent.EventType.PARTICIPANT_REINVITED,
]


def can_view_activity(user, goal):
    if goal is None or not is_shared_goal(goal):
        return False
    membership = get_participant(goal, user)
    return membership is not None and membership.status == GoalParticipant.Status.ACTIVE


def _format_activity_summary(goal, event, target_users_by_id):
    actor_name = event.actor.name if event.actor else "A participant"
    ev_type = event.event_type
    meta = event.metadata or {}

    if ev_type in (GoalEvent.EventType.CHECKIN_RECORDED, GoalEvent.EventType.CHECKIN_UPDATED):
        status = meta.get("status") or meta.get("checkin_status")
        if status == "SKIPPED":
            return f"{actor_name} skipped today's practice"
        if goal.tracking_kind == Goal.TrackingKind.COUNT:
            value = meta.get("value")
            unit = goal.target_unit or "units"
            if value is not None:
                return f"{actor_name} checked in with {value} {unit}"
            return f"{actor_name} recorded progress"
        return f"{actor_name} completed today's practice"

    if ev_type == GoalEvent.EventType.PARTICIPANT_JOINED:
        return f"{actor_name} joined the goal"

    if ev_type == GoalEvent.EventType.PARTICIPANT_LEFT:
        return f"{actor_name} left the goal"

    if ev_type == GoalEvent.EventType.PARTICIPANT_REMOVED:
        target_id = str(meta.get("target_user_id") or "")
        target_user = target_users_by_id.get(target_id)
        target_name = target_user.name if target_user else "A participant"
        return f"{target_name} was removed by {actor_name}"

    if ev_type == GoalEvent.EventType.GOAL_OWNERSHIP_TRANSFERRED:
        new_name = meta.get("new_owner_name") or "another member"
        return f"{actor_name} transferred ownership to {new_name}"

    if ev_type == GoalEvent.EventType.PARTICIPANT_REINVITED:
        reinvited_name = meta.get("reinvited_user_name") or "A participant"
        return f"{actor_name} re-invited {reinvited_name}"

    if ev_type == GoalEvent.EventType.COMPLETED:
        return f"{actor_name} marked the goal as completed"

    if ev_type == GoalEvent.EventType.PAUSED:
        return f"{actor_name} paused the goal"

    if ev_type == GoalEvent.EventType.RESUMED:
        return f"{actor_name} resumed the goal"

    if ev_type == GoalEvent.EventType.CANCELLED:
        return f"{actor_name} cancelled the goal"

    if ev_type == GoalEvent.EventType.CREATED:
        return f"{actor_name} created the goal"

    return f"Activity recorded by {actor_name}"


def get_goal_activity(*, viewer, goal_id, limit=20, before_created_at=None, before_id=None):
    goal = Goal.objects.filter(id=goal_id).first()
    if goal is None or not can_view_activity(viewer, goal):
        raise GoalNotFoundError()

    limit = max(1, min(limit, 100))
    queryset = (
        GoalEvent.objects.filter(
            goal=goal,
            event_type__in=ACTIVITY_EVENT_TYPES,
        )
        .select_related("actor")
        .order_by("-created_at", "-id")
    )

    if before_created_at and before_id:
        queryset = queryset.filter(
            models.Q(created_at__lt=before_created_at)
            | models.Q(created_at=before_created_at, id__lt=before_id)
        )
    elif before_created_at:
        queryset = queryset.filter(created_at__lt=before_created_at)

    events = list(queryset[:limit])

    # Collect any target_user_ids to resolve names in a single batch query
    target_user_ids = set()
    for ev in events:
        if ev.metadata and "target_user_id" in ev.metadata and ev.metadata["target_user_id"]:
            try:
                target_user_ids.add(uuid.UUID(str(ev.metadata["target_user_id"])))
            except Exception:
                pass

    target_users_by_id = {}
    if target_user_ids:
        User = get_user_model()
        for u in User.objects.filter(id__in=target_user_ids):
            target_users_by_id[str(u.id)] = u

    # Build activity items
    items = []
    for ev in events:
        summary = _format_activity_summary(goal, ev, target_users_by_id)
        target_user = None
        if ev.metadata and "target_user_id" in ev.metadata:
            target_user = target_users_by_id.get(str(ev.metadata["target_user_id"]))

        period_date = None
        if ev.metadata and "period_date" in ev.metadata and ev.metadata["period_date"]:
            period_date = ev.metadata["period_date"]

        items.append({
            "id": ev.id,
            "event_type": ev.event_type,
            "actor": ev.actor,
            "target_user": target_user,
            "summary": summary,
            "period_date": period_date,
            "created_at": ev.created_at,
        })
    return items


def shared_goal_group_summary(goal):
    """Calculates deterministic collective summary for the current day and period."""
    if not goal.is_shared:
        return None

    if hasattr(goal, "_prefetched_objects_cache") and "participants" in goal._prefetched_objects_cache:
        active_participants = [p for p in goal.participants.all() if p.status == GoalParticipant.Status.ACTIVE]
    else:
        active_participants = list(goal.participants.filter(status=GoalParticipant.Status.ACTIVE))

    active_count = len(active_participants)
    if active_count == 0:
        return {
            "active_participants_count": 0,
            "today_completed_count": 0,
            "today_completion_rate": 0.0,
            "current_period_completed_count": 0,
            "current_period_target_count": 0,
            "current_period_completion_rate": 0.0,
            "headline": "No active participants",
        }

    active_ids = {p.id for p in active_participants}
    tz = ZoneInfo(goal.timezone or "Asia/Kolkata")
    today = timezone.now().astimezone(tz).date()

    # Current period (week) bounds
    start_of_week = today - timedelta(days=today.weekday())
    end_of_week = start_of_week + timedelta(days=6)

    if hasattr(goal, "_prefetched_objects_cache") and "check_ins" in goal._prefetched_objects_cache:
        all_checkins = list(goal.check_ins.all())
        today_completed_count = sum(
            1 for c in all_checkins
            if c.period_date == today and c.status == GoalCheckIn.Status.COMPLETED and (c.participant_id in active_ids or (c.participant and c.participant.status == GoalParticipant.Status.ACTIVE))
        )
        week_checkins = [
            c for c in all_checkins
            if start_of_week <= c.period_date <= end_of_week and c.status == GoalCheckIn.Status.COMPLETED and (c.participant_id in active_ids or (c.participant and c.participant.status == GoalParticipant.Status.ACTIVE))
        ]
        week_completed_count = len(week_checkins)
        week_sum_value = sum(c.value or 0 for c in week_checkins)
    else:
        today_completed_count = goal.check_ins.filter(
            period_date=today,
            status=GoalCheckIn.Status.COMPLETED,
            participant__status=GoalParticipant.Status.ACTIVE,
        ).count()
        week_qs = goal.check_ins.filter(
            period_date__gte=start_of_week,
            period_date__lte=end_of_week,
            status=GoalCheckIn.Status.COMPLETED,
            participant__status=GoalParticipant.Status.ACTIVE,
        )
        week_completed_count = week_qs.count()
        week_sum_value = week_qs.aggregate(models.Sum("value"))["value__sum"] or 0

    today_rate = round(today_completed_count / active_count, 2)

    if goal.tracking_kind == Goal.TrackingKind.COUNT:
        total_value = week_sum_value
        expected_per_member = (goal.target_value or 1) * (goal.times_per_period if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD else (len(goal.weekdays) if goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS else 7))
        target_value = expected_per_member * active_count
        period_rate = round(min(1.0, total_value / target_value), 2) if target_value > 0 else 0.0
        unit = goal.target_unit or "units"
        headline = f"{total_value} of {target_value} {unit} this week"
        completed_metric = total_value
        target_metric = target_value
    else:
        completed_metric = week_completed_count
        expected_per_member = goal.times_per_period if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD else (len(goal.weekdays) if goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS else 7)
        target_metric = expected_per_member * active_count
        period_rate = round(min(1.0, completed_metric / target_metric), 2) if target_metric > 0 else 0.0
        pct = int(period_rate * 100)
        headline = f"{today_completed_count} of {active_count} completed today • {pct}% group weekly pace"

    return {
        "active_participants_count": active_count,
        "today_completed_count": today_completed_count,
        "today_completion_rate": today_rate,
        "current_period_completed_count": completed_metric,
        "current_period_target_count": target_metric,
        "current_period_completion_rate": period_rate,
        "headline": headline,
    }


def shared_goal_milestones(goal):
    """Calculates deterministic lightweight milestone status without extra persistence."""
    if not goal.is_shared:
        return []

    if hasattr(goal, "_prefetched_objects_cache") and "check_ins" in goal._prefetched_objects_cache:
        completed_checkins = [c for c in goal.check_ins.all() if c.status == GoalCheckIn.Status.COMPLETED]
        total_completed = len(completed_checkins)
        total_count_value = sum(c.value or 0 for c in completed_checkins) if goal.tracking_kind == Goal.TrackingKind.COUNT else 0
        distinct_weeks = len({c.period_date.isocalendar()[:2] for c in completed_checkins})
    else:
        total_completed = goal.check_ins.filter(
            status=GoalCheckIn.Status.COMPLETED,
        ).count()

        total_count_value = 0
        if goal.tracking_kind == Goal.TrackingKind.COUNT:
            total_count_value = goal.check_ins.filter(
                status=GoalCheckIn.Status.COMPLETED,
            ).aggregate(models.Sum("value"))["value__sum"] or 0

        checkin_dates = list(
            goal.check_ins.filter(status=GoalCheckIn.Status.COMPLETED)
            .values_list("period_date", flat=True)
        )
        distinct_weeks = len({d.isocalendar()[:2] for d in checkin_dates})

    milestones_def = [
        {
            "key": "FIRST_SHARED_WEEK",
            "title": "First Week Together",
            "description": "Completed practice check-ins across the first week.",
            "achieved": distinct_weeks >= 1,
            "target": 1,
            "current": distinct_weeks,
        },
        {
            "key": "PRACTICES_10",
            "title": "10 Practices Completed",
            "description": "Your group has recorded 10 completed practices.",
            "achieved": total_completed >= 10,
            "target": 10,
            "current": total_completed,
        },
        {
            "key": "PRACTICES_30",
            "title": "30 Practices Completed",
            "description": "Your group has recorded 30 completed practices.",
            "achieved": total_completed >= 30,
            "target": 30,
            "current": total_completed,
        },
        {
            "key": "PRACTICES_50",
            "title": "50 Practices Completed",
            "description": "Your group has recorded 50 practices completed together.",
            "achieved": total_completed >= 50,
            "target": 50,
            "current": total_completed,
        },
        {
            "key": "PRACTICES_100",
            "title": "100 Practices Completed",
            "description": "Your group has recorded 100 completed practices.",
            "achieved": total_completed >= 100,
            "target": 100,
            "current": total_completed,
        },
    ]

    if goal.tracking_kind == Goal.TrackingKind.COUNT:
        unit = goal.target_unit or "units"
        milestones_def.extend([
            {
                "key": "COUNT_MILESTONE_100",
                "title": f"100 {unit.capitalize()} Reached",
                "description": f"The group has recorded over 100 {unit} together.",
                "achieved": total_count_value >= 100,
                "target": 100,
                "current": total_count_value,
            },
            {
                "key": "COUNT_MILESTONE_500",
                "title": f"500 {unit.capitalize()} Reached",
                "description": f"The group has recorded over 500 {unit} together.",
                "achieved": total_count_value >= 500,
                "target": 500,
                "current": total_count_value,
            },
        ])

    return milestones_def


def shared_goal_weekly_reflection(goal):
    """Generates a neutral, deterministic weekly summary for the current ISO week."""
    if not goal.is_shared:
        return None

    tz = ZoneInfo(goal.timezone or "Asia/Kolkata")
    today = timezone.now().astimezone(tz).date()
    start_of_week = today - timedelta(days=today.weekday())
    end_of_week = start_of_week + timedelta(days=6)

    if hasattr(goal, "_prefetched_objects_cache") and "participants" in goal._prefetched_objects_cache:
        active_participants = [p for p in goal.participants.all() if p.status == GoalParticipant.Status.ACTIVE]
    else:
        active_participants = list(goal.participants.filter(status=GoalParticipant.Status.ACTIVE))

    active_count = len(active_participants)
    if active_count == 0:
        return None

    active_ids = {p.id for p in active_participants}
    expected_per_member = goal.times_per_period if goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD else (len(goal.weekdays) if goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS else 7)
    expected_total = expected_per_member * active_count

    if hasattr(goal, "_prefetched_objects_cache") and "check_ins" in goal._prefetched_objects_cache:
        all_checkins = list(goal.check_ins.all())
        week_checkins = [
            c for c in all_checkins
            if start_of_week <= c.period_date <= end_of_week and c.status == GoalCheckIn.Status.COMPLETED and (c.participant_id in active_ids or (c.participant and c.participant.status == GoalParticipant.Status.ACTIVE))
        ]
        completed_total = len(week_checkins)
        total_val = sum(c.value or 0 for c in week_checkins)
        members_with_checkins = len({c.participant_id for c in week_checkins})
    else:
        week_checkins_qs = goal.check_ins.filter(
            period_date__gte=start_of_week,
            period_date__lte=end_of_week,
            status=GoalCheckIn.Status.COMPLETED,
            participant__status=GoalParticipant.Status.ACTIVE,
        )
        completed_total = week_checkins_qs.count()
        total_val = week_checkins_qs.aggregate(models.Sum("value"))["value__sum"] or 0
        members_with_checkins = week_checkins_qs.values("participant_id").distinct().count()

    pct = round((completed_total / expected_total) * 100, 1) if expected_total > 0 else 0.0

    if goal.tracking_kind == Goal.TrackingKind.COUNT:
        expected_val = (goal.target_value or 1) * expected_total
        unit = goal.target_unit or "units"
        reflection_text = f"This week, the group completed {total_val} of {expected_val} {unit}."
    else:
        reflection_text = f"This week, your group completed {completed_total} of {expected_total} planned practices."

    if members_with_checkins == active_count:
        trend_text = "All active members checked in this week."
    elif members_with_checkins > 0:
        trend_text = f"{members_with_checkins} of {active_count} members checked in this week."
    else:
        trend_text = "No practices recorded yet this week."

    return {
        "period_start": start_of_week.isoformat(),
        "period_end": end_of_week.isoformat(),
        "completed": completed_total,
        "expected": expected_total,
        "percentage": pct,
        "reflection_text": reflection_text,
        "trend_text": trend_text,
    }
