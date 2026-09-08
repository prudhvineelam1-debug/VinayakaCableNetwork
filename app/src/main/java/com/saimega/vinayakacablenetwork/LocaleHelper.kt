package com.saimega.vinayakacablenetwork

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * LocaleHelper
 *
 * Handles saving and applying in-app language preferences.
 * Uses createConfigurationContext() — the modern, non-deprecated approach
 * fully compatible with Android 13/14+ (API 33+).
 *
 * Usage:
 *   1. Call onAttach(context) from every Activity's attachBaseContext()
 *   2. Call setLocale(context, "te") + activity.recreate() to switch language at runtime
 *   3. Call getLanguage(context) to read the current saved language code
 */
object LocaleHelper {

    private const val PREFS_NAME        = "vinayaka_prefs"
    private const val KEY_LANGUAGE      = "selected_language"
    private const val DEFAULT_LANGUAGE  = "en"

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call this from attachBaseContext() in every Activity.
     * Reads the saved language and returns a context wrapped with the correct locale,
     * so the Activity inflates all its views in the right language.
     */
    fun onAttach(context: Context): Context {
        val language = getLanguage(context)
        return applyLocale(context, language)
    }

    /**
     * Returns the currently saved language code ("en" or "te").
     * Defaults to "en" if nothing has been saved yet.
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Saves the new language code to SharedPreferences and returns a context
     * with the new locale applied. Call activity.recreate() after this to refresh the UI.
     */
    fun setLocale(context: Context, language: String): Context {
        saveLanguage(context, language)
        return applyLocale(context, language)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun saveLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    /**
     * Creates a new context with the given locale applied.
     *
     * Uses createConfigurationContext() instead of the deprecated
     * resources.updateConfiguration() — fully compatible with Android 13/14+ (API 33+).
     */
    private fun applyLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        // createConfigurationContext returns a new context with the locale baked in —
        // all getString() and view inflation calls on this context will use the new locale.
        return context.createConfigurationContext(config)
    }
}
