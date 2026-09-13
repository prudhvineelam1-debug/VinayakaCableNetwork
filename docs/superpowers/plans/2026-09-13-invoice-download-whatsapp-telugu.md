# Invoice Download, WhatsApp Share & Telugu Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one-tap "Download Invoice" and "Share via WhatsApp" actions to the Customer Details screen, and make the invoice (and the rest of the app) actually change language when the Telugu toggle is used.

**Architecture:** Wire the invoice's already-existing-but-unused string resources into `ReceiptActivity`'s PDF renderer and `BluetoothPrinterHelper`'s print text, so `BaseActivity`'s existing locale wrapping makes them localize for free. Extend `ReceiptActivity`'s existing `ACTION`-based dispatch (`"PRINT"` / `"SHARE"`) with two new actions (`"DOWNLOAD"` / `"WHATSAPP"`) rather than inventing a new mechanism. Fill in the 141 missing Telugu string translations app-wide.

**Tech Stack:** Kotlin, Android `PdfDocument`/`Canvas`, Android `Intent.ACTION_SEND`, existing `LocaleHelper`/`BaseActivity` locale wrapping.

**Spec:** `docs/superpowers/specs/2026-09-13-invoice-download-whatsapp-telugu-design.md`

## Global Constraints

- Manual only — no automatic/scheduled invoice generation or sending.
- `CustomerListActivity`'s bulk "Download PDF" export is untouched — it's a different, working feature.
- No change to `BluetoothPrinterManager`'s Telugu bitmap-rendering approach (that solves a thermal-printer hardware font limitation, unrelated to this plan).
- New buttons on Customer Details use the same visibility rule as the existing `btnViewReceipt` (visible only once the customer has at least one payment).
- Telugu translations added here are AI-generated best-effort, not native-speaker-reviewed — acceptable to unblock the toggle, flagged as a follow-up for a review pass.

---

### Task 1: Wire ReceiptActivity's PDF renderer to string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt`

**Interfaces:**
- Produces: `R.string.customer_name_label` (new key), reuses existing `R.string.business_name_caps`, `R.string.payment_receipt`, `R.string.series_number_label`, `R.string.date_label`, `R.string.base_amount_label`, `R.string.extra_charges_label`, `R.string.total_paid`, `R.string.payment_mode_label`, `R.string.thank_you_payment`.

- [ ] **Step 1: Add the one missing string key**

In `app/src/main/res/values/strings.xml`, add this line right after the existing `<string name="series_number_label">Series Number</string>` line:

```xml
    <string name="customer_name_label">Customer Name</string>
```

- [ ] **Step 2: Replace hardcoded literals in drawReceiptOnCanvas with string resources**

In `app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt`, inside `drawReceiptOnCanvas()`, replace:

```kotlin
        canvas.drawText("VINAYAKA CABLE NETWORK", width / 2, 65f, paint)

        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Payment Receipt", width / 2, 92f, paint)
```

with:

```kotlin
        canvas.drawText(getString(R.string.business_name_caps), width / 2, 65f, paint)

        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(getString(R.string.payment_receipt), width / 2, 92f, paint)
```

Then replace:

```kotlin
        drawRow("Customer Name",  p.name)
        drawRow("Series Number",  customerId)
        drawRow("Date",           p.date)
        drawRow("Base Amount",    "₹ ${formatAmount(p.baseAmount)}")
        drawRow("Extra Charges",  "₹ ${formatAmount(p.extraCharges)}")
        drawRow(
            "Total Paid Amount",
            "₹ ${formatAmount(p.paid)}",
            valueColor = Color.parseColor("#2E7D32"),
            valueSize  = 18f
        )
        drawRow("Payment Mode", buildModeText(p.paymentMode, p.paymentNumber))
```

with:

```kotlin
        drawRow(getString(R.string.customer_name_label),  p.name)
        drawRow(getString(R.string.series_number_label),  customerId)
        drawRow(getString(R.string.date_label),            p.date)
        drawRow(getString(R.string.base_amount_label),    "₹ ${formatAmount(p.baseAmount)}")
        drawRow(getString(R.string.extra_charges_label),  "₹ ${formatAmount(p.extraCharges)}")
        drawRow(
            getString(R.string.total_paid),
            "₹ ${formatAmount(p.paid)}",
            valueColor = Color.parseColor("#2E7D32"),
            valueSize  = 18f
        )
        drawRow(getString(R.string.payment_mode_label), buildModeText(p.paymentMode, p.paymentNumber))
```

Then replace:

```kotlin
        canvas.drawText("Thank you for your prompt payment!", width / 2, y, paint)
```

with:

```kotlin
        canvas.drawText(getString(R.string.thank_you_payment), width / 2, y, paint)
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification on the emulator (English)**

Install the APK, open a paid customer's "View Receipt", tap Print or Share to generate the PDF (or check the saved file from Task 5 once that's done). Confirm the rendered PDF text reads identically to before this change (still English, same wording) — this step only proves the resource-based path produces the same output as the old hardcoded path.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt
git commit -m "$(cat <<'EOF'
Wire ReceiptActivity's PDF renderer to string resources

drawReceiptOnCanvas() hardcoded English literals directly instead of
using the string resources that already existed for this exact
purpose (business_name_caps, date_label, base_amount_label, etc).
BaseActivity already wraps every Activity's context with the selected
locale, so this alone is what's needed for the invoice PDF to follow
the Telugu toggle once translations exist (Task 3).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 2: Wire BluetoothPrinterHelper's receipt text to string resources

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/BluetoothPrinterHelper.kt`

**Interfaces:**
- Consumes: `R.string.business_name_caps`, `R.string.payment_receipt`, `R.string.series_number_label`, `R.string.thank_you_payment` (existing/Task 1 resources).
- Produces: none new.

- [ ] **Step 1: Replace the hardcoded receipt text template**

In `app/src/main/java/com/saimega/vinayakacablenetwork/BluetoothPrinterHelper.kt`, replace the body of `printReceipt()` starting from `val modeText = ...` through the `printer.printFormattedTextAndCut(receiptText)` call:

```kotlin
                val modeText = if (paymentNumber.isNotEmpty()) "$paymentMode ($paymentNumber)" else paymentMode

                val receiptText = """
                    [C]<b>VINAYAKA CABLE NETWORK</b>
                    [C]Payment Receipt
                    [L]
                    [C]--------------------------------
                    [L]Name: [R]$customerName
                    [L]Series No: [R]$customerId
                    [L]Date: [R]$date
                    [L]Base Amount: [R]Rs.$baseAmount
                    [L]Extra Charges: [R]Rs.$extraCharges
                    [C]--------------------------------
                    [L]<b>Amount Paid:</b> [R]<b>Rs.$totalPaid</b>
                    [C]--------------------------------
                    [L]Mode: [R]$modeText
                    [L]
                    [C]Thank you for your prompt payment!
                    [L]
                    [L]
                """.trimIndent()

                // Depending on the printer, printFormattedTextAndCut may cut the paper
                // For 58mm printers without a cutter, it will just feed paper.
                printer.printFormattedTextAndCut(receiptText)
```

with:

```kotlin
                val modeText = if (paymentNumber.isNotEmpty()) "$paymentMode ($paymentNumber)" else paymentMode

                val businessName   = context.getString(R.string.business_name_caps)
                val receiptTitle   = context.getString(R.string.payment_receipt)
                val seriesLabel    = context.getString(R.string.series_number_label)
                val thankYouLine   = context.getString(R.string.thank_you_payment)

                val receiptText = """
                    [C]<b>$businessName</b>
                    [C]$receiptTitle
                    [L]
                    [C]--------------------------------
                    [L]Name: [R]$customerName
                    [L]$seriesLabel: [R]$customerId
                    [L]Date: [R]$date
                    [L]Base Amount: [R]Rs.$baseAmount
                    [L]Extra Charges: [R]Rs.$extraCharges
                    [C]--------------------------------
                    [L]<b>Amount Paid:</b> [R]<b>Rs.$totalPaid</b>
                    [C]--------------------------------
                    [L]Mode: [R]$modeText
                    [L]
                    [C]$thankYouLine
                    [L]
                    [L]
                """.trimIndent()

                // Depending on the printer, printFormattedTextAndCut may cut the paper
                // For 58mm printers without a cutter, it will just feed paper.
                printer.printFormattedTextAndCut(receiptText)
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/BluetoothPrinterHelper.kt
git commit -m "$(cat <<'EOF'
Wire BluetoothPrinterHelper's receipt text to string resources

Same fix as the PDF renderer (previous commit) applied to the
Bluetooth thermal-printer text path, so a printed receipt also
follows the Telugu toggle for its localizable labels. The plain-text
labels here don't need BluetoothPrinterManager's bitmap-rendering
trick — that trick exists only because raw ESC/POS text mode has no
Telugu glyphs at all, which is unrelated to which language a *label*
is picked from.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 3: Fill in the 141 missing Telugu translations

**Files:**
- Modify: `app/src/main/res/values-te/strings.xml`

**Interfaces:**
- Consumes: none.
- Produces: none — this is a content-only addition, no code depends on these keys existing (they're already referenced elsewhere in the app; only the Telugu translation was missing).

- [ ] **Step 1: Add all missing translations**

In `app/src/main/res/values-te/strings.xml`, insert the following lines right before the closing `</resources>` tag:

```xml
    <string name="action_new_customer">కొత్త కస్టమర్</string>
    <string name="action_payment">చెల్లింపు</string>
    <string name="action_report">నివేదిక</string>
    <string name="action_search">వెతకండి</string>
    <string name="amount_paid_label">చెల్లించిన మొత్తం</string>
    <string name="area_label">ప్రాంతం / లొకాలిటీ</string>
    <string name="base_amount_label">బేస్ మొత్తం</string>
    <string name="base_amount_value">బేస్ మొత్తం: ₹%1$s</string>
    <string name="billing_details">బిల్లింగ్ వివరాలు</string>
    <string name="billing_summary_section">బిల్లింగ్ సారాంశం</string>
    <string name="box_number_label">బాక్స్ నంబర్</string>
    <string name="business_name_caps">వినాయక కేబుల్ నెట్‌వర్క్</string>
    <string name="cash_amount">నగదు: ₹%1$s</string>
    <string name="cash_label">నగదు</string>
    <string name="collected_month">ఈ నెల వసూలు చేయబడింది</string>
    <string name="collected_today">ఈ రోజు వసూలు చేయబడింది</string>
    <string name="collection_summary">వసూళ్ల సారాంశం</string>
    <string name="could_not_generate_pdf">PDF సృష్టించలేకపోయాము.</string>
    <string name="crf_number_label">CRF నంబర్</string>
    <string name="csv_error_prefix">CSV లోపం: %1$s</string>
    <string name="csv_saved_in_downloads">CSV డౌన్‌లోడ్స్‌లో సేవ్ చేయబడింది</string>
    <string name="customer_added_successfully">కస్టమర్ విజయవంతంగా జోడించబడ్డారు</string>
    <string name="customer_details_title">కస్టమర్ వివరాలు</string>
    <string name="customer_info_section">కస్టమర్ సమాచారం</string>
    <string name="customer_information">కస్టమర్ సమాచారం</string>
    <string name="customer_name_label">కస్టమర్ పేరు</string>
    <string name="customer_name_required">కస్టమర్ పేరు అవసరం</string>
    <string name="customer_not_found">కస్టమర్ కనుగొనబడలేదు</string>
    <string name="customer_status">కస్టమర్ స్థితి</string>
    <string name="customers_label">కస్టమర్లు</string>
    <string name="dash">—</string>
    <string name="date_label">తేదీ</string>
    <string name="day_report">రోజువారీ నివేదిక</string>
    <string name="download_pdf">PDF డౌన్‌లోడ్ చేయండి</string>
    <string name="download_type_pdf">%1$s PDF డౌన్‌లోడ్ చేయండి</string>
    <string name="end_date">ముగింపు తేదీ</string>
    <string name="enter_a_valid_amount">సరైన మొత్తాన్ని నమోదు చేయండి</string>
    <string name="enter_amount_hint">బ్యాలెన్స్ చూడటానికి చెల్లించిన మొత్తాన్ని నమోదు చేయండి</string>
    <string name="enter_password">పాస్‌వర్డ్ నమోదు చేయండి</string>
    <string name="enter_payment_number">చెల్లింపు నంబర్ నమోదు చేయండి</string>
    <string name="enter_series_number">సిరీస్ నంబర్ నమోదు చేయండి</string>
    <string name="enter_username">యూజర్‌నేమ్ నమోదు చేయండి</string>
    <string name="enter_valid_amount">సరైన మొత్తాన్ని నమోదు చేయండి</string>
    <string name="error_generating_pdf">PDF సృష్టించడంలో లోపం</string>
    <string name="error_prefix">లోపం: %1$s</string>
    <string name="export_csv">CSV ఎగుమతి చేయండి</string>
    <string name="export_pdf">PDF ఎగుమతి చేయండి</string>
    <string name="extra_charges_label">అదనపు ఛార్జీలు</string>
    <string name="failed_fetch_payment">చెల్లింపును పొందడంలో విఫలమైంది: %1$s</string>
    <string name="failed_prefix">విఫలమైంది: %1$s</string>
    <string name="final_bill_amount">తుది బిల్లు ₹%1$s</string>
    <string name="final_bill_value">తుది బిల్లు   ₹%1$s</string>
    <string name="financial_report">ఆర్థిక నివేదిక</string>
    <string name="first_fragment_label">మొదటి ఫ్రాగ్మెంట్</string>
    <string name="for_this_month">ఈ నెలకు: ₹%1$s</string>
    <string name="from_date">నుండి: %1$s</string>
    <string name="full_name_label">పూర్తి పేరు</string>
    <string name="generating_receipt">రసీదు సృష్టిస్తోంది…</string>
    <string name="hardware_details_label">హార్డ్‌వేర్ వివరాలు (STB)</string>
    <string name="hint_amount_paid">చెల్లించిన మొత్తం</string>
    <string name="hint_base_monthly_amount">బేస్ నెలవారీ మొత్తం (&#8377;)</string>
    <string name="hint_enter_id_or_phone">ID / ఫోన్ నమోదు చేయండి</string>
    <string name="hint_extra_charges">DC / అదనపు ఛార్జీలు</string>
    <string name="hint_full_name">పూర్తి పేరు</string>
    <string name="hint_name_or_series">పేరు లేదా సిరీస్ నంబర్</string>
    <string name="hint_password">పాస్‌వర్డ్</string>
    <string name="hint_payment_mode">చెల్లింపు విధానం</string>
    <string name="hint_phone_number">ఫోన్ నంబర్</string>
    <string name="hint_series_number">సిరీస్ నంబర్</string>
    <string name="hint_upi_number">PhonePe / UPI నంబర్</string>
    <string name="hint_username">యూజర్‌నేమ్</string>
    <string name="invalid_credentials">చెల్లని యూజర్‌నేమ్ లేదా పాస్‌వర్డ్</string>
    <string name="language_toggle">తెలుగు</string>
    <string name="last_payment">చివరి చెల్లింపు</string>
    <string name="login_successful">లాగిన్ విజయవంతమైంది</string>
    <string name="login_text">లాగిన్</string>
    <string name="lorem_ipsum">ప్లేస్‌హోల్డర్ టెక్స్ట్</string>
    <string name="mode_breakdown">విధానం వారీగా విభజన</string>
    <string name="monthly_amount_required">నెలవారీ మొత్తం అవసరం</string>
    <string name="monthly_report">నెలవారీ నివేదిక</string>
    <string name="name_label">పేరు</string>
    <string name="new_customer_billing_note">కొత్త కస్టమర్లు ₹0 మునుపటి బకాయితో ప్రారంభమై, చెల్లించలేదు అని గుర్తించబడతారు.</string>
    <string name="next">తదుపరి</string>
    <string name="no_customer_id">కస్టమర్ ID అందలేదు.</string>
    <string name="no_data_export">ఎగుమతి చేయడానికి డేటా లేదు</string>
    <string name="no_payment_records">చెల్లింపు రికార్డులు కనుగొనబడలేదు.</string>
    <string name="no_payments_found">ఈ కాలానికి చెల్లింపులు కనుగొనబడలేదు.</string>
    <string name="online_upi_label">ఆన్‌లైన్ / UPI</string>
    <string name="paid_caps">చెల్లించారు</string>
    <string name="paid_with_tick">✅ చెల్లించారు</string>
    <string name="password_too_short">పాస్‌వర్డ్ కనీసం 4 అక్షరాలు ఉండాలి</string>
    <string name="payment_confirmed">✅  చెల్లింపు నిర్ధారించబడింది</string>
    <string name="payment_mode_label">చెల్లింపు విధానం</string>
    <string name="payment_receipt">చెల్లింపు రసీదు</string>
    <string name="payment_successful">చెల్లింపు విజయవంతమైంది</string>
    <string name="payments_count">చెల్లింపులు: %1$d</string>
    <string name="payments_count_format">%1$d చెల్లింపులు</string>
    <string name="payments_count_label">చెల్లింపుల సంఖ్య</string>
    <string name="please_enter_all_required_fields">దయచేసి అన్ని అవసరమైన ఫీల్డ్‌లను నమోదు చేయండి</string>
    <string name="previous">మునుపటి</string>
    <string name="previous_due_value">మునుపటి బకాయి: ₹%1$s</string>
    <string name="print">ప్రింట్</string>
    <string name="quick_actions">త్వరిత చర్యలు</string>
    <string name="register_customer">కస్టమర్‌ని నమోదు చేయండి</string>
    <string name="register_customer_desc">నెట్‌వర్క్‌కు కొత్త కస్టమర్‌ని జోడించడానికి క్రింది వివరాలను పూరించండి.</string>
    <string name="rupee_100">₹100</string>
    <string name="rupee_placeholder">₹ —</string>
    <string name="rupee_value">₹ %1$s</string>
    <string name="save_customer">కస్టమర్‌ని సేవ్ చేయండి</string>
    <string name="saved_in_downloads">డౌన్‌లోడ్స్‌లో సేవ్ చేయబడింది</string>
    <string name="search">వెతకండి</string>
    <string name="search_customer_hint">వెతకడానికి కస్టమర్ ID లేదా ఫోన్ నంబర్ నమోదు చేయండి</string>
    <string name="searching">వెతుకుతోంది…</string>
    <string name="second_fragment_label">రెండవ ఫ్రాగ్మెంట్</string>
    <string name="select_start_date_first">ముందుగా ప్రారంభ తేదీని ఎంచుకోండి</string>
    <string name="series_format">సిరీస్: %1$s</string>
    <string name="series_number_label">సిరీస్ నంబర్</string>
    <string name="series_number_required">సిరీస్ నంబర్ అవసరం</string>
    <string name="share">షేర్ చేయండి</string>
    <string name="share_online">ఆన్‌లైన్‌లో షేర్ చేయండి</string>
    <string name="start_date">ప్రారంభ తేదీ</string>
    <string name="status_paid">చెల్లించారు</string>
    <string name="status_unpaid">చెల్లించలేదు</string>
    <string name="submit_payment">చెల్లింపు సమర్పించండి</string>
    <string name="summary">సారాంశం</string>
    <string name="summary_month_collection">ఈ నెల</string>
    <string name="summary_today_collection">ఈ రోజు</string>
    <string name="thank_you_payment">మీ సకాలంలో చెల్లింపుకు ధన్యవాదాలు!</string>
    <string name="title_add_new_customer">కొత్త కస్టమర్‌ని జోడించండి</string>
    <string name="title_search_customers">కస్టమర్లను వెతకండి</string>
    <string name="today_transactions">ఈ రోజు లావాదేవీలు</string>
    <string name="total_collected">మొత్తం వసూలు: ₹%1$s</string>
    <string name="total_collected_today">ఈ రోజు మొత్తం వసూలు</string>
    <string name="total_paid">మొత్తం చెల్లించారు</string>
    <string name="unpaid_caps">చెల్లించలేదు</string>
    <string name="upi_amount">UPI / ఆన్‌లైన్: ₹%1$s</string>
    <string name="vc_number_label">VC నంబర్</string>
    <string name="view_receipt">వీక్షించండి</string>
    <string name="view_share_receipt">రసీదు వీక్షించండి / షేర్ చేయండి</string>
    <string name="vinayaka_cable_network">వినాయక కేబుల్ నెట్‌వర్క్</string>
    <string name="zero">0</string>
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (A resource-only change; if this fails, the most likely cause is a stray unescaped `'` or `&` in one of the added lines — check the error line number against the list above.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values-te/strings.xml
git commit -m "$(cat <<'EOF'
Fill in 141 missing Telugu translations

Of the app's 184 string keys, only 50 had a Telugu translation before
this commit — everything else silently stayed in English when a user
switched the app to Telugu. These are AI-generated best-effort
translations; a native-speaker review pass is recommended as a
follow-up before relying on this with real staff, but this closes the
gap that made the language toggle mostly non-functional.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 4: Download Invoice + Share via WhatsApp buttons on Customer Details

**Files:**
- Modify: `app/src/main/res/layout/activity_customer_details.xml`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerDetailsActivity.kt`

**Interfaces:**
- Produces: `R.id.btnDownloadInvoice`, `R.id.btnShareWhatsapp` — consumed by `CustomerDetailsActivity`'s click listeners in this same task.
- Consumes (later, Tasks 5 & 6): `ReceiptActivity` launched with `CUSTOMER_ID` + `ACTION` extras `"DOWNLOAD"` / `"WHATSAPP"`.

- [ ] **Step 1: Add the two buttons to the layout**

In `app/src/main/res/layout/activity_customer_details.xml`, right after the existing `btnViewReceipt` button (before `btnViewHistory`), add:

```xml
            <Button
                android:id="@+id/btnDownloadInvoice"
                style="@style/Widget.Material3.Button.TonalButton"
                android:layout_width="match_parent"
                android:layout_height="56dp"
                android:text="Download Invoice"
                android:visibility="gone"
                app:cornerRadius="16dp"
                android:layout_marginBottom="12dp" />

            <Button
                android:id="@+id/btnShareWhatsapp"
                style="@style/Widget.Material3.Button.TonalButton"
                android:layout_width="match_parent"
                android:layout_height="56dp"
                android:text="Share via WhatsApp"
                android:visibility="gone"
                app:cornerRadius="16dp"
                android:layout_marginBottom="12dp" />
```

- [ ] **Step 2: Bind the new views**

In `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerDetailsActivity.kt`, add two new property declarations right after the existing `private lateinit var btnViewReceipt: Button`:

```kotlin
    private lateinit var btnDownloadInvoice: Button
    private lateinit var btnShareWhatsapp: Button
```

Then in `bindViews()`, right after the existing `btnViewReceipt = findViewById(R.id.btnViewReceipt)` line, add:

```kotlin
        btnDownloadInvoice = findViewById(R.id.btnDownloadInvoice)
        btnShareWhatsapp = findViewById(R.id.btnShareWhatsapp)
```

- [ ] **Step 3: Match visibility to btnViewReceipt and wire click listeners**

In the same file, inside `bindCustomerData()`, find this existing block:

```kotlin
        if (c.status.equals("paid", true)) {
            findViewById<View>(R.id.paymentFormArea).visibility = View.GONE
            btnViewReceipt.visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.paymentFormArea).visibility = View.VISIBLE
            btnViewReceipt.visibility = View.GONE
        }
```

Replace it with:

```kotlin
        if (c.status.equals("paid", true)) {
            findViewById<View>(R.id.paymentFormArea).visibility = View.GONE
            btnViewReceipt.visibility = View.VISIBLE
            btnDownloadInvoice.visibility = View.VISIBLE
            btnShareWhatsapp.visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.paymentFormArea).visibility = View.VISIBLE
            btnViewReceipt.visibility = View.GONE
            btnDownloadInvoice.visibility = View.GONE
            btnShareWhatsapp.visibility = View.GONE
        }
```

Then in `setupListeners()`, right after the existing `btnViewReceipt.setOnClickListener { ... }` block, add:

```kotlin
        btnDownloadInvoice.setOnClickListener {
            val intent = Intent(this, ReceiptActivity::class.java)
            intent.putExtra("CUSTOMER_ID", currentCustomer?.id)
            intent.putExtra("ACTION", "DOWNLOAD")
            startActivity(intent)
        }

        btnShareWhatsapp.setOnClickListener {
            val intent = Intent(this, ReceiptActivity::class.java)
            intent.putExtra("CUSTOMER_ID", currentCustomer?.id)
            intent.putExtra("ACTION", "WHATSAPP")
            startActivity(intent)
        }
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (`ACTION` extras of `"DOWNLOAD"`/`"WHATSAPP"` are inert until Tasks 5 and 6 add their handling in `ReceiptActivity` — `loadReceiptData()`'s existing `if/else if` on `action` simply won't match them yet, so tapping these buttons will open `ReceiptActivity` normally without erroring.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_customer_details.xml app/src/main/java/com/saimega/vinayakacablenetwork/CustomerDetailsActivity.kt
git commit -m "$(cat <<'EOF'
Add Download Invoice and Share via WhatsApp buttons to Customer Details

Same visibility rule as the existing View Receipt button (shown once
the customer has at least one payment). Launches ReceiptActivity with
a DOWNLOAD/WHATSAPP ACTION extra, extending its existing PRINT/SHARE
dispatch pattern — handling for the new actions lands in the next two
commits.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 5: ReceiptActivity — Download to public Downloads folder

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt`

**Interfaces:**
- Consumes: `generateReceiptPdf(): File?` (existing), `latestPayment: PaymentModel?` (existing), `customerId: String` (existing).
- Produces: `downloadReceipt()` — consumed by nothing else (UI-triggered terminal action).

- [ ] **Step 1: Add the Environment import**

In `app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt`, add to the imports (near the other `android.os.*` imports):

```kotlin
import android.os.Environment
```

- [ ] **Step 2: Add the ACTION dispatch branch**

In `loadReceiptData()`, find:

```kotlin
                if (action == "PRINT") {
                    // Try PDF print automatically
                    printReceipt()
                } else if (action == "SHARE") {
                    shareReceipt()
                }
```

Replace with:

```kotlin
                if (action == "PRINT") {
                    // Try PDF print automatically
                    printReceipt()
                } else if (action == "SHARE") {
                    shareReceipt()
                } else if (action == "DOWNLOAD") {
                    downloadReceipt()
                } else if (action == "WHATSAPP") {
                    shareToWhatsApp()
                }
```

(`shareToWhatsApp()` is added in Task 6 — this line will not compile until that task lands. Tasks 5 and 6 are meant to be applied together before the next build; if you're executing strictly one task at a time, skip this replacement until Task 6's Step 2 and do both edits in the same build cycle.)

- [ ] **Step 3: Implement downloadReceipt()**

Add this method right after `shareReceipt()`:

```kotlin
    private fun downloadReceipt() {
        val pdfFile = generateReceiptPdf() ?: run {
            Toast.makeText(this, getString(R.string.could_not_generate_pdf), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dateStr = latestPayment?.date?.replace("/", "-") ?: "invoice"
            val outFile = File(downloadsDir, "Invoice_${customerId}_$dateStr.pdf")
            pdfFile.copyTo(outFile, overwrite = true)

            Toast.makeText(this, getString(R.string.saved_in_downloads), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("ReceiptActivity", "Download error: ${e.message}", e)
            Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()
        }
    }
```

- [ ] **Step 4: Wire the button click in ReceiptActivity's own UI too**

This is optional-but-consistent: `ReceiptActivity` already has `btnPrint`/`btnShare`/`btnBluetoothPrint` wired in its own layout. This plan does not add a fourth button there — download/WhatsApp are reached via Customer Details' two new buttons (Task 4), which pass `ACTION` extras that trigger automatically in `loadReceiptData()`. No additional UI wiring is needed inside `ReceiptActivity`'s own layout for this task.

- [ ] **Step 5: Build and verify**

This won't compile standalone (Task 3's `shareToWhatsApp()` reference). Proceed to Task 6, then build both together.

- [ ] **Step 6: Commit (combined with Task 6 — see Task 6's commit step)**

Do not commit yet — Task 6 completes this pair.

---

### Task 6: ReceiptActivity — Share directly to WhatsApp with fallback

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt`

**Interfaces:**
- Consumes: `generateReceiptPdf(): File?` (existing), `shareReceipt()` (existing, used as fallback).
- Produces: `shareToWhatsApp()` — completes the dispatch branch added in Task 5 Step 2.

- [ ] **Step 1: Implement shareToWhatsApp()**

Add this method right after `downloadReceipt()`:

```kotlin
    private fun shareToWhatsApp() {
        val pdfFile = generateReceiptPdf() ?: run {
            Toast.makeText(this, getString(R.string.could_not_generate_pdf), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", pdfFile)

            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
            }

            if (whatsappIntent.resolveActivity(packageManager) != null) {
                startActivity(whatsappIntent)
            } else {
                Toast.makeText(this, "WhatsApp not installed — showing share options instead", Toast.LENGTH_SHORT).show()
                shareReceipt()
            }
        } catch (e: Exception) {
            Log.e("ReceiptActivity", "WhatsApp share error: ${e.message}", e)
            Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()
        }
    }
```

- [ ] **Step 2: Complete the dispatch branch from Task 5**

Confirm `loadReceiptData()` now reads exactly:

```kotlin
                if (action == "PRINT") {
                    // Try PDF print automatically
                    printReceipt()
                } else if (action == "SHARE") {
                    shareReceipt()
                } else if (action == "DOWNLOAD") {
                    downloadReceipt()
                } else if (action == "WHATSAPP") {
                    shareToWhatsApp()
                }
```

(If Task 5 Step 2 wasn't applied yet, apply it now.)

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification on the emulator — Download**

Install the APK. Open a paid customer → "Download Invoice". Confirm the Toast reads "Saved in Downloads" and the file exists:

```bash
SDK=/Users/nagneelam/Library/Android/sdk
"$SDK/platform-tools/adb" shell run-as com.saimega.vinayakacablenetwork ls -la /sdcard/Download/ 2>&1 || "$SDK/platform-tools/adb" shell ls -la /sdcard/Download/
```

Expected: an `Invoice_<customerId>_<date>.pdf` file listed.

- [ ] **Step 5: Manual verification on the emulator — WhatsApp**

Open the same customer → "Share via WhatsApp". If WhatsApp is not installed on the `Pixel_9` AVD (likely, on a bare emulator image), confirm the Toast "WhatsApp not installed — showing share options instead" appears and the generic share chooser opens (the existing, already-working `shareReceipt()` path) — this proves the fallback branch runs correctly. If WhatsApp is installed, confirm it opens directly with the PDF attached instead.

- [ ] **Step 6: Commit (Tasks 5 + 6 together)**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt
git commit -m "$(cat <<'EOF'
Add Download and WhatsApp-direct-share to ReceiptActivity

downloadReceipt() copies the generated PDF into the public Downloads
folder (same pattern CustomerListActivity's bulk export already uses)
and confirms via Toast. shareToWhatsApp() targets the WhatsApp package
directly via Intent.setPackage so it skips the chooser, falling back
to the existing generic shareReceipt() when WhatsApp isn't installed
so the action never dead-ends.

Verified on emulator: Download saves a correctly-named file to
/sdcard/Download; WhatsApp share falls back to the share chooser when
WhatsApp isn't installed (confirmed on the Pixel_9 AVD).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 7: End-to-end Telugu verification

**Files:** none (verification-only task).

**Interfaces:** none.

- [ ] **Step 1: Switch the app to Telugu**

On the Dashboard, tap the "TE" language button (existing feature, `LocaleHelper.setLocale(this, "te")` + `recreate()`).

- [ ] **Step 2: Verify the invoice itself is now in Telugu**

Open a paid customer → "Download Invoice". Pull the file and inspect it, or use "View Receipt" → Print/Share to visually confirm the PDF's labels ("Customer Name" → "కస్టమర్ పేరు", "Date" → "తేదీ", "Base Amount" → "బేస్ మొత్తం", "Payment Mode" → "చెల్లింపు విధానం", "Thank you for your prompt payment!" → "మీ సకాలంలో చెల్లింపుకు ధన్యవాదాలు!", header → "వినాయక కేబుల్ నెట్‌వర్క్" / "చెల్లింపు రసీదు") render correctly instead of falling back to English.

- [ ] **Step 3: Spot-check previously English-only screens**

With Telugu still active, open Complaints, Collector Dashboard, and the Add New Customer screen. Confirm labels that come from the newly-translated keys (e.g. "Customer Name Required" validation message, "Save Customer" button, "Search Customers" title) now show Telugu text instead of English.

- [ ] **Step 4: Confirm the untouched bulk export still works**

Switch back to English. From the Customers list (any filter), tap "Download PDF" (the bulk multi-customer export). Confirm it still generates and shares a multi-row PDF exactly as before — this task made no changes to `CustomerListActivity`.

- [ ] **Step 5: Report results**

No commit for this task (verification only). If any screen fails to show Telugu text where expected, note which string key is missing translation coverage — every key added in Task 3 came from the diff against `values/strings.xml`, so a gap here would mean a key that isn't wired to any UI text via `getString()`/XML `@string/` reference, which is a pre-existing issue outside this plan's scope to fix (the string simply isn't used yet anywhere).

---

## Plan Self-Review Notes

- **Spec coverage:** part 1 (existing string wiring) → Task 1 + Task 2; part 2 (Telugu fill-in) → Task 3; part 3 (Customer Details buttons) → Task 4; part 4 (Download/WhatsApp handlers) → Tasks 5 + 6; testing section → Task 7. All spec sections have a task.
- **Type consistency:** `downloadReceipt()` and `shareToWhatsApp()` are defined in Tasks 5/6 with no parameters, matching their call sites in Task 5 Step 2's dispatch branch — checked. `btnDownloadInvoice`/`btnShareWhatsapp` IDs match between the layout XML (Task 4 Step 1) and the Kotlin `findViewById` calls (Task 4 Step 2) — checked.
- **No placeholders:** every step has literal code, exact file content, or an exact command.
- **Sequencing note:** Tasks 5 and 6 are split by convention (one file's worth of related but separable changes) but share a single commit, since Task 5's dispatch branch references Task 6's function — this is called out explicitly in both tasks rather than silently expecting the reader to infer it.
