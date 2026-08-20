package app.promise.android.domain

interface CommitmentRepository {
    suspend fun list(
        filter: CommitmentListFilter,
        page: Int = 1,
        timeZoneId: String,
    ): CommitmentPage

    suspend fun get(id: String): Commitment

    suspend fun create(input: CreateCommitmentInput): Commitment

    suspend fun complete(id: String): Commitment

    suspend fun snooze(id: String, snoozedUntilIso: String): Commitment

    suspend fun unsnooze(id: String): Commitment

    suspend fun wait(id: String): Commitment

    suspend fun cancel(id: String): Commitment
}
