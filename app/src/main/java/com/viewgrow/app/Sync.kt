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
            return SyncResult(false, 0, "Saved, but could not confirm. Tap Sync again.")
        }

        val count = pj.optInt("count", maxOf(0, cards.size - 1))
        Store.lastSyncCount = count
        Store.lastSyncAt = System.currentTimeMillis()
        if (Store.firstSyncAt == 0L) Store.firstSyncAt = Store.lastSyncAt
        Reminder.restart(ctx)
        Notifier.contactsSaved(ctx, count)
        return SyncResult(true, count, "")
    }
}

// The "48 contacts saved" message, made on the phone itself.
object Notifier {
    private const val CHANNEL = "contacts"

    fun contactsSaved(ctx: Context, count: Int) {
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

// One automatic run, used by both triggers: the timer below and the sync adapter (SyncAdapter.kt).
// Returns true when it finished cleanly (nothing to save counts), false when the database could not
// be reached (try again soon), and null when it was skipped or did not finish.
// A clean run is remembered, which is what clears the "Auto-sync looks stopped" banner on Home.
object BackgroundSync {
    fun run(ctx: Context): Boolean? {
        Store.init(ctx)
        if (!Store.loggedIn || Store.syncPaused || Store.banned) return null
        if (!ContactsSaver.hasPermission(ctx)) return null
        val st = Api.request("web_status", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password))
        val j = st.json ?: return false
        if (!j.optBoolean("ok")) {
            if (j.optString("code") == "BANNED") {
                Ban.mark(ctx, j.optString("reason"))
                Ban.removeContacts(ctx)
            }
            return null
        }
        if (j.optString("state") == "READY" && !Sync.runBlocking(ctx).ok) return null
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
        SyncAdapterSetup.enable(app)
        Reminder.ensure(app)
        val hours = if (Store.syncHours in intArrayOf(1, 6, 12, 24)) Store.syncHours else 24
        val req = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME)
        SyncAdapterSetup.disable(ctx.applicationContext)
        Reminder.cancel(ctx.applicationContext)
    }
}
