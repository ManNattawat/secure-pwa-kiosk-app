package com.gse.securekiosk.remote

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.gse.securekiosk.deviceadmin.DeviceOwnerReceiver

/**
 * Remote Control & Monitoring Manager (ข้อที่ 9)
 * จัดการควบคุมแท็บจากระยะไกล เช่น ลบข้อมูล, ล็อกเครื่อง, รีสตาร์ท
 */
object RemoteControlManager {
    private const val TAG = "RemoteControlManager"

    /**
     * ตรวจสอบว่าแอพเป็น Device Owner หรือไม่
     */
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * ตรวจสอบว่าแอพเป็น Device Admin หรือไม่
     */
    fun isDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    /**
     * ล็อกเครื่องทันที (Lock Device)
     * ใช้เมื่อ: แท็บถูกขโมย หรือต้องการล็อกจากระยะไกล
     */
    fun lockDevice(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
            
            if (!dpm.isAdminActive(adminComponent)) {
                Log.e(TAG, "Device admin not active")
                return false
            }
            
            dpm.lockNow()
            Log.i(TAG, "Device locked successfully")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to lock device: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device: ${e.message}", e)
            false
        }
    }

    /**
     * ลบข้อมูลทั้งหมด (Wipe Device / Factory Reset)
     * ใช้เมื่อ: แท็บถูกขโมย และต้องการลบข้อมูลทั้งหมดเพื่อป้องกันข้อมูลรั่วไหล
     * ⚠️ คำเตือน: การลบข้อมูลนี้ไม่สามารถยกเลิกได้!
     */
    fun wipeDevice(context: Context, flags: Int = 0): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
            
            if (!isDeviceOwner(context)) {
                Log.e(TAG, "Device owner required for wipe operation")
                return false
            }
            
            dpm.wipeData(flags)
            Log.i(TAG, "Device wipe initiated successfully")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to wipe device: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping device: ${e.message}", e)
            false
        }
    }

    /**
     * รีสตาร์ทเครื่อง (Reboot Device)
     * ใช้เมื่อ: ต้องการรีสตาร์ทเครื่องจากระยะไกล
     * ⚠️ ต้องการ Android N (API 24) หรือสูงกว่า และต้องเป็น Device Owner
     */
    fun rebootDevice(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.e(TAG, "Reboot requires Android N (API 24) or higher")
                return false
            }
            
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
            
            if (!isDeviceOwner(context)) {
                Log.e(TAG, "Device owner required for reboot operation")
                return false
            }
            
            dpm.reboot(adminComponent)
            Log.i(TAG, "Device reboot initiated successfully")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to reboot device: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error rebooting device: ${e.message}", e)
            false
        }
    }

    /**
     * ตั้งค่ารหัสผ่านใหม่
     */
    fun resetPassword(context: Context, password: String): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
            
            if (!isDeviceOwner(context)) {
                Log.e(TAG, "Device owner required for password reset")
                return false
            }
            
            dpm.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)
            Log.i(TAG, "Password reset successfully")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to reset password: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting password: ${e.message}", e)
            false
        }
    }

    /**
     * ตั้งค่าจำนวนครั้งที่รหัสผ่านผิดสูงสุดก่อนลบข้อมูล
     */
    fun setMaximumFailedPasswordsForWipe(context: Context, maxAttempts: Int): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
            
            if (!isDeviceOwner(context)) {
                Log.e(TAG, "Device owner required for setting password policy")
                return false
            }
            
            dpm.setMaximumFailedPasswordsForWipe(adminComponent, maxAttempts)
            Log.i(TAG, "Maximum failed passwords set to $maxAttempts")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set password policy: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting password policy: ${e.message}", e)
            false
        }
    }

    /**
     * ส่งข้อมูลสถานะเครื่องกลับไปยัง server
     * ใช้สำหรับ monitoring
     */
    fun getDeviceStatus(context: Context): Map<String, Any> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)
        
        return mapOf(
            "isDeviceOwner" to isDeviceOwner(context),
            "isDeviceAdmin" to isDeviceAdmin(context),
            "androidVersion" to Build.VERSION.SDK_INT,
            "deviceModel" to Build.MODEL,
            "deviceManufacturer" to Build.MANUFACTURER,
            "packageName" to context.packageName
        )
    }
}

