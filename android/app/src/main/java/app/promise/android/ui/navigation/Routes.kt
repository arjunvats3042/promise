package app.promise.android.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object AuthGraphRoute

@Serializable
data object SessionRestoreRoute

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data object MainGraphRoute

@Serializable
data object HomeRoute

@Serializable
data object CommitmentsRoute

@Serializable
data object GoalsRoute

@Serializable
data object ProfileRoute

@Serializable
data class CommitmentRoute(val commitmentId: String)

@Serializable
data class GoalRoute(val goalId: String)

@Serializable
data class GoalChatRoute(val goalId: String)

@Serializable
data object SearchRoute
