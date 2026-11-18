package com.viewgrow.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// banned = the database said BANNED: the screens switch to the Banned page.
class SyncResult(val ok: Boolean, val count: Int, val message: String, val banned: Boolean = false)

// Gets the contacts the database prepared, saves them to the phone, then tells the database they were saved.
// The app never chooses the contacts: web_prepare_download does, with the same rules as the website.
object Sync {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // From a screen: runs in the background, answers on the main thread.
    fun start(ctx: Context, onDone: (SyncResult) -> Unit) {
        val app = ctx.applicationContext
        pool.execute {
            val res = runBlocking(app)
            main.post { onDone(res) }
        }
    }

    // "Sync now" also puts back contacts the member already received but deleted from the phone (web_app_restore).
    // Gives the number put back, or -1 when it could not be checked (not logged in, no Contacts permission, no
    // network, or the database function is not installed yet). Numbers already in the phone are skipped.
    fun restore(ctx: Context, onDone: (Int) -> Unit) {
        val app = ctx.applicationContext
        pool.execute {
            val n = if (running.compareAndSet(false, true)) {
                try { restoreMissing(app) } finally { running.set(false) }
            } else -1
            main.post { onDone(n) }
        }
    }

    private fun restoreMissing(ctx: Context): Int {
        Store.init(ctx)
        if (!Store.loggedIn || Store.banned || !ContactsSaver.hasPermission(ctx)) return -1
        val r = Api.request("web_app_restore", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password))
        val j = r.json ?: return -1
        if (!j.optBoolean("ok")) return -1
        return ContactsSaver.saveCounting(ctx, ContactsSaver.parse(j.optString("vcf")))
    }

    // On a background thread only.
    fun runBlocking(ctx: Context): SyncResult {
        if (!running.compareAndSet(false, true)) return SyncResult(false, 0, "Already syncing. Please wait.")
        try {
            return doSync(ctx)
        } finally {
            running.set(false)
        }
    }

    private fun doSync(ctx: Context): SyncResult {
        Store.init(ctx)
        if (!Store.loggedIn) return SyncResult(false, 0, "Please log in again.")
        if (Store.banned) return SyncResult(false, 0, "This account has been banned.", banned = true)
        if (!ContactsSaver.hasPermission(ctx)) {
            return SyncResult(false, 0, "Allow Contacts so ViewGrow can save your contacts.")
        }
        val phone = Store.phone
        val password = Store.password

        val p = Api.request("web_prepare_download", JSONObject().put("p_phone", phone).put("p_pin", password))
        val pj = p.json ?: return SyncResult(false, 0, Api.netMessage(p.error))
        if (!pj.optBoolean("ok")) {
            if (pj.optString("code") == "BANNED") {
                // Banned since the last time: remember it and take the saved contacts off the phone.
                Ban.mark(ctx, pj.optString("reason"))
                Ban.removeContacts(ctx)
                return SyncResult(false, 0, Api.loginMessage(pj), banned = true)
            }
            return SyncResult(false, 0, Api.loginMessage(pj))
        }
        if (pj.optString("status") != "ready") return SyncResult(false, 0, "Nothing to save right now. Check back later.")

        val cards = ContactsSaver.parse(pj.optString("vcf"))
        if (!ContactsSaver.save(ctx, cards)) {
            return SyncResult(false, 0, "Could not save to your phone. Please try again.")
        }
        // The phone keeps its own record of who was saved today, for the History day screen.
        HistoryLog.add(ctx, cards, HistoryLog.today())

        val c = Api.request(
            "web_confirm_download",
            JSONObject().put("p_phone", phone).put("p_pin", password).put("p_download_id", pj.get("download_id"))
        )
        val cj = c.json
        if (cj == null || !cj.optBoolean("ok")) {
            // The contacts are in the phone. Syncing again gives the same file, and nothing is saved twice.
            return SyncResult(false, 0, "Saved, but could not confirm. Please open the app again.")
        }

        val count = pj.optInt("count", maxOf(0, cards.size - 1))
        Store.lastSyncCount = count
        Store.lastSyncAt = System.currentTimeMillis()
        Reminder.restart(ctx)
        Notifier.contactsSaved(ctx, count)
        return SyncResult(true, count, "")
    }
}

// The "48 contacts saved" message, made on the phone itself.
// At night (9pm to 7am on the phone's clock) nothing is shown: the count is kept and the 8am message
// "48 new status viewers added last night" shows it instead (Nightly.kt), so nobody is woken up.
object Notifier {
    private const val CHANNEL = "contacts"

    fun contactsSaved(ctx: Context, count: Int) {
        if (count <= 0) return
        if (Nightly.isNight()) {
            Store.morningCount = Store.morningCount + count
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Contacts saved", NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (count == 1) "1 contact saved" else "$count contacts saved"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(ctx, R.color.green))
            .setContentTitle(title)
            .setContentText("They are in your phone now, tagged " + ContactsSaver.TAG + ".")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1001, n)
        } catch (e: SecurityException) {
            // Notifications are off for the app. The contacts are saved anyway.
        }
    }
}

// One automatic run, run by the timer below (WorkManager).
// Returns true when it finished cleanly (nothing to save counts), false when the database could not
// be reached (try again soon), and null when it was skipped or did not finish.
// A clean run is remembered (Store.lastBgSyncAt), so the 3-day reminder knows the last time contacts were checked.
object BackgroundSync {
    // Set by run(): true when the night's contacts are not in the phone yet but should be soon (the release had not
    // landed when we asked, or the save failed). The 10pm wake-up and the 10:10pm check use it to try again shortly.
    @Volatile var retryWanted = false

    fun run(ctx: Context): Boolean? {
        retryWanted = false
        Store.init(ctx)
        // Put the "Get new status viewers today" notification back if it was swiped away.
        ReshareReminder.show(ctx)
        if (!Store.loggedIn || Store.syncPaused || Store.banned) return null
        // Contacts or Notifications off: warn (once), then carry on if Contacts is still allowed.
        PermissionWatch.check(ctx)
        if (!ContactsSaver.hasPermission(ctx)) return null
        // The admin's number, saved once so the member can see the admin's status (AdminContactSaver.kt).
        AdminContactSaver.saveNow(ctx)
        val st = Api.request("web_status", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password))
        val j = st.json ?: return false
        if (!j.optBoolean("ok")) {
            if (j.optString("code") == "BANNED") {
                Ban.mark(ctx, j.optString("reason"))
                Ban.removeContacts(ctx)
            }
            return null
        }
        var status = j
        // Verified but the release has not happened yet: at 10pm that means the database is a little late.
        if (j.optString("state") == "PREPARING") retryWanted = true
        if (j.optString("state") == "READY") {
            val r = Sync.runBlocking(ctx)
            if (!r.ok) {
                retryWanted = !r.banned && !r.message.startsWith("Already syncing")
                return null
            }
            // The sync changed the total, so ask again before checking the limit.
            Api.request("web_status", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)).json
                ?.takeIf { it.optBoolean("ok") }?.let { status = it }
        }
        // 80% of the contact limit used: one notification (LimitAlert.kt).
        if (!status.isNull("limit")) {
            LimitAlert.check(ctx, if (status.isNull("total")) 0 else status.optInt("total"), status.optInt("limit"))
        }
        Store.lastBgSyncAt = System.currentTimeMillis()
        Reminder.restart(ctx)
        return true
    }
}

// The background timer: every 1h, 6h, 12h or 24h the phone asks the database if contacts are ready,
// and saves them. It does nothing while paused, logged out, or without the Contacts permission.
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        if (BackgroundSync.run(applicationContext) == false) return ListenableWorker.Result.retry()
        return ListenableWorker.Result.success()
    }
}

object SyncScheduler {
    private const val NAME = "viewgrow-sync"

    // Starts the timer with the saved interval, or stops it when paused or logged out.
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.syncPaused || Store.banned) { cancel(app); return }
        Reminder.ensure(app)
        WeeklySummary.ensure(app)
        Nightly.ensure(app)
        val hours = if (Store.syncHours in intArrayOf(1, 6, 12, 24)) Store.syncHours else 24
        val req = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME)
        Reminder.cancel(ctx.applicationContext)
        WeeklySummary.cancel(ctx.applicationContext)
        Nightly.cancel(ctx.applicationContext)
        PermissionWatch.cancel(ctx.applicationContext)
    }
}
