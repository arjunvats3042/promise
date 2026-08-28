package app.promise.android.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val actionId: String,
    val actionType: String,
    val entityId: String,
    val payloadJson: String = "{}",
    val clientTimestampIso: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = "PENDING", // PENDING, IN_FLIGHT, FAILED
)
