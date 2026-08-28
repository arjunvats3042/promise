import logging
from django.db import transaction
from django.utils import timezone
from rest_framework import permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.commitments.services import complete_commitment, create_commitment
from apps.goals.services import create_goal, record_check_in
from apps.sync.models import SyncActionAudit
from apps.sync.serializers import SyncBatchRequestSerializer

logger = logging.getLogger(__name__)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def sync_outbox_view(request):
    """Processes an idempotent batch of offline client outbox mutations."""
    serializer = SyncBatchRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    actions = serializer.validated_data["actions"]
    results = []

    for action in actions:
        action_id = action["action_id"]
        action_type = action["action_type"]
        entity_id = action["entity_id"]
        payload = action.get("payload", {})
        client_timestamp = action.get("client_timestamp")

        # 1. Check idempotency: already processed?
        if SyncActionAudit.objects.filter(action_id=action_id).exists():
            results.append({
                "action_id": action_id,
                "status": "ALREADY_PROCESSED",
                "error_message": None,
            })
            continue

        action_status = "APPLIED"
        error_msg = None

        try:
            with transaction.atomic():
                if action_type == "CREATE_COMMITMENT":
                    create_commitment(
                        creator=request.user,
                        title=payload.get("title", "Untitled"),
                        description=payload.get("description", ""),
                        due_at=payload.get("due_at"),
                        due_precision=payload.get("due_precision", "NONE"),
                    )
                elif action_type == "COMPLETE_COMMITMENT":
                    complete_commitment(
                        actor=request.user,
                        commitment_id=entity_id,
                    )
                elif action_type == "CHECK_IN_GOAL":
                    record_check_in(
                        actor=request.user,
                        goal_id=entity_id,
                        status=payload.get("status", "COMPLETED"),
                        value=payload.get("value"),
                    )
                elif action_type == "CREATE_GOAL":
                    create_goal(
                        creator=request.user,
                        title=payload.get("title", "Untitled"),
                        description=payload.get("description", ""),
                        recurrence_kind=payload.get("recurrence_kind", "DAILY"),
                        weekdays=payload.get("weekdays", []),
                        tracking_kind=payload.get("tracking_kind", "BINARY"),
                        target_value=payload.get("target_value"),
                        target_unit=payload.get("target_unit", ""),
                    )

                SyncActionAudit.objects.create(
                    action_id=action_id,
                    user=request.user,
                    action_type=action_type,
                    entity_id=entity_id,
                    status=SyncActionAudit.ActionStatus.APPLIED,
                )
        except Exception as e:
            logger.warning("Sync action failed: %s - %s", action_id, str(e))
            action_status = "FAILED"
            error_msg = str(e)
            try:
                SyncActionAudit.objects.create(
                    action_id=action_id,
                    user=request.user,
                    action_type=action_type,
                    entity_id=entity_id,
                    status=SyncActionAudit.ActionStatus.FAILED,
                )
            except Exception:
                pass

        results.append({
            "action_id": action_id,
            "status": action_status,
            "error_message": error_msg,
        })

    return Response({
        "results": results,
        "server_timestamp": timezone.now().isoformat(),
    }, status=status.HTTP_200_OK)
