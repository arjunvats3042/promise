package app.promise.android.ui.auth

/**
 * Startup / login gate for Android 17 local-network access.
 * NotRequired on older SDKs and for non-LAN API hosts (release/production).
 */
sealed interface LocalNetworkAccessState {
    data object NotRequired : LocalNetworkAccessState
    data object NeedsRequest : LocalNetworkAccessState
    data object Denied : LocalNetworkAccessState
    data object Granted : LocalNetworkAccessState
}
