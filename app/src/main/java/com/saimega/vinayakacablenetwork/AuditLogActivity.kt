package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLogActivity : BaseActivity() {

    private lateinit var auditListContainer: LinearLayout
    private lateinit var tvEmptyState: TextView
    private lateinit var btnClearAll: MaterialButton
    private val auditLogRepository = AuditLogRepository()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audit_log)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        auditListContainer = findViewById(R.id.auditListContainer)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnClearAll = findViewById(R.id.btnClearAll)

        btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_audit_log_title)
                .setMessage(R.string.clear_audit_log_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        val success = auditLogRepository.clearAll()
                        if (success) {
                            Toast.makeText(this@AuditLogActivity, getString(R.string.audit_log_cleared), Toast.LENGTH_SHORT).show()
                            loadEntries()
                        }
                    }
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEntries()
    }

    private fun loadEntries() {
        lifecycleScope.launch {
            val entries = auditLogRepository.listEntries()
            renderEntries(entries)
        }
    }

    private fun renderEntries(entries: List<AuditLogEntry>) {
        auditListContainer.removeAllViews()
        tvEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        for (entry in entries) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_audit_log_row, auditListContainer, false)
            row.findViewById<TextView>(R.id.tvAuditAction).text = getString(R.string.audit_action_format, entry.action, entry.targetName)
            row.findViewById<TextView>(R.id.tvAuditActor).text = getString(R.string.audit_actor_format, entry.actorUsername, entry.actorRole)
            row.findViewById<TextView>(R.id.tvAuditTimestamp).text = dateFormat.format(Date(entry.timestamp))
            row.findViewById<View>(R.id.btnDeleteEntry).setOnClickListener {
                lifecycleScope.launch {
                    if (auditLogRepository.deleteEntry(entry.id)) {
                        loadEntries()
                    }
                }
            }
            auditListContainer.addView(row)
        }
    }
}
