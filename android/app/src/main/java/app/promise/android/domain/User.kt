package app.promise.android.domain

data class User(
    val id: String,
    val email: String,
    val name: String,
    val timezone: String,
    val createdAt: String,
    val emailVerified: Boolean = false,
    val hasPassword: Boolean = true,
    val googleLinked: Boolean = false,
)
