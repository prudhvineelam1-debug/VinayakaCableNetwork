import os

replacements = {
    'CollectorDashboardActivity.kt': [
        ('etSearchSeries.error = "Enter series number"', 'etSearchSeries.error = getString(R.string.enter_series_number)'),
        ('btnSearch.text = "Searching..."', 'btnSearch.text = getString(R.string.searching)'),
        ('btnSearch.text = "Search"', 'btnSearch.text = getString(R.string.search)'),
        ('etSearchSeries.error = "Customer not found"', 'etSearchSeries.error = getString(R.string.customer_not_found)'),
        ('Toast.makeText(this, "Customer not found", Toast.LENGTH_SHORT).show()', 'Toast.makeText(this, getString(R.string.customer_not_found), Toast.LENGTH_SHORT).show()'),
        ('Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()', 'Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_SHORT).show()'),
        ('tvCustomerStatus.text = "PAID"', 'tvCustomerStatus.text = getString(R.string.paid_caps)'),
        ('tvCustomerStatus.text = "UNPAID"', 'tvCustomerStatus.text = getString(R.string.unpaid_caps)'),
        ('tvCustomerName.text = name', 'tvCustomerName.text = name'), # Unchanged
        ('tvFinalBill.text = "Final Bill ₹${finalBill.toInt()}"', 'tvFinalBill.text = getString(R.string.final_bill_amount, String.format("%.2f", finalBill))')
    ],
    'CustomerAdapter.kt': [
        ('binding.tvStatus.text = "PAID"', 'binding.tvStatus.text = binding.root.context.getString(R.string.paid_caps)'),
        ('binding.tvStatus.text = "UNPAID"', 'binding.tvStatus.text = binding.root.context.getString(R.string.unpaid_caps)'),
        ('binding.tvSeries.text = "Series: ${item.id}"', 'binding.tvSeries.text = binding.root.context.getString(R.string.series_format, item.id)'),
        ('binding.tvAmount.text = "₹${item.finalBill?.toInt() ?: 0}"', 'binding.tvAmount.text = binding.root.context.getString(R.string.rupee_value, String.format("%.2f", item.finalBill ?: 0.0))')
    ],
    'CustomerDetailsActivity.kt': [
        ('tvFinalBill.text = "Final Bill   ₹${finalBill.toInt()}"', 'tvFinalBill.text = getString(R.string.final_bill_value, String.format("%.2f", finalBill))'),
        ('tvBaseAmount.text = "Base Amount: ₹${base.toInt()}"', 'tvBaseAmount.text = getString(R.string.base_amount_value, String.format("%.2f", base))'),
        ('tvPreviousDue.text = "Previous Due: ₹${prevDue.toInt()}"', 'tvPreviousDue.text = getString(R.string.previous_due_value, String.format("%.2f", prevDue))')
    ],
    'DashboardActivity.kt': [
        ('tvTodayAmount.text = "₹ …"', 'tvTodayAmount.text = getString(R.string.rupee_placeholder)'),
        ('tvMonthAmount.text = "₹ …"', 'tvMonthAmount.text = getString(R.string.rupee_placeholder)'),
        ('tvTodayAmount.text = "₹ —"', 'tvTodayAmount.text = getString(R.string.rupee_placeholder)'),
        ('tvMonthAmount.text = "₹ —"', 'tvMonthAmount.text = getString(R.string.rupee_placeholder)'),
        ('"₹ ${String.format("%.2f", amount)}"', 'getString(R.string.rupee_value, String.format("%.2f", amount))')
    ],
    'NewCustomerActivity.kt': [
        ('Toast.makeText(this, "Please enter all required fields", Toast.LENGTH_SHORT).show()', 'Toast.makeText(this, "Please enter all required fields", Toast.LENGTH_SHORT).show()'),
    ]
}

src_dir = '/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/java/com/saimega/vinayakacablenetwork'

for file_name, reps in replacements.items():
    file_path = os.path.join(src_dir, file_name)
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            content = f.read()
        for old, new in reps:
            content = content.replace(old, new)
        with open(file_path, 'w') as f:
            f.write(content)

print("Kotlin files updated.")
