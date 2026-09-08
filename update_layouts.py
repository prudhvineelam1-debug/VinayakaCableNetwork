import os
import re

replacements = {
    'activity_receipt.xml': [
        ('android:text="Payment Receipt"', 'android:text="@string/payment_receipt"'),
        ('android:text="VINAYAKA CABLE NETWORK"', 'android:text="@string/business_name_caps"'),
        ('android:text="Customer Name"', 'android:text="@string/name_label"'),
        ('android:text="—"', 'android:text="@string/dash"'),
        ('android:text="Series Number"', 'android:text="@string/series_number_label"'),
        ('android:text="Date"', 'android:text="@string/date_label"'),
        ('android:text="Base Amount"', 'android:text="@string/base_amount_label"'),
        ('android:text="₹ 0"', 'android:text="@string/rupee_placeholder"'),
        ('android:text="Extra Charges"', 'android:text="@string/extra_charges_label"'),
        ('android:text="Amount Paid"', 'android:text="@string/amount_paid_label"'),
        ('android:text="Payment Mode"', 'android:text="@string/payment_mode_label"'),
        ('android:text="✅  Payment Confirmed"', 'android:text="@string/payment_confirmed"'),
        ('android:text="Thank you for your prompt payment!"', 'android:text="@string/thank_you_payment"'),
        ('android:text="Print Receipt"', 'android:text="@string/print_receipt"'),
        ('android:text="Share Online"', 'android:text="@string/share_online"'),
    ],
    'activity_customer_details.xml': [
        ('android:text="Customer Details"', 'android:text="@string/customer_details_title"'),
        ('android:text="CUSTOMER INFO"', 'android:text="@string/customer_info_section"'),
        ('android:text="Full Name"', 'android:text="@string/full_name_label"'),
        ('android:text="Series Number"', 'android:text="@string/series_number_label"'),
        ('android:text="BILLING SUMMARY"', 'android:text="@string/billing_summary_section"'),
        ('android:text="Base Amount: ₹0"', 'android:text="@string/base_amount_value"'),
        ('android:text="Previous Due: ₹0"', 'android:text="@string/previous_due_value"'),
        ('android:text="Final Bill   ₹0"', 'android:text="@string/final_bill_value"'),
        ('android:text="Enter amount paid to see balance"', 'android:text="@string/enter_amount_hint"'),
        ('android:text="Submit Payment"', 'android:text="@string/submit_payment"'),
        ('android:text="View / Share Receipt"', 'android:text="@string/view_share_receipt"'),
    ],
    'item_report.xml': [
        ('android:text="Name"', 'android:text="@string/name_label"'),
        ('android:text="Series Number"', 'android:text="@string/series_number_label"'),
        ('android:text="₹100"', 'android:text="@string/rupee_100"'),
        ('android:text="Cash"', 'android:text="@string/cash_label"'),
    ],
    'activity_collector_dashboard.xml': [
        ('android:text="Vinayaka Cable Network"', 'android:text="@string/vinayaka_cable_network"'),
        ('android:text="Search Customer"', 'android:text="@string/search_customer"'),
        ('android:text="Search"', 'android:text="@string/search"'),
        ('android:text="Customer Details"', 'android:text="@string/customer_details_title"'),
        ('android:text="Name"', 'android:text="@string/name_label"'),
        ('android:text="Final Bill ₹0"', 'android:text="@string/final_bill_amount"'),
        ('android:text="Submit Payment"', 'android:text="@string/submit_payment"'),
    ],
    'activity_customer_list.xml': [
        ('android:text="Download PDF"', 'android:text="@string/download_pdf"'),
        ('android:text="Export CSV"', 'android:text="@string/export_csv"'),
        ('android:text="Enter a Customer ID or Phone Number to search"', 'android:text="@string/search_customer_hint"'),
    ],
    'activity_dashboard.xml': [
        ('android:text="₹ —"', 'android:text="@string/rupee_placeholder"'),
        ('android:text="customers"', 'android:text="@string/customers_label"'),
        ('android:text="0"', 'android:text="@string/zero"'),
    ]
}

layout_dir = '/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/layout'

for file_name, reps in replacements.items():
    file_path = os.path.join(layout_dir, file_name)
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            content = f.read()
        for old, new in reps:
            content = content.replace(old, new)
        with open(file_path, 'w') as f:
            f.write(content)

print("Layout files updated.")
