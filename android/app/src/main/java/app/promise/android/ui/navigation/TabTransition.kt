package app.promise.android.ui.navigation

/**
 * Pure helpers for direction-aware tab transitions.
 */
object TabTransition {
    fun indexOf(route: Any?): Int? {
        return when (route) {
            is HomeRoute -> 0
            is CommitmentsRoute -> 1
            is GoalsRoute -> 2
            is ProfileRoute -> 3
            else -> null
        }
    }

    /** +1 enters from the trailing edge (forward); -1 from the leading edge. */
    fun enterSlideSign(fromIndex: Int, toIndex: Int): Int {
        return if (toIndex >= fromIndex) 1 else -1
    }

    fun exitSlideSign(fromIndex: Int, toIndex: Int): Int {
        return if (toIndex >= fromIndex) -1 else 1
    }
}
