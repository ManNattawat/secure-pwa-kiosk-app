package com.gse.securekiosk.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gse.securekiosk.R
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Camera Scanner Activity
 * เปิดกล้องและสแกนบาร์โค้ดแบบ real-time
 */
class CameraScannerActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var instructionText: TextView
    private lateinit var resultText: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var flashButton: ImageButton
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var flashEnabled = false
    
    private var lastScanTime = 0L
    private val scanThrottleMs = 1000L // ป้องกันสแกนซ้ำภายใน 1 วินาที
    
    companion object {
        private const val TAG = "CameraScanner"
        const val REQUEST_CODE = 2001
        const val EXTRA_RESULT = "scan_result"
        const val EXTRA_BARCODE_VALUE = "barcode_value"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1003
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup fullscreen (supplement theme settings for Android 11+)
        setupFullscreen()
        
        // Handle back button (Android 13+)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "OnBackPressedDispatcher: Back pressed")
                cancelScan()
            }
        })
        
        try {
            setContentView(R.layout.activity_camera_scanner)
            
            previewView = findViewById(R.id.cameraPreview)
            instructionText = findViewById(R.id.instructionText)
            resultText = findViewById(R.id.resultText)
            closeButton = findViewById(R.id.closeButton)
            flashButton = findViewById(R.id.flashButton)
            
            setupButtons()
            
            if (checkCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error starting camera scanner: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupFullscreen() {
        // Hide system bars for fullscreen experience
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController?.apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
        
        // Keep screen on while scanning
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun setupButtons() {
        // Close button - ปุ่มปิดกล้อง
        closeButton.setOnClickListener {
            Log.d(TAG, "Close button clicked")
            cancelScan()
        }
        
        // Make close button more visible and clickable
        closeButton.isClickable = true
        closeButton.isFocusable = true
        closeButton.elevation = 8f
        
        flashButton.setOnClickListener {
            toggleFlash()
        }
    }
    
    /**
     * Cancel scan and return to previous activity
     */
    private fun cancelScan() {
        Log.d(TAG, "Cancelling scan")
        
        // Stop camera first to release resources immediately
        stopCamera()
        
        // Set result
        setResult(RESULT_CANCELED)
        
        // Force finish - ensure activity closes
        if (!isFinishing) {
            finish()
            // Force close if still not finishing
            if (!isFinishing) {
                Log.w(TAG, "Activity not finishing, forcing finish")
                finishAffinity()
            }
        }
    }
    
    /**
     * Stop camera and release resources
     */
    private fun stopCamera() {
        try {
            // Clear analyzer first
            imageAnalyzer?.let {
                try {
                    it.clearAnalyzer()
                    Log.d(TAG, "Image analyzer cleared")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing analyzer", e)
                }
            }
            
            // Unbind all camera use cases
            cameraProvider?.let {
                try {
                    it.unbindAll()
                    Log.d(TAG, "Camera provider unbound")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unbinding camera provider", e)
                }
            }
            
            // Clear camera reference
            camera = null
            imageAnalyzer = null
            
            Log.d(TAG, "Camera stopped and resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
    
    /**
     * Handle back button press
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Log.d(TAG, "Back button pressed")
        cancelScan()
    }
    
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera", e)
                Toast.makeText(this, "Error starting camera: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: run {
            Log.e(TAG, "Camera provider is null")
            Toast.makeText(this, "Camera provider unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // Image Analysis for barcode scanning
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor(), BarcodeAnalyzer { barcodes ->
                        handleBarcodeResult(barcodes)
                    })
                }
            
            // Select camera (back camera preferred, fallback to front if unavailable)
            val cameraSelector = try {
                CameraSelector.DEFAULT_BACK_CAMERA
            } catch (e: Exception) {
                Log.w(TAG, "Back camera unavailable, using front camera", e)
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            // Setup flash toggle
            flashButton.visibility = if (camera?.cameraInfo?.hasFlashUnit() == true) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error binding camera use cases", e)
            Toast.makeText(this, "Error starting camera: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun toggleFlash() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        
        flashEnabled = !flashEnabled
        cam.cameraControl.enableTorch(flashEnabled)
        
        // Update button icon
        flashButton.setImageResource(
            if (flashEnabled) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_menu_camera
        )
    }
    
    private fun handleBarcodeResult(barcodes: List<Barcode>) {
        val currentTime = System.currentTimeMillis()
        
        // Throttle scanning to prevent duplicate scans
        if (currentTime - lastScanTime < scanThrottleMs) {
            return
        }
        
        if (barcodes.isNotEmpty()) {
            val barcode = barcodes[0] // Take the first barcode
            lastScanTime = currentTime
            
            // Show result on screen
            runOnUiThread {
                resultText.text = "✓ สแกนพบ: ${barcode.rawValue}"
                resultText.visibility = View.VISIBLE
                resultText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            }
            
            // Return result to caller
            val result = Intent().apply {
                putExtra(EXTRA_BARCODE_VALUE, barcode.rawValue)
                putExtra(EXTRA_RESULT, BarcodeScannerService.barcodeToJson(barcode))
            }
            setResult(RESULT_OK, result)
            
            // Auto close after short delay (ให้ผู้ใช้เห็นผลลัพธ์ก่อน)
            previewView.postDelayed({
                setResult(RESULT_OK, result)
                finish()
            }, 800)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop camera when activity is paused
        stopCamera()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure cleanup
        stopCamera()
    }
    
    /**
     * Image Analyzer for barcode scanning
     */
    private class BarcodeAnalyzer(
        private val onBarcodeDetected: (List<Barcode>) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        override fun analyze(imageProxy: ImageProxy) {
            try {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    
                    BarcodeScannerService.scanBarcode(
                        image = inputImage,
                        onSuccess = { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                onBarcodeDetected(barcodes)
                            }
                        },
                        onError = { e ->
                            Log.e(TAG, "Barcode scanning error", e)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing image", e)
            } finally {
                imageProxy.close()
            }
        }
    }
}

