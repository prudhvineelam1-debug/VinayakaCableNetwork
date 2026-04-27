package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CustomerDetailsActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvSeries: TextView
    private lateinit var etPhone: EditText
    private lateinit var tvBaseAmount: TextView
    private lateinit var tvPendingAmount: TextView
    private lateinit var etManualAmount: EditText
    private lateinit var tvFinalBill: TextView
    private lateinit var etAmountPaid: EditText
    private lateinit var btnSubmit: Button

    private lateinit var spPaymentMode: Spinner
    private lateinit var etPaymentNumber: EditText

    private var baseAmountValue = 0.0
    private var pendingAmountValue = 0.0

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_details)

        tvName = findViewById(R.id.tvName)
        tvSeries = findViewById(R.id.tvSeries)
        etPhone = findViewById(R.id.etPhone)
        tvBaseAmount = findViewById(R.id.tvBaseAmount)
        tvPendingAmount = findViewById(R.id.tvPendingAmount)
        etManualAmount = findViewById(R.id.etManualAmount)
        tvFinalBill = findViewById(R.id.tvFinalBill)
        etAmountPaid = findViewById(R.id.etAmountPaid)
        btnSubmit = findViewById(R.id.btnSubmitPayment)

        spPaymentMode = findViewById(R.id.spPaymentMode)
        etPaymentNumber = findViewById(R.id.etPaymentNumber)

        setupPaymentMode()

        val series = intent.getStringExtra("seriesNumber") ?: ""
        if (series.isNotEmpty()) {
            fetchCustomer(series)
        }

        etManualAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                calculateFinalBill()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etAmountPaid.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                calculateFinalBill()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSubmit.setOnClickListener {

            val seriesId = tvSeries.text.toString()

            val paid = etAmountPaid.text.toString().toDoubleOrNull() ?: 0.0
            val manual = etManualAmount.text.toString().toDoubleOrNull() ?: 0.0
            val paymentMode = spPaymentMode.selectedItem.toString()
            val paymentNumber = etPaymentNumber.text.toString()

            val total = baseAmountValue + pendingAmountValue + manual

            if (paid <= 0) {
                Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (paymentMode != "Cash" && paymentNumber.isEmpty()) {
                Toast.makeText(this, "Enter payment number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newPending = (total - paid).coerceAtLeast(0.0)

            savePaymentHistory(seriesId, paid, manual, total, newPending, paymentMode, paymentNumber)
        }
    }

    private fun fetchCustomer(series: String) {
        db.collection("customers")
            .document(series)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: "N/A"
                    val phone = doc.getString("phone") ?: ""

                    val base = (doc.get("baseAmount") as? Number)?.toDouble() ?: 0.0
                    val pending = (doc.get("pendingAmount") as? Number)?.toDouble() ?: 0.0

                    tvName.text = name
                    tvSeries.text = series
                    etPhone.setText(phone)

                    baseAmountValue = base
                    pendingAmountValue = pending

                    tvBaseAmount.text = "Base Amount: ₹$base"
                    tvPendingAmount.text = "Previous Due: ₹$pending"

                    calculateFinalBill()
                }
            }
    }

    private fun calculateFinalBill() {
        val manual = etManualAmount.text.toString().toDoubleOrNull() ?: 0.0
        val paid = etAmountPaid.text.toString().toDoubleOrNull() ?: 0.0

        val total = baseAmountValue + pendingAmountValue + manual
        val remaining = total - paid

        val text = StringBuilder()
        text.append("Final Bill: ₹$total")

        if (remaining > 0) {
            text.append("\nRemaining: ₹$remaining")
        } else if (remaining < 0) {
            text.append("\nChange: ₹${-remaining}")
        } else {
            text.append("\nPaid Fully ✅")
        }

        tvFinalBill.text = text.toString()
    }

    private fun setupPaymentMode() {
        val modes = listOf("Cash", "PhonePe", "GPay", "UPI")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPaymentMode.adapter = adapter

        spPaymentMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val mode = parent.getItemAtPosition(position).toString()
                etPaymentNumber.visibility =
                    if (mode == "Cash") android.view.View.GONE else android.view.View.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // 🔥 NEW METHOD (MAIN FEATURE)
    private fun savePaymentHistory(
        series: String,
        paid: Double,
        manual: Double,
        total: Double,
        newPending: Double,
        paymentMode: String,
        paymentNumber: String
    ) {

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val paymentData = hashMapOf(
            "amount" to paid,
            "totalBill" to total,
            "pendingAfter" to newPending,
            "mode" to paymentMode,
            "paymentNumber" to paymentNumber,
            "date" to date,
            "timestamp" to System.currentTimeMillis()
        )

        // ✅ SAVE HISTORY
        db.collection("customers")
            .document(series)
            .collection("payments")
            .add(paymentData)

        // ✅ UPDATE CUSTOMER
        db.collection("customers")
            .document(series)
            .update("pendingAmount", newPending)
            .addOnSuccessListener {
                Toast.makeText(this, "Payment Saved ✅", Toast.LENGTH_SHORT).show()

                pendingAmountValue = newPending
                tvPendingAmount.text = "Previous Due: ₹$newPending"

                etAmountPaid.setText("")
                etManualAmount.setText("")
                etPaymentNumber.setText("")

                calculateFinalBill()
            }
    }
}