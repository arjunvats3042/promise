package app.promise.android.data.users

import kotlinx.serialization.Serializable

@Serializable
data class LookupUserDto(
    val id: String,
    val name: String,
    val email: String,
)

@Serializable
data class UserSearchResponseDto(
    val results: List<LookupUserDto> = emptyList(),
)
