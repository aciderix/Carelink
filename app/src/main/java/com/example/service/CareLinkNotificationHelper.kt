package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AlertEventType
import com.example.model.RoomAlertEvent

object CareLinkNotificationHelper {

    const val CHANNEL_ONGOING_SURVEILLANCE = "carelink_ongoing_surveillance"
    const val CHANNEL_RESIDENT_ALERTS = "carelink_resident_alerts"
    const val CHANNEL_TECHNICAL_ALERTS = "carelink_technical_alerts"

    const val NOTIFICATION_ID_ROOM_ONGOING = 1001
    const val NOTIFICATION_ID_MONITOR_ONGOING = 1002
    private const val BASE_ROOM_ALERT_NOTIFICATION_ID = 2000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel 1: Ongoing active surveillance
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_SURVEILLANCE,
                "Surveillance active (Arrière-plan)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient la surveillance active lorsque l'écran est éteint"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(ongoingChannel)

            // Channel 2: Resident acoustic alerts (High priority, sound, vibration)
            val residentSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val residentChannel = NotificationChannel(
                CHANNEL_RESIDENT_ALERTS,
                "Alertes sonores résidents",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes sonores lors d'un dépassement de seuil acoustique"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(
                    residentSoundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(residentChannel)

            // Channel 3: Technical alerts (Disconnections, mic issues, battery)
            val techSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val technicalChannel = NotificationChannel(
                CHANNEL_TECHNICAL_ALERTS,
                "Alertes techniques & Déconnexions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avertissements lors d'une perte de connexion ou incident matériel"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(
                    techSoundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(technicalChannel)
        }
    }

    fun buildRoomOngoingNotification(context: Context, roomName: String): Notification {
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, RoomMonitoringService::class.java).apply {
            action = RoomMonitoringService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ONGOING_SURVEILLANCE)
            .setContentTitle("CareLink – Surveillance active")
            .setContentText("Microphone actif : $roomName")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun buildMonitorOngoingNotification(context: Context, activeRoomsCount: Int, alertCount: Int): Notification {
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, MonitorMonitoringService::class.java).apply {
            action = MonitorMonitoringService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = when {
            alertCount > 0 -> "$alertCount alerte(s) active(s) • $activeRoomsCount chambre(s)"
            activeRoomsCount > 0 -> "$activeRoomsCount chambre(s) connectée(s) et surveillée(s)"
            else -> "En attente de connexion des chambres..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ONGOING_SURVEILLANCE)
            .setContentTitle("CareLink – Poste de Surveillance")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun postRoomAlertNotification(context: Context, event: RoomAlertEvent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = if (event.eventType == AlertEventType.SOUND_ALERT) {
            CHANNEL_RESIDENT_ALERTS
        } else {
            CHANNEL_TECHNICAL_ALERTS
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("selected_room_id", event.roomId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            event.roomId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val ackIntent = Intent(context, MonitorMonitoringService::class.java).apply {
            action = MonitorMonitoringService.ACTION_ACKNOWLEDGE
            putExtra(MonitorMonitoringService.EXTRA_ROOM_ID, event.roomId)
        }
        val ackPendingIntent = PendingIntent.getService(
            context,
            event.roomId.hashCode() + 1,
            ackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (event.eventType) {
            AlertEventType.SOUND_ALERT -> "Alerte sonore : ${event.roomName}"
            AlertEventType.DISCONNECTION -> "Déconnexion : ${event.roomName}"
            AlertEventType.MIC_ERROR -> "Incident micro : ${event.roomName}"
            AlertEventType.LOW_BATTERY -> "Batterie faible : ${event.roomName}"
            AlertEventType.SERVICE_INTERRUPTED -> "Service interrompu : ${event.roomName}"
        }

        val body = when (event.eventType) {
            AlertEventType.SOUND_ALERT -> "Niveau sonore : ${event.levelReachedDb} dB (Seuil : ${event.thresholdDb} dB)"
            AlertEventType.DISCONNECTION -> "La chambre ne répond plus depuis le réseau Wi-Fi"
            else -> event.notes.ifEmpty { "Intervention requise" }
        }

        val notification = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_input_get, "Acquitter", ackPendingIntent)
            .build()

        val notificationId = BASE_ROOM_ALERT_NOTIFICATION_ID + (event.roomId.hashCode() and 0x7FFF)
        notificationManager.notify(notificationId, notification)
    }

    fun cancelRoomAlertNotification(context: Context, roomId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = BASE_ROOM_ALERT_NOTIFICATION_ID + (roomId.hashCode() and 0x7FFF)
        notificationManager.cancel(notificationId)
    }
}
