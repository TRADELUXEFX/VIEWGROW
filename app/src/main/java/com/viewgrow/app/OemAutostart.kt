package com.viewgrow.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

// For phone brands that ship their own background-app killer on top of Android (Tecno, Infinix,
// itel, Xiaomi, Oppo, Vivo, Huawei ...). On those phones ViewGrow can be exempt from battery
// optimisation and still be stopped by the brand's own manager, so the automatic sync silently stops.
//
// Android has no way to read that setting back, so the app watches the result instead: the banner
// on Home shows only when no BACKGROUND sync has worked for a while (Store.isSyncStalled), and it
// goes away by itself as soon as one does.
//
// The screens below are brand-specific and can change between OS versions, so each one is tried in
// turn and the app's own Settings page is the fallback.
object OemAutostart {

    private val KNOWN_OEMS = setOf(
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
        "tecno", "infinix", "itel"
    )

    fun isKnownOem(): Boolean = Build.MANUFACTURER.lowercase() in KNOWN_OEMS

    // True only on a listed brand, while syncing is not paused, and only while the automatic sync
    // really looks stalled.
    fun needsPrompt(): Boolean = isKnownOem() && !Store.syncPaused && Store.isSyncStalled()

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
                Toast.makeText(
                    activity,
                    "Allow ViewGrow to auto-start / run in background, then come back",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (e: Exception) {
                // This screen does not exist on this phone or OS version. Try the next one.
            }
        }

        try {
            activity.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${activity.packageName}"))
            )
        } catch (e: Exception) {
            Toast.makeText(activity, "Couldn't open Settings", Toast.LENGTH_SHORT).show()
        }
    }
}
