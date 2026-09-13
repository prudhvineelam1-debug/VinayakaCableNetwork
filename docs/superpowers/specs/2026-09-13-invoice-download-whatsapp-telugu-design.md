# Invoice Download, WhatsApp Share & Telugu Localization — Design Spec

Sub-project 3 of 4 in the "CableManager Pro completeness" initiative (see
`docs/superpowers/specs/2026-09-13-billing-engine-carry-forward-design.md`
for the full roadmap; sub-project 1, the billing carry-forward fix, is
merged to `main`).

## Problem

Business owner wants, when viewing a customer:
- A one-tap "Download Invoice" action.
- A one-tap "Share via WhatsApp" action.
- All of it (and the rest of the app) to actually change language when the
  Telugu toggle is used — including the invoice text itself.

Manual-only: no automatic/scheduled sending. The business owner decides
when an invoice is generated or sent (confirmed in conversation).

## What already exists (found during investigation)

`ReceiptActivity` (app/src/main/java/com/saimega/vinayakacablenetwork/ReceiptActivity.kt)
already has a working PDF pipeline:
- `generateReceiptPdf()` / `drawReceiptOnCanvas()` — renders a receipt to a
  one-page PDF via `PdfDocument` + `Canvas`.
- `printReceipt()` — Android print framework.
- `shareReceipt()` — generic `ACTION_SEND` share chooser (already includes
  WhatsApp as one of the options a user can pick, today).
- `loadReceiptData()` already dispatches on an `ACTION` intent extra
  (`"PRINT"` / `"SHARE"`) — an established pattern this spec extends
  rather than replaces.

`BaseActivity.attachBaseContext()` already wraps every Activity (including
`ReceiptActivity`) with `LocaleHelper`'s selected locale. Any `getString()`
call in an Activity that extends `BaseActivity` already respects the
Telugu toggle with zero extra plumbing.

Most of the invoice's label strings **already exist** as strimg resources
in `values/strings.xml` (`business_name_caps`, `date_label`,
`base_amount_label`, `extra_charges_label`, `amount_paid_label`,
`payment_mode_label`, `series_number_label`, `payment_receipt`,
`thank_you_payment`, `total_paid`) — `drawReceiptOnCanvas()` and
`BluetoothPrinterHelper.printReceipt()` simply don't use them; they
hardcode literal English text instead.

Separately, of the app's 184 string keys, only 50 have a Telugu
translation in `values-te/strings.xml` today — a **pre-existing** gap
unrelated to invoices. The business owner asked to close this gap in the
same pass ("everything should change" when the language is switched).

## What's explicitly out of scope

- `CustomerListActivity`'s "Download PDF" button (bulk multi-customer
  report export) — a distinct, working feature. Left untouched.
- Automatic/scheduled invoice generation or sending — confirmed manual
  only.
- Any change to the Bluetooth ESC/POS printing path's Telugu handling
  (`BluetoothPrinterManager`'s bitmap-rendering approach) beyond making
  its plain-text labels use the same string resources — thermal printers
  needing a rendered bitmap for non-Latin scripts is a hardware font
  limitation, already solved there, and not part of this sub-project.

## Design

### 1. Wire existing string resources into the invoice renderer

`ReceiptActivity.drawReceiptOnCanvas()`: replace every hardcoded literal
(`"VINAYAKA CABLE NETWORK"`, `"Customer Name"`, `"Date"`, `"Base Amount"`,
`"Extra Charges"`, `"Total Paid Amount"`, `"Payment Mode"`, `"Thank you for
your prompt payment!"`) with `getString(R.string.xxx)` calls against the
resources listed above. One new key is needed:
`customer_name_label` = "Customer Name" (English) — the existing
`name_label` = "Name" is used elsewhere in the app for a different,
shorter context and shouldn't be repurposed.

`BluetoothPrinterHelper.printReceipt()`'s hardcoded receipt text
(`"VINAYAKA CABLE NETWORK"`, `"Name:"`, `"Series No:"`, `"Date:"`, `"Base
Amount:"`, `"Extra Charges:"`, `"Amount Paid:"`, `"Mode:"`, `"Thank you for
your prompt payment!"`) gets the same treatment, reusing
`series_number_label` in place of the shorter `"Series No:"` for
consistency across every printed/shared output.

Because Android's `Canvas`/`TextPaint` renders Unicode Telugu directly via
system font fallback (unlike raw ESC/POS thermal-printer text mode, which
has no Telugu glyphs at all — the reason `BluetoothPrinterManager` renders
names as bitmaps), no bitmap trick is needed for the PDF/print/download
path. Once the strings are wired to resources and Telugu translations
exist, the invoice simply renders in Telugu when the app's language is set
to Telugu.

### 2. Fill in the missing Telugu translations

Diff of `values/strings.xml` vs `values-te/strings.xml` keys shows 140
missing translations (plus the 1 new `customer_name_label` key from part
1 — 141 total). These get added to `values-te/strings.xml` as best-effort
AI-generated Telugu translations.

**Caveat to flag to the business owner:** these translations are not
reviewed by a native Telugu speaker. They're good enough to make the
language toggle actually work everywhere instead of silently leaving most
of the app in English, but a native-speaker review pass before relying on
this with real staff/customers is recommended as a follow-up — not
blocking this sub-project.

### 3. CustomerDetailsActivity: new buttons

Add two buttons in `activity_customer_details.xml`, alongside the existing
`btnViewReceipt` (same visibility rule: shown only when the customer has
at least one payment record, matching `btnViewReceipt`'s existing
`View.GONE`/`View.VISIBLE` toggle in `bindCustomerData()`):

- `btnDownloadInvoice` — "Download Invoice"
- `btnShareWhatsapp` — "Share via WhatsApp"

Both start `ReceiptActivity` the same way `btnViewReceipt` does (passing
`CUSTOMER_ID`), plus an `ACTION` extra of `"DOWNLOAD"` or `"WHATSAPP"`
respectively — extending the existing dispatch in
`ReceiptActivity.loadReceiptData()` (which already handles `"PRINT"` and
`"SHARE"`) rather than introducing a parallel mechanism.

### 4. ReceiptActivity: two new handlers

`downloadReceipt()`: generates the PDF (reuses `generateReceiptPdf()`),
writes it into the public Downloads directory (`Environment.
getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)`,
matching the pattern `CustomerListActivity.generatePDF()` already uses for
its bulk export) with filename `Invoice_<customerId>_<date>.pdf`, and
Toasts "Invoice saved to Downloads."

`shareToWhatsApp()`: generates the PDF, builds the same `ACTION_SEND`
intent `shareReceipt()` already builds, but sets
`setPackage("com.whatsapp")` so it opens WhatsApp directly instead of the
chooser. If `packageManager.resolveActivity(...)` comes back null (WhatsApp
not installed), falls back to calling the existing `shareReceipt()` (generic
chooser) so the action never dead-ends.

## Testing

- Unit-testable: none of this sub-project's logic is pure/side-effect-free
  in a way that benefits from a new JUnit test (it's Canvas rendering,
  file I/O, and Intent construction — consistent with how the rest of
  `ReceiptActivity` and `CustomerListActivity` are already untested at the
  unit level in this codebase).
- Manual verification on the `Pixel_9` emulator, in both English and
  Telugu (toggle via the existing EN/TE buttons on Dashboard):
  - Open a paid customer → "Download Invoice" → confirm PDF appears in
    Downloads with correctly-localized text.
  - "Share via WhatsApp" → confirm WhatsApp opens directly with the PDF
    attached (or, if WhatsApp isn't installed on the test emulator, confirm
    the generic share chooser opens instead — acceptable fallback proof).
  - Confirm `CustomerListActivity`'s "Download PDF" bulk export still
    works unchanged.
  - Spot-check several previously-English-only screens (e.g. Complaints,
    Collector Dashboard) after switching to Telugu to confirm the
    translation fill-in took effect app-wide, not just on the invoice.
