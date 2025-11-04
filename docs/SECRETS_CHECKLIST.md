# 🔐 Secrets Checklist - SecurePwaKioskApp

**วันที่:** 2025-01-XX  
**Repository:** `secure-pwa-kiosk-app` (NOT GSE-Enterprise-Platform)

---

## ⚠️ **สำคัญ!**

**Secrets ต้องตั้งค่าใน repository:** `secure-pwa-kiosk-app`  
**NOT:** `GSE-Enterprise-Platform`

---

## 📋 **Secrets ที่ต้องมีใน `secure-pwa-kiosk-app`**

### **1. SECURE_KIOSK_SUPABASE_URL**
- **Value:** `https://cifnlfayusnkpnamelga.supabase.co`
- **Source:** Supabase Dashboard → Settings → API

### **2. SECURE_KIOSK_SUPABASE_KEY**
- **Value:** `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNpZm5sZmF5dXNua3BuYW1lbGdhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM5NDc4MjgsImV4cCI6MjA2OTUyMzgyOH0.5Da2JLNG88DHSxv5sxmvGUcuSk8ZOgKNvwOcIoWLH-Q`
- **Source:** Supabase Dashboard → Settings → API → anon key

### **3. SECURE_KIOSK_PWA_URL**
- **Value:** `https://gse-enterprise-platform.pages.dev`
- **Source:** Cloudflare Pages URL

### **4. SECURE_KIOSK_CERT_PINS** (Optional)
- **Value:** Certificate pins (ถ้ามี)
- **Source:** SSL Certificate SHA-256 fingerprints

---

## 🔧 **วิธีตั้งค่า**

### **URL:**
```
https://github.com/ManNattawat/secure-pwa-kiosk-app/settings/secrets/actions
```

### **ขั้นตอน:**
1. ไปที่ repository `secure-pwa-kiosk-app`
2. Settings → Secrets and variables → Actions
3. คลิก "New repository secret"
4. เพิ่ม secrets ทั้ง 4 ตัว (หรือ 3 ตัวถ้าไม่ใช้ CERT_PINS)

---

## ✅ **Checklist**

- [ ] ไปที่ repository `secure-pwa-kiosk-app` (ไม่ใช่ GSE-Enterprise-Platform)
- [ ] ตั้งค่า `SECURE_KIOSK_SUPABASE_URL`
- [ ] ตั้งค่า `SECURE_KIOSK_SUPABASE_KEY`
- [ ] ตั้งค่า `SECURE_KIOSK_PWA_URL`
- [ ] ตั้งค่า `SECURE_KIOSK_CERT_PINS` (optional)

---

## 🔍 **ตรวจสอบ**

### **ตรวจสอบว่า secrets ถูกตั้งค่าแล้ว:**
1. ไปที่: `https://github.com/ManNattawat/secure-pwa-kiosk-app/settings/secrets/actions`
2. ดู "Repository secrets" section
3. ควรมี secrets ทั้งหมด

---

## 📝 **หมายเหตุ**

- **Secrets ใน `GSE-Enterprise-Platform`** ≠ **Secrets ใน `secure-pwa-kiosk-app`**
- **ต้องตั้งค่าแยกกัน** ในแต่ละ repository
- **Secrets จะถูกใช้ใน:** GitHub Actions workflow สำหรับ build APK

---

**อัปเดตล่าสุด:** 2025-01-XX

