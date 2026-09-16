package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var chipThisMonth: TextView
    private lateinit var chipLastMonth: TextView
    private lateinit var chipCustomRange: TextView
    private lateinit var tvCollectionLabel: TextView

    private var countListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var role: String = Roles.ADMIN

    private enum class DashboardPeriod { THIS_MONTH, LAST_MONTH, CUSTOM }
    private var selectedPeriod = DashboardPeriod.THIS_MONTH
    private var customRangeStartMs: Long = 0L
    private var customRangeEndMs: Long = 0L
    private data class PeriodRange(val startMs: Long, val endMs: Long, val label: String)

    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        db = FirebaseFirestore.getInstance()
        role = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("user_role", Roles.ADMIN) ?: Roles.ADMIN

        bindViews()
        applyRoleVisibility()
        setupQuickActions()
        setupTopBar()
        setupBottomNav()
        setupDateFilters()
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
        chipThisMonth = findViewById(R.id.chipThisMonth)
        chipLastMonth = findViewById(R.id.chipLastMonth)
        chipCustomRange = findViewById(R.id.chipCustomRange)
        tvCollectionLabel = findViewById(R.id.tvCollectionLabel)

        val username = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("username", "Admin") ?: "Admin"
        findViewById<TextView>(R.id.tvProfileInitial).text = username.firstOrNull()?.uppercase() ?: "A"

        val roleLabel = when (role) {
            Roles.ADMIN -> getString(R.string.role_admin_label)
            Roles.EMPLOYEE -> getString(R.string.role_employee_label)
            else -> getString(R.string.role_technician_label)
        }
        findViewById<TextView>(R.id.tvTopSubtitle).text = getString(R.string.dashboard_subtitle_format, roleLabel)

        setupStatCardNavigation()
    }

    private fun setupStatCardNavigation() {
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

    private fun applyRoleVisibility() {
        adminFinSection.visibility = if (role == Roles.ADMIN) View.VISIBLE else View.GONE
        employeeFinSection.visibility = if (role == Roles.EMPLOYEE) View.VISIBLE else View.GONE
        findViewById<View>(R.id.qaAddCustomer).visibility = if (role == Roles.ADMIN) View.VISIBLE else View.GONE
    }

    private fun setupQuickActions() {
        findViewById<View>(R.id.qaPayment).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "ALL"))
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
            .setTitle(getString(R.string.generate_bills_label))
            .setMessage(getString(R.string.generate_bills_confirm_message, monthLabel))
            .setPositiveButton(getString(R.string.generate_action)) { _, _ -> runGenerateBills(monthKey) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun runGenerateBills(monthKey: String) {
        lifecycleScope.launch {
            val result = CustomerRepository().generateMonthlyBills(monthKey)
            val message = when (result) {
                is BillingRunResult.Success -> getString(R.string.bills_generated_format, result.customersBilled)
                is BillingRunResult.AlreadyRun -> getString(R.string.bills_already_generated)
                is BillingRunResult.Failure -> getString(R.string.error_prefix, result.exception.message)
            }
            Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupTopBar() {
        // topbar title/subtitle already bound in bindViews()
    }

    private fun setupDateFilters() {
        chipThisMonth.setOnClickListener { selectPeriod(DashboardPeriod.THIS_MONTH) }
        chipLastMonth.setOnClickListener { selectPeriod(DashboardPeriod.LAST_MONTH) }
        chipCustomRange.setOnClickListener { showCustomRangePicker() }
        updateFilterChipStyles()
    }

    private fun selectPeriod(period: DashboardPeriod) {
        selectedPeriod = period
        updateFilterChipStyles()
        fetchCollectionSummaries()
    }

    private fun updateFilterChipStyles() {
        val chips = listOf(
            chipThisMonth to DashboardPeriod.THIS_MONTH,
            chipLastMonth to DashboardPeriod.LAST_MONTH,
            chipCustomRange to DashboardPeriod.CUSTOM
        )
        for ((chip, period) in chips) {
            val isSelected = period == selectedPeriod
            chip.setBackgroundResource(if (isSelected) R.drawable.cm_bg_input else R.drawable.cm_bg_card)
            chip.setTextColor(getColorCompat(if (isSelected) R.color.cm_blue else R.color.cm_text_secondary))
            chip.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun showCustomRangePicker() {
        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Range")
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            customRangeStartMs = utcPickerMillisToLocalStartOfDay(selection.first)
            customRangeEndMs = utcPickerMillisToLocalEndOfDay(selection.second)
            selectPeriod(DashboardPeriod.CUSTOM)
        }
        picker.show(supportFragmentManager, "dashboard_custom_range")
    }

    /** MaterialDatePicker returns UTC midnight millis; rebuild the same calendar date at local midnight. */
    private fun utcPickerMillisToLocalStartOfDay(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun utcPickerMillisToLocalEndOfDay(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private fun resolvePeriodRange(): PeriodRange {
        return when (selectedPeriod) {
            DashboardPeriod.THIS_MONTH -> monthRange(0)
            DashboardPeriod.LAST_MONTH -> monthRange(-1)
            DashboardPeriod.CUSTOM -> {
                val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                val label = "${fmt.format(Date(customRangeStartMs))} – ${fmt.format(Date(customRangeEndMs))}"
                PeriodRange(customRangeStartMs, customRangeEndMs, label)
            }
        }
    }

    private fun monthRange(monthOffset: Int): PeriodRange {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthOffset)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(start))
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return PeriodRange(start, end, label)
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
                    startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "ALL"))
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

    /**
     * Real 6-month collection trend: sums actual "paid" amounts from the
     * payments collection per calendar month (this month plus the prior 5),
     * fetched in a single range query and bucketed client-side. Bar heights
     * are relative to the highest-collecting month in the window (that
     * month renders at 100%) since there's no historical "expected billing"
     * total to compare against — only actual collections are recorded.
     */
    private fun renderSixMonthTrend() {
        if (role != Roles.ADMIN) return // section is hidden outside Admin (see applyRoleVisibility)
        lifecycleScope.launch {
            try {
                val startCal = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -5)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val windowStartMs = startCal.timeInMillis

                val endCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }
                val windowEndMs = endCal.timeInMillis

                val snap = db.collection("payments")
                    .whereGreaterThanOrEqualTo("timestamp", windowStartMs)
                    .whereLessThanOrEqualTo("timestamp", windowEndMs)
                    .get().await()

                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val sumsByMonthKey = HashMap<String, Double>()
                for (doc in snap.documents) {
                    val ts = doc.getLong("timestamp") ?: continue
                    val key = monthKeyFormat.format(Date(ts))
                    val paid = doc.getDouble("paid") ?: 0.0
                    sumsByMonthKey[key] = (sumsByMonthKey[key] ?: 0.0) + paid
                }

                val labelCal = Calendar.getInstance().apply { add(Calendar.MONTH, -5) }
                val monthlySums = (0 until 6).map {
                    val key = monthKeyFormat.format(labelCal.time)
                    val sum = sumsByMonthKey[key] ?: 0.0
                    labelCal.add(Calendar.MONTH, 1)
                    sum
                }

                drawSixMonthBars(monthlySums)
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Six-month trend fetch failed: ${e.message}")
            }
        }
    }

    private fun drawSixMonthBars(monthlySums: List<Double>) {
        val monthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -5)

        val maxSum = monthlySums.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

        sixMonthBars.removeAllViews()
        for ((index, sum) in monthlySums.withIndex()) {
            val percent = if (sum <= 0.0) 0.0 else ((sum / maxSum) * 100).coerceIn(2.0, 100.0)
            val isCurrent = index == monthlySums.lastIndex
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
        renderSixMonthTrend()
        lifecycleScope.launch {
            try {
                CustomerRepository().refreshConnectionStatuses()
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Connection status refresh failed: ${e.message}")
            }
        }
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

                val periodRange = resolvePeriodRange()

                val todaySnap = db.collection("payments").whereGreaterThanOrEqualTo("timestamp", todayStartMs).get().await()
                val periodSnap = db.collection("payments")
                    .whereGreaterThanOrEqualTo("timestamp", periodRange.startMs)
                    .whereLessThanOrEqualTo("timestamp", periodRange.endMs)
                    .get().await()
                val customerSnap = db.collection("customers").get().await()

                val todaySum = todaySnap.documents.sumOf { it.getDouble("paid") ?: 0.0 }
                val periodCollectionSum = periodSnap.documents.sumOf { it.getDouble("paid") ?: 0.0 }
                val outstandingSum = customerSnap.documents.sumOf { (it.get("pendingAmount") as? Number)?.toDouble() ?: 0.0 }
                val baseSum = customerSnap.documents.sumOf { (it.get("baseAmount") as? Number)?.toDouble() ?: 0.0 }

                tvTodayAmount.text = formatCurrency(todaySum)
                tvEmployeeToday.text = formatCurrency(todaySum)
                tvTotalOutstanding.text = formatCurrency(outstandingSum)
                tvMonthBilling.text = formatCurrency(baseSum)
                tvMonthCollection.text = formatCurrency(periodCollectionSum)
                tvCollectionLabel.text = getString(R.string.collection_period_label_format, periodRange.label)

                val percent = if (baseSum > 0) ((periodCollectionSum / baseSum) * 100).coerceIn(0.0, 100.0) else 0.0
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
                for (doc in snapshot) {
                    val status = doc.getString("status") ?: "unpaid"
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
