package com.viewgrow.admin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
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
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// The same phone number written different ways gets one key (last 10 digits):
// "+2348031152290", "08031152290" and "0803 115 2290" all give "8031152290".
fun numberKey(number: String): String {
    val d = number.filter { it.isDigit() }
    return if (d.length > 10) d.takeLast(10) else d
}

// The ViewGrow members, kept on this phone: number key -> name (as "Name VG").
// Filled by the sync from the database (web_admin_new_members). Used to tell members from other contacts.
object MemberList {
    private var cache: HashMap<String, String>? = null
    private fun file(ctx: Context) = File(ctx.filesDir, "members.json")

    @Synchronized
    fun all(ctx: Context): Map<String, String> {
        cache?.let { return it }
        val m = HashMap<String, String>()
        try {
            val f = file(ctx)
            if (f.exists()) {
                val o = JSONObject(f.readText())
                o.keys().forEach { k -> m[k] = o.optString(k) }
            }
        } catch (e: Exception) { }
        cache = m
        return m
    }

    @Synchronized
    fun save(ctx: Context, m: Map<String, String>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        try { file(ctx).writeText(o.toString()) } catch (e: Exception) { }
        cache = HashMap(m)
    }

    @Synchronized
    fun clear(ctx: Context) { file(ctx).delete(); cache = null }

    fun isMember(ctx: Context, number: String): Boolean = all(ctx).containsKey(numberKey(number))
    fun nameOf(ctx: Context, number: String): String? = all(ctx)[numberKey(number)]
}

// The phone's own contacts.
object PhoneContacts {
    fun allowed(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    // Number keys of everyone in the phone.
    fun keys(ctx: Context): HashSet<String> {
        val set = HashSet<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
            )?.use { c -> while (c.moveToNext()) numberKey(c.getString(0) ?: "").let { if (it.length >= 10) set.add(it) } }
        } catch (e: Exception) { }
        return set
    }

    // The numbers saved under a name, exactly as shown in WhatsApp (case does not matter).
    fun numbersFor(ctx: Context, name: String): List<String> {
        val wanted = name.trim().lowercase(Locale.ROOT)
        if (wanted.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "LOWER(" + ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + ") = ?",
                arrayOf(wanted), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0) ?: continue
                    val k = numberKey(n)
                    if (k.length >= 10 && !out.containsKey(k)) out[k] = n
                }
            }
        } catch (e: Exception) { }
        return out.values.toList()
    }

    // Saves people whose number is not in the phone yet, as "Name VG" with company VG. Returns the names saved.
    fun saveNew(ctx: Context, people: List<Pair<String, String>>): List<String> {
        val have = keys(ctx)
        val todo = people.filter { (_, num) -> numberKey(num).length >= 10 && have.add(numberKey(num)) }
        val saved = ArrayList<String>()
        var i = 0
        while (i < todo.size) {
            val end = minOf(i + 50, todo.size)
            val ops = ArrayList<ContentProviderOperation>()
            for (k in i until end) {
                val (name, num) = todo[k]
                val at = ops.size
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, at)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build())
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, at)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, num)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, at)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, "VG").build())
            }
            try {
                ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                for (k in i until end) saved.add(todo[k].first)
            } catch (e: Exception) {
                break
            }
            i = end
        }
        return saved
    }
}

// Reads the contact file the database makes (one card per member: FN = "Name VG", TEL = +234...).
object Vcf {
    fun parse(vcf: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var name = ""
        var tel = ""
        for (raw in vcf.split("\n")) {
            val line = raw.trim()
            when {
                line.equals("BEGIN:VCARD", true) -> { name = ""; tel = "" }
                line.startsWith("FN:", true) -> name = line.substring(3).replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()
                line.startsWith("TEL", true) -> line.indexOf(':').let { if (it >= 0) tel = line.substring(it + 1).trim() }
                line.equals("END:VCARD", true) -> if (tel.isNotEmpty()) out.add((if (name.isEmpty()) tel else name) to tel)
            }
        }
        return out
    }
}

// Saves new members to the admin's phone. Runs every 15 minutes in the background when the phone is online,
// when the app opens, and from "Check now". The database decides who is new (web_admin_new_members, the same list
// as the website's New members button). Once a day it also fetches the full list, to keep MemberList up to date.
object MemberSync {
    private val running = AtomicBoolean(false)
    private const val WORK = "vgadmin-sync"
    private const val DAY = 24L * 3600L * 1000L

    class Result(val saved: List<String>, val problem: String?)

    fun schedule(ctx: Context) {
        Store.init(ctx)
        if (!Store.signedIn || !Store.syncOn) { cancel(ctx); return }
        val req = PeriodicWorkRequestBuilder<MemberSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(WORK)

    // Blocking. full = fetch every member, not only the new ones.
    fun run(ctx: Context, full: Boolean = false): Result {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.signedIn) return Result(emptyList(), "Not signed in.")
        if (!running.compareAndSet(false, true)) return Result(emptyList(), "Already checking. Please wait.")
        try {
            if (!PhoneContacts.allowed(app)) {
                Store.lastProblem = "Contacts permission is off. Turn it on so members can be saved."
                return Result(emptyList(), Store.lastProblem)
            }
            val doFull = full || MemberList.all(app).isEmpty() || System.currentTimeMillis() - Store.lastFullAt > DAY
            val args = JSONObject()
            if (doFull) args.put("p_from_id", 0)
            val r = Api.admin("web_admin_new_members", args)
            val j = r.json
            if (j == null) {
                Store.lastProblem = Api.netMessage(r.error)
                return Result(emptyList(), Store.lastProblem)
            }
            if (!j.optBoolean("ok")) {
                Store.lastProblem = if (Api.keyRefused(j)) "The admin key was refused. Sign in again." else "Could not check for new members."
                return Result(emptyList(), Store.lastProblem)
            }
            val people = Vcf.parse(j.optString("vcf"))
            val members = if (doFull) HashMap<String, String>() else HashMap(MemberList.all(app))
            people.forEach { (name, num) -> members[numberKey(num)] = name }
            MemberList.save(app, members)

            val before = PhoneContacts.keys(app)
            val saved = PhoneContacts.saveNew(app, people)
            if (doFull) {
                Store.alreadyInPhone = people.count { before.contains(numberKey(it.second)) }
                Store.lastFullAt = System.currentTimeMillis()
            }
            // Move the bookmark so the website's New members count matches: these people are now on the phone.
            if (j.optInt("count") > 0) {
                Api.admin("web_admin_new_members_done", JSONObject().put("p_upto_id", j.optLong("upto_id")))
            }
            Store.lastCheckAt = System.currentTimeMillis()
            Store.lastProblem = ""
            Store.addSavedToday(saved.size)
            Store.addRecent(saved)
            if (saved.isNotEmpty()) notifySaved(app, saved)
            return Result(saved, null)
        } finally {
            running.set(false)
        }
    }

    // The line on the admin page's Today tab (HTML, short).
    fun lineHtml(ctx: Context): String {
        Store.init(ctx)
        val n = MemberList.all(ctx).size
        if (!Store.syncOn) return "<b>Contacts sync off.</b> Tap to turn it on."
        if (Store.lastProblem.isNotEmpty()) return "<b>Contacts sync needs you.</b> " + esc(Store.lastProblem)
        val checked = if (Store.lastCheckAt == 0L) "not checked yet" else "checked " + ago(Store.lastCheckAt)
        return "<b>Contacts sync on.</b> $n members on your phone · ${Store.savedToday()} new today · $checked"
    }

    fun ago(at: Long): String {
        val m = (System.currentTimeMillis() - at) / 60000L
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 24 * 60 -> "${m / 60} h ago"
            else -> "${m / (24 * 60)} days ago"
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun notifySaved(app: Context, names: List<String>) {
        if (!Store.notifyOn) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("members", "New members saved", NotificationManager.IMPORTANCE_LOW)
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val title = if (names.size == 1) "1 new member saved" else "${names.size} new members saved"
        val text = when {
            names.size == 1 -> names[0] + " is now in your phone."
            names.size == 2 -> names[0] + " and " + names[1] + " are now in your phone."
            else -> names[0] + ", " + names[1] + " and ${names.size - 2} more are now in your phone."
        }
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, "members")
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(app, R.color.green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(app).notify(2001, n) } catch (e: SecurityException) { }
    }
}

class MemberSyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        Store.init(applicationContext)
        if (!Store.signedIn || !Store.syncOn) return ListenableWorker.Result.success()
        MemberSync.run(applicationContext)
        return ListenableWorker.Result.success()
    }
}
