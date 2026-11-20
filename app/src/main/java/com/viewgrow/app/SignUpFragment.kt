package com.viewgrow.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

// Create an account on the ViewGrow database: same name, number and password rules as the website.
class SignUpFragment : Fragment(R.layout.fragment_signup) {

    private val passwordRule = Regex("^[A-Za-z0-9]{6,30}$")
    private var busy = false
    private lateinit var country: CountryField

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val name = view.findViewById<EditText>(R.id.name)
        val phone = view.findViewById<EditText>(R.id.phone)
        val password = view.findViewById<EditText>(R.id.password)
        val button = view.findViewById<Button>(R.id.btn_signup)
        val main = activity as? MainActivity

        country = CountryField(requireContext(), view.findViewById(R.id.country), phone)
        // The big green header shrinks while the keyboard is open, so the form has room.
        view.findViewById<android.view.ViewGroup>(R.id.hero)?.let { KeyboardAware.compactHeader(view, it) }
        Ui.passwordEye(password, view.findViewById(R.id.eye))
        // Password box: a little taller than before, with a lock icon in front (same look as the name box).
        val eye = view.findViewById<View>(R.id.eye)
        password.layoutParams = password.layoutParams.also { it.height = Ui.dp(requireContext(), 56) }
        eye.layoutParams = eye.layoutParams.also { it.height = Ui.dp(requireContext(), 56) }
        password.compoundDrawablePadding = Ui.dp(requireContext(), 12)
        password.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_lock, 0, 0, 0)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
            password, androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.green_text)
        )
        Fonts.apply(view)

        // Signing up means accepting the Terms and Privacy Policy, and agreeing that other members
        // get this number as a status viewer. The database records that as "consent".
        Ui.linkText(
            view.findViewById(R.id.terms),
            "By signing up you accept the Terms and Conditions and Privacy Policy, and agree that ViewGrow can share your name and number with other members, so they can see your status.",
            listOf(
                "Terms and Conditions" to { main?.showPage(Pages.TERMS) },
                "Privacy Policy" to { main?.showPage(Pages.PRIVACY) }
            )
        )
        Ui.linkText(
            view.findViewById(R.id.to_login),
            "Already a member? Log in",
            listOf("Log in" to { main?.showLogIn() })
        )
        // Shown instead of the form while sign-up is closed.
        view.findViewById<View>(R.id.btn_closed_login).setOnClickListener { main?.showLogIn() }

        // Is sign-up open? (the admin can close it on the website)
        Remote.load {
            if (isAdded && view.parent != null) applyOpen(view)
        }
        applyOpen(view)

        button.setOnClickListener {
            submit(view, name.text.toString().trim(), country.value(), password.text.toString().trim())
        }
    }

    private fun applyOpen(view: View) {
        // Closed: the whole form is hidden and a short closed screen (lock, message, Log in) shows instead.
        val open = Remote.signupOpen
        view.findViewById<View>(R.id.form).visibility = if (open) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.closed_box).visibility = if (open) View.GONE else View.VISIBLE
        view.findViewById<Button>(R.id.btn_signup).isEnabled = open && !busy
    }

    private fun submit(view: View, name: String, phone: String, password: String) {
        val error = view.findViewById<TextView>(R.id.error)
        val button = view.findViewById<Button>(R.id.btn_signup)
        view.findViewById<View>(R.id.sugg_box).visibility = View.GONE
        Ui.showError(error, null)

        if (name.length < 2) { Ui.showError(error, "Please enter your name."); return }
        country.problem()?.let { Ui.showError(error, it); return }
        if (phone.filter { it.isDigit() }.length < 10) {
            Ui.showError(error, "Enter a valid number. Pick your country, then type your number."); return
        }
        if (password.length < 6) { Ui.showError(error, "Your password needs at least 6 characters."); return }
        if (!passwordRule.matches(password)) { Ui.showError(error, "Your password can only use letters and numbers."); return }

        busy = true
        Ui.setBusy(button, true, "Sign up", "Signing up")
        val args = JSONObject()
            .put("p_name", name)
            .put("p_phone", phone)
            .put("p_consent", true)
            .put("p_pin", password)
        Api.call("web_register", args) { r ->
            busy = false
            if (!isAdded || view.parent == null) return@call
            Ui.setBusy(button, false, "Sign up")
            applyOpen(view)
            val j = r.json
            if (j == null) { Ui.showError(error, Api.netMessage(r.error)); return@call }
            if (j.optBoolean("ok")) {
                val num = j.optString("phone", phone)
                val nm = j.optString("name", name)
                // Account made: log in once to get a login token, so the password is not kept on the phone.
                Session.logIn(num, password) { lr ->
                    val token = Session.token(lr.json)
                    if (token.isEmpty()) {
                        // The account is made but this first log in did not work: the number is filled in on Log in.
                        Store.phone = num
                        if (isAdded) Ui.showError(error, "Your account is ready. Please tap Log in below.")
                        return@logIn
                    }
                    Store.logIn(num, token, nm)
                    if (isAdded) (activity as? MainActivity)?.onLoggedIn()
                }
                return@call
            }
            when (val code = j.optString("code")) {
                "NAME_TAKEN" -> showSuggestions(view, j)
                // The admin closed sign-up while the form was open: swap to the closed screen at once.
                "SIGNUP_CLOSED" -> { Store.signupOpen = false; applyOpen(view) }
                else -> Ui.showError(error, messageFor(code))
            }
        }
    }

    private fun messageFor(code: String): String = when (code) {
        "NAME_REQUIRED" -> "Please enter your name."
        "INVALID_PHONE" -> "Enter a valid number. Pick your country, then type your number."
        "CONSENT_REQUIRED" -> "Please accept the terms to continue."
        "CHECK_DETAILS" -> "We could not create this account. If you already have one, tap Log in below, or message the admin."
        "ACCOUNT_DELETED" -> "This number belongs to a deleted account. To use it again, message the admin to reactivate it."
        "SIGNUP_CLOSED" -> "Sign-up is closed right now. Check back later."
        "PIN_INVALID" -> "Your password needs at least 6 characters, letters and numbers only."
        "SIGNUP_LIMIT" -> "Too many sign-ups from this connection today. Please try again tomorrow."
        else -> "Could not sign up. Please try again."
    }

    // The name is taken: show the names the database suggests. Tapping one fills the name box.
    private fun showSuggestions(view: View, j: JSONObject) {
        val error = view.findViewById<TextView>(R.id.error)
        val box = view.findViewById<View>(R.id.sugg_box)
        val list = view.findViewById<LinearLayout>(R.id.sugg_list)
        val name = view.findViewById<EditText>(R.id.name)
        list.removeAllViews()
        val arr = j.optJSONArray("suggestions")
        val count = arr?.length() ?: 0
        if (arr == null || count == 0) {
            Ui.showError(error, "That name is taken. Try a different one.")
            return
        }
        HomeViews.suggestions(view.context, list, arr) { picked ->
            name.setText(picked)
            name.setSelection(name.text.length)
            box.visibility = View.GONE
        }
        Fonts.apply(list)
        box.visibility = View.VISIBLE
    }
}
