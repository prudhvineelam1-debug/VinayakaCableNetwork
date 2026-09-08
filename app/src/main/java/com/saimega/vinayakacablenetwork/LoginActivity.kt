package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class LoginActivity : BaseActivity() {

    // ── State ──────────────────────────────────────────────────────────────
    private var isPasswordVisible = false

    // ── View references ────────────────────────────────────────────────────
    private lateinit var etUsername       : EditText
    private lateinit var etPassword       : EditText
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var cbRememberMe     : CheckBox
    private lateinit var tvForgotPassword : TextView
    private lateinit var tvContactSupport : TextView
    private lateinit var btnLogin         : AppCompatButton

    // ── Prefs key ──────────────────────────────────────────────────────────
    private val PREFS_NAME     = "vinayaka_prefs"
    private val KEY_USERNAME   = "username"
    private val KEY_ROLE       = "user_role"
    private val KEY_REMEMBERED = "remembered_username"
    private val KEY_REMEMBER   = "remember_me"

    // ──────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        bindViews()
        restoreRememberedCredentials()
        setListeners()
    }

    // ── Bind all view references ───────────────────────────────────────────
    private fun bindViews() {
        etUsername        = findViewById(R.id.etUsername)
        etPassword        = findViewById(R.id.etPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        cbRememberMe      = findViewById(R.id.cbRememberMe)
        tvForgotPassword  = findViewById(R.id.tvForgotPassword)
        tvContactSupport  = findViewById(R.id.tvContactSupport)
        btnLogin          = findViewById(R.id.btnLogin)
    }

    // ── Pre-fill saved username if "Remember me" was checked before ────────
    private fun restoreRememberedCredentials() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REMEMBER, false)) {
            val saved = prefs.getString(KEY_REMEMBERED, "") ?: ""
            if (saved.isNotEmpty()) {
                etUsername.setText(saved)
                cbRememberMe.isChecked = true
            }
        }
    }

    // ── Wire all click / action listeners ─────────────────────────────────
    private fun setListeners() {

        // ── Password visibility toggle ─────────────────────────────────────
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_login_eye_off)
                btnTogglePassword.setColorFilter(
                    getColor(android.R.color.white),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_login_eye)
                btnTogglePassword.clearColorFilter()
            }
            // Keep cursor at end
            etPassword.setSelection(etPassword.text?.length ?: 0)
        }

        // ── Forgot password (placeholder) ─────────────────────────────────
        tvForgotPassword.setOnClickListener {
            Toast.makeText(
                this,
                "Please contact your administrator to reset your password.",
                Toast.LENGTH_LONG
            ).show()
        }

        // ── Contact support (placeholder) ─────────────────────────────────
        tvContactSupport.setOnClickListener {
            Toast.makeText(
                this,
                "Support: vinayakacablenetwork@gmail.com",
                Toast.LENGTH_LONG
            ).show()
        }

        // ── Sign In button ─────────────────────────────────────────────────
        btnLogin.setOnClickListener {
            handleLogin()
        }
    }

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

        // Credential check (mocking multiple roles for now)
        val role = when {
            username == "admin" && password == "1234" -> "ADMIN"
            username == "ravi"  && password == "1234" -> "EMPLOYEE"
            username == "tech"  && password == "1234" -> "TECHNICIAN"
            else -> null
        }

        if (role != null) {

            // Persist "remember me" preference
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            prefs.putString(KEY_USERNAME, username)
            prefs.putString(KEY_ROLE, role)
            if (cbRememberMe.isChecked) {
                prefs.putString(KEY_REMEMBERED, username)
                prefs.putBoolean(KEY_REMEMBER, true)
            } else {
                prefs.remove(KEY_REMEMBERED)
                prefs.putBoolean(KEY_REMEMBER, false)
            }
            prefs.apply()

            Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()

            // Navigate to Dashboard
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()

        } else {
            Toast.makeText(this, getString(R.string.invalid_credentials), Toast.LENGTH_SHORT).show()
        }
    }
}