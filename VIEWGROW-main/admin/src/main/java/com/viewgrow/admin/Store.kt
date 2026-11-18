package com.viewgrow.admin

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// What the admin app keeps on this phone. The admin key is encrypted (SecretBox).
object Store {
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("vgadmin", Context.MODE_PRIVATE)
    }

    var key: String
        get() = p.getString("key_enc", null)?.let { SecretBox.decrypt(it) } ?: ""
        set(v) {
            val e = p.edit()
            if (v.isEmpty()) e.remove("key_enc") else SecretBox.encrypt(v)?.let { e.putString("key_enc", it) }
            e.apply()
        }

    val signedIn: Boolean get() = key.isNotEmpty()

    var setupDone: Boolean
        get() = p.getBoolean("setup_done", false)
        set(v) { p.edit().putBoolean("setup_done", v).apply() }

    // Contacts sync on or off (Sync screen).
    var syncOn: Boolean
        get() = p.getBoolean("sync_on", true)
        set(v) { p.edit().putBoolean("sync_on", v).apply() }

    // How often the background check runs, in minutes: 15, 60, 720 or 1440 (Sync screen). Opening the app always checks too.
    var syncEveryMin: Int
        get() = p.getInt("sync_every_min", 15)
        set(v) { p.edit().putInt("sync_every_min", v).apply() }

    // A quiet notification when members are saved.
    var notifyOn: Boolean
        get() = p.getBoolean("notify_on", true)
        set(v) { p.edit().putBoolean("notify_on", v).apply() }

    var lastCheckAt: Long
        get() = p.getLong("last_check_at", 0L)
        set(v) { p.edit().putLong("last_check_at", v).apply() }

    // The last time the full member list was fetched (once a day, and on the first run).
    var lastFullAt: Long
        get() = p.getLong("last_full_at", 0L)
        set(v) { p.edit().putLong("last_full_at", v).apply() }

    // The last sync problem, shown on the Sync screen ("" = none).
    var lastProblem: String
        get() = p.getString("last_problem", "") ?: ""
        set(v) { p.edit().putString("last_problem", v).apply() }

    // Members saved to the phone today (by the phone's date).
    fun savedToday(): Int = if (p.getString("saved_day", "") == today()) p.getInt("saved_count", 0) else 0
    fun addSavedToday(n: Int) {
        if (n <= 0) return
        p.edit().putString("saved_day", today()).putInt("saved_count", savedToday() + n).apply()
    }

    // Members already in the phone under another name, from the last full check.
    var alreadyInPhone: Int
        get() = p.getInt("already_in_phone", 0)
        set(v) { p.edit().putInt("already_in_phone", v).apply() }

    // The last 50 members saved to the phone: [{name, at}] newest first.
    fun recent(): List<Pair<String, Long>> = try {
        val a = JSONArray(p.getString("recent", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { it.optString("name") to it.optLong("at") } }
    } catch (e: Exception) { emptyList() }

    fun addRecent(names: List<String>) {
        if (names.isEmpty()) return
        val now = System.currentTimeMillis()
        val all = names.map { it to now } + recent()
        val a = JSONArray()
        all.take(50).forEach { a.put(JSONObject().put("name", it.first).put("at", it.second)) }
        p.edit().putString("recent", a.toString()).apply()
    }

    // A screenshot name the admin matched by hand: name (lower case) -> number key. Asked once, remembered.
    fun rememberedPick(name: String): String? = try {
        JSONObject(p.getString("picks", "{}")).optString(name.lowercase(), "").ifEmpty { null }
    } catch (e: Exception) { null }

    fun rememberPick(name: String, numberKey: String) {
        val o = try { JSONObject(p.getString("picks", "{}")) } catch (e: Exception) { JSONObject() }
        o.put(name.lowercase(), numberKey)
        p.edit().putString("picks", o.toString()).apply()
    }

    // Sign out of this phone: forget the key and the settings. Saved contacts stay in the phone.
    fun signOut() {
        p.edit().clear().apply()
    }

    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
}
