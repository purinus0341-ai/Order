package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.model.HeldBill
import com.sitticun.orderapp.data.model.HeldBillPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldBillMatchingTest {
    private val bill = HeldBill(id = "held-1", customerId = "customer-1", payload = HeldBillPayload(paidQty = mapOf("1" to 2)))

    @Test fun acceptsSameContentAfterServerNormalization() {
        assertTrue(heldBillsMatch(bill, bill.copy(note = "", version = 1)))
    }

    @Test fun rejectsDifferentBillCustomerOrContents() {
        assertFalse(heldBillsMatch(bill, bill.copy(id = "other")))
        assertFalse(heldBillsMatch(bill, bill.copy(customerId = "other")))
        assertFalse(heldBillsMatch(bill, bill.copy(payload = HeldBillPayload(paidQty = mapOf("1" to 3)))))
        assertFalse(heldBillsMatch(bill, bill.copy(note = "other")))
    }
}
