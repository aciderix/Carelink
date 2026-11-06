package com.example.audio

import android.content.Context
import android.media.*
import android.os.Build

class AudioPlayerEngine(private val context: Context, private val speaker: Boolean = false) : AutoCloseable {
    private var track: AudioTrack? = null
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focus: AudioFocusRequest? = null
    private var oldMode = AudioManager.MODE_NORMAL
    @Volatile var isPlaying = false; private set
    var onError: ((String) -> Unit)? = null
    fun start() {
        if (isPlaying) return
        val attributes = AudioAttributes.Builder().setUsage(if (speaker) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        try {
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                if (change < 0) { onError?.invoke("Écoute interrompue par une autre source audio"); close() }
            }
            val granted = if (Build.VERSION.SDK_INT >= 26) {
                focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(listener).build()
                manager.requestAudioFocus(focus!!)
            } else {
                @Suppress("DEPRECATION") manager.requestAudioFocus(listener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            check(granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Sortie audio indisponible" }
            if (speaker) {
                oldMode = manager.mode; manager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= 31) manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { manager.setCommunicationDevice(it) }
                else {
                    @Suppress("DEPRECATION")
                    manager.isSpeakerphoneOn = true
                }
            }
            track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(AudioFormat.Builder()
                .setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(4096, AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track!!.play(); isPlaying = true
        } catch (e: Exception) { close(); throw e }
    }
    fun writeAudio(bytes: ByteArray) {
        check(isPlaying) { "Écoute interrompue" }
        var offset = 0
        while (offset < bytes.size) {
            val written = track?.write(bytes, offset, bytes.size - offset) ?: -1
            check(written > 0) { "Erreur de lecture audio" }; offset += written
        }
    }
    override fun close() {
        isPlaying = false
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
        if (Build.VERSION.SDK_INT >= 26) focus?.let { manager.abandonAudioFocusRequest(it) }
        focus = null
        if (speaker) {
            if (Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice()
            else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = false
            }
            manager.mode = oldMode
        }
    }
}
