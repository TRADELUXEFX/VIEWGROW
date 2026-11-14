package com.viewgrow.app

import android.net.Uri

// The settings the admin controls on the website: whether sign-up is open, and how to reach the admin.
class AdminContact(val channel: String, val value: String, val label: String)

object Remote {
    var signupOpen = true
        private set
    var contacts: List<AdminContact> = emptyList()
        private set

    // Fetches the settings (web_public_config), then calls done on the main thread.
    // If the fetch fails the previous values stay, and done is still called.
    fun load(done: () -> Unit) {
        Api.call("web_public_config") { r ->
            val j = r.json
            if (j != null && j.optBoolean("ok")) {
                signupOpen = j.optBoolean("signup_open", true)
                val arr = j.optJSONArray("contacts")
                val list = ArrayList<AdminContact>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        list.add(AdminContact(o.optString("channel"), o.optString("value"), o.optString("label")))
                    }
                }
                contacts = list
            }
            done()
        }
    }

    // A link that opens a chat with the admin, with the message filled in. Null when the admin set no contact.
    // WhatsApp contacts become wa.me links; other options are used only if they are https links.
    fun adminLink(text: String): String? {
        for (c in contacts) {
            if (c.channel == "whatsapp_dm") {
                val digits = c.value.filter { it.isDigit() }
                if (digits.isNotEmpty()) return "https://wa.me/" + digits + "?text=" + Uri.encode(text)
            } else if (c.value.startsWith("https://", ignoreCase = true)) {
                return c.value
            }
        }
        return null
    }
}
