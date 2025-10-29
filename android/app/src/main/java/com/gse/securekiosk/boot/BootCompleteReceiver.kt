package com.gse.securekiosk.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gse.securekiosk.MainActivity
import com.gse.securekiosk.location.LocationSyncService

enum class BootEvent {
    BOOT_COMPLETED,
    QUICKBOOT_POWERON
}

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received boot event: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                startMainActivity(context)
                startLocationService(context)
            }
        }
    }

    private fun startMainActivity(context: Context) {
        val startIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(startIntent)
    }

    private fun startLocationService(context: Context) {
        val serviceIntent = Intent(context, LocationSyncService::class.java)
        context.startForegroundService(serviceIntent)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
