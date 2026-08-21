package app.promise.android.domain

interface GoalRepository {
    suspend fun list(
        filter: GoalListFilter,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): GoalPage

    suspend fun get(id: String): Goal

    suspend fun getDetail(id: String): GoalDetail

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

    suspend fun inviteParticipant(goalId: String, userId: String): GoalParticipant

    suspend fun listParticipants(goalId: String): List<GoalParticipant>

    suspend fun acceptInvitation(goalId: String): GoalDetail

    suspend fun declineInvitation(goalId: String): GoalParticipant

    suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant

    suspend fun leave(goalId: String): GoalParticipant

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val HOME_PAGE_SIZE = 100
    }
}
