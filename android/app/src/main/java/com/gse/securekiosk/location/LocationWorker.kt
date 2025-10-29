package com.gse.securekiosk.location

import android.content.Context
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.IntentFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gse.securekiosk.supabase.SupabaseClient
import com.gse.securekiosk.util.DeviceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val deviceId = DeviceConfig.getDeviceId(context)

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivity.activeNetworkInfo
        val networkType = activeNetwork?.typeName ?: "UNKNOWN"
        val isConnected = activeNetwork?.isConnectedOrConnecting == true

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val lockState = dpm?.lockTaskModeState ?: DevicePolicyManager.LOCK_TASK_MODE_NONE
        val kioskLocked = lockState == DevicePolicyManager.LOCK_TASK_MODE_LOCKED ||
            lockState == DevicePolicyManager.LOCK_TASK_MODE_PINNED

        val success = SupabaseClient(context).sendStatus(
            deviceId = deviceId,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            networkType = networkType,
            connectivity = if (isConnected) "CONNECTED" else "DISCONNECTED",
            kioskLocked = kioskLocked
        )

        if (success) Result.success() else Result.retry()
    }
}
