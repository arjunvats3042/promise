package app.promise.android.core.copy

/**
 * Centralized design-system copy tokens, editorial voice standards,
 * and semantic UI messaging constants for Promise.
 */
object PromiseCopy {

    // Brand Signature & Headings
    const val APP_TAGLINE = "Intentions kept. Momentum built."
    const val DAILY_FOCUS_HEADER = "DAILY FOCUS"
    const val TODAY_THOUGHT_HEADER = "TODAY'S THOUGHT"
    const val DAILY_PRACTICE_HEADER = "Daily Practice"
    const val TODAY_COMMITMENTS_HEADER = "Today's Commitments"
    const val UPCOMING_COMMITMENTS_HEADER = "Upcoming Commitments"
    const val SHARED_GOALS_HEADER = "Shared Goals"

    // Offline & Sync Status
    const val STATUS_OFFLINE_BANNER = "● Working offline · Changes saved locally"
    const val STATUS_OFFLINE_INDICATOR = "Offline"
    const val SYNC_SUCCESS_TOAST = "Synced with cloud"

    // Action Microcopy
    object Actions {
        const val CREATE_PROMISE = "Create Promise"
        const val CREATE_GOAL = "Create Goal"
        const val CREATE_COMMITMENT = "Create Commitment"
        const val QUICK_VOICE = "Speak Promise"
        const val DECOMPOSE_THOUGHTS = "Decompose Thoughts"
        const val REFINE_AI = "Refine with AI"
        const val TRY_AGAIN = "Try Again"
        const val RETRY = "Retry"
        const val DISMISS = "Dismiss"
        const val CANCEL = "Cancel"
        const val SAVE = "Save"
        const val CONFIRM = "Confirm"
        const val SIGN_IN = "Sign In"
        const val REVIEW_INVITE = "Review"
        const val EDIT_THOUGHTS = "Edit thoughts"
    }

    // AI Inspiration Prompts for Brain Dumps
    val THOUGHT_PARSER_HINTS = listOf(
        "“Review quarterly draft by 4pm, yoga for 20m on weekdays, buy groceries tonight”",
        "“Gym Mon Wed Fri, send client invoices tomorrow at 11am, read 15 pages daily”",
        "“Call Sarah tomorrow at 5pm, meditate 10 mins every morning, submit taxes Friday”",
        "“Team standup at 10am, run 5km on Saturday morning, prepare slides by Thursday”",
    )

    // Interactive Sample Chips for Empty AI Detection
    val INSPIRATION_STARTER_CHIPS = listOf(
        "Read 20 minutes daily",
        "Submit project proposal by Friday 5pm",
        "Morning workout Mon, Wed, Fri",
        "Call accountant tomorrow at 3pm",
        "Drink 2L of water every day",
    )
}
