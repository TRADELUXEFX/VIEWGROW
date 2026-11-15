package com.viewgrow.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// Tells the member when a permission is off. Android closes the app the moment a permission is
// switched off in Settings, so the app cannot hear it happen. It looks at two moments only:
//   1. When the app is opened or returns from Settings: Home shows a red box naming what is off.
//   2. When an automatic sync runs and finds Contacts or Battery off: one notification.
// There is no timer. The notification shows once per "off" period and is removed when it is back.
object PermissionWatch {
    private const val CHANNEL = "permissions"
    private const val NOTIFICATION_ID = 1003

    // Names of the permissions that are off right now (empty when all is well).
    fun missing(ctx: Context): List<String> {
        val out = ArrayList<String>()
        if (!ContactsSaver.hasPermission(ctx)) out.add("Contacts")
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) out.add("Notifications")
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) out.add("Battery")
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

    // Background check (from an automatic sync): notifies once if Contacts or Battery is off while
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
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Permission alerts", NotificationManager.IMPORTANCE_HIGH)
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (off.size == 1) "${off[0]} is off" else "Some permissions are off"
        val message = off.joinToString(", ") + (if (off.size == 1) " is" else " are") +
            " off, so ViewGrow may stop saving your contacts. Tap to fix in Profile."
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(app, R.color.green))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n)
            Store.permAlerted = key
        } catch (e: SecurityException) {
            // Notifications are off for the app. Home still shows the box.
        }
    }
}
