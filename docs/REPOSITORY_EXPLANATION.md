# 📚 Repository Structure - คำอธิบาย

**วันที่:** 2025-01-XX

---

## 🎯 **สรุป**

มี **2 repositories** แยกกัน:

---

## 📦 **1. GSE-Enterprise-Platform**

**Repository:** `GSE-Enterprise-Platform`  
**URL:** `https://github.com/ManNattawat/GSE-Enterprise-Platform`

### **หน้าที่:**
- ✅ **PWA (Progressive Web App)** - ไม่มี APK
- ✅ Deploy เป็นเว็บ (Cloudflare Pages)
- ✅ เมื่อ deploy แล้ว → อัพเดทอัตโนมัติ

### **Secrets ที่มี:**
- `CLOUDFLARE_ACCOUNT_ID` - สำหรับ deploy PWA
- `CLOUDFLARE_API_TOKEN` - สำหรับ deploy PWA
- `SECURE_KIOSK_PWA_URL` - PWA URL (optional)
- `SECURE_KIOSK_SUPABASE_KEY` - Supabase key (optional)
- `SECURE_KIOSK_SUPABASE_URL` - Supabase URL (optional)

### **Output:**
- ✅ **PWA Website:** `https://gse-enterprise-platform.pages.dev`
- ❌ **ไม่มี APK**

---

## 🤖 **2. secure-pwa-kiosk-app**

**Repository:** `secure-pwa-kiosk-app`  
**URL:** `https://github.com/ManNattawat/secure-pwa-kiosk-app`

### **หน้าที่:**
- ✅ **Android Native App** - Build APK
- ✅ **Wrapper App** ที่โหลด PWA จาก `GSE-Enterprise-Platform`
- ✅ Build APK ผ่าน GitHub Actions

### **Secrets ที่ต้องมี:**
- `SECURE_KIOSK_SUPABASE_URL` - Supabase project URL
- `SECURE_KIOSK_SUPABASE_KEY` - Supabase anon key
- `SECURE_KIOSK_PWA_URL` - PWA URL (default: `https://gse-enterprise-platform.pages.dev`)
- `SECURE_KIOSK_CERT_PINS` - Certificate pins (optional)

### **Output:**
- ✅ **APK File:** `app-release-unsigned.apk`
- ✅ **APK Package:** `com.gse.securekiosk.v2`
- ✅ **Version:** `1.1.0` (Code: 2)

---

## 🔄 **ความสัมพันธ์**

```
┌─────────────────────────┐
│ GSE-Enterprise-Platform │
│      (PWA Repository)    │
│                          │
│  ┌──────────────────┐   │
│  │  Deploy to       │   │
│  │  Cloudflare      │   │
│  └────────┬─────────┘   │
│           │             │
│           ▼             │
│  https://gse-enterprise- │
│  platform.pages.dev     │
└───────────┬─────────────┘
            │
            │ (APK โหลด PWA จาก URL นี้)
            │
            ▼
┌─────────────────────────┐
│ secure-pwa-kiosk-app    │
│   (APK Repository)      │
│                          │
│  ┌──────────────────┐   │
│  │  Build APK       │   │
│  │  (GitHub Actions)│   │
│  └────────┬─────────┘   │
│           │             │
│           ▼             │
│  app-release-unsigned   │
│  .apk                   │
└─────────────────────────┘
```

---

## ⚠️ **ความสับสนที่พบบ่อย**

### **ผิด:**
- ❌ ไปดู secrets ใน `GSE-Enterprise-Platform` → คิดว่าจะ build APK
- ❌ คิดว่า PWA จะสร้าง APK ให้

### **ถูก:**
- ✅ **PWA** = ไม่มี APK (เป็นเว็บ)
- ✅ **APK** = ต้อง build จาก `secure-pwa-kiosk-app`
- ✅ **Secrets สำหรับ APK** = ต้องอยู่ใน `secure-pwa-kiosk-app`

---

## 📋 **Checklist**

### **GSE-Enterprise-Platform:**
- [x] ✅ PWA deploy แล้ว
- [x] ✅ Secrets สำหรับ Cloudflare deploy
- [x] ✅ URL: `https://gse-enterprise-platform.pages.dev`

### **secure-pwa-kiosk-app:**
- [ ] ⏳ Secrets สำหรับ build APK (ต้องสร้าง)
- [ ] ⏳ GitHub Actions build APK
- [ ] ⏳ Download APK จาก Actions artifacts

---

## 🎯 **สรุป**

### **GSE-Enterprise-Platform:**
- ✅ **PWA** - ไม่มี APK
- ✅ Deploy เป็นเว็บ
- ✅ Secrets สำหรับ deploy

### **secure-pwa-kiosk-app:**
- ✅ **APK** - Build APK
- ✅ GitHub Actions build
- ✅ Secrets สำหรับ build APK

---

**อัปเดตล่าสุด:** 2025-01-XX

