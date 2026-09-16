package com.saimega.vinayakacablenetwork

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class DataImportActivity : BaseActivity() {

    private lateinit var btnPaste: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private val repository = CustomerRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_import)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        btnPaste = findViewById(R.id.btnPasteData)
        progressBar = findViewById(R.id.progressBar)

        btnPaste.setOnClickListener {
            handleClipboardImport()
        }
    }

    private fun handleClipboardImport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""

        if (text.isEmpty()) {
            Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val lines = text.split("\n").filter { it.isNotBlank() }
        val customers = mutableListOf<CustomerModel>()

        try {
            for (line in lines) {
                val parts = line.split("\t")
                if (parts.size >= 6) {
                    val teluguName = parts[0].trim()
                    val englishName = parts[1].trim()
                    val id = parts[2].trim()
                    val total = parts[3].trim().toDoubleOrNull() ?: 0.0
                    val paid = parts[4].trim().toDoubleOrNull() ?: 0.0
                    val pending = parts[5].trim().toDoubleOrNull() ?: 0.0

                    customers.add(
                        CustomerModel(
                            id = id,
                            name = englishName,
                            teluguName = teluguName,
                            baseAmount = total,
                            pendingAmount = pending,
                            status = if (pending <= 0) "paid" else if (paid > 0) "partial" else "unpaid",
                            connectionStatus = "active",
                            lastPaidMonth = "2024-05"
                        )
                    )
                }
            }

            if (customers.isNotEmpty()) {
                importToFirebase(customers)
            } else {
                Toast.makeText(this, getString(R.string.no_valid_customer_data), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_parsing_data, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun importToFirebase(list: List<CustomerModel>) {
        progressBar.visibility = View.VISIBLE
        btnPaste.isEnabled = false

        lifecycleScope.launch {
            try {
                repository.importCustomerBatch(list)
                Toast.makeText(this@DataImportActivity, getString(R.string.import_success_format, list.size), Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@DataImportActivity, getString(R.string.import_failed_prefix, e.message), Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
                btnPaste.isEnabled = true
            }
        }
    }
}
