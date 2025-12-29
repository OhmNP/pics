package com.photosync.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun testThrottling() {
        var currentTime = 0L
        val timeSource = { currentTime }
        val limiter = RateLimiter(100, timeSource) // 100ms interval
        
        // First call should succeed
        assertTrue("First call should be allowed", limiter.tryAcquire())
        
        // Immediate second call should fail (time hasn't advanced)
        assertFalse("Second call within interval should be throttled", limiter.tryAcquire())
        
        // Advance time by 50ms (still within 100ms interval)
        currentTime = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50)
        assertFalse("Call after 50ms should be throttled", limiter.tryAcquire())
        
        // Advance time to 101ms
        currentTime = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(101)
        
        // Third call should succeed
        assertTrue("Call after interval should be allowed", limiter.tryAcquire())
        
        // Immediate fourth call should fail
        assertFalse("Call immediately after successful one should be throttled", limiter.tryAcquire())
    }
    
    @Test
    fun testReset() {
        // Start at 100 seconds to avoid 0 confusions
        var currentTime = java.util.concurrent.TimeUnit.SECONDS.toNanos(100)
        val limiter = RateLimiter(1000, { currentTime })
        
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
        
        limiter.reset()
        
        assertTrue("Should allow call immediately after reset", limiter.tryAcquire())
    }

    @Test
    fun testTimeJumps() {
        // This test simulates time going "backwards" or weird jumps if the source was unstable,
        // but since we control the source, we can prove the logic holds for monotonic increases.
        // The main benefit of the fix is that System.nanoTime is monotonic.
        // We can test that "large gaps" work fine.
        
        var currentTime = java.util.concurrent.TimeUnit.HOURS.toNanos(1)
        val limiter = RateLimiter(100, { currentTime })
        
        assertTrue(limiter.tryAcquire())
        
        // Jump forward 1 hour
        currentTime += java.util.concurrent.TimeUnit.HOURS.toNanos(1)
        assertTrue(limiter.tryAcquire())
    }
}
