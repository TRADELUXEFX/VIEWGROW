package com.viewgrow.app

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.security.MessageDigest

// Device code for the device ban (database: migration 20261121020000_device_ban.sql).
// The phone's Android ID is mixed with "viewgrow" and turned into a SHA-256; only "a:" + the first 32 letters of that
// leave the phone. The Android ID itself is never sent and cannot be worked back from the code. Used only to stop a
// banned member from signing up again on the same phone (fraud prevention). Not used for ads or tracking.
// The Android ID stays the same when the app is uninstalled and installed again; it changes after a factory reset.
object Device {
    private lateinit var app: Context
    private var cached: String? = null

    fun init(ctx: Context) { app = ctx.applicationContext }

    // "a:" + 32 hex letters, or "" if the phone gives no usable Android ID.
    @SuppressLint("HardwareIds")
    fun code(): String {
        cached?.let { return it }
        val id = try {
            if (::app.isInitialized) Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID).orEmpty() else ""
        } catch (e: Exception) { "" }
        // 9774d56d682e549c: the same value on many old phones, useless as an ID.
        val c = if (id.isBlank() || id == "9774d56d682e549c") "" else {
            val h = MessageDigest.getInstance("SHA-256").digest(("viewgrow:" + id).toByteArray(Charsets.UTF_8))
            "a:" + h.joinToString("") { "%02x".format(it) }.take(32)
        }
        cached = c
        return c
    }

    // What web_login gets as p_device: the code, or "android" as before when there is none.
    fun forLogin(): String = code().ifEmpty { "android" }

    // Opening the app while logged in (also while banned): tells the database which phone this member uses,
    // at most once a day. Members who logged in before this version get their phone recorded this way.
    fun report() {
        if (!Store.loggedIn || !Session.isToken(Store.password)) return
        val c = code()
        if (c.isEmpty()) return
        if (System.currentTimeMillis() - Store.deviceSeenAt < 24L * 3600 * 1000) return
        val phone = Store.phone
        val args = JSONObject().put("p_phone", phone).put("p_pin", Store.password).put("p_device", c)
        Api.call("web_device_seen", args) { r ->
            if (r.json?.optBoolean("ok") == true && Store.phone == phone) Store.deviceSeenAt = System.currentTimeMillis()
        }
    }
}
