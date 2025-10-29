package com.gse.securekiosk.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gse.securekiosk.R

object DeviceConfig {

    private const val PREF_FILE = "secure_config"
    private const val KEY_SUPABASE_API = "supabase_api_key"
    private const val KEY_SUPABASE_PIN = "supabase_cert_pin"
    private const val KEY_SUPABASE_URL = "supabase_url"
    private const val KEY_PWA_URL = "pwa_url"
    private const val KEY_SUPABASE_PINS = "supabase_cert_pins"

    fun getPwaUrl(context: Context): String =
        encryptedPrefs(context).getString(KEY_PWA_URL, null)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.config_pwa_url)

    fun savePwaUrl(context: Context, url: String) {
        encryptedPrefs(context).edit().putString(KEY_PWA_URL, url.trim()).apply()
    }

    fun getAllowedOrigin(context: Context): String {
        val pwaUrl = getPwaUrl(context)
        val uri = Uri.parse(pwaUrl)
        val portPart = if (uri.port != -1 && uri.port != 443 && uri.port != 80) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host ?: ""}$portPart"
    }

    fun getSupabaseUrl(context: Context): String =
        encryptedPrefs(context).getString(KEY_SUPABASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.config_supabase_url)

    fun saveSupabaseUrl(context: Context, url: String) {
        encryptedPrefs(context).edit().putString(KEY_SUPABASE_URL, url.trim()).apply()
    }

    fun getLocationEndpoint(context: Context): String =
        context.getString(R.string.config_supabase_location_endpoint)

    fun getStatusEndpoint(context: Context): String =
        context.getString(R.string.config_supabase_status_endpoint)

    fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    fun saveSupabaseApiKey(context: Context, apiKey: String) {
        encryptedPrefs(context).edit().putString(KEY_SUPABASE_API, apiKey).apply()
    }

    fun getSupabaseApiKey(context: Context): String? =
        encryptedPrefs(context).getString(KEY_SUPABASE_API, null)

    fun saveCertificatePin(context: Context, pin: String) {
        saveCertificatePins(context, listOf(pin))
    }

    fun saveCertificatePins(context: Context, pins: List<String>) {
        encryptedPrefs(context).edit()
            .putString(KEY_SUPABASE_PINS, pins.joinToString(separator = "\n") { it.trim() })
            .apply()
        pins.firstOrNull()?.let { primary ->
            encryptedPrefs(context).edit().putString(KEY_SUPABASE_PIN, primary.trim()).apply()
        }
    }

    fun getCertificatePins(context: Context): List<String> =
        encryptedPrefs(context).getString(KEY_SUPABASE_PINS, null)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toList()
            ?: emptyList()

    fun getCertificatePin(context: Context): String? =
        encryptedPrefs(context).getString(KEY_SUPABASE_PIN, null)

    fun applyRuntimeSecrets(
        context: Context,
        supabaseUrl: String?,
        supabaseKey: String?,
        pwaUrl: String?,
        certPins: List<String>
    ) {
        val editor = encryptedPrefs(context).edit()
        supabaseUrl?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_SUPABASE_URL, it.trim()) }
        supabaseKey?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_SUPABASE_API, it.trim()) }
        pwaUrl?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_PWA_URL, it.trim()) }
        if (certPins.isNotEmpty()) {
            editor.putString(KEY_SUPABASE_PINS, certPins.joinToString("\n") { it.trim() })
            editor.putString(KEY_SUPABASE_PIN, certPins.first().trim())
        }
        editor.apply()
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
