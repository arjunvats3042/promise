from rest_framework import status

from config.exceptions import ApplicationAPIError


class AccessTokenError(Exception):
    """Invalid access JWT.

    Future DRF authentication should map this to HTTP 401. The message must
    not include cryptographic details, keys, or token material.
    """

    def __init__(self):
        super().__init__("Invalid access token.")


class AuthenticationCredentialsError(ApplicationAPIError):
    status_code = status.HTTP_401_UNAUTHORIZED
    error_code = "AUTHENTICATION_FAILED"
    public_message = "Invalid email or password."


class EmailAlreadyExistsError(ApplicationAPIError):
    status_code = status.HTTP_409_CONFLICT
    error_code = "EMAIL_ALREADY_EXISTS"
    public_message = "A user with this email already exists."


class RefreshTokenInvalidError(ApplicationAPIError):
    status_code = status.HTTP_401_UNAUTHORIZED
    error_code = "TOKEN_INVALID"
    public_message = "Refresh token is invalid."


class RefreshTokenExpiredError(ApplicationAPIError):
    status_code = status.HTTP_401_UNAUTHORIZED
    error_code = "TOKEN_EXPIRED"
    public_message = "Refresh token has expired."


class RefreshSessionRevokedError(ApplicationAPIError):
    status_code = status.HTTP_401_UNAUTHORIZED
    error_code = "SESSION_REVOKED"
    public_message = "Session is no longer valid."


class RefreshSecretDecryptionError(Exception):
    """Current refresh secret ciphertext could not be decrypted."""
