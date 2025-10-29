# Secure PWA Kiosk App

แอป Wrapper สำหรับล็อกแท็บเล็ต Android ให้เปิดใช้งาน PWA เดียว พร้อมมาตรการรักษาความปลอดภัย (ป้องกันแคปหน้าจอ, kiosk mode, ควบคุม network, ส่งตำแหน่งขึ้น Supabase) แยกจาก MDM เดิมตาม blueprint ที่ได้รับ

## ภาพรวมสถาปัตยกรรม

```
┌──────────────────┐
│ Android Tablet    │
│  • Wrapper App    │
│    - WebView/TWA  │
│    - Lock Task    │
│    - FLAG_SECURE  │
│    - Location Svc │
└────────┬─────────┘
         │ HTTPS + Certificate Pinning
┌────────▼─────────┐
│ Supabase Backend │
│  • device_status │
│  • device_locations
│  • REST functions │
└──────────────────┘
```

- Provisioning ใช้ Android Enterprise/QR เพื่อกำหนด Device Owner + ติดตั้งแอปนี้เป็น Default Launcher
- Wrapper App แยกจาก MDM agent เดิมเพื่อลดผลกระทบต่อระบบที่ใช้งานอยู่
- ข้อมูลสถานะ/ตำแหน่งจะอัปเดต Supabase ผ่าน REST Function ที่มี certificate pinning

## โครงสร้างโฟลเดอร์

```
SecurePwaKioskApp/
├── README.md
├── provisioning/
│   └── device_owner_config.json
├── supabase/
│   ├── schema.sql
│   └── rest-api.md
└── android/
    ├── settings.gradle
    ├── build.gradle.kts
    ├── gradle.properties
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/gse/securekiosk/
            │   ├── MainActivity.kt
            │   ├── kiosk/KioskManager.kt
            │   ├── location/LocationSyncService.kt
            │   ├── supabase/SupabaseClient.kt
            │   └── util/DeviceInfoProvider.kt
            └── res/
                ├── layout/activity_main.xml
                └── values/strings.xml
```

> หมายเหตุ: ไม่มีการคัดลอกโค้ดจากระบบ MDM เดิม ทุกไฟล์เป็นของแอปใหม่ทั้งหมด

## ขั้นตอนหลัก

1. **Supabase** – สร้างตาราง `device_locations`, `device_status` และ REST Endpoint ตามไฟล์ในโฟลเดอร์ `supabase/`
2. **Android Wrapper App** – เปิดโปรเจ็กต์ `android/` ด้วย Android Studio, อัปเดตค่า PWA URL และ Supabase Key ใน `strings.xml`
3. **Provisioning** – ใช้ `device_owner_config.json` ในโฟลเดอร์ `provisioning/` สำหรับ QR Enrollment เพื่อตั้งให้แอปเป็น Device Owner
4. **ทดสอบ** – ลงบนเครื่องจริงและยืนยันการทำงานของ kiosk mode, การบล็อกแคปหน้าจอ, การส่งข้อมูลขึ้น Supabase

## ความปลอดภัยและ Audit

- ใช้ `FLAG_SECURE` ป้องกันการจับภาพหน้าจอ/record
- Lock Task Mode กำหนดให้แอปเป็น launcher ภายใต้ Device Owner
- Certificate pinning ผ่าน OkHttp เพื่อป้องกันการดักฟังระหว่างเครื่องกับ Supabase
- โค้ดอัปโหลดตำแหน่งทำงานใน Foreground Service พร้อม WorkManager สำหรับ retry
- การส่งข้อมูลมี Device ID (จาก `Settings.Secure.ANDROID_ID`) เพื่อตรวจสอบย้อนกลับ

## คำสั่งที่เกี่ยวข้อง

สร้าง/อัปเดตสคีมาที่ Supabase:

```sql
\i supabase/schema.sql
```

เปิดแอปบน Android Studio:

1. `File > Open...`
2. เลือกโฟลเดอร์ `SecurePwaKioskApp/android`

การสร้าง QR Enrollment (ผ่าน Android Management API หรือ provisioning tools) ใช้ค่าใน `provisioning/device_owner_config.json`

## Next Steps

- เชื่อม Supabase Realtime เพื่อทำ dashboard monitoring
- เพิ่ม Always-on VPN ตามนโยบายภายหลัง
- ผสานระบบแจ้งเตือนเมื่อเครื่อง offline หรือออกนอกพื้นที่ที่อนุญาต

## CI/CD ด้วย GitHub Actions

- Workflow อยู่ที่ `.github/workflows/android-build.yml`
- ต้องตั้ง Secrets:
  - `SECURE_KIOSK_SUPABASE_URL`
  - `SECURE_KIOSK_SUPABASE_KEY`
  - `SECURE_KIOSK_PWA_URL`
  - `SECURE_KIOSK_CERT_PINS` (คั่นด้วยบรรทัดหากมีหลายค่า)
- Pipeline จะ:
  1. แทนค่าใน `strings.xml` ด้วย Secrets จริง
  2. รัน `scripts/update_device_config.py` เพื่ออัปเดต provisioning JSON และ network security config
  3. บิลด์ทั้ง Debug / Release และอัปโหลด APK เป็น artifact

## แนวทาง Dashboard / Alert บน Supabase

1. สร้าง view สำหรับสถานะล่าสุด:

```sql
create or replace view public.device_status_latest as
  select distinct on (device_id)
    device_id,
    battery_percent,
    is_charging,
    network_type,
    connectivity,
    kiosk_locked,
    updated_at
  from public.device_status
  order by device_id, updated_at desc;
```

2. ใช้ Supabase Realtime subscription กับ view/triggers เพื่อแจ้งเตือนเมื่อ:
   - `connectivity` เปลี่ยนเป็น `DISCONNECTED`
   - `updated_at` ไม่อัปเดตเกิน 15 นาที (ใช้ edge function + cron)

3. Dashboard ตัวอย่าง:
   - สร้างหน้า Vue/React หรือ Supabase Studio เพื่อโชว์ตำแหน่งจาก `device_locations` (ใช้ PostgREST หรือ RPC)
   - ผสานกับ map component (Mapbox หรือ Leaflet)

4. ตั้ง Edge Function + Scheduler สำหรับ Push alert (เช่น Telegram/Email) โดยตรวจสอบเงื่อนไขด้านบนทุก 5 นาที
