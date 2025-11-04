# 🔒 Certificate Pins - คำอธิบาย

**วันที่:** 2025-01-XX

---

## 🎯 **Certificate Pins คืออะไร?**

**Certificate Pinning** = การกำหนด SSL certificate ที่อนุญาตให้เชื่อมต่อเท่านั้น

### **ทำไมต้องใช้?**
- ✅ ป้องกันการโจมตีแบบ Man-in-the-Middle (MITM)
- ✅ ป้องกันการดักฟังข้อมูล
- ✅ เพิ่มความปลอดภัยให้กับ HTTPS connection

### **วิธีการทำงาน:**
- แอพจะตรวจสอบ SSL certificate ของ server
- ถ้า certificate ไม่ตรงกับ pins ที่กำหนด → ปฏิเสธการเชื่อมต่อ
- ถ้า certificate ตรงกับ pins → อนุญาตการเชื่อมต่อ

---

## ❓ **ต้องสร้าง SECURE_KIOSK_CERT_PINS หรือไม่?**

### **คำตอบ: ไม่บังคับ (Optional)**

**ถ้าไม่สร้าง:**
- ✅ แอพยังทำงานได้ปกติ
- ✅ ใช้ HTTPS connection ปกติ
- ✅ ไม่มี certificate pinning

**ถ้าสร้าง:**
- ✅ เพิ่มความปลอดภัยมากขึ้น
- ✅ ป้องกัน MITM attacks
- ⚠️ ถ้า certificate เปลี่ยน → ต้องอัพเดท pins

---

## 🔧 **ถ้าต้องการสร้าง Certificate Pins**

### **วิธีหา Certificate Pins:**

#### **1. จาก Supabase (ถ้าใช้ Supabase)**

**OpenSSL:**
```bash
openssl s_client -connect cifnlfayusnkpnamelga.supabase.co:443 -servername cifnlfayusnkpnamelga.supabase.co < /dev/null 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
```

**Online Tool:**
- ไปที่: https://www.ssllabs.com/ssltest/
- ใส่ URL: `cifnlfayusnkpnamelga.supabase.co`
- ดู "Certificate #1" → "SHA-256 Fingerprint"
- Format: `sha256/XXXXXXXXXX...`

#### **2. จาก PWA URL (Cloudflare Pages)**

**Cloudflare Pages:**
- ใช้ Cloudflare SSL certificate
- Certificate pins อาจเปลี่ยนได้เมื่อ Cloudflare rotate certificates
- **แนะนำ:** ใช้ Cloudflare Certificate Transparency logs

**วิธีหา:**
```bash
# ใช้ openssl
openssl s_client -connect gse-enterprise-platform.pages.dev:443 -servername gse-enterprise-platform.pages.dev < /dev/null 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
```

---

## 📝 **Format ของ Certificate Pins**

### **รูปแบบ:**
```
sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
```

### **ตัวอย่าง:**
```
sha256/E5bN5c7z3rOL6F3z7vK7T5mK7T5mK7T5mK7T5mK7T5=
sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
```

**Note:** สามารถมีหลาย pins (backup pins) เพื่อรองรับการเปลี่ยน certificate

---

## ✅ **คำแนะนำ**

### **สำหรับตอนนี้:**

**ไม่ต้องสร้าง SECURE_KIOSK_CERT_PINS** เพราะ:
1. ✅ เป็น optional (ไม่บังคับ)
2. ✅ แอพทำงานได้ปกติโดยไม่ต้องใช้
3. ✅ ถ้าต้องการเพิ่มความปลอดภัยภายหลัง สามารถเพิ่มได้

### **ถ้าต้องการสร้างภายหลัง:**

1. หา certificate pins จาก server (Supabase หรือ Cloudflare)
2. ไปที่ GitHub Secrets
3. เพิ่ม `SECURE_KIOSK_CERT_PINS` ด้วยค่า pins ที่หาได้

---

## 🎯 **สรุป**

- ✅ **Certificate Pins = Optional** (ไม่บังคับ)
- ✅ **ถ้าไม่สร้าง → แอพยังทำงานได้**
- ✅ **ถ้าต้องการเพิ่มความปลอดภัย → สามารถสร้างภายหลังได้**
- ✅ **สำหรับตอนนี้: ไม่ต้องสร้างก็ได้**

---

**อัปเดตล่าสุด:** 2025-01-XX

