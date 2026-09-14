package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class CustomerDetailsActivity : BaseActivity() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var tvName: TextView
    private lateinit var tvSeries: TextView
    private lateinit var tvAvatarInitial: TextView
    private lateinit var chipStatus: Chip

    private lateinit var rowBaseAmount: View
    private lateinit var rowPendingBill: View
    private lateinit var rowExtraCharges: View
    private lateinit var rowTotal: View
    private lateinit var rowRemaining: View
    private lateinit var rowPaidFully: View
    private lateinit var rowChange: View

    private lateinit var tvBaseAmount: TextView
    private lateinit var tvPendingAmount: TextView
    private lateinit var tvExtraChargesDisplay: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvRemainingAmount: TextView
    private lateinit var tvPaidFullyAmount: TextView
    private lateinit var tvChangeAmount: TextView

    private lateinit var etManualAmount: EditText
    private lateinit var etAmountPaid: EditText
    private lateinit var spPaymentMode: AutoCompleteTextView
    private lateinit var etPaymentNumber: EditText
    private lateinit var tilPaymentNumber: View
    private lateinit var tvUpiLabel: TextView

    private lateinit var btnSubmit: Button
    private lateinit var btnViewReceipt: Button
    private lateinit var btnDownloadInvoice: Button
    private lateinit var btnShareWhatsapp: Button
    private lateinit var btnViewHistory: Button
    private lateinit var btnCreateComplaint: Button

    private lateinit var rowEditDelete: View
    private lateinit var btnEditCustomer: Button
    private lateinit var btnDeleteCustomer: Button

    private var currentCustomer: CustomerModel? = null
    private val repository = CustomerRepository()
    private val auditLogRepository = AuditLogRepository()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        bindViews()
        setupPaymentMode()
        setupListeners()
        setupEditDelete()

        val series = intent.getStringExtra("seriesNumber") ?: ""
        val passedCustomer = intent.getSerializableExtra("customerModel") as? CustomerModel

        if (passedCustomer != null) {
            currentCustomer = passedCustomer
            bindCustomerData()
        } else if (series.isNotEmpty()) {
            fetchCustomer(series)
        }
    }

    private fun bindViews() {
        scrollView = findViewById(R.id.scrollView)
        tvName = findViewById(R.id.tvName)
        tvSeries = findViewById(R.id.tvSeries)
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)
        chipStatus = findViewById(R.id.chipStatus)

        rowBaseAmount = findViewById(R.id.rowBaseAmount)
        rowPendingBill = findViewById(R.id.rowPendingBill)
        rowExtraCharges = findViewById(R.id.rowExtraCharges)
        rowTotal = findViewById(R.id.rowTotal)
        rowRemaining = findViewById(R.id.rowRemaining)
        rowPaidFully = findViewById(R.id.rowPaidFully)
        rowChange = findViewById(R.id.rowChange)

        tvBaseAmount = rowBaseAmount.findViewById(R.id.rowValue)
        tvPendingAmount = rowPendingBill.findViewById(R.id.rowValue)
        tvExtraChargesDisplay = rowExtraCharges.findViewById(R.id.rowValue)
        tvTotalAmount = rowTotal.findViewById(R.id.rowValue)
        tvRemainingAmount = rowRemaining.findViewById(R.id.rowValue)
        tvPaidFullyAmount = rowPaidFully.findViewById(R.id.rowValue)
        tvChangeAmount = rowChange.findViewById(R.id.rowValue)

        // Set Labels
        rowBaseAmount.findViewById<TextView>(R.id.rowLabel).text = "Base Plan"
        rowPendingBill.findViewById<TextView>(R.id.rowLabel).text = "Arrears"
        rowExtraCharges.findViewById<TextView>(R.id.rowLabel).text = "Extra Charges"
        rowTotal.findViewById<TextView>(R.id.rowLabel).text = "Total Payable"
        rowRemaining.findViewById<TextView>(R.id.rowLabel).text = "Remaining"
        rowPaidFully.findViewById<TextView>(R.id.rowLabel).text = "Status"
        rowChange.findViewById<TextView>(R.id.rowLabel).text = "Return Change"

        etManualAmount = findViewById(R.id.etManualAmount)
        etAmountPaid = findViewById(R.id.etAmountPaid)
        spPaymentMode = findViewById(R.id.spPaymentMode)
        etPaymentNumber = findViewById(R.id.etPaymentNumber)
        tilPaymentNumber = findViewById(R.id.tilPaymentNumber)
        tvUpiLabel = findViewById(R.id.tvUpiLabel)

        btnSubmit = findViewById(R.id.btnSubmitPayment)
        btnViewReceipt = findViewById(R.id.btnViewReceipt)
        btnDownloadInvoice = findViewById(R.id.btnDownloadInvoice)
        btnShareWhatsapp = findViewById(R.id.btnShareWhatsapp)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        btnCreateComplaint = findViewById(R.id.btnCreateComplaint)

        rowEditDelete = findViewById(R.id.rowEditDelete)
        btnEditCustomer = findViewById(R.id.btnEditCustomer)
        btnDeleteCustomer = findViewById(R.id.btnDeleteCustomer)
    }

    private fun setupEditDelete() {
        val role = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("user_role", Roles.EMPLOYEE) ?: Roles.EMPLOYEE
        rowEditDelete.visibility = if (role == Roles.ADMIN) View.VISIBLE else View.GONE

        btnEditCustomer.setOnClickListener {
            val customer = currentCustomer ?: return@setOnClickListener
            val intent = Intent(this, NewCustomerActivity::class.java).apply {
                putExtra("customerModel", customer)
            }
            startActivity(intent)
        }

        btnDeleteCustomer.setOnClickListener {
            val customer = currentCustomer ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_customer_title)
                .setMessage(getString(R.string.delete_customer_message, customer.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        val success = repository.deleteCustomer(customer.id)
                        if (success) {
                            val prefs = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE)
                            auditLogRepository.logAction(
                                actorUsername = prefs.getString("username", "") ?: "",
                                actorRole = prefs.getString("user_role", Roles.EMPLOYEE) ?: Roles.EMPLOYEE,
                                action = AuditAction.DELETE_CUSTOMER,
                                targetType = AuditTargetType.CUSTOMER,
                                targetId = customer.id,
                                targetName = customer.name
                            )
                            Toast.makeText(this@CustomerDetailsActivity, getString(R.string.customer_deleted_successfully), Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@CustomerDetailsActivity, getString(R.string.error_prefix, "unknown"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .show()
        }
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { calculateFinalBill() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etManualAmount.addTextChangedListener(watcher)
        etAmountPaid.addTextChangedListener(watcher)

        btnSubmit.setOnClickListener {
            handlePayment()
        }

        btnViewReceipt.setOnClickListener {
            val intent = Intent(this, ReceiptActivity::class.java)
            intent.putExtra("CUSTOMER_ID", currentCustomer?.id)
            startActivity(intent)
        }

        btnDownloadInvoice.setOnClickListener {
            val intent = Intent(this, ReceiptActivity::class.java)
            intent.putExtra("CUSTOMER_ID", currentCustomer?.id)
            intent.putExtra("ACTION", "DOWNLOAD")
            startActivity(intent)
        }

        btnShareWhatsapp.setOnClickListener {
            val intent = Intent(this, ReceiptActivity::class.java)
            intent.putExtra("CUSTOMER_ID", currentCustomer?.id)
            intent.putExtra("ACTION", "WHATSAPP")
            startActivity(intent)
        }

        btnViewHistory.setOnClickListener {
            val intent = Intent(this, CustomerPaymentHistoryActivity::class.java)
            intent.putExtra("CUSTOMER_ID", currentCustomer?.id)
            startActivity(intent)
        }

        btnCreateComplaint.setOnClickListener {
            currentCustomer?.let { showCreateComplaintDialog(it) }
        }
    }

    private fun fetchCustomer(series: String) {
        db.collection("customers").document(series).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentCustomer = doc.toObject(CustomerModel::class.java)?.copy(id = doc.id)
                    bindCustomerData()
                }
            }
    }

    private fun bindCustomerData() {
        val c = currentCustomer ?: return
        tvName.text = c.name
        tvSeries.text = "ID: ${c.id}"
        tvAvatarInitial.text = c.name.firstOrNull()?.toString()?.uppercase() ?: "?"
        
        chipStatus.text = c.connectionStatus.uppercase()
        if (c.connectionStatus.equals("active", true)) {
            chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_dark)
        } else {
            chipStatus.setChipBackgroundColorResource(android.R.color.holo_red_dark)
        }

        tvBaseAmount.text = "₹${"%.2f".format(c.baseAmount)}"
        tvPendingAmount.text = "₹${"%.2f".format(c.pendingAmount)}"
        
        if (c.status.equals("paid", true)) {
            findViewById<View>(R.id.paymentFormArea).visibility = View.GONE
            btnViewReceipt.visibility = View.VISIBLE
            btnDownloadInvoice.visibility = View.VISIBLE
            btnShareWhatsapp.visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.paymentFormArea).visibility = View.VISIBLE
            btnViewReceipt.visibility = View.GONE
            btnDownloadInvoice.visibility = View.GONE
            btnShareWhatsapp.visibility = View.GONE
        }

        calculateFinalBill()
    }

    private fun calculateFinalBill() {
        val c = currentCustomer ?: return
        val extra = etManualAmount.text.toString().toDoubleOrNull() ?: 0.0
        val paid = etAmountPaid.text.toString().toDoubleOrNull() ?: 0.0

        val total = c.baseAmount + c.pendingAmount + extra
        val balance = total - paid

        tvExtraChargesDisplay.text = "₹${"%.2f".format(extra)}"
        tvTotalAmount.text = "₹${"%.2f".format(total)}"

        rowBaseAmount.visibility = View.VISIBLE
        rowPendingBill.visibility = if (c.pendingAmount > 0) View.VISIBLE else View.GONE
        rowExtraCharges.visibility = if (extra > 0) View.VISIBLE else View.GONE
        
        when {
            paid > 0 && balance == 0.0 -> {
                rowRemaining.visibility = View.GONE
                rowChange.visibility = View.GONE
                rowPaidFully.visibility = View.VISIBLE
                tvPaidFullyAmount.text = "PAID"
            }
            balance < 0 -> {
                rowRemaining.visibility = View.GONE
                rowPaidFully.visibility = View.GONE
                rowChange.visibility = View.VISIBLE
                tvChangeAmount.text = "₹${"%.2f".format(-balance)}"
            }
            else -> {
                rowPaidFully.visibility = View.GONE
                rowChange.visibility = View.GONE
                rowRemaining.visibility = View.VISIBLE
                tvRemainingAmount.text = "₹${"%.2f".format(balance)}"
            }
        }
    }

    private fun handlePayment() {
        val c = currentCustomer ?: return
        val paid = etAmountPaid.text.toString().toDoubleOrNull() ?: 0.0
        if (paid <= 0) {
            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false
        repository.submitPayment(
            customer = c,
            amountPaid = paid,
            paymentMode = spPaymentMode.text.toString(),
            paymentNumber = etPaymentNumber.text.toString(),
            onSuccess = {
                Toast.makeText(this, "Payment Successful", Toast.LENGTH_SHORT).show()
                finish()
            },
            onFailure = { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSubmit.isEnabled = true
            }
        )
    }

    private fun setupPaymentMode() {
        val modes = listOf("Cash", "PhonePe", "Google Pay", "Paytm", "UPI")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, modes)
        spPaymentMode.setAdapter(adapter)
        spPaymentMode.setText("Cash", false)

        spPaymentMode.setOnItemClickListener { _, _, position, _ ->
            val selected = modes[position]
            if (selected != "Cash") {
                tilPaymentNumber.visibility = View.VISIBLE
            } else {
                tilPaymentNumber.visibility = View.GONE
            }
        }
    }

    private fun showCreateComplaintDialog(customer: CustomerModel) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_create_complaint, null)
        dialog.setContentView(view)

        val etDesc = view.findViewById<EditText>(R.id.etDescription)
        val btnAdd = view.findViewById<Button>(R.id.btnSubmitComplaint)

        btnAdd.setOnClickListener {
            val desc = etDesc.text.toString().trim()
            if (desc.isEmpty()) return@setOnClickListener

            val complaint = ComplaintModel(
                id = db.collection("complaints").document().id,
                customerId = customer.id,
                customerName = customer.name,
                description = desc
            )

            db.collection("complaints").document(complaint.id).set(complaint)
                .addOnSuccessListener {
                    Toast.makeText(this, "Complaint Raised", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
        }
        dialog.show()
    }
}
