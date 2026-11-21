package com.viewgrow.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

// The four full-screen "stop" pages, one layout (fragment_stop.xml):
//   BANNED  red. Account, reason, what it means, message the admin to appeal. Saved contacts come off
//           the phone. Back leaves the app. Every time it comes into view it asks the database again, and
//           a lifted ban takes the member back into the app by itself.
//   BLOCKED amber. The password is locked. Number, what happened, how to get a new one, message the
//           admin, or go back to Log in. Nothing is deleted.
//   DEVICE  red. Sign-up refused because this phone was used by a banned account (DEVICE_BANNED, device ban,
//           20261121020000). Why, what it means, message the admin, or go back to Log in. Nothing is deleted.
//   DELETED grey. The account was deleted (by the member, or the number is on the deleted list: ACCOUNT_DELETED). What it means, message the
//           admin to come back as a new member, or back to the start. Nothing is kept on the phone.
//   EXISTS  amber. Sign-up refused because this phone already has an account (DEVICE_HAS_ACCOUNT, one account per
//           phone, 20261121030000). The attempt is flagged for the admin. Log in instead, or message the admin.
class StopFragment : Fragment(R.layout.fragment_stop) {

    companion object {
        private const val KIND = "kind"
        private const val PHONE = "phone"
        const val BANNED = "banned"
        const val BLOCKED = "blocked"
        const val DEVICE = "device"
        const val EXISTS = "exists"
        const val DELETED = "deleted"

        fun banned() = StopFragment().apply { arguments = Bundle().apply { putString(KIND, BANNED) } }

        fun deviceBanned(phone: String) = StopFragment().apply {
            arguments = Bundle().apply { putString(KIND, DEVICE); putString(PHONE, phone) }
        }

        fun accountExists(phone: String) = StopFragment().apply {
            arguments = Bundle().apply { putString(KIND, EXISTS); putString(PHONE, phone) }
        }

        fun deleted(phone: String) = StopFragment().apply {
            arguments = Bundle().apply { putString(KIND, DELETED); putString(PHONE, phone) }
        }

        fun blocked(phone: String) = StopFragment().apply {
            arguments = Bundle().apply { putString(KIND, BLOCKED); putString(PHONE, phone) }
        }
    }

    private val kind: String get() = arguments?.getString(KIND) ?: BANNED

    val isBanned: Boolean get() = kind == BANNED

    private val number: String
        get() = if (isBanned) Store.phone else (arguments?.getString(PHONE) ?: Store.phone)

    // Account deleted has its own page (design A, fragment_deleted.xml): no coloured header, so the status bar icons
    // turn dark in light mode while it shows, and go back to white when it closes.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        if (kind == DELETED) inflater.inflate(R.layout.fragment_deleted, container, false)
        else super.onCreateView(inflater, container, savedInstanceState)

    override fun onDestroyView() {
        if (kind == DELETED) activity?.window?.let { w -> WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = false }
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        when (kind) {
            BLOCKED -> buildBlocked(view)
            DEVICE -> buildDevice(view)
            EXISTS -> buildExists(view)
            DELETED -> buildDeleted(view)
            else -> buildBanned(view)
        }
        Fonts.apply(view)
        applyAdmin(view)
        Remote.load { if (isAdded && view.parent != null) applyAdmin(view) }
        view.findViewById<MaterialButton>(R.id.btn_admin).setOnClickListener { openAdmin() }
        view.findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            // A deleted number cannot log in, so its button goes back to the start screen.
            if (kind == DELETED) (activity as? MainActivity)?.showWelcome() else (activity as? MainActivity)?.showLogIn()
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
        addRow(rows, "\u2715", true, "Saved contacts removed", "The contacts ViewGrow saved are removed from your phone.")

        v.findViewById<TextView>(R.id.stop_foot).text =
            "Making a new account will not get around this."
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
        addRow(rows, "1", false, "Message the admin", "Send it from the number you signed up with.")
        addRow(rows, "2", false, "Get a new password", "The admin resets it, then you log in.")

        // Short on purpose: both buttons and the card fit one screen. Nothing is deleted in this state.
        v.findViewById<View>(R.id.stop_foot).visibility = View.GONE
        v.findViewById<View>(R.id.stop_caption).visibility = View.GONE
        v.findViewById<View>(R.id.btn_back).visibility = View.VISIBLE
    }

    // ---- Account deleted ----

    private fun buildDeleted(v: View) {
        // Words and buttons are in fragment_deleted.xml; only the number is filled in here.
        showNumber(v)
        activity?.window?.let { w -> WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = !AppTheme.isDark(v.context) }
    }

    // ---- Sign-up blocked on this phone (device ban) ----

    private fun buildDevice(v: View) {
        v.findViewById<View>(R.id.hero).setBackgroundResource(R.drawable.bg_hero_red)
        v.findViewById<TextView>(R.id.stop_lead).text = "On this phone, sign-up is"
        v.findViewById<TextView>(R.id.stop_title).text = "Blocked"
        v.findViewById<ImageView>(R.id.stop_illus).setImageResource(R.drawable.ill_phone_blocked)
        // The number typed does not matter here (it is the phone that is blocked), so no number pill.
        v.findViewById<View>(R.id.stop_number).visibility = View.GONE

        v.findViewById<TextView>(R.id.label2).text = "WHY"
        val why = v.findViewById<TextView>(R.id.value2)
        why.text = "An account on this phone was banned"
        why.setTextColor(ContextCompat.getColor(v.context, R.color.red_dark))

        v.findViewById<TextView>(R.id.label3).text = "WHAT THIS MEANS"
        val rows = v.findViewById<LinearLayout>(R.id.rows)
        addRow(rows, "\u2715", true, "No new accounts on this phone", "Signing up with another number won\u2019t work here.")
        addRow(rows, "?", false, "Think this is a mistake?", "Message the admin. They can check it and lift the block.")

        v.findViewById<View>(R.id.stop_foot).visibility = View.GONE
        v.findViewById<View>(R.id.stop_caption).visibility = View.GONE
        v.findViewById<View>(R.id.btn_back).visibility = View.VISIBLE
    }

    // ---- This phone already has an account (one account per phone) ----

    private fun buildExists(v: View) {
        v.findViewById<View>(R.id.hero).setBackgroundResource(R.drawable.bg_hero_amber)
        v.findViewById<TextView>(R.id.stop_lead).text = "One account per phone"
        v.findViewById<TextView>(R.id.stop_title).text = "Already signed up"
        v.findViewById<ImageView>(R.id.stop_illus).setImageResource(R.drawable.ill_phone_exists)
        v.findViewById<View>(R.id.stop_number).visibility = View.GONE

        v.findViewById<TextView>(R.id.label2).text = "WHAT HAPPENED"
        val what = v.findViewById<TextView>(R.id.value2)
        what.text = "This phone already has a ViewGrow account"
        what.setTextColor(ContextCompat.getColor(v.context, R.color.amber_text))

        v.findViewById<TextView>(R.id.label3).text = "WHAT TO DO"
        val rows = v.findViewById<LinearLayout>(R.id.rows)
        addRow(rows, "1", false, "Message the admin", "Tap the button below. The admin will check it and help you.")
        addRow(rows, "2", false, "Or log in", "Use the number you first signed up with on this phone.")

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
        if (kind == DELETED) {
            return if (shown.isNotBlank()) "Hi, I deleted my ViewGrow account ($shown) and I would like to come back. Please let my number back in."
            else "Hi, I deleted my ViewGrow account and I would like to come back. Please let my number back in."
        }
        if (kind == EXISTS) {
            return if (shown.isNotBlank()) "Hi, I tried to sign up on ViewGrow with $shown, but it says this phone already has an account. Please help me."
            else "Hi, I tried to sign up on ViewGrow, but it says this phone already has an account. Please help me."
        }
        if (kind == DEVICE) {
            return if (shown.isNotBlank()) "Hi, I tried to sign up on ViewGrow with $shown, but it says sign-up is blocked on this phone. Please check it."
            else "Hi, I tried to sign up on ViewGrow, but it says sign-up is blocked on this phone. Please check it."
        }
        return if (isBanned) {
            if (shown.isNotBlank()) "Hi, my ViewGrow account ($shown) has been banned. I would like to appeal this."
            else "Hi, my ViewGrow account has been banned. I would like to appeal this."
        } else {
            "Hi, my ViewGrow password is locked after too many wrong tries. Please reset it. My number is $shown"
        }
    }

    // The admin button shows only when the admin set a contact on the website.
    private fun applyAdmin(v: View) {
        val has = Remote.adminExpected()
        v.findViewById<View>(R.id.btn_admin).visibility = if (has) View.VISIBLE else View.GONE
        if (isBanned) v.findViewById<View>(R.id.stop_caption).visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun openAdmin() {
        val link = Remote.adminLink(message())
        if (link == null) {
            view?.let { Ui.toast(it, "Could not open the link. Check your internet and try again.") }
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (e: ActivityNotFoundException) {
            view?.let { Ui.toast(it, "Could not open the link.") }
        }
    }
}
