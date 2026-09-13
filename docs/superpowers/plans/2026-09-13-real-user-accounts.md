# Real User Accounts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hardcoded login credentials with a real Firestore-backed `users` collection, add Change Password, and enforce account deactivation (blocks login, forces an immediate logout of an active session).

**Architecture:** A new `PasswordHasher` utility (pure, salted SHA-256) backs a new `AuthRepository` (Firestore lookups: seed/login/changePassword). `LoginActivity` calls it instead of its hardcoded `when` check. `BaseActivity` gains a real-time Firestore listener on the logged-in user's own document — already extended by every screen — so a flipped `active` flag logs the session out immediately from wherever it is.

**Tech Stack:** Kotlin, `java.security.MessageDigest`/`SecureRandom`, `android.util.Base64`, Firebase Firestore (existing SDK, no new dependency), Kotlin Coroutines.

**Spec:** `docs/superpowers/specs/2026-09-13-real-user-accounts-design.md`

## Global Constraints

- No Firebase Authentication, no Firestore security rules changes — explicitly declined by the business owner (documented tradeoff in the spec).
- Passwords are never stored or compared in plain text — salted SHA-256 only.
- The `users` Firestore document ID is always the lowercased username; every place that builds this ID lowercases consistently.
- The bootstrap seed only ever runs once (guarded by "does `users` have any document at all") and must not silently re-run and clobber changed data.

---

### Task 1: PasswordHasher utility (TDD)

**Files:**
- Create: `app/src/main/java/com/saimega/vinayakacablenetwork/PasswordHasher.kt`
- Test: `app/src/test/java/com/saimega/vinayakacablenetwork/PasswordHasherTest.kt`

**Interfaces:**
- Produces: `object PasswordHasher { fun generateSalt(): String; fun hash(password: String, salt: String): String }` — consumed by Task 2 (`AuthRepository`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/saimega/vinayakacablenetwork/PasswordHasherTest.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `same password and salt produce the same hash every time`() {
        val salt = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("mypassword", salt)
        val hash2 = PasswordHasher.hash("mypassword", salt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different passwords with the same salt produce different hashes`() {
        val salt = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("password1", salt)
        val hash2 = PasswordHasher.hash("password2", salt)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `same password with different salts produces different hashes`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        val hash1 = PasswordHasher.hash("samepassword", salt1)
        val hash2 = PasswordHasher.hash("samepassword", salt2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `generateSalt produces different values each call`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `hash never contains the plain password as a substring`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("1234", salt)
        assertTrue(!hash.contains("1234"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.saimega.vinayakacablenetwork.PasswordHasherTest"`
Expected: FAIL — `Unresolved reference: PasswordHasher`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/saimega/vinayakacablenetwork/PasswordHasher.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted SHA-256 password hashing. Not a substitute for Firebase Auth or
 * a proper KDF like bcrypt/Argon2 — a deliberate, documented tradeoff
 * (see the real-user-accounts spec) accepted in place of adopting
 * Firebase Authentication for this app.
 */
object PasswordHasher {

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(salt, Base64.NO_WRAP))
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.saimega.vinayakacablenetwork.PasswordHasherTest"`
Expected: BUILD SUCCESSFUL, all 5 tests pass.

Note: `android.util.Base64` is part of the Android SDK, not the JVM — this test runs under Robolectric-free plain JUnit against `testDebugUnitTest`, which normally can't resolve real Android framework classes. If this fails with a `NullPointerException`/`RuntimeException` "not mocked" from `Base64`, it's because `app/build.gradle.kts` has no `testOptions { unitTests { isReturnDefaultValues = true } }` set. Add it inside the existing `android { ... }` block in `app/build.gradle.kts`:

```kotlin
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
```

Re-run the test command above after adding this if the first attempt fails for that reason.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/PasswordHasher.kt app/src/test/java/com/saimega/vinayakacablenetwork/PasswordHasherTest.kt app/build.gradle.kts
git commit -m "$(cat <<'EOF'
Add salted SHA-256 PasswordHasher utility

Pure, testable hashing used by the upcoming Firestore-backed account
system: generateSalt() for a fresh random salt per user,
hash(password, salt) to compute the stored value. Passwords are never
stored or compared in plain text.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 2: AuthRepository (seed, login, change password)

**Files:**
- Create: `app/src/main/java/com/saimega/vinayakacablenetwork/AuthRepository.kt`

**Interfaces:**
- Consumes: `PasswordHasher.generateSalt(): String`, `PasswordHasher.hash(String, String): String` (Task 1).
- Produces: `class AuthRepository` with `suspend fun ensureSeeded()`, `suspend fun login(username: String, password: String): LoginResult`, `suspend fun changePassword(username: String, currentPassword: String, newPassword: String): ChangePasswordResult`; `sealed class LoginResult` (`Success(username, name, role)`, `InvalidCredentials`, `AccountDeactivated`, `Failure(exception)`); `sealed class ChangePasswordResult` (`Success`, `IncorrectCurrentPassword`, `Failure(exception)`) — consumed by Task 3 (`LoginActivity`) and Task 5 (`SettingsActivity`).

- [ ] **Step 1: Create AuthRepository.kt**

Create `app/src/main/java/com/saimega/vinayakacablenetwork/AuthRepository.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

sealed class LoginResult {
    data class Success(val username: String, val name: String, val role: String) : LoginResult()
    object InvalidCredentials : LoginResult()
    object AccountDeactivated : LoginResult()
    data class Failure(val exception: Exception) : LoginResult()
}

sealed class ChangePasswordResult {
    object Success : ChangePasswordResult()
    object IncorrectCurrentPassword : ChangePasswordResult()
    data class Failure(val exception: Exception) : ChangePasswordResult()
}

class AuthRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Seeds the three pre-existing hardcoded accounts into Firestore the
     * first time this app ever runs against a project with an empty
     * `users` collection. A no-op on every subsequent call. Passwords
     * match what they always were ("1234") so nothing breaks for
     * existing staff — they should change it via Settings afterward.
     */
    suspend fun ensureSeeded() {
        val snapshot = db.collection("users").limit(1).get(Source.SERVER).await()
        if (!snapshot.isEmpty) return

        val seedAccounts = listOf(
            Triple("admin", "Admin", "ADMIN"),
            Triple("ravi", "Ravi", "EMPLOYEE"),
            Triple("tech", "Technician", "TECHNICIAN")
        )

        val batch = db.batch()
        for ((username, name, role) in seedAccounts) {
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hash("1234", salt)
            val ref = db.collection("users").document(username)
            batch.set(ref, mapOf(
                "username" to username,
                "name" to name,
                "passwordHash" to hash,
                "passwordSalt" to salt,
                "role" to role,
                "active" to true,
                "createdAt" to System.currentTimeMillis()
            ))
        }
        batch.commit().await()
    }

    suspend fun login(username: String, password: String): LoginResult {
        return try {
            val docId = username.trim().lowercase()
            val doc = db.collection("users").document(docId).get(Source.SERVER).await()
            if (!doc.exists()) return LoginResult.InvalidCredentials

            val storedHash = doc.getString("passwordHash") ?: ""
            val salt = doc.getString("passwordSalt") ?: ""
            val computedHash = PasswordHasher.hash(password, salt)
            if (computedHash != storedHash) return LoginResult.InvalidCredentials

            val active = doc.getBoolean("active") ?: true
            if (!active) return LoginResult.AccountDeactivated

            LoginResult.Success(
                username = docId,
                name = doc.getString("name") ?: docId,
                role = doc.getString("role") ?: "EMPLOYEE"
            )
        } catch (e: Exception) {
            LoginResult.Failure(e)
        }
    }

    suspend fun changePassword(username: String, currentPassword: String, newPassword: String): ChangePasswordResult {
        return try {
            val docId = username.trim().lowercase()
            val ref = db.collection("users").document(docId)
            val doc = ref.get(Source.SERVER).await()

            val storedHash = doc.getString("passwordHash") ?: ""
            val salt = doc.getString("passwordSalt") ?: ""
            if (PasswordHasher.hash(currentPassword, salt) != storedHash) {
                return ChangePasswordResult.IncorrectCurrentPassword
            }

            val newSalt = PasswordHasher.generateSalt()
            val newHash = PasswordHasher.hash(newPassword, newSalt)
            ref.update(mapOf("passwordHash" to newHash, "passwordSalt" to newSalt)).await()
            ChangePasswordResult.Success
        } catch (e: Exception) {
            ChangePasswordResult.Failure(e)
        }
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (No manual verification yet — nothing calls this class until Task 3.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/AuthRepository.kt
git commit -m "$(cat <<'EOF'
Add AuthRepository: Firestore-backed seed/login/changePassword

ensureSeeded() migrates the three hardcoded accounts into a new
Firestore users collection exactly once. login() replaces the
in-memory credential check with a real lookup, distinguishing invalid
credentials from a deactivated account. changePassword() re-hashes
with a fresh salt after verifying the current password.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 3: Rewrite LoginActivity to use AuthRepository

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/LoginActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-te/strings.xml`

**Interfaces:**
- Consumes: `AuthRepository().ensureSeeded()`, `AuthRepository().login(username, password): LoginResult`, `LoginResult.Success/InvalidCredentials/AccountDeactivated/Failure` (Task 2).

- [ ] **Step 1: Add the new string**

In `app/src/main/res/values/strings.xml`, add right after the existing `<string name="invalid_credentials">Invalid Username or Password</string>` line:

```xml
    <string name="account_deactivated">Your account has been deactivated. Contact your administrator.</string>
```

In `app/src/main/res/values-te/strings.xml`, add right before the closing `</resources>` tag:

```xml
    <string name="account_deactivated">మీ ఖాతా నిష్క్రియం చేయబడింది. మీ నిర్వాహకుడిని సంప్రదించండి.</string>
```

- [ ] **Step 2: Seed on launch and rewrite handleLogin as a coroutine**

In `app/src/main/java/com/saimega/vinayakacablenetwork/LoginActivity.kt`, add these imports (alongside the existing ones):

```kotlin
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
```

In `onCreate()`, right after `setListeners()`, add:

```kotlin
        lifecycleScope.launch {
            AuthRepository().ensureSeeded()
        }
```

Replace the entire `handleLogin()` method:

```kotlin
    // ── Login validation + navigation logic ───────────────────────────────
    private fun handleLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // Validation
        if (username.isEmpty()) {
            etUsername.error = getString(R.string.enter_username)
            etUsername.requestFocus()
            return
        }
        if (password.isEmpty()) {
            etPassword.error = getString(R.string.enter_password)
            etPassword.requestFocus()
            return
        }
        if (password.length < 4) {
            etPassword.error = getString(R.string.password_too_short)
            etPassword.requestFocus()
            return
        }

        btnLogin.isEnabled = false
        lifecycleScope.launch {
            when (val result = AuthRepository().login(username, password)) {
                is LoginResult.Success -> {
                    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    prefs.putString(KEY_USERNAME, result.username)
                    prefs.putString(KEY_ROLE, result.role)
                    if (cbRememberMe.isChecked) {
                        prefs.putString(KEY_REMEMBERED, result.username)
                        prefs.putBoolean(KEY_REMEMBER, true)
                    } else {
                        prefs.remove(KEY_REMEMBERED)
                        prefs.putBoolean(KEY_REMEMBER, false)
                    }
                    prefs.apply()

                    Toast.makeText(this@LoginActivity, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                    finish()
                }
                is LoginResult.InvalidCredentials -> {
                    btnLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, getString(R.string.invalid_credentials), Toast.LENGTH_SHORT).show()
                }
                is LoginResult.AccountDeactivated -> {
                    btnLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, getString(R.string.account_deactivated), Toast.LENGTH_LONG).show()
                }
                is LoginResult.Failure -> {
                    btnLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, getString(R.string.error_prefix, result.exception.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification on the emulator**

If testing against a Firestore project that already has data from earlier sub-projects (it does, in this repo's case), the `users` collection is empty (nothing has ever written to it) — `ensureSeeded()` will run and populate it. Install the APK, launch, and confirm login with `admin`/`1234` still works exactly as before (now via Firestore instead of the hardcoded check). Confirm a wrong password still shows "Invalid Username or Password".

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/LoginActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values-te/strings.xml
git commit -m "$(cat <<'EOF'
Rewrite LoginActivity to authenticate against Firestore

Replaces the hardcoded admin/ravi/tech when-check with
AuthRepository.login(), seeded once via ensureSeeded() on first
launch. Existing admin/1234 etc. credentials keep working unchanged
— only the storage moved. Deactivated accounts now get a distinct,
clear message instead of "invalid credentials".

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 4: Real-time forced logout in BaseActivity

**Files:**
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/BaseActivity.kt`

**Interfaces:**
- Consumes: none new (raw Firestore read on the `users` collection this plan already defined in Task 2).
- Produces: none — this is a cross-cutting behavior every `BaseActivity` subclass inherits automatically.

- [ ] **Step 1: Rewrite BaseActivity.kt**

Replace the entire contents of `app/src/main/java/com/saimega/vinayakacablenetwork/BaseActivity.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

abstract class BaseActivity : AppCompatActivity() {

    private var originalBaseContext: Context? = null
    private var sessionListener: ListenerRegistration? = null

    override fun attachBaseContext(newBase: Context) {
        originalBaseContext = newBase
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        attachSessionListener()
    }

    override fun onPause() {
        super.onPause()
        sessionListener?.remove()
        sessionListener = null
    }

    /**
     * Watches the logged-in user's own Firestore document in real time.
     * If an admin flips `active` to false while this screen is in the
     * foreground, the session is cleared and the app returns to Login
     * immediately — no waiting for the next app resume. A no-op when
     * there's no active session (including on LoginActivity itself,
     * since vinayaka_prefs has no "username" before a successful login).
     */
    private fun attachSessionListener() {
        val prefs = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", null) ?: return

        sessionListener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(username)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val active = snapshot.getBoolean("active") ?: true
                if (!active) {
                    prefs.edit().clear().apply()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
            }
    }

    override fun getSystemService(name: String): Any? {
        // FIX: PrintManager requires an Activity context. Because we wrap the context
        // for localization (createConfigurationContext), PrintManager loses the Activity reference
        // and crashes with "Can print only from an activity".
        // Routing PRINT_SERVICE through the original base context fixes this.
        if (name == Context.PRINT_SERVICE && originalBaseContext != null) {
            return originalBaseContext!!.getSystemService(name)
        }
        return super.getSystemService(name)
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification on the emulator**

Log in as `admin`. While on Dashboard, use the Firebase console (or `firebase firestore` CLI, if a write path is available in this environment — otherwise use the Firebase console in a browser) to set `users/admin`'s `active` field to `false`. Within a second or two, confirm the app automatically navigates to Login without any tap. Set `active` back to `true` afterward so `admin` isn't locked out for the rest of testing.

Also confirm the unaffected case: normal use (no deactivation) continues to work exactly as before — open several screens in a row, confirm no unexpected navigation or Firestore-listener-related lag.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/saimega/vinayakacablenetwork/BaseActivity.kt
git commit -m "$(cat <<'EOF'
Force immediate logout when an account is deactivated

BaseActivity now attaches a real-time Firestore listener on the
logged-in user's own document whenever any screen is in the
foreground. If active flips to false — an admin deactivating the
account from elsewhere — the session is cleared and the app returns
to Login immediately, without waiting for the next resume.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 5: Change Password in Settings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-te/strings.xml`
- Create: `app/src/main/res/layout/dialog_change_password.xml`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt`

**Interfaces:**
- Consumes: `AuthRepository().changePassword(username, current, new): ChangePasswordResult`, `ChangePasswordResult.Success/IncorrectCurrentPassword/Failure` (Task 2).

- [ ] **Step 1: Add the new strings**

In `app/src/main/res/values/strings.xml`, add right after the `logout_confirm_message` line:

```xml
    <string name="change_password">Change Password</string>
    <string name="current_password_hint">Current Password</string>
    <string name="new_password_hint">New Password</string>
    <string name="confirm_new_password_hint">Confirm New Password</string>
    <string name="passwords_do_not_match">New passwords do not match</string>
    <string name="password_changed_successfully">Password changed successfully</string>
    <string name="incorrect_current_password">Current password is incorrect</string>
```

In `app/src/main/res/values-te/strings.xml`, add right before the closing `</resources>` tag:

```xml
    <string name="change_password">పాస్‌వర్డ్ మార్చండి</string>
    <string name="current_password_hint">ప్రస్తుత పాస్‌వర్డ్</string>
    <string name="new_password_hint">కొత్త పాస్‌వర్డ్</string>
    <string name="confirm_new_password_hint">కొత్త పాస్‌వర్డ్ నిర్ధారించండి</string>
    <string name="passwords_do_not_match">కొత్త పాస్‌వర్డ్‌లు సరిపోలలేదు</string>
    <string name="password_changed_successfully">పాస్‌వర్డ్ విజయవంతంగా మార్చబడింది</string>
    <string name="incorrect_current_password">ప్రస్తుత పాస్‌వర్డ్ తప్పు</string>
```

- [ ] **Step 2: Create the dialog layout**

Create `app/src/main/res/layout/dialog_change_password.xml`:

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
        android:id="@+id/etCurrentPassword"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/current_password_hint"
        android:inputType="textPassword"
        android:layout_marginBottom="12dp" />

    <EditText
        android:id="@+id/etNewPassword"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/new_password_hint"
        android:inputType="textPassword"
        android:layout_marginBottom="12dp" />

    <EditText
        android:id="@+id/etConfirmNewPassword"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/confirm_new_password_hint"
        android:inputType="textPassword" />

</LinearLayout>
```

- [ ] **Step 3: Add the button to Settings' Account section**

In `app/src/main/res/layout/activity_settings.xml`, find the `btnLogout` `MaterialButton` inside the Account card, and add a new button right after it (still inside the same section's `LinearLayout`, before that card's closing tags):

```xml
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnChangePassword"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="match_parent"
                        android:layout_height="48dp"
                        android:layout_marginTop="12dp"
                        android:text="@string/change_password" />
```

- [ ] **Step 4: Wire it in SettingsActivity.kt**

In `app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt`, add these imports:

```kotlin
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
```

Add a new property declaration alongside the others:

```kotlin
    private lateinit var btnChangePassword: MaterialButton
```

In `bindViews()`, add:

```kotlin
        btnChangePassword = findViewById(R.id.btnChangePassword)
```

In `bindAccountSection()`, right after the existing `btnLogout.setOnClickListener { ... }` block, add:

```kotlin
        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
```

Add this new private method (a good spot is right after `performLogout()`):

```kotlin
    private fun showChangePasswordDialog() {
        val prefs = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", null) ?: return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmNewPassword = dialogView.findViewById<EditText>(R.id.etConfirmNewPassword)

        AlertDialog.Builder(this)
            .setTitle(R.string.change_password)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.change_password) { _, _ ->
                val current = etCurrentPassword.text.toString()
                val newPassword = etNewPassword.text.toString()
                val confirm = etConfirmNewPassword.text.toString()

                if (newPassword != confirm) {
                    Toast.makeText(this, getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    when (AuthRepository().changePassword(username, current, newPassword)) {
                        is ChangePasswordResult.Success -> {
                            Toast.makeText(this@SettingsActivity, getString(R.string.password_changed_successfully), Toast.LENGTH_SHORT).show()
                        }
                        is ChangePasswordResult.IncorrectCurrentPassword -> {
                            Toast.makeText(this@SettingsActivity, getString(R.string.incorrect_current_password), Toast.LENGTH_SHORT).show()
                        }
                        is ChangePasswordResult.Failure -> {
                            Toast.makeText(this@SettingsActivity, getString(R.string.error_prefix, "unknown"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual verification on the emulator**

Open Settings → Change Password. Enter the wrong current password: confirm "Current password is incorrect". Enter the right current password but mismatched new/confirm: confirm "New passwords do not match". Enter the right current password and matching new passwords: confirm "Password changed successfully". Log out, confirm the OLD password no longer works and the NEW one does.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-te/strings.xml app/src/main/res/layout/dialog_change_password.xml app/src/main/res/layout/activity_settings.xml app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt
git commit -m "$(cat <<'EOF'
Add Change Password to Settings

New button in Settings' Account section opens a dialog for current/
new/confirm password, backed by AuthRepository.changePassword().
Distinct error messages for a wrong current password vs. mismatched
new-password confirmation.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 6: End-to-end verification

**Files:** none (verification-only task).

**Interfaces:** none.

- [ ] **Step 1: Full login/logout cycle**

Log out (from Settings, per the existing feature). Log back in with `admin`/`1234` (or the new password if Task 5's verification changed it — use whichever is currently correct). Confirm Dashboard loads normally.

- [ ] **Step 2: Deactivation blocks login**

Using the Firebase console, set `users/ravi`'s `active` field to `false`. Attempt to log in as `ravi`/`1234`: confirm the distinct "Your account has been deactivated..." message, and that login is refused. Set it back to `true` afterward.

- [ ] **Step 3: Deactivation forces an already-open session out**

Log in as `ravi`. While on Dashboard, set `users/ravi`'s `active` to `false` via the console. Confirm the app kicks to Login within a couple of seconds with no user interaction. Set `active` back to `true`.

- [ ] **Step 4: Regression check on existing features**

Confirm Settings' theme toggle, language toggle, and Logout (from the previous sub-project) still work unchanged — this plan only added to `BaseActivity`/`SettingsActivity`, it didn't remove anything from them.

- [ ] **Step 5: Confirm Firestore now has real user data**

Using the Firebase console (or `firebase firestore` CLI read access), confirm the `users` collection now has 3 documents (`admin`, `ravi`, `tech`) with hashed passwords — directly answering the business owner's original "I'm not seeing any data on Firestore" observation.

No commit for this task (verification only).

---

## Plan Self-Review Notes

- **Spec coverage:** data model + bootstrap → Task 2; login rewrite → Task 3; real-time forced logout → Task 4; change password → Task 5; testing checklist → Task 6. All spec sections have a task.
- **Type consistency:** `LoginResult` and `ChangePasswordResult` sealed classes are defined once (Task 2) and referenced identically in Task 3 (`LoginActivity`) and Task 5 (`SettingsActivity`) — checked. `PasswordHasher.generateSalt()`/`hash()` signatures match between Task 1's definition and Task 2's usage — checked.
- **No placeholders:** every step has literal code or an exact command.
- **Firestore document ID consistency:** Task 2's `login()` computes `docId = username.trim().lowercase()` and returns that as `LoginResult.Success.username`; Task 3 stores exactly that value (not the raw typed username) into `vinayaka_prefs`; Task 4's `attachSessionListener()` reads that same stored value directly as the document ID with no further transformation. This chain was traced end-to-end to confirm no case-mismatch bug.
