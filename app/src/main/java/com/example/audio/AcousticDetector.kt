package com.example.audio

/** Uses monotonic milliseconds supplied by the caller. Values are relative PCM levels, not dB SPL. */
class AcousticDetector {
    private var aboveSince: Long? = null
    private var belowSince: Long? = null
    var active = false; private set
    fun reset() { aboveSince = null; belowSince = null; active = false }
    fun process(level: Int, threshold: Int, minimumMs: Int, now: Long): Boolean {
        var triggered = false
        if (level >= threshold) {
            belowSince = null
            if (aboveSince == null) aboveSince = now
            if (!active && now - aboveSince!! >= minimumMs) { active = true; triggered = true }
        } else {
            aboveSince = null
            if (level < threshold - 6) {
                if (belowSince == null) belowSince = now
                if (now - belowSince!! >= 1500) active = false
            } else belowSince = null
        }
        return triggered
    }
}
