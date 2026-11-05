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
import com.example.model.*

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
    fun groupTag(roomId: String, kind: AlertEventType) = "carelink:$roomId:${kind.name}"
    fun cancelGroup(context: Context, roomId: String, kind: AlertEventType) =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(groupTag(roomId, kind), 2000)
    fun postGroup(context: Context, group: AlertGroup, pending: Set<String> = emptySet(), remind: Boolean = false) {
        if (!group.needsAttention(pending)) { cancelGroup(context, group.roomId, group.eventType); return }
        val prefs = PreferencesManager(context)
        val title = "À vérifier · ${group.roomName}"
        val kind = when (group.eventType) {
            AlertEventType.SOUND_ALERT -> "Bruit détecté"
            AlertEventType.DISCONNECTION -> "Connexion interrompue"
            AlertEventType.MIC_ERROR -> "Incident microphone"
            AlertEventType.LOW_BATTERY -> "Batterie faible"
            AlertEventType.SERVICE_INTERRUPTED -> "Incident technique"
            AlertEventType.TEST -> "Test de transmission"
        }
        val body = kind + if (group.occurrences > 1) " · ${group.occurrences} occurrences" else ""
        val n = NotificationCompat.Builder(context, ALERTS).setSmallIcon(R.drawable.ic_stat_carelink)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(launch(context, group.roomId)).setAutoCancel(false).setOngoing(true)
            .setOnlyAlertOnce(!remind).setSilent(!prefs.isSoundAlertEnabled)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (Build.VERSION.SDK_INT < 26) n.setVibrate(if (prefs.isVibrationAlertEnabled) longArrayOf(0, 350, 200, 350) else longArrayOf(0))
        // Human transitions require the app, preventing accidental lock-screen acknowledgements.
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(groupTag(group.roomId, group.eventType), 2000, n.build()) }
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
