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

    suspend fun reinviteParticipant(goalId: String, participantId: String? = null, userId: String? = null): GoalParticipant

    suspend fun transferOwnership(goalId: String, participantId: String? = null, userId: String? = null): GoalDetail

    suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant

    suspend fun leave(goalId: String): GoalParticipant

    suspend fun listChatMessages(
        goalId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
    ): List<ChatMessage>

    suspend fun sendChatMessage(goalId: String, body: String): ChatMessage

    suspend fun markChatRead(goalId: String, lastReadMessageId: String): GoalChatReadState

    suspend fun getChatSummary(goalId: String): GoalChatSummary

    suspend fun listActivity(
        goalId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
    ): List<GoalActivityItem>

    suspend fun searchChatMessages(
        goalId: String,
        query: String,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): List<ChatSearchResult>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val HOME_PAGE_SIZE = 100
    }
}
