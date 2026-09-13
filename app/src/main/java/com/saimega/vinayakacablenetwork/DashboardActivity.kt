package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var tvPaidCount: TextView
    private lateinit var tvUnpaidCount: TextView
    private lateinit var tvPartialCount: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvActiveCount: TextView
    private lateinit var tvOpenComplaints: TextView
    private lateinit var tvTodayAmount: TextView
    private lateinit var tvTotalOutstanding: TextView
    private lateinit var tvMonthBilling: TextView
    private lateinit var tvMonthCollection: TextView
    private lateinit var tvEmployeeToday: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressFill: View
    private lateinit var progressRemainder: View
    private lateinit var adminFinSection: View
    private lateinit var employeeFinSection: View
    private lateinit var sixMonthBars: android.widget.LinearLayout

    private var countListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var role: String = "ADMIN"

    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        db = FirebaseFirestore.getInstance()
        role = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("user_role", "ADMIN") ?: "ADMIN"

        bindViews()
        applyRoleVisibility()
        setupQuickActions()
        setupTopBar()
        setupBottomNav()
        renderSixMonthTrend()
        applyStatusBarInset()
    }

    private fun applyStatusBarInset() {
        val topBar = findViewById<View>(R.id.dashboardTopBar)
        val basePaddingTop = topBar.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val statusBarInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInset.top + basePaddingTop, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun bindViews() {
        tvPaidCount = findViewById(R.id.tvPaidCount)
        tvUnpaidCount = findViewById(R.id.tvUnpaidCount)
        tvPartialCount = findViewById(R.id.tvPartialCount)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvOpenComplaints = findViewById(R.id.tvOpenComplaints)
        tvTodayAmount = findViewById(R.id.tvTodayAmount)
        tvTotalOutstanding = findViewById(R.id.tvTotalOutstanding)
        tvMonthBilling = findViewById(R.id.tvMonthBilling)
        tvMonthCollection = findViewById(R.id.tvMonthCollection)
        tvEmployeeToday = findViewById(R.id.tvEmployeeToday)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        progressFill = findViewById(R.id.progressFill)
        progressRemainder = findViewById(R.id.progressRemainder)
        adminFinSection = findViewById(R.id.adminFinSection)
        employeeFinSection = findViewById(R.id.employeeFinSection)
        sixMonthBars = findViewById(R.id.sixMonthBars)

        val username = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("username", "Admin") ?: "Admin"
        findViewById<TextView>(R.id.tvProfileInitial).text = username.firstOrNull()?.uppercase() ?: "A"

        val roleLabel = when (role) {
            "ADMIN" -> "Admin"
            "EMPLOYEE" -> "Employee"
            else -> "Technician"
        }
        findViewById<TextView>(R.id.tvTopSubtitle).text = "$roleLabel · Vinayaka Cable Network"

        val searchBox = findViewById<EditText>(R.id.etDashboardSearch)
        searchBox.doAfterTextChanged { text ->
            if ((text?.length ?: 0) > 1) {
                startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "ALL"))
            }
        }
    }

    private fun applyRoleVisibility() {
        adminFinSection.visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
        employeeFinSection.visibility = if (role == "EMPLOYEE") View.VISIBLE else View.GONE
        findViewById<View>(R.id.qaAddCustomer).visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
    }

    private fun setupQuickActions() {
        findViewById<View>(R.id.qaPayment).setOnClickListener {
            startActivity(Intent(this, CollectorDashboardActivity::class.java))
        }
        findViewById<View>(R.id.qaAddCustomer).setOnClickListener {
            startActivity(Intent(this, NewCustomerActivity::class.java))
        }
        findViewById<View>(R.id.qaPaid).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "PAID"))
        }
        findViewById<View>(R.id.qaUnpaid).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "UNPAID"))
        }
        findViewById<View>(R.id.qaComplaints).setOnClickListener {
            startActivity(Intent(this, ComplaintActivity::class.java))
        }
        findViewById<View>(R.id.qaReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        findViewById<View>(R.id.qaGenerateBills).setOnClickListener {
            showGenerateBillsConfirmation()
        }

        findViewById<View>(R.id.statTotal).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "ALL"))
        }
        findViewById<View>(R.id.statPaid).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "PAID"))
        }
        findViewById<View>(R.id.statUnpaid).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "UNPAID"))
        }
        findViewById<View>(R.id.statPartial).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "PARTIAL"))
        }
    }

    private fun showGenerateBillsConfirmation() {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Generate Bills")
            .setMessage("Generate bills for $monthLabel for all active customers?")
            .setPositiveButton("Generate") { _, _ -> runGenerateBills(monthKey) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runGenerateBills(monthKey: String) {
        lifecycleScope.launch {
            val result = CustomerRepository().generateMonthlyBills(monthKey)
            val message = when (result) {
                is BillingRunResult.Success -> "Bills generated for ${result.customersBilled} customers."
                is BillingRunResult.AlreadyRun -> "Bills for this month were already generated."
                is BillingRunResult.Failure -> "Error: ${result.exception.message}"
            }
            Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupTopBar() {
        // topbar title/subtitle already bound in bindViews()
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_customers -> {
                    startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "ALL"))
                    false
                }
                R.id.nav_pay -> {
                    startActivity(Intent(this, CollectorDashboardActivity::class.java))
                    false
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ComplaintActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun renderSixMonthTrend() {
        // Illustrative trend — real 6-month historical aggregation is Reports-sub-project work.
        val demoPercents = listOf(68, 82, 58, 88, 79, 77)
        val monthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -5)

        sixMonthBars.removeAllViews()
        for ((index, percent) in demoPercents.withIndex()) {
            val isCurrent = index == demoPercents.lastIndex
            val column = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = if (index == 0) 0 else 5
                }
            }
            val bar = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0
                ).apply { weight = percent.toFloat() }
                setBackgroundResource(if (isCurrent) R.drawable.cm_bg_soft_teal else R.drawable.cm_bg_soft_blue)
            }
            val spacer = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0
                ).apply { weight = (100 - percent).toFloat() }
            }
            val barColumn = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                addView(spacer)
                addView(bar)
            }
            column.addView(barColumn)

            val label = TextView(this).apply {
                text = monthFormat.format(cal.time)
                textSize = 9f
                setTextColor(getColorCompat(R.color.cm_text_tertiary))
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            column.addView(label)

            sixMonthBars.addView(column)
            cal.add(Calendar.MONTH, 1)
        }
    }

    private fun getColorCompat(colorRes: Int): Int = androidx.core.content.ContextCompat.getColor(this, colorRes)

    override fun onResume() {
        super.onResume()
        listenToCustomerStats()
        fetchCollectionSummaries()
    }

    override fun onPause() {
        super.onPause()
        countListener?.remove()
        countListener = null
    }

    private fun fetchCollectionSummaries() {
        lifecycleScope.launch {
            try {
                val dayCal = Calendar.getInstance()
                dayCal.set(Calendar.HOUR_OF_DAY, 0); dayCal.set(Calendar.MINUTE, 0)
                dayCal.set(Calendar.SECOND, 0); dayCal.set(Calendar.MILLISECOND, 0)
                val todayStartMs = dayCal.timeInMillis

                val monthCal = Calendar.getInstance()
                monthCal.set(Calendar.DAY_OF_MONTH, 1)
                monthCal.set(Calendar.HOUR_OF_DAY, 0); monthCal.set(Calendar.MINUTE, 0)
                monthCal.set(Calendar.SECOND, 0); monthCal.set(Calendar.MILLISECOND, 0)
                val monthStartMs = monthCal.timeInMillis

                val todaySnap = db.collection("payments").whereGreaterThanOrEqualTo("timestamp", todayStartMs).get().await()
                val monthSnap = db.collection("payments").whereGreaterThanOrEqualTo("timestamp", monthStartMs).get().await()
                val customerSnap = db.collection("customers").get().await()

                val todaySum = todaySnap.documents.sumOf { it.getDouble("paid") ?: 0.0 }
                val monthCollectionSum = monthSnap.documents.sumOf { it.getDouble("paid") ?: 0.0 }
                val outstandingSum = customerSnap.documents.sumOf { (it.get("pendingAmount") as? Number)?.toDouble() ?: 0.0 }
                val baseSum = customerSnap.documents.sumOf { (it.get("baseAmount") as? Number)?.toDouble() ?: 0.0 }

                tvTodayAmount.text = formatCurrency(todaySum)
                tvEmployeeToday.text = formatCurrency(todaySum)
                tvTotalOutstanding.text = formatCurrency(outstandingSum)
                tvMonthBilling.text = formatCurrency(baseSum)
                tvMonthCollection.text = formatCurrency(monthCollectionSum)

                val percent = if (baseSum > 0) ((monthCollectionSum / baseSum) * 100).coerceIn(0.0, 100.0) else 0.0
                tvProgressPercent.text = "${percent.toInt()}%"
                (progressFill.layoutParams as android.widget.LinearLayout.LayoutParams).weight = percent.toFloat()
                (progressRemainder.layoutParams as android.widget.LinearLayout.LayoutParams).weight = (100 - percent).toFloat()
                progressFill.requestLayout()
                progressRemainder.requestLayout()

            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Summary fetch failed: ${e.message}")
            }
        }
    }

    private fun listenToCustomerStats() {
        if (countListener != null) return
        countListener = db.collection("customers")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                var paid = 0
                var unpaid = 0
                var partial = 0
                var active = 0
                for (doc in snapshot) {
                    val status = doc.getString("status") ?: "unpaid"
                    val conn = doc.getString("Connection Status") ?: "active"
                    if (conn.equals("active", true)) active++
                    when {
                        status.equals("paid", true) -> paid++
                        status.equals("partial", true) -> partial++
                        else -> unpaid++
                    }
                }
                tvPaidCount.text = paid.toString()
                tvUnpaidCount.text = unpaid.toString()
                tvPartialCount.text = partial.toString()
                tvTotalCount.text = snapshot.size().toString()
                tvActiveCount.text = active.toString()
            }

        db.collection("complaints").whereEqualTo("status", "NEW").addSnapshotListener { snap, _ ->
            tvOpenComplaints.text = (snap?.size() ?: 0).toString()
        }
    }

    private fun formatCurrency(amount: Double): String {
        return try {
            currencyFormatter.format(amount)
        } catch (e: Exception) {
            "₹${"%.2f".format(amount)}"
        }
    }

}
