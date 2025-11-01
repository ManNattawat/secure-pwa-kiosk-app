package com.gse.securekiosk

import android.app.Activity
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gse.securekiosk.bridge.AndroidBridge
import com.gse.securekiosk.kiosk.KioskManager
import com.gse.securekiosk.location.LocationSyncService
import com.gse.securekiosk.scanner.CameraScannerActivity
import com.gse.securekiosk.util.DeviceConfig

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var kioskManager: KioskManager
    private lateinit var androidBridge: AndroidBridge

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1002
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION
        )
        private val CAMERA_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        kioskManager = KioskManager(this)

        webView = findViewById(R.id.kioskWebView)
        configureWebView(webView)
        
        // Add JavaScript Interface Bridge
        androidBridge = AndroidBridge(this, webView, this)
        webView.addJavascriptInterface(androidBridge, "AndroidBridge")
        
        webView.loadUrl(DeviceConfig.getPwaUrl(this))

        // Delay permission request and service start to avoid Android 15 restrictions
        // Android 15 doesn't allow starting foreground service immediately on app launch
        webView.postDelayed({
            requestLocationPermissions()
        }, 1000) // Delay 1 second to allow activity to be fully visible
    }

    override fun onResume() {
        super.onResume()
        kioskManager.ensureLockTaskMode()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Activity won't be destroyed/recreated, just handle orientation change
        // WebView will maintain its state and session
        enterImmersiveMode()
    }

    private fun requestLocationPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // All permissions granted, start service
            startLocationService()
        } else {
            // Request missing permissions
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    startLocationService()
                }
                // If permissions denied, service won't start (handled by service itself)
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    // Camera permission granted - notify AndroidBridge
                    androidBridge.onCameraPermissionGranted()
                    // Also notify PWA if needed
                    webView.evaluateJavascript("if(window.onCameraPermissionGranted) window.onCameraPermissionGranted();", null)
                } else {
                    // Camera permission denied - notify AndroidBridge
                    androidBridge.onCameraPermissionDenied()
                    // Also notify PWA if needed
                    webView.evaluateJavascript("if(window.onCameraPermissionDenied) window.onCameraPermissionDenied();", null)
                }
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle camera scanner result
        if (requestCode == CameraScannerActivity.REQUEST_CODE) {
            androidBridge.handleCameraScanResult(resultCode, data)
        }
        
        // Handle document scanner result
        if (requestCode == com.gse.securekiosk.ocr.DocumentScannerActivity.REQUEST_CODE) {
            androidBridge.handleDocumentScanResult(resultCode, data)
        }
    }

    private fun startLocationService() {
        try {
            val intent = Intent(this, LocationSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                startService(intent)
            }
        } catch (e: Exception) {
            // Ignore if service can't start (Android 15 restrictions)
            // Service will be started later when conditions are met
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Allow JavaScript to access AndroidBridge
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
        }

        webView.webViewClient = SecureWebViewClient(this)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                // Handle camera permission request from PWA
                if (request?.resources?.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE) == true) {
                    requestCameraPermission()
                    request.deny()
                } else {
                    request?.grant(request.resources)
                }
            }
        }

        WebView.setWebContentsDebuggingEnabled(false)
    }
    
    /**
     * Request camera permission
     */
    fun requestCameraPermission() {
        val missingPermissions = CAMERA_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) - Use WindowInsetsController
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 and below - Use deprecated systemUiVisibility
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    private class SecureWebViewClient(context: android.content.Context) : WebViewClient() {
        private val allowedOrigin = Uri.parse(DeviceConfig.getAllowedOrigin(context))

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            if (request == null) return false
            val url = request.url ?: return false

            return if (isSameOrigin(url, allowedOrigin)) {
                false
            } else {
                view?.context?.startActivity(Intent(Intent.ACTION_VIEW, url))
                true
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: android.webkit.WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            
            // Handle network errors
            if (error != null && request != null) {
                val errorCode = error.errorCode
                val errorDescription = error.description?.toString() ?: ""
                
                // Generate HTML error page
                val htmlError = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>ข้อผิดพลาด</title>
                        <style>
                            body {
                                font-family: 'Segoe UI', Arial, sans-serif;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                min-height: 100vh;
                                margin: 0;
                                background: #f5f5f5;
                                color: #333;
                            }
                            .error-container {
                                text-align: center;
                                padding: 20px;
                                max-width: 500px;
                            }
                            .error-icon {
                                font-size: 64px;
                                margin-bottom: 20px;
                            }
                            .error-title {
                                font-size: 24px;
                                font-weight: bold;
                                margin-bottom: 10px;
                                color: #d32f2f;
                            }
                            .error-message {
                                font-size: 16px;
                                margin-bottom: 20px;
                                color: #666;
                            }
                            .error-details {
                                font-size: 14px;
                                color: #999;
                                margin-bottom: 20px;
                            }
                            .retry-button {
                                background: #1976d2;
                                color: white;
                                border: none;
                                padding: 12px 24px;
                                font-size: 16px;
                                border-radius: 4px;
                                cursor: pointer;
                                margin-top: 10px;
                            }
                            .retry-button:hover {
                                background: #1565c0;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="error-container">
                            <div class="error-icon">⚠️</div>
                            <div class="error-title">ไม่สามารถโหลดหน้าเว็บ</div>
                            <div class="error-message">กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต</div>
                            <div class="error-details">Error: ${errorCode} - ${errorDescription}</div>
                            <button class="retry-button" onclick="window.location.reload()">ลองใหม่อีกครั้ง</button>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                
                // Load error page only for main frame
                if (request.isForMainFrame) {
                    view?.loadDataWithBaseURL(null, htmlError, "text/html", "UTF-8", null)
                }
            }
        }

        private fun isSameOrigin(target: Uri, allowed: Uri): Boolean {
            return target.scheme == allowed.scheme &&
                target.host == allowed.host &&
                target.port == allowed.port
        }
    }
}
