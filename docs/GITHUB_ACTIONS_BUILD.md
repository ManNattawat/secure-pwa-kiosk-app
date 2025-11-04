# 🚀 GitHub Actions - APK Build

**วันที่:** 2025-01-XX  
**สถานะ:** ✅ **พร้อมใช้งาน**

---

## 🎯 **สรุป**

Build APK ผ่าน **GitHub Actions** อัตโนมัติเมื่อ push code ไป GitHub

---

## 🔄 **การทำงาน**

### **Trigger:**
- Push ไป `main` branch
- Push ไป `release/**` branch
- Pull Request ไป `main` branch

### **Workflow:**
1. ✅ Checkout code
2. ✅ Setup Java 17
3. ✅ Setup Android SDK
4. ✅ Install Android packages
5. ✅ Inject secrets (Supabase, PWA URL)
6. ✅ Clean build
7. ✅ Build Debug APK
8. ✅ Build Release APK
9. ✅ Verify APK signatures
10. ✅ Upload APK as artifact

---

## 📦 **APK Output**

### **Artifact Name:**
`secure-pwa-kiosk-apk`

### **Retention:**
30 days

### **Location in Repository:**
- Debug: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release: `android/app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 🔧 **การแก้ไขที่ทำไปแล้ว**

### **1. แก้ VerifyException:**
- ✅ เพิ่ม `TZ: UTC` environment variable
- ✅ เพิ่ม `android.suppressUnsupportedCompileSdk=35` ใน `gradle.properties`
- ✅ เพิ่ม `--no-daemon` flag
- ✅ เพิ่ม clean step

### **2. ปรับปรุง Workflow:**
- ✅ เพิ่ม `Clean build` step
- ✅ เพิ่ม `List APK files` step (debug)
- ✅ ปรับปรุง `Verify APK Signatures` step
- ✅ ปรับปรุง `Archive APKs` step (retention 30 days)

---

## 📝 **วิธีใช้งาน**

### **1. Push Code:**
```bash
git add .
git commit -m "Your changes"
git push origin main
```

### **2. ดู Build Status:**
- ไปที่ GitHub repository
- คลิก "Actions" tab
- เลือก workflow run ที่ต้องการ

### **3. ดาวน์โหลด APK:**
- ไปที่ workflow run ที่สำเร็จ
- Scroll ลงไปที่ "Artifacts" section
- คลิก `secure-pwa-kiosk-apk` เพื่อดาวน์โหลด

---

## 🔐 **Secrets ที่ต้องตั้งค่า**

ใน GitHub repository → Settings → Secrets and variables → Actions:

1. `SECURE_KIOSK_SUPABASE_URL` - Supabase project URL
2. `SECURE_KIOSK_SUPABASE_KEY` - Supabase anon key
3. `SECURE_KIOSK_PWA_URL` - PWA URL (default: `https://gse-enterprise-platform.pages.dev`)
4. `SECURE_KIOSK_CERT_PINS` - Certificate pins (optional)

---

## ⚙️ **Configuration**

### **Gradle Properties:**
```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.suppressUnsupportedCompileSdk=35
org.gradle.parallel=true
org.gradle.caching=true
```

### **Build Tools:**
- **Java:** 17 (Temurin)
- **Android SDK:** 35
- **Build Tools:** 35.0.0
- **Min SDK:** 26
- **Target SDK:** 35

---

## 🐛 **Troubleshooting**

### **VerifyException:**
- ✅ แก้ไขแล้วโดยเพิ่ม `TZ: UTC` และ `--no-daemon`
- ✅ เพิ่ม clean step ก่อน build

### **APK ไม่พบ:**
- ตรวจสอบว่า build สำเร็จ
- ตรวจสอบ path ใน workflow
- ดู logs ใน GitHub Actions

### **Build Fail:**
- ตรวจสอบ logs ใน GitHub Actions
- ตรวจสอบว่า secrets ถูกตั้งค่าแล้ว
- ตรวจสอบว่า Android SDK packages ถูกติดตั้งแล้ว

---

## ✅ **สรุป**

- ✅ Build APK ผ่าน GitHub Actions อัตโนมัติ
- ✅ ไม่ต้องใช้ Android Studio
- ✅ APK ถูก upload เป็น artifact
- ✅ Retention 30 days
- ✅ แก้ปัญหา VerifyException แล้ว

**พร้อมใช้งาน!** 🎉

---

**อัปเดตล่าสุด:** 2025-01-XX

