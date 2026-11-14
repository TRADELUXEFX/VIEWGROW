package com.viewgrow.app

import android.content.Context
import android.content.SharedPreferences

// What the phone remembers: who is logged in. The website keeps the same two things (number and
// password) in the browser, because every database call needs them. Nothing leaves the phone except
// in those calls.
object Store {
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        p = ctx.applicationContext.getSharedPreferences("viewgrow", Context.MODE_PRIVATE)
    }

    // The number as typed or as the database returned it. Kept after log out, to fill the Log in box.
    var phone: String
        get() = p.getString("phone", "") ?: ""
        set(v) { p.edit().putString("phone", v).apply() }

    var password: String
        get() = p.getString("password", "") ?: ""
        set(v) { p.edit().putString("password", v).apply() }

    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) { p.edit().putString("name", v).apply() }

    // Background timer settings (changed in Profile): how often to check, and the pause switch.
    var syncHours: Int
        get() = p.getInt("sync_hours", 24)
        set(v) { p.edit().putInt("sync_hours", v).apply() }

    var syncPaused: Boolean
        get() = p.getBoolean("sync_paused", false)
        set(v) { p.edit().putBoolean("sync_paused", v).apply() }

    // The last time contacts were saved to this phone, and how many.
    var lastSyncAt: Long
        get() = p.getLong("last_sync_at", 0L)
        set(v) { p.edit().putLong("last_sync_at", v).apply() }

    var lastSyncCount: Int
        get() = p.getInt("last_sync_count", 0)
        set(v) { p.edit().putInt("last_sync_count", v).apply() }

    val loggedIn: Boolean get() = phone.isNotEmpty() && password.isNotEmpty()

    fun logIn(phone: String, password: String, name: String) {
        p.edit().putString("phone", phone).putString("password", password).putString("name", name).apply()
    }

    // Forgets the password and name. The number stays so the Log in box is filled next time.
    fun logOut() {
        p.edit().remove("password").remove("name").remove("last_sync_at").remove("last_sync_count").apply()
    }
}
