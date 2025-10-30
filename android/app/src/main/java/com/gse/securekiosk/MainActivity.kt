package com.gse.securekiosk

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gse.securekiosk.kiosk.KioskManager
import com.gse.securekiosk.location.LocationSyncService
import com.gse.securekiosk.util.DeviceConfig

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var kioskManager: KioskManager

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
        webView.loadUrl(DeviceConfig.getPwaUrl(this))

        startLocationService()
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

    private fun startLocationService() {
        val intent = Intent(this, LocationSyncService::class.java)
        startForegroundService(intent)
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
        }

        webView.webViewClient = SecureWebViewClient(this)
        webView.webChromeClient = WebChromeClient()

        WebView.setWebContentsDebuggingEnabled(false)
    }

    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
            )
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

        private fun isSameOrigin(target: Uri, allowed: Uri): Boolean {
            return target.scheme == allowed.scheme &&
                target.host == allowed.host &&
                target.port == allowed.port
        }
    }
}
