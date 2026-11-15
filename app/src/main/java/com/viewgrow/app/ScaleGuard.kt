package com.viewgrow.app

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics

// The ONE place that decides how much the phone's "Font size" and "Display size" settings can
// stretch this app. MainActivity runs every screen through it, so a screen is never drawn at a
// size the layouts were not built for.
//
//  - MAX_FONT_SCALE: text is never scaled above this. 2.0 is the most Android itself allows, so
//    every phone's text size is respected. The screens all scroll, buttons grow with their text
//    (minHeight, not a fixed height), and names and numbers shrink to fit (TextFit).
//  - MAX_DISPLAY_DENSITY_RATIO: "Display size" can make everything bigger, which leaves less room.
//    1.0 means the app never grows beyond the phone's default display size. Smaller display sizes
//    are respected.
object ScaleGuard {
    const val MAX_FONT_SCALE = 2.0f
    const val MAX_DISPLAY_DENSITY_RATIO = 1.0f

    fun wrap(base: Context): Context {
        val cfg = Configuration(base.resources.configuration)
        var changed = false

        if (cfg.fontScale > MAX_FONT_SCALE) {
            cfg.fontScale = MAX_FONT_SCALE
            changed = true
        }

        val maxDensity = (DisplayMetrics.DENSITY_DEVICE_STABLE * MAX_DISPLAY_DENSITY_RATIO).toInt()
        val current = cfg.densityDpi
        if (current > maxDensity && maxDensity > 0) {
            // Keep the dp-based sizes in step with the new density.
            val ratio = current.toFloat() / maxDensity
            cfg.densityDpi = maxDensity
            if (cfg.screenWidthDp > 0) cfg.screenWidthDp = (cfg.screenWidthDp * ratio).toInt()
            if (cfg.screenHeightDp > 0) cfg.screenHeightDp = (cfg.screenHeightDp * ratio).toInt()
            if (cfg.smallestScreenWidthDp > 0) {
                cfg.smallestScreenWidthDp = (cfg.smallestScreenWidthDp * ratio).toInt()
            }
            changed = true
        }

        return if (changed) base.createConfigurationContext(cfg) else base
    }
}
