# 🌉 Android Bridge Usage Guide - คู่มือการใช้งาน

## 📱 วิธีเรียกใช้ Native App Features จาก PWA

### 1. **ตรวจสอบว่า AndroidBridge พร้อมหรือไม่**

```typescript
// ใน PWA
if (window.AndroidBridge) {
    console.log('Android Bridge is available!');
} else {
    console.log('Running on web browser, Android Bridge not available');
}
```

---

## 🎯 API ที่ใช้ได้

### **A. Remote Control**

#### 1. **Lock Device (ล็อกเครื่อง)**
```typescript
if (window.AndroidBridge) {
    const result = JSON.parse(window.AndroidBridge.lockDevice());
    if (result.success) {
        console.log('Device locked successfully');
    } else {
        console.error('Failed to lock:', result.error);
    }
}
```

#### 2. **Wipe Device (ลบข้อมูลทั้งหมด)**
```typescript
if (window.AndroidBridge) {
    const result = JSON.parse(window.AndroidBridge.wipeDevice("0"));
    if (result.success) {
        console.log('Device wipe initiated');
    }
}
```

#### 3. **Reboot Device (รีสตาร์ท)**
```typescript
if (window.AndroidBridge) {
    const result = JSON.parse(window.AndroidBridge.rebootDevice());
    if (result.success) {
        console.log('Device reboot initiated');
    }
}
```

#### 4. **Get Device Status (ดูสถานะเครื่อง)**
```typescript
if (window.AndroidBridge) {
    const result = JSON.parse(window.AndroidBridge.getDeviceStatus());
    if (result.success) {
        const status = result.data;
        console.log('Device Owner:', status.isDeviceOwner);
        console.log('Device Admin:', status.isDeviceAdmin);
        console.log('Android Version:', status.androidVersion);
        console.log('Device Model:', status.deviceModel);
    }
}
```

---

### **B. Location Features**

#### 5. **Get Location History (ดึงประวัติการเดินทาง)**
```typescript
if (window.AndroidBridge) {
    // ดึงประวัติทั้งหมด
    const result = JSON.parse(window.AndroidBridge.getLocationHistory("null"));
    
    // หรือดึงแค่ 50 รายการล่าสุด
    const result = JSON.parse(window.AndroidBridge.getLocationHistory("50"));
    
    if (result.success) {
        const locations = result.data;
        console.log(`Found ${result.count} location(s)`);
        locations.forEach((loc, index) => {
            console.log(`${index + 1}. ${loc.latitude}, ${loc.longitude} at ${new Date(loc.timestamp)}`);
        });
    }
}
```

#### 6. **Get Last Location (ดึงตำแหน่งล่าสุด)**
```typescript
if (window.AndroidBridge) {
    const result = JSON.parse(window.AndroidBridge.getLastLocation());
    if (result.success) {
        const location = result.data;
        console.log('Last location:', location.latitude, location.longitude);
    }
}
```

---

### **C. Barcode Scanner**

#### 7. **Scan Barcode from Image (สแกนบาร์โค้ดจากรูปภาพ)**
```typescript
if (window.AndroidBridge) {
    // รับ image จาก camera หรือ file input
    const fileInput = document.getElementById('imageInput');
    const file = fileInput.files[0];
    
    const reader = new FileReader();
    reader.onload = function(e) {
        const base64Image = e.target.result; // data:image/jpeg;base64,/9j/4AAQ...
        
        // กำหนด callback function
        window.onBarcodeScanned = function(resultJson) {
            const result = JSON.parse(resultJson);
            if (result.success && result.count > 0) {
                const barcodes = result.data;
                barcodes.forEach(barcode => {
                    console.log('Barcode found:', barcode.rawValue);
                    console.log('Format:', barcode.formatName);
                    console.log('Type:', barcode.typeName);
                });
            } else {
                console.log('No barcode found');
            }
        };
        
        // เรียกสแกน
        window.AndroidBridge.scanBarcodeFromImage(base64Image, 'onBarcodeScanned');
    };
    reader.readAsDataURL(file);
}
```

---

### **D. Device Info**

#### 8. **Get Device ID (ดึง Device ID)**
```typescript
if (window.AndroidBridge) {
    const result = JSON.parse(window.AndroidBridge.getDeviceId());
    if (result.success) {
        console.log('Device ID:', result.data);
    }
}
```

---

## 📝 TypeScript Wrapper (แนะนำ)

สร้างไฟล์ `src/utils/androidBridge.ts`:

```typescript
interface AndroidBridge {
    lockDevice(): string;
    wipeDevice(flags?: string): string;
    rebootDevice(): string;
    getDeviceStatus(): string;
    getLocationHistory(limit?: string): string;
    getLastLocation(): string;
    getDeviceId(): string;
    scanBarcodeFromImage(base64Image: string, callbackName: string): void;
}

interface DeviceStatus {
    isDeviceOwner: boolean;
    isDeviceAdmin: boolean;
    androidVersion: number;
    deviceModel: string;
    deviceManufacturer: string;
    packageName: string;
}

interface LocationHistoryItem {
    latitude: number;
    longitude: number;
    accuracy: number;
    bearing: number;
    speed: number;
    timestamp: number;
    provider: string;
}

interface BarcodeResult {
    rawValue: string;
    format: number;
    formatName: string;
    type: number;
    typeName: string;
    boundingBox: {
        left: number;
        top: number;
        right: number;
        bottom: number;
        width: number;
        height: number;
    };
    timestamp: number;
}

class AndroidBridgeService {
    private bridge: AndroidBridge | null;

    constructor() {
        this.bridge = (window as any).AndroidBridge || null;
    }

    isAvailable(): boolean {
        return this.bridge !== null;
    }

    async lockDevice(): Promise<{ success: boolean; message?: string; error?: string }> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        return JSON.parse(this.bridge.lockDevice());
    }

    async wipeDevice(flags: number = 0): Promise<{ success: boolean; message?: string; error?: string }> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        return JSON.parse(this.bridge.wipeDevice(flags.toString()));
    }

    async rebootDevice(): Promise<{ success: boolean; message?: string; error?: string }> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        return JSON.parse(this.bridge.rebootDevice());
    }

    async getDeviceStatus(): Promise<DeviceStatus | null> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        const result = JSON.parse(this.bridge.getDeviceStatus());
        return result.success ? result.data : null;
    }

    async getLocationHistory(limit?: number): Promise<LocationHistoryItem[]> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        const limitStr = limit ? limit.toString() : "null";
        const result = JSON.parse(this.bridge.getLocationHistory(limitStr));
        return result.success ? result.data : [];
    }

    async getLastLocation(): Promise<LocationHistoryItem | null> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        const result = JSON.parse(this.bridge.getLastLocation());
        return result.success ? result.data : null;
    }

    async getDeviceId(): Promise<string | null> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        const result = JSON.parse(this.bridge.getDeviceId());
        return result.success ? result.data : null;
    }

    async scanBarcodeFromImage(base64Image: string): Promise<BarcodeResult[]> {
        if (!this.bridge) throw new Error('Android Bridge not available');
        
        return new Promise((resolve, reject) => {
            const callbackName = `onBarcodeScan_${Date.now()}`;
            
            (window as any)[callbackName] = (resultJson: string) => {
                try {
                    const result = JSON.parse(resultJson);
                    if (result.success) {
                        resolve(result.data || []);
                    } else {
                        reject(new Error(result.error || 'Scan failed'));
                    }
                } catch (e) {
                    reject(e);
                } finally {
                    // Cleanup callback
                    delete (window as any)[callbackName];
                }
            };
            
            this.bridge!.scanBarcodeFromImage(base64Image, callbackName);
        });
    }
}

export const androidBridge = new AndroidBridgeService();
```

---

## 🎯 ตัวอย่างการใช้งานใน PWA

### **ใน WebBarcodeScanner.tsx:**

```typescript
import { androidBridge } from '@/utils/androidBridge';

// แทนที่ Web Scanner ด้วย Native Scanner
const useNativeScanner = androidBridge.isAvailable();

if (useNativeScanner) {
    // ใช้ Native Scanner (เร็วกว่า, แม่นยำกว่า)
    const barcodes = await androidBridge.scanBarcodeFromImage(imageBase64);
    barcodes.forEach(barcode => {
        console.log('Scanned:', barcode.rawValue);
    });
} else {
    // Fallback ไปใช้ Web Scanner
    // ... existing web scanner code
}
```

---

## ⚠️ ข้อควรระวัง

1. **ตรวจสอบ Availability** - ตรวจสอบว่า `window.AndroidBridge` มีอยู่ก่อนใช้
2. **Error Handling** - ตรวจสอบ `success` field ในผลลัพธ์เสมอ
3. **Permissions** - Camera permission ต้องได้รับอนุญาตก่อนสแกนบาร์โค้ด
4. **Async Operations** - `scanBarcodeFromImage` เป็น async ต้องใช้ callback

---

## ✅ Status

- ✅ Remote Control (lock, wipe, reboot)
- ✅ Device Status
- ✅ Location History
- ✅ Last Location
- ✅ Device ID
- ✅ Barcode Scanner (from image)
- ⏳ Real-time Camera Scanner (Phase 2)

---

**พร้อมใช้งานแล้ว!** 🎉

