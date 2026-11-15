package com.viewgrow.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

// Did the background timer really run lately? Android cannot tell the app whether a brand's own
// "auto-start" switch is on, so the app watches the result instead: every time the timer runs it
// writes down the time, and if that has not happened for a long while the timer looks stopped.
object SyncHealth {
    fun markScheduled() {
        if (Store.bgBaselineAt == 0L) Store.bgBaselineAt = System.currentTimeMillis()
    }

    fun recordBackgroundRun() {
        Store.lastBgAt = System.currentTimeMillis()
    }

    // Twice the chosen interval, but never less than 30 hours. Android may run background jobs late to
    // save battery, so one late run must not count as stopped. Never true before the timer was first
    // scheduled, so a new install never shows it.
    fun isStalled(): Boolean {
        val since = if (Store.lastBgAt != 0L) Store.lastBgAt else Store.bgBaselineAt
        if (since == 0L) return false
        val interval = if (Store.syncHours in intArrayOf(1, 6, 12, 24)) Store.syncHours else 24
        val limitHours = maxOf(30, 2 * interval)
        return System.currentTimeMillis() - since > limitHours * 3600L * 1000L
    }
}

// Phone brands that ship their own background-app killer on top of Android (Tecno, Infinix, itel,
// Xiaomi, Oppo, Vivo, Huawei ...). The app can be exempt from battery optimisation and still be stopped
// by the brand's manager. These screens are brand-specific and undocumented, so each one is tried in
// turn and the app's own settings page is the fallback.
object OemAutostart {

    private val KNOWN = setOf(
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
        "tecno", "infinix", "itel"
    )

    fun isKnownOem(): Boolean = Build.MANUFACTURER.lowercase() in KNOWN

    // True only on a listed brand and only while the timer really looks stopped.
    fun needsPrompt(): Boolean = isKnownOem() && SyncHealth.isStalled()

    fun openSettings(activity: Activity) {
        val m = Build.MANUFACTURER.lowercase()
        fun c(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

        val candidates: List<Intent> = when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> listOf(
                c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                c("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity")
            )
            m.contains("huawei") || m.contains("honor") -> listOf(
                c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                c("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> listOf(
                c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            )
            m.contains("vivo") || m.contains("iqoo") -> listOf(
                c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            m.contains("tecno") || m.contains("infinix") || m.contains("itel") -> listOf(
                c("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity"),
                c("com.transsion.phonemanager", "com.transsion.phonemanager.module.security.autoboot.AutoBootManagerActivity")
            )
            else -> emptyList()
        }

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
                Toast.makeText(activity, "Allow ViewGrow to auto-start and run in the background, then come back", Toast.LENGTH_LONG).show()
                return
            } catch (e: Exception) {
                // This screen does not exist on this phone or version: try the next one.
            }
        }

        try {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + activity.packageName))
            )
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open Settings", Toast.LENGTH_SHORT).show()
        }
    }
}
