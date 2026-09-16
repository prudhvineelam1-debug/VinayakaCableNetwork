package com.saimega.vinayakacablenetwork

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saimega.vinayakacablenetwork.databinding.ItemCustomerCardBinding
import android.util.Log

/**
 * CustomerAdapter
 *
 * Uses [ListAdapter] + [DiffUtil.ItemCallback] for efficient, flicker-free updates.
 * Internally keeps a FULL list and a FILTERED list so search works instantly
 * without re-fetching from Firestore.
 *
 * Usage:
 *   adapter.submitFullList(viewModel.customers)  ← on LiveData update
 *   adapter.filter("search term")                ← on SearchView text change
 */
class CustomerAdapter(
    private val onItemClick: (CustomerModel) -> Unit
) : ListAdapter<CustomerModel, CustomerAdapter.CustomerViewHolder>(DIFF_CALLBACK) {

    // ── Full (unfiltered) backing list ────────────────────────────────────────
    private var fullList: List<CustomerModel> = emptyList()

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CustomerModel>() {
            // Two items represent the same customer iff their IDs match (Series number)
            override fun areItemsTheSame(old: CustomerModel, new: CustomerModel) =
                old.id == new.id

            // Check for data changes — data class equals() handles this perfectly
            override fun areContentsTheSame(old: CustomerModel, new: CustomerModel) =
                old == new
        }

        // Avatar colours cycling through a branded palette
        private val AVATAR_COLORS = listOf(
            "#2E7D32", "#1565C0", "#6A1B9A", "#BF360C",
            "#00695C", "#AD1457", "#0277BD", "#558B2F"
        )
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────
    inner class CustomerViewHolder(
        private val binding: ItemCustomerCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CustomerModel) {
            // Debug log – high visibility
            Log.e("DATABASE_CHECK", "Customer: ${item.name} | Raw Status: ${item.status}")

            // Name
            binding.tvCustomerName.text = item.name

            // Series
            binding.tvSeries.text = binding.root.context.getString(R.string.series_format, item.id)

            // Determine UI state from Firestore status field — never from local arithmetic.
            // DC rule: pendingAmount is the authoritative bill; we never re-add baseAmount.
            when (item.status.lowercase()) {
                "paid" -> {
                    binding.tvStatus.text = binding.root.context.getString(R.string.paid_caps)
                    binding.tvStatus.setTextColor(Color.WHITE)
                    binding.tvStatus.backgroundTintList =
                        ColorStateList.valueOf(Color.parseColor("#2E7D32")) // green
                    binding.tvAmount.visibility = android.view.View.VISIBLE
                    binding.tvAmountBreakdown.visibility = android.view.View.GONE
                    binding.tvAmount.text = if (item.baseAmount > 0) "₹${item.baseAmount.toInt()}" else "₹0"
                }
                "partial" -> {
                    binding.tvStatus.text = binding.root.context.getString(R.string.partial_caps)
                    binding.tvStatus.setTextColor(Color.WHITE)
                    binding.tvStatus.backgroundTintList =
                        ColorStateList.valueOf(Color.parseColor("#F57C00")) // orange
                    
                    // DC Rule: pendingAmount is already the full outstanding bill — don't re-add baseAmount.
                    val totalDue = item.pendingAmount
                    binding.tvAmount.visibility = android.view.View.VISIBLE
                    binding.tvAmount.text = binding.root.context.getString(R.string.due_amount_format, totalDue.toInt())

                    binding.tvAmountBreakdown.visibility = android.view.View.VISIBLE
                    binding.tvAmountBreakdown.text = binding.root.context.getString(R.string.pending_amount_format, item.pendingAmount.toInt())
                }
                else -> {
                    // unpaid
                    binding.tvStatus.text = binding.root.context.getString(R.string.unpaid_caps)
                    binding.tvStatus.setTextColor(Color.WHITE)
                    binding.tvStatus.backgroundTintList =
                        ColorStateList.valueOf(Color.parseColor("#C62828")) // red
                    
                    // DC Rule: pendingAmount is already the full outstanding bill — don't re-add baseAmount.
                    val totalDue = item.pendingAmount
                    binding.tvAmount.visibility = android.view.View.VISIBLE
                    binding.tvAmount.text = binding.root.context.getString(R.string.total_due_amount_format, totalDue.toInt())

                    binding.tvAmountBreakdown.visibility = android.view.View.VISIBLE
                    binding.tvAmountBreakdown.text = binding.root.context.getString(R.string.pending_amount_format, item.pendingAmount.toInt())
                }
            }

            // Avatar: first letter of name, colour determined by hashCode for consistency
            val letter = item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.tvAvatar.text = letter
            val color = AVATAR_COLORS[Math.abs(item.id.hashCode()) % AVATAR_COLORS.size]
            binding.tvAvatar.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(color))

            // Click
            binding.root.setOnClickListener { onItemClick(item) }

            // Deactivated Badge - only show if past 10th and inactive, or manually deactivated
            val cal = java.util.Calendar.getInstance()
            val dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
            val currentMonth = sdf.format(cal.time)
            cal.add(java.util.Calendar.MONTH, -1)
            val lastMonth = sdf.format(cal.time)

            val isPaidThisMonth = (item.lastPaidMonth == currentMonth)
            val isPaidLastMonth = (item.lastPaidMonth == lastMonth)
            
            val shouldBeActive = if (dayOfMonth <= 10) {
                isPaidLastMonth || isPaidThisMonth
            } else {
                isPaidThisMonth
            }

            if (!shouldBeActive) {
                binding.chipDeactivated.visibility = android.view.View.VISIBLE
                val deactivatedText = if (item.deactivatedMonth != null) 
                    "Deactivated in: ${item.deactivatedMonth}" 
                    else "Deactivated (Unpaid)"
                binding.chipDeactivated.text = deactivatedText
            } else {
                binding.chipDeactivated.visibility = android.view.View.GONE
            }
        }
    }

    // ── ListAdapter overrides ─────────────────────────────────────────────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called when the ViewModel emits a new page.
     * Stores the full list and re-applies any active search filter.
     * Returns the resulting (post-filter) item count so callers can drive
     * empty-state visibility without racing ListAdapter's async DiffUtil dispatch.
     */
    fun submitFullList(newList: List<CustomerModel>): Int {
        fullList = newList
        return applyFilter(currentQuery)
    }

    /**
     * Instantly filters the displayed list without touching Firestore.
     * Returns the resulting item count (see [submitFullList]).
     */
    fun filter(query: String): Int {
        currentQuery = query
        return applyFilter(query)
    }

    /**
     * Sets the status filter (paid/unpaid/partial/all) applied when there is
     * no active search query. Returns the resulting item count.
     */
    fun setStatusFilter(status: String): Int {
        statusFilter = status.lowercase()
        return applyFilter(currentQuery)
    }

    private var currentQuery = ""
    private var statusFilter = "all"

    private fun applyFilter(query: String): Int {
        val result = if (query.isBlank()) {
            // No active search — browse the status-filtered list (e.g. "Paid" from a stat card).
            val byStatus = when (statusFilter) {
                "paid" -> fullList.filter { it.status.equals("paid", true) }
                "unpaid" -> fullList.filter { it.status.equals("unpaid", true) }
                "partial" -> fullList.filter { it.status.equals("partial", true) }
                else -> fullList
            }
            byStatus.sortedBy { it.name.lowercase() }
        } else {
            // Active search always searches every customer, regardless of the
            // status filter — typing a name/series number that belongs to a
            // different status than the current filter must still find it.
            val lower = query.lowercase()
            fullList.filter { c ->
                c.name.lowercase().contains(lower) ||
                c.id.lowercase().contains(lower)
            }.sortedWith(
                compareByDescending<CustomerModel> { it.name.lowercase().startsWith(lower) }
                    .thenBy { it.name.lowercase() }
            )
        }
        submitList(result)
        return result.size
    }
}
