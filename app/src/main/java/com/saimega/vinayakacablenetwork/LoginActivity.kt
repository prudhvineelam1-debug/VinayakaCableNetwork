package com.saimega.vinayakacablenetwork

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 🔍 Validation
            if (username.isEmpty()) {
                etUsername.error = "Enter Username"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Enter Password"
                return@setOnClickListener
            }

            if (password.length < 4) {
                etPassword.error = "Password must be at least 4 characters"
                return@setOnClickListener
            }

            // 🔐 Dummy Login (you can replace with Firebase Auth later)
            if (username == "admin" && password == "1234") {

                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()

                // 🚀 Open Dashboard
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)

                // Close login screen
                finish()

            } else {
                Toast.makeText(this, "Invalid Username or Password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}