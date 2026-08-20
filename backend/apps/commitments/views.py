from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

from apps.commitments.models import Commitment
from apps.commitments.rate_limits import enforce_commitment_write_rate_limit
from apps.commitments.serializers import (
    CommitmentCreateSerializer,
    CommitmentListQuerySerializer,
    CommitmentSerializer,
    CommitmentUpdateSerializer,
    SnoozeSerializer,
)
from apps.commitments.services import (
    cancel_commitment,
    complete_commitment,
    create_commitment,
    get_visible_commitment,
    list_visible_commitments,
    set_waiting,
    snooze_commitment,
    unsnooze_commitment,
    update_commitment,
)


class CommitmentListPagination(PageNumberPagination):
    page_size = 20
    page_size_query_param = "page_size"
    max_page_size = 100


def _commitment_response(commitment, http_status=status.HTTP_200_OK):
    return Response(CommitmentSerializer(commitment).data, status=http_status)


@api_view(["GET", "POST"])
def commitment_collection(request):
    if request.method == "POST":
        enforce_commitment_write_rate_limit(request.user)
        serializer = CommitmentCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        commitment = create_commitment(
            creator=request.user,
            source=Commitment.Source.MANUAL,
            **serializer.validated_data,
        )
        return _commitment_response(commitment, status.HTTP_201_CREATED)

    query = CommitmentListQuerySerializer(data=request.query_params.dict())
    query.is_valid(raise_exception=True)
    queryset = list_visible_commitments(viewer=request.user, **query.validated_data)
    paginator = CommitmentListPagination()
    page = paginator.paginate_queryset(queryset, request)
    return paginator.get_paginated_response(CommitmentSerializer(page, many=True).data)


@api_view(["GET", "PATCH"])
def commitment_detail(request, commitment_id):
    if request.method == "GET":
        commitment = get_visible_commitment(
            viewer=request.user,
            commitment_id=commitment_id,
        )
        return _commitment_response(commitment)

    enforce_commitment_write_rate_limit(request.user)
    serializer = CommitmentUpdateSerializer(data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    commitment = update_commitment(
        actor=request.user,
        commitment_id=commitment_id,
        **serializer.validated_data,
    )
    return _commitment_response(commitment)


@api_view(["POST"])
def commitment_complete(request, commitment_id):
    enforce_commitment_write_rate_limit(request.user)
    commitment = complete_commitment(actor=request.user, commitment_id=commitment_id)
    return _commitment_response(commitment)


@api_view(["POST"])
def commitment_snooze(request, commitment_id):
    enforce_commitment_write_rate_limit(request.user)
    serializer = SnoozeSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    commitment = snooze_commitment(
        actor=request.user,
        commitment_id=commitment_id,
        snoozed_until=serializer.validated_data["snoozed_until"],
    )
    return _commitment_response(commitment)


@api_view(["POST"])
def commitment_unsnooze(request, commitment_id):
    enforce_commitment_write_rate_limit(request.user)
    commitment = unsnooze_commitment(actor=request.user, commitment_id=commitment_id)
    return _commitment_response(commitment)


@api_view(["POST"])
def commitment_wait(request, commitment_id):
    enforce_commitment_write_rate_limit(request.user)
    commitment = set_waiting(actor=request.user, commitment_id=commitment_id)
    return _commitment_response(commitment)


@api_view(["POST"])
def commitment_cancel(request, commitment_id):
    enforce_commitment_write_rate_limit(request.user)
    commitment = cancel_commitment(actor=request.user, commitment_id=commitment_id)
    return _commitment_response(commitment)
