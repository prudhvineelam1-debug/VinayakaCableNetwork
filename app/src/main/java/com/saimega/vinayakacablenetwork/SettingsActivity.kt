package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    private lateinit var tvLoggedInAs: TextView
    private lateinit var tvAppVersion: TextView
    private lateinit var btnLogout: MaterialButton
    private lateinit var btnChangePassword: MaterialButton
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
        btnChangePassword = findViewById(R.id.btnChangePassword)
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

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
    }

    private fun performLogout() {
        getSharedPreferences("vinayaka_prefs", MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

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
