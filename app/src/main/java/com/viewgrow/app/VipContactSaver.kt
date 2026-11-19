package com.viewgrow.app

import android.content.Context
import org.json.JSONObject

// Saves the admin's VIP contacts to the member's phone, each with the name the admin typed (Settings > VIP contacts
// on the admin page). They are real contacts for the member: no " VG" added, no company tag and no "ViewGrow-" note,
// so Delete My Contacts and a ban leave them alone.
//
// - Runs at the same moments as AdminContactSaver (Contacts allowed, Home opened, every background sync).
// - Only with the Contacts permission, only when logged in and not banned.
// - Once per number (Store.vipSaved): if the member deletes a VIP, it is not put back. A VIP the admin adds later
//   reaches phones at their next check; removing a VIP from the list does not remove it from any phone.
// - Skipped if the number is already in the phone under any name (ContactsSaver.save never adds a number twice).
// - No internet or a database error: nothing happens, it is tried again next time.
object VipContactSaver {
    private const val EVERY_MS = 15 * 60 * 1000L

    // The admin's VIP list as cards, named exactly as the admin typed. Null if the list could not be read.
    private fun fetch(): List<ContactsSaver.Card>? {
        val r = Api.request("web_vip_numbers", JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password))
        val j = r.json ?: return null
        if (!j.optBoolean("ok")) return null
        val arr = j.optJSONArray("contacts") ?: return null
        val out = ArrayList<ContactsSaver.Card>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val digits = o.optString("phone").filter { it.isDigit() }
            if (name.isEmpty() || digits.length < 10 || !seen.add(digits)) continue
            out.add(ContactsSaver.Card(name, "+$digits", "", exact = true))
        }
        return out
    }

    // Blocking, background thread only. Synchronized so two screens asking at once do not save a number twice.
    @Synchronized
    fun saveNow(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.banned || !ContactsSaver.hasPermission(app)) return
        // Asked cleanly in the last 15 minutes: nothing new to fetch yet.
        if (System.currentTimeMillis() - Store.vipCheckedAt < EVERY_MS) return
        val all = fetch() ?: return
        val done = Store.vipSaved
        val todo = all.filter { it.number.drop(1) !in done }
        if (todo.isNotEmpty()) {
            if (!ContactsSaver.save(app, todo)) return          // the phone refused: try again next time
            Store.vipSaved = done + todo.map { it.number.drop(1) }
        }
        Store.vipCheckedAt = System.currentTimeMillis()
    }

    // For "Sync now": every VIP on the list, even one saved before and deleted, so it comes back.
    // ContactsSaver skips numbers already in the phone. Blocking. Empty if the list could not be read.
    fun allCards(ctx: Context): List<ContactsSaver.Card> {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.banned) return emptyList()
        val all = fetch() ?: return emptyList()
        Store.vipSaved = Store.vipSaved + all.map { it.number.drop(1) }
        return all
    }
}
