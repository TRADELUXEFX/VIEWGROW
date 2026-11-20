package com.viewgrow.app

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

// "Update ViewGrow": when Google Play has a newer version of the app, a pop-up offers to open the Play Store page.
// Google Play itself says whether an update exists (Play In-App Updates library), so nothing is set by hand.
// Asked every time the app is opened while it is out of date: on start, and when coming back after at least 30 seconds
// away (a quick trip to the phone's Settings or the Play Store does not bring it back at once). "Later" closes it until
// the next open. Shown on every screen except the Banned screen, also before log in.
// Note: Google Play on the phone can take some hours to learn that a new version was published; until then it says
// there is no update, and the pop-up cannot know either.
// Builds that were not installed from Google Play (test APKs) never see it.
object UpdateCheck {
    private const val AWAY = 30_000L

    private var askedThisVisit = false     // already checked (or shown) since the app came to the front
    private var checking = false           // a check is running: never two at once, never two pop-ups
    private var showing: Dialog? = null
    private var leftAt = 0L

    // MainActivity.onStop: the app went to the background.
    fun onLeave() { leftAt = System.currentTimeMillis() }

    // MainActivity.onDestroy: close a pop-up that belongs to the screen going away (it is shown again on the next open).
    fun onScreenGone() {
        try { showing?.dismiss() } catch (e: Exception) { }
        showing = null
        checking = false
        askedThisVisit = false
    }

    fun check(activity: Activity) {
        Store.init(activity)
        if (Store.banned) return
        if (leftAt > 0 && System.currentTimeMillis() - leftAt >= AWAY) askedThisVisit = false
        leftAt = 0
        if (askedThisVisit || checking || showing?.isShowing == true) return
        checking = true
        try {
            AppUpdateManagerFactory.create(activity).appUpdateInfo
                .addOnSuccessListener { info ->
                    checking = false
                    askedThisVisit = true
                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        !activity.isFinishing && !activity.isDestroyed && showing?.isShowing != true) {
                        show(activity)
                    }
                }
                .addOnFailureListener { checking = false }
        } catch (e: Exception) {
            // Google Play not available on this phone: nothing to do.
            checking = false
        }
    }

    private fun show(activity: Activity) {
        val v = activity.layoutInflater.inflate(R.layout.dialog_update, null)
        val dialog = Dialog(activity, R.style.Theme_ViewGrow_Dialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        Fonts.apply(v)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(activity.resources.displayMetrics.widthPixels - Ui.dp(activity, 44), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.CENTER)
        v.findViewById<TextView>(R.id.update_now).setOnClickListener {
            dialog.dismiss()
            val id = activity.packageName
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
            } catch (e: ActivityNotFoundException) {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
                } catch (e2: ActivityNotFoundException) { }
            }
        }
        v.findViewById<TextView>(R.id.update_later).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { if (showing === dialog) showing = null }
        showing = dialog
        dialog.show()
    }
}
