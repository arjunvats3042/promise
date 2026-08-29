package app.promise.android.ui.navigation

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface DeepLinkDestination {
    data class Commitment(val id: String) : DeepLinkDestination
    data class Goal(val id: String) : DeepLinkDestination
    data class GoalChat(val id: String) : DeepLinkDestination
    data object Home : DeepLinkDestination
    data object Roadmap : DeepLinkDestination
    data object Profile : DeepLinkDestination
    data object VoiceCapture : DeepLinkDestination
}

@Singleton
class DeepLinkRouter @Inject constructor() {

    private val _destinations = MutableSharedFlow<DeepLinkDestination>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val destinations: SharedFlow<DeepLinkDestination> = _destinations.asSharedFlow()

    fun routeUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val dest = parseUriToDestination(uri) ?: return false
        _destinations.tryEmit(dest)
        return true
    }

    fun routeEntity(entityType: String, entityId: String, eventType: String = ""): Boolean {
        val upperType = entityType.uppercase()
        if (entityId.isBlank() && upperType != "DIGEST" && upperType != "SYSTEM" && upperType != "VOICE" && upperType != "VOICE_CAPTURE") {
            return false
        }
        val dest = when {
            upperType == "VOICE" || upperType == "VOICE_CAPTURE" -> {
                DeepLinkDestination.VoiceCapture
            }
            eventType == "goal.chat.message_created" || eventType.startsWith("goal.chat.") -> {
                DeepLinkDestination.GoalChat(entityId)
            }
            entityType.equals("GOAL", ignoreCase = true) || entityType.equals("PRACTICE", ignoreCase = true) -> {
                DeepLinkDestination.Goal(entityId)
            }
            entityType.equals("COMMITMENT", ignoreCase = true) -> {
                DeepLinkDestination.Commitment(entityId)
            }
            entityType.equals("DIGEST", ignoreCase = true) || entityType.equals("SYSTEM", ignoreCase = true) -> {
                DeepLinkDestination.Home
            }
            else -> DeepLinkDestination.Home
        }
        _destinations.tryEmit(dest)
        return true
    }

    fun parseUriToDestination(uri: Uri): DeepLinkDestination? {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "promise" && scheme != "https") return null

        val host = uri.host?.lowercase().orEmpty()
        val pathSegments = uri.pathSegments

        // promise://voice or promise:///voice
        if (host == "voice") {
            return DeepLinkDestination.VoiceCapture
        }
        // promise://commitment/{id}
        if (host == "commitment" && pathSegments.isNotEmpty()) {
            return DeepLinkDestination.Commitment(pathSegments[0])
        }
        // promise://goal/{id}/chat or promise://goal/{id}
        if (host == "goal" && pathSegments.isNotEmpty()) {
            val goalId = pathSegments[0]
            return if (pathSegments.size > 1 && pathSegments[1].equals("chat", ignoreCase = true)) {
                DeepLinkDestination.GoalChat(goalId)
            } else {
                DeepLinkDestination.Goal(goalId)
            }
        }
        // promise://home
        if (host == "home") {
            return DeepLinkDestination.Home
        }
        // promise://roadmap
        if (host == "roadmap") {
            return DeepLinkDestination.Roadmap
        }
        // promise://profile
        if (host == "profile") {
            return DeepLinkDestination.Profile
        }

        // Alternative path parsing for https or full paths (e.g. promise:///goal/123/chat)
        if (pathSegments.size >= 1) {
            when (pathSegments[0].lowercase()) {
                "voice" -> return DeepLinkDestination.VoiceCapture
                "home" -> return DeepLinkDestination.Home
                "roadmap" -> return DeepLinkDestination.Roadmap
                "profile" -> return DeepLinkDestination.Profile
                "commitment" -> if (pathSegments.size >= 2) return DeepLinkDestination.Commitment(pathSegments[1])
                "goal" -> {
                    if (pathSegments.size >= 2) {
                        val goalId = pathSegments[1]
                        return if (pathSegments.size >= 3 && pathSegments[2].equals("chat", ignoreCase = true)) {
                            DeepLinkDestination.GoalChat(goalId)
                        } else {
                            DeepLinkDestination.Goal(goalId)
                        }
                    }
                }
            }
        }
        return null
    }

    fun consumeLatest(): DeepLinkDestination? {
        return _destinations.replayCache.lastOrNull()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clearReplay() {
        _destinations.resetReplayCache()
    }
}
