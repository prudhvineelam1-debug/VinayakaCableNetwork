# Add Employee & Role-Based Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin create employee accounts, edit an existing account's role/active status from a real screen (not raw Firestore edits), and restrict "Add Customer" to ADMIN only.

**Architecture:** `AuthRepository` gains `listUsers()`, `createEmployee()`, `updateUserRoleAndActive()` on top of sub-project 1's Firestore `users` collection. A new `EmployeeListActivity` (reachable only from a new ADMIN-only "Team" section in Settings) renders that list with simple inflated rows (no RecyclerView — a handful of staff, not worth the machinery) and two dialogs: edit (role + active toggle, with a guard against deactivating yourself) and add (username/name/password/role, guarded against overwriting an existing username).

**Tech Stack:** Kotlin, Firebase Firestore (existing SDK), Kotlin Coroutines, `android.widget.Switch`/`AutoCompleteTextView` (plain framework widgets, matching the app's existing dialog style in `SettingsActivity`/`CustomerDetailsActivity`).

**Spec:** `docs/superpowers/specs/2026-09-13-employee-management-design.md`

## Global Constraints

- Role values are the literal stored strings `"ADMIN"`, `"EMPLOYEE"`, `"TECHNICIAN"` used directly as dropdown options — no separate display-name mapping layer (matches how the rest of the app already treats role as a raw constant, e.g. `DashboardActivity`'s existing `when` block).
- Only Reports, Generate Bills, and Data Import stay open to every role — this plan restricts nothing there. Only "Add Customer" is ADMIN-gated.
- No protection against deactivating the last remaining admin — only against deactivating the currently logged-in account (yourself).
- Firestore document ID for a user is always the lowercased username — `createEmployee()` must check for an existing document at that ID before writing, never overwrite silently.

---

### Task 1: AuthRepository — list, create, update employees

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/AuthRepository.kt`

**Interfaces:**
- Consumes: `PasswordHasher.generateSalt()`, `PasswordHasher.hash()` (existing, from sub-project 1).
- Produces: `data class UserAccount(val username: String, val name: String, val role: String, val active: Boolean)`; `sealed class CreateEmployeeResult` (`Success`, `UsernameAlreadyExists`, `Failure(exception)`); `AuthRepository.listUsers(): List<UserAccount>`; `AuthRepository.createEmployee(username: String, name: String, password: String, role: String): CreateEmployeeResult`; `AuthRepository.updateUserRoleAndActive(username: String, role: String, active: Boolean): Boolean` — all consumed by Task 2 (`EmployeeListActivity`).

- [ ] **Step 1: Add the new types and methods**

In `app/src/main/java/com/saimega/vinayakacablenetwork/AuthRepository.kt`, add this new top-level data class and sealed class right after the existing `ChangePasswordResult` sealed class:

```kotlin
data class UserAccount(
    val username: String,
    val name: String,
    val role: String,
    val active: Boolean
)

sealed class CreateEmployeeResult {
    object Success : CreateEmployeeResult()
    object UsernameAlreadyExists : CreateEmployeeResult()
    data class Failure(val exception: Exception) : CreateEmployeeResult()
}
```

Add these three methods inside the `AuthRepository` class, right after `changePassword()`:

```kotlin
    suspend fun listUsers(): List<UserAccount> {
        val snapshot = db.collection("users").get(Source.SERVER).await()
        return snapshot.documents.map { doc ->
            UserAccount(
                username = doc.getString("username") ?: doc.id,
                name = doc.getString("name") ?: doc.id,
                role = doc.getString("role") ?: "EMPLOYEE",
                active = doc.getBoolean("active") ?: true
            )
        }
    }

    suspend fun createEmployee(username: String, name: String, password: String, role: String): CreateEmployeeResult {
        return try {
            val docId = username.trim().lowercase()
            val ref = db.collection("users").document(docId)
            val existing = ref.get(Source.SERVER).await()
            if (existing.exists()) return CreateEmployeeResult.UsernameAlreadyExists

            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hash(password, salt)
            ref.set(mapOf(
                "username" to docId,
                "name" to name,
                "passwordHash" to hash,
                "passwordSalt" to salt,
                "role" to role,
                "active" to true,
                "createdAt" to System.currentTimeMillis()
            )).await()
            CreateEmployeeResult.Success
        } catch (e: Exception) {
            CreateEmployeeResult.Failure(e)
        }
    }

    suspend fun updateUserRoleAndActive(username: String, role: String, active: Boolean): Boolean {
        return try {
            db.collection("users").document(username.trim().lowercase())
                .update(mapOf("role" to role, "active" to active))
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (No manual verification yet — nothing calls these until Task 2.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/AuthRepository.kt
git commit -m "$(cat <<'EOF'
Add listUsers/createEmployee/updateUserRoleAndActive to AuthRepository

listUsers() reads every account for the upcoming employee-management
screen. createEmployee() checks for an existing document at the
target username first — since the username is the Firestore document
ID, proceeding without this check would silently overwrite an
existing account's credentials. updateUserRoleAndActive() backs the
edit-employee dialog's role/active toggle.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 2: EmployeeListActivity — list, edit, and add, reachable from Settings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-te/strings.xml`
- Create: `app/src/main/res/layout/item_employee_row.xml`
- Create: `app/src/main/res/layout/dialog_edit_employee.xml`
- Create: `app/src/main/res/layout/dialog_add_employee.xml`
- Create: `app/src/main/res/layout/activity_employee_list.xml`
- Create: `app/src/main/java/com/saimega/vinayakacablenetwork/EmployeeListActivity.kt`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AuthRepository.listUsers()`, `AuthRepository.createEmployee()`, `AuthRepository.updateUserRoleAndActive()`, `UserAccount`, `CreateEmployeeResult` (Task 1).
- Produces: `EmployeeListActivity` — launched by `SettingsActivity`.

- [ ] **Step 1: Add the new strings**

In `app/src/main/res/values/strings.xml`, add right after the existing `<string name="incorrect_current_password">Current password is incorrect</string>` line:

```xml
    <string name="team_section">Team</string>
    <string name="manage_employees">Manage Employees</string>
    <string name="add_employee">Add Employee</string>
    <string name="edit_employee">Edit Employee</string>
    <string name="role_label">Role</string>
    <string name="active_label">Active</string>
    <string name="initial_password_hint">Initial Password</string>
    <string name="confirm_password_hint">Confirm Password</string>
    <string name="username_already_exists">Username already exists</string>
    <string name="employee_added_successfully">Employee added successfully</string>
    <string name="employee_updated_successfully">Employee updated successfully</string>
    <string name="cannot_deactivate_self">You cannot deactivate your own account</string>
    <string name="no_permission_message">You don\'t have permission to access this screen</string>
    <string name="status_active">Active</string>
    <string name="status_inactive">Inactive</string>
```

In `app/src/main/res/values-te/strings.xml`, add right before the closing `</resources>` tag:

```xml
    <string name="team_section">టీమ్</string>
    <string name="manage_employees">ఉద్యోగులను నిర్వహించండి</string>
    <string name="add_employee">ఉద్యోగిని జోడించండి</string>
    <string name="edit_employee">ఉద్యోగిని సవరించండి</string>
    <string name="role_label">పాత్ర</string>
    <string name="active_label">యాక్టివ్</string>
    <string name="initial_password_hint">ప్రారంభ పాస్‌వర్డ్</string>
    <string name="confirm_password_hint">పాస్‌వర్డ్ నిర్ధారించండి</string>
    <string name="username_already_exists">యూజర్‌నేమ్ ఇప్పటికే ఉంది</string>
    <string name="employee_added_successfully">ఉద్యోగి విజయవంతంగా జోడించబడ్డారు</string>
    <string name="employee_updated_successfully">ఉద్యోగి విజయవంతంగా నవీకరించబడ్డారు</string>
    <string name="cannot_deactivate_self">మీరు మీ స్వంత ఖాతాను నిష్క్రియం చేయలేరు</string>
    <string name="no_permission_message">ఈ స్క్రీన్‌ను యాక్సెస్ చేయడానికి మీకు అనుమతి లేదు</string>
    <string name="status_active">యాక్టివ్</string>
    <string name="status_inactive">ఇన్యాక్టివ్</string>
```

- [ ] **Step 2: Create the employee row layout**

Create `app/src/main/res/layout/item_employee_row.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    app:cardBackgroundColor="@color/dashboard_card"
    app:cardCornerRadius="16dp"
    app:strokeWidth="1dp"
    app:strokeColor="@color/dashboard_border">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tvEmployeeName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="15sp"
                android:textStyle="bold"
                android:textColor="@color/dashboard_text_primary"
                tools:text="Ravi Kumar" />

            <TextView
                android:id="@+id/tvEmployeeUsernameRole"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:textSize="12sp"
                android:textColor="@color/dashboard_text_secondary"
                tools:text="ravi · EMPLOYEE" />

        </LinearLayout>

        <TextView
            android:id="@+id/tvEmployeeStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textStyle="bold"
            tools:text="Active" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: Create the edit-employee dialog layout**

Create `app/src/main/res/layout/dialog_edit_employee.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="24dp"
    android:paddingTop="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/role_label"
        android:textColor="@color/dashboard_text_secondary"
        android:layout_marginBottom="4dp" />

    <AutoCompleteTextView
        android:id="@+id/actvRole"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="none"
        android:layout_marginBottom="16dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/active_label" />

        <Switch
            android:id="@+id/switchActive"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

    </LinearLayout>

    <TextView
        android:id="@+id/tvSelfGuardNote"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/cannot_deactivate_self"
        android:textColor="@color/accent_red"
        android:textSize="12sp"
        android:visibility="gone" />

</LinearLayout>
```

- [ ] **Step 4: Create the add-employee dialog layout**

Create `app/src/main/res/layout/dialog_add_employee.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="24dp"
    android:paddingTop="16dp">

    <EditText
        android:id="@+id/etNewUsername"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/hint_username"
        android:layout_marginBottom="12dp" />

    <EditText
        android:id="@+id/etNewFullName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/hint_full_name"
        android:layout_marginBottom="12dp" />

    <EditText
        android:id="@+id/etNewPassword"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/initial_password_hint"
        android:inputType="textPassword"
        android:layout_marginBottom="12dp" />

    <EditText
        android:id="@+id/etNewConfirmPassword"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/confirm_password_hint"
        android:inputType="textPassword"
        android:layout_marginBottom="12dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/role_label"
        android:textColor="@color/dashboard_text_secondary"
        android:layout_marginBottom="4dp" />

    <AutoCompleteTextView
        android:id="@+id/actvNewRole"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="none" />

</LinearLayout>
```

- [ ] **Step 5: Create the Employee List screen layout**

Create `app/src/main/res/layout/activity_employee_list.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/dashboard_bg">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@android:color/transparent"
        app:elevation="0dp">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="@string/manage_employees"
            app:titleTextColor="#FFFFFF"
            app:navigationIcon="?attr/homeAsUpIndicator"
            app:navigationIconTint="#FFFFFF" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="20dp">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnAddEmployee"
                style="@style/Widget.Material3.Button"
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:layout_marginBottom="20dp"
                android:text="@string/add_employee" />

            <LinearLayout
                android:id="@+id/employeeListContainer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 6: Create EmployeeListActivity.kt**

Create `app/src/main/java/com/saimega/vinayakacablenetwork/EmployeeListActivity.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class EmployeeListActivity : BaseActivity() {

    private lateinit var employeeListContainer: LinearLayout
    private lateinit var btnAddEmployee: MaterialButton
    private val authRepository = AuthRepository()
    private var currentUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee_list)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        employeeListContainer = findViewById(R.id.employeeListContainer)
        btnAddEmployee = findViewById(R.id.btnAddEmployee)

        currentUsername = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("username", "") ?: ""

        btnAddEmployee.setOnClickListener {
            showAddEmployeeDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEmployees()
    }

    private fun loadEmployees() {
        lifecycleScope.launch {
            val employees = authRepository.listUsers()
            renderEmployeeList(employees)
        }
    }

    private fun renderEmployeeList(employees: List<UserAccount>) {
        employeeListContainer.removeAllViews()
        for (employee in employees) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_employee_row, employeeListContainer, false)
            row.findViewById<TextView>(R.id.tvEmployeeName).text = employee.name
            row.findViewById<TextView>(R.id.tvEmployeeUsernameRole).text = "${employee.username} · ${employee.role}"
            val tvStatus = row.findViewById<TextView>(R.id.tvEmployeeStatus)
            tvStatus.text = if (employee.active) getString(R.string.status_active) else getString(R.string.status_inactive)
            tvStatus.setTextColor(
                ContextCompat.getColor(this, if (employee.active) R.color.accent_green else R.color.accent_red)
            )
            row.setOnClickListener {
                showEditEmployeeDialog(employee)
            }
            employeeListContainer.addView(row)
        }
    }

    private fun showEditEmployeeDialog(employee: UserAccount) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_employee, null)
        val actvRole = dialogView.findViewById<AutoCompleteTextView>(R.id.actvRole)
        val switchActive = dialogView.findViewById<Switch>(R.id.switchActive)
        val tvSelfGuardNote = dialogView.findViewById<TextView>(R.id.tvSelfGuardNote)

        val roles = listOf("ADMIN", "EMPLOYEE", "TECHNICIAN")
        actvRole.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, roles))
        actvRole.setText(employee.role, false)
        switchActive.isChecked = employee.active

        val isSelf = employee.username == currentUsername
        if (isSelf) {
            switchActive.isEnabled = false
            tvSelfGuardNote.visibility = View.VISIBLE
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_employee)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newRole = actvRole.text.toString().ifBlank { employee.role }
                val newActive = if (isSelf) true else switchActive.isChecked

                lifecycleScope.launch {
                    val success = authRepository.updateUserRoleAndActive(employee.username, newRole, newActive)
                    if (success) {
                        Toast.makeText(this@EmployeeListActivity, getString(R.string.employee_updated_successfully), Toast.LENGTH_SHORT).show()
                        loadEmployees()
                    }
                }
            }
            .show()
    }

    private fun showAddEmployeeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_employee, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.etNewUsername)
        val etFullName = dialogView.findViewById<EditText>(R.id.etNewFullName)
        val etPassword = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etNewConfirmPassword)
        val actvRole = dialogView.findViewById<AutoCompleteTextView>(R.id.actvNewRole)

        val roles = listOf("ADMIN", "EMPLOYEE", "TECHNICIAN")
        actvRole.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, roles))
        actvRole.setText("EMPLOYEE", false)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_employee)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add_employee) { _, _ ->
                val username = etUsername.text.toString().trim()
                val name = etFullName.text.toString().trim()
                val password = etPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()
                val role = actvRole.text.toString().ifBlank { "EMPLOYEE" }

                if (username.isEmpty() || name.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, getString(R.string.please_enter_all_required_fields), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (password != confirmPassword) {
                    Toast.makeText(this, getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    when (authRepository.createEmployee(username, name, password, role)) {
                        is CreateEmployeeResult.Success -> {
                            Toast.makeText(this@EmployeeListActivity, getString(R.string.employee_added_successfully), Toast.LENGTH_SHORT).show()
                            loadEmployees()
                        }
                        is CreateEmployeeResult.UsernameAlreadyExists -> {
                            Toast.makeText(this@EmployeeListActivity, getString(R.string.username_already_exists), Toast.LENGTH_SHORT).show()
                        }
                        is CreateEmployeeResult.Failure -> {
                            Toast.makeText(this@EmployeeListActivity, getString(R.string.error_prefix, "unknown"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }
}
```

- [ ] **Step 7: Add the "Team" section to Settings**

In `app/src/main/res/layout/activity_settings.xml`, add a new card right after the closing `</com.google.android.material.card.MaterialCardView>` of the Account card (i.e. between the Account card and the Appearance card):

```xml
            <!-- Team (ADMIN only) -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardTeamSection"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
                android:visibility="gone"
                app:cardBackgroundColor="@color/dashboard_card"
                app:cardCornerRadius="20dp"
                app:strokeWidth="1dp"
                app:strokeColor="@color/dashboard_border">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/team_section"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:letterSpacing="0.1"
                        android:textColor="@color/dashboard_text_secondary"
                        android:layout_marginBottom="12dp" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnManageEmployees"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="match_parent"
                        android:layout_height="48dp"
                        android:text="@string/manage_employees" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 8: Wire the Team section in SettingsActivity.kt**

In `app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt`, add these imports:

```kotlin
import android.view.View
import com.google.android.material.card.MaterialCardView
```

Add a new property declaration:

```kotlin
    private lateinit var cardTeamSection: MaterialCardView
    private lateinit var btnManageEmployees: MaterialButton
```

In `bindViews()`, add:

```kotlin
        cardTeamSection = findViewById(R.id.cardTeamSection)
        btnManageEmployees = findViewById(R.id.btnManageEmployees)
```

In `onCreate()`, add a call to a new `bindTeamSection()` right after the existing `bindAccountSection()` call:

```kotlin
        bindTeamSection()
```

Add this new private method (a good spot is right after `bindAccountSection()`'s closing brace, before `performLogout()`):

```kotlin
    private fun bindTeamSection() {
        val role = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("user_role", "EMPLOYEE") ?: "EMPLOYEE"
        cardTeamSection.visibility = if (role == "ADMIN") View.VISIBLE else View.GONE

        btnManageEmployees.setOnClickListener {
            startActivity(Intent(this, EmployeeListActivity::class.java))
        }
    }
```

- [ ] **Step 9: Register EmployeeListActivity in the manifest**

In `app/src/main/AndroidManifest.xml`, add this entry after the `<!-- SETTINGS -->` `SettingsActivity` entry:

```xml
        <!-- EMPLOYEE MANAGEMENT (ADMIN only) -->
        <activity
            android:name=".EmployeeListActivity"
            android:exported="false" />
```

- [ ] **Step 10: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Manual verification on the emulator — list and edit**

Log in as `admin`. Open Settings: confirm a new "Team" section with "Manage Employees" appears. Tap it: confirm the Employee List screen opens showing all 3 seeded accounts (admin/ravi/tech) with correct name, username, role, and an "Active" status in green. Tap the `admin` row (your own account): confirm the Active switch is disabled and the warning note is visible; tap Cancel. Tap the `ravi` row: confirm the switch is enabled, change role to `TECHNICIAN`, Save: confirm the success Toast and that the list refreshes showing the new role. Log out, log in as `ravi` with their existing password: confirm Dashboard now reflects the TECHNICIAN role (no admin financial section, no employee financial section either, matching `applyRoleVisibility()`'s existing `when` — TECHNICIAN gets neither). Log back in as `admin` and change `ravi` back to `EMPLOYEE` to keep later tests consistent with earlier sub-projects' assumptions.

- [ ] **Step 12: Manual verification on the emulator — add employee**

From Employee List, tap "Add Employee". Leave fields blank and submit: confirm "Please enter all required fields". Fill in username `newstaff`, name, password `pass123`, confirm `different`: confirm "New passwords do not match". Fix the confirmation to match, submit: confirm "Employee added successfully" and the new row appears. Log out, log in as `newstaff`/`pass123`: confirm it works. Log back in as `admin`, try adding another employee with username `ravi` (already exists): confirm "Username already exists", and confirm `ravi`'s existing password still works afterward (unchanged).

- [ ] **Step 13: Manual verification — non-admin can't see Team**

Log in as `newstaff` (EMPLOYEE) or `tech`: open Settings, confirm the "Team" section is absent.

- [ ] **Step 14: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-te/strings.xml app/src/main/res/layout/item_employee_row.xml app/src/main/res/layout/dialog_edit_employee.xml app/src/main/res/layout/dialog_add_employee.xml app/src/main/res/layout/activity_employee_list.xml app/src/main/java/com/saimega/vinayakacablenetwork/EmployeeListActivity.kt app/src/main/res/layout/activity_settings.xml app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt app/src/main/AndroidManifest.xml
git commit -m "$(cat <<'EOF'
Add EmployeeListActivity: list, edit role/active, add employee

New "Team" section in Settings (visible only to ADMIN) opens a screen
listing every account in Firestore's users collection. Tapping a row
lets an admin change that account's role or active status — the
first real UI for this, replacing the raw Firestore edits used to
verify sub-project 1's deactivation behavior. Editing your own
currently-logged-in row disables the active toggle so you can't
accidentally lock yourself out. A new "Add Employee" dialog creates
accounts directly, refusing a username that already exists rather
than silently overwriting it (the Firestore document ID is the
username).

Verified on emulator: list/edit/role-change flow, add-employee
validation (empty fields, mismatched passwords), successful creation
and login as the new account, duplicate-username rejection leaves the
existing account untouched, Team section hidden for non-admins.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 3: Restrict Add Customer to ADMIN

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/NewCustomerActivity.kt`

**Interfaces:** none new.

- [ ] **Step 1: Hide the Add Customer tile for non-admins**

In `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`, find `applyRoleVisibility()`:

```kotlin
    private fun applyRoleVisibility() {
        adminFinSection.visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
        employeeFinSection.visibility = if (role == "EMPLOYEE") View.VISIBLE else View.GONE
    }
```

Replace with:

```kotlin
    private fun applyRoleVisibility() {
        adminFinSection.visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
        employeeFinSection.visibility = if (role == "EMPLOYEE") View.VISIBLE else View.GONE
        findViewById<View>(R.id.qaAddCustomer).visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
    }
```

- [ ] **Step 2: Guard NewCustomerActivity itself**

In `app/src/main/java/com/saimega/vinayakacablenetwork/NewCustomerActivity.kt`, find:

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_customer)

        db = FirebaseFirestore.getInstance()
```

Replace with:

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val role = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).getString("user_role", "EMPLOYEE") ?: "EMPLOYEE"
        if (role != "ADMIN") {
            Toast.makeText(this, getString(R.string.no_permission_message), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_new_customer)

        db = FirebaseFirestore.getInstance()
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification on the emulator**

Log in as `admin`: confirm the "Add Customer" tile is visible and works as before. Log out, log in as `ravi` (EMPLOYEE) or `tech` (TECHNICIAN): confirm the "Add Customer" tile is gone from Dashboard. Confirm directly launching it is also blocked:

```bash
SDK=/Users/nagneelam/Library/Android/sdk
"$SDK/platform-tools/adb" shell am start -n com.saimega.vinayakacablenetwork/com.saimega.vinayakacablenetwork.NewCustomerActivity
```

Expected: the Toast "You don't have permission to access this screen" appears and the screen immediately closes (returns to whatever was previously in the foreground), while still logged in as the non-admin account.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt app/src/main/java/com/saimega/vinayakacablenetwork/NewCustomerActivity.kt
git commit -m "$(cat <<'EOF'
Restrict Add Customer to ADMIN

Dashboard's Add Customer tile is hidden for EMPLOYEE/TECHNICIAN.
NewCustomerActivity also checks the role itself and immediately
finishes with an explanatory Toast if reached directly (e.g. via a
saved task or adb) rather than relying solely on the tile being
hidden.

Verified on emulator: tile hidden for non-admin roles, direct launch
via adb blocked with the correct message for a non-admin session,
unaffected for admin.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 4: End-to-end verification

**Files:** none (verification-only task).

**Interfaces:** none.

- [ ] **Step 1: Full employee lifecycle**

As `admin`: add a new employee, confirm they can log in, edit their role, confirm the change persists, then deactivate them from Employee List and confirm (per sub-project 1's still-active real-time listener) they're kicked out immediately if logged in, and blocked from logging back in with the correct deactivated message.

- [ ] **Step 2: Self-protection**

Confirm you cannot deactivate the account you're currently using from Employee List (switch disabled, note visible).

- [ ] **Step 3: Duplicate username protection**

Confirm attempting to add an employee with an existing username is rejected and does not alter the existing account.

- [ ] **Step 4: Add Customer role gate**

Confirm ADMIN sees and can use "Add Customer"; EMPLOYEE/TECHNICIAN do not see the tile and are blocked if they reach `NewCustomerActivity` directly.

- [ ] **Step 5: Regression check**

Confirm Reports, Generate Bills, and Data Import remain accessible to every role (unchanged by this plan) — open each from an EMPLOYEE session and confirm no new restriction blocks them.

No commit for this task (verification only).

---

## Plan Self-Review Notes

- **Spec coverage:** AuthRepository additions → Task 1; Settings Team section + Employee List + edit + add dialogs → Task 2; Add Customer role gate → Task 3; full testing checklist → Task 4. All spec sections have a task.
- **Type consistency:** `UserAccount`, `CreateEmployeeResult` defined in Task 1, used identically in Task 2's `EmployeeListActivity`. `R.id.btnAddEmployee`, `R.id.etNewUsername`, etc. match between Task 2's layouts and their `findViewById` calls — checked.
- **No placeholders:** every step has literal code or an exact command. (An earlier draft of this plan split Add Employee into a separate task using an `open`/`protected` stub-then-override pattern across two Activities' worth of edits to the same file — reworked into one cohesive task instead, since that pattern added fragility without a real independent-approval benefit for a screen this small.)
- **Firestore document ID consistency:** `createEmployee()` and `updateUserRoleAndActive()` (Task 1) both lowercase the username before building the document reference, matching sub-project 1's established convention in `AuthRepository.login()`'s `docId = username.trim().lowercase()` — checked.
