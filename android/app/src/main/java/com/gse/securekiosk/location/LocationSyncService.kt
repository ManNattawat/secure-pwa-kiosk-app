package com.gse.securekiosk.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gse.securekiosk.MainActivity
import com.gse.securekiosk.R
import com.gse.securekiosk.supabase.SupabaseClient
import com.gse.securekiosk.util.DeviceConfig
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocationSyncService : LifecycleService() {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = TimeUnit.MINUTES.toMillis(5)
        fastestInterval = TimeUnit.MINUTES.toMillis(2)
        priority = Priority.PRIORITY_HIGH_ACCURACY
        maxWaitTime = TimeUnit.MINUTES.toMillis(15)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            val location = result.lastLocation ?: return
            sendLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        requestLocationUpdates()
        schedulePeriodicStatusUpload()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun requestLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun sendLocation(location: Location) {
        val deviceId = DeviceConfig.getDeviceId(this)
        lifecycleScope.launch {
            SupabaseClient(this@LocationSyncService).sendLocation(
                deviceId = deviceId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy.toDouble(),
                bearing = location.bearing.toDouble(),
                speed = location.speed.toDouble(),
                recordedAt = location.time
            )
        }
    }

    private fun schedulePeriodicStatusUpload() {
        val workRequest = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun buildNotification(): Notification {
        val channelId = createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.config_notification_title))
            .setContentText(getString(R.string.config_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel(): String {
        val channelId = "secure_kiosk_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.config_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.config_notification_channel_desc)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val UNIQUE_WORK_NAME = "secure-kiosk-location-work"
    }
}
