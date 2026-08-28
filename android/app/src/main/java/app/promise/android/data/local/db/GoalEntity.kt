package app.promise.android.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val recurrenceKind: String = "DAILY",
    val weekdaysJson: String = "[]",
    val trackingKind: String = "BINARY",
    val targetValue: Int? = null,
    val targetUnit: String = "",
    val status: String = "ACTIVE",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val checkedInToday: Boolean = false,
    val periodValue: Int = 0,
    val isShared: Boolean = false,
    val participantCount: Int = 1,
    val unreadChatCount: Int = 0,
    val isSynced: Boolean = true,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
