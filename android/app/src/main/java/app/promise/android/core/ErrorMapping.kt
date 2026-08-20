package app.promise.android.core

import app.promise.android.data.network.ApiException

fun ApiException.toErrorKind(): app.promise.android.core.ErrorKind {
    return when {
        code == "AUTHENTICATION_FAILED" -> ErrorKind.InvalidCredentials
        code == "EMAIL_ALREADY_EXISTS" -> ErrorKind.EmailAlreadyExists
        code == "VALIDATION_ERROR" -> ErrorKind.Validation(fields = fieldErrors)
        code == "GOAL_INVALID_CHECKIN" -> ErrorKind.InvalidCheckIn
        code == "GOAL_SCHEDULE_LOCKED" -> ErrorKind.ScheduleLocked
        code == "GOAL_TIMEZONE_LOCKED" -> ErrorKind.TimezoneLocked
        status == 404 || code.endsWith("_NOT_FOUND") || code == "NOT_FOUND" -> ErrorKind.NotFound
        status == 409 || code.contains("INVALID_TRANSITION") || code.contains("_LOCKED") ->
            ErrorKind.Conflict
        status == 429 || code == "RATE_LIMITED" ->
            ErrorKind.RateLimited(retryAfterSeconds)
        code == "TIMEOUT" -> ErrorKind.Timeout
        code == "NETWORK" -> ErrorKind.Network
        isSessionEnded -> ErrorKind.Unauthenticated
        else -> ErrorKind.Unknown
    }
}

fun ErrorKind.toUserMessage(): String {
    return when (this) {
        ErrorKind.InvalidCredentials -> "Invalid email or password."
        ErrorKind.EmailAlreadyExists -> "An account with this email already exists."
        is ErrorKind.Validation -> "Check the fields and try again."
        ErrorKind.InvalidCheckIn -> "That check-in isn’t valid for this goal."
        ErrorKind.ScheduleLocked -> "Schedule can’t change after the first check-in."
        ErrorKind.TimezoneLocked -> "Timezone can’t change after the first check-in."
        is ErrorKind.RateLimited -> "Too many tries. Wait a moment."
        ErrorKind.Timeout -> "Connection timed out."
        ErrorKind.Network -> "You're offline."
        ErrorKind.LocalNetworkDenied ->
            "Allow local network access so Promise can reach your computer."
        ErrorKind.Unauthenticated -> "Session ended."
        ErrorKind.NotFound -> "Not found."
        ErrorKind.Conflict -> "That can’t be done in the current state."
        ErrorKind.Unknown -> "Something went wrong. Try again."
    }
}
