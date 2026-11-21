package com.viewgrow.app

import android.content.Context
import android.view.View
import org.json.JSONObject

// Banned and Blocked, the two "you can't go on" states. (Idea from VG Contact.)
//
// BANNED: the admin banned this account. The database answers BANNED to web_status and
//   web_prepare_download. The phone remembers it (Store.banned), so the Banned screen shows at once, also
//   offline, and the saved contacts are taken off the phone. A definite "not banned" answer from the database
//   (the Banned screen asks every time it comes into view) lifts it again.
// BLOCKED: the password is locked after too many wrong tries (LOCKED). Nothing is deleted. The member
//   messages the admin from their registered number to get a new password.
// The app never decides either one. The database does, with the same rules as the website.
object Ban {

    // Remembers the ban and stops everything that works for the account. Quick, safe on any thread.
    fun mark(ctx: Context, reason: String?) {
        val app = ctx.applicationContext
        Store.init(app)
        Store.banned = true
        if (!reason.isNullOrBlank()) Store.banReason = reason
        SyncScheduler.cancel(app)
        ReshareReminder.cancel(app)
        Status.last = null
        Release.clear()
    }

    // A banned account keeps no ViewGrow contacts on the phone: removes every contact the app saved
    // (found by the VG company). Contacts that are not ViewGrow's are never touched.
    // Blocking: call it from a background thread. Safe to call again.
    fun removeContacts(ctx: Context) {
        val app = ctx.applicationContext
        try {
            if (ContactsSaver.hasPermission(app)) ContactsSaver.deleteMine(app)
        } catch (t: Throwable) {
            // A failed clean-up must never crash the app. It runs again next time the screen opens.
        }
    }

    // The words for the reason. The database may send a short code or its own sentence.
    fun reasonText(raw: String): String {
        val code = raw.trim()
        return when (code.lowercase()) {
            "" -> "Breaking the app\u2019s rules"
            "leaking", "leaking_contacts", "leak" -> "Leaking or sharing contacts"
            "fake_reshares", "fake_reshare", "fake_reposts" -> "Submitting fake reshares"
            "multiple_accounts" -> "Using more than one account"
            "abuse" -> "Abusing the app or the admin"
            "scam" -> "Scamming or misusing the app"
            "reported" -> "Reported by several members"
            "under18_review", "under18" -> "Reported as under 18 (being checked)"
            // The admin's own words (typed when banning, e.g. "leaked file"), first letter capital.
            else -> code.replaceFirstChar { it.uppercase() }
        }
    }
}

// What a screen does when the database answers "ok: false" with a code that stops the account.
object AccountGate {
    // Returns true when it took over: a Banned or Blocked screen is now showing, or the member was logged out
    // (wrong password, session ended, number not found, invalid number, account deleted) with the reason shown
    // on `view` when one is given. Every screen uses this one place, so logging out behaves the same everywhere.
    fun handle(main: MainActivity?, j: JSONObject, view: View? = null): Boolean {
        if (main == null) return false
        return when (j.optString("code")) {
            "ACCOUNT_DELETED" -> {
                val phone = Store.phone
                main.onLoggedOut(goWelcome = false)
                main.showDeleted(phone)
                true
            }
            "WRONG_PIN", "SESSION_EXPIRED", "NOT_FOUND", "INVALID_PHONE" -> {
                if (view != null) Ui.toast(view, Api.loginMessage(j))
                main.onLoggedOut()
                true
            }
            "BANNED" -> {
                Ban.mark(main, j.optString("reason"))
                main.showBanned()
                true
            }
            "LOCKED" -> {
                val phone = Store.phone
                main.onLoggedOut(goWelcome = false)
                main.showBlocked(phone)
                true
            }
            else -> false
        }
    }
}
