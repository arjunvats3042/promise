package app.promise.android.ui.auth

/**
 * Time-aware greeting + calm typewriter reducer (pure, unit-testable).
 */
object GreetingClock {
    fun greetingForHour(hour: Int): String {
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun editorialGreetingForHour(hour: Int): String {
        return when (hour) {
            in 5..11 -> "Good morning · Set your daily intention"
            in 12..16 -> "Good afternoon · Stay in steady flow"
            in 17..21 -> "Good evening · Reflect and follow through"
            else -> "Quiet night · Rest and prepare for tomorrow"
        }
    }
}

enum class GreetingPhase {
    Idle,
    Typing,
    Holding,
    Deleting,
    Settled,
}

data class GreetingAnimationState(
    val fullText: String,
    val visibleText: String = "",
    val phase: GreetingPhase = GreetingPhase.Idle,
    val pendingText: String? = null,
)

sealed interface GreetingEvent {
    data class Start(val text: String) : GreetingEvent
    data class Change(val text: String) : GreetingEvent
    data object Tick : GreetingEvent
    data object SkipToComplete : GreetingEvent
}

object GreetingAnimator {
    fun reduce(
        state: GreetingAnimationState,
        event: GreetingEvent,
        reduceMotion: Boolean = false,
    ): GreetingAnimationState {
        return when (event) {
            is GreetingEvent.Start -> {
                if (reduceMotion) {
                    GreetingAnimationState(
                        fullText = event.text,
                        visibleText = event.text,
                        phase = GreetingPhase.Settled,
                    )
                } else {
                    GreetingAnimationState(
                        fullText = event.text,
                        visibleText = "",
                        phase = GreetingPhase.Typing,
                    )
                }
            }
            is GreetingEvent.Change -> {
                if (event.text == state.fullText && state.pendingText == null) {
                    state
                } else if (reduceMotion) {
                    GreetingAnimationState(
                        fullText = event.text,
                        visibleText = event.text,
                        phase = GreetingPhase.Settled,
                    )
                } else if (state.phase == GreetingPhase.Settled || state.phase == GreetingPhase.Holding) {
                    state.copy(pendingText = event.text, phase = GreetingPhase.Deleting)
                } else {
                    state.copy(pendingText = event.text)
                }
            }
            GreetingEvent.SkipToComplete -> {
                val target = state.pendingText ?: state.fullText
                GreetingAnimationState(
                    fullText = target,
                    visibleText = target,
                    phase = GreetingPhase.Settled,
                )
            }
            GreetingEvent.Tick -> tick(state)
        }
    }

    private fun tick(state: GreetingAnimationState): GreetingAnimationState {
        return when (state.phase) {
            GreetingPhase.Idle -> state
            GreetingPhase.Typing -> {
                if (state.visibleText.length >= state.fullText.length) {
                    state.copy(visibleText = state.fullText, phase = GreetingPhase.Settled)
                } else {
                    val next = state.fullText.take(state.visibleText.length + 1)
                    val done = next.length >= state.fullText.length
                    state.copy(
                        visibleText = next,
                        phase = if (done) GreetingPhase.Settled else GreetingPhase.Typing,
                    )
                }
            }
            GreetingPhase.Deleting -> {
                if (state.visibleText.isEmpty()) {
                    val next = state.pendingText ?: state.fullText
                    state.copy(
                        fullText = next,
                        pendingText = null,
                        visibleText = "",
                        phase = GreetingPhase.Typing,
                    )
                } else {
                    val nextVisible = state.visibleText.dropLast(1)
                    if (nextVisible.isEmpty()) {
                        val next = state.pendingText ?: state.fullText
                        state.copy(
                            fullText = next,
                            pendingText = null,
                            visibleText = "",
                            phase = GreetingPhase.Typing,
                        )
                    } else {
                        state.copy(visibleText = nextVisible)
                    }
                }
            }
            GreetingPhase.Holding, GreetingPhase.Settled -> state
        }
    }
}
