package com.saimega.vinayakacablenetwork

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 🔹 Data Model (UPDATED)
data class ReportItem(
    val name: String,
    val series: String,
    val amount: Double,
    val mode: String,
    val upiNumber: String   // ✅ NEW FIELD
)

// 🔹 Adapter
class ReportAdapter(private val list: List<ReportItem>) :
    RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvSeries: TextView = view.findViewById(R.id.tvSeries)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvMode: TextView = view.findViewById(R.id.tvMode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.tvName.text = item.name
        holder.tvSeries.text = item.series
        holder.tvAmount.text = "₹${item.amount}"

        // ✅ SHOW UPI NUMBER IF AVAILABLE
        holder.tvMode.text = if (item.mode == "UPI" && item.upiNumber.isNotEmpty()) {
            "UPI (${item.upiNumber})"
        } else {
            item.mode
        }
    }
}