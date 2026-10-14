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
            CREATE TABLE cached_products (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                tag TEXT,
                volume TEXT,
                price REAL NOT NULL,
                is_active INTEGER DEFAULT 1,
                created_at TEXT,
                updated_at TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE cached_customers (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                created_at TEXT,
                updated_at TEXT,
                deleted_at TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE point_balances (
                customer_id TEXT NOT NULL,
                product_id INTEGER NOT NULL,
                balance INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (customer_id, product_id)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE point_ledger (
                id TEXT PRIMARY KEY,
                idempotency_key TEXT,
                customer_id TEXT NOT NULL,
                product_id INTEGER NOT NULL,
                delta INTEGER NOT NULL,
                reason TEXT NOT NULL,
                order_id TEXT,
                created_at TEXT,
                created_by TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE cached_orders (
                id TEXT PRIMARY KEY,
                idempotency_key TEXT,
                customer_id TEXT NOT NULL,
                customer_name TEXT NOT NULL,
                created_at TEXT NOT NULL,
                total_qty INTEGER NOT NULL,
                total REAL NOT NULL,
                status TEXT NOT NULL,
                source_device_id TEXT,
                created_by TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE order_items (
                id TEXT PRIMARY KEY,
                order_id TEXT NOT NULL,
                product_id INTEGER NOT NULL,
                name_snapshot TEXT NOT NULL,
                volume_snapshot TEXT,
                unit_price REAL NOT NULL,
                qty INTEGER NOT NULL,
                subtotal REAL NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE order_free_items (
                id TEXT PRIMARY KEY,
                order_id TEXT NOT NULL,
                product_id INTEGER NOT NULL,
                qty INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE order_point_uses (
                id TEXT PRIMARY KEY,
                order_id TEXT NOT NULL,
                product_id INTEGER NOT NULL,
                points INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE held_bills (
                id TEXT PRIMARY KEY,
                customer_id TEXT NOT NULL,
                customer_name TEXT NOT NULL,
                note TEXT,
                payload_json TEXT NOT NULL,
                updated_at TEXT,
                updated_by TEXT,
                version INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE stock_snapshots (
                id TEXT PRIMARY KEY,
                captured_at TEXT NOT NULL,
                note TEXT,
                source_device_id TEXT,
                created_by TEXT,
                idempotency_key TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE stock_snapshot_items (
                id TEXT PRIMARY KEY,
                snapshot_id TEXT NOT NULL,
                product_id INTEGER NOT NULL,
                qty INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE count_sessions (
                id TEXT PRIMARY KEY,
                created_at TEXT NOT NULL,
                created_by TEXT,
                source_device_id TEXT,
                image_path TEXT,
                image_width INTEGER,
                image_height INTEGER,
                status TEXT NOT NULL,
                note TEXT,
                version INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE count_markers (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                product_id INTEGER,
                x_normalized REAL NOT NULL,
                y_normalized REAL NOT NULL,
                created_at TEXT,
                deleted_at TEXT
            )
        """.trimIndent())

        // Insert default initial products from OrderApp
        seedInitialProducts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS outbox")
        db.execSQL("DROP TABLE IF EXISTS held_bills")
        db.execSQL("DROP TABLE IF EXISTS cached_products")
        db.execSQL("DROP TABLE IF EXISTS cached_customers")
        db.execSQL("DROP TABLE IF EXISTS point_balances")
        db.execSQL("DROP TABLE IF EXISTS point_ledger")
        db.execSQL("DROP TABLE IF EXISTS cached_orders")
        db.execSQL("DROP TABLE IF EXISTS order_items")
        db.execSQL("DROP TABLE IF EXISTS order_free_items")
        db.execSQL("DROP TABLE IF EXISTS order_point_uses")
        db.execSQL("DROP TABLE IF EXISTS stock_snapshots")
        db.execSQL("DROP TABLE IF EXISTS stock_snapshot_items")
        db.execSQL("DROP TABLE IF EXISTS count_sessions")
        db.execSQL("DROP TABLE IF EXISTS count_markers")
        onCreate(db)
    }

    private fun seedInitialProducts(db: SQLiteDatabase) {
        val initialProducts = listOf(
            Product(1L, "ผสมขวด 75 1500ml", "Herbal Drink", "1500 ml", 75.0, true),
            Product(2L, "ผสมขวด 50 1000 ml", "Herbal Drink", "1000 ml", 50.0, true),
            Product(3L, "น้ำดิป 35 1500ml", "Herbal Drink", "1500 ml", 35.0, true),
            Product(4L, "ยา", "Herbal Drink", "60 ml", 55.0, true)
        )

        for (p in initialProducts) {
            val cv = ContentValues().apply {
                put("id", p.id)
                put("name", p.name)
                put("tag", p.tag)
                put("volume", p.volume)
                put("price", p.price)
                put("is_active", if (p.isActive) 1 else 0)
            }
            db.insertWithOnConflict("cached_products", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // -------------------------------------------------------------
    // PRODUCTS
    // -------------------------------------------------------------
    fun getAllProducts(): List<Product> {
        val list = mutableListOf<Product>()
        val db = readableDatabase
        val cursor = db.query(
            "cached_products",
            null,
            "is_active = 1",
            null,
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToProduct(it))
            }
        }
        return list
    }

    fun getAllProductsAdmin(): List<Product> {
        val list = mutableListOf<Product>()
        val db = readableDatabase
        val cursor = db.query(
            "cached_products",
            null,
            null,
            null,
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToProduct(it))
            }
        }
        return list
    }

    fun saveProducts(products: List<Product>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (p in products) {
                val cv = ContentValues().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("tag", p.tag)
                    put("volume", p.volume)
                    put("price", p.price)
                    put("is_active", if (p.isActive) 1 else 0)
                    put("created_at", p.createdAt)
                    put("updated_at", p.updatedAt)
                }
                db.insertWithOnConflict("cached_products", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun cursorToProduct(c: Cursor): Product {
        return Product(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            tag = c.getString(c.getColumnIndexOrThrow("tag")),
            volume = c.getString(c.getColumnIndexOrThrow("volume")),
            price = c.getDouble(c.getColumnIndexOrThrow("price")),
            isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1,
            createdAt = c.getString(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getString(c.getColumnIndexOrThrow("updated_at"))
        )
    }

    // -------------------------------------------------------------
    // CUSTOMERS
    // -------------------------------------------------------------
    fun getAllCustomers(): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = readableDatabase
        val cursor = db.query(
            "cached_customers",
            null,
            "deleted_at IS NULL",
            null,
            null,
            null,
            "name ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToCustomer(it))
            }
        }
        return list
    }

    fun saveCustomers(customers: List<Customer>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (c in customers) {
                val cv = ContentValues().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("normalized_name", c.normalizedName)
                    put("created_at", c.createdAt)
                    put("updated_at", c.updatedAt)
                    put("deleted_at", c.deletedAt)
                }
                db.insertWithOnConflict("cached_customers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveCustomer(c: Customer) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", c.id)
            put("name", c.name)
            put("normalized_name", c.normalizedName)
            put("created_at", c.createdAt)
            put("updated_at", c.updatedAt)
            put("deleted_at", c.deletedAt)
        }
        db.insertWithOnConflict("cached_customers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun cursorToCustomer(c: Cursor): Customer {
        return Customer(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            normalizedName = c.getString(c.getColumnIndexOrThrow("normalized_name")),
            createdAt = c.getString(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getString(c.getColumnIndexOrThrow("updated_at")),
            deletedAt = c.getString(c.getColumnIndexOrThrow("deleted_at"))
        )
    }

    // -------------------------------------------------------------
    // POINT BALANCES
    // -------------------------------------------------------------
    fun getPointBalancesForCustomer(customerId: String): List<PointBalance> {
        val list = mutableListOf<PointBalance>()
        val db = readableDatabase
        val cursor = db.query(
            "point_balances",
            null,
            "customer_id = ?",
            arrayOf(customerId),
            null,
            null,
            "product_id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PointBalance(
                        customerId = it.getString(it.getColumnIndexOrThrow("customer_id")),
                        productId = it.getLong(it.getColumnIndexOrThrow("product_id")),
                        balance = it.getLong(it.getColumnIndexOrThrow("balance"))
                    )
                )
            }
        }
        return list
    }

    fun savePointBalances(customerId: String, balances: List<PointBalance>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("point_balances", "customer_id = ?", arrayOf(customerId))
            for (b in balances) {
                val cv = ContentValues().apply {
                    put("customer_id", b.customerId)
                    put("product_id", b.productId)
                    put("balance", b.balance)
                }
                db.insertWithOnConflict("point_balances", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updatePointBalance(customerId: String, productId: Long, delta: Int) {
        val db = writableDatabase
        val cursor = db.query(
            "point_balances",
            arrayOf("balance"),
            "customer_id = ? AND product_id = ?",
            arrayOf(customerId, productId.toString()),
            null,
            null,
            null
        )
        val currentBalance = cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        val newBalance = (currentBalance + delta).coerceAtLeast(0L)
        val cv = ContentValues().apply {
            put("customer_id", customerId)
            put("product_id", productId)
            put("balance", newBalance)
        }
        db.insertWithOnConflict("point_balances", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // -------------------------------------------------------------
    // ORDERS
    // -------------------------------------------------------------
    fun getAllOrders(): List<OrderSummary> {
        val list = mutableListOf<OrderSummary>()
        val db = readableDatabase
        val cursor = db.query(
            "cached_orders",
            null,
            null,
            null,
            null,
            null,
            "created_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val orderId = it.getString(it.getColumnIndexOrThrow("id"))
                val items = getOrderItems(orderId)
                val freeItems = getOrderFreeItems(orderId)
                val pointUses = getOrderPointUses(orderId)
                list.add(
                    OrderSummary(
                        id = orderId,
                        customerId = it.getString(it.getColumnIndexOrThrow("customer_id")),
                        customerName = it.getString(it.getColumnIndexOrThrow("customer_name")),
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")),
                        totalQty = it.getInt(it.getColumnIndexOrThrow("total_qty")),
                        total = it.getDouble(it.getColumnIndexOrThrow("total")),
                        status = it.getString(it.getColumnIndexOrThrow("status")),
                        createdBy = it.getString(it.getColumnIndexOrThrow("created_by")),
                        items = items,
                        freeItems = freeItems,
                        pointUses = pointUses
                    )
                )
            }
        }
        return list
    }

    fun saveOrders(orders: List<OrderSummary>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (o in orders) {
                saveOrderInternal(db, o)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveOrder(o: OrderSummary) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            saveOrderInternal(db, o)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun saveOrderInternal(db: SQLiteDatabase, o: OrderSummary) {
        val cv = ContentValues().apply {
            put("id", o.id)
            put("customer_id", o.customerId)
            put("customer_name", o.customerName)
            put("created_at", o.createdAt)
            put("total_qty", o.totalQty)
            put("total", o.total)
            put("status", o.status)
            put("created_by", o.createdBy)
        }
        db.insertWithOnConflict("cached_orders", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        db.delete("order_items", "order_id = ?", arrayOf(o.id))
        for (item in o.items) {
            val icv = ContentValues().apply {
                put("id", item.id.ifBlank { "${o.id}:item:${item.productId}" })
                put("order_id", o.id)
                put("product_id", item.productId)
                put("name_snapshot", item.nameSnapshot)
                put("volume_snapshot", item.volumeSnapshot)
                put("unit_price", item.unitPrice)
                put("qty", item.qty)
                put("subtotal", item.subtotal)
            }
            db.insertWithOnConflict("order_items", null, icv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        db.delete("order_free_items", "order_id = ?", arrayOf(o.id))
        for (free in o.freeItems) {
            val fcv = ContentValues().apply {
                put("id", free.id.ifBlank { "${o.id}:free:${free.productId}" })
                put("order_id", o.id)
                put("product_id", free.productId)
                put("qty", free.qty)
            }
            db.insertWithOnConflict("order_free_items", null, fcv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        db.delete("order_point_uses", "order_id = ?", arrayOf(o.id))
        for (u in o.pointUses) {
            val ucv = ContentValues().apply {
                put("id", u.id.ifBlank { "${o.id}:use:${u.productId}" })
                put("order_id", o.id)
                put("product_id", u.productId)
                put("points", u.points)
            }
            db.insertWithOnConflict("order_point_uses", null, ucv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun markOrderVoided(orderId: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("status", "voided")
        }
        db.update("cached_orders", cv, "id = ?", arrayOf(orderId))
    }

    private fun getOrderItems(orderId: String): List<OrderItemRecord> {
        val list = mutableListOf<OrderItemRecord>()
        val cursor = readableDatabase.query(
            "order_items",
            null,
            "order_id = ?",
            arrayOf(orderId),
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OrderItemRecord(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        orderId = it.getString(it.getColumnIndexOrThrow("order_id")),
                        productId = it.getLong(it.getColumnIndexOrThrow("product_id")),
                        nameSnapshot = it.getString(it.getColumnIndexOrThrow("name_snapshot")),
                        volumeSnapshot = it.getString(it.getColumnIndexOrThrow("volume_snapshot")),
                        unitPrice = it.getDouble(it.getColumnIndexOrThrow("unit_price")),
                        qty = it.getInt(it.getColumnIndexOrThrow("qty")),
                        subtotal = it.getDouble(it.getColumnIndexOrThrow("subtotal"))
                    )
                )
            }
        }
        return list
    }

    private fun getOrderFreeItems(orderId: String): List<OrderFreeItemRecord> {
        val list = mutableListOf<OrderFreeItemRecord>()
        val cursor = readableDatabase.query(
            "order_free_items",
            null,
            "order_id = ?",
            arrayOf(orderId),
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OrderFreeItemRecord(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        orderId = it.getString(it.getColumnIndexOrThrow("order_id")),
                        productId = it.getLong(it.getColumnIndexOrThrow("product_id")),
                        qty = it.getInt(it.getColumnIndexOrThrow("qty"))
                    )
                )
            }
        }
        return list
    }

    private fun getOrderPointUses(orderId: String): List<OrderPointUseRecord> {
        val list = mutableListOf<OrderPointUseRecord>()
        val cursor = readableDatabase.query(
            "order_point_uses",
            null,
            "order_id = ?",
            arrayOf(orderId),
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OrderPointUseRecord(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        orderId = it.getString(it.getColumnIndexOrThrow("order_id")),
                        productId = it.getLong(it.getColumnIndexOrThrow("product_id")),
                        points = it.getInt(it.getColumnIndexOrThrow("points"))
                    )
                )
            }
        }
        return list
    }

    // Calculate total sold (paid + free) for each product since a given timestamp
    fun getProductTotalSoldSince(sinceIsoTimestamp: String?): Map<Long, Int> {
        val map = mutableMapOf<Long, Int>()
        val db = readableDatabase

        val timeClause = if (!sinceIsoTimestamp.isNullOrBlank()) "AND o.created_at > ?" else ""
        val args = if (!sinceIsoTimestamp.isNullOrBlank()) arrayOf(sinceIsoTimestamp) else emptyArray()

        // Sum paid items for non-voided orders
        val paidQuery = """
            SELECT oi.product_id, SUM(oi.qty) as total_qty
            FROM order_items oi
            JOIN cached_orders o ON oi.order_id = o.id
            WHERE o.status != 'voided' $timeClause
            GROUP BY oi.product_id
        """.trimIndent()

        db.rawQuery(paidQuery, args).use { cursor ->
            while (cursor.moveToNext()) {
                val pId = cursor.getLong(0)
                val qty = cursor.getInt(1)
                map[pId] = (map[pId] ?: 0) + qty
            }
        }

        // Sum free items for non-voided orders
        val freeQuery = """
            SELECT fi.product_id, SUM(fi.qty) as total_qty
            FROM order_free_items fi
            JOIN cached_orders o ON fi.order_id = o.id
            WHERE o.status != 'voided' $timeClause
            GROUP BY fi.product_id
        """.trimIndent()

        db.rawQuery(freeQuery, args).use { cursor ->
            while (cursor.moveToNext()) {
                val pId = cursor.getLong(0)
                val qty = cursor.getInt(1)
                map[pId] = (map[pId] ?: 0) + qty
            }
        }

        return map
    }

    // -------------------------------------------------------------
    // HELD BILLS
    // -------------------------------------------------------------
    fun getAllHeldBills(): List<HeldBill> {
        val list = mutableListOf<HeldBill>()
        val db = readableDatabase
        val cursor = db.query(
            "held_bills",
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC, version DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val payloadJson = it.getString(it.getColumnIndexOrThrow("payload_json"))
                val payload = try {
                    json.decodeFromString<HeldBillPayload>(payloadJson)
                } catch (e: Exception) {
                    HeldBillPayload()
                }
                list.add(
                    HeldBill(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        customerId = it.getString(it.getColumnIndexOrThrow("customer_id")),
                        customerName = it.getString(it.getColumnIndexOrThrow("customer_name")),
                        note = it.getString(it.getColumnIndexOrThrow("note")),
                        payload = payload,
                        updatedAt = it.getString(it.getColumnIndexOrThrow("updated_at")),
                        updatedBy = it.getString(it.getColumnIndexOrThrow("updated_by")),
                        version = it.getLong(it.getColumnIndexOrThrow("version"))
                    )
                )
            }
        }
        return list
    }

    fun saveHeldBill(bill: HeldBill) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", bill.id)
            put("customer_id", bill.customerId)
            put("customer_name", bill.customerName)
            put("note", bill.note)
            put("payload_json", json.encodeToString(bill.payload))
            put("updated_at", bill.updatedAt)
            put("updated_by", bill.updatedBy)
            put("version", bill.version)
        }
        db.insertWithOnConflict("held_bills", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveHeldBills(bills: List<HeldBill>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("held_bills", null, null)
            for (b in bills) {
                val cv = ContentValues().apply {
                    put("id", b.id)
                    put("customer_id", b.customerId)
                    put("customer_name", b.customerName)
                    put("note", b.note)
                    put("payload_json", json.encodeToString(b.payload))
                    put("updated_at", b.updatedAt)
                    put("updated_by", b.updatedBy)
                    put("version", b.version)
                }
                db.insertWithOnConflict("held_bills", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteHeldBill(id: String) {
        writableDatabase.delete("held_bills", "id = ?", arrayOf(id))
    }

    // -------------------------------------------------------------
    // STOCK SNAPSHOTS
    // -------------------------------------------------------------
    fun getAllStockSnapshots(): List<StockSnapshot> {
        val list = mutableListOf<StockSnapshot>()
        val db = readableDatabase
        val cursor = db.query(
            "stock_snapshots",
            null,
            null,
            null,
            null,
            null,
            "captured_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val snapshotId = it.getString(it.getColumnIndexOrThrow("id"))
                val items = getStockSnapshotItems(snapshotId)
                list.add(
                    StockSnapshot(
                        id = snapshotId,
                        capturedAt = it.getString(it.getColumnIndexOrThrow("captured_at")),
                        note = it.getString(it.getColumnIndexOrThrow("note")),
                        sourceDeviceId = it.getString(it.getColumnIndexOrThrow("source_device_id")),
                        createdBy = it.getString(it.getColumnIndexOrThrow("created_by")),
                        items = items
                    )
                )
            }
        }
        return list
    }

    fun getLatestStockSnapshot(): StockSnapshot? {
        val snapshots = getAllStockSnapshots()
        return snapshots.firstOrNull()
    }

    fun saveStockSnapshot(snap: StockSnapshot) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("id", snap.id)
                put("captured_at", snap.capturedAt)
                put("note", snap.note)
                put("source_device_id", snap.sourceDeviceId)
                put("created_by", snap.createdBy)
            }
            db.insertWithOnConflict("stock_snapshots", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            db.delete("stock_snapshot_items", "snapshot_id = ?", arrayOf(snap.id))
            for (item in snap.items) {
                val icv = ContentValues().apply {
                    put("id", item.id.ifBlank { "${snap.id}:item:${item.productId}" })
                    put("snapshot_id", snap.id)
                    put("product_id", item.productId)
                    put("qty", item.qty)
                }
                db.insertWithOnConflict("stock_snapshot_items", null, icv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveStockSnapshots(snapshots: List<StockSnapshot>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (snap in snapshots) {
                saveStockSnapshot(snap)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun getStockSnapshotItems(snapshotId: String): List<StockSnapshotItem> {
        val list = mutableListOf<StockSnapshotItem>()
        val cursor = readableDatabase.query(
            "stock_snapshot_items",
            null,
            "snapshot_id = ?",
            arrayOf(snapshotId),
            null,
            null,
            "product_id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    StockSnapshotItem(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        snapshotId = it.getString(it.getColumnIndexOrThrow("snapshot_id")),
                        productId = it.getLong(it.getColumnIndexOrThrow("product_id")),
                        qty = it.getInt(it.getColumnIndexOrThrow("qty"))
                    )
                )
            }
        }
        return list
    }

    // -------------------------------------------------------------
    // COUNT SESSIONS & MARKERS
    // -------------------------------------------------------------
    fun getAllCountSessions(): List<CountSession> {
        val list = mutableListOf<CountSession>()
        val cursor = readableDatabase.query(
            "count_sessions",
            null,
            null,
            null,
            null,
            null,
            "created_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val sessionId = it.getString(it.getColumnIndexOrThrow("id"))
                val markers = getCountMarkersForSession(sessionId)
                list.add(
                    CountSession(
                        id = sessionId,
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")),
                        createdBy = it.getString(it.getColumnIndexOrThrow("created_by")),
                        sourceDeviceId = it.getString(it.getColumnIndexOrThrow("source_device_id")),
                        imagePath = it.getString(it.getColumnIndexOrThrow("image_path")),
                        imageWidth = if (it.isNull(it.getColumnIndexOrThrow("image_width"))) null else it.getInt(it.getColumnIndexOrThrow("image_width")),
                        imageHeight = if (it.isNull(it.getColumnIndexOrThrow("image_height"))) null else it.getInt(it.getColumnIndexOrThrow("image_height")),
                        status = it.getString(it.getColumnIndexOrThrow("status")),
                        note = it.getString(it.getColumnIndexOrThrow("note")),
                        version = it.getLong(it.getColumnIndexOrThrow("version")),
                        markers = markers
                    )
                )
            }
        }
        return list
    }

    fun saveCountSession(session: CountSession) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("id", session.id)
                put("created_at", session.createdAt ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))
                put("created_by", session.createdBy)
                put("source_device_id", session.sourceDeviceId)
                put("image_path", session.imagePath)
                put("image_width", session.imageWidth)
                put("image_height", session.imageHeight)
                put("status", session.status)
                put("note", session.note)
                put("version", session.version)
            }
            db.insertWithOnConflict("count_sessions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            db.delete("count_markers", "session_id = ?", arrayOf(session.id))
            for (m in session.markers) {
                val mcv = ContentValues().apply {
                    put("id", m.id)
                    put("session_id", session.id)
                    put("product_id", m.productId)
                    put("x_normalized", m.xNormalized)
                    put("y_normalized", m.yNormalized)
                    put("created_at", m.createdAt)
                    put("deleted_at", m.deletedAt)
                }
                db.insertWithOnConflict("count_markers", null, mcv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun getCountMarkersForSession(sessionId: String): List<CountMarker> {
        val list = mutableListOf<CountMarker>()
        val cursor = readableDatabase.query(
            "count_markers",
            null,
            "session_id = ? AND deleted_at IS NULL",
            arrayOf(sessionId),
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CountMarker(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        sessionId = it.getString(it.getColumnIndexOrThrow("session_id")),
                        productId = if (it.isNull(it.getColumnIndexOrThrow("product_id"))) null else it.getLong(it.getColumnIndexOrThrow("product_id")),
                        xNormalized = it.getDouble(it.getColumnIndexOrThrow("x_normalized")),
                        yNormalized = it.getDouble(it.getColumnIndexOrThrow("y_normalized")),
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")),
                        deletedAt = it.getString(it.getColumnIndexOrThrow("deleted_at"))
                    )
                )
            }
        }
        return list
    }

    // -------------------------------------------------------------
    // OUTBOX
    // -------------------------------------------------------------
    fun insertOutbox(actionType: String, idempotencyKey: String, payloadJson: String): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("action_type", actionType)
            put("idempotency_key", idempotencyKey)
            put("payload_json", payloadJson)
            put("created_at", System.currentTimeMillis())
            put("status", "PENDING")
            put("retry_count", 0)
        }
        return db.insertWithOnConflict("outbox", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getPendingOutbox(): List<OutboxTask> {
        val list = mutableListOf<OutboxTask>()
        val db = readableDatabase
        val cursor = db.query(
            "outbox",
            null,
            "status IN ('PENDING', 'FAILED')",
            null,
            null,
            null,
            "created_at ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OutboxTask(
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

    fun getAllOutbox(): List<OutboxTask> {
        val list = mutableListOf<OutboxTask>()
        val db = readableDatabase
        val cursor = db.query(
            "outbox",
            null,
            null,
            null,
            null,
            null,
            "created_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OutboxTask(
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
            }
        }
        db.update("outbox", cv, "id = ?", arrayOf(id.toString()))
    }

    fun incrementOutboxRetry(id: Long, error: String?) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE outbox SET retry_count = retry_count + 1, last_error = ?, status = 'FAILED' WHERE id = ?",
            arrayOf(error, id)
        )
    }

    fun deleteOutbox(id: Long) {
        writableDatabase.delete("outbox", "id = ?", arrayOf(id.toString()))
    }

    companion object {
        const val DATABASE_NAME = "order_app.db"
        const val DATABASE_VERSION = 2
    }
}

data class OutboxTask(
    val id: Long,
    val actionType: String,
    val idempotencyKey: String,
    val payloadJson: String,
    val createdAt: Long,
    val status: String,
    val retryCount: Int,
    val lastError: String?
)
