package com.saimega.vinayakacablenetwork

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS_NAME = "vinayaka_prefs"
    private const val KEY_THEME = "app_theme"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(getTheme(context)))
    }

    fun toggleTheme(context: Context): String {
        val next = if (getTheme(context) == THEME_DARK) THEME_LIGHT else THEME_DARK
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, next).apply()
        AppCompatDelegate.setDefaultNightMode(nightModeFor(next))
        return next
    }

    fun nightModeFor(theme: String): Int =
        if (theme == THEME_LIGHT) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
}
