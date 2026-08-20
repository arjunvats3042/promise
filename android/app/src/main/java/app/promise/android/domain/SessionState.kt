package app.promise.android.domain

sealed interface SessionState {
    data object Restoring : SessionState
    data object Unauthenticated : SessionState
    data class Authenticated(val user: User) : SessionState
}
