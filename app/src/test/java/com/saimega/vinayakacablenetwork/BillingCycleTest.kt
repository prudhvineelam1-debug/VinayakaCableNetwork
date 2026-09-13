package com.saimega.vinayakacablenetwork

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingCycleTest {

    @Test
    fun `partial customer carries forward remainder plus new monthly charge`() {
        val customers = listOf(
            CustomerBillingState(
                id = "c1",
                connectionStatus = "active",
                pendingAmount = 50.0,
                monthlyCharge = 200.0
            )
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(1, updates.size)
        assertEquals("c1", updates[0].id)
        assertEquals(250.0, updates[0].newPendingAmount, 0.001)
    }

    @Test
    fun `fully paid customer still gets billed for the new month`() {
        val customers = listOf(
            CustomerBillingState(
                id = "c2",
                connectionStatus = "active",
                pendingAmount = 0.0,
                monthlyCharge = 200.0
            )
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(1, updates.size)
        assertEquals(200.0, updates[0].newPendingAmount, 0.001)
    }

    @Test
    fun `inactive customer is excluded entirely`() {
        val customers = listOf(
            CustomerBillingState(
                id = "c3",
                connectionStatus = "inactive",
                pendingAmount = 100.0,
                monthlyCharge = 200.0
            )
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(0, updates.size)
    }

    @Test
    fun `mixed batch only bills active customers`() {
        val customers = listOf(
            CustomerBillingState("active-1", "active", 50.0, 200.0),
            CustomerBillingState("inactive-1", "inactive", 999.0, 200.0),
            CustomerBillingState("active-2", "active", 0.0, 150.0)
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(2, updates.size)
        assertEquals(250.0, updates.first { it.id == "active-1" }.newPendingAmount, 0.001)
        assertEquals(150.0, updates.first { it.id == "active-2" }.newPendingAmount, 0.001)
    }

    @Test
    fun `connection status match is case insensitive`() {
        val customers = listOf(
            CustomerBillingState("c4", "ACTIVE", 0.0, 200.0)
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(1, updates.size)
    }
}
