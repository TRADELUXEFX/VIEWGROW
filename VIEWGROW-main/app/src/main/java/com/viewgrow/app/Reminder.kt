package com.viewgrow.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// A gentle reminder when nothing has synced for a few days. (Idea from VG Contact.)
// A one-off timer is restarted every time a sync finishes cleanly (Home, timer or sync adapter).
// Only syncing counts, not opening the app. It fires after DAYS days without one, and says so only
// if the phone really has not synced. If Contacts permission is off it says that instead.
// It does nothing while logged out or paused.
class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        Reminder.fire(applicationContext)
        return ListenableWorker.Result.success()
    }
}

object Reminder {
    private const val NAME = "viewgrow-reminder"
    private const val CHANNEL = "reminders"
    private const val NOTIFICATION_ID = 1002
    private const val DAYS = 3L

    private fun request() = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(DAYS, TimeUnit.DAYS)
        .build()

    // A sync just finished: clear any reminder on screen and start the clock again.
    fun restart(ctx: Context) {
        val app = ctx.applicationContext
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        WorkManager.getInstance(app).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request())
    }

    // Logged in and not paused: make sure a clock is running, without restarting one that already is.
    fun ensure(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request())
    }

    // Paused or logged out.
    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(NAME)
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    fun fire(ctx: Context) {
        Store.init(ctx)
        if (!Store.loggedIn || Store.syncPaused || Store.banned) return
        // A sync may have worked since the clock started (for example if the app was force-stopped).
        val last = maxOf(Store.lastBgSyncAt, Store.lastSyncAt)
        if (last > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DAYS)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val title: String
        val message: String
        if (!ContactsSaver.hasPermission(ctx)) {
            title = "Contacts permission is off"
            message = "Turn on Contacts to save your contacts. Tap to open."
        } else {
            title = "New contacts may be waiting"
            message = "Not synced in $DAYS days. Open the app to save new contacts."
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(ctx, R.color.green))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Notifications are off for the app. Nothing to do.
        }
    }
}
