package com.sitticun.orderapp.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sitticun.orderapp.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OrderDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE outbox (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action_type TEXT NOT NULL,
                idempotency_key TEXT NOT NULL UNIQUE,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                status TEXT NOT NULL,
                retry_count INTEGER DEFAULT 0,
                last_error TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE held_bills (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                customer_json TEXT,
                items_json TEXT NOT NULL,
                redeemed_points INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE cached_products (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                sku TEXT,
                barcode TEXT,
                category TEXT,
                price REAL NOT NULL,
                cost REAL DEFAULT 0,
                stock_qty INTEGER NOT NULL,
                min_stock_alert INTEGER DEFAULT 5,
                image_url TEXT,
                is_active INTEGER DEFAULT 1,
                created_at TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE cached_customers (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                points_balance INTEGER DEFAULT 0,
                notes TEXT,
                created_at TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE cached_orders (
                id TEXT PRIMARY KEY,
                order_number TEXT NOT NULL,
                customer_name TEXT,
                total_amount REAL NOT NULL,
                payment_method TEXT,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                json_data TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE stock_snapshots (
                id TEXT PRIMARY KEY,
                product_id TEXT NOT NULL,
                product_name TEXT NOT NULL,
                previous_qty INTEGER NOT NULL,
                new_qty INTEGER NOT NULL,
                delta_qty INTEGER NOT NULL,
                reason TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        """.trimIndent())

        // Insert initial seed products if table is empty
        seedInitialData(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val initialProducts = listOf(
            Product("p-001", "กาแฟเอสเพรสโซ่เย็น", "ESP-01", "8850010001", "เครื่องดื่ม", 55.0, 20.0, 48, 10),
            Product("p-002", "ชาไทยพรีเมียมเย็น", "THAI-01", "8850010002", "เครื่องดื่ม", 50.0, 18.0, 35, 10),
            Product("p-003", "มัทฉะลาเต้เย็น", "MAT-01", "8850010003", "เครื่องดื่ม", 65.0, 25.0, 22, 5),
            Product("p-004", "ครัวซองต์เนยสดฝรั่งเศส", "BAKE-01", "8850010004", "เบเกอรี่", 45.0, 22.0, 4, 8),
            Product("p-005", "เค้กส้มหน้านิ่ม", "BAKE-02", "8850010005", "เบเกอรี่", 75.0, 35.0, 8, 5),
            Product("p-006", "แซนด์วิชแฮมชีส", "FOOD-01", "8850010006", "อาหาร", 60.0, 28.0, 12, 5),
            Product("p-007", "สลัดอกไก่คลีน", "FOOD-02", "8850010007", "อาหาร", 89.0, 45.0, 2, 5),
            Product("p-008", "น้ำดื่มแร่ธรรมชาติ 500ml", "WATER-01", "8850010008", "เครื่องดื่ม", 15.0, 6.0, 120, 20),
            Product("p-009", "คุกกี้ดับเบิ้ลช็อกโกแลต", "SNACK-01", "8850010009", "ของทานเล่น", 35.0, 15.0, 0, 5),
            Product("p-010", "กระเป๋าผ้า POS Eco Bag (ของแถม)", "GIFT-01", "8850010010", "ของแถม", 0.0, 0.0, 50, 10)
        )

        for (p in initialProducts) {
            val cv = ContentValues().apply {
                put("id", p.id)
                put("name", p.name)
                put("sku", p.sku)
                put("barcode", p.barcode)
                put("category", p.category)
                put("price", p.price)
                put("cost", p.cost)
                put("stock_qty", p.stockQty)
                put("min_stock_alert", p.minStockAlert)
                put("is_active", if (p.isActive) 1 else 0)
                put("created_at", "2026-10-14T00:00:00Z")
            }
            db.insert("cached_products", null, cv)
        }

        val initialCustomers = listOf(
            Customer("c-001", "คุณสมชาย ใจดี", "081-234-5678", "somchai@email.com", 250, "ลูกค้าประจำ สั่งหวานน้อย"),
            Customer("c-002", "คุณวิภาวรรณ สดใส", "089-876-5432", "wipawan@email.com", 580, "สะสมแต้มแลกเครื่องดื่ม"),
            Customer("c-003", "คุณอนุรักษ์ เก่งการ", "090-555-1212", null, 40, "ชอบทานครัวซองต์")
        )

        for (c in initialCustomers) {
            val cv = ContentValues().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("email", c.email)
                put("points_balance", c.pointsBalance)
                put("notes", c.notes)
                put("created_at", "2026-10-14T00:00:00Z")
            }
            db.insert("cached_customers", null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS outbox")
        db.execSQL("DROP TABLE IF EXISTS held_bills")
        db.execSQL("DROP TABLE IF EXISTS cached_products")
        db.execSQL("DROP TABLE IF EXISTS cached_customers")
        db.execSQL("DROP TABLE IF EXISTS cached_orders")
        db.execSQL("DROP TABLE IF EXISTS stock_snapshots")
        onCreate(db)
    }

    // --- OUTBOX OPERATIONS ---
    fun insertOutboxItem(actionType: String, idempotencyKey: String, payloadJson: String): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("action_type", actionType)
            put("idempotency_key", idempotencyKey)
            put("payload_json", payloadJson)
            put("created_at", System.currentTimeMillis())
            put("status", OutboxStatus.PENDING)
            put("retry_count", 0)
        }
        return db.insertWithOnConflict("outbox", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getPendingOutboxItems(): List<OutboxItem> {
        val list = mutableListOf<OutboxItem>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            "outbox",
            null,
            "status = ? OR status = ?",
            arrayOf(OutboxStatus.PENDING, OutboxStatus.FAILED),
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OutboxItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        actionType = it.getString(it.getColumnIndexOrThrow("action_type")),
                        idempotencyKey = it.getString(it.getColumnIndexOrThrow("idempotency_key")),
                        payloadJson = it.getString(it.getColumnIndexOrThrow("payload_json")),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                        status = it.getString(it.getColumnIndexOrThrow("status")),
                        retryCount = it.getInt(it.getColumnIndexOrThrow("retry_count")),
                        lastError = it.getString(it.getColumnIndexOrThrow("last_error"))
                    )
                )
            }
        }
        return list
    }

    fun getAllOutboxItems(): List<OutboxItem> {
        val list = mutableListOf<OutboxItem>()
        val db = readableDatabase
        val cursor = db.query("outbox", null, null, null, null, null, "id DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OutboxItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        actionType = it.getString(it.getColumnIndexOrThrow("action_type")),
                        idempotencyKey = it.getString(it.getColumnIndexOrThrow("idempotency_key")),
                        payloadJson = it.getString(it.getColumnIndexOrThrow("payload_json")),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                        status = it.getString(it.getColumnIndexOrThrow("status")),
                        retryCount = it.getInt(it.getColumnIndexOrThrow("retry_count")),
                        lastError = it.getString(it.getColumnIndexOrThrow("last_error"))
                    )
                )
            }
        }
        return list
    }

    fun updateOutboxStatus(id: Long, status: String, error: String? = null) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("status", status)
            if (error != null) {
                put("last_error", error)
                // increment retry count
                val currentRetries = getOutboxRetryCount(id)
                put("retry_count", currentRetries + 1)
            }
        }
        db.update("outbox", cv, "id = ?", arrayOf(id.toString()))
    }

    private fun getOutboxRetryCount(id: Long): Int {
        val db = readableDatabase
        val c = db.rawQuery("SELECT retry_count FROM outbox WHERE id = ?", arrayOf(id.toString()))
        c.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun clearSyncedOutboxItems() {
        val db = writableDatabase
        db.delete("outbox", "status = ?", arrayOf(OutboxStatus.SYNCED))
    }

    fun clearAllOutboxItems() {
        val db = writableDatabase
        db.delete("outbox", null, null)
    }

    // --- HELD BILLS OPERATIONS ---
    fun saveHeldBill(bill: HeldBill) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", bill.id)
            put("title", bill.title)
            put("customer_json", bill.customer?.let { json.encodeToString(it) })
            put("items_json", json.encodeToString(bill.items))
            put("redeemed_points", bill.redeemedPoints)
            put("created_at", bill.createdAt)
        }
        db.insertWithOnConflict("held_bills", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllHeldBills(): List<HeldBill> {
        val list = mutableListOf<HeldBill>()
        val db = readableDatabase
        val c = db.query("held_bills", null, null, null, null, null, "created_at DESC")
        c.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val custJson = it.getString(it.getColumnIndexOrThrow("customer_json"))
                val itemsJson = it.getString(it.getColumnIndexOrThrow("items_json"))
                val points = it.getInt(it.getColumnIndexOrThrow("redeemed_points"))
                val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))

                val customer = custJson?.let { str ->
                    try { json.decodeFromString<Customer>(str) } catch (e: Exception) { null }
                }
                val items = try {
                    json.decodeFromString<List<CartItem>>(itemsJson)
                } catch (e: Exception) {
                    emptyList()
                }

                list.add(HeldBill(id, title, customer, items, points, createdAt))
            }
        }
        return list
    }

    fun deleteHeldBill(id: String) {
        val db = writableDatabase
        db.delete("held_bills", "id = ?", arrayOf(id))
    }

    // --- CACHED PRODUCTS OPERATIONS ---
    fun saveProducts(products: List<Product>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (p in products) {
                val cv = ContentValues().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("sku", p.sku)
                    put("barcode", p.barcode)
                    put("category", p.category)
                    put("price", p.price)
                    put("cost", p.cost)
                    put("stock_qty", p.stockQty)
                    put("min_stock_alert", p.minStockAlert)
                    put("image_url", p.imageUrl)
                    put("is_active", if (p.isActive) 1 else 0)
                    put("created_at", p.createdAt)
                }
                db.insertWithOnConflict("cached_products", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllProducts(): List<Product> {
        val list = mutableListOf<Product>()
        val db = readableDatabase
        val c = db.query("cached_products", null, null, null, null, null, "name ASC")
        c.use {
            while (it.moveToNext()) {
                list.add(
                    Product(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        sku = it.getString(it.getColumnIndexOrThrow("sku")) ?: "",
                        barcode = it.getString(it.getColumnIndexOrThrow("barcode")) ?: "",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "ทั่วไป",
                        price = it.getDouble(it.getColumnIndexOrThrow("price")),
                        cost = it.getDouble(it.getColumnIndexOrThrow("cost")),
                        stockQty = it.getInt(it.getColumnIndexOrThrow("stock_qty")),
                        minStockAlert = it.getInt(it.getColumnIndexOrThrow("min_stock_alert")),
                        imageUrl = it.getString(it.getColumnIndexOrThrow("image_url")),
                        isActive = it.getInt(it.getColumnIndexOrThrow("is_active")) == 1,
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    fun updateProductStockLocal(productId: String, delta: Int) {
        val db = writableDatabase
        db.execSQL("UPDATE cached_products SET stock_qty = MAX(0, stock_qty + ?) WHERE id = ?", arrayOf(delta, productId))
    }

    fun setProductStockDirect(productId: String, newQty: Int) {
        val db = writableDatabase
        db.execSQL("UPDATE cached_products SET stock_qty = ? WHERE id = ?", arrayOf(newQty, productId))
    }

    // --- CACHED CUSTOMERS OPERATIONS ---
    fun saveCustomers(customers: List<Customer>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (c in customers) {
                val cv = ContentValues().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("email", c.email)
                    put("points_balance", c.pointsBalance)
                    put("notes", c.notes)
                    put("created_at", c.createdAt)
                }
                db.insertWithOnConflict("cached_customers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllCustomers(): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = readableDatabase
        val c = db.query("cached_customers", null, null, null, null, null, "name ASC")
        c.use {
            while (it.moveToNext()) {
                list.add(
                    Customer(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        phone = it.getString(it.getColumnIndexOrThrow("phone")) ?: "",
                        email = it.getString(it.getColumnIndexOrThrow("email")),
                        pointsBalance = it.getInt(it.getColumnIndexOrThrow("points_balance")),
                        notes = it.getString(it.getColumnIndexOrThrow("notes")),
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    fun updateCustomerPointsLocal(customerId: String, deltaPoints: Int) {
        val db = writableDatabase
        db.execSQL("UPDATE cached_customers SET points_balance = MAX(0, points_balance + ?) WHERE id = ?", arrayOf(deltaPoints, customerId))
    }

    // --- CACHED ORDERS OPERATIONS ---
    fun saveOrder(order: OrderRecord) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", order.id)
            put("order_number", order.orderNumber)
            put("customer_name", order.customerName)
            put("total_amount", order.totalAmount)
            put("payment_method", order.paymentMethod)
            put("status", order.status)
            put("created_at", order.createdAt)
            put("json_data", json.encodeToString(order))
        }
        db.insertWithOnConflict("cached_orders", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllOrders(): List<OrderRecord> {
        val list = mutableListOf<OrderRecord>()
        val db = readableDatabase
        val c = db.query("cached_orders", null, null, null, null, null, "created_at DESC")
        c.use {
            while (it.moveToNext()) {
                val raw = it.getString(it.getColumnIndexOrThrow("json_data"))
                try {
                    list.add(json.decodeFromString<OrderRecord>(raw))
                } catch (e: Exception) {
                    // Fallback to row fields if JSON decoding fails
                }
            }
        }
        return list
    }

    fun updateOrderStatus(orderId: String, status: String, voidReason: String?, voidedAt: String?) {
        val db = writableDatabase
        // Update row status
        val cv = ContentValues().apply {
            put("status", status)
        }
        db.update("cached_orders", cv, "id = ?", arrayOf(orderId))

        // Also update JSON payload
        val c = db.query("cached_orders", arrayOf("json_data"), "id = ?", arrayOf(orderId), null, null, null)
        c.use {
            if (it.moveToFirst()) {
                val raw = it.getString(0)
                try {
                    val order = json.decodeFromString<OrderRecord>(raw)
                    val updatedOrder = order.copy(
                        status = status,
                        voidReason = voidReason,
                        voidedAt = voidedAt
                    )
                    val updateCv = ContentValues().apply {
                        put("json_data", json.encodeToString(updatedOrder))
                    }
                    db.update("cached_orders", updateCv, "id = ?", arrayOf(orderId))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // --- STOCK SNAPSHOTS ---
    fun insertStockSnapshot(snapshot: StockSnapshot) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", snapshot.id)
            put("product_id", snapshot.productId)
            put("product_name", snapshot.productName)
            put("previous_qty", snapshot.previousQty)
            put("new_qty", snapshot.newQty)
            put("delta_qty", snapshot.deltaQty)
            put("reason", snapshot.reason)
            put("created_at", snapshot.createdAt)
        }
        db.insertWithOnConflict("stock_snapshots", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getStockSnapshots(): List<StockSnapshot> {
        val list = mutableListOf<StockSnapshot>()
        val db = readableDatabase
        val c = db.query("stock_snapshots", null, null, null, null, null, "created_at DESC")
        c.use {
            while (it.moveToNext()) {
                list.add(
                    StockSnapshot(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        productId = it.getString(it.getColumnIndexOrThrow("product_id")),
                        productName = it.getString(it.getColumnIndexOrThrow("product_name")),
                        previousQty = it.getInt(it.getColumnIndexOrThrow("previous_qty")),
                        newQty = it.getInt(it.getColumnIndexOrThrow("new_qty")),
                        deltaQty = it.getInt(it.getColumnIndexOrThrow("delta_qty")),
                        reason = it.getString(it.getColumnIndexOrThrow("reason")),
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    companion object {
        const val DATABASE_NAME = "orderapp.db"
        const val DATABASE_VERSION = 1
    }
}
