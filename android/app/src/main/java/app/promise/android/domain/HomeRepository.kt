package app.promise.android.domain

import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    fun observeFeed(): Flow<HomeFeed>

    suspend fun completeCommitment(id: String)

    suspend fun checkInPractice(id: String)

    fun loadEmptyScenario()
}
