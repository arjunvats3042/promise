from zoneinfo import ZoneInfo

from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

from apps.goals.models import GoalParticipant
from apps.goals.rate_limits import (
    enforce_goal_checkin_rate_limit,
    enforce_goal_write_rate_limit,
)
from apps.goals.serializers import (
    GoalCheckInListQuerySerializer,
    GoalCheckInSerializer,
    GoalCheckInWriteSerializer,
    GoalCreateSerializer,
    GoalInviteCreateSerializer,
    GoalInvitePreviewSerializer,
    GoalListQuerySerializer,
    GoalParticipantSerializer,
    GoalSerializer,
    GoalUpdateSerializer,
    SharedGoalSerializer,
)
from apps.goals.services import (
    accept_invitation,
    cancel_goal,
    complete_goal,
    create_goal,
    decline_invitation,
    get_participant,
    get_visible_goal,
    invite_participant,
    is_invite_preview,
    is_shared_goal,
    leave_goal,
    list_active_participants,
    list_goal_check_ins,
    list_visible_goals,
    pause_goal,
    record_check_in,
    remove_participant,
    resume_goal,
    update_goal,
)


class GoalListPagination(PageNumberPagination):
    page_size = 20
    page_size_query_param = "page_size"
    max_page_size = 100


def _serializer_for_goal(goal, request):
    context = {"request": request}
    if is_invite_preview(request.user, goal):
        return GoalInvitePreviewSerializer(goal, context=context)
    if is_shared_goal(goal):
        return SharedGoalSerializer(goal, context=context)
    return GoalSerializer(goal, context=context)


def _goal_response(goal, request, http_status=status.HTTP_200_OK):
    return Response(_serializer_for_goal(goal, request).data, status=http_status)


def _serialize_goal_list(goals, request):
    return [_serializer_for_goal(goal, request).data for goal in goals]


def _checkin_response(check_in, http_status=status.HTTP_200_OK):
    return Response(GoalCheckInSerializer(check_in).data, status=http_status)


def _local_today(goal):
    return timezone.now().astimezone(ZoneInfo(goal.timezone)).date()


@api_view(["GET", "POST"])
def goal_collection(request):
    if request.method == "POST":
        enforce_goal_write_rate_limit(request.user)
        serializer = GoalCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        goal = create_goal(creator=request.user, **serializer.validated_data)
        return _goal_response(goal, request, status.HTTP_201_CREATED)

    query = GoalListQuerySerializer(data=request.query_params.dict())
    query.is_valid(raise_exception=True)
    queryset = list_visible_goals(viewer=request.user, **query.validated_data)
    paginator = GoalListPagination()
    page = paginator.paginate_queryset(queryset, request)
    return paginator.get_paginated_response(_serialize_goal_list(page, request))


@api_view(["GET", "PATCH"])
def goal_detail(request, goal_id):
    if request.method == "GET":
        goal = get_visible_goal(viewer=request.user, goal_id=goal_id)
        return _goal_response(goal, request)

    enforce_goal_write_rate_limit(request.user)
    serializer = GoalUpdateSerializer(data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    goal = update_goal(
        actor=request.user,
        goal_id=goal_id,
        **serializer.validated_data,
    )
    return _goal_response(goal, request)


@api_view(["POST"])
def goal_pause(request, goal_id):
    enforce_goal_write_rate_limit(request.user)
    return _goal_response(
        pause_goal(actor=request.user, goal_id=goal_id), request
    )


@api_view(["POST"])
def goal_resume(request, goal_id):
    enforce_goal_write_rate_limit(request.user)
    return _goal_response(
        resume_goal(actor=request.user, goal_id=goal_id), request
    )


@api_view(["POST"])
def goal_complete(request, goal_id):
    enforce_goal_write_rate_limit(request.user)
    return _goal_response(
        complete_goal(actor=request.user, goal_id=goal_id), request
    )


@api_view(["POST"])
def goal_cancel(request, goal_id):
    enforce_goal_write_rate_limit(request.user)
    return _goal_response(
        cancel_goal(actor=request.user, goal_id=goal_id), request
    )


@api_view(["GET", "POST"])
def goal_check_ins(request, goal_id):
    if request.method == "GET":
        query = GoalCheckInListQuerySerializer(data=request.query_params.dict())
        query.is_valid(raise_exception=True)
        queryset = list_goal_check_ins(
            viewer=request.user,
            goal_id=goal_id,
            **query.validated_data,
        )
        paginator = GoalListPagination()
        page = paginator.paginate_queryset(queryset, request)
        return paginator.get_paginated_response(
            GoalCheckInSerializer(page, many=True).data
        )

    enforce_goal_checkin_rate_limit(request.user)
    serializer = GoalCheckInWriteSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    goal = get_visible_goal(viewer=request.user, goal_id=goal_id)
    data = serializer.validated_data
    period_date = data.get("period_date") or _local_today(goal)
    participant = get_participant(goal, request.user)
    existed = False
    if participant is not None:
        existed = goal.check_ins.filter(
            period_date=period_date,
            participant=participant,
        ).exists()
    check_in = record_check_in(
        actor=request.user,
        goal_id=goal_id,
        period_date=period_date,
        status=data["status"],
        value=data.get("value"),
        note=data.get("note") or "",
    )
    http_status = status.HTTP_200_OK if existed else status.HTTP_201_CREATED
    return _checkin_response(check_in, http_status)


@api_view(["GET", "POST"])
def goal_participants(request, goal_id):
    if request.method == "GET":
        rows = list_active_participants(viewer=request.user, goal_id=goal_id)
        return Response(GoalParticipantSerializer(rows, many=True).data)

    enforce_goal_write_rate_limit(request.user)
    serializer = GoalInviteCreateSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user_id = serializer.validated_data["user_id"]
    existing = GoalParticipant.objects.filter(
        goal_id=goal_id, user_id=user_id
    ).first()
    already_invited = (
        existing is not None
        and existing.status == GoalParticipant.Status.INVITED
    )
    participant = invite_participant(
        actor=request.user,
        goal_id=goal_id,
        user_id=user_id,
    )
    http_status = (
        status.HTTP_200_OK if already_invited else status.HTTP_201_CREATED
    )
    return Response(
        GoalParticipantSerializer(participant).data,
        status=http_status,
    )


@api_view(["DELETE"])
def goal_participant_detail(request, goal_id, user_id):
    enforce_goal_write_rate_limit(request.user)
    participant = remove_participant(
        actor=request.user,
        goal_id=goal_id,
        user_id=user_id,
    )
    return Response(
        GoalParticipantSerializer(participant).data,
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
def goal_participants_accept(request, goal_id):
    enforce_goal_write_rate_limit(request.user)
    accept_invitation(actor=request.user, goal_id=goal_id)
    goal = get_visible_goal(viewer=request.user, goal_id=goal_id)
    return _goal_response(goal, request)


@api_view(["POST"])
def goal_participants_decline(request, goal_id):
    enforce_goal_write_rate_limit(request.user)
    participant = decline_invitation(actor=request.user, goal_id=goal_id)
    return Response(
        GoalParticipantSerializer(participant).data,
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
def goal_leave(request, goal_id):
    enforce_goal_write_rate_limit(request.user)
    participant = leave_goal(actor=request.user, goal_id=goal_id)
    return Response(
        GoalParticipantSerializer(participant).data,
        status=status.HTTP_200_OK,
    )
