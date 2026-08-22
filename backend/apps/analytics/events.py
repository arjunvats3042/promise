"""Central Analytics Event Taxonomy and Privacy Sanitization.

Strict Privacy Protections:
- NEVER store raw commitment text, goal descriptions, check-in notes, chat bodies,
  AI prompts/responses, raw search query text, passwords, tokens, FCM tokens, or Google credentials.
- All event names MUST belong to the whitelisted taxonomy below.
- Arbitrary client event names are rejected.
"""

from typing import Any, Dict

# Central Taxonomy Constants
EVENT_APP_OPENED = "app_opened"
EVENT_LOGIN_SUCCESS = "login_success"
EVENT_LOGIN_FAILURE = "login_failure"
EVENT_REGISTER_SUCCESS = "register_success"
EVENT_GOOGLE_LOGIN_SUCCESS = "google_login_success"
EVENT_EMAIL_VERIFIED = "email_verified"

EVENT_COMMITMENT_CREATED = "commitment_created"
EVENT_COMMITMENT_COMPLETED = "commitment_completed"
EVENT_COMMITMENT_CANCELLED = "commitment_cancelled"
EVENT_COMMITMENT_SNOOZED = "commitment_snoozed"
EVENT_COMMITMENT_WAITED = "commitment_waited"
EVENT_COMMITMENT_UNSNOOZED = "commitment_unsnoozed"

EVENT_GOAL_CREATED = "goal_created"
EVENT_GOAL_CHECKED_IN = "goal_checked_in"
EVENT_GOAL_COMPLETED = "goal_completed"
EVENT_GOAL_PAUSED = "goal_paused"
EVENT_GOAL_RESUMED = "goal_resumed"
EVENT_GOAL_CANCELLED = "goal_cancelled"

EVENT_SHARED_GOAL_CREATED = "shared_goal_created"
EVENT_PARTICIPANT_INVITED = "participant_invited"
EVENT_PARTICIPANT_JOINED = "participant_joined"
EVENT_PARTICIPANT_LEFT = "participant_left"
EVENT_PARTICIPANT_REMOVED = "participant_removed"
EVENT_OWNERSHIP_TRANSFERRED = "ownership_transferred"
EVENT_SHARED_GOAL_CHAT_OPENED = "shared_goal_chat_opened"
EVENT_SHARED_GOAL_MESSAGE_SENT = "shared_goal_message_sent"
EVENT_SHARED_GOAL_ACTIVITY_VIEWED = "shared_goal_activity_viewed"

EVENT_NOTIFICATION_SCHEDULED = "notification_scheduled"
EVENT_NOTIFICATION_DELIVERED = "notification_delivered"
EVENT_NOTIFICATION_OPENED = "notification_opened"
EVENT_NOTIFICATION_ACTION_CLICKED = "notification_action_clicked"
EVENT_NOTIFICATION_SUPPRESSED = "notification_suppressed"
EVENT_NOTIFICATION_FAILED = "notification_failed"

EVENT_SEARCH_STARTED = "search_started"
EVENT_SEARCH_RESULT_SELECTED = "search_result_selected"
EVENT_SEARCH_ZERO_RESULTS = "search_zero_results"
EVENT_PEOPLE_SEARCH_USED = "people_search_used"
EVENT_CHAT_SEARCH_USED = "chat_search_used"

EVENT_AI_GOAL_BUILDER_USED = "ai_goal_builder_used"
EVENT_AI_GOAL_BUILDER_CONFIRMED = "ai_goal_builder_confirmed"
EVENT_AI_COMMITMENT_REFINER_USED = "ai_commitment_refiner_used"
EVENT_AI_COMMITMENT_REFINER_CONFIRMED = "ai_commitment_refiner_confirmed"
EVENT_AI_THOUGHT_PARSER_USED = "ai_thought_parser_used"
EVENT_AI_THOUGHT_PARSER_CONFIRMED = "ai_thought_parser_confirmed"
EVENT_AI_WEEKLY_INSIGHTS_VIEWED = "ai_weekly_insights_viewed"
EVENT_AI_COMMAND_USED = "ai_command_used"
EVENT_AI_PLANNER_USED = "ai_planner_used"
EVENT_AI_REFLECTION_USED = "ai_reflection_used"
EVENT_AI_SHARED_GOAL_SUMMARY_USED = "ai_shared_goal_summary_used"
EVENT_AI_CHAT_SUMMARY_USED = "ai_chat_summary_used"
EVENT_AI_UNAVAILABLE = "ai_unavailable"
EVENT_AI_CLARIFICATION_REQUESTED = "ai_clarification_requested"

EVENT_ONBOARDING_STARTED = "onboarding_started"
EVENT_ONBOARDING_COMPLETED = "onboarding_completed"
EVENT_FIRST_GOAL_CREATED = "first_goal_created"
EVENT_FIRST_COMMITMENT_CREATED = "first_commitment_created"
EVENT_FIRST_CHECKIN_COMPLETED = "first_checkin_completed"
EVENT_FIRST_SHARED_GOAL_CREATED = "first_shared_goal_created"
EVENT_FIRST_INVITATION_SENT = "first_invitation_sent"
EVENT_FIRST_INVITATION_ACCEPTED = "first_invitation_accepted"

APPROVED_TAXONOMY_EVENTS = {
    # Auth
    EVENT_APP_OPENED,
    EVENT_LOGIN_SUCCESS,
    EVENT_LOGIN_FAILURE,
    EVENT_REGISTER_SUCCESS,
    EVENT_GOOGLE_LOGIN_SUCCESS,
    EVENT_EMAIL_VERIFIED,
    # Commitments
    EVENT_COMMITMENT_CREATED,
    EVENT_COMMITMENT_COMPLETED,
    EVENT_COMMITMENT_CANCELLED,
    EVENT_COMMITMENT_SNOOZED,
    EVENT_COMMITMENT_WAITED,
    EVENT_COMMITMENT_UNSNOOZED,
    # Goals
    EVENT_GOAL_CREATED,
    EVENT_GOAL_CHECKED_IN,
    EVENT_GOAL_COMPLETED,
    EVENT_GOAL_PAUSED,
    EVENT_GOAL_RESUMED,
    EVENT_GOAL_CANCELLED,
    # Shared Goals
    EVENT_SHARED_GOAL_CREATED,
    EVENT_PARTICIPANT_INVITED,
    EVENT_PARTICIPANT_JOINED,
    EVENT_PARTICIPANT_LEFT,
    EVENT_PARTICIPANT_REMOVED,
    EVENT_OWNERSHIP_TRANSFERRED,
    EVENT_SHARED_GOAL_CHAT_OPENED,
    EVENT_SHARED_GOAL_MESSAGE_SENT,
    EVENT_SHARED_GOAL_ACTIVITY_VIEWED,
    # Notifications
    EVENT_NOTIFICATION_SCHEDULED,
    EVENT_NOTIFICATION_DELIVERED,
    EVENT_NOTIFICATION_OPENED,
    EVENT_NOTIFICATION_ACTION_CLICKED,
    EVENT_NOTIFICATION_SUPPRESSED,
    EVENT_NOTIFICATION_FAILED,
    # Search
    EVENT_SEARCH_STARTED,
    EVENT_SEARCH_RESULT_SELECTED,
    EVENT_SEARCH_ZERO_RESULTS,
    EVENT_PEOPLE_SEARCH_USED,
    EVENT_CHAT_SEARCH_USED,
    # AI
    EVENT_AI_GOAL_BUILDER_USED,
    EVENT_AI_GOAL_BUILDER_CONFIRMED,
    EVENT_AI_COMMITMENT_REFINER_USED,
    EVENT_AI_COMMITMENT_REFINER_CONFIRMED,
    EVENT_AI_THOUGHT_PARSER_USED,
    EVENT_AI_THOUGHT_PARSER_CONFIRMED,
    EVENT_AI_WEEKLY_INSIGHTS_VIEWED,
    EVENT_AI_COMMAND_USED,
    EVENT_AI_PLANNER_USED,
    EVENT_AI_REFLECTION_USED,
    EVENT_AI_SHARED_GOAL_SUMMARY_USED,
    EVENT_AI_CHAT_SUMMARY_USED,
    EVENT_AI_UNAVAILABLE,
    EVENT_AI_CLARIFICATION_REQUESTED,
    # Onboarding
    EVENT_ONBOARDING_STARTED,
    EVENT_ONBOARDING_COMPLETED,
    EVENT_FIRST_GOAL_CREATED,
    EVENT_FIRST_COMMITMENT_CREATED,
    EVENT_FIRST_CHECKIN_COMPLETED,
    EVENT_FIRST_SHARED_GOAL_CREATED,
    EVENT_FIRST_INVITATION_SENT,
    EVENT_FIRST_INVITATION_ACCEPTED,
}

# Key substrings that are FORBIDDEN in analytics property payloads to preserve user privacy
FORBIDDEN_PROPERTY_KEYWORDS = {
    "title",
    "description",
    "text",
    "note",
    "body",
    "prompt",
    "response",
    "query",
    "q",
    "password",
    "secret",
    "token",
    "fcm",
    "jwt",
    "key",
    "email",
    "name",
    "phone",
    "address",
    "credential",
    "location",
}


def is_event_whitelisted(event_name: str) -> bool:
    """Returns True if the event_name is part of the approved taxonomy."""
    return event_name in APPROVED_TAXONOMY_EVENTS


def sanitize_analytics_properties(properties: Dict[str, Any]) -> Dict[str, Any]:
    """Sanitizes properties dictionary by stripping any key matching forbidden privacy keywords."""
    if not isinstance(properties, dict):
        return {}

    sanitized = {}
    for key, value in properties.items():
        key_lower = key.lower()
        if any(forbidden in key_lower for forbidden in FORBIDDEN_PROPERTY_KEYWORDS):
            continue
        # Truncate strings to max 256 chars to bound payload size
        if isinstance(value, str):
            sanitized[key] = value[:256]
        elif isinstance(value, (int, float, bool)) or value is None:
            sanitized[key] = value
        elif isinstance(value, list) and len(value) <= 20:
            sanitized[key] = [v[:256] if isinstance(v, str) else v for v in value]
    return sanitized
