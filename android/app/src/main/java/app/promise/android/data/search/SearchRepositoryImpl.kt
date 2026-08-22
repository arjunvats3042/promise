package app.promise.android.data.search

import app.promise.android.data.network.toApiException
import app.promise.android.domain.GlobalSearchResult
import app.promise.android.domain.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val api: SearchApi,
) : SearchRepository {

    override suspend fun search(query: String, type: String): GlobalSearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return GlobalSearchResult()
        }
        return try {
            val dto = api.search(query = trimmed, type = type)
            dto.toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }
}
