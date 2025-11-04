package com.gse.securekiosk.ocr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gse.securekiosk.R
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Document Scanner Activity
 * เปิดกล้องและสแกนเอกสารด้วย OCR (อ่านข้อความจากบัตรประชาชน, เอกสาร)
 */
class DocumentScannerActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var instructionText: TextView
    private lateinit var resultText: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var flashButton: ImageButton
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var flashEnabled = false
    private var isProcessing = false
    
    companion object {
        private const val TAG = "DocumentScanner"
        const val REQUEST_CODE = 2002
        const val EXTRA_OCR_RESULT = "ocr_result"
        const val EXTRA_IMAGE_BASE64 = "image_base64"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1004
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupFullscreen()
        
        // Handle back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelScan()
            }
        })
        
        try {
            setContentView(R.layout.activity_document_scanner)
            
            previewView = findViewById(R.id.cameraPreview)
            instructionText = findViewById(R.id.instructionText)
            resultText = findViewById(R.id.resultText)
            closeButton = findViewById(R.id.closeButton)
            captureButton = findViewById(R.id.captureButton)
            flashButton = findViewById(R.id.flashButton)
            
            setupButtons()
            
            if (checkCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error starting document scanner: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController?.apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun setupButtons() {
        closeButton.setOnClickListener {
            cancelScan()
        }
        
        captureButton.setOnClickListener {
            captureAndProcessImage()
        }
        
        flashButton.setOnClickListener {
            toggleFlash()
        }
    }
    
    private fun cancelScan() {
        Log.d(TAG, "Cancelling scan")
        stopCamera()
        setResult(RESULT_CANCELED)
        if (!isFinishing) {
            finish()
        }
    }
    
    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageCapture = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
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
        val cameraProvider = this.cameraProvider ?: return
        
        // Preview
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image Capture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        
        // Select camera (back camera preferred)
        val cameraSelector = try {
            CameraSelector.DEFAULT_BACK_CAMERA
        } catch (e: Exception) {
            Log.w(TAG, "Back camera unavailable, using front camera", e)
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        
        try {
            cameraProvider.unbindAll()
            
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
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
        
        flashButton.setImageResource(
            if (flashEnabled) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_menu_camera
        )
    }
    
    private fun captureAndProcessImage() {
        if (isProcessing) return
        
        val imageCapture = this.imageCapture ?: return
        
        isProcessing = true
        captureButton.isEnabled = false
        
        runOnUiThread {
            resultText.text = "กำลังประมวลผล..."
            resultText.visibility = View.VISIBLE
            resultText.setTextColor(android.graphics.Color.parseColor("#FFA500"))
        }
        
        // Capture image
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(image)
                        if (bitmap != null) {
                            processImageWithOCR(bitmap)
                        } else {
                            showError("ไม่สามารถแปลงรูปภาพได้")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image", e)
                        showError("เกิดข้อผิดพลาด: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturing image", exception)
                    showError("เกิดข้อผิดพลาด: ${exception.message}")
                }
            }
        )
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            null
        }
    }
    
    private fun processImageWithOCR(bitmap: Bitmap) {
        runOnUiThread {
            resultText.text = "กำลังอ่านข้อความ..."
        }
        
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val enhancedParsing = intent.getBooleanExtra("enhanced_parsing", false)
        val documentType = intent.getStringExtra("document_type") ?: "generic"
        
        OcrService.extractText(
            image = inputImage,
            onSuccess = { ocrResult ->
                Log.d(TAG, "OCR Success: ${ocrResult.fullText}")
                
                // Convert bitmap to base64
                val base64Image = bitmapToBase64(bitmap)
                
                // Enhanced parsing for Thai ID Card
                val finalResult = if (enhancedParsing && documentType == "thai_id_card") {
                    val parsedData = ThaiIDCardParser.parse(ocrResult.fullText)
                    Log.d(TAG, "Parsed ID Card: ${parsedData.toJson()}")
                    
                    // Combine OCR result with parsed data
                    org.json.JSONObject(ocrResult.toJson()).apply {
                        put("parsedData", org.json.JSONObject(parsedData.toJson()))
                        put("documentType", "thai_id_card")
                    }.toString()
                } else {
                    ocrResult.toJson()
                }
                
                // Return result
                val result = Intent().apply {
                    putExtra(EXTRA_OCR_RESULT, finalResult)
                    putExtra(EXTRA_IMAGE_BASE64, base64Image)
                }
                setResult(RESULT_OK, result)
                
                runOnUiThread {
                    resultText.text = "✓ อ่านข้อความสำเร็จ\n${ocrResult.fullText.take(50)}..."
                    resultText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    
                    // Auto close after delay
                    previewView.postDelayed({
                        finish()
                    }, 2000)
                }
            },
            onError = { e ->
                Log.e(TAG, "OCR Error", e)
                showError("ไม่สามารถอ่านข้อความได้: ${e.message}")
            }
        )
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            resultText.text = "✗ $message"
            resultText.setTextColor(android.graphics.Color.parseColor("#F44336"))
            isProcessing = false
            captureButton.isEnabled = true
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        cancelScan()
    }
    
    override fun onPause() {
        super.onPause()
        stopCamera()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }
}

