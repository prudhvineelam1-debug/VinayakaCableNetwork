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
