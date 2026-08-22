package app.promise.android.core.events

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class MembershipChangeType {
    INVITED,
    ACCEPTED,
    DECLINED,
    REMOVED,
    LEFT,
}

sealed interface AppMutationEvent {
    // Goal events
    data class GoalCreated(val goalId: String) : AppMutationEvent
    data class GoalUpdated(val goalId: String) : AppMutationEvent
    data class GoalCheckedIn(val goalId: String) : AppMutationEvent
    data class GoalPaused(val goalId: String) : AppMutationEvent
    data class GoalResumed(val goalId: String) : AppMutationEvent
    data class GoalCompleted(val goalId: String) : AppMutationEvent
    data class GoalCancelled(val goalId: String) : AppMutationEvent
    data class SharedGoalMembershipChanged(
        val goalId: String,
        val changeType: MembershipChangeType = MembershipChangeType.ACCEPTED,
    ) : AppMutationEvent

    // Commitment events
    data class CommitmentCreated(val commitmentId: String) : AppMutationEvent
    data class CommitmentUpdated(val commitmentId: String) : AppMutationEvent
    data class CommitmentCompleted(val commitmentId: String) : AppMutationEvent
    data class CommitmentCancelled(val commitmentId: String) : AppMutationEvent
    data class CommitmentSnoozed(val commitmentId: String) : AppMutationEvent

    // Profile / Notification preferences
    data object NotificationPreferencesChanged : AppMutationEvent

    // Chat
    data class ChatMessageCreated(val goalId: String, val messageId: String) : AppMutationEvent
}

@Singleton
class AppEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppMutationEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AppMutationEvent> = _events.asSharedFlow()

    fun emit(event: AppMutationEvent) {
        _events.tryEmit(event)
    }
}
