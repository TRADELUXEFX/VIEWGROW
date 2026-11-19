package com.viewgrow.app

import android.content.Context
import android.content.SharedPreferences

// What the phone remembers: who is logged in. The number, and the login token the database gave at log in
// (Session.kt), which every database call sends. Nothing leaves the phone except in those calls.
object Store {
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        p = ctx.applicationContext.getSharedPreferences("viewgrow", Context.MODE_PRIVATE)
        // A plain-text password left by a very old test version is wiped, not used: that phone logs in again.
        if (p.contains("password")) p.edit().remove("password").apply()
    }

    // The number as typed or as the database returned it. Kept after log out, to fill the Log in box.
    var phone: String
        get() = p.getString("phone", "") ?: ""
        set(v) { p.edit().putString("phone", v).apply() }

    // The login token (after a ban is lifted, briefly the password until it is traded for a token). Kept encrypted (SecretBox).
    // If the phone's Keystore cannot be used nothing is kept and the member logs in again.
    // Decrypted once and kept in memory (until it changes): the app reads it many times per screen, and decrypting
    // each time froze the screen white for seconds on some phones (the blank screen after the setup steps).
    var password: String
        get() {
            val enc = p.getString("password_enc", "") ?: ""
            synchronized(this) {
                if (enc == pwEnc) return pwPlain
            }
            val plain = if (enc.isEmpty()) "" else SecretBox.decrypt(enc)
            synchronized(this) { pwEnc = enc; pwPlain = plain }
            return plain
        }
        set(v) {
            val enc = if (v.isEmpty()) null else SecretBox.encrypt(v)
            p.edit().apply { if (enc != null) putString("password_enc", enc) else remove("password_enc") }.apply()
            synchronized(this) { pwEnc = enc ?: ""; pwPlain = if (enc != null) v else "" }
        }
    private var pwEnc: String? = null
    private var pwPlain = ""

    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) { p.edit().putString("name", v).apply() }

    // Background timer settings (changed in Profile): how often to check, and the pause switch.
    var syncHours: Int
        get() = p.getInt("sync_hours", 24)
        set(v) { p.edit().putInt("sync_hours", v).apply() }

    var syncPaused: Boolean
        get() = p.getBoolean("sync_paused", false)
        set(v) {
            val e = p.edit().putBoolean("sync_paused", v)
            // Coming back from a pause starts the 3-day reminder clock again.
            if (!v && p.getLong("last_sync_at", 0L) != 0L) e.putLong("last_bg_sync_at", System.currentTimeMillis())
            e.apply()
        }

    // The last time contacts were saved to this phone, and how many.
    var lastSyncAt: Long
        get() = p.getLong("last_sync_at", 0L)
        set(v) { p.edit().putLong("last_sync_at", v).apply() }

    // The last time the app finished checking or saving this phone's contacts (any successful sync). Home shows it as "Last sync".
    var lastCheckAt: Long
        get() = p.getLong("last_check_at", 0L)
        set(v) { p.edit().putLong("last_check_at", v).apply() }

    // The contacts total and limit Home last showed, so the FAQ page can open Increase my contact limit with the right numbers.
    var lastTotal: Int
        get() = p.getInt("last_total", 0)
        set(v) { p.edit().putInt("last_total", v).apply() }

    var lastLimit: Int
        get() = p.getInt("last_limit", 0)
        set(v) { p.edit().putInt("last_limit", v).apply() }

    var lastSyncCount: Int
        get() = p.getInt("last_sync_count", 0)
        set(v) { p.edit().putInt("last_sync_count", v).apply() }

    // Contacts really put in the phone since the database last confirmed a save. A save whose confirmation failed
    // is counted with the next one, so the "contacts saved" number is what the phone actually received.
    var unannouncedSaved: Int
        get() = p.getInt("unannounced_saved", 0)
        set(v) { p.edit().putInt("unannounced_saved", v).apply() }

    // The last time an AUTOMATIC sync finished cleanly. The 3-day reminder (Reminder.kt) uses it with lastSyncAt.
    var lastBgSyncAt: Long
        get() = p.getLong("last_bg_sync_at", 0L)
        set(v) { p.edit().putLong("last_bg_sync_at", v).apply() }

    // Banned by the admin. Kept on the phone (also after the app is closed) so the Banned screen shows at
    // once, and so syncing stays off. Only a "not banned" answer from the database lifts it (Ban.kt).
    var banned: Boolean
        get() = p.getBoolean("banned", false)
        set(v) { p.edit().putBoolean("banned", v).apply() }

    var banReason: String
        get() = p.getString("ban_reason", "") ?: ""
        set(v) { p.edit().putString("ban_reason", v).apply() }

    fun clearBan() {
        p.edit().remove("banned").remove("ban_reason").apply()
    }

    // What the "permission is off" notification last warned about (for example "Contacts"),
    // empty when nothing is off. The same list is not warned twice; a new item turning off warns again.
    var permAlerted: String
        get() = p.getString("perm_alerted", "") ?: ""
        set(v) { p.edit().putString("perm_alerted", v).apply() }

    // The admin's contact from the website's settings (WhatsApp digits or an https link), kept so the
    // "Reshare" notification can open the chat even when the app was closed and has not loaded it yet.
    var adminTarget: String
        get() = p.getString("admin_target", "") ?: ""
        set(v) { p.edit().putString("admin_target", v).apply() }

    // Whether the admin has sign-up open, kept from the last time the settings loaded, so the Sign up screen
    // can show the right thing the moment it opens (no notice popping in late).
    var signupOpen: Boolean
        get() = p.getBoolean("signup_open", true)
        set(v) { p.edit().putBoolean("signup_open", v).apply() }

    // The contact limit the "80% used" notification was last shown for (0 = not shown, or the limit went up since).
    var limitAlerted: Int
        get() = p.getInt("limit_alerted", 0)
        set(v) { p.edit().putInt("limit_alerted", v).apply() }

    // Contacts saved during the night (9pm to 7am on the phone's clock). They are not announced at night; the
    // 8am "added last night" message (Nightly.kt) shows the total, then sets this back to 0.
    var morningCount: Int
        get() = p.getInt("morning_count", 0)
        set(v) { p.edit().putInt("morning_count", v).apply() }

    // The release night the "people joined last night" message was last shown for, so it shows once per night.
    var missedNoticeFor: String
        get() = p.getString("missed_notice_for", "") ?: ""
        set(v) { p.edit().putString("missed_notice_for", v).apply() }

    // The admin's WhatsApp number (digits) the app last saved to the phone as "ViewGrow Admin". Saved once per
    // number, so if the member deletes that contact it is not put back (unless the admin's number changes).
    var adminSavedFor: String
        get() = p.getString("admin_saved_for", "") ?: ""
        set(v) { p.edit().putString("admin_saved_for", v).apply() }

    // The VIP numbers (digits, with country code) the app already put on this phone (VipContactSaver.kt). A VIP is
    // saved once per number, so if the member deletes it, it is not put back. Kept after log out, like adminSavedFor.
    var vipSaved: Set<String>
        get() = HashSet(p.getStringSet("vip_saved", emptySet()) ?: emptySet())
        set(v) { p.edit().putStringSet("vip_saved", HashSet(v)).apply() }

    // The last time the VIP list was fetched and saved cleanly (ms), so screens do not ask the database every time.
    var vipCheckedAt: Long
        get() = p.getLong("vip_checked_at", 0L)
        set(v) { p.edit().putLong("vip_checked_at", v).apply() }

    // The last time "Update ViewGrow" was shown (UpdateCheck.kt), so it asks at most once a day.
    var updateAskedAt: Long
        get() = p.getLong("update_asked_at", 0L)
        set(v) { p.edit().putLong("update_asked_at", v).apply() }

    // Google Play's rating pop-up was asked for on this phone (Rating.kt). Kept on log out, so it is asked only once.
    var reviewAsked: Boolean
        get() = p.getBoolean("review_asked", false)
        set(v) { p.edit().putBoolean("review_asked", v).apply() }

    // The member's id in the database, from web_app_seen. Used for reminders meant for this member only
    // (Firebase topic "u<id>"). 0 = not known yet.
    var userId: Long
        get() = p.getLong("user_id", 0L)
        set(v) { p.edit().putLong("user_id", v).apply() }

    // The last time the phone joined the Firebase topics (StatusPush.kt). Joining lasts, so it is repeated only once a week.
    var topicsJoinedAt: Long
        get() = p.getLong("topics_joined_at", 0L)
        set(v) { p.edit().putLong("topics_joined_at", v).apply() }

    // The last time the app told the database "this member uses the app" (AppSeen in StatusPush.kt).
    var appSeenAt: Long
        get() = p.getLong("app_seen_at", 0L)
        set(v) { p.edit().putLong("app_seen_at", v).apply() }

    val loggedIn: Boolean get() = phone.isNotEmpty() && password.isNotEmpty()

    fun logIn(phone: String, password: String, name: String) {
        // A different member on this phone starts with the normal sync settings, not the last member's pause or interval.
        val before = this.phone.filter { it.isDigit() }.takeLast(10)
        val now = phone.filter { it.isDigit() }.takeLast(10)
        val e = p.edit().putString("phone", phone).putString("name", name)
        if (before.isNotEmpty() && before != now) e.remove("sync_paused").remove("sync_hours")
        e.apply()
        this.password = password
    }

    // Account deleted: forget everything the app kept, including the number.
    fun clearAll() {
        p.edit().clear().apply()
    }

    // Forgets the password and name. The number stays so the Log in box is filled next time.
    fun logOut() {
        p.edit().remove("password").remove("password_enc").remove("name").remove("last_sync_at").remove("last_sync_count").remove("unannounced_saved")
            .remove("first_sync_at") /* left by older versions */.remove("last_bg_sync_at").remove("perm_alerted")
            .remove("topics_joined_at").remove("last_total").remove("last_limit").remove("limit_alerted")
            .remove("morning_count").remove("missed_notice_for").remove("app_seen_at").apply()
    }
}
