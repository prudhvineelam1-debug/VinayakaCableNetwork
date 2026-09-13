package com.saimega.vinayakacablenetwork

/**
 * Snapshot of the fields needed to compute one customer's next billing cycle.
 * Deliberately a plain data class (not CustomerModel) so this stays testable
 * without touching Firestore.
 */
data class CustomerBillingState(
    val id: String,
    val connectionStatus: String,
    val pendingAmount: Double,
    val monthlyCharge: Double
)

/** Result of billing one customer: their new pendingAmount for the new cycle. */
data class BilledCustomerUpdate(
    val id: String,
    val newPendingAmount: Double
)

object BillingCycle {

    /**
     * Carry-forward rule: whatever is left unpaid (0 if fully paid, the
     * remainder otherwise) plus this month's monthlyCharge. Deactivated
     * customers are excluded — they accrue nothing while inactive.
     */
    fun computeMonthlyBillUpdates(customers: List<CustomerBillingState>): List<BilledCustomerUpdate> {
        return customers
            .filter { it.connectionStatus.equals("active", ignoreCase = true) }
            .map { BilledCustomerUpdate(id = it.id, newPendingAmount = it.pendingAmount + it.monthlyCharge) }
    }
}
