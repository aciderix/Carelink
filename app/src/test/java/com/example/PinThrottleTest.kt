package com.example

import com.example.security.PinThrottle
import org.junit.Assert.assertEquals
import org.junit.Test

class PinThrottleTest {
    @Test fun firstAttemptsAreFreeThenDelayDoublesUpToFifteenMinutes() {
        (0..4).forEach { assertEquals(0L, PinThrottle.lockoutMs(it)) }
        assertEquals(30_000L, PinThrottle.lockoutMs(5))
        assertEquals(60_000L, PinThrottle.lockoutMs(6))
        assertEquals(480_000L, PinThrottle.lockoutMs(9))
        assertEquals(900_000L, PinThrottle.lockoutMs(10))
        assertEquals(900_000L, PinThrottle.lockoutMs(50))
    }
}
