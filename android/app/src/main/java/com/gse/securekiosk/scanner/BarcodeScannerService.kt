package com.gse.securekiosk.scanner

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Barcode Scanner Service
 * ใช้ Google ML Kit สำหรับสแกนบาร์โค้ด/QR Code แบบเร็วและแม่นยำ
 */
object BarcodeScannerService {
    private const val TAG = "BarcodeScanner"
    private val isScanning = AtomicBoolean(false)
    
    private val scanner = BarcodeScanning.getClient()

    /**
     * สแกนบาร์โค้ดจาก InputImage
     * @param image InputImage จาก Camera
     * @param onSuccess callback เมื่อสแกนสำเร็จ
     * @param onError callback เมื่อเกิด error
     */
    fun scanBarcode(
        image: InputImage,
        onSuccess: (List<Barcode>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isScanning.get()) {
            Log.d(TAG, "Scan already in progress, skipping...")
            return
        }

        isScanning.set(true)
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                isScanning.set(false)
                if (barcodes.isNotEmpty()) {
                    Log.d(TAG, "Found ${barcodes.size} barcode(s)")
                    onSuccess(barcodes)
                } else {
                    // No barcode found, but this is not an error
                    Log.d(TAG, "No barcode found")
                }
            }
            .addOnFailureListener { e ->
                isScanning.set(false)
                Log.e(TAG, "Barcode scanning failed", e)
                onError(e)
            }
    }

    /**
     * แปลง Barcode เป็น JSON string สำหรับส่งไปยัง PWA
     */
    fun barcodeToJson(barcode: Barcode): String {
        return """
        {
            "rawValue": "${barcode.rawValue ?: ""}",
            "format": ${barcode.format},
            "formatName": "${getFormatName(barcode.format)}",
            "type": ${barcode.valueType},
            "typeName": "${getTypeName(barcode.valueType)}",
            "boundingBox": {
                "left": ${barcode.boundingBox?.left ?: 0},
                "top": ${barcode.boundingBox?.top ?: 0},
                "right": ${barcode.boundingBox?.right ?: 0},
                "bottom": ${barcode.boundingBox?.bottom ?: 0},
                "width": ${(barcode.boundingBox?.right ?: 0) - (barcode.boundingBox?.left ?: 0)},
                "height": ${(barcode.boundingBox?.bottom ?: 0) - (barcode.boundingBox?.top ?: 0)}
            },
            "timestamp": ${System.currentTimeMillis()}
        }
        """.trimIndent()
    }

    /**
     * แปลง Barcodes list เป็น JSON array
     */
    fun barcodesToJson(barcodes: List<Barcode>): String {
        val jsonArray = barcodes.joinToString(",", "[", "]") { barcodeToJson(it) }
        return jsonArray
    }

    private fun getFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            else -> "UNKNOWN"
        }
    }

    private fun getTypeName(type: Int): String {
        return when (type) {
            Barcode.TYPE_CONTACT_INFO -> "CONTACT_INFO"
            Barcode.TYPE_EMAIL -> "EMAIL"
            Barcode.TYPE_ISBN -> "ISBN"
            Barcode.TYPE_PHONE -> "PHONE"
            Barcode.TYPE_PRODUCT -> "PRODUCT"
            Barcode.TYPE_SMS -> "SMS"
            Barcode.TYPE_TEXT -> "TEXT"
            Barcode.TYPE_URL -> "URL"
            Barcode.TYPE_WIFI -> "WIFI"
            Barcode.TYPE_GEO -> "GEO"
            Barcode.TYPE_CALENDAR_EVENT -> "CALENDAR_EVENT"
            Barcode.TYPE_DRIVER_LICENSE -> "DRIVER_LICENSE"
            else -> "UNKNOWN"
        }
    }

    /**
     * ปิด scanner และ release resources
     */
    fun close() {
        scanner.close()
        isScanning.set(false)
    }
}

