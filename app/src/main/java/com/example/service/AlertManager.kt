package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.PreferencesManager
import com.example.model.AlertEventType
import com.example.model.RoomAlertEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AlertManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val prefs = PreferencesManager(context)

    // Map of active, unacknowledged alerts keyed by roomId
    private val activeAlerts = ConcurrentHashMap<String, RoomAlertEvent>()

    private val _activeAlertsFlow = MutableStateFlow<List<RoomAlertEvent>>(emptyList())
    val activeAlertsFlow: StateFlow<List<RoomAlertEvent>> = _activeAlertsFlow.asStateFlow()

    private var repeatJob: Job? = null
    private var residentRingtone: Ringtone? = null
    private var techRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    init {
        initMedia()
    }

    private fun initMedia() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val residentUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            residentRingtone = RingtoneManager.getRingtone(context, residentUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }

            val techUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            techRingtone = RingtoneManager.getRingtone(context, techUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
        } catch (_: Exception) {}
    }

    fun triggerAlert(event: RoomAlertEvent) {
        val existing = activeAlerts[event.roomId]
        // If an alert is already unacknowledged for this room, update its decibels/duration but do not replace
        val mergedEvent = if (existing != null && !existing.isAcknowledged) {
            existing.copy(
                levelReachedDb = maxOf(existing.levelReachedDb, event.levelReachedDb),
                timestamp = event.timestamp,
                durationSeconds = existing.durationSeconds + 1
            )
        } else {
            event
        }

        activeAlerts[event.roomId] = mergedEvent
        updateFlow()

        // Post Android Notification
        CareLinkNotificationHelper.postRoomAlertNotification(context, mergedEvent)

        // Play feedback
        playFeedback(mergedEvent.eventType)

        // Start repeat scheduler if not already running
        startRepeatScheduler()
    }

    fun acknowledgeAlert(roomId: String) {
        activeAlerts.remove(roomId)
        updateFlow()
        CareLinkNotificationHelper.cancelRoomAlertNotification(context, roomId)

        if (activeAlerts.isEmpty()) {
            stopRepeatScheduler()
            stopAudio()
        }
    }

    fun acknowledgeAll() {
        for (roomId in activeAlerts.keys) {
            CareLinkNotificationHelper.cancelRoomAlertNotification(context, roomId)
        }
        activeAlerts.clear()
        updateFlow()
        stopRepeatScheduler()
        stopAudio()
    }

    private fun updateFlow() {
        _activeAlertsFlow.value = activeAlerts.values.toList()
    }

    private fun playFeedback(eventType: AlertEventType) {
        if (prefs.isVibrationAlertEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = if (eventType == AlertEventType.SOUND_ALERT) {
                        longArrayOf(0, 450, 150, 450, 150, 450)
                    } else {
                        longArrayOf(0, 250, 150, 250)
                    }
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 400, 200, 400), -1)
                }
            } catch (_: Exception) {}
        }

        if (prefs.isSoundAlertEnabled) {
            try {
                if (eventType == AlertEventType.SOUND_ALERT) {
                    if (residentRingtone?.isPlaying == false) {
                        residentRingtone?.play()
                    }
                } else {
                    if (techRingtone?.isPlaying == false) {
                        techRingtone?.play()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopAudio() {
        try {
            residentRingtone?.stop()
            techRingtone?.stop()
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun startRepeatScheduler() {
        if (repeatJob != null) return
        if (!prefs.isAlertRepeatEnabled) return

        repeatJob = scope.launch {
            while (isActive && activeAlerts.isNotEmpty()) {
                val intervalMs = (prefs.alertRepeatIntervalSeconds.coerceIn(10, 120)) * 1000L
                delay(intervalMs)
                if (activeAlerts.isNotEmpty()) {
                    val highestPriority = if (activeAlerts.values.any { it.eventType == AlertEventType.SOUND_ALERT }) {
                        AlertEventType.SOUND_ALERT
                    } else {
                        AlertEventType.DISCONNECTION
                    }
                    playFeedback(highestPriority)
                }
            }
        }
    }

    private fun stopRepeatScheduler() {
        repeatJob?.cancel()
        repeatJob = null
    }

    fun testAlertFeedback() {
        playFeedback(AlertEventType.SOUND_ALERT)
    }

    fun release() {
        stopRepeatScheduler()
        stopAudio()
    }
}
