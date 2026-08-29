package app.promise.android.core

import app.promise.android.data.network.ApiException

data class UserMessageDetails(
    val title: String,
    val description: String,
    val actionLabel: String? = null,
    val isRecoverable: Boolean = true,
)

fun ApiException.toErrorKind(): app.promise.android.core.ErrorKind {
    return when {
        code == "AUTHENTICATION_FAILED" -> ErrorKind.InvalidCredentials
        code == "EMAIL_ALREADY_EXISTS" -> ErrorKind.EmailAlreadyExists
        code == "VALIDATION_ERROR" -> ErrorKind.Validation(fields = fieldErrors)
        code == "GOAL_INVALID_CHECKIN" -> ErrorKind.InvalidCheckIn
        code == "GOAL_SCHEDULE_LOCKED" -> ErrorKind.ScheduleLocked
        code == "GOAL_TIMEZONE_LOCKED" -> ErrorKind.TimezoneLocked
        code == "GOAL_ALREADY_PARTICIPANT" -> ErrorKind.AlreadyParticipant
        code == "GOAL_INVITE_EXPIRED" -> ErrorKind.InviteExpired
        code == "GOAL_INVITE_REVOKED" -> ErrorKind.InviteRevoked
        code == "GOAL_OWNER_CANNOT_LEAVE" -> ErrorKind.OwnerCannotLeave
        code == "GOAL_CANNOT_REMOVE_OWNER" -> ErrorKind.CannotRemoveOwner
        code == "GOAL_INVALID_PARTICIPANT_STATE" -> ErrorKind.InvalidParticipantState
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
        ErrorKind.InvalidCredentials -> "Incorrect email or password. Please verify and try again."
        ErrorKind.EmailAlreadyExists -> "An account with this email address already exists."
        is ErrorKind.Validation -> "Please review the highlighted fields and try again."
        ErrorKind.InvalidCheckIn -> "This check-in doesn't match the tracking rules for this goal."
        ErrorKind.ScheduleLocked -> "Habit frequency is locked once check-ins begin to preserve streak integrity."
        ErrorKind.TimezoneLocked -> "Timezone is locked after the first check-in to preserve date history."
        is ErrorKind.RateLimited -> "Too many requests. Please wait a moment before trying again."
        ErrorKind.Timeout -> "The server took too long to respond. Your data is safe—please try again."
        ErrorKind.Network -> "You're currently offline. Changes are saved locally and will sync when reconnected."
        ErrorKind.LocalNetworkDenied ->
            "Please allow local network access so Promise can reach your local development service."
        ErrorKind.Unauthenticated -> "Your session has expired. Please sign in again to keep synced."
        ErrorKind.NotFound -> "The requested commitment or goal was not found."
        ErrorKind.Conflict -> "This action cannot be completed in its current state."
        ErrorKind.AlreadyParticipant -> "This member is already part of this shared goal."
        ErrorKind.InviteExpired -> "This group invitation link has expired."
        ErrorKind.InviteRevoked -> "This invitation is no longer valid."
        ErrorKind.OwnerCannotLeave -> "As the goal creator, you cannot leave without assigning a new owner."
        ErrorKind.CannotRemoveOwner -> "The creator of this goal cannot be removed."
        ErrorKind.InvalidParticipantState -> "This invitation action is no longer available."
        ErrorKind.Unknown -> "Unable to complete request right now. Please try again."
    }
}

fun ErrorKind.toHumanizedDetails(): UserMessageDetails {
    return when (this) {
        ErrorKind.Network -> UserMessageDetails(
            title = "You're currently offline",
            description = "Changes are saved on your device and will sync automatically when you reconnect.",
            actionLabel = "Try Reconnecting",
            isRecoverable = true,
        )
        ErrorKind.Timeout -> UserMessageDetails(
            title = "Connection timed out",
            description = "The server is taking longer than usual. Your progress is saved locally.",
            actionLabel = "Try Again",
            isRecoverable = true,
        )
        ErrorKind.InvalidCredentials -> UserMessageDetails(
            title = "Authentication failed",
            description = "Please check your email and password, or use Google Sign-In.",
            actionLabel = "Retry",
            isRecoverable = true,
        )
        ErrorKind.Unauthenticated -> UserMessageDetails(
            title = "Session expired",
            description = "Please sign in again to synchronize your goals and commitments with the cloud.",
            actionLabel = "Sign In",
            isRecoverable = true,
        )
        ErrorKind.ScheduleLocked -> UserMessageDetails(
            title = "Cadence locked",
            description = "Once check-ins begin, recurrence is locked to protect the integrity of your streak.",
            actionLabel = null,
            isRecoverable = false,
        )
        is ErrorKind.RateLimited -> UserMessageDetails(
            title = "Brief pause needed",
            description = "Too many requests in a short period. Please wait a few seconds.",
            actionLabel = null,
            isRecoverable = true,
        )
        else -> UserMessageDetails(
            title = "Something went wrong",
            description = toUserMessage(),
            actionLabel = "Try Again",
            isRecoverable = true,
        )
    }
}
