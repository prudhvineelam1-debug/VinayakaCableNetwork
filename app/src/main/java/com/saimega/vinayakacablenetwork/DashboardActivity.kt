package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // 🔹 Initialize Views
        val paymentLayout = findViewById<LinearLayout>(R.id.layoutPayment)
        val searchLayout = findViewById<LinearLayout>(R.id.layoutSearch)
        val newCustomerLayout = findViewById<LinearLayout>(R.id.layoutNewCustomer)

        // 🔥 NEW → REPORT BUTTON (DO NOT REMOVE ANYTHING)
        val reportLayout = findViewById<LinearLayout>(R.id.btnReport)

        // 💰 Collect Payment
        paymentLayout.setOnClickListener {
            startActivity(Intent(this, CollectorDashboardActivity::class.java))
        }

        // 🔍 Search Customer
        searchLayout.setOnClickListener {
            startActivity(Intent(this, CollectorDashboardActivity::class.java))
        }

        // ➕ Add Customer
        newCustomerLayout.setOnClickListener {
            Toast.makeText(this, "New Customer screen coming soon", Toast.LENGTH_SHORT).show()
        }

        // 📊 OPEN REPORT SCREEN ✅
        reportLayout.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }
}