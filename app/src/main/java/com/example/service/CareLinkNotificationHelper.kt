package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesManager
import com.example.model.RoomAlertEvent

object CareLinkNotificationHelper {
    const val ONGOING = "carelink_status_v2"
    const val ALERTS = "carelink_events_v2"
    const val NOTIFICATION_ID_ROOM_ONGOING = 1001
    const val NOTIFICATION_ID_MONITOR_ONGOING = 1002
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(ONGOING, "État de la surveillance", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(ALERTS, "Événements à prendre en charge", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Sons et vibrations réglables dans Android ; aucun contournement de Ne pas déranger"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).build()); enableVibration(true)
            })
        }
    }
    private fun launch(context: Context, id: String? = null): PendingIntent = PendingIntent.getActivity(context,
        0, Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            id?.let { putExtra("selected_room_id", it) }
            data = android.net.Uri.parse("carelink://open/${id ?: "home"}")
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun ongoing(context: Context, title: String, body: String): Notification = NotificationCompat.Builder(context, ONGOING)
        .setSmallIcon(R.drawable.ic_stat_carelink).setContentTitle(title).setContentText(body)
        .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(launch(context))
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    fun post(context: Context, event: RoomAlertEvent, remind: Boolean = false) {
        if (event.resolved) { cancel(context, event.eventId); return }
        val prefs = PreferencesManager(context)
        val title = if (event.isAcknowledged) "Prise en charge · ${event.roomName}" else "À vérifier · ${event.roomName}"
        val n = NotificationCompat.Builder(context, ALERTS).setSmallIcon(R.drawable.ic_stat_carelink)
            .setContentTitle(title).setContentText(event.notes.ifBlank { event.eventType.name })
            .setContentIntent(launch(context, event.roomId)).setAutoCancel(false).setOngoing(!event.isAcknowledged)
            .setOnlyAlertOnce(!remind).setSilent(event.isAcknowledged || !prefs.isSoundAlertEnabled)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (Build.VERSION.SDK_INT < 26) n.setVibrate(if (prefs.isVibrationAlertEnabled) longArrayOf(0, 350, 200, 350) else longArrayOf(0))
        // Human transitions require the app, preventing accidental lock-screen acknowledgements.
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(event.eventId, 2000, n.build()) }
    }
    fun cancel(context: Context, id: String) = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id, 2000)
    fun readiness(context: Context): List<String> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return buildList {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) add("Notifications non autorisées")
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = nm.getNotificationChannel(ALERTS)
                if (channel == null || channel.importance < NotificationManager.IMPORTANCE_HIGH) add("Canal d’alerte désactivé ou peu visible")
                if (channel?.sound == null) add("Canal d’alerte sans son")
                if (channel != null && !channel.shouldVibrate()) add("Vibration du canal désactivée")
            }
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM) == 0) add("Volume des alarmes nul")
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) add("Ne pas déranger actif : vérifier l’audibilité")
            if (!PreferencesManager(context).isSoundAlertEnabled) add("Son désactivé dans CareLink")
        }
    }
    fun restartReminder(context: Context) {
        createNotificationChannels(context)
        val n = NotificationCompat.Builder(context, ALERTS).setSmallIcon(R.drawable.ic_stat_carelink)
            .setContentTitle("CareLink · reprise nécessaire").setContentText("Ouvrez l’application pour vérifier et reprendre la surveillance.")
            .setContentIntent(launch(context)).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1003, n) }
    }
}
