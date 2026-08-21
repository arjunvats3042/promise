from rest_framework import status

from config.exceptions import ApplicationAPIError


class GoalNotFoundError(ApplicationAPIError):
    status_code = status.HTTP_404_NOT_FOUND
    error_code = "GOAL_NOT_FOUND"
    public_message = "Goal was not found."


class GoalInvalidTransitionError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_INVALID_TRANSITION"
    public_message = "This goal cannot be updated that way."


class GoalScheduleLockedError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_SCHEDULE_LOCKED"
    public_message = "This goal's schedule cannot be changed after a check-in."


class GoalTimezoneLockedError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_TIMEZONE_LOCKED"
    public_message = "This goal's timezone cannot be changed after a check-in."


class GoalInvalidCheckInError(ApplicationAPIError):
    status_code = status.HTTP_400_BAD_REQUEST
    error_code = "GOAL_INVALID_CHECKIN"
    public_message = "Check-in is invalid."


class GoalValidationError(ApplicationAPIError):
    status_code = status.HTTP_400_BAD_REQUEST
    error_code = "VALIDATION_ERROR"
    public_message = "Request validation failed."


class GoalAlreadyParticipantError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_ALREADY_PARTICIPANT"
    public_message = "This user is already an active participant."


class GoalInviteExpiredError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_INVITE_EXPIRED"
    public_message = "This invitation has expired."


class GoalInviteRevokedError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_INVITE_REVOKED"
    public_message = "This invitation is no longer available."


class GoalCannotRemoveOwnerError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_CANNOT_REMOVE_OWNER"
    public_message = "The goal owner cannot be removed."


class GoalOwnerCannotLeaveError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_OWNER_CANNOT_LEAVE"
    public_message = "The goal owner cannot leave. Cancel the goal instead."


class GoalInvalidParticipantStateError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "GOAL_INVALID_PARTICIPANT_STATE"
    public_message = "Participant state does not allow this action."
