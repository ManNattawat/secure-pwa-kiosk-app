package com.gse.securekiosk.util

import android.app.admin.DevicePolicyManager
import android.os.Build

object DevicePolicyUtils {
    private val lockTaskModeNone: Int = getConstant("LOCK_TASK_MODE_NONE", 0)
    private val lockTaskModePinned: Int = getConstant("LOCK_TASK_MODE_PINNED", 1)
    private val lockTaskModeLocked: Int = getConstant("LOCK_TASK_MODE_LOCKED", 2)
    private val stayAwakeSetting: String? = getStringConstant("GLOBAL_SETTING_STAY_ON_WHILE_PLUGGED_IN")

    fun lockTaskModeState(dpm: DevicePolicyManager?): Int {
        if (dpm == null) return lockTaskModeNone
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val method = DevicePolicyManager::class.java.getMethod("getLockTaskModeState")
                val result = method.invoke(dpm)
                if (result is Int) {
                    return result
                }
            } catch (_: Throwable) {
                // fall through to default
            }
        }
        return lockTaskModeNone
    }

    fun isLockTaskActive(dpm: DevicePolicyManager?): Boolean {
        val state = lockTaskModeState(dpm)
        return state == lockTaskModeLocked || state == lockTaskModePinned
    }

    fun isLockTaskActive(state: Int): Boolean {
        return state == lockTaskModeLocked || state == lockTaskModePinned
    }

    fun stayAwakeSettingKey(): String? = stayAwakeSetting

    private fun getConstant(name: String, fallback: Int): Int {
        return try {
            DevicePolicyManager::class.java.getField(name).getInt(null)
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun getStringConstant(name: String): String? {
        return try {
            DevicePolicyManager::class.java.getField(name).get(null) as? String
        } catch (_: Throwable) {
            null
        }
    }
}
