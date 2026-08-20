package app.promise.android.data.network

import app.promise.android.data.local.TokenStore
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

class SessionRefresher(
    private val publicApi: AuthApi,
    private val tokenStore: TokenStore,
    private val session: AuthSession,
) {
    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<Boolean>? = null

    suspend fun refresh(): Boolean {
        val waitFor: CompletableDeferred<Boolean>
        val leader: Boolean
        mutex.withLock {
            val current = inFlight
            if (current != null) {
                waitFor = current
                leader = false
            } else {
                waitFor = CompletableDeferred()
                inFlight = waitFor
                leader = true
            }
        }
        if (!leader) {
            return waitFor.await()
        }
        try {
            val result = doRefresh()
            waitFor.complete(result)
            return result
        } catch (e: kotlinx.coroutines.CancellationException) {
            waitFor.cancel(e)
            throw e
        } catch (t: Throwable) {
            waitFor.complete(false)
            throw t
        } finally {
            mutex.withLock {
                if (inFlight === waitFor) {
                    inFlight = null
                }
            }
        }
    }

    private suspend fun doRefresh(): Boolean {
        val refreshToken = tokenStore.readRefreshToken() ?: return false
        return try {
            persist(callRefresh(refreshToken))
            true
        } catch (e: ApiException) {
            if (e.isSessionEnded) {
                clearLocal()
            }
            false
        } catch (e: HttpException) {
            val parsed = e.toApiException()
            if (parsed.isSessionEnded) {
                clearLocal()
            }
            false
        } catch (_: SocketTimeoutException) {
            retrySameTokenOnce(refreshToken)
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun retrySameTokenOnce(refreshToken: String): Boolean {
        return try {
            persist(callRefresh(refreshToken))
            true
        } catch (e: ApiException) {
            if (e.isSessionEnded) {
                clearLocal()
            }
            false
        } catch (e: HttpException) {
            val parsed = e.toApiException()
            if (parsed.isSessionEnded) {
                clearLocal()
            }
            false
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun callRefresh(refreshToken: String): TokensDto {
        return publicApi.refresh(RefreshRequest(refreshToken)).tokens
    }

    private suspend fun persist(tokens: TokensDto) {
        tokenStore.saveRefreshToken(tokens.refreshToken)
        session.setAccessToken(tokens.accessToken)
    }

    private suspend fun clearLocal() {
        tokenStore.clear()
        session.clear()
    }
}
