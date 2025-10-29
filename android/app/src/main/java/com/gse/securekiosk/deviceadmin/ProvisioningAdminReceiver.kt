package com.gse.securekiosk.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gse.securekiosk.util.DeviceConfig

class ProvisioningAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Provisioning admin enabled")

        intent.extras?.let { extras ->
            extras.getString("supabase_service_key")?.let {
                DeviceConfig.saveSupabaseApiKey(context, it)
            }
            extras.getString("certificate_pin")?.let {
                DeviceConfig.saveCertificatePin(context, it)
            }
        }
    }

    companion object {
        private const val TAG = "ProvisioningReceiver"
    }
}
