# 🔐 GitHub Secrets Setup Guide

**วันที่:** 2025-01-XX  
**Repository:** `secure-pwa-kiosk-app`

---

## 🎯 **Secrets ที่ต้องตั้งค่า**

สำหรับ GitHub Actions build workflow ต้องตั้งค่า secrets ต่อไปนี้:

---

## 📋 **Required Secrets**

### **1. SECURE_KIOSK_SUPABASE_URL**
**Description:** Supabase project URL  
**Example:** `https://cifnlfayusnkpnamelga.supabase.co`  
**Location:** Supabase Dashboard → Settings → API → Project URL

### **2. SECURE_KIOSK_SUPABASE_KEY**
**Description:** Supabase Anon Key (Frontend)  
**Example:** `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`  
**Location:** Supabase Dashboard → Settings → API → anon/public key

### **3. SECURE_KIOSK_PWA_URL**
**Description:** PWA URL ที่ APK จะโหลด  
**Default:** `https://gse-enterprise-platform.pages.dev`  
**Note:** ถ้าไม่ตั้งค่า จะใช้ค่า default จาก `strings.xml`

### **4. SECURE_KIOSK_CERT_PINS** (Optional)
**Description:** Certificate Pins สำหรับ Certificate Pinning  
**Format:** One pin per line  
**Example:**
```
sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
```

---

## 🔧 **วิธีตั้งค่า Secrets**

### **ขั้นตอน:**

1. **ไปที่ GitHub Repository:**
   - URL: `https://github.com/ManNattawat/secure-pwa-kiosk-app`
   - หรือ repository ที่คุณใช้

2. **เปิด Settings:**
   - คลิก "Settings" ในเมนูด้านบนของ repository

3. **ไปที่ Secrets and variables:**
   - คลิก "Secrets and variables" ใน sidebar ด้านซ้าย
   - คลิก "Actions"

4. **เพิ่ม Secrets:**
   - คลิก "New repository secret"
   - กรอก:
     - **Name:** `SECURE_KIOSK_SUPABASE_URL`
     - **Secret:** `https://cifnlfayusnkpnamelga.supabase.co`
   - คลิก "Add secret"
   - ทำซ้ำสำหรับ secrets อื่นๆ

---

## ✅ **Checklist**

- [ ] `SECURE_KIOSK_SUPABASE_URL` - Supabase project URL
- [ ] `SECURE_KIOSK_SUPABASE_KEY` - Supabase anon key
- [ ] `SECURE_KIOSK_PWA_URL` - PWA URL (default: `https://gse-enterprise-platform.pages.dev`)
- [ ] `SECURE_KIOSK_CERT_PINS` - Certificate pins (optional)

---

## 📝 **หมายเหตุ**

- **Secrets จะถูก inject** เข้าไปใน `strings.xml` ระหว่าง build
- **ถ้าไม่ตั้งค่า secrets:** Workflow อาจจะ build ได้ แต่ APK อาจไม่ทำงานถูกต้อง
- **Secrets ถูกใช้ใน:** `.github/workflows/android-build.yml` → `Inject secrets into config` step

---

## 🔍 **ตรวจสอบ Secrets**

### **วิธีตรวจสอบว่า secrets ถูกตั้งค่าแล้ว:**

1. ไปที่: `https://github.com/ManNattawat/secure-pwa-kiosk-app/settings/secrets/actions`
2. ดู "Repository secrets" section
3. ควรมี secrets ทั้ง 4 ตัว (หรืออย่างน้อย 3 ตัวถ้าไม่ใช้ CERT_PINS)

---

## 🚨 **Troubleshooting**

### **Build Fail:**
- ตรวจสอบว่า secrets ถูกตั้งค่าครบแล้ว
- ตรวจสอบว่า values ถูกต้อง (URL, Key)
- ดู logs ใน GitHub Actions

### **APK ไม่ทำงาน:**
- ตรวจสอบว่า `SECURE_KIOSK_PWA_URL` ถูกต้อง
- ตรวจสอบว่า `SECURE_KIOSK_SUPABASE_URL` และ `SECURE_KIOSK_SUPABASE_KEY` ถูกต้อง

---

**อัปเดตล่าสุด:** 2025-01-XX

