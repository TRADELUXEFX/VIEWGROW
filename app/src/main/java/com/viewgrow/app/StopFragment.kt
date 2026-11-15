package com.viewgrow.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

// The two full-screen "stop" pages, one layout (fragment_stop.xml):
//   BANNED  red. Account, reason, what it means, message the admin to appeal. Saved contacts come off
//           the phone. Back leaves the app. Every time it comes into view it asks the database again, and
//           a lifted ban takes the member back into the app by itself.
//   BLOCKED amber. The password is locked. Number, what happened, how to get a new one, message the
//           admin, or go back to Log in. Nothing is deleted.
class StopFragment : Fragment(R.layout.fragment_stop) {

    companion object {
        private const val KIND = "kind"
        private const val PHONE = "phone"
        const val BANNED = "banned"
        const val BLOCKED = "blocked"

        fun banned() = StopFragment().apply { arguments = Bundle().apply { putString(KIND, BANNED) } }

        fun blocked(phone: String) = StopFragment().apply {
            arguments = Bundle().apply { putString(KIND, BLOCKED); putString(PHONE, phone) }
        }
    }

    val isBanned: Boolean get() = arguments?.getString(KIND) != BLOCKED

    private val number: String
        get() = if (isBanned) Store.phone else (arguments?.getString(PHONE) ?: Store.phone)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (isBanned) buildBanned(view) else buildBlocked(view)
        Fonts.apply(view)
        applyAdmin(view)
        Remote.load { if (isAdded && view.parent != null) applyAdmin(view) }
        view.findViewById<MaterialButton>(R.id.btn_admin).setOnClickListener { openAdmin() }
        view.findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            (activity as? MainActivity)?.showLogIn()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isBanned) {
            removeContacts()
            recheck()
        }
    }

    // ---- Banned ----

    private fun buildBanned(v: View) {
        v.findViewById<View>(R.id.hero).setBackgroundResource(R.drawable.bg_hero_red)
        v.findViewById<TextView>(R.id.stop_lead).text = "Your account has been"
        v.findViewById<TextView>(R.id.stop_title).text = "Banned"
        v.findViewById<ImageView>(R.id.stop_illus).setImageResource(R.drawable.ill_banned)

        showNumber(v)

        v.findViewById<TextView>(R.id.label2).text = "REASON"
        showReason(v)

        v.findViewById<TextView>(R.id.label3).text = "WHAT THIS MEANS"
        val rows = v.findViewById<LinearLayout>(R.id.rows)
        addRow(rows, "\u2715", true, "No new contacts", "You can\u2019t get contacts while banned.")
        addRow(rows, "\u2715", true, "Saved contacts removed", "ViewGrow contacts leave your phone.")

        v.findViewById<TextView>(R.id.stop_foot).text =
            "A new account won\u2019t get around this."
        v.findViewById<TextView>(R.id.stop_caption).text = "Message the admin if you think this is wrong."
    }

    // The number sits in the header, in a small glass pill. Hidden when the phone has no number saved.
    private fun showNumber(v: View) {
        val pill = v.findViewById<TextView>(R.id.stop_number)
        val shown = Phone.show(number)
        pill.text = shown
        pill.visibility = if (shown.isBlank()) View.GONE else View.VISIBLE
    }

    private fun showReason(v: View) {
        val t = v.findViewById<TextView>(R.id.value2)
        t.text = Ban.reasonText(Store.banReason)
        t.setTextColor(ContextCompat.getColor(v.context, R.color.red_dark))
    }

    // Takes the saved contacts off the phone. Runs every time the screen shows, so a phone that
    // was offline, or had Contacts switched off, is cleaned as soon as it can be.
    private fun removeContacts() {
        val app = context?.applicationContext ?: return
        Thread { Ban.removeContacts(app) }.start()
    }

    // Asks the database again. Only a definite "ok" lifts the ban and goes back into the app.
    // Banned refreshes the reason. Anything else, and no answer, keeps the screen as it is.
    private fun recheck() {
        if (Store.phone.isEmpty() || Store.password.isEmpty()) return
        val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
        Api.call("web_status", args) { r ->
            val v = view
            if (!isAdded || v == null) return@call
            val j = r.json ?: return@call
            val main = activity as? MainActivity ?: return@call
            if (j.optBoolean("ok")) {
                Store.clearBan()
                val name = j.optString("name")
                if (name.isNotEmpty()) Store.name = name
                main.onLoggedIn()
                return@call
            }
            when (j.optString("code")) {
                "BANNED" -> {
                    val reason = j.optString("reason")
                    if (reason.isNotBlank()) { Store.banReason = reason; showReason(v) }
                }
                // Any other answer (wrong password, not found, locked, no connection) proves nothing.
                // Only ok = true lifts a ban, so the screen stays.
            }
        }
    }

    // ---- Blocked ----

    private fun buildBlocked(v: View) {
        v.findViewById<View>(R.id.hero).setBackgroundResource(R.drawable.bg_hero_amber)
        v.findViewById<TextView>(R.id.stop_lead).text = "Too many wrong passwords"
        v.findViewById<TextView>(R.id.stop_title).text = "Log in blocked"
        v.findViewById<ImageView>(R.id.stop_illus).setImageResource(R.drawable.ill_blocked)

        showNumber(v)

        v.findViewById<TextView>(R.id.label2).text = "WHAT HAPPENED"
        val what = v.findViewById<TextView>(R.id.value2)
        what.text = "Wrong password too many times"
        what.textSize = 15f
        what.setTextColor(ContextCompat.getColor(v.context, R.color.amber_text))

        v.findViewById<TextView>(R.id.label3).text = "HOW TO FIX IT"
        val rows = v.findViewById<LinearLayout>(R.id.rows)
        addRow(rows, "1", false, "Message the admin", "Send it from your registered number.")
        addRow(rows, "2", false, "Get a new password", "The admin resets it, then you log in.")

        // Short on purpose: both buttons and the card fit one screen. Nothing is deleted in this state.
        v.findViewById<View>(R.id.stop_foot).visibility = View.GONE
        v.findViewById<View>(R.id.stop_caption).visibility = View.GONE
        v.findViewById<View>(R.id.btn_back).visibility = View.VISIBLE
    }

    // ---- Shared ----

    private fun addRow(into: LinearLayout, glyph: String, red: Boolean, title: String, sub: String) {
        val row = LayoutInflater.from(into.context).inflate(R.layout.item_stop_row, into, false)
        val g = row.findViewById<TextView>(R.id.row_glyph)
        g.text = glyph
        g.setBackgroundResource(if (red) R.drawable.bg_stop_glyph_red else R.drawable.bg_stop_glyph_amber)
        g.setTextColor(ContextCompat.getColor(into.context, if (red) R.color.red_dark else R.color.amber_text))
        row.findViewById<TextView>(R.id.row_title).text = title
        row.findViewById<TextView>(R.id.row_sub).text = sub
        into.addView(row)
    }

    private fun message(): String {
        val shown = Phone.show(number)
        return if (isBanned) {
            if (shown.isNotBlank()) "Hi, my ViewGrow account ($shown) has been banned. I would like to appeal this."
            else "Hi, my ViewGrow account has been banned. I would like to appeal this."
        } else {
            "Hi, my ViewGrow password is locked after too many wrong tries. Please reset it. My number is $shown"
        }
    }

    // The admin button shows only when the admin set a contact on the website.
    private fun applyAdmin(v: View) {
        val has = Remote.adminLink("x") != null
        v.findViewById<View>(R.id.btn_admin).visibility = if (has) View.VISIBLE else View.GONE
        if (isBanned) v.findViewById<View>(R.id.stop_caption).visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun openAdmin() {
        val link = Remote.adminLink(message()) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (e: ActivityNotFoundException) {
            view?.let { Ui.toast(it, "Could not open the link.") }
        }
    }
}
