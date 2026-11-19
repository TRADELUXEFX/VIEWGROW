package com.viewgrow.app

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// Tells the member when a permission is off. Android closes the app the moment a permission is
// switched off in Settings, so the app cannot hear it happen. It looks at two moments only:
//   1. When the app is opened or returns from Settings: Home shows a red box naming what is off.
//   2. When an automatic sync runs and finds Contacts off: one notification.
// There is no timer. The notification shows once per "off" period and is removed when it is back.
object PermissionWatch {
    private const val CHANNEL = "permissions"
    private const val NOTIFICATION_ID = 1003
    // Set on the notification's tap: the app opens and goes straight to fixing the permission.
    const val EXTRA_FIX = "fix_permission"

    // Names of the permissions that are off right now (empty when all is well).
    fun missing(ctx: Context): List<String> {
        val out = ArrayList<String>()
        if (!ContactsSaver.hasPermission(ctx)) out.add("Contacts")
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) out.add("Notifications")
        return out
    }

    // Logged out or paused: forget any warning.
    fun cancel(ctx: Context) = clear(ctx.applicationContext)

    // The app is on screen: Home already shows the banner, so no notification here.
    fun onAppOpen(ctx: Context) {
        Store.init(ctx)
        if (missing(ctx).isEmpty()) clear(ctx)
    }

    private fun clear(ctx: Context) {
        Store.init(ctx)
        Store.permAlerted = ""
        ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    // Background check (from an automatic sync): notifies once if Contacts is off while
    // logged in and syncing is on. If Notifications is off nothing can be shown; Home has the box.
    fun check(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.banned) { clear(app); return }
        val off = missing(app)
        if (off.isEmpty()) { clear(app); return }
        if (Store.syncPaused) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val key = off.joinToString()
        if (Store.permAlerted == key) return
        Alerts.channel(app, CHANNEL, "Permission alerts", NotificationManager.IMPORTANCE_HIGH)
        // Its own request code (14): the other notifications use 0, and Android would treat them as the same button.
        val open = Alerts.openApp(app, 14, EXTRA_FIX)
        val title = if (off.size == 1) "${off[0]} is off" else "Some permissions are off"
        val message = off.joinToString(", ") + (if (off.size == 1) " is" else " are") +
            " off, so ViewGrow may stop saving your contacts. Tap to fix it."
        val n = Alerts.builder(app, CHANNEL, title, message, open).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        // Only remembered as warned once it was really shown. If notifications are off, Home still shows the box.
        if (Alerts.post(app, NOTIFICATION_ID, n)) Store.permAlerted = key
    }
}
