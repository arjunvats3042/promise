package app.promise.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadStateTest {
    @Test
    fun readyDefaultsIsRefreshingFalse() {
        val state = LoadState.Ready("ok")
        assertEquals("ok", state.value)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun readyCanMarkRefreshingWithoutBooleanFlags() {
        val state = LoadState.Ready(listOf(1), isRefreshing = true)
        assertTrue(state.isRefreshing)
    }

    @Test
    fun errorKeepsKindAndRetry() {
        val state = LoadState.Error(ErrorKind.Timeout, canRetry = true)
        assertEquals(ErrorKind.Timeout, state.kind)
        assertTrue(state.canRetry)
    }

    @Test
    fun emptyIsDistinctFromError() {
        val empty: LoadState<Unit> = LoadState.Empty
        assertTrue(empty is LoadState.Empty)
        assertFalse(empty is LoadState.Error)
    }
}
