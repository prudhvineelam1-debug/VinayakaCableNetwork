# Billing Engine Carry-Forward Fix — Design Spec

Sub-project 1 of 4 in the "CableManager Pro completeness" initiative:

1. **Billing engine fix** (this spec) — correct monthly carry-forward, remove hidden test-data hazard, fix broken Partial filter.
2. Accessories ledger — separate tracking for hardware sales (remote, STB), linked to customer, excluded from subscription money math.
3. Invoice print + WhatsApp share — consolidate the two Bluetooth printer implementations, add PNG invoice generation, share via Android share sheet.
4. UI redesign rollout — bring remaining old-style screens (ComplaintActivity, CollectorDashboardActivity, DataImportActivity, Report's month picker) to the CableManager Pro visual style already used in Login/Dashboard/CustomerDetails.

Each sub-project gets its own design → plan → implementation cycle. This document covers only sub-project 1.

## Problem

The business rule: if a customer owes ₹200 this month and pays only ₹150, the remaining ₹50 must carry forward and add to next month's ₹200 charge (₹250 due next cycle).

Today, `CustomerRepository.submitPayment()` (app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt:84-188) correctly *decrements* `pendingAmount` when a payment is recorded, and correctly leaves any unpaid remainder sitting in `pendingAmount`. But nothing ever *advances the billing cycle* — no code adds a new month's `monthlyCharge` on top of whatever is left in `pendingAmount`. A customer who pays nothing this month keeps the exact same `pendingAmount` forever; it never grows to reflect a second month owed.

A real fix for this already exists as an unused scheduled Cloud Function in `firebase_functions/index.js` (a `monthlyRollover` trigger), but `firebase.json` deploys Functions from `functions/` — an empty boilerplate directory — so the real function has never run in production.

Three related correctness/safety issues found in the same area during audit:

- `DashboardActivity.kt:97-103` — long-pressing the profile avatar silently calls `CustomerRepository.loadTestData()`, writing 4 hardcoded fake customer records into the **live production Firestore** `customers` collection. No confirmation, no build-type gate.
- `DashboardActivity.kt:186-188` / `CustomerListActivity` — the "Partially Paid" dashboard tile passes `FILTER_TYPE=PARTIAL`, but `CustomerListActivity` has no such filter branch and silently falls back to showing all customers.
- `firebase_functions/index.js` — dead, undeployed code duplicating what this spec now handles client-side. To be deleted to avoid a second, contradictory implementation existing in the repo.

## Decision: manual trigger, not scheduled function

Confirmed with the business owner: billing cycle advances via an explicit "Generate Bills" action in the app, not an automatic scheduled job. Reasons: simpler to build and debug, gives the admin/employee control over exactly when a new cycle starts (e.g. if data entry for the month isn't finished yet), and avoids needing to fix the Firebase Functions deployment path at all.

## Data model change

`CustomerModel` (app/src/main/java/com/saimega/vinayakacablenetwork/CustomerModel.kt) gains one field:

```kotlin
val lastBilledMonth: String = ""   // yyyy-MM, last month a monthlyCharge was added
```

This is distinct from the existing `lastPaidMonth` (yyyy-MM, last month a payment cleared the balance). `lastBilledMonth` tracks the billing cycle; `lastPaidMonth` tracks payment history. Conflating them was not attempted — they answer different questions and a customer can be billed for a month without paying it.

`CustomerRepository.mapDocToCustomer()` gains a matching read of `doc.getString("lastBilledMonth") ?: ""`.

## Generate Bills flow

New method on `CustomerRepository`:

```kotlin
suspend fun generateMonthlyBills(monthKey: String): BillingRunResult
```

Where `BillingRunResult` is a small sealed result (`AlreadyRun`, `Success(customersBilled: Int)`, `Failure(Exception)`) so the UI can show a precise message.

Steps:

1. Read `meta/billing` document, field `lastGeneratedMonth`. If it equals `monthKey`, return `AlreadyRun` immediately — no writes. This is the global idempotency guard: it blocks a second run for the same month even if two people tap the button around the same time (read-then-write is acceptable here since this is a rare, human-triggered, low-frequency admin action, not a hot path needing transactional strictness).
2. Query `customers` where `connectionStatus == "active"` (server-side `Source.SERVER` fetch, consistent with existing repository patterns).
3. For each customer document, compute `newPending = pendingAmount + monthlyCharge`. This is the carry-forward: whatever was left unpaid (0 if they paid in full, the remainder if partial or unpaid) plus the new month's charge.
4. Batch-write updates: `pendingAmount = newPending`, `lastBilledMonth = monthKey`, `status = "unpaid"`, `paymentStatus = "Unpaid"`. Use `db.batch()` (Firestore batches cap at 500 writes; chunk into groups of 500 if the customer count exceeds that — current test data is ~9 customers, but the code must not silently truncate a larger real customer base).
5. On successful commit, write `meta/billing.lastGeneratedMonth = monthKey` and `meta/billing.lastGeneratedAt = <timestamp>` (for an audit trail visible to the admin later if needed).
6. Deactivated customers (`connectionStatus == "inactive"`) are excluded entirely from the query in step 2 — they accrue nothing while deactivated, matching the confirmed business rule.

## UI

New Quick Action tile on `DashboardActivity`, labeled "Generate Bills", visible to both Admin and Employee roles (confirmed — no role gate needed here, unlike the hidden test-data trigger which is being removed, not extended).

Tapping it shows a confirmation `AlertDialog`: *"Generate bills for September 2026 for all active customers?"* (month name computed from current date) with Cancel/Confirm. On confirm, calls `generateMonthlyBills()`, shows a progress indicator, then a Toast summarizing the result:
- Success → `"Bills generated for N customers."`
- AlreadyRun → `"Bills for this month were already generated."`
- Failure → `"Error: <message>"`

The tile is not disabled pre-emptively based on client-side date checks (that would require an extra read on every dashboard load for a rare action) — the guard lives entirely in `generateMonthlyBills()`'s server round-trip, and the confirmation dialog plus the "already generated" message are sufficient feedback.

## Cleanup

- Delete the long-click listener on `tvProfileInitial` in `DashboardActivity.kt:97-103` that calls `loadTestData()`. Delete `CustomerRepository.loadTestData()` and `importCustomerBatch()` if nothing else calls `importCustomerBatch` (verify at implementation time — `DataImportActivity` may use it for CSV import; if so keep `importCustomerBatch`, only remove `loadTestData`).
- Implement the missing `PARTIAL` branch in `CustomerListActivity`'s filter logic (`c.status.equals("partial", ignoreCase = true)`), removing the fallback-to-ALL behavior and the comment describing it as deferred work.
- Delete `firebase_functions/index.js` (and the now-pointless `firebase_functions/` directory if it contains nothing else) — superseded by the manual in-app flow. `functions/index.js` (the actually-deployed boilerplate) is untouched since nothing in this sub-project needs a Cloud Function.

## Testing

- Unit-style verification via the existing emulator (Pixel_9, already confirmed working): log in, use Generate Bills on a customer with a partial payment, confirm `pendingAmount` becomes `remainder + monthlyCharge` and `lastBilledMonth` updates.
- Re-tap Generate Bills same month → confirm "already generated" message, no second charge applied (verify `pendingAmount` unchanged).
- Confirm Partial dashboard tile now shows only customers with `status == "partial"`.
- Confirm long-press on profile avatar no longer writes test data (no Toast, no Firestore write).
- Confirm build still compiles and installs after `firebase_functions/index.js` removal (it's outside the Android Gradle module, so this should have zero effect on `assembleDebug`).
