package com.saimega.vinayakacablenetwork

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    private var originalBaseContext: Context? = null

    override fun attachBaseContext(newBase: Context) {
        originalBaseContext = newBase
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
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
