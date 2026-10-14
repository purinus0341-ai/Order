# OrderApp POS & Stock Management (Android Native)

แอปพลิเคชันระบบขายหน้าร้าน (Point of Sale - POS) และระบบบริหารจัดการสต็อกสินค้า พัฒนาด้วย **Android Native (Kotlin + Jetpack Compose)** สำหรับร้านค้า คาเฟ่ และธุรกิจค้าปลีก-ส่ง โดยพอร์ตระบบและการทำงานจากระบบ OrderApp เดิมมาอย่างสมบูรณ์แบบ 100% พร้อมระบบทำงานออฟไลน์ (Offline-first Outbox) และการซิงค์ข้อมูลกับ Supabase

---

## 🎨 ธีมและการแสดงผล (Pastel Redesign)
ออกแบบด้วยพาเลทสีพาสเทลที่ผ่านเกณฑ์ความคมชัด (WCAG Contrast Standards) ให้อ่านง่าย สบายตา และดูทันสมัย:
- **พื้นหลังหลัก (Background):** ครีมอ่อน `#FDFBF7`
- **การ์ดและพื้นผิว (Card / Surface):** สีขาวบริสุทธิ์ `#FFFFFF`
- **สีหลัก (Primary):** เขียวมิ้นต์พาสเทล `#5B8A72`
- **สีรอง (Secondary):** ส้มพีช `#E07A5F`
- **สีที่สาม (Tertiary):** ฟ้าลาเวนเดอร์ `#6B7FD7`
- **สีตัวอักษรหลัก (Text Primary):** เข้ม ชัดเจน คมชัดสูง `#1F2421`
- **พื้นผิวรอง (Surface Variant):** `#F4F0EA`

---

## 🛠️ รายละเอียดการตั้งค่าและ Tech Stack
- **Platform:** Android Native
- **Package Name:** `com.sitticun.orderapp`
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK / Compile SDK:** 34 (Android 14)
- **Language:** Kotlin `2.0.0`
- **UI Framework:** Jetpack Compose (BOM `2024.06.00`)
- **Build System:** Gradle `8.7`, Android Gradle Plugin (AGP) `8.5.2`
- **JDK:** Java 17
- **Networking & API:** OkHttp 4.12, Kotlinx Serialization
- **Local Storage:** SQLite (`OrderDatabaseHelper`), SharedPreferences (`PreferenceManager`)
- **Image Loading:** Coil Compose 2.6

---

## 🌟 ฟีเจอร์หลักและกฎทางธุรกิจ (Business Rules)

### 1. ระบบล็อกอินและเช็กสิทธิ์ (Auth & Profile Active Guard)
- ล็อกอินผ่าน Supabase Auth (Email & Password)
- ตรวจสอบและรีเฟรช Token อัตโนมัติเมื่อหมดอายุ (`refresh_token`)
- **Profile Active Guard:** ตรวจสอบสถานะ `is_active` จากตาราง `profiles` ทุกครั้ง หากเป็น `false` จะระงับการเข้าถึงและบังคับออกจากระบบทันที
- มีระบบ Demo Login สำหรับการทดสอบออฟไลน์หน้าร้านได้ทันที

### 2. หน้าขายสินค้า POS (Sales Screen)
- ค้นหาสินค้าได้ทั้งจาก ชื่อ, SKU และรหัสบาร์โค้ด
- แท็บกรองหมวดหมู่สินค้า (Categories Filter)
- ตารางสินค้า (`ProductGrid`): แสดงภาพ, ชื่อ, ราคา, สถานะสต็อก (พร้อมแถบสีเตือนสต็อกใกล้หมด/หมด)
- เลือกลูกค้าเข้าบิล (`CustomerPickerDialog`): เลือกลูกค้าสมาชิกพร้อมแสดงแต้มสะสมปัจจุบัน หรือขายแบบ Walk-in
- เลือกของแถม (`FreeItemsEditorDialog`): กำหนดจำนวนของแถมสำหรับแต่ละสินค้า หรือใส่ของขวัญโปรโมชั่น
- ใช้แต้มสะสมลดราคา (`PointsEditorDialog`): กำหนดแต้มที่ต้องการแลก โดยระบบจำกัดไม่ให้เกินแต้มที่มีและไม่ให้เกินยอดรวมบิล

### 3. การคำนวณยอดขายและตัดสต็อก (Sales Engine & Idempotency)
- **แยกรายการชัดเจน:**
  - `paid_qty`: จำนวนชิ้นที่คิดเงิน
  - `free_qty`: จำนวนชิ้นของแถม (คิดเงิน ฿0.00)
  - `total_qty = paid_qty + free_qty`: ยอดตัดสต็อกรวม
- คิดเงินและคำนวณแต้มสะสมเฉพาะส่วนที่จ่ายเงิน หักลบด้วยส่วนลดแต้มที่แลก
- แนบ `idempotency_key` (UUID) และ `device_id` (ประจำเครื่อง) ส่งเข้า RPC `commit_sale` ป้องกันการส่งซ้ำ

### 4. ตะกร้าชั่วคราว (Drafts) และระบบพักบิล (Held Bills)
- บันทึกตะกร้าปัจจุบันลงเครื่องอัตโนมัติ (Auto-Draft) ป้องกันข้อมูลสูญหายเมื่อปิดหรือสลับแอป
- ปุ่ม **"พักบิล" (Hold Bill)**: เก็บรายการบิลที่ยังไม่เสร็จไว้ในระบบ เพื่อรับลูกค้ารายถัดไปได้ทันที
- ปุ่ม **"บิลที่พักไว้" (Held Bills)**: ดึงบิลที่พักกลับมาคิดเงินต่อ หรือเลือกลบบิลได้

### 5. ระบบสมาชิกและแต้มสะสม (Customers & Points)
- ค้นหา เพิ่ม และแก้ไขข้อมูลลูกค้า (ชื่อ, เบอร์โทร, อีเมล, โน้ต)
- ดูประวัติการสั่งซื้อย้อนหลังของลูกค้าแต่ละคน
- ระบบปรับปรุงแต้มสะสม (+/-): บังคับระบุเหตุผลในการปรับแต้มทุกครั้ง

### 6. ประวัติการขายและยกเลิกบิล (History & Atomic Void Order)
- แสดงประวัติการขายย้อนหลัง พร้อมตัวกรองสถานะ (ทั้งหมด, สำเร็จ, ยกเลิกแล้ว)
- ดูใบเสร็จรายละเอียดบิล (แยก paid_qty และ free_qty)
- **ปุ่มยกเลิกบิล (Void Order):**
  - บังคับกรอกเหตุผลในการยกเลิกบิล
  - คืนสต็อกสินค้าทั้งหมด (`paid_qty + free_qty`)
  - คืนแต้มสะสมของลูกค้าแบบ Atomic ผ่าน RPC `void_sale`

### 7. จัดการสต็อกและแจ้งเตือน LINE (Stock & LINE Notify)
- แสดงยอดสต็อกคงเหลือ และตัวกรองสินค้าใกล้หมด (Low Stock) / สินค้าหมด (Out of Stock)
- บันทึกการปรับยอดสต็อก (**Stock Snapshot**): ระบุเหตุผล เช่น ตรวจนับสต็อกประจำงวด, สินค้าเสียหาย, รับสินค้าเข้า
- ปุ่ม **"ส่งแจ้งเตือนเข้า LINE"**: เรียกใช้ Supabase Edge Function `line-stock-notify` เพื่อส่งรายการสินค้าใกล้หมดเข้า LINE อัตโนมัติ

### 8. ระบบนับสต็อกจากรูปภาพ (Image Counter)
- หน้ากระดาน Canvas แบบ Interactive สำหรับการนับสต็อกผ่านรูปถ่าย
- แตะบนรูปภาพเพื่อวางจุดมาร์กพร้อมตัวเลขลำดับอัตโนมัติ (1, 2, 3...)
- **MarkerToolbar:**
  - เปลี่ยนสีจุดมาร์ก (เขียวมิ้นต์, ส้มพีช, ฟ้าลาเวนเดอร์, แดงคอรัล, เหลืองอำพัน)
  - ปุ่มย้อนกลับ (Undo) และล้างจุดทั้งหมด (Clear)
  - แสดงยอดนับรวมปัจจุบัน
  - ปุ่ม **"บันทึกยอดนับลงสต็อก"**: เลือกว่าจะบันทึกทับยอดเดิมหรือบวกเพิ่มเข้าสต็อก พร้อมแนบ `idempotency_key`

### 9. ระบบซิงค์ออฟไลน์และหน้าผู้ดูแล (Offline Outbox, Admin & Diagnostics)
- แถบแสดงสถานะ **Online / Offline** แบบเรียลไทม์
- **Offline Outbox Queue:** เมื่อออฟไลน์ รายการขาย ปรับสต็อก และยกเลิกบิลจะถูกเก็บเข้าคิวในเครื่อง และซิงค์อัตโนมัติเมื่อกลับมาออนไลน์
- **Sync Diagnostics:** ดูรายการในคิว Outbox, สั่ง Force Sync, ล้างรายการที่สำเร็จ
- **Product Admin:** เพิ่ม/แก้ไขสินค้า, สลับเปิด/ปิดการขาย (`is_active`), แก้ไขราคา ทุน บาร์โค้ด
- **User Admin:** จัดการผู้ใช้ในระบบ, สลับสถานะ `is_active` ของผู้ใช้เพื่อบังคับ Profile Active Guard
- **App Version & Update Checker:** ตรวจสอบเวอร์ชันล่าสุดของแอปผ่าน GitHub Releases API และกดดาวน์โหลดไฟล์ APK ได้ทันที

---

## 🤖 CI/CD Build และ GitHub Actions
ไฟล์ `.github/workflows/android-apk.yml` ตั้งค่าระบบอัตโนมัติ:
1. ทำงานทุกครั้งที่มีการ `push` ขึ้น branch `main` หรือ push แท็ก `v*`
2. ตั้งค่า JDK 17 (Temurin) และ Gradle 8.7
3. สั่งคอมไพล์ด้วยคำสั่ง `gradle assembleDebug`
4. อัปโหลดไฟล์ APK ขึ้น **GitHub Actions Artifacts**
5. สร้างหรืออัปเดต **GitHub Releases** พร้อมแนบไฟล์ `OrderApp-debug.apk` อัตโนมัติ

---

## 🔑 การตั้งค่า Environment & Supabase Keys
สามารถกำหนดค่า `SUPABASE_URL` และ `SUPABASE_ANON_KEY` ผ่าน:
1. **GitHub Secrets:** กำหนด `SUPABASE_URL` และ `SUPABASE_ANON_KEY` ใน Settings > Secrets and variables > Actions ของ GitHub Repository
2. **Local Properties / Environment Variables:** ในเครื่องพัฒนา สามารถตั้งค่าใน `local.properties` หรือผ่านตัวแปรสภาพแวดล้อมระบบ
