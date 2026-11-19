package com.viewgrow.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Messages from the admin, through Firebase Cloud Messaging. The Edge Function notify-status sends them.
//
// Topics the phone joins while the member is logged in and not banned:
//   "members"  everyone: "Today's status is up", the admin's own messages, and the 10pm wake-up.
//   "u<id>"    this member only: group reminders (Never reshared, Limit full). The id comes from web_app_seen.
// No phone token is stored anywhere. The messages carry no personal data; the phone writes the words for
// "status" and shows the admin's own words for "msg".
//
// Message types (data "type"):
//   status  "Today's status is up". Tap opens the admin's WhatsApp chat.
//   msg     title + body from the admin. data "open": app | admin (WhatsApp chat, nothing typed) | link (data "url", https) | upgrade (Increase my contact limit).
//   wake    silent: tonight's contacts are ready, save them now (WakeWorker). Nothing is shown.
object StatusPush {
    const val TOPIC = "members"
    private const val CHANNEL = "status_alert"
    private const val CHANNEL_MSG = "admin_messages"
    private const val NOTIFICATION_ID = 1008
    private const val MSG_ID = 1009

    private fun userTopic(id: Long) = "u$id"

    // Joins the topics. Called with the Reshare reminder (logged in, not banned). Also tells the database, at most
    // every 6 hours, that this member uses the app, which gives the id for the member's own topic.
    fun start(ctx: Context) {
        // Joining a topic lasts, so it is repeated only once a week (this runs on every app open and every sync).
        val now = System.currentTimeMillis()
        if (now - Store.topicsJoinedAt > 7L * 24L * 3600L * 1000L) {
            try { FirebaseMessaging.getInstance().subscribeToTopic(TOPIC) } catch (e: Exception) { }
            val id = Store.userId
            if (id > 0) try { FirebaseMessaging.getInstance().subscribeToTopic(userTopic(id)) } catch (e: Exception) { }
            Store.topicsJoinedAt = now
        }
        AppSeen.ping(ctx)
    }

    // Leaves the topics: log out, ban, account deleted.
    fun stop(ctx: Context) {
        try { FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC) } catch (e: Exception) { }
        val id = Store.userId
        if (id > 0) try { FirebaseMessaging.getInstance().unsubscribeFromTopic(userTopic(id)) } catch (e: Exception) { }
        Store.userId = 0L
        Store.topicsJoinedAt = 0L
        val nm = ctx.applicationContext.getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID)
        nm?.cancel(MSG_ID)
    }

    // The member's id is known (first time, or it changed): join the member's own topic.
    fun joinUser(id: Long) {
        try { FirebaseMessaging.getInstance().subscribeToTopic(userTopic(id)) } catch (e: Exception) { }
    }

    fun onMessage(ctx: Context, data: Map<String, String>) {
        val app = ctx.applicationContext
        Store.init(app)
        // Logged out or banned since the phone joined: show nothing, and leave.
        if (!Store.loggedIn || Store.banned) { stop(app); return }
        when (data["type"]) {
            "status" -> showStatus(app)
            "msg" -> showMessage(app, data["title"].orEmpty(), data["body"].orEmpty(), data["open"].orEmpty(), data["url"].orEmpty())
            "wake" -> WakeWorker.start(app)
        }
    }

    // [reshare] true: the chat opens with "Hi, I am ready to reshare your status" typed in (the status-is-up alert).
    // false: the chat just opens, nothing typed (messages the admin sends from the admin page).
    private fun adminTap(app: Context, code: Int, reshare: Boolean): PendingIntent {
        val name = Store.name
        val link = Remote.adminLink(
            if (!reshare) "" else if (name.isEmpty()) "Hi, I am ready to reshare your status." else "Hi, I am ready to reshare your status. My name is $name."
        )
        return if (link != null) {
            PendingIntent.getActivity(
                app, code, Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else Alerts.openApp(app, code, null)
    }

    private fun linkTap(app: Context, code: Int, url: String): PendingIntent? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(app, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun showStatus(app: Context) {
        if (!Alerts.canPost(app)) return
        // High importance: it pops up, because it is the one moment that matters for the day.
        Alerts.channel(app, CHANNEL, "Status is up", NotificationManager.IMPORTANCE_HIGH)
        post(app, CHANNEL, NOTIFICATION_ID, "Today's status is up 📣",
            "Reshare it now to be on tonight's list for new status viewers.", adminTap(app, 9, reshare = true))
    }

    private fun showMessage(app: Context, title: String, body: String, open: String, url: String = "") {
        if (title.isBlank() || body.isBlank() || !Alerts.canPost(app)) return
        Alerts.channel(app, CHANNEL_MSG, "Messages from the admin", NotificationManager.IMPORTANCE_HIGH)
        val tap = when (open) {
            "admin" -> adminTap(app, 10, reshare = false)
            // The link the admin typed (https only): opens in the browser, or the app that owns that link.
            "link" -> linkTap(app, 13, url) ?: Alerts.openApp(app, 12, null)
            "upgrade" -> Alerts.openApp(app, 11, LimitAlert.EXTRA_UPGRADE)
            else -> Alerts.openApp(app, 12, null)
        }
        post(app, CHANNEL_MSG, MSG_ID, title.take(80), body.take(300), tap)
    }

    private fun post(app: Context, channel: String, id: Int, title: String, text: String, tap: PendingIntent) {
        val n = Alerts.builder(app, channel, title, text, tap).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        Alerts.post(app, id, n)
    }
}

// Tells the database "this member uses the app" (web_app_seen), at most every 6 hours, and learns the member's id.
// The admin page uses it to show who uses the app and to send group reminders to app members only.
object AppSeen {
    private const val EVERY = 6L * 3600L * 1000L
    private val pool = Executors.newSingleThreadExecutor()

    fun ping(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.banned) return
        if (Store.userId > 0 && System.currentTimeMillis() - Store.appSeenAt < EVERY) return
        pool.execute {
            val r = Api.request("web_app_seen", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password))
            val j = r.json ?: return@execute
            if (!j.optBoolean("ok")) return@execute
            Store.appSeenAt = System.currentTimeMillis()
            val id = j.optLong("id", 0L)
            if (id > 0 && id != Store.userId) {
                Store.userId = id
                StatusPush.joinUser(id)
            }
        }
    }
}

// The 10pm wake-up: saves tonight's contacts right away, even if the app was closed. Each phone starts after a
// random 0 to 90 seconds, so they do not all reach the database at the same moment. Normal background work (not a
// foreground service), so it needs no extra permission; Android may still hold it a little on some phones, and the
// 10:10pm check and the 8am catch-up (Nightly.kt) remain as backups.
class WakeWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        val ok = BackgroundSync.run(applicationContext)
        // Database not reachable, the release not landed yet, or the save failed: try again in a few minutes.
        if ((ok == false || BackgroundSync.retryWanted) && runAttemptCount < 4) return ListenableWorker.Result.retry()
        return ListenableWorker.Result.success()
    }

    companion object {
        fun start(ctx: Context) {
            Store.init(ctx)
            if (!Store.loggedIn || Store.syncPaused || Store.banned) return
            val req = OneTimeWorkRequestBuilder<WakeWorker>()
                .setInitialDelay((Math.random() * 90_000).toLong(), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 3, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("viewgrow-wake", ExistingWorkPolicy.KEEP, req)
        }
    }
}

// Receives the messages. Declared in AndroidManifest.xml.
class StatusPushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        StatusPush.onMessage(this, message.data)
    }

    // Topics do not need the phone's token, so a new token needs no action.
    override fun onNewToken(token: String) {}
}
