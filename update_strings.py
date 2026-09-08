import re

strings_en = """
    <!-- Missing String Resources -->
    <string name="login_successful">Login Successful</string>
    <string name="invalid_credentials">Invalid Username or Password</string>
    <string name="enter_username">Enter Username</string>
    <string name="enter_password">Enter Password</string>
    <string name="password_too_short">Password must be at least 4 characters</string>

    <string name="vinayaka_cable_network">Vinayaka Cable Network</string>
    <string name="search_customer">Search Customer</string>
    <string name="searching">Searching...</string>
    <string name="enter_series_number">Enter series number</string>
    <string name="customer_not_found">Customer not found</string>
    <string name="error_prefix">Error: %1$s</string>
    <string name="customer_details_title">Customer Details</string>
    <string name="name_label">Name</string>
    <string name="final_bill_amount">Final Bill ₹%1$s</string>
    <string name="submit_payment">Submit Payment</string>

    <string name="customer_info_section">CUSTOMER INFO</string>
    <string name="full_name_label">Full Name</string>
    <string name="series_number_label">Series Number</string>
    <string name="billing_summary_section">BILLING SUMMARY</string>
    <string name="base_amount_value">Base Amount: ₹%1$s</string>
    <string name="previous_due_value">Previous Due: ₹%1$s</string>
    <string name="final_bill_value">Final Bill   ₹%1$s</string>
    <string name="enter_amount_hint">Enter amount paid to see balance</string>
    <string name="view_share_receipt">View / Share Receipt</string>

    <string name="download_pdf">Download PDF</string>
    <string name="export_csv">Export CSV</string>
    <string name="search_customer_hint">Enter a Customer ID or Phone Number to search</string>

    <string name="register_customer">Register Customer</string>
    <string name="register_customer_desc">Fill in the details below to add a new customer to the network.</string>
    <string name="customer_information">Customer Information</string>
    <string name="billing_details">Billing Details</string>
    <string name="new_customer_billing_note">New customers start with ₹0 previous due and are marked as \'unpaid\'.</string>
    <string name="save_customer">Save Customer</string>

    <string name="payment_receipt">Payment Receipt</string>
    <string name="business_name_caps">VINAYAKA CABLE NETWORK</string>
    <string name="dash">—</string>
    <string name="date_label">Date</string>
    <string name="base_amount_label">Base Amount</string>
    <string name="extra_charges_label">Extra Charges</string>
    <string name="amount_paid_label">Amount Paid</string>
    <string name="payment_mode_label">Payment Mode</string>
    <string name="rupee_value">₹ %1$s</string>
    <string name="rupee_placeholder">₹ —</string>
    <string name="payment_confirmed">✅  Payment Confirmed</string>
    <string name="thank_you_payment">Thank you for your prompt payment!</string>
    <string name="print_receipt">Print Receipt</string>
    <string name="share_online">Share Online</string>

    <string name="rupee_100">₹100</string>
    <string name="cash_label">Cash</string>

    <string name="series_format">Series: %1$s</string>
    <string name="paid_caps">PAID</string>
    <string name="unpaid_caps">UNPAID</string>
    <string name="zero">0</string>
"""

strings_te = """
    <!-- Missing String Resources -->
    <string name="login_successful">లాగిన్ విజయవంతమైంది</string>
    <string name="invalid_credentials">తప్పు వినియోగదారు పేరు లేదా పాస్‌వర్డ్</string>
    <string name="enter_username">వినియోగదారు పేరు నమోదు చేయండి</string>
    <string name="enter_password">పాస్‌వర్డ్ నమోదు చేయండి</string>
    <string name="password_too_short">పాస్‌వర్డ్ కనీసం 4 అక్షరాలు ఉండాలి</string>

    <string name="vinayaka_cable_network">వినాయక కేబుల్ నెట్‌వర్క్</string>
    <string name="search_customer">కస్టమర్‌ను వెతకండి</string>
    <string name="searching">వెతుకుతోంది...</string>
    <string name="enter_series_number">సిరీస్ నంబర్‌ను నమోదు చేయండి</string>
    <string name="customer_not_found">కస్టమర్ కనుగొనబడలేదు</string>
    <string name="error_prefix">లోపం: %1$s</string>
    <string name="customer_details_title">కస్టమర్ వివరాలు</string>
    <string name="name_label">పేరు</string>
    <string name="final_bill_amount">తుది బిల్లు ₹%1$s</string>
    <string name="submit_payment">చెల్లింపు సమర్పించండి</string>

    <string name="customer_info_section">కస్టమర్ సమాచారం</string>
    <string name="full_name_label">పూర్తి పేరు</string>
    <string name="series_number_label">సిరీస్ నంబర్</string>
    <string name="billing_summary_section">బిల్లింగ్ సారాంశం</string>
    <string name="base_amount_value">బేస్ మొత్తం: ₹%1$s</string>
    <string name="previous_due_value">మునుపటి బకాయి: ₹%1$s</string>
    <string name="final_bill_value">తుది బిల్లు   ₹%1$s</string>
    <string name="enter_amount_hint">బ్యాలెన్స్ చూడటానికి చెల్లించిన మొత్తాన్ని నమోదు చేయండి</string>
    <string name="view_share_receipt">రశీదు చూడండి / భాగస్వామ్యం చేయండి</string>

    <string name="download_pdf">పిడిఎఫ్ డౌన్‌లోడ్ చేయండి</string>
    <string name="export_csv">సిఎస్‌వి ఎగుమతి చేయండి</string>
    <string name="search_customer_hint">వెతకడానికి కస్టమర్ ఐడి లేదా ఫోన్ నంబర్ నమోదు చేయండి</string>

    <string name="register_customer">కస్టమర్‌ను నమోదు చేయండి</string>
    <string name="register_customer_desc">నెట్‌వర్క్‌కు కొత్త కస్టమర్‌ని జోడించడానికి క్రింది వివరాలను పూరించండి.</string>
    <string name="customer_information">కస్టమర్ సమాచారం</string>
    <string name="billing_details">బిల్లింగ్ వివరాలు</string>
    <string name="new_customer_billing_note">కొత్త కస్టమర్లు ₹0 మునుపటి బకాయితో ప్రారంభమవుతారు మరియు \'చెల్లించబడలేదు\' అని గుర్తించబడతారు.</string>
    <string name="save_customer">కస్టమర్‌ను సేవ్ చేయండి</string>

    <string name="payment_receipt">చెల్లింపు రశీదు</string>
    <string name="business_name_caps">వినాయక కేబుల్ నెట్‌వర్క్</string>
    <string name="dash">—</string>
    <string name="date_label">తేదీ</string>
    <string name="base_amount_label">బేస్ మొత్తం</string>
    <string name="extra_charges_label">అదనపు ఛార్జీలు</string>
    <string name="amount_paid_label">చెల్లించిన మొత్తం</string>
    <string name="payment_mode_label">చెల్లింపు విధానం</string>
    <string name="rupee_value">₹ %1$s</string>
    <string name="rupee_placeholder">₹ —</string>
    <string name="payment_confirmed">✅  చెల్లింపు నిర్ధారించబడింది</string>
    <string name="thank_you_payment">మీరు త్వరగా చెల్లించినందుకు ధన్యవాదాలు!</string>
    <string name="print_receipt">రశీదు ముద్రించండి</string>
    <string name="share_online">ఆన్‌లైన్‌లో భాగస్వామ్యం చేయండి</string>

    <string name="rupee_100">₹100</string>
    <string name="cash_label">నగదు</string>

    <string name="series_format">సిరీస్: %1$s</string>
    <string name="paid_caps">చెల్లించబడింది</string>
    <string name="unpaid_caps">చెల్లించబడలేదు</string>
    <string name="zero">0</string>
"""

def insert_strings(filepath, strings_to_insert):
    with open(filepath, 'r') as f:
        content = f.read()
    content = content.replace("</resources>", f"{strings_to_insert}\n</resources>")
    with open(filepath, 'w') as f:
        f.write(content)

insert_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values/strings.xml', strings_en)
insert_strings('/Users/nagneelam/AndroidStudioProjects/VinayakaCableNetwork/app/src/main/res/values-te/strings.xml', strings_te)

print("Strings updated successfully!")
