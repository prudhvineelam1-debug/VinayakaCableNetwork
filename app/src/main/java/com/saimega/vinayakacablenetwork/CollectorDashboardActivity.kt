package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CollectorDashboardActivity : AppCompatActivity() {

    private lateinit var etSearchSeries: EditText
    private lateinit var btnSearch: Button
    private lateinit var formLayout: LinearLayout

    // 🔥 SAFE (no crash even if not in XML)
    private var btnReport: Button? = null

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collector_dashboard)

        // 🔹 Bind Views
        etSearchSeries = findViewById(R.id.etSearchSeries)
        btnSearch = findViewById(R.id.btnSearch)
        formLayout = findViewById(R.id.formLayout)

        // 🔥 SAFE binding (important)
        btnReport = findViewById(R.id.btnReport)

        // 🔹 Firebase init
        db = FirebaseFirestore.getInstance()

        // ❌ Hide old form
        formLayout.visibility = View.GONE

        setupListeners()
    }

    private fun setupListeners() {

        // 🔍 SEARCH CUSTOMER
        btnSearch.setOnClickListener {

            val seriesText = etSearchSeries.text.toString().trim()

            if (seriesText.isEmpty()) {
                etSearchSeries.error = "Enter series number"
                return@setOnClickListener
            }

            btnSearch.isEnabled = false
            btnSearch.text = "Searching..."

            db.collection("customers")
                .document(seriesText)
                .get()
                .addOnSuccessListener { document ->

                    btnSearch.isEnabled = true
                    btnSearch.text = "Search"

                    if (document.exists()) {

                        val intent = Intent(this, CustomerDetailsActivity::class.java)
                        intent.putExtra("seriesNumber", seriesText)
                        startActivity(intent)

                    } else {
                        etSearchSeries.error = "Customer not found"
                        Toast.makeText(this, "Customer not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    btnSearch.isEnabled = true
                    btnSearch.text = "Search"
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 📊 REPORT BUTTON (SAFE)
        btnReport?.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }
}