package com.viewgrow.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

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
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            // Low importance: no sound, no pop-up, it just sits in the bar.
            val ch = NotificationChannel(CHANNEL, "Reshare reminder", NotificationManager.IMPORTANCE_LOW)
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
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
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(app, R.color.green))
            .setContentTitle("Get new status viewers today")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Notifications are off for the app. Nothing to show.
        }
    }

    fun cancel(ctx: Context) {
        ctx.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
