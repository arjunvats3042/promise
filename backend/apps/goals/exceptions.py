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
