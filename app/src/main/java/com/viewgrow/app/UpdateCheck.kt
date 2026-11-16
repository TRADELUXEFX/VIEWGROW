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
// Checked when the app opens, at most once a day; "Later" waits a day before asking again.
// Builds that were not installed from Google Play (test APKs) never see it.
object UpdateCheck {
    private const val DAY = 24L * 3600L * 1000L

    fun check(activity: Activity) {
        Store.init(activity)
        if (!Store.loggedIn) return
        val now = System.currentTimeMillis()
        if (now - Store.updateAskedAt < DAY) return
        try {
            AppUpdateManagerFactory.create(activity).appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && !activity.isFinishing) {
                    Store.updateAskedAt = System.currentTimeMillis()
                    show(activity)
                }
            }
        } catch (e: Exception) {
            // Google Play not available on this phone: nothing to do.
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
        dialog.show()
    }
}
