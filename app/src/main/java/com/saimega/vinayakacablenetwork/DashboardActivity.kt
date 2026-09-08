package com.saimega.vinayakacablenetwork

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvPaidCount: TextView
    private lateinit var tvUnpaidCount: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvActiveCount: TextView
    private lateinit var tvOpenComplaints: TextView
    private lateinit var tvTodayAmount: TextView
    private lateinit var tvTotalOutstanding: TextView
    
    private var countListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        db = FirebaseFirestore.getInstance()

        bindViews()
        setupQuickActions()
        setupListeners()
    }

    private fun bindViews() {
        tvPaidCount = findViewById(R.id.tvPaidCount)
        tvUnpaidCount = findViewById(R.id.tvUnpaidCount)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvOpenComplaints = findViewById(R.id.tvOpenComplaints)
        tvTodayAmount = findViewById(R.id.tvTodayAmount)
        tvTotalOutstanding = findViewById(R.id.tvTotalOutstanding)

        val username = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("username", "Admin") ?: "Admin"
        findViewById<TextView>(R.id.tvUsername).text = "Welcome, $username 👋"
        findViewById<TextView>(R.id.tvProfileInitial).text = username.firstOrNull()?.uppercase() ?: "A"
    }

    private fun setupQuickActions() {
        val actions = listOf(
            Triple(R.id.btn_payment, "Payment", R.drawable.ic_payment_premium),
            Triple(R.id.btn_search, "Search", R.drawable.ic_search_premium),
            Triple(R.id.btn_new_customer, "New Connection", R.drawable.ic_new_user_premium),
            Triple(R.id.btn_reports, "Analytics", R.drawable.ic_report_premium)
        )

        for ((id, title, icon) in actions) {
            val view = findViewById<View>(id)
            view.findViewById<TextView>(R.id.actionText).text = title
            view.findViewById<ImageView>(R.id.actionIcon).setImageResource(icon)
            
            view.setOnClickListener {
                bounceAndNavigate(it) {
                    when (id) {
                        R.id.btn_payment -> startActivity(Intent(this, CollectorDashboardActivity::class.java))
                        R.id.btn_search -> startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "ALL"))
                        R.id.btn_new_customer -> startActivity(Intent(this, NewCustomerActivity::class.java))
                        R.id.btn_reports -> startActivity(Intent(this, ReportActivity::class.java))
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnLangEn).setOnClickListener {
            LocaleHelper.setLocale(this, "en")
            recreate()
        }
        findViewById<View>(R.id.btnLangTe).setOnClickListener {
            LocaleHelper.setLocale(this, "te")
            recreate()
        }

        findViewById<View>(R.id.header).setOnClickListener {
            lifecycleScope.launch {
                CustomerRepository().loadTestData()
                Toast.makeText(this@DashboardActivity, "Test Data Loaded", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.layoutPaid).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "PAID"))
        }
        findViewById<View>(R.id.layoutUnpaid).setOnClickListener {
            startActivity(Intent(this, CustomerListActivity::class.java).putExtra("FILTER_TYPE", "UNPAID"))
        }
    }

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
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val todayStartMs = cal.timeInMillis

                val todaySnap = db.collection("payments").whereGreaterThanOrEqualTo("timestamp", todayStartMs).get().await()
                val customerSnap = db.collection("customers").get().await()

                val todaySum = todaySnap.documents.sumOf { it.getDouble("paid") ?: 0.0 }
                val outstandingSum = customerSnap.documents.sumOf { (it.get("pendingAmount") as? Number)?.toDouble() ?: 0.0 }
                val baseSum = customerSnap.documents.sumOf { (it.get("baseAmount") as? Number)?.toDouble() ?: 0.0 }

                tvTodayAmount.text = formatCurrency(todaySum)
                tvTotalOutstanding.text = formatCurrency(outstandingSum + baseSum)

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
                var active = 0
                for (doc in snapshot) {
                    val status = doc.getString("status") ?: "unpaid"
                    val conn = doc.getString("Connection Status") ?: "active"
                    if (conn.equals("active", true)) active++
                    if (status.equals("paid", true)) paid++ else unpaid++
                }
                tvPaidCount.text = paid.toString()
                tvUnpaidCount.text = unpaid.toString()
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

    private fun bounceAndNavigate(view: View, action: () -> Unit) {
        val bounce = AnimationUtils.loadAnimation(this, R.anim.button_bounce)
        bounce.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(a: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
            override fun onAnimationEnd(a: android.view.animation.Animation?) { action() }
        })
        view.startAnimation(bounce)
    }

    private fun setupTactileTouch(vararg views: View) {
        for (view in views) {
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
                false
            }
        }
    }
    
    // Legacy / Mock fields for logic compatibility
    private fun checkBluetoothPermissions() {}
    private fun showPairedPrintersDialog() {}
    private fun showLogoutDialog() {
        getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
