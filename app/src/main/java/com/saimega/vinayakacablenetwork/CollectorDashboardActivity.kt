package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CollectorDashboardActivity : BaseActivity() {

    private lateinit var etSearchSeries: EditText
    private lateinit var btnSearch: Button
    private lateinit var formLayout: LinearLayout

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collector_dashboard)

        // 🔹 Bind Views
        etSearchSeries = findViewById(R.id.etSearchSeries)
        btnSearch = findViewById(R.id.btnSearch)
        formLayout = findViewById(R.id.formLayout)

        db = FirebaseFirestore.getInstance()

        formLayout.visibility = View.GONE

        setupListeners()

        // 🔥 UPDATED LOGIC
        preloadCustomerStatus()
    }

    private fun setupListeners() {

        btnSearch.setOnClickListener {

            val seriesText = etSearchSeries.text.toString().trim()

            if (seriesText.isEmpty()) {
                etSearchSeries.error = getString(R.string.enter_series_number)
                return@setOnClickListener
            }

            btnSearch.isEnabled = false
            btnSearch.text = getString(R.string.searching)

            db.collection("customers")
                .document(seriesText)
                .get()
                .addOnSuccessListener { document ->

                    btnSearch.isEnabled = true
                    btnSearch.text = getString(R.string.search)

                    if (document.exists()) {

                        val intent = Intent(this, CustomerDetailsActivity::class.java)
                        intent.putExtra("seriesNumber", seriesText)
                        startActivity(intent)

                    } else {
                        etSearchSeries.error = getString(R.string.customer_not_found)
                        Toast.makeText(this, getString(R.string.customer_not_found), Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    btnSearch.isEnabled = true
                    btnSearch.text = getString(R.string.search)
                    Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_SHORT).show()
                }
        }
    }

    // 🔥 FIXED: monthly billing reset lifecycle with carryforward preservation
    private fun preloadCustomerStatus() {
        val cal = java.util.Calendar.getInstance()
        val currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
        val currentMonth = sdf.format(cal.time)
        
        // Calculate last month for grace period eligibility check
        val calLast = java.util.Calendar.getInstance()
        calLast.add(java.util.Calendar.MONTH, -1)
        val lastMonth = sdf.format(calLast.time)

        db.collection("customers")
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val lastPaid = doc.getString("lastPaidMonth") ?: ""
                    val baseAmount = (doc.get("baseAmount") as? Number)?.toDouble() ?: 275.0
                    
                    val isPaidThisMonth = (lastPaid == currentMonth)
                    val isPaidLastMonth = (lastPaid == lastMonth)
                    
                    val newStatus = if (isPaidThisMonth) "paid" else "unpaid"
                    
                    // Grace Period Logic:
                    // 1-10th: Active IF they paid last month or this month.
                    // 11th+: Active ONLY IF they paid this month.
                    val newConnectionStatus = if (currentDay <= 10) {
                        if (isPaidLastMonth || isPaidThisMonth) "active" else "deactivated"
                    } else {
                        if (isPaidThisMonth) "active" else "deactivated"
                    }

                    val updateMap = mutableMapOf<String, Any>(
                        "status" to newStatus,
                        "Connection Status" to newConnectionStatus
                    )

                    // Generate fresh bill for new month lifecycle
                    if (lastPaid != currentMonth) {
                        // Reset current month's bill to base plan amount
                        updateMap["finalBill"] = baseAmount
                        
                        // CRITICAL: pendingAmount is NOT touched here.
                        // It preserves historical carryforward arrears exactly as-is.
                    }

                    doc.reference.update(updateMap)
                }
            }
    }
}