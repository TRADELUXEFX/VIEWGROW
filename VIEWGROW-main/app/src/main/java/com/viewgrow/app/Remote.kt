package com.viewgrow.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

// The settings the admin controls on the website: whether sign-up is open, and how to reach the admin.
class AdminContact(val channel: String, val value: String, val label: String)

object Remote {
    // Sign-up open or closed: the last answer is kept on the phone, so screens know it from the first moment.
    val signupOpen: Boolean get() = Store.signupOpen

    // True once the settings have loaded successfully in this run.
    var loaded = false
        private set
    var contacts: List<AdminContact> = emptyList()
        private set

    // Fetches the settings (web_public_config), then calls done on the main thread.
    // If the fetch fails the previous values stay, and done is still called.
    fun load(done: () -> Unit) {
        Api.call("web_public_config") { r ->
            val j = r.json
            if (j != null && j.optBoolean("ok")) {
                Store.signupOpen = j.optBoolean("signup_open", true)
                loaded = true
                val arr = j.optJSONArray("contacts")
                val list = ArrayList<AdminContact>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        list.add(AdminContact(o.optString("channel"), o.optString("value"), o.optString("label")))
                    }
                }
                contacts = list
                target(list)?.let { Store.adminTarget = it }
            }
            done()
        }
    }

    // The first usable contact: WhatsApp digits, or an https link.
    private fun target(list: List<AdminContact>): String? {
        for (c in list) {
            if (c.channel == "whatsapp_dm") {
                val digits = c.value.filter { it.isDigit() }
                if (digits.isNotEmpty()) return digits
            } else if (c.value.startsWith("https://", ignoreCase = true)) {
                return c.value
            }
        }
        return null
    }

    // Should a "Message the admin" button or link show? Yes when a contact is known. Also yes until the settings
    // have loaded for the first time, so the button is already there when the screen opens instead of popping in
    // a moment later. It is hidden only when we know the admin set no contact.
    fun adminExpected(): Boolean = adminLink("x") != null || !loaded

    // A link that opens a chat with the admin, with the message filled in. Null when the admin set no contact.
    // WhatsApp contacts become wa.me links; other options are used only if they are https links.
    // When the settings have not been loaded in this run, the last saved contact is used.
    fun adminLink(text: String): String? {
        val t = target(contacts) ?: Store.adminTarget.ifEmpty { null } ?: return null
        if (t.startsWith("https://", ignoreCase = true)) return t
        // No text: just opens the chat with the admin, with nothing typed in the box.
        return if (text.isEmpty()) "https://wa.me/$t" else "https://wa.me/" + t + "?text=" + Uri.encode(text)
    }

    // Opens the chat with the admin (WhatsApp or https link). False when no contact is set or it cannot open.
    fun openAdmin(ctx: Context, text: String): Boolean {
        val link = adminLink(text) ?: return false
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
