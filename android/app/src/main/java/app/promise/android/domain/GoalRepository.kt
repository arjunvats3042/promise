package app.promise.android.domain

interface GoalRepository {
    suspend fun list(filter: GoalListFilter, page: Int = 1): GoalPage

    suspend fun get(id: String): Goal

    suspend fun create(input: CreateGoalInput): Goal

    suspend fun pause(id: String): Goal

    suspend fun resume(id: String): Goal

    suspend fun complete(id: String): Goal

    suspend fun cancel(id: String): Goal

    suspend fun checkIn(id: String, input: CheckInInput): GoalCheckIn

    suspend fun listCheckIns(
        id: String,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 1,
    ): GoalCheckInPage
}
