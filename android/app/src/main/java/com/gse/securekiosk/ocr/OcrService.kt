package com.gse.securekiosk.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.thai.TextRecognitionThai
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * OCR Service
 * ใช้ ML Kit Text Recognition สำหรับอ่านข้อความจากรูปภาพ
 */
object OcrService {
    private const val TAG = "OcrService"
    
    // Thai + Latin text recognizer (รองรับทั้งภาษาไทยและอังกฤษ)
    private val thaiTextRecognizer = TextRecognitionThai.getClient()
    
    // Latin-only recognizer (fallback)
    private val latinTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Extract text from image
     * @param image InputImage from ML Kit
     * @param callback Result callback
     */
    fun extractText(
        image: InputImage,
        onSuccess: (OcrResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Try Thai + Latin first (รองรับภาษาไทย)
        thaiTextRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val result = processTextResult(visionText)
                onSuccess(result)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Thai recognizer failed, trying Latin", e)
                // Fallback to Latin-only
                latinTextRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val result = processTextResult(visionText)
                        onSuccess(result)
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Both recognizers failed", e2)
                        onError(e2)
                    }
            }
    }
    
    /**
     * Extract text from bitmap
     */
    fun extractTextFromBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        onSuccess: (OcrResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        extractText(image, onSuccess, onError)
    }
    
    /**
     * Process ML Kit VisionText to OcrResult
     */
    private fun processTextResult(visionText: com.google.mlkit.vision.text.Text): OcrResult {
        val fullText = visionText.text
        val blocks = mutableListOf<TextBlock>()
        
        for (block in visionText.textBlocks) {
            val blockText = block.text
            val lines = mutableListOf<String>()
            
            for (line in block.lines) {
                lines.add(line.text)
            }
            
            blocks.add(TextBlock(
                text = blockText,
                lines = lines,
                boundingBox = block.boundingBox?.let {
                    BoundingBox(
                        left = it.left,
                        top = it.top,
                        right = it.right,
                        bottom = it.bottom
                    )
                }
            ))
        }
        
        return OcrResult(
            fullText = fullText,
            blocks = blocks,
            blockCount = blocks.size
        )
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            thaiTextRecognizer.close()
            latinTextRecognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing recognizers", e)
        }
    }
    
    /**
     * OCR Result
     */
    data class OcrResult(
        val fullText: String,
        val blocks: List<TextBlock>,
        val blockCount: Int
    ) {
        fun toJson(): String {
            val json = JSONObject()
            json.put("fullText", fullText)
            json.put("blockCount", blockCount)
            
            val blocksArray = org.json.JSONArray()
            blocks.forEach { block ->
                val blockJson = JSONObject()
                blockJson.put("text", block.text)
                blockJson.put("lines", org.json.JSONArray(block.lines))
                block.boundingBox?.let {
                    val boxJson = JSONObject()
                    boxJson.put("left", it.left)
                    boxJson.put("top", it.top)
                    boxJson.put("right", it.right)
                    boxJson.put("bottom", it.bottom)
                    blockJson.put("boundingBox", boxJson)
                }
                blocksArray.put(blockJson)
            }
            json.put("blocks", blocksArray)
            
            return json.toString()
        }
    }
    
    /**
     * Text Block
     */
    data class TextBlock(
        val text: String,
        val lines: List<String>,
        val boundingBox: BoundingBox?
    )
    
    /**
     * Bounding Box
     */
    data class BoundingBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}

