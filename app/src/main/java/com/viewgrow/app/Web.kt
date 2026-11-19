package com.viewgrow.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

// Opens a ViewGrow website page inside the app (Chrome Custom Tab): a browser window with a green top bar that sits on top
// of the app. The Back button (or the X) returns to the screen the member came from. The app's Terms, Privacy,
// Contact us and Delete my account are on the website under /android/ (separate from the website's own pages).
object Web {
    // The app's own pages. They live on the website under /android/, are not in the website's menu, and have no
    // menu of their own, so a member who opens one from the app only sees the app's rules.
    const val TERMS = "https://viewgrow.site/android/terms/"
    const val PRIVACY = "https://viewgrow.site/android/privacy/"
    const val DELETE_ACCOUNT = "https://viewgrow.site/android/delete-account/"

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
