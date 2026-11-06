package com.example

import android.media.MediaRecorder
import com.example.audio.AudioCaptureEngine
import org.junit.Assert.*
import org.junit.Test

class CaptureSourceTest {
    @Test fun standardMicrophoneIsTriedFirstThenUnprocessedAndHigherRates() {
        val configs = AudioCaptureEngine.CONFIGS
        assertEquals(MediaRecorder.AudioSource.MIC, configs.first().source)
        assertEquals(16000, configs.first().rate)
        assertTrue(configs.any { it.source == MediaRecorder.AudioSource.VOICE_RECOGNITION })
        assertTrue(configs.all { it.rate % AudioCaptureEngine.RATE == 0 })
        assertEquals(configs.size, configs.map { it.source to it.rate }.toSet().size)
    }
    @Test fun silentSourceIsSwappedBeforeItCouldBeReportedAsAFault() {
        assertTrue(AudioCaptureEngine.ROTATE_MS < AudioCaptureEngine.ZERO_LIMIT_MS)
    }
    @Test fun decimationAveragesTo16kHz() {
        val input = shortArrayOf(3, 6, 9, -3, -6, -9, 0, 0, 0, 1)
        assertArrayEquals(shortArrayOf(6, -6, 0), AudioCaptureEngine.decimate(input, input.size, 3))
        assertArrayEquals(shortArrayOf(1, 2), AudioCaptureEngine.decimate(shortArrayOf(1, 2, 3), 2, 1))
    }
}
