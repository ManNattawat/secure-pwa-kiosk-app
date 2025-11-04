# 📱 APK Build Instructions - SecurePwaKioskApp

**วันที่:** 2025-01-XX  
**สถานะ:** ⚠️ **มีปัญหา Build - ต้องใช้ Android Studio**

---

## 🎯 **สรุป**

### **✅ Compilation ผ่าน**
- ✅ Kotlin compilation สำเร็จ
- ✅ Warnings เฉพาะ (deprecated APIs, unused parameters)
- ✅ Version updated: `versionCode = 2`, `versionName = "1.1.0"`

### **❌ Build ไม่สำเร็จ (Command Line)**
- ❌ **Error:** `mergeReleaseJavaResource` 
- ❌ **Exception:** `com.google.common.base.VerifyException`

**⚠️ แนะนำ:** ใช้ **Android Studio** เพื่อ build APK แทน

---

## 🛠️ **วิธี Build APK**

### **Option 1: ใช้ Android Studio (แนะนำ)**

1. **เปิดโปรเจ็กต์:**
   - เปิด Android Studio
   - `File > Open...`
   - เลือก `D:\projects\SecurePwaKioskApp\android`

2. **Sync Project:**
   - รอให้ Gradle sync เสร็จ
   - ตรวจสอบว่าไม่มี errors

3. **Build APK:**
   - `Build > Clean Project`
   - `Build > Rebuild Project`
   - `Build > Build Bundle(s) / APK(s) > Build APK(s)`

4. **หา APK:**
   - `app/build/outputs/apk/release/app-release.apk`
   - หรือ `app/build/outputs/apk/debug/app-debug.apk`

---

### **Option 2: ใช้ Command Line (ถ้ายังมีปัญหา)**

```bash
cd D:\projects\SecurePwaKioskApp\android

# Clean
.\gradlew.bat clean

# Stop daemon
.\gradlew.bat --stop

# ลบ cache
Remove-Item -Recurse -Force .gradle -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue

# Build
.\gradlew.bat assembleRelease --no-daemon --info
```

**ถ้ายังมีปัญหา:** ใช้ Android Studio แทน

---

## 📦 **APK Information**

### **Package Details:**
- **Package Name:** `com.gse.securekiosk.v2`
- **Version Code:** `2`
- **Version Name:** `1.1.0`
- **Min SDK:** `26` (Android 8.0)
- **Target SDK:** `35` (Android 15)

### **PWA Configuration:**
- **PWA URL:** `https://gse-enterprise-platform.pages.dev`
- **Configured in:** `android/app/src/main/res/values/strings.xml`

### **APK Location:**
```
D:\projects\SecurePwaKioskApp\android\app\build\outputs\apk\release\app-release.apk
```

---

## 🔧 **ปัญหาที่พบ**

### **Error: VerifyException**
```
> Task :app:mergeReleaseJavaResource FAILED
> com.google.common.base.VerifyException (no error message)
```

**สาเหตุที่เป็นไปได้:**
1. Corrupted Java resources จาก dependencies
2. Path ที่ยาวเกินไป
3. Dependency conflict
4. Gradle cache issue

**วิธีแก้ไข:**
- ✅ ใช้ Android Studio (แก้ปัญหาได้ในหลายกรณี)
- ✅ ลบ `.gradle` และ `app/build` แล้ว rebuild
- ✅ ตรวจสอบ dependencies ใน `build.gradle.kts`

---

## 📝 **การแก้ไขที่ทำไปแล้ว**

### **1. แก้ Compilation Errors:**
- ✅ `LocationSyncService.kt` - ลบ `getEmployeeId()` 
- ✅ `OfflineStorageManager.kt` - เปลี่ยน `put(null)` เป็น `putNull()`
- ✅ `UnifiedSyncManager.kt` - เพิ่ม cast สำหรับ JSONObject
- ✅ `FileUploadManager.kt` - แก้ import `okio.buffer`

### **2. อัพเดท Version:**
- ✅ `versionCode`: 1 → 2
- ✅ `versionName`: "1.0.0" → "1.1.0"

---

## ✅ **ขั้นตอนต่อไป**

1. **เปิด Android Studio**
2. **Build APK** ตามวิธีใน Option 1
3. **ติดตั้ง APK** บนอุปกรณ์
4. **ทดสอบ** - แอพจะโหลด PWA จาก `https://gse-enterprise-platform.pages.dev`

---

**หมายเหตุ:** เมื่อ PWA deploy แล้ว APK จะโหลดเวอร์ชันล่าสุดอัตโนมัติ (ไม่ต้อง build APK ใหม่)

---

**อัปเดตล่าสุด:** 2025-01-XX

