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

/** Result of the grace-period sweep: a customer's payment status and connection status for today. */
data class ConnectionStatusUpdate(
    val status: String,
    val connectionStatus: String
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

    /**
     * Grace-period rule: 1st–10th of the month, a customer stays active if
     * they paid last month or this month; from the 11th on, active requires
     * having paid this month. monthKey format is "yyyy-MM".
     */
    fun computeConnectionStatus(
        currentDay: Int,
        lastPaidMonth: String,
        currentMonth: String,
        lastMonth: String
    ): ConnectionStatusUpdate {
        val isPaidThisMonth = lastPaidMonth == currentMonth
        val isPaidLastMonth = lastPaidMonth == lastMonth
        val status = if (isPaidThisMonth) "paid" else "unpaid"
        val connectionStatus = if (currentDay <= 10) {
            if (isPaidLastMonth || isPaidThisMonth) "active" else "deactivated"
        } else {
            if (isPaidThisMonth) "active" else "deactivated"
        }
        return ConnectionStatusUpdate(status, connectionStatus)
    }
}
