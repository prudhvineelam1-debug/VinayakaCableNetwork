import os

strings_en = """
    <!-- More Remaining Strings -->
    <string name="paid_with_tick">✅ Paid</string>
    <string name="for_this_month">For this month: ₹%1$s</string>
    <string name="download_type_pdf">Download %1$s PDF</string>
    <string name="from_date">From: %1$s</string>
"""

strings_te = """
    <!-- More Remaining Strings -->
    <string name="paid_with_tick">✅ చెల్లించబడింది</string>
    <string name="for_this_month">ఈ నెల కోసం: ₹%1$s</string>
    <string name="download_type_pdf">%1$s పిడిఎఫ్ డౌన్‌లోడ్ చేయండి</string>
    <string name="from_date">నుండి: %1$s</string>
"""

def append_strings(filepath, strings):
    with open(filepath, 'r') as f:
        content = f.read()
    content = content.replace("</resources>", f"{strings}\n</resources>")
    with open(filepath, 'w') as f:
        f.write(content)

append_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values/strings.xml', strings_en)
append_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values-te/strings.xml', strings_te)

replacements_kotlin = {
    'CustomerAdapter.kt': [
        ('binding.tvAmount.text = "₹${item.totalBill.toInt()}"', 'binding.tvAmount.text = binding.root.context.getString(R.string.rupee_value, item.totalBill.toInt().toString())')
    ],
    'ReportAdapter.kt': [
        ('holder.tvSeries.text = "Series: ${p.customerId.ifEmpty { \\"N/A\\" }}"', 'holder.tvSeries.text = holder.itemView.context.getString(R.string.series_format, p.customerId.ifEmpty { "N/A" })'),
        ('holder.tvAmount.text = "₹${p.paid.toLong()}"', 'holder.tvAmount.text = holder.itemView.context.getString(R.string.rupee_value, p.paid.toLong().toString())')
    ],
    'CustomerDetailsActivity.kt': [
        ('tvBaseAmount.text = "Base Amount: ₹$baseAmount"', 'tvBaseAmount.text = getString(R.string.base_amount_value, baseAmount.toString())'),
        ('tvPendingAmount.text = "Previous Due: ₹$previous"', 'tvPendingAmount.text = getString(R.string.previous_due_value, previous.toString())'),
        ('tvFinalBill.text = "✅ Paid"', 'tvFinalBill.text = getString(R.string.paid_with_tick)'),
        ('tvRemainingBalance.text = "For this month: ₹$baseAmountStr"', 'tvRemainingBalance.text = getString(R.string.for_this_month, baseAmountStr)')
    ],
    'CustomerListActivity.kt': [
        ('btnDownload.text = "Download $type PDF"', 'btnDownload.text = getString(R.string.download_type_pdf, type)')
    ],
    'ReportActivity.kt': [
        ('btnStartDate.text = "Start Date"', 'btnStartDate.text = getString(R.string.start_date)'),
        ('btnStartDate.text = "From: $startDateStr"', 'btnStartDate.text = getString(R.string.from_date, startDateStr)'),
        ('tvTotal.text = "Total Collected: ₹${grandTotal.toLong()}"', 'tvTotal.text = getString(R.string.total_collected, grandTotal.toLong().toString())'),
        ('tvCount.text = "Payments: ${paymentList.size}"', 'tvCount.text = getString(R.string.payments_count, paymentList.size)')
    ],
    'ReceiptActivity.kt': [
        ('tvReceiptExtraCharges.text = "₹ ${formatAmount(p.extraCharges)}"', 'tvReceiptExtraCharges.text = getString(R.string.rupee_value, formatAmount(p.extraCharges))')
    ]
}

src_dir = '/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/java/com/saimega/vinayakacablenetwork'
for file_name, reps in replacements_kotlin.items():
    file_path = os.path.join(src_dir, file_name)
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            content = f.read()
        for old, new in reps:
            content = content.replace(old, new)
        with open(file_path, 'w') as f:
            f.write(content)

print("Remaining kotlin files updated.")
