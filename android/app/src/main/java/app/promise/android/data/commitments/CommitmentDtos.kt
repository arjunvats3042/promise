package app.promise.android.data.commitments

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommitmentDto(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String,
    @SerialName("due_at")
    val dueAt: String? = null,
    @SerialName("due_precision")
    val duePrecision: String = "NONE",
    val source: String = "MANUAL",
    @SerialName("snoozed_until")
    val snoozedUntil: String? = null,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("cancelled_at")
    val cancelledAt: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("is_overdue")
    val isOverdue: Boolean = false,
)

@Serializable
data class CommitmentPageDto(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<CommitmentDto> = emptyList(),
)

@Serializable
data class CreateCommitmentRequest(
    val title: String,
    val description: String = "",
    @SerialName("due_at")
    val dueAt: String? = null,
    @SerialName("due_precision")
    val duePrecision: String = "NONE",
)

@Serializable
data class SnoozeRequest(
    @SerialName("snoozed_until")
    val snoozedUntil: String,
)
