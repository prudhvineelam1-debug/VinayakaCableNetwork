package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CustomerPaymentHistoryActivity : BaseActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var adapter: PaymentHistoryAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var pbLoadingMore: ProgressBar
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var cardSummary: MaterialCardView
    
    private lateinit var btnFilter: ImageButton
    private lateinit var chipClearFilter: Chip
    private lateinit var tvTotalPaid: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvLastDate: TextView

    private val db = FirebaseFirestore.getInstance()
    private var customerId: String = ""

    // Pagination & Filtering state
    private var filterStartDate: Long? = null
    private var filterEndDate: Long? = null
    private var lastVisibleDocument: DocumentSnapshot? = null
    private var isLoadingMore = false
    private var isLastPage = false
    private val paymentList = mutableListOf<PaymentModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_payment_history)

        customerId = intent.getStringExtra("CUSTOMER_ID") ?: ""
        if (customerId.isEmpty()) {
            Toast.makeText(this, "Customer ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        rvHistory = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        pbLoadingMore = findViewById(R.id.pbLoadingMore)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        cardSummary = findViewById(R.id.cardSummary)
        
        btnFilter = findViewById(R.id.btnFilter)
        chipClearFilter = findViewById(R.id.chipClearFilter)
        tvTotalPaid = findViewById(R.id.tvTotalPaid)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvLastDate = findViewById(R.id.tvLastDate)

        setupRecyclerView()
        setupListeners()
        
        resetAndFetch()
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        rvHistory.layoutManager = layoutManager
        adapter = PaymentHistoryAdapter(emptyList()) { paymentId, action ->
            val intent = Intent(this, ReceiptActivity::class.java).apply {
                putExtra("CUSTOMER_ID", customerId)
                putExtra("PAYMENT_ID", paymentId)
                putExtra("ACTION", action)
            }
            startActivity(intent)
        }
        rvHistory.adapter = adapter

        rvHistory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoadingMore && !isLastPage) {
                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                            fetchHistoryPage()
                        }
                    }
                }
            }
        })
    }

    private fun setupListeners() {
        btnFilter.setOnClickListener {
            val builder = MaterialDatePicker.Builder.dateRangePicker()
            builder.setTitleText("Select Date Range")
            val picker = builder.build()
            
            picker.addOnPositiveButtonClickListener { selection ->
                filterStartDate = selection.first
                filterEndDate = selection.second
                
                chipClearFilter.visibility = View.VISIBLE
                val startStr = android.text.format.DateFormat.format("dd MMM yy", filterStartDate!!)
                val endStr = android.text.format.DateFormat.format("dd MMM yy", filterEndDate!!)
                chipClearFilter.text = "Filtered: $startStr - $endStr"
                
                resetAndFetch()
            }
            picker.show(supportFragmentManager, picker.toString())
        }

        chipClearFilter.setOnClickListener {
            filterStartDate = null
            filterEndDate = null
            chipClearFilter.visibility = View.GONE
            resetAndFetch()
        }
    }

    private fun resetAndFetch() {
        lastVisibleDocument = null
        isLastPage = false
        paymentList.clear()
        adapter.updateData(ArrayList(paymentList))
        
        progressBar.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE
        cardSummary.visibility = View.GONE
        
        tvLastDate.text = "..."
        tvTotalCount.text = "..."
        tvTotalPaid.text = "..."
        
        fetchSummary()
        fetchHistoryPage()
    }

    private fun fetchSummary() {
        lifecycleScope.launch {
            try {
                var query = db.collection("payments").whereEqualTo("customerId", customerId)
                
                if (filterStartDate != null && filterEndDate != null) {
                    val endTs = filterEndDate!! + 86400000L - 1L // End of day
                    query = query.whereGreaterThanOrEqualTo("timestamp", filterStartDate!!)
                                 .whereLessThanOrEqualTo("timestamp", endTs)
                }
                
                val aggregateQuery = query.aggregate(AggregateField.sum("paid"), AggregateField.count())
                val snapshot = withContext(Dispatchers.IO) { aggregateQuery.get(AggregateSource.SERVER).await() }
                
                val totalPaid = (snapshot.get(AggregateField.sum("paid")) as? Number)?.toDouble() ?: 0.0
                val count = snapshot.count
                
                cardSummary.visibility = View.VISIBLE
                val totalPaidStr = if (totalPaid == totalPaid.toLong().toDouble()) totalPaid.toLong().toString() else String.format("%.2f", totalPaid)
                tvTotalPaid.text = "₹ $totalPaidStr"
                tvTotalCount.text = count.toString()
                
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchHistoryPage() {
        if (isLoadingMore || isLastPage) return
        isLoadingMore = true
        
        if (lastVisibleDocument != null) {
            pbLoadingMore.visibility = View.VISIBLE
        }
        
        lifecycleScope.launch {
            try {
                var query = db.collection("payments")
                    .whereEqualTo("customerId", customerId)
                    
                if (filterStartDate != null && filterEndDate != null) {
                    val endTs = filterEndDate!! + 86400000L - 1L // End of day
                    query = query.whereGreaterThanOrEqualTo("timestamp", filterStartDate!!)
                                 .whereLessThanOrEqualTo("timestamp", endTs)
                }
                
                query = query.orderBy("timestamp", Query.Direction.DESCENDING)
                             .limit(15)
                             
                if (lastVisibleDocument != null) {
                    query = query.startAfter(lastVisibleDocument!!)
                }
                
                val snapshot = withContext(Dispatchers.IO) { query.get().await() }
                
                if (snapshot.isEmpty) {
                    isLastPage = true
                    if (paymentList.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                        cardSummary.visibility = View.GONE
                    }
                } else {
                    lastVisibleDocument = snapshot.documents.last()
                    
                    for (doc in snapshot.documents) {
                        val p = PaymentModel(
                            paymentId = doc.id,
                            customerId = doc.getString("customerId") ?: "",
                            name = doc.getString("name") ?: "",
                            baseAmount = (doc.get("baseAmount") as? Number)?.toDouble() ?: 0.0,
                            extraCharges = (doc.get("extraCharges") as? Number)?.toDouble() ?: 0.0,
                            total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                            paid = (doc.get("paid") as? Number)?.toDouble() ?: 0.0,
                            remaining = (doc.get("remaining") as? Number)?.toDouble() ?: 0.0,
                            paymentMode = doc.getString("paymentMode") ?: "",
                            paymentNumber = doc.getString("paymentNumber") ?: "",
                            date = doc.getString("date") ?: "",
                            timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: 0L
                        )
                        paymentList.add(p)
                    }
                    
                    adapter.updateData(ArrayList(paymentList))
                    
                    // Update Last Date only on the first page
                    if (paymentList.size == snapshot.size()) {
                        tvLastDate.text = paymentList.first().date.ifEmpty { "N/A" }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CustomerPaymentHistoryActivity, "Error loading data", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingMore = false
                progressBar.visibility = View.GONE
                pbLoadingMore.visibility = View.GONE
            }
        }
    }
}
