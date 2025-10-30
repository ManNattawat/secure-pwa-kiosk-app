package com.gse.securekiosk.kiosk

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.gse.securekiosk.deviceadmin.DeviceOwnerReceiver
import com.gse.securekiosk.util.DevicePolicyUtils

class KioskManager(private val activity: Activity) {

    private val dpm by lazy {
        activity.getSystemService(android.app.admin.DevicePolicyManager::class.java)
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(activity, DeviceOwnerReceiver::class.java)
    }

    fun ensureLockTaskMode() {
        val manager = dpm ?: run {
            Log.w(TAG, "DevicePolicyManager unavailable")
            return
        }

        if (!manager.isDeviceOwnerApp(activity.packageName)) {
            Log.w(TAG, "App is not device owner; lock task mode not enforced")
            return
        }

        val packages = arrayOf(activity.packageName)
        manager.setLockTaskPackages(adminComponent, packages)

        if (!activity.isInLockTaskMode) {
            try {
                activity.startLockTask()
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start lock task", t)
            }
        }

        val stayAwakeKey = DevicePolicyUtils.stayAwakeSettingKey()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && stayAwakeKey != null) {
            manager.setGlobalSetting(adminComponent, stayAwakeKey, "1")
        }
    }

    private val Activity.isInLockTaskMode: Boolean
        get() {
            return try {
                DevicePolicyUtils.isLockTaskActive(DevicePolicyUtils.lockTaskModeState(dpm))
            } catch (t: Throwable) {
                false
            }
        }

    companion object {
        private const val TAG = "KioskManager"

        fun unlock(context: Context) {
            val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
            if (DevicePolicyUtils.isLockTaskActive(dpm)) {
                try {
                    (context as? Activity)?.stopLockTask()
                } catch (_: Throwable) {
                }
            }
        }
    }
}
