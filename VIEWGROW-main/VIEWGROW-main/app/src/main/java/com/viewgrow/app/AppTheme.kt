package com.viewgrow.app

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

// Light or dark. Follows the phone until the moon button is used; after that the choice is remembered.
object AppTheme {
    private const val PREFS = "theme"
    private const val KEY_DARK = "dark"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDark(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (p.contains(KEY_DARK)) return p.getBoolean(KEY_DARK, false)
        val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    fun apply(ctx: Context) {
        val p = prefs(ctx)
        AppCompatDelegate.setDefaultNightMode(
            when {
                !p.contains(KEY_DARK) -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                p.getBoolean(KEY_DARK, false) -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    // Switches and restarts the screen in the other mode.
    fun toggle(ctx: Context) {
        val dark = !isDark(ctx)
        prefs(ctx).edit().putBoolean(KEY_DARK, dark).apply()
        apply(ctx)
    }
}
