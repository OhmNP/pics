package com.photosync.android.util

import java.util.concurrent.atomic.AtomicLong

/**
 * A threat-safe rate limiter that allows an action to be performed at most once per defined interval.
 */
class RateLimiter(
    minIntervalMs: Long,
    private val timeSource: () -> Long = { System.nanoTime() }
) {
    private val minIntervalNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(minIntervalMs)
    private val lastRun = AtomicLong(Long.MIN_VALUE)

    /**
     * Checks if the action can proceed.
     * If approved, updates the last run timestamp atomically.
     * 
     * @return true if allowed, false if throttled
     */
    fun tryAcquire(): Boolean {
        val now = timeSource()
        val last = lastRun.get()
        
        // Check if enough time has passed
        // If last is MIN_VALUE, it's the first run (or reset), so we allow it.
        if (last != Long.MIN_VALUE && (now - last < minIntervalNanos)) {
            return false
        }
        
        // Attempt to update. If it fails, another thread updated it, so we are throttled.
        return lastRun.compareAndSet(last, now)
    }
    
    /**
     * Resets the limiter state, allowing the next action immediately.
     */
    fun reset() {
        lastRun.set(Long.MIN_VALUE)
    }
}
