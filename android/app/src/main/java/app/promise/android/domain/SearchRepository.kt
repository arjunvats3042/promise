package app.promise.android.domain

interface SearchRepository {
    suspend fun search(query: String, type: String = "all"): GlobalSearchResult
}
