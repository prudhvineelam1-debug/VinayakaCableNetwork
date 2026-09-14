package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class NewCustomerActivity : BaseActivity() {

    // ── Firestore ─────────────────────────────────────────────────────────────
    private lateinit var db: FirebaseFirestore
    private val customerRepository = CustomerRepository()
    private val auditLogRepository = AuditLogRepository()

    // ── Input fields ──────────────────────────────────────────────────────────
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilSeriesNumber: TextInputLayout
    private lateinit var tilPhoneNumber: TextInputLayout
    private lateinit var tilBaseAmount: TextInputLayout

    private lateinit var etFullName: TextInputEditText
    private lateinit var etSeriesNumber: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etBaseAmount: TextInputEditText

    // ── Action views ──────────────────────────────────────────────────────────
    private lateinit var btnSaveCustomer: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator

    // ── Edit mode ─────────────────────────────────────────────────────────────
    private var editingCustomer: CustomerModel? = null

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val role = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("user_role", Roles.EMPLOYEE) ?: Roles.EMPLOYEE
        if (role != Roles.ADMIN) {
            Toast.makeText(this, getString(R.string.no_permission_message), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_new_customer)

        db = FirebaseFirestore.getInstance()
        editingCustomer = intent.getSerializableExtra("customerModel") as? CustomerModel

        // Toolbar with back navigation
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Bind input fields
        tilFullName     = findViewById(R.id.tilFullName)
        tilSeriesNumber = findViewById(R.id.tilSeriesNumber)
        tilPhoneNumber  = findViewById(R.id.tilPhoneNumber)
        tilBaseAmount   = findViewById(R.id.tilBaseAmount)

        etFullName     = findViewById(R.id.etFullName)
        etSeriesNumber = findViewById(R.id.etSeriesNumber)
        etPhoneNumber  = findViewById(R.id.etPhoneNumber)
        etBaseAmount   = findViewById(R.id.etBaseAmount)

        // Bind action views
        btnSaveCustomer = findViewById(R.id.btnSaveCustomer)
        progressBar     = findViewById(R.id.progressBar)

        editingCustomer?.let { customer ->
            toolbar.title = getString(R.string.edit_customer)
            btnSaveCustomer.text = getString(R.string.update_customer)
            etFullName.setText(customer.name)
            etSeriesNumber.setText(customer.id)
            etPhoneNumber.setText(customer.phone)
            etBaseAmount.setText(if (customer.baseAmount > 0) customer.baseAmount.toString() else "")
            // Series number is the Firestore document ID — changing it here would
            // orphan the existing document instead of renaming it, so it's locked.
            etSeriesNumber.isEnabled = false
        }

        // Save click
        btnSaveCustomer.setOnClickListener {
            if (validateForm()) {
                if (editingCustomer != null) {
                    updateCustomerInFirebase()
                } else {
                    saveCustomerToFirebase()
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Validates all required form fields. Returns true if the form is valid.
     * Sets inline errors on the corresponding [TextInputLayout] for any failures.
     */
    private fun validateForm(): Boolean {
        var isValid = true

        // Clear previous errors
        tilFullName.error = null
        tilSeriesNumber.error = null
        tilBaseAmount.error = null

        // Full Name — required
        val name = etFullName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            tilFullName.error = getString(R.string.customer_name_required)
            isValid = false
        }

        // Series Number — required
        val series = etSeriesNumber.text?.toString()?.trim().orEmpty()
        if (series.isEmpty()) {
            tilSeriesNumber.error = getString(R.string.series_number_required)
            isValid = false
        }

        // Base Amount — required and must be a valid number
        val amountStr = etBaseAmount.text?.toString()?.trim().orEmpty()
        if (amountStr.isEmpty()) {
            tilBaseAmount.error = getString(R.string.monthly_amount_required)
            isValid = false
        } else {
            val parsed = amountStr.toDoubleOrNull()
            if (parsed == null || parsed < 0) {
                tilBaseAmount.error = getString(R.string.enter_a_valid_amount)
                isValid = false
            }
        }

        return isValid
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FIRESTORE SAVE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Uses the entered Series Number as the Firestore document ID, builds
     * the customer data map, and writes it to the `customers` collection.
     */
    private fun saveCustomerToFirebase() {
        // Lock the UI while saving
        setLoadingState(true)

        // Read form values
        val name        = etFullName.text?.toString()?.trim().orEmpty()
        val series      = etSeriesNumber.text?.toString()?.trim().orEmpty()
        val phone       = etPhoneNumber.text?.toString()?.trim().orEmpty()
        val baseAmount  = etBaseAmount.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0

        // Use the series number as the document ID so search-by-ID works
        val customerId = series

        // Build the customer document
        val customerData = hashMapOf(
            "id"            to customerId,
            "name"          to name,
            "seriesNumber"  to series,
            "phone"         to phone,
            "baseAmount"    to baseAmount,
            "monthlyCharge" to 0.0,
            "extraCharges"  to 0.0,
            "previousDue"   to 0.0,
            "pendingAmount" to baseAmount,   // first month starts as owing the base
            "finalBill"     to baseAmount,
            "status"        to "unpaid",
            "timestamp"     to System.currentTimeMillis()
        )

        // Write to Firestore
        db.collection("customers")
            .document(customerId)
            .set(customerData)
            .addOnSuccessListener {
                setLoadingState(false)
                Toast.makeText(this, getString(R.string.customer_added_successfully), Toast.LENGTH_SHORT).show()
                finish()   // return to Dashboard
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                Toast.makeText(
                    this,
                    getString(R.string.failed_prefix, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /**
     * Updates the identity/billing-input fields of an existing customer.
     * Never touches payment/status fields — see [CustomerRepository.updateCustomer].
     */
    private fun updateCustomerInFirebase() {
        val customer = editingCustomer ?: return
        setLoadingState(true)

        val name = etFullName.text?.toString()?.trim().orEmpty()
        val phone = etPhoneNumber.text?.toString()?.trim().orEmpty()
        val baseAmount = etBaseAmount.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0

        lifecycleScope.launch {
            val success = customerRepository.updateCustomer(customer.id, name, phone, baseAmount)
            setLoadingState(false)
            if (success) {
                val prefs = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE)
                auditLogRepository.logAction(
                    actorUsername = prefs.getString("username", "") ?: "",
                    actorRole = prefs.getString("user_role", Roles.EMPLOYEE) ?: Roles.EMPLOYEE,
                    action = AuditAction.EDIT_CUSTOMER,
                    targetType = AuditTargetType.CUSTOMER,
                    targetId = customer.id,
                    targetName = name
                )
                Toast.makeText(this@NewCustomerActivity, getString(R.string.customer_updated_successfully), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@NewCustomerActivity, getString(R.string.error_prefix, "unknown"), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Toggles the progress bar and disables the save button during network I/O. */
    private fun setLoadingState(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSaveCustomer.isEnabled = !loading
    }
}
