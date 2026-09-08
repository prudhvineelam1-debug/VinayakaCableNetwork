package com.saimega.vinayakacablenetwork

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*

class ComplaintAdapter(
    private var list: List<ComplaintModel>,
    private val onItemClick: (ComplaintModel) -> Unit
) : RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvCustomerName)
        val tvDesc: TextView = view.findViewById(R.id.tvDescription)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvPriority: TextView = view.findViewById(R.id.tvPriority)
        val chipStatus: Chip = view.findViewById(R.id.chipStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_complaint, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvName.text = item.customerName
        holder.tvDesc.text = item.description
        holder.tvPriority.text = item.priority

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(item.timestamp))

        holder.chipStatus.text = item.status
        val statusColor = when (item.status) {
            "NEW" -> "#EF4444"
            "IN_PROGRESS" -> "#3B82F6"
            "RESOLVED" -> "#10B981"
            "CLOSED" -> "#6B7280"
            else -> "#9CA3AF"
        }
        holder.chipStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(statusColor))
        holder.chipStatus.setTextColor(Color.WHITE)

        val priorityColor = when (item.priority) {
            "URGENT" -> "#DC2626"
            "HIGH" -> "#F97316"
            "NORMAL" -> "#3B82F6"
            else -> "#10B981"
        }
        holder.tvPriority.setTextColor(Color.parseColor(priorityColor))

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = list.size

    fun updateList(newList: List<ComplaintModel>) {
        list = newList
        notifyDataSetChanged()
    }
}
