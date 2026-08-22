package app.promise.android.domain

data class CommitmentSearchResult(
    val id: String,
    val numericId: Long? = null,
    val title: String,
    val description: String = "",
    val status: String,
    val dueAt: String? = null,
    val duePrecision: String = "NONE",
    val createdAt: String,
    val updatedAt: String,
)

data class GoalSearchResult(
    val id: String,
    val numericId: Long? = null,
    val title: String,
    val description: String = "",
    val status: String,
    val recurrenceKind: String,
    val startDate: String,
    val targetValue: Int? = null,
    val isShared: Boolean = false,
    val participantCount: Int = 1,
    val createdAt: String,
    val updatedAt: String,
)

data class GlobalSearchResult(
    val commitments: List<CommitmentSearchResult> = emptyList(),
    val goals: List<GoalSearchResult> = emptyList(),
    val sharedGoals: List<GoalSearchResult> = emptyList(),
) {
    val isEmpty: Boolean
        get() = commitments.isEmpty() && goals.isEmpty() && sharedGoals.isEmpty()
}

data class ChatSearchResult(
    val id: String,
    val messageId: String,
    val sender: ChatMessageSender,
    val body: String,
    val snippet: String,
    val createdAt: String,
)
