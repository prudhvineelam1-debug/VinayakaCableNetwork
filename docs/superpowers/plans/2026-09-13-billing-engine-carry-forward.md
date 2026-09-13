# Billing Engine Carry-Forward Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make monthly billing correctly carry forward unpaid balances, add a manual "Generate Bills" action, and remove two known correctness/safety hazards (hidden test-data write, broken Partial filter).

**Architecture:** Extract the carry-forward arithmetic into a small pure function (`BillingCycle.computeMonthlyBillUpdates`) that is unit-testable without Firestore. `CustomerRepository.generateMonthlyBills()` wraps that pure function with the actual Firestore reads/writes (query active customers, batch-update, idempotency guard via a `meta/billing` document). `DashboardActivity` gets a new Quick Action tile that calls it after a confirmation dialog.

**Tech Stack:** Kotlin, Android Gradle Plugin, Firebase Firestore (`firebase-firestore-ktx:24.10.3`), Kotlin Coroutines, JUnit 4 (currently not wired up — Task 1 fixes this), Material Components.

**Spec:** `docs/superpowers/specs/2026-09-13-billing-engine-carry-forward-design.md`

## Global Constraints

- `lastBilledMonth` (yyyy-MM) is a new, separate field from the existing `lastPaidMonth` — never conflate them.
- Deactivated customers (`Connection Status` != `"active"`) are excluded entirely from bill generation.
- Idempotency guard: `meta/billing.lastGeneratedMonth` must block a second run for the same month.
- Both ADMIN and EMPLOYEE roles can trigger Generate Bills — no role gate.
- This plan does not touch: accessories tracking, Bluetooth printing, or old-style-screen UI redesign — those are separate sub-projects.
- The repo has zero Firestore emulator/test-double infrastructure. Any step that needs a real Firestore round-trip is verified manually on the already-confirmed-working `Pixel_9` emulator (package `com.saimega.vinayakacablenetwork`), not via an automated test. This is a real constraint, not a shortcut — building Firestore test infra is out of scope for this sub-project.

---

### Task 1: Fix broken unit test infrastructure

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/test/java/com/saimega/vinayakacablenetwork/ExampleUnitTest.kt` (verify only, no content change expected)

**Interfaces:**
- Produces: a working `testDebugUnitTest` Gradle task that all later tasks' JUnit tests run under.

- [ ] **Step 1: Confirm the current failure**

Run: `./gradlew testDebugUnitTest`
Expected: FAIL with `e: ... Unresolved reference 'junit'.` (already confirmed during planning — this step is to have the failure on record before the fix).

- [ ] **Step 2: Add the JUnit test dependency**

In `app/build.gradle.kts`, inside the `dependencies { ... }` block, add:

```kotlin
    testImplementation(libs.junit)
```

Place it as the last line inside `dependencies { }`, before the closing `}`.

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, `ExampleUnitTest.addition_isCorrect` passes.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "$(cat <<'EOF'
Fix broken unit test task: wire up missing junit dependency

testDebugUnitTest failed to compile — app/build.gradle.kts declared
no test dependencies at all, so the existing ExampleUnitTest.kt could
not resolve org.junit.Test / assertEquals.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 2: Pure carry-forward logic with TDD

**Files:**
- Create: `app/src/main/java/com/saimega/vinayakacablenetwork/BillingCycle.kt`
- Test: `app/src/test/java/com/saimega/vinayakacablenetwork/BillingCycleTest.kt`

**Interfaces:**
- Produces:
  - `data class CustomerBillingState(val id: String, val connectionStatus: String, val pendingAmount: Double, val monthlyCharge: Double)`
  - `data class BilledCustomerUpdate(val id: String, val newPendingAmount: Double)`
  - `object BillingCycle { fun computeMonthlyBillUpdates(customers: List<CustomerBillingState>): List<BilledCustomerUpdate> }`
- Consumed by: Task 4 (`CustomerRepository.generateMonthlyBills`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/saimega/vinayakacablenetwork/BillingCycleTest.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingCycleTest {

    @Test
    fun `partial customer carries forward remainder plus new monthly charge`() {
        val customers = listOf(
            CustomerBillingState(
                id = "c1",
                connectionStatus = "active",
                pendingAmount = 50.0,
                monthlyCharge = 200.0
            )
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(1, updates.size)
        assertEquals("c1", updates[0].id)
        assertEquals(250.0, updates[0].newPendingAmount, 0.001)
    }

    @Test
    fun `fully paid customer still gets billed for the new month`() {
        val customers = listOf(
            CustomerBillingState(
                id = "c2",
                connectionStatus = "active",
                pendingAmount = 0.0,
                monthlyCharge = 200.0
            )
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(1, updates.size)
        assertEquals(200.0, updates[0].newPendingAmount, 0.001)
    }

    @Test
    fun `inactive customer is excluded entirely`() {
        val customers = listOf(
            CustomerBillingState(
                id = "c3",
                connectionStatus = "inactive",
                pendingAmount = 100.0,
                monthlyCharge = 200.0
            )
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(0, updates.size)
    }

    @Test
    fun `mixed batch only bills active customers`() {
        val customers = listOf(
            CustomerBillingState("active-1", "active", 50.0, 200.0),
            CustomerBillingState("inactive-1", "inactive", 999.0, 200.0),
            CustomerBillingState("active-2", "active", 0.0, 150.0)
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(2, updates.size)
        assertEquals(250.0, updates.first { it.id == "active-1" }.newPendingAmount, 0.001)
        assertEquals(150.0, updates.first { it.id == "active-2" }.newPendingAmount, 0.001)
    }

    @Test
    fun `connection status match is case insensitive`() {
        val customers = listOf(
            CustomerBillingState("c4", "ACTIVE", 0.0, 200.0)
        )

        val updates = BillingCycle.computeMonthlyBillUpdates(customers)

        assertEquals(1, updates.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.saimega.vinayakacablenetwork.BillingCycleTest"`
Expected: FAIL — `Unresolved reference: BillingCycle` (the class doesn't exist yet).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/saimega/vinayakacablenetwork/BillingCycle.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

/**
 * Snapshot of the fields needed to compute one customer's next billing cycle.
 * Deliberately a plain data class (not CustomerModel) so this stays testable
 * without touching Firestore.
 */
data class CustomerBillingState(
    val id: String,
    val connectionStatus: String,
    val pendingAmount: Double,
    val monthlyCharge: Double
)

/** Result of billing one customer: their new pendingAmount for the new cycle. */
data class BilledCustomerUpdate(
    val id: String,
    val newPendingAmount: Double
)

object BillingCycle {

    /**
     * Carry-forward rule: whatever is left unpaid (0 if fully paid, the
     * remainder otherwise) plus this month's monthlyCharge. Deactivated
     * customers are excluded — they accrue nothing while inactive.
     */
    fun computeMonthlyBillUpdates(customers: List<CustomerBillingState>): List<BilledCustomerUpdate> {
        return customers
            .filter { it.connectionStatus.equals("active", ignoreCase = true) }
            .map { BilledCustomerUpdate(id = it.id, newPendingAmount = it.pendingAmount + it.monthlyCharge) }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.saimega.vinayakacablenetwork.BillingCycleTest"`
Expected: BUILD SUCCESSFUL, all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/BillingCycle.kt app/src/test/java/com/saimega/vinayakacablenetwork/BillingCycleTest.kt
git commit -m "$(cat <<'EOF'
Add pure billing carry-forward calculation with tests

Extracts the carry-forward arithmetic (unpaid remainder + new
monthlyCharge, active customers only) into a Firestore-free pure
function so it's unit-testable.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 3: Add `lastBilledMonth` to CustomerModel

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerModel.kt`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt:38-54` (`mapDocToCustomer`)

**Interfaces:**
- Consumes: none new.
- Produces: `CustomerModel.lastBilledMonth: String` — read by nothing yet in this task; Task 4 doesn't need it either (it reads billing state fresh from Firestore documents directly), but the field must exist on the model so the app compiles against the new Firestore field consistently and so a later screen (outside this plan) can display "last billed" if needed.

- [ ] **Step 1: Add the field to CustomerModel**

In `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerModel.kt`, add `lastBilledMonth` right after the existing `lastPaidMonth` field (line 19):

```kotlin
    val lastPaidMonth: String = "",
    val lastBilledMonth: String = "",
```

- [ ] **Step 2: Read the field in CustomerRepository.mapDocToCustomer**

In `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt`, inside `mapDocToCustomer` (around line 52), add a line right after the existing `lastPaidMonth` read:

```kotlin
            lastPaidMonth = doc.getString("lastPaidMonth") ?: "",
            lastBilledMonth = doc.getString("lastBilledMonth") ?: ""
```

(The `CustomerModel(...)` constructor call's closing `)` on the next line stays as-is — this just adds one more named argument before it.)

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (this is a pure data/model change, no behavior change yet — nothing to manually test in the app for this step).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/CustomerModel.kt app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt
git commit -m "$(cat <<'EOF'
Add lastBilledMonth field to CustomerModel

Separate from lastPaidMonth (which tracks payment history) — this
tracks which month a monthlyCharge was last added, so the next task's
Generate Bills action can eventually report it. A customer can be
billed for a month without having paid it, so the two fields must not
be conflated.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 4: `generateMonthlyBills()` on CustomerRepository

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt`

**Interfaces:**
- Consumes: `BillingCycle.computeMonthlyBillUpdates(List<CustomerBillingState>): List<BilledCustomerUpdate>` (Task 2), `CustomerBillingState`, `BilledCustomerUpdate`.
- Produces:
  - `sealed class BillingRunResult` with `data class Success(val customersBilled: Int)`, `object AlreadyRun`, `data class Failure(val exception: Exception)`.
  - `suspend fun CustomerRepository.generateMonthlyBills(monthKey: String): BillingRunResult` — consumed by Task 5 (`DashboardActivity`).

- [ ] **Step 1: Add the `BillingRunResult` sealed class**

In `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt`, add this at the top level of the file (outside the `CustomerRepository` class, e.g. right before `class CustomerRepository {`):

```kotlin
sealed class BillingRunResult {
    data class Success(val customersBilled: Int) : BillingRunResult()
    object AlreadyRun : BillingRunResult()
    data class Failure(val exception: Exception) : BillingRunResult()
}
```

- [ ] **Step 2: Add `generateMonthlyBills` to CustomerRepository**

Inside the `CustomerRepository` class, add this method (a good spot is right after `submitPayment`, before `rebuildBillingFromPayments`):

```kotlin
    // =========================
    // MONTHLY BILL GENERATION
    // =========================

    /**
     * Advances every active customer's billing cycle: pendingAmount becomes
     * whatever was left unpaid plus this month's monthlyCharge (the
     * carry-forward rule). Deactivated customers are skipped entirely.
     *
     * Idempotent per [monthKey]: if meta/billing.lastGeneratedMonth already
     * equals [monthKey], this is a no-op that returns [BillingRunResult.AlreadyRun].
     */
    suspend fun generateMonthlyBills(monthKey: String): BillingRunResult {
        return try {
            val metaRef = db.collection("meta").document("billing")
            val metaSnap = metaRef.get(Source.SERVER).await()
            if (metaSnap.getString("lastGeneratedMonth") == monthKey) {
                return BillingRunResult.AlreadyRun
            }

            val snapshot = db.collection("customers")
                .whereEqualTo("Connection Status", "active")
                .get(Source.SERVER)
                .await()

            val states = snapshot.documents.map { doc ->
                CustomerBillingState(
                    id = doc.id,
                    connectionStatus = doc.getString("Connection Status") ?: "active",
                    pendingAmount = (doc.get("pendingAmount") as? Number)?.toDouble() ?: 0.0,
                    monthlyCharge = (doc.get("monthlyCharge") as? Number)?.toDouble() ?: 0.0
                )
            }

            val updates = BillingCycle.computeMonthlyBillUpdates(states)

            updates.chunked(500).forEach { chunk ->
                val batch = db.batch()
                for (update in chunk) {
                    val ref = db.collection("customers").document(update.id)
                    batch.update(ref, mapOf(
                        "pendingAmount" to update.newPendingAmount,
                        "lastBilledMonth" to monthKey,
                        "status" to "unpaid",
                        "paymentStatus" to "Unpaid"
                    ))
                }
                batch.commit().await()
            }

            metaRef.set(
                mapOf(
                    "lastGeneratedMonth" to monthKey,
                    "lastGeneratedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()

            BillingRunResult.Success(updates.size)
        } catch (e: Exception) {
            BillingRunResult.Failure(e)
        }
    }
```

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification on the emulator**

No automated Firestore test exists in this repo (see Global Constraints). Verify by hand using the `Pixel_9` AVD (already confirmed running/working):

```bash
SDK=/Users/nagneelam/Library/Android/sdk
"$SDK/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

Then, from a Kotlin scratch call site or the Firebase console: pick one existing active customer, note its `pendingAmount` and `monthlyCharge`. This task alone doesn't add a UI trigger yet (that's Task 5) — so defer the full end-to-end manual check to Task 5's verification step, which exercises this method through the real UI. Skip a standalone check here; do not fabricate one.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt
git commit -m "$(cat <<'EOF'
Add generateMonthlyBills to CustomerRepository

Wraps BillingCycle's pure carry-forward math with the real Firestore
read/write: fetches active customers, computes new pendingAmount per
customer, batch-writes (chunked at 500 per Firestore's batch limit),
and guards against a double run for the same month via meta/billing.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 5: "Generate Bills" Quick Action on Dashboard

**Files:**
- Modify: `app/src/main/res/layout/activity_dashboard.xml`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`

**Interfaces:**
- Consumes: `CustomerRepository.generateMonthlyBills(monthKey: String): BillingRunResult` (Task 4), `BillingRunResult.Success/AlreadyRun/Failure` (Task 4).
- Produces: nothing consumed by later tasks — this is the UI entry point.

- [ ] **Step 1: Add the tile to the dashboard layout**

In `app/src/main/res/layout/activity_dashboard.xml`, right after the second Quick Actions row's closing `</LinearLayout>` (immediately after line 715, before the `</LinearLayout>` that closes the whole quick-actions container on line 717), add a third row:

```xml
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="9dp"
                    android:orientation="horizontal">

                    <LinearLayout android:id="@+id/qaGenerateBills" style="@style/QuickActionCell" android:layout_marginEnd="0dp">
                        <TextView style="@style/QuickActionIcon" android:text="🧾" android:background="@drawable/cm_bg_soft_blue" />
                        <TextView style="@style/QuickActionLabel" android:text="Generate Bills" />
                    </LinearLayout>
                </LinearLayout>
```

- [ ] **Step 2: Add the import for Date**

In `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`, add to the imports (alphabetically near the other `java.util` imports):

```kotlin
import java.util.Date
```

- [ ] **Step 3: Wire the click listener**

In `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`, inside `setupQuickActions()` (after the existing `findViewById<View>(R.id.qaReport)...` block, before the closing `}` of the function), add:

```kotlin
        findViewById<View>(R.id.qaGenerateBills).setOnClickListener {
            showGenerateBillsConfirmation()
        }
```

- [ ] **Step 4: Add the confirmation dialog and run logic**

In the same file, add these two new private methods (a good spot is right after `setupQuickActions()`):

```kotlin
    private fun showGenerateBillsConfirmation() {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Generate Bills")
            .setMessage("Generate bills for $monthLabel for all active customers?")
            .setPositiveButton("Generate") { _, _ -> runGenerateBills(monthKey) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runGenerateBills(monthKey: String) {
        lifecycleScope.launch {
            val result = CustomerRepository().generateMonthlyBills(monthKey)
            val message = when (result) {
                is BillingRunResult.Success -> "Bills generated for ${result.customersBilled} customers."
                is BillingRunResult.AlreadyRun -> "Bills for this month were already generated."
                is BillingRunResult.Failure -> "Error: ${result.exception.message}"
            }
            Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_LONG).show()
        }
    }
```

- [ ] **Step 5: Build and install**

```bash
./gradlew assembleDebug
SDK=/Users/nagneelam/Library/Android/sdk
"$SDK/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: BUILD SUCCESSFUL, install succeeds.

- [ ] **Step 6: Manual end-to-end verification on the emulator**

```bash
SDK=/Users/nagneelam/Library/Android/sdk
PKG=com.saimega.vinayakacablenetwork
"$SDK/platform-tools/adb" shell am start -n "$PKG/$PKG.LoginActivity"
```

Log in, land on Dashboard, note one active customer's current `pendingAmount` and `monthlyCharge` (via Customer Details screen). Tap the new "Generate Bills" tile, confirm the dialog, confirm the Toast reads "Bills generated for N customers." Re-open that same customer's details and confirm `pendingAmount` now equals `old pendingAmount + monthlyCharge`. Tap "Generate Bills" again in the same month; confirm the Toast now reads "Bills for this month were already generated." and the customer's `pendingAmount` did not change a second time.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/activity_dashboard.xml app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt
git commit -m "$(cat <<'EOF'
Wire Generate Bills quick action into Dashboard

Confirmation dialog shows the target month, then calls
CustomerRepository.generateMonthlyBills and reports the result via
Toast. Visible to both ADMIN and EMPLOYEE roles — no visibility gate
needed since the repository method itself guards against double-runs.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 6: Remove hidden test-data trigger

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt:97-103`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt:349-357` (`loadTestData`)

**Interfaces:**
- Consumes: none.
- Produces: none — pure removal. `importCustomerBatch` (used by `DataImportActivity`) is untouched.

- [ ] **Step 1: Remove the long-click listener**

In `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`, inside `bindViews()`, delete this block entirely:

```kotlin
        findViewById<TextView>(R.id.tvProfileInitial).setOnLongClickListener {
            lifecycleScope.launch {
                CustomerRepository().loadTestData()
                Toast.makeText(this@DashboardActivity, "Test Data Loaded", Toast.LENGTH_SHORT).show()
            }
            true
        }
```

(Leave the two lines right before it — setting `tvProfileInitial.text` — untouched.)

- [ ] **Step 2: Remove `loadTestData` from CustomerRepository**

In `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt`, delete the entire `loadTestData()` function (the one with the 4 hardcoded `CustomerModel` test records). Do **not** touch `importCustomerBatch` — it's still called by `DataImportActivity.importToFirebase()`.

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification on the emulator**

Reinstall and long-press the profile avatar circle on Dashboard (top-right, the single-letter badge). Confirm nothing happens — no Toast, no Firestore write.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt app/src/main/java/com/saimega/vinayakacablenetwork/CustomerRepository.kt
git commit -m "$(cat <<'EOF'
Remove hidden test-data trigger from production Dashboard

Long-pressing the profile avatar silently wrote 4 hardcoded fake
customer records into the live production Firestore customers
collection, with no confirmation and no build-type gate.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 7: Fix the Partial filter

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerListActivity.kt`

**Interfaces:**
- Consumes: `CustomerViewModel.init(status: String)` (already supports `"partial"` — see below, no change needed there).
- Produces: none.

Investigation note (already confirmed during planning): `CustomerViewModel.attachSnapshotListener()` already has a working `"partial"` branch (line 113: `"partial" -> allDocs.filter { it.status.equals("partial", true) }`). The actual bug is entirely in `CustomerListActivity.onCreate`, which forces `type` into only `{"paid", "unpaid", "all"}` before it ever reaches the ViewModel — so `"partial"` silently becomes `"all"`.

- [ ] **Step 1: Allow "partial" through the type validation**

In `app/src/main/java/com/saimega/vinayakacablenetwork/CustomerListActivity.kt`, change line 83:

```kotlin
        if (type !in listOf("paid", "unpaid", "all")) type = "all"
```

to:

```kotlin
        if (type !in listOf("paid", "unpaid", "partial", "all")) type = "all"
```

- [ ] **Step 2: Add a chip-visibility branch for "partial"**

In the same file, in the `when (type) { ... }` block (lines 94-116), add a new branch. There is no dedicated "Partial" chip in the layout (that's deferred to the UI redesign sub-project), so for this filter hide the whole status chip row rather than mislabel an existing chip:

```kotlin
            "partial" -> {
                chipAll.visibility     = View.GONE
                chipUnpaid.visibility  = View.GONE
                chipPaid.visibility    = View.GONE
                btnDownloadPdfBar.visibility = View.VISIBLE
            }
```

Add this branch right before the existing `else -> { ... }` branch (which handles `"all"`).

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification on the emulator**

Reinstall, open Dashboard, tap the "Partially Paid" stat tile. Confirm the Customer List screen now shows only customers with `status == "partial"` (not the full customer list), and no status chips are visible at the top.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/CustomerListActivity.kt
git commit -m "$(cat <<'EOF'
Fix Partially Paid dashboard tile silently showing all customers

CustomerListActivity forced its FILTER_TYPE into {paid, unpaid, all}
before it ever reached CustomerViewModel, so a PARTIAL intent extra
was silently downgraded to ALL. CustomerViewModel's partial filter
already worked correctly — the bug was entirely in the type whitelist.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 8: Delete the dead scheduled billing function

**Files:**
- Delete: `firebase_functions/index.js`
- Delete: `firebase_functions/` (the directory, if this is its only file)

**Interfaces:**
- Consumes: none.
- Produces: none.

- [ ] **Step 1: Confirm nothing references this directory**

Run: `grep -rn "firebase_functions" firebase.json package.json 2>/dev/null`
Expected: no output (confirmed during planning — `firebase.json`'s `functions.source` points at `functions/`, not `firebase_functions/`).

- [ ] **Step 2: Delete the directory**

```bash
git rm -r firebase_functions
```

- [ ] **Step 3: Verify the Android build is unaffected**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (this directory is outside the Gradle module and was never part of the Android build).

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
Remove dead scheduled billing Cloud Function

firebase_functions/index.js contained a monthlyRollover scheduled
function that was never deployed (firebase.json points Functions
deployment at functions/, an unrelated empty boilerplate directory).
Superseded by the manual Generate Bills action added in this branch.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

## Plan Self-Review Notes

- **Spec coverage:** data model change → Task 3; Generate Bills flow (guard, batch, carry-forward math) → Tasks 2 + 4; UI + confirmation → Task 5; cleanup (test-data trigger, Partial filter, dead function file) → Tasks 6, 7, 8. All spec sections have a task.
- **Type consistency:** `BillingRunResult`, `CustomerBillingState`, `BilledCustomerUpdate` are defined once (Tasks 2 and 4) and referenced with identical names/signatures in Task 5 — checked.
- **No placeholders:** every step has literal code or an exact command; no "add tests for the above" or "similar to Task N" shortcuts.
