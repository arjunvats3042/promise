from rest_framework import status

from config.exceptions import ApplicationAPIError


class UserNotFoundError(ApplicationAPIError):
    status_code = status.HTTP_404_NOT_FOUND
    error_code = "USER_NOT_FOUND"
    public_message = "User was not found."
