package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.PreferencesManager
import com.example.model.DeviceRole

/** Android does not permit a generic automatic microphone restart after boot. Ask the operator. */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (PreferencesManager(context).role != DeviceRole.NONE) CareLinkNotificationHelper.restartReminder(context)
    }
}
