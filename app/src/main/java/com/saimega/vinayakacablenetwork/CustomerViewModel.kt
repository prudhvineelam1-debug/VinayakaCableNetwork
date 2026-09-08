package com.saimega.vinayakacablenetwork

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source

/**
 * CustomerViewModel
 *
 * Owns all state for CustomerListActivity.
 *
 * Data strategy — real-time Firestore snapshot listener:
 *  - [_customers] is updated automatically whenever any customer document changes in Firestore.
 *  - This means returning from CustomerDetailsActivity after a payment automatically
 *    reflects the new "paid" status and ₹0 pendingAmount with NO manual refresh needed.
 *  - The listener is started in [init] and stopped in [onCleared] to prevent memory leaks.
 *
 * DC Rule: the UI must show [CustomerModel.pendingAmount] directly from Firestore —
 * never a locally-recalculated sum. The adapter and model already enforce this.
 */
class CustomerViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val repository = CustomerRepository()

    // ── Public state ──────────────────────────────────────────────────────────

    /** Full filtered list pushed to the adapter in real-time. */
    private val _customers = MutableLiveData<List<CustomerModel>>(emptyList())
    val customers: LiveData<List<CustomerModel>> = _customers

    /** True while the initial load is in-flight. */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** Non-null on Firestore error. */
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // ── Private state ─────────────────────────────────────────────────────────
    private var currentStatus = "unpaid"
    private var snapshotListener: ListenerRegistration? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call once from Activity.onCreate.
     * Attaches a real-time Firestore snapshot listener filtered by [status].
     * Any Firestore document change (e.g. status flipped to "paid") will push a
     * fresh list to [customers] automatically.
     */
    fun init(status: String) {
        currentStatus = status.lowercase()
        attachSnapshotListener()
    }

    /**
     * Force a re-attach (e.g. after a connectivity recovery).
     * For normal use, the snapshot listener handles updates automatically.
     */
    fun refresh() {
        detachSnapshotListener()
        attachSnapshotListener()
    }

    /**
     * Updates the status filter dynamically from the UI (e.g. filter chips)
     */
    fun updateFilter(status: String) {
        currentStatus = status.lowercase()
        detachSnapshotListener()
        attachSnapshotListener()
    }

    // Compatibility shim — kept so scroll listener calls still compile
    fun loadNextPageIfNeeded() { /* real-time listener returns all matching docs */ }

    // ── Snapshot listener ─────────────────────────────────────────────────────

    private fun attachSnapshotListener() {
        if (snapshotListener != null) return   // already attached

        _isLoading.value = true

        // Build the query: filter by the status field that submitPayment writes ("paid"/"unpaid").
        // Firestore real-time listener — fires immediately with current data, then on every change.
        val query = if (currentStatus == "paid") {
            db.collection("customers").whereEqualTo("status", "paid")
        } else {
            db.collection("customers")
        }

        snapshotListener = query.addSnapshotListener { snapshot, error ->
            _isLoading.value = false

            if (error != null) {
                _error.value = error.message
                return@addSnapshotListener
            }

            if (snapshot == null) return@addSnapshotListener

            val allDocs = snapshot.documents.mapNotNull { doc ->
                try { mapDocToCustomer(doc) } catch (e: Exception) { null }
            }

            // Client-side status filter with total visibility for unpaid collection
            val filtered = when (currentStatus.lowercase()) {
                "paid"    -> allDocs.filter { it.status.equals("paid", true) }
                "partial" -> allDocs.filter { it.status.equals("partial", true) }
                "unpaid"  -> allDocs.filter { it.status.equals("unpaid", true) }
                "active"  -> allDocs.filter { it.connectionStatus.equals("active", true) }
                "inactive"-> allDocs.filter { it.connectionStatus.equals("deactivated", true) }
                else      -> allDocs // "all"
            }

            // Sort alphabetically by name
            _customers.value = filtered.sortedBy { it.name }
        }
    }

    private fun detachSnapshotListener() {
        snapshotListener?.remove()
        snapshotListener = null
    }

    // ── Firestore → CustomerModel mapping ─────────────────────────────────────

    private fun mapDocToCustomer(doc: com.google.firebase.firestore.DocumentSnapshot): CustomerModel {
        val rawStatus = doc.getString("status")?.trim()
        val statusVal = if (rawStatus.isNullOrEmpty()) "DATA_ERROR" else rawStatus
        val paymentStatusRaw = doc.getString("paymentStatus")?.trim()
        val connStatus = doc.getString("Connection Status") ?: "active"

        return CustomerModel(
            id            = doc.id,
            name          = doc.getString("name") ?: "",
            teluguName    = doc.getString("telugu name") ?: "",
            phone         = doc.getString("phone") ?: "",
            baseAmount    = (doc.get("baseAmount") as? Number)?.toDouble() ?: 0.0,
            monthlyCharge = (doc.get("monthlyCharge") as? Number)?.toDouble() ?: 0.0,
            extraCharges  = (doc.get("extraCharges") as? Number)?.toDouble() ?: 0.0,
            previousDue   = (doc.get("previousDue") as? Number)?.toDouble() ?: 0.0,
            pendingAmount = (doc.get("pendingAmount") as? Number)?.toDouble() ?: 0.0,
            status        = statusVal,
            paymentStatus = paymentStatusRaw ?: statusVal,
            connectionStatus = connStatus,
            deactivatedMonth = doc.getString("deactivatedMonth"),
            lastPaidMonth = doc.getString("lastPaidMonth") ?: ""
        )
    }

    // ── Distinct fetch helpers (used by ReportActivity / DashboardActivity) ───

    suspend fun getPaidCustomers(): List<CustomerModel> = repository.getPaidCustomers()

    suspend fun getUnpaidCustomers(): List<CustomerModel> = repository.getUnpaidCustomers()

    suspend fun getUnpaidCustomersForPdf(): List<CustomerModel> = repository.getUnpaidCustomersForPdf()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        detachSnapshotListener()  // prevent memory leak when Activity is destroyed
    }
}
