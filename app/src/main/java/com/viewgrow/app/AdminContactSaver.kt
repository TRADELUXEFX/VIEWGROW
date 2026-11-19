package com.viewgrow.app

import android.content.Context
import java.util.concurrent.Executors

// Saves the admin's WhatsApp number to the member's phone as "ViewGrow Admin", so the member can see the admin's
// status (WhatsApp only shows a status to people who have that number saved). This removes the "save the admin's
// number" step for new members.
//
// - Only with the Contacts permission, only when logged in and not banned, only for a WhatsApp number.
// - Once per admin number (Store.adminSavedFor): if the member deletes the contact, it is not put back,
//   unless the admin's number changes.
// - Skipped if the number is already in the phone under any name (ContactsSaver.save never adds a number twice).
// - Delete My Contacts and a ban skip it by its name, like a contact the member saved.
//
// The same moments also save the admin's VIP contacts (VipContactSaver.kt): the numbers and names the admin sets in
// Settings > VIP contacts on the admin page.
object AdminContactSaver {
    private const val NAME = "ViewGrow Admin"
    private val pool = Executors.newSingleThreadExecutor()

    // From a screen: does the work in the background.
    fun save(ctx: Context) {
        val app = ctx.applicationContext
        pool.execute { saveNow(app) }
    }

    // Blocking, background thread only.
    fun saveNow(ctx: Context) {
        val app = ctx.applicationContext
        Store.init(app)
        if (!Store.loggedIn || Store.banned || !ContactsSaver.hasPermission(app)) return
        saveAdmin(app)
        VipContactSaver.saveNow(app)
    }

    private fun saveAdmin(app: Context) {
        val target = Store.adminTarget
        // Only a WhatsApp number (digits). A Telegram or other link cannot be saved as a contact.
        if (target.isEmpty() || !target.all { it.isDigit() } || target.length < 10) return
        if (Store.adminSavedFor == target) return
        if (ContactsSaver.save(app, listOf(ContactsSaver.Card(NAME, "+$target", "")))) Store.adminSavedFor = target
    }
}
