package app.promise.android.data.home

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory Home freshness: last successful load + dirty flag after Commitment/Goal mutations.
 * Not an event bus; no persistent cache.
 */
@Singleton
class HomeFreshness @Inject constructor() {
    @Volatile
    private var lastSuccessfulLoadMs: Long = 0L

    @Volatile
    private var dirty: Boolean = true

    fun markDirty() {
        dirty = true
    }

    fun markSuccessfulLoad(nowMs: Long = System.currentTimeMillis()) {
        lastSuccessfulLoadMs = nowMs
        dirty = false
    }

    fun shouldRefresh(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (dirty) return true
        if (lastSuccessfulLoadMs == 0L) return true
        return nowMs - lastSuccessfulLoadMs >= FRESH_TTL_MS
    }

    companion object {
        const val FRESH_TTL_MS = 60_000L
    }
}
