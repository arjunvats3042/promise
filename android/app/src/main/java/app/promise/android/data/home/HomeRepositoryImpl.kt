package app.promise.android.data.home

import app.promise.android.core.ErrorKind
import app.promise.android.core.toErrorKind
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.toApiException
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.HomeFeed
import app.promise.android.domain.HomeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val commitmentRepository: CommitmentRepository,
    private val goalRepository: GoalRepository,
) : HomeRepository {
    override suspend fun loadFeed(timeZoneId: String): HomeFeed = coroutineScope {
        val commitmentsDeferred = async {
            runCatching {
                commitmentRepository.list(
                    filter = CommitmentListFilter.OPEN,
                    page = 1,
                    timeZoneId = timeZoneId,
                    pageSize = CommitmentRepository.HOME_PAGE_SIZE,
                ).items
            }
        }
        val goalsDeferred = async {
            runCatching {
                goalRepository.list(
                    filter = GoalListFilter.ACTIVE,
                    page = 1,
                    pageSize = GoalRepository.HOME_PAGE_SIZE,
                ).items.mapNotNull { (it as? GoalListItem.Membership)?.goal }
            }
        }

        val commitmentsResult = commitmentsDeferred.await()
        val goalsResult = goalsDeferred.await()

        val commitmentsError = commitmentsResult.exceptionOrNull()?.toHomeError()
        val practicesError = goalsResult.exceptionOrNull()?.toHomeError()

        if (commitmentsError != null && practicesError != null) {
            throw (commitmentsResult.exceptionOrNull() ?: goalsResult.exceptionOrNull())!!
                .let { if (it is ApiException) it else it.toApiException() }
        }

        HomeFeed(
            commitments = commitmentsResult.getOrNull()
                ?.let { HomeFeedMapper.commitmentsForHome(it, timeZoneId) }
                .orEmpty(),
            practices = goalsResult.getOrNull()
                ?.let { HomeFeedMapper.practicesForHome(it) }
                .orEmpty(),
            commitmentsError = commitmentsError,
            practicesError = practicesError,
        )
    }

    override suspend fun completeCommitment(id: String) {
        commitmentRepository.complete(id)
    }

    override suspend fun checkInPractice(id: String, input: CheckInInput) {
        goalRepository.checkIn(id, input)
    }

    private fun Throwable.toHomeError(): ErrorKind? {
        if (this is kotlinx.coroutines.CancellationException || this is java.util.concurrent.CancellationException) {
            return null
        }
        if (this is java.io.IOException && message?.contains("Canceled", ignoreCase = true) == true) {
            return null
        }
        return when (this) {
            is ApiException -> toErrorKind()
            else -> runCatching { toApiException().toErrorKind() }.getOrNull()
        }
    }
}
