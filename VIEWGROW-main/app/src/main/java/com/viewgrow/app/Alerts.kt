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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Small helpers the two notifications below share.
object Alerts {
    // False when the phone would not show a notification (permission off or notifications switched off).
    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun channel(ctx: Context, id: String, name: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    // Tapping the notification opens the app. extra (if any) tells MainActivity what to show.
    fun openApp(ctx: Context, code: Int, extra: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (extra != null) i.putExtra(extra, true)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

// A notification when 80% or more of the contact limit is used. It is shown once for each limit: a new
// one comes only after the limit changes (a pack was bought) and 80% is reached again. The numbers are the
// total and limit the database sends (web_status), checked by the automatic sync. Tapping it opens the
// "Increase my contact limit" pop-up. No database change.
object LimitAlert {
    private const val CHANNEL = "limit"
    private const val NOTIFICATION_ID = 1005
    private const val PERCENT = 80
    const val EXTRA_UPGRADE = "open_upgrade"

    fun check(ctx: Context, total: Int, limit: Int) {
        val app = ctx.applicationContext
        Store.init(app)
        if (limit <= 0) return
        val pct = (total * 100L / limit).toInt()
        if (pct < PERCENT) {
            // Under 80% again (the limit went up): the next time it fills, the member is told again.
            if (Store.limitAlerted != 0) Store.limitAlerted = 0
            app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            return
        }
        if (Store.limitAlerted == limit) return
        // The pop-up that opens from the notification shows these numbers.
        Store.lastTotal = total
        Store.lastLimit = limit
        if (show(app, total, limit, minOf(pct, 99))) Store.limitAlerted = limit
    }

    private fun show(app: Context, total: Int, limit: Int, pct: Int): Boolean {
        if (!Alerts.canPost(app)) return false
        Alerts.channel(app, CHANNEL, "Contact limit")
        val title: String
        val text: String
        if (total >= limit) {
            title = "Contact limit reached"
            text = "You have %,d of %,d contacts. Raise your limit, then keep resharing to get new contacts.".format(total, limit)
        } else {
            title = "You have used $pct% of your contact limit"
            text = "%,d of %,d contacts used. Raise your limit before it is full.".format(total, limit)
        }
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(app, R.color.green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(Alerts.openApp(app, 5, EXTRA_UPGRADE))
            .build()
        return try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

// Once a week (Sunday evening on the phone's clock): "142 new contacts this week". The number comes from the
// phone's own record of what each sync saved (HistoryLog), so it needs no database call. A week with no new
// contacts sends nothing.
class WeeklySummaryWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        WeeklySummary.fire(applicationContext)
        return ListenableWorker.Result.success()
    }
}

object WeeklySummary {
    private const val NAME = "viewgrow-weekly"
    private const val CHANNEL = "weekly"
    private const val NOTIFICATION_ID = 1006
    private const val DAYS = 7

    // Milliseconds from now to the next Sunday at 6pm.
    private fun delayToSunday(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance()
        next.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        next.set(Calendar.HOUR_OF_DAY, 18)
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 7)
        return next.timeInMillis - now.timeInMillis
    }

    // Starts the weekly clock, or stops it when the member is logged out, paused, or banned.
    // A clock that is already running is left alone.
    fun ensure(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.syncPaused || Store.banned) { cancel(app); return }
        val req = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(DAYS.toLong(), TimeUnit.DAYS)
            .setInitialDelay(delayToSunday(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(NAME)
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    fun fire(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.syncPaused || Store.banned) return
        val n = HistoryLog.countSince(app, DAYS)
        if (n <= 0 || !Alerts.canPost(app)) return
        Alerts.channel(app, CHANNEL, "Weekly summary")
        val title = if (n == 1) "1 new contact this week" else "%,d new contacts this week".format(n)
        val text = "That is how many ViewGrow saved to your phone in the last 7 days."
        val note = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(app, R.color.green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(Alerts.openApp(app, 6, null))
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, note)
        } catch (e: SecurityException) {
            // Notifications are off for the app. Nothing to show.
        }
    }
}
