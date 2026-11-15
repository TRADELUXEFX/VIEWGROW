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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
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

// retryable: the only problem was the connection, so trying again when it is back makes sense.
class SyncResult(val ok: Boolean, val count: Int, val message: String, val retryable: Boolean = false)

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

    private fun isNet(error: String?): Boolean = error == "NETWORK" || error == "TIMEOUT"

    private fun doSync(ctx: Context): SyncResult {
        Store.init(ctx)
        if (!Store.loggedIn) return SyncResult(false, 0, "Please log in again.")
        if (!ContactsSaver.hasPermission(ctx)) {
            return SyncResult(false, 0, "Allow Contacts so ViewGrow can save your contacts.")
        }
        val phone = Store.phone
        val password = Store.password

        val p = Api.request("web_prepare_download", JSONObject().put("p_phone", phone).put("p_pin", password))
        val pj = p.json ?: return SyncResult(false, 0, Api.netMessage(p.error), isNet(p.error))
        if (!pj.optBoolean("ok")) return SyncResult(false, 0, Api.loginMessage(pj))
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
            return SyncResult(false, 0, "Saved, but could not confirm. Tap Sync again.", cj == null && isNet(c.error))
        }

        val count = pj.optInt("count", maxOf(0, cards.size - 1))
        Store.lastSyncCount = count
        Store.lastSyncAt = System.currentTimeMillis()
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

// The background timer: every 1h, 6h, 12h or 24h the phone asks the database if contacts are ready,
// and saves them. It does nothing while paused, logged out, or without the Contacts permission.
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        val app = applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.syncPaused) return ListenableWorker.Result.success()
        // The timer really ran, so the phone is letting the app work in the background.
        SyncHealth.recordBackgroundRun()
        if (!ContactsSaver.hasPermission(app)) return ListenableWorker.Result.success()
        val attempt = inputData.getInt(SyncRetry.KEY, 0)
        val st = Api.request("web_status", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password))
        val j = st.json
        if (j == null) {
            // No answer (no data, or too slow): try again as soon as the connection is back.
            SyncRetry.later(app, attempt)
            return ListenableWorker.Result.success()
        }
        if (j.optBoolean("ok") && j.optString("state") == "READY") {
            val res = Sync.runBlocking(app)
            if (!res.ok && res.retryable) SyncRetry.later(app, attempt)
        }
        return ListenableWorker.Result.success()
    }
}

object SyncScheduler {
    private const val NAME = "viewgrow-sync"

    // Starts the timer with the saved interval, or stops it when paused or logged out.
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.syncPaused) {
            cancel(app)
            // Not running on purpose, so the "auto-sync stopped" check starts fresh when it is turned back on.
            if (Store.loggedIn) { Store.bgBaselineAt = 0L; Store.lastBgAt = 0L }
            return
        }
        SyncHealth.markScheduled()
        val hours = if (Store.syncHours in intArrayOf(1, 6, 12, 24)) Store.syncHours else 24
        val req = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME)
        SyncRetry.cancel(ctx)
    }
}

// One extra try, held by Android until the phone has a connection again. Same worker, so it gets the
// same checks. REPLACE keeps a single waiting job however many times it fails, and it gives up after
// a few tries so a broken connection cannot loop all day. The next normal timer run still happens.
object SyncRetry {
    const val KEY = "attempt"
    private const val NAME = "viewgrow-sync-retry"
    private const val MAX_TRIES = 4

    fun later(ctx: Context, attempt: Int) {
        if (attempt >= MAX_TRIES) return
        val req = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(2, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putInt(KEY, attempt + 1).build())
            .build()
        WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME)
    }
}
