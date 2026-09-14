package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.util.*

class TodayCollectionActivity : BaseActivity() {

    private lateinit var tvTotalCollection: TextView
    private lateinit var tvPaymentCount: TextView
    private lateinit var tvCashAmount: TextView
    private lateinit var tvOnlineAmount: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var adapter: PaymentHistoryAdapter

    private val db = FirebaseFirestore.getInstance()
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_today_collection)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        tvTotalCollection = findViewById(R.id.tvTotalCollection)
        tvPaymentCount = findViewById(R.id.tvPaymentCount)
        tvCashAmount = findViewById(R.id.tvCashAmount)
        tvOnlineAmount = findViewById(R.id.tvOnlineAmount)
        rvTransactions = findViewById(R.id.rvTransactions)

        rvTransactions.layoutManager = LinearLayoutManager(this)
        adapter = PaymentHistoryAdapter(emptyList(), canEdit = false) { _, _ ->
            // Open payment details if needed
        }
        rvTransactions.adapter = adapter

        fetchTodayCollection()
    }

    private fun fetchTodayCollection() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayStartMs = cal.timeInMillis

        db.collection("payments")
            .whereGreaterThanOrEqualTo("timestamp", todayStartMs)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                val payments = snapshot?.toObjects(PaymentModel::class.java) ?: emptyList()
                adapter.updateData(payments)

                var total = 0.0
                var cash = 0.0
                var online = 0.0

                for (p in payments) {
                    total += p.paid
                    if (p.paymentMode.equals("Cash", true)) {
                        cash += p.paid
                    } else {
                        online += p.paid
                    }
                }

                tvTotalCollection.text = currencyFormatter.format(total)
                tvPaymentCount.text = getString(R.string.payments_count_format, payments.size)
                tvCashAmount.text = currencyFormatter.format(cash)
                tvOnlineAmount.text = currencyFormatter.format(online)
            }
    }
}
