# 📱 Enhanced OCR System - Developer Guide

## 🎯 Overview

ระบบ OCR ที่ปรับปรุงสำหรับการอ่านบัตรประชาชนไทย พร้อม Enhanced Parsing และ Validation

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────┐
│          DocumentScannerActivity                │
│  ┌──────────────┐  ┌──────────────┐           │
│  │ CameraX      │→ │ ML Kit OCR   │           │
│  │ Capture      │  │ Text Extract │           │
│  └──────────────┘  └──────┬───────┘           │
│                            │                    │
│                     ┌──────▼───────┐           │
│                     │ ThaiIDCard   │           │
│                     │ Parser       │           │
│                     └──────┬───────┘           │
│                            │                    │
│                     ┌──────▼───────┐           │
│                     │ Validation   │           │
│                     │ & Confidence │           │
│                     └──────┬───────┘           │
└────────────────────────────┼────────────────────┘
                             │
                    ┌────────▼────────┐
                    │ AndroidBridge   │
                    │ JavaScript      │
                    │ Interface       │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │      PWA        │
                    └─────────────────┘
```

---

## 📦 Components

### 1. ThaiIDCardParser.kt

สร้างไฟล์: `com/gse/securekiosk/ocr/ThaiIDCardParser.kt`

#### Features
- ✅ Extract เลขบัตร 13 หลัก
- ✅ Extract ชื่อ-นามสกุล (ไทย + อังกฤษ)
- ✅ Extract วันเกิด, วันออกบัตร, วันหมดอายุ
- ✅ Extract ที่อยู่
- ✅ Extract ศาสนา
- ✅ Validate เลขบัตรด้วย Checksum
- ✅ Calculate Confidence score
- ✅ Error reporting

#### API

```kotlin
val parsedData = ThaiIDCardParser.parse(ocrText)

// Returns: IDCardData
data class IDCardData(
    val idNumber: String,
    val titleTh: String,
    val firstNameTh: String,
    val lastNameTh: String,
    val titleEn: String,
    val firstNameEn: String,
    val lastNameEn: String,
    val birthDate: String,      // DD/MM/YYYY
    val issueDate: String,       // DD/MM/YYYY
    val expiryDate: String,      // DD/MM/YYYY
    val address: String,
    val religion: String,
    val confidence: Float,       // 0.0 - 1.0
    val isValid: Boolean,
    val errors: List<String>
)
```

#### Parsing Logic

##### 1. ID Number Extraction
```kotlin
// Patterns:
// - 1-2345-67890-12-3 (formatted)
// - 1234567890123 (plain)
// - เลขประจำตัวประชาชน: 1-2345-67890-12-3

// Validation:
// Checksum = (11 - (sum % 11)) % 10
```

##### 2. Name Extraction
```kotlin
// Thai: นาย/นาง/นางสาว/เด็กชาย/เด็กหญิง
// English: Mr./Mrs./Miss/Master

// Extract ชื่อ-นามสกุล ตามคำนำหน้า
```

##### 3. Date Extraction
```kotlin
// Patterns:
// - 01 ม.ค. 2563
// - 01/01/2563
// - 01-01-2563

// Auto-convert Buddhist year to Christian year
// (พ.ศ. 2563 → ค.ศ. 2020)
```

#### Confidence Scoring

```kotlin
// Weights:
// - ID Number (valid): 40%
// - First Name (Thai): 15%
// - Last Name (Thai): 15%
// - Birth Date: 20%
// - Expiry Date: 10%

// Total: 100%
```

---

### 2. DocumentScannerActivity.kt

Enhanced with Thai ID Card support

#### Changes

```kotlin
private fun processImageWithOCR(bitmap: Bitmap) {
    val enhancedParsing = intent.getBooleanExtra("enhanced_parsing", false)
    val documentType = intent.getStringExtra("document_type") ?: "generic"
    
    OcrService.extractText(image) { ocrResult ->
        val finalResult = if (enhancedParsing && documentType == "thai_id_card") {
            // Use ThaiIDCardParser
            val parsedData = ThaiIDCardParser.parse(ocrResult.fullText)
            
            // Combine results
            JSONObject(ocrResult.toJson()).apply {
                put("parsedData", JSONObject(parsedData.toJson()))
                put("documentType", "thai_id_card")
            }.toString()
        } else {
            ocrResult.toJson()
        }
        
        // Return to PWA
        returnResult(finalResult)
    }
}
```

---

### 3. AndroidBridge.kt

Added new methods for Thai ID Card scanning

#### New Methods

```kotlin
@JavascriptInterface
fun scanThaiIDCard(callbackName: String = ""): String {
    // Store callback
    documentScanCallbackName = callbackName
    
    // Launch scanner with enhanced parsing
    val intent = Intent(activity, DocumentScannerActivity::class.java)
    intent.putExtra("document_type", "thai_id_card")
    intent.putExtra("enhanced_parsing", true)
    activity?.startActivityForResult(intent, DocumentScannerActivity.REQUEST_CODE)
    
    return JSONObject().apply {
        put("success", true)
        put("message", "Thai ID card scanner opened")
    }.toString()
}

@JavascriptInterface
fun scanDocument(callbackName: String = ""): String {
    // Generic document scanning (no enhanced parsing)
    // ...
}
```

---

## 🔌 PWA Integration

### 1. TypeScript Wrapper

`src/utils/idCardScanner.ts`

```typescript
export async function scanThaiIDCard(): Promise<OCRResult> {
  return new Promise((resolve, reject) => {
    const callbackName = `idCardCallback_${Date.now()}`
    
    window[callbackName] = (resultJson: string) => {
      const result = JSON.parse(resultJson)
      delete window[callbackName]
      
      if (result.success !== false) {
        resolve({
          success: true,
          fullText: result.fullText,
          parsedData: result.parsedData,
          documentType: result.documentType
        })
      } else {
        reject(new Error(result.error))
      }
    }
    
    // Call native
    AndroidBridge.scanThaiIDCard(callbackName)
  })
}
```

### 2. React Hook

`src/hooks/useIDCardScanner.ts`

```typescript
export function useIDCardScanner() {
  const [data, setData] = useState(null)
  const [isScanning, setIsScanning] = useState(false)
  const [error, setError] = useState(null)

  const scan = async () => {
    setIsScanning(true)
    try {
      const result = await scanThaiIDCard()
      setData(result.parsedData)
    } catch (err) {
      setError(err.message)
    } finally {
      setIsScanning(false)
    }
  }

  return { scan, data, isScanning, error }
}
```

### 3. React Component

`src/components/IDCardScannerForm.tsx`

```tsx
export function IDCardScannerForm({ onDataExtracted }) {
  const { scan, data, isScanning } = useIDCardScanner()

  useEffect(() => {
    if (data) {
      onDataExtracted(data)
    }
  }, [data])

  return (
    <div>
      <button onClick={scan} disabled={isScanning}>
        {isScanning ? 'Scanning...' : 'Scan ID Card'}
      </button>
      
      {data && (
        <form>
          <input value={data.idNumber} />
          <input value={data.firstNameTh} />
          <input value={data.lastNameTh} />
          {/* ... */}
        </form>
      )}
    </div>
  )
}
```

---

## 🧪 Testing

### Test Cases

1. **Normal ID Card** (ชัดเจน, แสงดี)
   - Expected: Confidence ≥ 80%
   - All fields extracted

2. **Low Light** (แสงน้อย)
   - Expected: Confidence 60-79%
   - Some fields may be missing

3. **Blurry Card** (ภาพเบลอ)
   - Expected: Confidence < 60%
   - Errors reported

4. **Expired Card**
   - Expected: `errors` contains "บัตรหมดอายุแล้ว"

5. **Invalid ID Number**
   - Expected: `isValid = false`
   - `errors` contains "เลขบัตรประชาชนไม่ถูกต้อง"

### Manual Testing

```bash
# 1. Build APK
cd SecurePwaKioskApp/android
./gradlew assembleDebug

# 2. Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# 3. Open PWA
# Navigate to demo page

# 4. Test scanning
# - Scan clear ID card
# - Scan in low light
# - Scan with flash
# - Edit fields manually
# - Validate
```

### Automated Testing

```kotlin
@Test
fun testIDNumberValidation() {
    // Valid
    assertTrue(ThaiIDCardParser.isValidIDNumber("1234567890123"))
    
    // Invalid checksum
    assertFalse(ThaiIDCardParser.isValidIDNumber("1234567890120"))
    
    // Wrong length
    assertFalse(ThaiIDCardParser.isValidIDNumber("123"))
}

@Test
fun testNameExtraction() {
    val ocrText = "นาย สมชาย ใจดี\nMr. Somchai Jaidee"
    val parsed = ThaiIDCardParser.parse(ocrText)
    
    assertEquals("นาย", parsed.titleTh)
    assertEquals("สมชาย", parsed.firstNameTh)
    assertEquals("ใจดี", parsed.lastNameTh)
    assertEquals("Mr.", parsed.titleEn)
}

@Test
fun testConfidenceCalculation() {
    val data = IDCardData(
        idNumber = "1234567890123",
        firstNameTh = "สมชาย",
        lastNameTh = "ใจดี",
        birthDate = "01/01/1990",
        expiryDate = "01/01/2030",
        // ...
    )
    
    assertTrue(data.confidence >= 0.8)
}
```

---

## 🔧 Configuration

### ML Kit Dependencies

`build.gradle.kts`

```kotlin
dependencies {
    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
}
```

### Permissions

`AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
```

---

## 📈 Performance Optimization

### 1. Camera Resolution
```kotlin
// Use optimal resolution
imageCapture = ImageCapture.Builder()
    .setTargetResolution(Size(1080, 1920))
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    .build()
```

### 2. OCR Processing
```kotlin
// Run on background thread
val executor = Executors.newSingleThreadExecutor()
OcrService.extractText(image, executor) { result ->
    // Process result on main thread
    runOnUiThread {
        handleResult(result)
    }
}
```

### 3. Bitmap Compression
```kotlin
// Compress before processing
val compressed = Bitmap.createScaledBitmap(bitmap, 1080, 1920, true)
```

---

## 🐛 Troubleshooting

### Issue: Low Confidence Score

**Causes**:
- Poor lighting
- Blurry image
- Reflections on card
- Card not fully in frame

**Solutions**:
1. Enable flash
2. Hold card steady
3. Avoid reflections
4. Ensure card fills frame

### Issue: Wrong Data Extracted

**Causes**:
- OCR misread text
- Card damaged/faded
- Unusual card format

**Solutions**:
1. Rescan with better lighting
2. Manual correction in UI
3. Validate before save

### Issue: Crash on Scan

**Causes**:
- Camera permission denied
- ML Kit not initialized
- Memory issue

**Solutions**:
1. Check camera permission
2. Verify ML Kit dependency
3. Reduce image resolution

---

## 📚 References

- [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition)
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [Thai ID Card Format](https://th.wikipedia.org/wiki/บัตรประจำตัวประชาชนไทย)

---

**Last Updated**: November 3, 2025  
**Version**: 1.0.0  
**Status**: ✅ Production Ready

