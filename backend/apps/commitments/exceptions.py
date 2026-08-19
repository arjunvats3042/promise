from rest_framework import status

from config.exceptions import ApplicationAPIError


class CommitmentNotFoundError(ApplicationAPIError):
    status_code = status.HTTP_404_NOT_FOUND
    error_code = "COMMITMENT_NOT_FOUND"
    public_message = "Commitment was not found."


class CommitmentForbiddenError(ApplicationAPIError):
    status_code = status.HTTP_403_FORBIDDEN
    error_code = "FORBIDDEN"
    public_message = "You do not have permission to perform this action."


class CommitmentInvalidTransitionError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "COMMITMENT_INVALID_TRANSITION"
    public_message = "This commitment cannot be updated that way."


class CommitmentInvalidSnoozeError(ApplicationAPIError):
    status_code = status.HTTP_400_BAD_REQUEST
    error_code = "COMMITMENT_INVALID_SNOOZE"
    public_message = "Snooze time is invalid."


class CommitmentValidationError(ApplicationAPIError):
    status_code = status.HTTP_400_BAD_REQUEST
    error_code = "VALIDATION_ERROR"
    public_message = "Request validation failed."
