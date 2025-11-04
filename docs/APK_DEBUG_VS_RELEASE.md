# 📱 APK Debug vs Release - คำอธิบาย

**วันที่:** 2025-01-XX

---

## ✅ **คำตอบ: app-debug.apk ถูกต้อง!**

### **Debug APK:**
- ✅ **ใช้ได้** - ทำงานเหมือน Release APK
- ✅ **เหมาะสำหรับทดสอบ** - มี debug symbols
- ✅ **ขนาดใหญ่กว่า** - มี debug info
- ⚠️ **Signed ด้วย debug keystore** - ใช้สำหรับทดสอบ

### **Release APK:**
- ✅ **เหมาะสำหรับ Production** - Optimized
- ✅ **ขนาดเล็กกว่า** - ไม่มี debug info
- ⚠️ **Unsigned** - ต้อง sign ก่อนติดตั้ง (หรือเปิด "Install from Unknown Sources")

---

## 🎯 **สรุป**

**ติดตั้ง `app-debug.apk` ถูกต้อง!** ✅

- ใช้สำหรับทดสอบได้
- ทำงานเหมือน Release APK
- ถ้าต้องการ Production → ใช้ `app-release-unsigned.apk` แทน

---

**อัปเดตล่าสุด:** 2025-01-XX

