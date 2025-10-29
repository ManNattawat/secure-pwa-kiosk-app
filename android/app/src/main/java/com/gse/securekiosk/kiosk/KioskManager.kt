package com.gse.securekiosk.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.gse.securekiosk.deviceadmin.DeviceOwnerReceiver

class KioskManager(private val activity: Activity) {

    private val dpm: DevicePolicyManager by lazy {
        activity.getSystemService(DevicePolicyManager::class.java)
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(activity, DeviceOwnerReceiver::class.java)
    }

    fun ensureLockTaskMode() {
        if (!dpm.isDeviceOwnerApp(activity.packageName)) {
            Log.w(TAG, "App is not device owner; lock task mode not enforced")
            return
        }

        val packages = arrayOf(activity.packageName)
        dpm.setLockTaskPackages(adminComponent, packages)

        if (!activity.isInLockTaskMode) {
            try {
                activity.startLockTask()
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start lock task", t)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setGlobalSetting(adminComponent, DevicePolicyManager.GLOBAL_SETTING_STAY_ON_WHILE_PLUGGED_IN, "1")
        }
    }

    private val Activity.isInLockTaskMode: Boolean
        get() {
            return try {
                val lockTaskMode = dpm.lockTaskModeState
                lockTaskMode == DevicePolicyManager.LOCK_TASK_MODE_LOCKED ||
                    lockTaskMode == DevicePolicyManager.LOCK_TASK_MODE_PINNED
            } catch (t: Throwable) {
                false
            }
        }

    companion object {
        private const val TAG = "KioskManager"

        fun unlock(context: Context) {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            if (dpm.lockTaskModeState != DevicePolicyManager.LOCK_TASK_MODE_NONE) {
                try {
                    (context as? Activity)?.stopLockTask()
                } catch (_: Throwable) {
                }
            }
        }
    }
}
