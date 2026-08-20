package app.promise.android.domain

enum class CommitmentStatus {
    PENDING,
    WAITING,
    SNOOZED,
    COMPLETED,
    CANCELLED,
}

enum class DuePrecision {
    NONE,
    DATE,
    DATETIME,
}

data class Commitment(
    val id: String,
    val title: String,
    val description: String,
    val status: CommitmentStatus,
    val dueAt: String?,
    val duePrecision: DuePrecision,
    val source: String,
    val snoozedUntil: String?,
    val completedAt: String?,
    val cancelledAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val isOverdue: Boolean,
) {
    val isTerminal: Boolean
        get() = status == CommitmentStatus.COMPLETED || status == CommitmentStatus.CANCELLED

    val canComplete: Boolean
        get() = !isTerminal

    val canSnooze: Boolean
        get() = status == CommitmentStatus.PENDING || status == CommitmentStatus.SNOOZED

    val canWait: Boolean
        get() = status == CommitmentStatus.PENDING

    val canUnsnooze: Boolean
        get() = status == CommitmentStatus.SNOOZED

    val canCancel: Boolean
        get() = !isTerminal
}

enum class CommitmentListFilter {
    OPEN,
    OVERDUE,
    TODAY,
    UPCOMING,
    DONE,
}

data class CreateCommitmentInput(
    val title: String,
    val description: String = "",
    val dueAt: String? = null,
    val duePrecision: DuePrecision = DuePrecision.NONE,
)

data class CommitmentPage(
    val items: List<Commitment>,
    val nextPage: Int?,
)
