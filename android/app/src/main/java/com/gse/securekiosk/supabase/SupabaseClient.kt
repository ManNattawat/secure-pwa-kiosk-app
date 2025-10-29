package com.gse.securekiosk.supabase

import android.content.Context
import android.util.Log
import com.gse.securekiosk.BuildConfig
import com.gse.securekiosk.util.DeviceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.IOException
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SupabaseClient(private val context: Context) {

    private val client: OkHttpClient by lazy { buildClient() }

    suspend fun sendLocation(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double,
        bearing: Double,
        speed: Double,
        recordedAt: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = """
            {
              "payload": {
                "device_id": "$deviceId",
                "latitude": $latitude,
                "longitude": $longitude,
                "accuracy": $accuracy,
                "bearing": $bearing,
                "speed": $speed,
                "recorded_at": "${java.time.Instant.ofEpochMilli(recordedAt)}"
              }
            }
        """.trimIndent()

        post(DeviceConfig.getLocationEndpoint(context), payload)
    }

    suspend fun sendStatus(
        deviceId: String,
        batteryPercent: Int,
        isCharging: Boolean,
        networkType: String,
        connectivity: String,
        kioskLocked: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = """
            {
              "payload": {
                "device_id": "$deviceId",
                "battery_percent": $batteryPercent,
                "is_charging": $isCharging,
                "network_type": "$networkType",
                "connectivity": "$connectivity",
                "kiosk_locked": $kioskLocked
              }
            }
        """.trimIndent()

        post(DeviceConfig.getStatusEndpoint(context), payload)
    }

    private fun post(endpoint: String, jsonBody: String): Boolean {
        val apiKey = DeviceConfig.getSupabaseApiKey(context)
        if (apiKey.isNullOrBlank()) {
            Log.e(TAG, "Supabase API key is missing")
            return false
        }

        val baseUrl = DeviceConfig.getSupabaseUrl(context)
        val url = baseUrl.trimEnd('/') + endpoint

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Supabase error: ${response.code} ${response.message}")
                }
                response.isSuccessful
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Network error", ex)
            false
        }
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(30))
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(30))

        val pins = DeviceConfig.getCertificatePins(context)
        if (pins.isNotEmpty()) {
            val host = getHost()
            val pinnerBuilder = okhttp3.CertificatePinner.Builder()
            pins.forEach { pin ->
                pinnerBuilder.add(host, pin)
            }
            builder.certificatePinner(pinnerBuilder.build())
        }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }

        return builder.build()
    }

    private fun getHost(): String {
        val uri = android.net.Uri.parse(DeviceConfig.getSupabaseUrl(context))
        return uri.host ?: ""
    }

    companion object {
        private const val TAG = "SupabaseClient"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
