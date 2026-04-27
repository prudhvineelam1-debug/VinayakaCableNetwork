package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.pdf.PdfDocument
import android.graphics.Paint

class ReportActivity : AppCompatActivity() {

    private lateinit var tvTotal: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvCash: TextView
    private lateinit var tvUPI: TextView
    private lateinit var recyclerReport: RecyclerView

    private lateinit var btnExcel: Button
    private lateinit var btnPdf: Button

    private val db = FirebaseFirestore.getInstance()
    private val reportList = mutableListOf<ReportItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        tvTotal = findViewById(R.id.tvTotal)
        tvCount = findViewById(R.id.tvCount)
        tvCash = findViewById(R.id.tvCash)
        tvUPI = findViewById(R.id.tvUPI)
        recyclerReport = findViewById(R.id.recyclerReport)

        btnExcel = findViewById(R.id.btnExcel)
        btnPdf = findViewById(R.id.btnPdf)

        recyclerReport.layoutManager = LinearLayoutManager(this)

        btnExcel.setOnClickListener { exportToExcel() }
        btnPdf.setOnClickListener { exportToPDF() }

        loadTodayReport()
    }

    private fun loadTodayReport() {

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date())

        var total = 0.0
        var count = 0
        var cashTotal = 0.0
        var upiTotal = 0.0

        reportList.clear()

        db.collection("customers")
            .get()
            .addOnSuccessListener { customers ->

                var pendingQueries = customers.size()

                if (pendingQueries == 0) {
                    showResult(total, count, cashTotal, upiTotal)
                    return@addOnSuccessListener
                }

                for (customer in customers) {

                    val name = customer.getString("name") ?: ""
                    val series = customer.id

                    db.collection("customers")
                        .document(series)
                        .collection("payments")
                        .whereEqualTo("date", today)
                        .get()
                        .addOnSuccessListener { payments ->

                            for (doc in payments) {

                                val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                                val mode = doc.getString("mode") ?: ""
                                val upiNumber = doc.getString("paymentNumber") ?: ""

                                total += amount
                                count++

                                if (mode == "Cash") cashTotal += amount
                                else upiTotal += amount

                                reportList.add(
                                    ReportItem(name, series, amount, mode, upiNumber)
                                )
                            }

                            pendingQueries--

                            if (pendingQueries == 0) {
                                showResult(total, count, cashTotal, upiTotal)
                            }
                        }
                        .addOnFailureListener {
                            pendingQueries--
                            if (pendingQueries == 0) {
                                showResult(total, count, cashTotal, upiTotal)
                            }
                        }
                }
            }
    }

    private fun showResult(
        total: Double,
        count: Int,
        cash: Double,
        upi: Double
    ) {
        tvTotal.text = "Total: ₹$total"
        tvCount.text = "Payments: $count"
        tvCash.text = "Cash: ₹$cash"
        tvUPI.text = "UPI: ₹$upi"

        recyclerReport.adapter = ReportAdapter(reportList)
    }

    // =========================
    // 📊 CSV (Excel)
    // =========================
    private fun exportToExcel() {
        try {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Daily_Report_${System.currentTimeMillis()}.csv"
            )

            val writer = file.printWriter()

            writer.println("Name,Series,Amount,Mode,UPI Number")

            for (item in reportList) {
                writer.println(
                    "${item.name},${item.series},${item.amount},${item.mode},${item.upiNumber}"
                )
            }

            writer.close()

            Toast.makeText(this, "Excel saved in Downloads", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================
    // 📄 PDF (FIXED + CLEAN)
    // =========================
    private fun exportToPDF() {

        val pdf = PdfDocument()
        val paint = Paint()

        val pageInfo = PdfDocument.PageInfo.Builder(842, 1191, 1).create()
        val page = pdf.startPage(pageInfo)

        val canvas = page.canvas

        var y = 60

        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Daily Report", 300f, y.toFloat(), paint)

        y += 60
        paint.textSize = 16f
        paint.isFakeBoldText = false

        for (item in reportList) {

            val line = if (item.mode == "UPI") {
                "${item.name} | ${item.series} | ₹${item.amount} | UPI: ${item.upiNumber}"
            } else {
                "${item.name} | ${item.series} | ₹${item.amount} | Cash"
            }

            canvas.drawText(line, 40f, y.toFloat(), paint)
            y += 30
        }

        pdf.finishPage(page)

        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Daily_Report_${System.currentTimeMillis()}.pdf"
        )

        pdf.writeTo(FileOutputStream(file))
        pdf.close()

        Toast.makeText(this, "PDF saved in Downloads", Toast.LENGTH_LONG).show()
    }
}