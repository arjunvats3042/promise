package app.promise.android.data.search

import app.promise.android.domain.CommitmentSearchResult
import app.promise.android.domain.GlobalSearchResult
import app.promise.android.domain.GoalSearchResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommitmentSearchResultDto(
    val id: String,
    @SerialName("numeric_id") val numericId: Long? = null,
    val title: String,
    val description: String = "",
    val status: String,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("due_precision") val duePrecision: String = "NONE",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class GoalSearchResultDto(
    val id: String,
    @SerialName("numeric_id") val numericId: Long? = null,
    val title: String,
    val description: String = "",
    val status: String,
    @SerialName("recurrence_kind") val recurrenceKind: String = "DAILY",
    @SerialName("cadence") val cadence: String = "DAILY",
    @SerialName("start_date") val startDate: String,
    @SerialName("target_value") val targetValue: Int? = null,
    @SerialName("is_shared") val isShared: Boolean = false,
    @SerialName("participant_count") val participantCount: Int = 1,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class GlobalSearchResponseDto(
    val commitments: List<CommitmentSearchResultDto> = emptyList(),
    val goals: List<GoalSearchResultDto> = emptyList(),
    @SerialName("shared_goals") val sharedGoals: List<GoalSearchResultDto> = emptyList(),
)

fun CommitmentSearchResultDto.toDomain() = CommitmentSearchResult(
    id = id,
    numericId = numericId,
    title = title,
    description = description,
    status = status,
    dueAt = dueAt,
    duePrecision = duePrecision,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun GoalSearchResultDto.toDomain() = GoalSearchResult(
    id = id,
    numericId = numericId,
    title = title,
    description = description,
    status = status,
    recurrenceKind = recurrenceKind,
    startDate = startDate,
    targetValue = targetValue,
    isShared = isShared,
    participantCount = participantCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun GlobalSearchResponseDto.toDomain() = GlobalSearchResult(
    commitments = commitments.map { it.toDomain() },
    goals = goals.map { it.toDomain() },
    sharedGoals = sharedGoals.map { it.toDomain() },
)
