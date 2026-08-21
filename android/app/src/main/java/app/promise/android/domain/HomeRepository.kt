package app.promise.android.domain

interface HomeRepository {
    suspend fun loadFeed(timeZoneId: String): HomeFeed

    suspend fun completeCommitment(id: String)

    suspend fun checkInPractice(id: String, input: CheckInInput)
}
