package com.sitticun.orderapp.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sitticun.orderapp.data.model.HeldBill
import com.sitticun.orderapp.data.model.HeldBillPayload
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrderDatabaseHelperTest {
    private lateinit var context: Context
    private lateinit var helper: OrderDatabaseHelper

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(OrderDatabaseHelper.DATABASE_NAME)
        helper = OrderDatabaseHelper(context)
    }

    @After fun cleanup() {
        helper.close()
        context.deleteDatabase(OrderDatabaseHelper.DATABASE_NAME)
    }

    private fun bill(id: String, qty: Int = 1) = HeldBill(
        id = id, customerId = "customer", customerName = "Customer",
        payload = HeldBillPayload(paidQty = mapOf("1" to qty))
    )

    @Test fun migrationKeepsOfflineBillsAndOutbox() {
        helper.saveHeldBill(bill("bill"))
        helper.insertOutbox("commit_sale", "sale:1", "{}")
        helper.onUpgrade(helper.writableDatabase, 2, OrderDatabaseHelper.DATABASE_VERSION)
        assertEquals("bill", helper.getAllHeldBills().single().id)
        assertEquals("sale:1", helper.getPendingOutbox().single().idempotencyKey)
    }

    @Test fun syncInProgressIsRecoveredAfterRestart() {
        val id = helper.insertOutbox("commit_sale", "sale:1", "{}")
        helper.updateOutboxStatus(id, "SYNCING")
        helper.close()
        helper = OrderDatabaseHelper(context)
        assertEquals(id, helper.getPendingOutbox().single().id)
    }

    @Test fun pendingBillIsNotOverwrittenByCloudRefresh() {
        helper.inTransaction { helper.saveHeldBill(bill("bill", 3)); helper.queueHeldBill("bill", "user") }
        helper.saveHeldBills(listOf(bill("bill", 8)))
        assertEquals(3, helper.getAllHeldBills().single().payload.paidQty["1"] ?: -1)
        assertEquals("user" to false, helper.pendingHeldBills()["bill"])
        helper.markHeldAttempted("bill")
        assertEquals("user" to true, helper.pendingHeldBills()["bill"])
    }

    @Test fun failedTransactionRollsBackBillAndQueue() {
        try {
            helper.inTransaction {
                helper.saveHeldBill(bill("bill"))
                helper.queueHeldBill("bill", "user")
                error("simulate failure")
            }
            fail("transaction should fail")
        } catch (_: IllegalStateException) { }
        assertTrue(helper.getAllHeldBills().isEmpty())
        assertTrue(helper.pendingHeldBills().isEmpty())
    }
    @Test fun noteQueueRetainsOwnerAndLegacyEntriesRemainUnassigned() {
        val db = android.database.sqlite.SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE pending_notes (note_id TEXT PRIMARY KEY, operation TEXT NOT NULL)")
            db.execSQL("INSERT INTO pending_notes VALUES ('old', 'delete')")
            db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY)")
            helper.onUpgrade(db, 6, 9)
            db.rawQuery("SELECT owner_id FROM pending_notes WHERE note_id = 'old'", null).use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
            }
        } finally { db.close() }
        helper.queueNote("new", "delete", "user-a", 0L)
        assertEquals("delete" to "user-a", helper.pendingNotes()["new"])
    }
    @Test fun noteQueueKeepsOriginalBaseAcrossEdits() {
        helper.queueNote("n", "upsert", "user", 1L)
        helper.queueNote("n", "delete", "user", 2L)
        assertEquals(1L, helper.pendingNoteBases()["n"] ?: -1L)
    }
    @Test fun upgradingVersionEightKeepsLegacyQueueUnverified() {
        val db = android.database.sqlite.SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY, title TEXT NOT NULL)")
            db.execSQL("INSERT INTO notes VALUES ('n', 'original')")
            db.execSQL("CREATE TABLE pending_notes (note_id TEXT PRIMARY KEY, operation TEXT NOT NULL, owner_id TEXT, base_updated_at TEXT)")
            db.execSQL("INSERT INTO pending_notes VALUES ('n', 'upsert', 'user', '2026-01-01T00:00:00Z')")
            helper.onUpgrade(db, 8, 9)
            db.rawQuery("SELECT title, revision FROM notes WHERE id = 'n'", null).use {
                assertTrue(it.moveToFirst()); assertEquals("original", it.getString(0)); assertTrue(it.isNull(1))
            }
            db.rawQuery("SELECT base_revision FROM pending_notes WHERE note_id = 'n'", null).use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0))
            }
        } finally { db.close() }
    }
    @Test fun remoteReconciliationKeepsPendingAndDeletesOnlyConfirmedMissing() {
        val a = com.sitticun.orderapp.data.model.NoteModel(id = "deleted", title = "A", content = "A")
        val b = com.sitticun.orderapp.data.model.NoteModel(id = "pending", title = "B", content = "B")
        val c = com.sitticun.orderapp.data.model.NoteModel(id = "unconfirmed", title = "C", content = "C")
        helper.upsertNote(a); helper.upsertNote(b); helper.upsertNote(c)
        helper.queueNote("pending", "upsert", "user", 1L)
        helper.reconcileNotes(listOf(b.copy(title = "remote")), setOf("deleted", "pending"))
        val remaining = helper.getAllNotes().associateBy { it.id }
        assertFalse(remaining.containsKey("deleted"))
        assertEquals("B", remaining["pending"]?.title)
        assertTrue(remaining.containsKey("unconfirmed"))
    }
    @Test fun heldBillRecoveryPreservesCopyAndClearsOriginalQueueTogether() {
        helper.inTransaction { helper.saveHeldBill(bill("original", 3)); helper.queueHeldBill("original", "user") }
        helper.inTransaction {
            helper.saveHeldBill(bill("copy", 3))
            helper.queueHeldBill("copy", "user")
            helper.deleteHeldBill("original")
            helper.clearPendingHeldBill("original")
        }
        assertEquals(listOf("copy"), helper.getAllHeldBills().map { it.id })
        assertEquals(setOf("copy"), helper.pendingHeldBills().keys)
        assertEquals(3, helper.getAllHeldBills().single().payload.paidQty["1"] ?: -1)
    }
}
