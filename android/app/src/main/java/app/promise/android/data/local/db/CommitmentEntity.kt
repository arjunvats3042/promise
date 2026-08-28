package app.promise.android.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commitments")
data class CommitmentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val dueAt: String? = null,
    val duePrecision: String = "NONE",
    val status: String = "OPEN",
    val completedAt: String? = null,
    val isOverdue: Boolean = false,
    val isDueToday: Boolean = false,
    val isSynced: Boolean = true,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
