from django.db.models import Case, Count, IntegerField, Q, Value, When
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.commitments.models import Commitment
from apps.goals.models import Goal, GoalParticipant
from apps.search.serializers import (
    CommitmentSearchResultSerializer,
    GoalSearchResultSerializer,
    SearchQuerySerializer,
)

MAX_RESULTS_PER_SECTION = 15


def prioritize_matches(queryset, field_name: str, query: str):
    """Orders matches by: 1. Exact match, 2. Prefix match, 3. Substring match, then by -updated_at."""
    return queryset.annotate(
        match_priority=Case(
            When(**{f"{field_name}__iexact": query}, then=Value(1)),
            When(**{f"{field_name}__istartswith": query}, then=Value(2)),
            When(**{f"{field_name}__icontains": query}, then=Value(3)),
            default=Value(4),
            output_field=IntegerField(),
        )
    ).order_by("match_priority", "-updated_at")


class GlobalSearchView(APIView):
    """Unified, authenticated global search across user's commitments and authorized goals."""

    permission_classes = [IsAuthenticated]

    def get(self, request):
        serializer = SearchQuerySerializer(data=request.query_params)
        serializer.is_valid(raise_exception=True)
        q = serializer.validated_data["q"]
        search_type = serializer.validated_data.get("type", "all")

        commitments_res = []
        goals_res = []
        shared_goals_res = []

        if search_type in ("all", "commitments"):
            commitments_qs = Commitment.objects.filter(
                created_by=request.user,
            ).filter(
                Q(title__icontains=q) | Q(description__icontains=q)
            )
            commitments_ordered = prioritize_matches(commitments_qs, "title", q)[:MAX_RESULTS_PER_SECTION]
            commitments_res = CommitmentSearchResultSerializer(commitments_ordered, many=True).data

        if search_type in ("all", "goals"):
            # Personal Goals owned by user
            goals_qs = Goal.objects.filter(
                created_by=request.user,
                is_shared=False,
            ).filter(
                Q(title__icontains=q) | Q(description__icontains=q)
            )
            goals_ordered = prioritize_matches(goals_qs, "title", q)[:MAX_RESULTS_PER_SECTION]
            goals_res = GoalSearchResultSerializer(goals_ordered, many=True).data

            # Shared Goals where user is an ACTIVE participant
            shared_goals_qs = (
                Goal.objects.filter(
                    is_shared=True,
                    participants__user=request.user,
                    participants__status=GoalParticipant.Status.ACTIVE,
                )
                .filter(Q(title__icontains=q) | Q(description__icontains=q))
                .distinct()
                .annotate(
                    participant_count=Count(
                        "participants",
                        filter=Q(participants__status=GoalParticipant.Status.ACTIVE),
                    )
                )
            )
            shared_goals_ordered = prioritize_matches(shared_goals_qs, "title", q)[:MAX_RESULTS_PER_SECTION]
            shared_goals_res = GoalSearchResultSerializer(shared_goals_ordered, many=True).data

        total_count = len(commitments_res) + len(goals_res) + len(shared_goals_res)

        # Emit privacy-conscious analytics events (NO raw query q stored!)
        try:
            from apps.analytics.events import EVENT_SEARCH_STARTED, EVENT_SEARCH_ZERO_RESULTS
            from apps.analytics.services import record_analytics_event

            record_analytics_event(
                event_name=EVENT_SEARCH_STARTED,
                user=request.user,
                properties={"search_type": search_type, "total_results": total_count},
            )
            if total_count == 0:
                record_analytics_event(
                    event_name=EVENT_SEARCH_ZERO_RESULTS,
                    user=request.user,
                    properties={"search_type": search_type},
                )
        except Exception:
            pass

        return Response({
            "commitments": commitments_res,
            "goals": goals_res,
            "shared_goals": shared_goals_res,
        })
