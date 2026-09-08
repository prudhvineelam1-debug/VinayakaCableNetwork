import os

strings_en = """
    <!-- More Remaining Strings -->
    <string name="login_text">Login</string>
    <string name="financial_report">Financial Report</string>
    <string name="monthly_report">Monthly Report</string>
    <string name="start_date">Start Date</string>
    <string name="end_date">End Date</string>
    <string name="export_pdf">Export PDF</string>
    <string name="day_report">Day Report</string>
    <string name="total_collected">Total Collected: ₹%1$s</string>
    <string name="payments_count">Payments: %1$d</string>
    <string name="cash_amount">Cash: ₹%1$s</string>
    <string name="upi_amount">UPI / Online: ₹%1$s</string>
    <string name="no_payments_found">No payments found for this period.</string>
"""

strings_te = """
    <!-- More Remaining Strings -->
    <string name="login_text">లాగిన్</string>
    <string name="financial_report">ఆర్థిక నివేదిక</string>
    <string name="monthly_report">నెలవారీ నివేదిక</string>
    <string name="start_date">ప్రారంభ తేదీ</string>
    <string name="end_date">ముగింపు తేదీ</string>
    <string name="export_pdf">పిడిఎఫ్ ఎగుమతి చేయండి</string>
    <string name="day_report">రోజు నివేదిక</string>
    <string name="total_collected">సేకరించిన మొత్తం: ₹%1$s</string>
    <string name="payments_count">చెల్లింపులు: %1$d</string>
    <string name="cash_amount">నగదు: ₹%1$s</string>
    <string name="upi_amount">యుపిఐ / ఆన్‌లైన్: ₹%1$s</string>
    <string name="no_payments_found">ఈ కాలానికి చెల్లింపులు కనుగొనబడలేదు.</string>
"""

def append_strings(filepath, strings):
    with open(filepath, 'r') as f:
        content = f.read()
    content = content.replace("</resources>", f"{strings}\n</resources>")
    with open(filepath, 'w') as f:
        f.write(content)

append_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values/strings.xml', strings_en)
append_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values-te/strings.xml', strings_te)

replacements_layout = {
    'activity_login.xml': [
        ('android:text="Login"', 'android:text="@string/login_text"'),
    ],
    'activity_report.xml': [
        ('android:text="Financial Report"', 'android:text="@string/financial_report"'),
        ('android:text="Monthly Report"', 'android:text="@string/monthly_report"'),
        ('android:text="Start Date"', 'android:text="@string/start_date"'),
        ('android:text="End Date"', 'android:text="@string/end_date"'),
        ('android:text="Export CSV"', 'android:text="@string/export_csv"'),
        ('android:text="Export PDF"', 'android:text="@string/export_pdf"'),
        ('android:text="Day Report"', 'android:text="@string/day_report"'),
        ('android:text="Total Collected: ₹0"', 'android:text="@string/total_collected"'), # We'll replace the hardcoded "₹0" in kotlin with formatted strings, wait, no in XML it's fine. Wait, in XML it shouldn't contain parameters like %1$s, we should just use "android:text" or "tools:text".
        ('android:text="Payments: 0"', 'android:text="@string/payments_count"'),
        ('android:text="Cash: ₹0"', 'android:text="@string/cash_amount"'),
        ('android:text="UPI / Online: ₹0"', 'android:text="@string/upi_amount"'),
        ('android:text="No payments found for this period."', 'android:text="@string/no_payments_found"'),
    ]
}
layout_dir = '/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/layout'
for file_name, reps in replacements_layout.items():
    file_path = os.path.join(layout_dir, file_name)
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            content = f.read()
        for old, new in reps:
            content = content.replace(old, new)
        with open(file_path, 'w') as f:
            f.write(content)

replacements_kotlin = {
    'ReportActivity.kt': [
        ('tvTotalAmount.text = "Total Collected: ₹${total.toInt()}"', 'tvTotalAmount.text = getString(R.string.total_collected, String.format("%.2f", total))'),
        ('tvPaymentCount.text = "Payments: ${payments.size}"', 'tvPaymentCount.text = getString(R.string.payments_count, payments.size)'),
        ('tvCashAmount.text = "Cash: ₹${cash.toInt()}"', 'tvCashAmount.text = getString(R.string.cash_amount, String.format("%.2f", cash))'),
        ('tvUpiAmount.text = "UPI / Online: ₹${upi.toInt()}"', 'tvUpiAmount.text = getString(R.string.upi_amount, String.format("%.2f", upi))'),
        ('tvReportTitle.text = "Monthly Report (${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(start.time)})"', 'tvReportTitle.text = getString(R.string.monthly_report) + " (${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(start.time)})"'),
        ('tvReportTitle.text = "Day Report (${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(start.time)})"', 'tvReportTitle.text = getString(R.string.day_report) + " (${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(start.time)})"'),
        ('Toast.makeText(this, "Select start date first", Toast.LENGTH_SHORT).show()', 'Toast.makeText(this, "Select start date first", Toast.LENGTH_SHORT).show()'), # Will address this manually
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

