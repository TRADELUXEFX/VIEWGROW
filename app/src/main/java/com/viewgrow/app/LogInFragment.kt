package com.viewgrow.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

// Log in with the same WhatsApp number and password as the website.
class LogInFragment : Fragment(R.layout.fragment_login) {

    private val passwordRule = Regex("^[A-Za-z0-9]{6,30}$")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val phone = view.findViewById<EditText>(R.id.phone)
        val password = view.findViewById<EditText>(R.id.password)
        val button = view.findViewById<Button>(R.id.btn_login)
        val main = activity as? MainActivity

        Phone.attach(phone)
        // The big green header shrinks while the keyboard is open, so the form has room.
        view.findViewById<android.view.ViewGroup>(R.id.hero)?.let { KeyboardAware.compactHeader(view, it) }
        if (Store.phone.isNotEmpty()) phone.setText(Phone.show(Store.phone))
        Ui.passwordEye(password, view.findViewById(R.id.eye))
        Fonts.apply(view)

        Ui.linkText(
            view.findViewById(R.id.to_signup),
            "New here? Sign up",
            listOf("Sign up" to { main?.showSignUp() })
        )

        // "Forgot your password?" opens a chat with the admin. It shows only if the admin set a contact.
        showForgot(view)
        Remote.load { if (isAdded && view.parent != null) showForgot(view) }

        button.setOnClickListener {
            submit(view, phone.text.toString().trim(), password.text.toString().trim())
        }
    }

    private fun showForgot(view: View) {
        val forgot = view.findViewById<TextView>(R.id.forgot)
        val phone = view.findViewById<EditText>(R.id.phone)
        if (!Remote.adminExpected()) { forgot.visibility = View.GONE; return }
        forgot.visibility = View.VISIBLE
        Ui.linkText(forgot, "Forgot your password? Message the admin", listOf("Message the admin" to {
            val text = "Hi, I forgot my ViewGrow password. Please reset it. My number is " + phone.text.toString().trim()
            val link = Remote.adminLink(text)
            if (link == null) {
                Ui.toast(view, "Could not open the link. Check your internet and try again.")
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                } catch (e: ActivityNotFoundException) {
                    Ui.toast(view, "Could not open the link.")
                }
            }
        }))
    }

    private fun submit(view: View, phone: String, password: String) {
        val error = view.findViewById<TextView>(R.id.error)
        val button = view.findViewById<Button>(R.id.btn_login)
        Ui.showError(error, null)

        if (phone.isEmpty()) { Ui.showError(error, "Enter your number."); return }
        if (password.length < 6) { Ui.showError(error, "Your password needs at least 6 characters."); return }
        if (!passwordRule.matches(password)) { Ui.showError(error, "Your password can only use letters and numbers."); return }

        Ui.setBusy(button, true, "Log in", "Logging in")
        // The password is sent once, here. The database answers with a login token, which the app keeps instead (Session.kt).
        Session.logIn(phone, password) { r ->
            if (!isAdded || view.parent == null) return@logIn
            Ui.setBusy(button, false, "Log in")
            val j = r.json
            if (j == null) { Ui.showError(error, Api.netMessage(r.error)); return@logIn }
            if (!j.optBoolean("ok")) {
                when (j.optString("code")) {
                    // Banned and Blocked get their own full screens. The number (and for a ban, the password)
                    // is kept so the Banned screen can ask again whether the ban is lifted.
                    "BANNED" -> {
                        Store.phone = phone
                        Store.password = password
                        Ban.mark(view.context, j.optString("reason"))
                        (activity as? MainActivity)?.showBanned()
                        return@logIn
                    }
                    "LOCKED" -> {
                        Store.phone = phone
                        (activity as? MainActivity)?.showBlocked(phone)
                        return@logIn
                    }
                }
                Ui.showError(error, Api.loginMessage(j))
                return@logIn
            }
            val token = Session.token(j)
            if (token.isEmpty()) { Ui.showError(error, "Could not log in. Please try again."); return@logIn }
            Store.logIn(phone, token, j.optString("name"))
            (activity as? MainActivity)?.onLoggedIn()
        }
    }
}
