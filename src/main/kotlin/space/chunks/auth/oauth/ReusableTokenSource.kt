package space.chunks.auth.oauth

import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ReusableTokenSource(
    private val base: ClientCredentialsTokenSource,
    private val expiryBuffer: Duration = Duration.ofMinutes(1),
) {
    private val lock = ReentrantLock()
    @Volatile private var cached: Token? = null

    fun token(): Token = lock.withLock {
        cached?.takeIf { it.validWithBuffer(expiryBuffer) }?.let { return it }
        base.fetchToken().also { cached = it }
    }
}