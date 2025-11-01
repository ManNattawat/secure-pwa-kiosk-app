package com.gse.securekiosk.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * OCR Service
 * ใช้ ML Kit Text Recognition สำหรับอ่านข้อความจากรูปภาพ
 * Unified model รองรับหลายภาษารวมถึงภาษาไทยและอังกฤษ
 */
object OcrService {
    private const val TAG = "OcrService"
    
    // Unified text recognizer (รองรับทั้งภาษาไทยและอังกฤษ)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
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
        // Unified model supports Thai and Latin text
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val result = processTextResult(visionText)
                onSuccess(result)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
                onError(e)
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
            textRecognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing recognizer", e)
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
            
            val blocksArray = JSONArray()
            blocks.forEach { block ->
                val blockJson = JSONObject()
                blockJson.put("text", block.text)
                blockJson.put("lines", JSONArray(block.lines))
                block.boundingBox?.let {
                    val boxJson = JSONObject()
                    boxJson.put("left", it.left)
                    boxJson.put("top", it.top)
                    boxJson.put("right", it.right)
                    boxJson.put("bottom", it.bottom)
                    blockJson.put("boundingBox", boxJson)
                }
                // Fix ambiguity by explicitly casting to Any
                blocksArray.put(blockJson as Any)
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

