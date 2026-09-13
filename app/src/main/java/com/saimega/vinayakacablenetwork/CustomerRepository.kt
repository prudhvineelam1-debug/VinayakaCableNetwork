package com.saimega.vinayakacablenetwork

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class BillingRunResult {
    data class Success(val customersBilled: Int) : BillingRunResult()
    object AlreadyRun : BillingRunResult()
    data class Failure(val exception: Exception) : BillingRunResult()
}

class CustomerRepository {

    companion object {
        const val PAGE_SIZE = 20L
    }

    private val db = FirebaseFirestore.getInstance()

    data class PageResult(
        val customers: List<CustomerModel>,
        val lastDocument: DocumentSnapshot?
    )

    // =========================
    // CUSTOMER PAGINATION
    // =========================

    private fun mapDocToCustomer(doc: DocumentSnapshot): CustomerModel {
        val rawStatus = doc.getString("status")?.trim()
        val statusVal = if (rawStatus.isNullOrEmpty()) "DATA_ERROR" else rawStatus
        val paymentStatusRaw = doc.getString("paymentStatus")?.trim()
        val connStatus = doc.getString("Connection Status") ?: "active"

        return CustomerModel(
            id = doc.id,
            name = doc.getString("name") ?: "",
            teluguName = doc.getString("telugu name") ?: "",
            phone = doc.getString("phone") ?: "",
            baseAmount = (doc.get("baseAmount") as? Number)?.toDouble() ?: 0.0,
            monthlyCharge = (doc.get("monthlyCharge") as? Number)?.toDouble() ?: 0.0,
            extraCharges = (doc.get("extraCharges") as? Number)?.toDouble() ?: 0.0,
            previousDue = (doc.get("previousDue") as? Number)?.toDouble() ?: 0.0,
            pendingAmount = (doc.get("pendingAmount") as? Number)?.toDouble() ?: 0.0,
            status = statusVal,
            paymentStatus = paymentStatusRaw ?: statusVal ?: "Unpaid",
            connectionStatus = connStatus,
            deactivatedMonth = doc.getString("deactivatedMonth"),
            lastPaidMonth = doc.getString("lastPaidMonth") ?: "",
            lastBilledMonth = doc.getString("lastBilledMonth") ?: ""
        )
    }

    suspend fun fetchFirstPage(status: String): PageResult {
        // Fetch ALL customers locally because we calculate status on the client now
        val snapshot = db.collection("customers")
            .get(Source.SERVER)
            .await()

        val customers = snapshot.documents.map { mapDocToCustomer(it) }

        // Apply local filtering
        val filtered = customers.filter { c ->
            if (status.equals("paid", true)) {
                c.paymentStatus.equals("Paid", true)
            } else {
                c.paymentStatus.equals("Unpaid", true)
            }
        }

        return PageResult(filtered, null) // null cursor means no more pages
    }

    suspend fun fetchNextPage(status: String, lastDocument: DocumentSnapshot): PageResult {
        return PageResult(emptyList(), null)
    }

    // =========================
    // SUBMIT PAYMENT
    // =========================

    fun submitPayment(
        customer: CustomerModel,
        amountPaid: Double,
        paymentMode: String,
        paymentNumber: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val paymentRef = db.collection("payments").document()
        val now = System.currentTimeMillis()
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(now))

        val totalAmount = customer.baseAmount + customer.extraCharges
        
        val payment = PaymentModel(
            paymentId = paymentRef.id,
            customerId = customer.id,
            name = customer.name,
            baseAmount = customer.baseAmount,
            extraCharges = customer.extraCharges,
            total = totalAmount,
            paid = amountPaid,
            remaining = totalAmount - amountPaid, // Temporary purely for payment document parity
            paymentMode = paymentMode,
            paymentNumber = paymentNumber,
            date = dateString,
            timestamp = now
        )

        val billingRef = db.collection("billing")
            .document(customer.id)
            .collection("months")
            .document(monthKey)
        val customerRef = db.collection("customers").document(customer.id)

        db.runTransaction { transaction ->
            // Fetch the customer document
            val customerSnapshot = transaction.get(customerRef)
            val billingSnapshot = transaction.get(billingRef)
            
            // Deduct the paymentAmount from the pendingAmount first.
            val currentPending = (customerSnapshot.get("pendingAmount") as? Number)?.toDouble() ?: 0.0
            val baseAmt = (customerSnapshot.get("baseAmount") as? Number)?.toDouble() ?: 0.0
            
            var newPending = currentPending - amountPaid
            var remainingPayment = 0.0
            
            if (newPending < 0) {
                remainingPayment = -newPending
                newPending = 0.0
            }
            
            var lastPaidMonth = customerSnapshot.getString("lastPaidMonth") ?: ""
            var status = "unpaid"
            
            // If there is remaining payment left after pendingAmount reaches 0, 
            // and the remainder is greater than or equal to the baseAmount, 
            // update the lastPaidMonth to the current month
            if (newPending == 0.0 && remainingPayment >= baseAmt) {
                lastPaidMonth = monthKey
            }
            
            // Classification Logic:
            // PAID: lastPaidMonth is current month AND pendingAmount is 0
            // PARTIAL: lastPaidMonth is current month BUT pendingAmount > 0 (or some payment made toward current)
            // UNPAID: lastPaidMonth is NOT current month
            
            if (lastPaidMonth == monthKey) {
                status = if (newPending == 0.0) "paid" else "partial"
            } else {
                status = "unpaid"
            }
            
            transaction.update(customerRef, mapOf(
                "pendingAmount" to newPending,
                "lastPaidMonth" to lastPaidMonth,
                "status" to status,
                "paymentStatus" to status.replaceFirstChar { it.uppercase() },
                "Connection Status" to "active" // Immediately reactivate on payment
            ))
            
            val previousPaid = (billingSnapshot.get("paid") as? Number)?.toDouble() ?: 0.0
            val newPaid = previousPaid + amountPaid
            val remaining = totalAmount - newPaid

            val billingData = mapOf(
                "customerId" to customer.id,
                "name" to customer.name,
                "total" to totalAmount,
                "paid" to newPaid,
                "remaining" to remaining,
                "timestamp" to now
            )

            transaction.set(paymentRef, payment)
            transaction.set(billingRef, billingData, SetOptions.merge())
            null
        }.addOnSuccessListener {
            Log.d("CustomerRepository", "Payment transaction completed successfully.")
            onSuccess()
        }.addOnFailureListener { e ->
            onFailure(e)
        }
    }



    // =========================
    // MONTHLY BILL GENERATION
    // =========================

    /**
     * Advances every active customer's billing cycle: pendingAmount becomes
     * whatever was left unpaid plus this month's monthlyCharge (the
     * carry-forward rule). Deactivated customers are skipped entirely.
     *
     * Idempotent per [monthKey]: if meta/billing.lastGeneratedMonth already
     * equals [monthKey], this is a no-op that returns [BillingRunResult.AlreadyRun].
     */
    suspend fun generateMonthlyBills(monthKey: String): BillingRunResult {
        return try {
            val metaRef = db.collection("meta").document("billing")
            val metaSnap = metaRef.get(Source.SERVER).await()
            if (metaSnap.getString("lastGeneratedMonth") == monthKey) {
                return BillingRunResult.AlreadyRun
            }

            // Fetch all and filter client-side (not whereEqualTo): many customer
            // documents (e.g. anything created via NewCustomerActivity) have no
            // "Connection Status" field at all, and Firestore's whereEqualTo can
            // never match a missing field. mapDocToCustomer's default-to-"active"
            // convention is what determines status for those documents, so the
            // query must fetch everything and apply that same default here.
            val snapshot = db.collection("customers")
                .get(Source.SERVER)
                .await()

            val states = snapshot.documents.map { doc ->
                CustomerBillingState(
                    id = doc.id,
                    connectionStatus = doc.getString("Connection Status") ?: "active",
                    pendingAmount = (doc.get("pendingAmount") as? Number)?.toDouble() ?: 0.0,
                    monthlyCharge = (doc.get("monthlyCharge") as? Number)?.toDouble() ?: 0.0
                )
            }

            val updates = BillingCycle.computeMonthlyBillUpdates(states)

            updates.chunked(500).forEach { chunk ->
                val batch = db.batch()
                for (update in chunk) {
                    val ref = db.collection("customers").document(update.id)
                    batch.update(ref, mapOf(
                        "pendingAmount" to update.newPendingAmount,
                        "lastBilledMonth" to monthKey,
                        "status" to "unpaid",
                        "paymentStatus" to "Unpaid"
                    ))
                }
                batch.commit().await()
            }

            metaRef.set(
                mapOf(
                    "lastGeneratedMonth" to monthKey,
                    "lastGeneratedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()

            BillingRunResult.Success(updates.size)
        } catch (e: Exception) {
            BillingRunResult.Failure(e)
        }
    }

    /**
     * Rebuilds a billing document for the given customer and month from the
     * source‑of‑truth payments collection. Used for recovery when a billing doc
     * is missing or corrupted.
     */
    suspend fun rebuildBillingFromPayments(customerId: String, monthKey: String) {
        // Parse monthKey (yyyy-MM) to start/end timestamps
        val monthDate = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(monthKey) ?: return
        val cal = Calendar.getInstance().apply { time = monthDate }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startTs = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val endTs = cal.timeInMillis

        // Fetch all payments for this customer in the month
        val snapshot = db.collection("payments")
            .whereEqualTo("customerId", customerId)
            .whereGreaterThanOrEqualTo("timestamp", startTs)
            .whereLessThanOrEqualTo("timestamp", endTs)
            .get(Source.SERVER)
            .await()

        // Sum paid amounts
        val totalPaid = snapshot.documents.sumOf { doc -> (doc.get("paid") as? Number)?.toDouble() ?: 0.0 }

        // Retrieve baseAmount and extraCharges from the customer document (source of truth)
        val customerSnap = db.collection("customers").document(customerId).get(Source.SERVER).await()
        val baseAmount = (customerSnap.get("baseAmount") as? Number)?.toDouble() ?: 0.0
        val extraCharges = (customerSnap.get("extraCharges") as? Number)?.toDouble() ?: 0.0
        val name = customerSnap.getString("name") ?: ""
        val total = baseAmount + extraCharges
        val remaining = total - totalPaid

        val billingData = mapOf(
            "customerId" to customerId,
            "name"       to name,
            "total"      to total,
            "paid"       to totalPaid,
            "remaining"  to remaining,
            "timestamp"  to System.currentTimeMillis()
        )

        val billingRef = db.collection("billing")
            .document(customerId)
            .collection("months")
            .document(monthKey)

        billingRef.set(billingData, SetOptions.merge()).await()
    }


    // =========================
    // FETCH UNPAID
    // =========================

    suspend fun fetchAllUnpaid(): List<CustomerModel> {
        val snapshot = db.collection("customers")
            .get(Source.SERVER)
            .await()

        val customers = snapshot.documents.map { mapDocToCustomer(it) }
        
        return customers.filter { it.paymentStatus.equals("Unpaid", ignoreCase = true) }
    }

    // =========================
    // DISTINCT FILTERING FUNCTIONS
    // =========================

    suspend fun getPaidCustomers(): List<CustomerModel> {
        val snapshot = db.collection("customers").get(Source.SERVER).await()
        return snapshot.documents
            .map { mapDocToCustomer(it) }
            .filter { it.paymentStatus.equals("Paid", ignoreCase = true) }
    }

    suspend fun getUnpaidCustomers(): List<CustomerModel> {
        val snapshot = db.collection("customers").get(Source.SERVER).await()
        return snapshot.documents
            .map { mapDocToCustomer(it) }
            .filter { it.paymentStatus.equals("Unpaid", ignoreCase = true) }
    }

    suspend fun getCustomersByStatus(status: String): List<CustomerModel> {
        val snapshot = db.collection("customers").get(Source.SERVER).await()
        return snapshot.documents
            .map { mapDocToCustomer(it) }
            .filter { it.paymentStatus.equals(status, ignoreCase = true) }
    }

    suspend fun getUnpaidCustomersForPdf(): List<CustomerModel> {
        // Fetch unpaid customers strictly based on the paymentStatus field,
        // and sort them alphabetically by name for the PDF report.
        return getUnpaidCustomers().sortedBy { it.name }
    }

    // ── Day-wise report: timestamp range ──────────────────────────────────────
    // startDate / endDate in "yyyy-MM-dd". Converts to midnight–23:59:59.999 ms.
    // Filters paid > 0 on the client; sorted latest first.
    suspend fun fetchPaymentsByDateRange(startDate: String, endDate: String): List<PaymentModel> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val cal = Calendar.getInstance()

        // Start of startDate → 00:00:00.000
        cal.time = sdf.parse(startDate) ?: Date()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
        val startTs = cal.timeInMillis

        // End of endDate → 23:59:59.999
        cal.time = sdf.parse(endDate) ?: Date()
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59);      cal.set(Calendar.MILLISECOND, 999)
        val endTs = cal.timeInMillis

        return fetchPaymentsByTimestampRange(startTs, endTs)
    }

    // ── Month-wise report: full calendar month ─────────────────────────────────
    // month: 1–12,  year: e.g. 2025
    suspend fun fetchPaymentsByMonth(month: Int, year: Int): List<PaymentModel> {
        val cal = Calendar.getInstance()

        // First millisecond of the month
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTs = cal.timeInMillis

        // Last millisecond of the month
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59);      cal.set(Calendar.MILLISECOND, 999)
        val endTs = cal.timeInMillis

        return fetchPaymentsByTimestampRange(startTs, endTs)
    }

    // ── Core: raw timestamp range query ───────────────────────────────────────
    // All other report methods delegate here.
    // Only records with paid > 0 are returned; sorted latest timestamp first.
    suspend fun fetchPaymentsByTimestampRange(startTs: Long, endTs: Long): List<PaymentModel> {
        val snapshot = db.collection("payments")
            .whereGreaterThanOrEqualTo("timestamp", startTs)
            .whereLessThanOrEqualTo("timestamp", endTs)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get(Source.SERVER)
            .await()

        return snapshot.toObjects(PaymentModel::class.java)
            .filter { it.paid > 0.0 }   // guard against ₹0 records
    }

    suspend fun importCustomerBatch(customers: List<CustomerModel>) {
        val batch = db.batch()
        for (c in customers) {
            val ref = db.collection("customers").document(c.id)
            val data = hashMapOf(
                "id" to c.id,
                "name" to c.name,
                "telugu name" to c.teluguName,
                "phone" to c.phone,
                "baseAmount" to c.baseAmount,
                "pendingAmount" to c.pendingAmount,
                "status" to c.status,
                "Connection Status" to c.connectionStatus,
                "lastPaidMonth" to c.lastPaidMonth,
                "timestamp" to System.currentTimeMillis()
            )
            batch.set(ref, data)
        }
        batch.commit().await()
    }
}