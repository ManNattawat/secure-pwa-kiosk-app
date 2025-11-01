# 📍 Location Tracking & History - ข้อมูลการติดตามตำแหน่ง

## ✅ ตอบคำถาม: PWA สามารถตรวจจับพิกัด GPS ได้หรือไม่?

**คำตอบ: ได้ครับ!** แอพนี้มีระบบติดตาม GPS แล้ว และสามารถดูประวัติการเดินทางได้

---

## 🎯 ฟีเจอร์ที่มีอยู่แล้ว

### 1. **GPS Tracking (ติดตาม GPS แบบเรียลไทม์)**
- ใช้ `LocationSyncService` ที่ทำงานเป็น Background Service
- ติดตามตำแหน่งทุก 5 นาที (configurable)
- ใช้ Google Play Services Location API (Fused Location Provider)
- ส่งข้อมูลไปยัง Supabase อัตโนมัติ

### 2. **Location History (ประวัติการเดินทาง)**
- ใช้ `LocationHistoryTracker` สำหรับบันทึกประวัติ
- บันทึกทั้งในเครื่อง (SharedPreferences) และบน Cloud (Supabase)
- เก็บประวัติสูงสุด 100 จุดในเครื่อง (เพื่อประหยัดพื้นที่)
- สามารถดูเส้นทางระหว่างสองช่วงเวลาได้

---

## 📱 วิธีการใช้งาน

### ดึงประวัติการเดินทาง

```kotlin
// ดึงประวัติทั้งหมด
val history = LocationHistoryTracker.getLocalHistory(context)

// ดึงประวัติล่าสุด 10 จุด
val recentHistory = LocationHistoryTracker.getLocalHistory(context, limit = 10)

// ดึงตำแหน่งล่าสุด
val lastLocation = LocationHistoryTracker.getLastLocation(context)

// ดึงเส้นทางระหว่างสองช่วงเวลา
val route = LocationHistoryTracker.getRouteBetween(
    context, 
    startTime = System.currentTimeMillis() - 3600000, // 1 ชั่วโมงที่แล้ว
    endTime = System.currentTimeMillis()
)
```

### ข้อมูลที่บันทึก
- **Latitude/Longitude** - พิกัด GPS
- **Accuracy** - ความแม่นยำ (เมตร)
- **Bearing** - ทิศทาง (องศา)
- **Speed** - ความเร็ว (m/s)
- **Timestamp** - เวลาที่บันทึก
- **Provider** - แหล่งข้อมูล (GPS, Network, etc.)

### คำนวณระยะทาง

```kotlin
val point1 = history[0]
val point2 = history[1]
val distance = point1.distanceTo(point2) // ระยะทางเป็นเมตร
```

---

## 🔧 การตั้งค่า

### Location Permissions
แอพขอ permissions ต่อไปนี้ (มีอยู่แล้วใน AndroidManifest.xml):
- `ACCESS_FINE_LOCATION` - สำหรับ GPS แม่นยำ
- `ACCESS_COARSE_LOCATION` - สำหรับ Network-based location
- `ACCESS_BACKGROUND_LOCATION` - สำหรับติดตามแบบ background

### Interval Settings
ใน `LocationSyncService.kt`:
- `interval = 5 minutes` - ติดตามทุก 5 นาที
- `fastestInterval = 2 minutes` - ติดตามเร็วสุด 2 นาที
- `priority = HIGH_ACCURACY` - ใช้ GPS แบบแม่นยำสูง

---

## 📊 ตัวอย่างข้อมูลที่บันทึก

```json
{
  "device_id": "device_001",
  "latitude": 13.7563,
  "longitude": 100.5018,
  "accuracy": 10.5,
  "bearing": 45.0,
  "speed": 5.2,
  "recorded_at": "2025-01-20T10:30:00Z",
  "provider": "gps"
}
```

---

## 🗺️ การแสดงผลเส้นทาง

### ใน PWA
สามารถสร้างหน้าเว็บที่:
1. ดึงประวัติจาก Supabase
2. แสดงบน Google Maps หรือ Leaflet
3. แสดงเส้นทางการเดินทางเป็น polyline
4. แสดงจุดสำคัญ (จุดเริ่มต้น, จุดสิ้นสุด, จุดเปลี่ยนทิศทาง)

### ตัวอย่างโค้ด PWA

```javascript
// ดึงประวัติจาก Supabase
const { data } = await supabase
  .from('device_locations')
  .select('*')
  .eq('device_id', deviceId)
  .order('recorded_at', { ascending: true })

// แสดงบนแผนที่
const path = data.map(loc => [loc.latitude, loc.longitude])
L.polyline(path, { color: 'blue' }).addTo(map)
```

---

## ⚠️ ข้อควรระวัง

1. **Privacy** - ต้องได้รับอนุญาตจากผู้ใช้ก่อนติดตาม GPS
2. **Battery** - การติดตาม GPS แบบต่อเนื่องอาจใช้แบตเตอรี่มาก
3. **Storage** - ประวัติในเครื่องจำกัด 100 จุด เพื่อประหยัดพื้นที่
4. **Network** - ต้องการ Internet เพื่อส่งข้อมูลไปยัง Supabase

---

## 🎨 ตัวอย่าง UI สำหรับดูประวัติ

สามารถสร้างหน้าจอใน PWA แสดง:
- **แผนที่** - แสดงเส้นทางการเดินทาง
- **Timeline** - แสดงรายการตำแหน่งตามเวลา
- **Statistics** - แสดงสถิติ (ระยะทาง, เวลา, ความเร็วเฉลี่ย)

---

## ✅ สรุป

✅ **PWA สามารถตรวจจับพิกัด GPS ได้** - ผ่าน `LocationSyncService`  
✅ **ดูประวัติการเดินทางได้** - ผ่าน `LocationHistoryTracker`  
✅ **บันทึกทั้งในเครื่องและ Cloud** - สำหรับ backup และ sync  
✅ **แสดงบนแผนที่ได้** - ดึงข้อมูลจาก Supabase แล้วแสดงบน Google Maps/Leaflet  

---

ต้องการเพิ่มฟีเจอร์ใดเพิ่มเติมหรือไม่? เช่น:
- แจ้งเตือนเมื่อเคลื่อนที่ออกจากพื้นที่
- Geofencing (กำหนดเขตพื้นที่)
- รายงานสถิติการเดินทาง

