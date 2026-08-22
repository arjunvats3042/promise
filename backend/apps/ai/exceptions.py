class AiServiceError(Exception):
    """Base exception for AI service operations."""
    code = "AI_ERROR"
    status_code = 500
    message = "An AI service error occurred."

    def __init__(self, message=None, code=None, status_code=None):
        if message:
            self.message = message
        if code:
            self.code = code
        if status_code:
            self.status_code = status_code
        super().__init__(self.message)


class AiUnavailableError(AiServiceError):
    code = "AI_UNAVAILABLE"
    status_code = 503
    message = "AI service is temporarily unavailable. Please try again later."


class AiRateLimitedError(AiServiceError):
    code = "RATE_LIMITED"
    status_code = 429
    message = "AI rate limit exceeded. Please try again shortly."


class AiInvalidResponseError(AiServiceError):
    code = "AI_INVALID_RESPONSE"
    status_code = 502
    message = "AI returned an unparseable or invalid response."


class AiRefusalError(AiServiceError):
    code = "AI_REFUSAL"
    status_code = 400
    message = "AI cannot fulfill this request due to safety or policy guidelines."


class AiBadRequestError(AiServiceError):
    code = "AI_BAD_REQUEST"
    status_code = 400
    message = "Invalid input or request for AI service."
