package com.viewgrow.app

import org.json.JSONObject

// Login tokens. The password is sent once, to web_login, which answers with a login token ("vgs_..."). The app keeps
// the token (encrypted, in Store.password) and sends it in place of the password on every call. The database renews
// it while it is used and ends it after 90 days without use, at log out, or when the admin sets a new password
// (the member then gets SESSION_EXPIRED and logs in again). Database side: migration 20261117140000_login_tokens.sql.
// There is no older way to log in: a log in that gets no token is a failed log in.
object Session {
    const val PREFIX = "vgs_"

    fun isToken(s: String) = s.startsWith(PREFIX)

    // Log in with the number and password. done gets the database's web_login answer (status + token).
    fun logIn(phone: String, password: String, done: (ApiResult) -> Unit) {
        val args = JSONObject().put("p_phone", Phone.forServer(phone)).put("p_pin", password).put("p_device", Device.forLogin())
        Api.call("web_login", args, done)
    }

    // The login token in a web_login answer, or "" when there is none (the log in did not work).
    fun token(answer: JSONObject?): String {
        val t = answer?.optString("token").orEmpty()
        return if (isToken(t)) t else ""
    }

    // A member let back in after a ban has the password saved (the Banned screen needed it to ask again). Trade it for
    // a token once, in the background, and forget the password. Nothing happens if there is no connection.
    fun upgrade() {
        if (!Store.loggedIn || isToken(Store.password)) return
        val phone = Store.phone
        val password = Store.password
        val args = JSONObject().put("p_phone", phone).put("p_pin", password).put("p_device", Device.forLogin())
        Api.call("web_login", args) { r ->
            val j = r.json ?: return@call
            val t = j.optString("token")
            // Only if the same member is still logged in with the same password (not logged out meanwhile).
            if (j.optBoolean("ok") && isToken(t) && Store.phone == phone && Store.password == password) Store.password = t
        }
    }

    // Log out: also ends this phone's token on the server (other phones stay logged in). Fire and forget.
    fun endOnServer() {
        val t = Store.password
        if (!isToken(t)) return
        Api.call("web_logout", JSONObject().put("p_phone", Store.phone).put("p_token", t)) { }
    }
}
