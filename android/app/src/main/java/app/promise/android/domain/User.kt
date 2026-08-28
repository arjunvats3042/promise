package app.promise.android.domain

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val timezone: String,
    val createdAt: String,
    val emailVerified: Boolean = false,
    val hasPassword: Boolean = true,
    val googleLinked: Boolean = false,
    val avatarUrl: String? = null,
)
