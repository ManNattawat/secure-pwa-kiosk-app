# Supabase REST Endpoints สำหรับ Secure PWA Kiosk App

## 1. Save Device Location

- **URL**: `https://<YOUR_PROJECT>.supabase.co/rest/v1/rpc/save_device_location`
- **Method**: `POST`
- **Headers**:
  - `apikey: <SERVICE_ROLE_KEY>`
  - `Authorization: Bearer <SERVICE_ROLE_KEY>`
  - `Content-Type: application/json`
- **Body** (ตัวอย่าง):

```json
{
  "payload": {
    "device_id": "a1b2c3d4",
    "latitude": 13.7563,
    "longitude": 100.5018,
    "accuracy": 5.0,
    "bearing": 90.0,
    "speed": 0.0,
    "recorded_at": "2025-10-29T08:15:30Z"
  }
}
```

- **Response**:

```json
{
  "status": "success"
}
```

## 2. Save Device Status

- **URL**: `https://<YOUR_PROJECT>.supabase.co/rest/v1/rpc/save_device_status`
- **Method**: `POST`
- **Headers** เหมือน endpoint ข้างบน
- **Body** (ตัวอย่าง):

```json
{
  "payload": {
    "device_id": "a1b2c3d4",
    "battery_percent": 87,
    "is_charging": true,
    "network_type": "WIFI",
    "connectivity": "CONNECTED",
    "kiosk_locked": true
  }
}
```

- **Response**: เช่นเดียวกับ endpoint แรก

## 3. นโยบายความปลอดภัยของคีย์

- ห้ามฝังคีย์ service role ในซอร์สโค้ด
- เก็บคีย์ไว้ใน secure storage ของอุปกรณ์ (เช่น EncryptedSharedPreferences) หลังจาก provisioning
- ทดลองด้วย service role เท่านั้น ห้ามเปิดใช้งาน endpoint นี้ด้วย anon key เพราะมีการเขียนข้อมูล

## 4. การปักหมุดใบรับรอง (Certificate Pinning)

- ดึง fingerprint (SHA-256) ของใบรับรอง Supabase ผ่านคำสั่ง:

```bash
openssl s_client -servername <YOUR_PROJECT>.supabase.co -connect <YOUR_PROJECT>.supabase.co:443 < /dev/null 2> /dev/null | openssl x509 -noout -fingerprint -sha256
```

- นำค่า fingerprint มาลงใน `certificate_pins` ภายใน provisioning JSON และในโค้ด OkHttp (ดู `SupabaseClient.kt`)

## 5. Realtime Dashboard (แนะนำ)

- สร้าง view หรือ materialized view สำหรับข้อมูลล่าสุดของสถานะเครื่อง
- ใช้ Supabase Realtime subscription ในหน้า dashboard เพื่อเฝ้าดูค่าการเชื่อมต่อแบบต่อเนื่อง
