package com.viewgrow.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// Two daily jobs that make the overnight delivery feel automatic. Each one runs once, then books the next day.
//
// 1. Release check, 10:10pm Lagos time (the release is at 10pm Lagos). Saves the new contacts the same night,
//    whatever interval the member chose in Profile (1, 6, 12 or 24 hours).
// 2. Morning message, 8am on the phone's clock. Saves anything still waiting, then shows ONE of:
//    - "48 new status viewers added last night" when contacts were saved during the night (they are not
//      announced at night, see Notifier in Sync.kt), or
//    - "41 people joined last night" when the member was not on last night's list (from web_last_release).
//      Tapping it opens the admin's WhatsApp chat. Not shown at the contact limit, and once per night only.
//
// Android decides the exact minute (it saves battery by grouping background work), so both can come a little late.
// Nothing runs while the member is logged out, paused or banned.
object Nightly {
    private const val RELEASE = "viewgrow-release-check"
    private const val MORNING = "viewgrow-morning"
    private const val CHANNEL = "morning"
    private const val NOTIFICATION_ID = 1007

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    // Milliseconds from now to the next hh:mm in the given time zone.
    private fun delayTo(hour: Int, minute: Int, zone: TimeZone): Long {
        val now = Calendar.getInstance(zone)
        val next = Calendar.getInstance(zone)
        next.set(Calendar.HOUR_OF_DAY, hour)
        next.set(Calendar.MINUTE, minute)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis - now.timeInMillis
    }

    private fun releaseRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ReleaseCheckWorker>()
        .setInitialDelay(delayTo(22, 10, TimeZone.getTimeZone("Africa/Lagos")), TimeUnit.MILLISECONDS)
        .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 3, TimeUnit.MINUTES)
        .setConstraints(online)
        .build()

    private fun morningRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<MorningWorker>()
        .setInitialDelay(delayTo(8, 0, TimeZone.getDefault()), TimeUnit.MILLISECONDS)
        .build()

    private fun active(): Boolean = Store.loggedIn && !Store.syncPaused && !Store.banned

    // Books both jobs if they are not booked yet (a booked one is left alone). Called whenever the sync timer is set.
    fun ensure(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!active()) { cancel(app); return }
        val wm = WorkManager.getInstance(app)
        wm.enqueueUniqueWork(RELEASE, ExistingWorkPolicy.KEEP, releaseRequest())
        wm.enqueueUniqueWork(MORNING, ExistingWorkPolicy.KEEP, morningRequest())
    }

    // A job just ran: book the next day.
    fun bookNextRelease(ctx: Context) {
        Store.init(ctx)
        if (active()) WorkManager.getInstance(ctx).enqueueUniqueWork(RELEASE, ExistingWorkPolicy.REPLACE, releaseRequest())
    }

    fun bookNextMorning(ctx: Context) {
        Store.init(ctx)
        if (active()) WorkManager.getInstance(ctx).enqueueUniqueWork(MORNING, ExistingWorkPolicy.REPLACE, morningRequest())
    }

    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        val wm = WorkManager.getInstance(app)
        wm.cancelUniqueWork(RELEASE)
        wm.cancelUniqueWork(MORNING)
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    // True between 9pm and 7am on the phone's clock: contacts saved then are announced at 8am instead.
    fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 21 || h < 7
    }

    // The 8am job. Blocking, background thread only.
    fun morning(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!active()) return
        // Catch up first: a phone that was off or offline at 10pm saves last night's contacts now.
        BackgroundSync.run(app)

        val saved = Store.morningCount
        if (saved > 0) {
            Store.morningCount = 0
            val title = if (saved == 1) "1 new status viewer added last night 🎉"
            else "%,d new status viewers added last night 🎉".format(saved)
            show(app, title, "They are saved in your phone, tagged " + ContactsSaver.TAG + ". Keep resharing every day to get more.",
                Alerts.openApp(app, 7, null))
            return
        }
        missedCheck(app)
    }

    // "41 people joined last night": only when the member was not on last night's list.
    private fun missedCheck(app: Context) {
        // At the contact limit nothing new can arrive anyway; the limit alert covers that.
        if (Store.lastLimit > 0 && Store.lastTotal >= Store.lastLimit) return
        val r = Api.request("web_last_release", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password))
        // No answer, or the database function is not installed yet: say nothing.
        val j = r.json ?: return
        if (!j.optBoolean("ok") || !j.optBoolean("last_night") || j.optBoolean("you_in")) return
        val people = j.optInt("people", 0)
        val night = j.optString("batch")
        if (people <= 0 || night.isEmpty() || night == Store.missedNoticeFor) return
        Store.missedNoticeFor = night
        val title = if (people == 1) "1 person joined last night" else "%,d people joined last night".format(people)
        val text = "You were not on last night's list. Reshare the admin's status today to get the next ones."
        val name = Store.name
        val link = Remote.adminLink(
            if (name.isEmpty()) "Hi, I am ready to reshare your status." else "Hi, I am ready to reshare your status. My name is $name."
        )
        val tap = if (link != null) {
            PendingIntent.getActivity(
                app, 8, Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else Alerts.openApp(app, 8, null)
        show(app, title, text, tap)
    }

    private fun show(app: Context, title: String, text: String, tap: PendingIntent) {
        if (!Alerts.canPost(app)) return
        Alerts.channel(app, CHANNEL, "Morning update")
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(app, R.color.green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Notifications are off for the app. Nothing to show.
        }
    }
}

// 10:10pm Lagos: save tonight's contacts, then book tomorrow.
class ReleaseCheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        val ok = BackgroundSync.run(applicationContext)
        // Database not reachable yet (for example the release is still running): try again shortly.
        if ((ok == false || BackgroundSync.retryWanted) && runAttemptCount < 4) return ListenableWorker.Result.retry()
        Nightly.bookNextRelease(applicationContext)
        return ListenableWorker.Result.success()
    }
}

// 8am: the morning message, then book tomorrow.
class MorningWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        Nightly.morning(applicationContext)
        Nightly.bookNextMorning(applicationContext)
        return ListenableWorker.Result.success()
    }
}
