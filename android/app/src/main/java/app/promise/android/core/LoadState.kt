package app.promise.android.core

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T, val isRefreshing: Boolean = false) : LoadState<T>
    data object Empty : LoadState<Nothing>
    data class Error(val kind: ErrorKind, val canRetry: Boolean) : LoadState<Nothing>
}

sealed interface ActionState {
    data object Idle : ActionState
    data object InFlight : ActionState
    data class Failed(val kind: ErrorKind) : ActionState
}

sealed interface ErrorKind {
    data object Network : ErrorKind
    data object Timeout : ErrorKind
    data object LocalNetworkDenied : ErrorKind
    data class RateLimited(val retryAfterSeconds: Int? = null) : ErrorKind
    data object Unauthenticated : ErrorKind
    data object InvalidCredentials : ErrorKind
    data object EmailAlreadyExists : ErrorKind
    data class Validation(val fields: Map<String, String> = emptyMap()) : ErrorKind
    data object NotFound : ErrorKind
    data object Conflict : ErrorKind
    data object ScheduleLocked : ErrorKind
    data object TimezoneLocked : ErrorKind
    data object InvalidCheckIn : ErrorKind
    data object Unknown : ErrorKind
}
