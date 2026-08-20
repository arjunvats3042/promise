import logging

from rest_framework import status
from rest_framework.exceptions import (
    APIException,
    AuthenticationFailed,
    MethodNotAllowed,
    NotAuthenticated,
    NotFound,
    ParseError,
    PermissionDenied,
    Throttled,
    ValidationError,
)
from rest_framework.response import Response
from rest_framework.views import exception_handler as drf_exception_handler

logger = logging.getLogger("promise")


class ApplicationAPIError(APIException):
    """Base for public, stable application API errors."""

    error_code = "INVALID_REQUEST"
    public_message = "Request is invalid."

    def __init__(self):
        super().__init__(detail=self.public_message, code=self.error_code)


class RateLimitedError(ApplicationAPIError):
    status_code = status.HTTP_429_TOO_MANY_REQUESTS
    error_code = "RATE_LIMITED"
    public_message = "Too many requests. Please try again later."

    def __init__(self, retry_after=None):
        self.retry_after = retry_after
        super().__init__()


def _error_payload(code, message, details=None):
    error = {"code": code, "message": message}
    if details is not None:
        error["details"] = details
    return {"error": error}


def _stringify_details(data):
    if isinstance(data, list):
        return [str(item) for item in data]
    if isinstance(data, dict):
        return {str(key): _stringify_details(value) for key, value in data.items()}
    return str(data)


def api_exception_handler(exc, context):
    if isinstance(exc, ApplicationAPIError):
        response = Response(
            _error_payload(exc.error_code, exc.public_message),
            status=exc.status_code,
        )
        retry_after = getattr(exc, "retry_after", None)
        if retry_after is not None:
            response["Retry-After"] = str(int(retry_after))
        return response

    if isinstance(exc, ValidationError):
        response = drf_exception_handler(exc, context)
        details = _stringify_details(response.data if response is not None else exc.detail)
        return Response(
            _error_payload(
                "VALIDATION_ERROR",
                "Request validation failed",
                details=details,
            ),
            status=status.HTTP_400_BAD_REQUEST,
        )

    if isinstance(exc, ParseError):
        return Response(
            _error_payload("INVALID_REQUEST", "Request is invalid."),
            status=status.HTTP_400_BAD_REQUEST,
        )

    response = drf_exception_handler(exc, context)
    if response is None:
        logger.exception("Unhandled API exception")
        return Response(
            _error_payload(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.",
            ),
            status=status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    if isinstance(exc, (NotAuthenticated, AuthenticationFailed)):
        code, message, http_status = (
            "UNAUTHENTICATED",
            "Authentication credentials were not provided.",
            status.HTTP_401_UNAUTHORIZED,
        )
        if isinstance(exc, AuthenticationFailed):
            message = "Authentication failed."
        return Response(_error_payload(code, message), status=http_status)

    if isinstance(exc, PermissionDenied):
        return Response(
            _error_payload("FORBIDDEN", "You do not have permission to perform this action."),
            status=status.HTTP_403_FORBIDDEN,
        )

    if isinstance(exc, NotFound):
        return Response(
            _error_payload("NOT_FOUND", "Resource was not found."),
            status=status.HTTP_404_NOT_FOUND,
        )

    if isinstance(exc, MethodNotAllowed):
        return Response(
            _error_payload("METHOD_NOT_ALLOWED", "Method not allowed."),
            status=status.HTTP_405_METHOD_NOT_ALLOWED,
        )

    if isinstance(exc, Throttled):
        return Response(
            _error_payload("RATE_LIMITED", "Too many requests."),
            status=status.HTTP_429_TOO_MANY_REQUESTS,
        )

    return Response(
        _error_payload("INVALID_REQUEST", "Request is invalid."),
        status=response.status_code,
    )
