package com.hermes.chat.network

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Retry policy with exponential backoff and jitter.
 *
 * @property maxAttempts How many times to attempt (including the first call).
 * @property baseDelayMs Initial delay before the first retry.
 * @property maxDelayMs Maximum delay cap (backoff won't exceed this).
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
)

/**
 * Execute [block] with exponential backoff retry.
 *
 * On each failure the next delay is: min(baseDelayMs * 2^attempt, maxDelayMs) + jitter(0..500ms)
 * Returns [Result.success] on the first successful call,
 * or [Result.failure] with the last exception when all attempts are exhausted.
 */
suspend fun <T> retryWithBackoff(
    policy: RetryPolicy = RetryPolicy(),
    block: suspend () -> T,
): Result<T> {
    var lastException: Exception? = null
    repeat(policy.maxAttempts) { attempt ->
        try {
            return Result.success(block())
        } catch (e: Exception) {
            lastException = e
            if (attempt < policy.maxAttempts - 1) {
                val delayMs = min(
                    policy.baseDelayMs * (1L shl attempt),
                    policy.maxDelayMs
                ) + Random.nextLong(0, 500)
                delay(delayMs)
            }
        }
    }
    return Result.failure(lastException ?: Exception("Retry exhausted"))
}
