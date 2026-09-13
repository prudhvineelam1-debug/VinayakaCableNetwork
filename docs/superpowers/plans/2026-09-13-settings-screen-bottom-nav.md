# Settings Screen & Bottom Nav Icon Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the bottom nav's mismatched stock-Android icons, and replace the dead "Settings coming soon" button with a real Settings screen (Account/Logout, Appearance, Language, About).

**Architecture:** Three custom Material-style vector icons replace stock ones in `bottom_nav_menu.xml`. A new `SettingsActivity` (same `MaterialToolbar` + `dashboard_*` color palette pattern as `CustomerListActivity`/`CustomerDetailsActivity`) absorbs the theme/language toggle logic that already exists in `DashboardActivity`, plus finally wires up the dead `showLogoutDialog()` code with a real confirmation dialog.

**Tech Stack:** Kotlin, Android vector drawables, `SharedPreferences` (`vinayaka_prefs`), existing `ThemeManager`/`LocaleHelper` singletons.

**Spec:** `docs/superpowers/specs/2026-09-13-settings-screen-bottom-nav-design.md`

## Global Constraints

- Settings is identical for every role — no role-based content gating.
- Every new user-facing string gets both an English and a Telugu translation added in the same task, not left as a fresh gap.
- New icons are 24dp viewport, single filled path, white fill — matching the existing `ic_group.xml`/`ic_payment.xml` convention (tinted at use time via `app:itemIconTint`).
- App version is read via `packageManager.getPackageInfo(packageName, 0).versionName` — NOT `BuildConfig.VERSION_NAME`, because `app/build.gradle.kts`'s `buildFeatures` block only enables `viewBinding`, not `buildConfig`, so a custom `BuildConfig` field would require a separate Gradle change this plan does not make.

---

### Task 1: New bottom nav icons

**Files:**
- Create: `app/src/main/res/drawable/ic_home.xml`
- Create: `app/src/main/res/drawable/ic_build.xml`
- Create: `app/src/main/res/drawable/ic_settings.xml`
- Modify: `app/src/main/res/menu/bottom_nav_menu.xml`

**Interfaces:**
- Produces: `@drawable/ic_home`, `@drawable/ic_build`, `@drawable/ic_settings` — referenced by `bottom_nav_menu.xml` in this same task.

- [ ] **Step 1: Create the Home icon**

Create `app/src/main/res/drawable/ic_home.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"/>
</vector>
```

- [ ] **Step 2: Create the Build (wrench) icon for Complaints**

Create `app/src/main/res/drawable/ic_build.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7C0.4,7.1 0.9,10.1 2.9,12.1c1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z"/>
</vector>
```

- [ ] **Step 3: Create the Settings (gear) icon**

Create `app/src/main/res/drawable/ic_settings.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"/>
</vector>
```

- [ ] **Step 4: Wire the new icons into the menu**

Replace the full contents of `app/src/main/res/menu/bottom_nav_menu.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/android">
    <item
        android:id="@+id/nav_home"
        android:icon="@drawable/ic_home"
        android:title="Home" />
    <item
        android:id="@+id/nav_customers"
        android:icon="@drawable/ic_group"
        android:title="Customers" />
    <item
        android:id="@+id/nav_pay"
        android:icon="@drawable/ic_payment"
        android:title="Pay" />
    <item
        android:id="@+id/nav_complaints"
        android:icon="@drawable/ic_build"
        android:title="Complaints" />
    <item
        android:id="@+id/nav_settings"
        android:icon="@drawable/ic_settings"
        android:title="Settings" />
</menu>
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual verification on the emulator**

Install the APK, open Dashboard. Confirm all 5 bottom nav icons now render as coherent, similarly-weighted glyphs (not a mix of old stock Android icons and modern ones), and that each icon's shape now matches its label (a house for Home, a wrench for Complaints, a gear for Settings).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/drawable/ic_home.xml app/src/main/res/drawable/ic_build.xml app/src/main/res/drawable/ic_settings.xml app/src/main/res/menu/bottom_nav_menu.xml
git commit -m "$(cat <<'EOF'
Replace mismatched stock Android bottom nav icons

bottom_nav_menu.xml used four ancient stock Android system icons
(ic_menu_view, ic_menu_myplaces, ic_menu_help, ic_menu_preferences)
that rendered at inconsistent visual weight and were semantically
wrong — Home showed an eye/magnifying-glass glyph, Complaints showed
a plain question mark instead of the wrench (🔧) used for Complaints
everywhere else in the app. Customers now reuses the existing
ic_group vector already used elsewhere in the app; Home, Complaints,
and Settings get new custom vectors matching that same convention.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 2: New Settings screen — strings, layout, and Kotlin

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-te/strings.xml`
- Create: `app/src/main/res/layout/activity_settings.xml`
- Create: `app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ThemeManager.getTheme(Context): String`, `ThemeManager.toggleTheme(Context): String`, `ThemeManager.THEME_DARK` (existing, `ThemeManager.kt`); `LocaleHelper.getLanguage(Context): String`, `LocaleHelper.setLocale(Context, String): Context` (existing, `LocaleHelper.kt`); SharedPreferences keys `"username"`, `"user_role"` on `"vinayaka_prefs"` (existing, written by `LoginActivity`).
- Produces: `SettingsActivity` — launched by Task 3's `DashboardActivity` change.

- [ ] **Step 1: Add the missing "settings" English string and the new Settings strings**

In `app/src/main/res/values/strings.xml`, add these lines (there is currently no English `"settings"` key at all — only a Telugu one exists, an orphan translation missing its English source):

```xml
    <string name="settings">Settings</string>
    <string name="logged_in_as">Logged in as %1$s (%2$s)</string>
    <string name="account_section">Account</string>
    <string name="appearance_section">Appearance</string>
    <string name="language_section">Language</string>
    <string name="about_section">About</string>
    <string name="app_version_label">App Version</string>
    <string name="logout">Logout</string>
    <string name="logout_confirm_title">Log Out?</string>
    <string name="logout_confirm_message">Are you sure you want to log out?</string>
```

- [ ] **Step 2: Add the Telugu translations**

In `app/src/main/res/values-te/strings.xml`, add these lines right before the closing `</resources>` tag (skip `settings` — it already has a Telugu translation, `సెట్టింగులు`):

```xml
    <string name="logged_in_as">%1$s (%2$s) గా లాగిన్ అయ్యారు</string>
    <string name="account_section">ఖాతా</string>
    <string name="appearance_section">కనిపించే తీరు</string>
    <string name="language_section">భాష</string>
    <string name="about_section">గురించి</string>
    <string name="app_version_label">యాప్ వెర్షన్</string>
    <string name="logout">లాగ్ అవుట్</string>
    <string name="logout_confirm_title">లాగ్ అవుట్ చేయాలా?</string>
    <string name="logout_confirm_message">మీరు ఖచ్చితంగా లాగ్ అవుట్ చేయాలనుకుంటున్నారా?</string>
```

- [ ] **Step 3: Create the layout**

Create `app/src/main/res/layout/activity_settings.xml`:

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
            app:title="@string/settings"
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

            <!-- Account -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
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
                        android:text="@string/account_section"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:letterSpacing="0.1"
                        android:textColor="@color/dashboard_text_secondary"
                        android:layout_marginBottom="12dp" />

                    <TextView
                        android:id="@+id/tvLoggedInAs"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textSize="14sp"
                        android:textColor="@color/dashboard_text_primary"
                        android:layout_marginBottom="16dp"
                        tools:text="Logged in as Admin (ADMIN)"
                        xmlns:tools="http://schemas.android.com/tools" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnLogout"
                        style="@style/Widget.Material3.Button"
                        android:layout_width="match_parent"
                        android:layout_height="48dp"
                        android:text="@string/logout"
                        android:backgroundTint="@color/accent_red" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Appearance -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
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
                        android:text="@string/appearance_section"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:letterSpacing="0.1"
                        android:textColor="@color/dashboard_text_secondary"
                        android:layout_marginBottom="12dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:id="@+id/btnThemeDark"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:padding="12dp"
                            android:gravity="center"
                            android:text="🌙 Dark"
                            android:textColor="@color/dashboard_text_secondary" />

                        <TextView
                            android:id="@+id/btnThemeLight"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:padding="12dp"
                            android:gravity="center"
                            android:text="☀️ Light"
                            android:textColor="@color/dashboard_text_secondary" />

                    </LinearLayout>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Language -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
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
                        android:text="@string/language_section"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:letterSpacing="0.1"
                        android:textColor="@color/dashboard_text_secondary"
                        android:layout_marginBottom="12dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:id="@+id/btnLangEn"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:padding="12dp"
                            android:gravity="center"
                            android:text="EN"
                            android:textColor="@color/dashboard_text_secondary" />

                        <TextView
                            android:id="@+id/btnLangTe"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:padding="12dp"
                            android:gravity="center"
                            android:text="తెలుగు"
                            android:textColor="@color/dashboard_text_secondary" />

                    </LinearLayout>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- About -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
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
                        android:text="@string/about_section"
                        android:textSize="11sp"
                        android:textStyle="bold"
                        android:letterSpacing="0.1"
                        android:textColor="@color/dashboard_text_secondary"
                        android:layout_marginBottom="12dp" />

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/business_name_caps"
                        android:textSize="14sp"
                        android:textStyle="bold"
                        android:textColor="@color/dashboard_text_primary"
                        android:layout_marginBottom="4dp" />

                    <TextView
                        android:id="@+id/tvAppVersion"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textSize="13sp"
                        android:textColor="@color/dashboard_text_secondary"
                        tools:text="App Version 1.0"
                        xmlns:tools="http://schemas.android.com/tools" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 4: Create SettingsActivity.kt**

Create `app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt`:

```kotlin
package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class SettingsActivity : BaseActivity() {

    private lateinit var tvLoggedInAs: TextView
    private lateinit var tvAppVersion: TextView
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnThemeDark: TextView
    private lateinit var btnThemeLight: TextView
    private lateinit var btnLangEn: TextView
    private lateinit var btnLangTe: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        bindViews()
        bindAccountSection()
        bindAppearanceSection()
        bindLanguageSection()
        bindAboutSection()
    }

    private fun bindViews() {
        tvLoggedInAs = findViewById(R.id.tvLoggedInAs)
        tvAppVersion = findViewById(R.id.tvAppVersion)
        btnLogout = findViewById(R.id.btnLogout)
        btnThemeDark = findViewById(R.id.btnThemeDark)
        btnThemeLight = findViewById(R.id.btnThemeLight)
        btnLangEn = findViewById(R.id.btnLangEn)
        btnLangTe = findViewById(R.id.btnLangTe)
    }

    private fun bindAccountSection() {
        val prefs = getSharedPreferences("vinayaka_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "Admin") ?: "Admin"
        val role = prefs.getString("user_role", "ADMIN") ?: "ADMIN"
        tvLoggedInAs.text = getString(R.string.logged_in_as, username, role)

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout) { _, _ -> performLogout() }
                .show()
        }
    }

    private fun performLogout() {
        getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun bindAppearanceSection() {
        val currentTheme = ThemeManager.getTheme(this)
        styleToggleButton(btnThemeDark, currentTheme == ThemeManager.THEME_DARK)
        styleToggleButton(btnThemeLight, currentTheme == ThemeManager.THEME_LIGHT)

        btnThemeDark.setOnClickListener {
            if (ThemeManager.getTheme(this) != ThemeManager.THEME_DARK) {
                ThemeManager.toggleTheme(this)
                recreate()
            }
        }
        btnThemeLight.setOnClickListener {
            if (ThemeManager.getTheme(this) != ThemeManager.THEME_LIGHT) {
                ThemeManager.toggleTheme(this)
                recreate()
            }
        }
    }

    private fun bindLanguageSection() {
        val currentLang = LocaleHelper.getLanguage(this)
        styleToggleButton(btnLangEn, currentLang == "en")
        styleToggleButton(btnLangTe, currentLang == "te")

        btnLangEn.setOnClickListener {
            LocaleHelper.setLocale(this, "en")
            recreate()
        }
        btnLangTe.setOnClickListener {
            LocaleHelper.setLocale(this, "te")
            recreate()
        }
    }

    private fun styleToggleButton(button: TextView, selected: Boolean) {
        if (selected) {
            button.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
            button.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            button.setTextColor(ContextCompat.getColor(this, R.color.dashboard_text_secondary))
            button.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun bindAboutSection() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }
        tvAppVersion.text = "${getString(R.string.app_version_label)} $versionName"
    }
}
```

- [ ] **Step 5: Register the Activity in the manifest**

In `app/src/main/AndroidManifest.xml`, add this entry after the `<!-- COMPLAINT MANAGEMENT -->` `ComplaintActivity` entry:

```xml
        <!-- SETTINGS -->
        <activity
            android:name=".SettingsActivity"
            android:exported="false" />
```

- [ ] **Step 6: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-te/strings.xml app/src/main/res/layout/activity_settings.xml app/src/main/java/com/saimega/vinayakacablenetwork/SettingsActivity.kt app/src/main/AndroidManifest.xml
git commit -m "$(cat <<'EOF'
Add SettingsActivity: Account/Logout, Appearance, Language, About

New screen with four sections: Account shows who's logged in and a
Logout button with a real confirmation dialog (finally delivering
what DashboardActivity's unused showLogoutDialog() always implied by
its name but never did — there was previously no way to log out of
the app through the UI at all); Appearance and Language duplicate
Dashboard's existing theme/locale toggle logic (ThemeManager /
LocaleHelper, unchanged); About shows the business name and app
version (read via PackageManager, not BuildConfig, since buildConfig
generation isn't enabled in this module).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 3: Wire Dashboard's Settings nav item, remove duplicated controls

**Files:**
- Modify: `app/src/main/res/layout/activity_dashboard.xml`
- Modify: `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`

**Interfaces:**
- Consumes: `SettingsActivity` (Task 2).
- Produces: none.

- [ ] **Step 1: Remove the language-toggle and theme-toggle views from the top bar**

In `app/src/main/res/layout/activity_dashboard.xml`, delete this entire block (the language-toggle pill, right after the title/subtitle `LinearLayout` closes and before the `btnThemeToggle` `TextView`):

```xml
            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:background="@drawable/cm_bg_input"
                android:padding="2dp"
                android:layout_marginEnd="8dp">

                <TextView
                    android:id="@+id/btnLangEn"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="EN"
                    android:textSize="10.5sp"
                    android:textStyle="bold"
                    android:paddingHorizontal="9dp"
                    android:paddingVertical="4dp" />

                <TextView
                    android:id="@+id/btnLangTe"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="తె"
                    android:textSize="10.5sp"
                    android:paddingHorizontal="9dp"
                    android:paddingVertical="4dp" />
            </LinearLayout>

            <TextView
                android:id="@+id/btnThemeToggle"
                android:layout_width="34dp"
                android:layout_height="34dp"
                android:layout_marginEnd="8dp"
                android:background="@drawable/cm_bg_input"
                android:gravity="center"
                android:text="🌙"
                android:textSize="15sp" />

```

(Leave the `FrameLayout` profile-avatar block immediately after it untouched — the top bar now goes directly from the title/subtitle block to the avatar.)

- [ ] **Step 2: Remove the corresponding Kotlin wiring**

In `app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt`, delete the property declaration:

```kotlin
    private lateinit var btnThemeToggle: TextView
```

Delete this block from `bindViews()`:

```kotlin
        btnThemeToggle.text = if (ThemeManager.getTheme(this) == ThemeManager.THEME_DARK) "🌙" else "☀️"
        btnThemeToggle.setOnClickListener {
            ThemeManager.toggleTheme(this)
            recreate()
        }

        val btnLangEn = findViewById<TextView>(R.id.btnLangEn)
        val btnLangTe = findViewById<TextView>(R.id.btnLangTe)
        val currentLang = LocaleHelper.getLanguage(this)
        styleLangButton(btnLangEn, currentLang == "en")
        styleLangButton(btnLangTe, currentLang == "te")
        btnLangEn.setOnClickListener {
            LocaleHelper.setLocale(this, "en")
            recreate()
        }
        btnLangTe.setOnClickListener {
            LocaleHelper.setLocale(this, "te")
            recreate()
        }
```

Delete the now-unused `findViewById(R.id.btnThemeToggle)` line from `bindViews()` (it was `btnThemeToggle = findViewById(R.id.btnThemeToggle)`, immediately before the block just deleted).

Delete the now-unused `styleLangButton` private method:

```kotlin
    private fun styleLangButton(button: TextView, selected: Boolean) {
        if (selected) {
            button.setBackgroundResource(R.drawable.cm_gradient_blue)
            button.setTextColor(getColorCompat(R.color.cm_on_gradient))
        } else {
            button.background = null
            button.setTextColor(getColorCompat(R.color.cm_text_secondary))
        }
    }
```

Delete the dead `showLogoutDialog()` method (never called from anywhere; its logic now lives in `SettingsActivity.performLogout()`):

```kotlin
    private fun showLogoutDialog() {
        getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
```

- [ ] **Step 3: Wire the Settings nav item to launch SettingsActivity**

In `setupBottomNav()`, find:

```kotlin
                R.id.nav_settings -> {
                    Toast.makeText(this, "Settings screen is coming soon", Toast.LENGTH_SHORT).show()
                    false
                }
```

Replace with:

```kotlin
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (If `getColorCompat` is now reported unused by the IDE that's fine — it's still called elsewhere in `DashboardActivity`, e.g. `renderSixMonthTrend()`; only remove it if the compiler actually errors, which it should not.)

- [ ] **Step 5: Manual verification on the emulator**

Reinstall. Confirm Dashboard's top bar no longer shows the EN/తె pill or the 🌙/☀️ button — just title, subtitle, and the profile avatar. Tap the Settings nav item: confirm `SettingsActivity` opens (not a Toast).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/activity_dashboard.xml app/src/main/java/com/saimega/vinayakacablenetwork/DashboardActivity.kt
git commit -m "$(cat <<'EOF'
Wire Settings nav to SettingsActivity, remove duplicated toggles

nav_settings previously showed a "coming soon" Toast. Dashboard's
top-bar theme/language toggles are removed now that the same controls
live in Settings — one place to change each preference instead of
two. The dead showLogoutDialog() (never called from anywhere) is also
removed; its logic now lives in SettingsActivity.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FHCdiUpSaJSRLKh2UpF936
EOF
)"
```

---

### Task 4: End-to-end verification

**Files:** none (verification-only task).

**Interfaces:** none.

- [ ] **Step 1: Verify Account section**

Open Settings. Confirm "Logged in as <username> (<ROLE>)" shows the actual logged-in user (matches what Dashboard's top bar subtitle already shows for role). Tap Logout: confirm the confirmation dialog appears. Tap Cancel: confirm nothing happens, still on Settings. Tap Logout again, then confirm in the dialog: confirm you land on the Login screen.

- [ ] **Step 2: Verify the session was actually cleared**

Force-close and relaunch the app (`adb shell am force-stop com.saimega.vinayakacablenetwork` then relaunch `LoginActivity`). Confirm it does NOT auto-navigate to Dashboard and does NOT have "Remember me" prefilled from a still-live session — confirms `vinayaka_prefs` was actually cleared, not just the screen changed. Log back in.

- [ ] **Step 3: Verify Appearance and Language sections**

From Settings, tap Light / Dark and confirm the whole app's theme changes (same visual effect the old Dashboard top-bar toggle had). Tap EN / తెలుగు and confirm the app's language changes app-wide (same effect as the old Dashboard top-bar toggle).

- [ ] **Step 4: Verify About section**

Confirm it shows the business name and "App Version 1.0" (matching `versionName` in `app/build.gradle.kts`).

- [ ] **Step 5: Final regression pass**

Confirm Dashboard's Home/Customers/Pay/Complaints nav items still navigate correctly (unchanged in this plan — only their icons changed in Task 1). Confirm the Dashboard top bar layout looks correct with the toggles removed (no leftover empty gap or misaligned avatar).

No commit for this task (verification only).

---

## Plan Self-Review Notes

- **Spec coverage:** icon fixes → Task 1; new Settings screen (all four sections) → Task 2; Dashboard wiring + cleanup → Task 3; manual verification checklist from the spec's Testing section → Task 4. All spec sections have a task.
- **Type consistency:** `SettingsActivity` is created in Task 2 and referenced by exact class name in Task 3's `Intent(this, SettingsActivity::class.java)` — checked. View IDs (`tvLoggedInAs`, `btnLogout`, `btnThemeDark`, `btnThemeLight`, `btnLangEn`, `btnLangTe`, `tvAppVersion`) match between the layout (Task 2 Step 3) and the Kotlin `findViewById` calls (Task 2 Step 4) — checked.
- **No placeholders:** every step has literal code or an exact command.
- **Note:** Task 2's layout XML declares the `tools:` namespace locally on two `TextView`s (`xmlns:tools=...`) rather than once at the file root, to keep the copy-pasteable snippet self-contained — a real implementer should hoist it to the root `<androidx.coordinatorlayout.widget.CoordinatorLayout>` tag instead for a cleaner file (functionally identical either way; XML allows a namespace redeclaration on a descendant element).
