package com.viewgrow.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

// Opens a ViewGrow website page inside the app (Chrome Custom Tab): a browser window with a green top bar that sits on top
// of the app. The Back button (or the X) returns to the screen the member came from. The website is the
// one source of truth for Terms, Privacy and Contact us.
object Web {
    const val TERMS = "https://viewgrow.site/terms/"
    const val PRIVACY = "https://viewgrow.site/privacy/"
    const val CONTACT = "https://viewgrow.site/contact/"

    // True when the page opened. False when the phone has no browser to open it with.
    fun open(activity: Activity, url: String): Boolean {
        return try {
            val bar = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(activity, R.color.green_dark))
                .build()
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(bar)
                .setShowTitle(true)
                .build()
                .launchUrl(activity, Uri.parse(url))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
