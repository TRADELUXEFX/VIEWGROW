package com.viewgrow.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// A notification that stays in the notification bar (Android calls it an "ongoing" notification).
// Tapping it opens a WhatsApp chat with the admin, so the member can get the status and reshare it.
// It is put back every time the app opens or an automatic sync runs, in case it was swiped away
// (Android 14 and up lets people swipe these away). It is removed on log out and when banned.
// If the admin's contact is not known yet, tapping it opens the app instead.
object ReshareReminder {
    private const val CHANNEL = "reshare"
    private const val NOTIFICATION_ID = 1004

    fun show(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.banned) { cancel(app); return }
        // The "Today's status is up" alert follows the same rule: on while logged in and not banned (StatusPush.kt).
        StatusPush.start(app)
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        // Low importance: no sound, no pop-up, it just sits in the bar.
        Alerts.channel(app, CHANNEL, "Reshare reminder", NotificationManager.IMPORTANCE_LOW)
        val name = Store.name
        val msg = if (name.isEmpty()) "Hi, I am ready to reshare your status."
        else "Hi, I am ready to reshare your status. My name is $name."
        val link = Remote.adminLink(msg)
        val intent = if (link != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val open = PendingIntent.getActivity(app, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // One short tip, easy to read at a glance.
        val text = "Tap to message the admin, then reshare his status to get new status viewers."
        val n = Alerts.builder(app, CHANNEL, "Get new status viewers today", text, open)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        Alerts.post(app, NOTIFICATION_ID, n)
    }

    fun cancel(ctx: Context) {
        StatusPush.stop(ctx)
        ctx.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
