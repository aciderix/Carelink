package com.example

import com.example.audio.AcousticDetector
import org.junit.Assert.*
import org.junit.Test

class AcousticDetectorTest {
    @Test fun shortSoundDoesNotTrigger() {
        val detector = AcousticDetector()
        assertFalse(detector.process(60, 55, 300, 0))
        assertFalse(detector.process(60, 55, 300, 250))
        assertFalse(detector.process(20, 55, 300, 290))
        assertFalse(detector.active)
    }
    @Test fun continuousEpisodeTriggersOnlyOnce() {
        val detector = AcousticDetector()
        assertFalse(detector.process(60, 55, 300, 100))
        assertTrue(detector.process(60, 55, 300, 400))
        repeat(100) { assertFalse(detector.process(70, 55, 300, 500L + it * 64)) }
        assertTrue(detector.active)
    }
    @Test fun hysteresisRequiresContinuousCalm() {
        val d = AcousticDetector()
        d.process(65, 55, 300, 0); d.process(65, 55, 300, 300)
        d.process(40, 55, 300, 400)
        d.process(51, 55, 300, 1800) // Hysteresis band interrupts the calm period.
        d.process(40, 55, 300, 2000)
        assertTrue(d.active)
        d.process(40, 55, 300, 3400); assertTrue(d.active)
        d.process(40, 55, 300, 3500); assertFalse(d.active)
        d.process(65, 55, 300, 3600); assertTrue(d.process(65, 55, 300, 3900))
    }
    @Test fun anInterruptionResetsMinimumDuration() {
        val d = AcousticDetector()
        d.process(65, 55, 300, 0); d.process(30, 55, 300, 299)
        assertFalse(d.process(65, 55, 300, 300))
        assertFalse(d.process(65, 55, 300, 599))
        assertTrue(d.process(65, 55, 300, 600))
    }
}
