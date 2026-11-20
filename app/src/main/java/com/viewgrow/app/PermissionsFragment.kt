package com.viewgrow.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

private enum class PermStep { CONTACTS, NOTIFICATIONS, DONE }

// Shown right after Sign up or Log in, before Home. One thing per screen, in this order:
//   Step 1: Contacts (so ViewGrow can save your contacts)
//   Step 2: Notifications (Android 13 and up only)
// A step that is already allowed is skipped, and when nothing is left the app goes straight to Home.
// Saying no never blocks anyone: "Don't allow" in Android's own box is the way to say no, and the app always moves on
// to the next step. (There is no "Not now" button: it only took space.)
class PermissionsFragment : Fragment(R.layout.fragment_permissions) {

    companion object {
        private fun notificationsAsked(ctx: Context): Boolean =
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        private fun needed(ctx: Context, s: PermStep): Boolean = when (s) {
            PermStep.CONTACTS -> !ContactsSaver.hasPermission(ctx)
            PermStep.NOTIFICATIONS -> notificationsAsked(ctx)
            PermStep.DONE -> true
        }

        // True when at least one of the three steps still has something to ask.
        fun anythingToAsk(ctx: Context): Boolean =
            PermStep.values().any { it != PermStep.DONE && needed(ctx, it) }
    }

    private var step = PermStep.CONTACTS

    // Whatever the answer (Allow or Deny), the app moves on.
    private val contactsAsk = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Allowed: save the admin's number now, so the member can see the admin's status straight away.
        context?.let { AdminContactSaver.save(it) }
        advance(PermStep.CONTACTS)
    }
    private val notificationsAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        advance(PermStep.NOTIFICATIONS)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        val saved = savedInstanceState?.getInt("step", -1) ?: -1
        if (saved >= 0) {
            step = PermStep.values()[saved]
            show()
        } else {
            advance(null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step.ordinal)
    }

    // Moves to the first step after [after] that still needs asking (the first step overall when
    // [after] is null). When none is left, opens Home.
    private fun advance(after: PermStep?) {
        val ctx = context ?: return
        val from = if (after == null) 0 else after.ordinal + 1
        step = PermStep.values().drop(from).firstOrNull { needed(ctx, it) } ?: PermStep.DONE
        if (!isAdded) return
        if (step == PermStep.DONE) {
            // Posted, so Home opens after any screen change that is still running.
            Handler(Looper.getMainLooper()).post { if (isAdded) (activity as? MainActivity)?.showTab(0) }
        } else if (view != null) {
            show()
        }
    }

    private fun show() {
        val v = view ?: return
        val ctx = v.context
        val icon: Int
        val title: String
        val text: String
        val button: String
        when (step) {
            PermStep.CONTACTS -> {
                v.findViewById<TextView>(R.id.step_counter).text = "STEP 1 OF 2"
                icon = R.drawable.ob_contacts
                title = "Save your contacts"
                text = ""
                button = "Agree and allow"
            }
            PermStep.NOTIFICATIONS -> {
                v.findViewById<TextView>(R.id.step_counter).text = "STEP 2 OF 2"
                icon = R.drawable.ob_bell
                title = "Stay notified"
                // Laid out like step 1. The rows cover every kind of notification the app sends: contacts saved / morning
                // update (Sync, Nightly), status is up and announcements (StatusPush), contact limit (Alerts), reshare
                // reminder, no-sync and permission reminders (ReshareReminder, Reminder, PermissionWatch), weekly summary (Alerts).
                text = ""
                button = "Allow Notifications"
            }
            else -> return
        }
        // Step dashes: the first N are filled on step N.
        val n = if (step == PermStep.CONTACTS) 1 else 2
        v.findViewById<View>(R.id.dash_3).visibility = View.GONE
        listOf(R.id.dash_1, R.id.dash_2, R.id.dash_3).forEachIndexed { i, id ->
            v.findViewById<View>(id).setBackgroundResource(if (i < n) R.drawable.bg_dash_on else R.drawable.bg_dash_off)
        }
        v.findViewById<TextView>(R.id.step_title).text = title
        v.findViewById<TextView>(R.id.step_text).text = text
        val img = v.findViewById<ImageView>(R.id.step_icon)
        // A full-colour picture (green circle drawn in the picture), so no tint, background or padding.
        img.setImageResource(icon)
        ImageViewCompat.setImageTintList(img, null)
        img.background = null
        img.setPadding(0, 0, 0, 0)
        // Both steps: "Why we ask", three short rows and one green line. Step 1 is Google Play's prominent disclosure for
        // Contacts (shown right before Android's own Allow box, and the button is the agreement). Step 2 says what the
        // notifications are for, in the same layout.
        val contacts = step == PermStep.CONTACTS
        v.findViewById<View>(R.id.step_details).visibility = View.VISIBLE
        fun bold(label: String, rest: String) = SpannableStringBuilder(label).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(rest)
        }
        val note = v.findViewById<TextView>(R.id.step_privacy)
        val noteIcon = (note.parent as? android.view.ViewGroup)?.getChildAt(0) as? ImageView
        if (contacts) {
            note.text = ContactsDisclosure.STEP_PRIVACY
            noteIcon?.setImageResource(R.drawable.ic_lock)
            v.findViewById<TextView>(R.id.step_text).text = bold(ContactsDisclosure.STEP_WHY_LABEL, ContactsDisclosure.STEP_WHY)
            v.findViewById<TextView>(R.id.perm_row_1).text = SpannableStringBuilder(ContactsDisclosure.STEP_ROW_1).apply {
                val i = ContactsDisclosure.STEP_ROW_1.lastIndexOf("VG")
                setSpan(StyleSpan(Typeface.BOLD), i, i + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            v.findViewById<TextView>(R.id.perm_row_2).text = ContactsDisclosure.STEP_ROW_2
            v.findViewById<TextView>(R.id.perm_row_3).text = ContactsDisclosure.STEP_ROW_3
        } else {
            note.text = "You can turn notifications off any time in your phone\u2019s Settings."
            noteIcon?.setImageResource(R.drawable.ic_bell_small)
            v.findViewById<TextView>(R.id.step_text).text =
                bold("Why we ask:", " so you know when something happens, without having to open the app.")
            v.findViewById<TextView>(R.id.perm_row_1).text = "New contacts saved to your phone"
            v.findViewById<TextView>(R.id.perm_row_2).text = "The admin\u2019s status is up, and announcements"
            v.findViewById<TextView>(R.id.perm_row_3).text = "Your contact limit, a weekly summary and reminders"
        }
        val btn = v.findViewById<MaterialButton>(R.id.btn_action)
        btn.text = button
        btn.setOnClickListener { ask() }
        Fonts.apply(v)
        fit(v)
    }

    // The whole step shows on one screen, with nothing to scroll: if it is taller than the phone's screen, it is made
    // compact (smaller icon, less space, slightly smaller text); if it still does not fit, the icon is left out.
    // Only a very small screen still scrolls. Checked again after each change, once the screen has been laid out.
    private fun fit(v: View) {
        val scroll = v.findViewById<View>(R.id.step_scroll) ?: return
        val content = v.findViewById<View>(R.id.step_content) ?: return
        var level = 0
        applyLevel(v, level)
        // After each layout pass: still taller than the space? Go one level more compact (at most level 2), then stop.
        val obs = scroll.viewTreeObserver
        val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (!isAdded || view !== v || scroll.height == 0) return
                if (content.height > scroll.height && level < 2) {
                    level += 1
                    applyLevel(v, level)        // triggers another layout pass, checked here again
                } else {
                    if (scroll.viewTreeObserver.isAlive) scroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        }
        if (obs.isAlive) obs.addOnGlobalLayoutListener(listener)
    }

    // 0 = the full design, 1 = compact, 2 = tighter still and without the icon (for the smallest phones).
    private fun applyLevel(v: View, level: Int) {
        val ctx = v.context
        fun dp(x: Int) = Ui.dp(ctx, x)
        fun <T> pick(full: T, compact: T, tight: T): T = when (level) { 0 -> full; 1 -> compact; else -> tight }
        fun margins(target: View?, top: Int? = null, bottom: Int? = null) {
            val view = target ?: return
            val lp = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
            if (top != null) lp.topMargin = top
            if (bottom != null) lp.bottomMargin = bottom
            view.layoutParams = lp
        }
        v.findViewById<View>(R.id.hero)?.let { it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, dp(pick(36, 22, 16))) }
        v.findViewById<TextView>(R.id.step_title)?.let { it.textSize = pick(26f, 23f, 22f); margins(it, top = dp(pick(8, 8, 4))) }
        margins((v.findViewById<View>(R.id.dash_1)?.parent as? View), top = dp(pick(18, 18, 12)))
        v.findViewById<View>(R.id.step_content)?.let {
            val h = dp(pick(20, 14, 14)); val t = dp(pick(20, 14, 10)); it.setPadding(h, t, h, t)
        }
        v.findViewById<View>(R.id.step_card)?.let {
            val h = dp(pick(22, 16, 14)); val t = dp(pick(22, 16, 12)); it.setPadding(h, t, h, t)
        }
        v.findViewById<ImageView>(R.id.step_icon)?.let { img ->
            img.visibility = if (level >= 2) View.GONE else View.VISIBLE
            val size = dp(pick(88, 60, 60))
            img.layoutParams = img.layoutParams.also { it.width = size; it.height = size }
        }
        v.findViewById<TextView>(R.id.step_text)?.let { t ->
            t.textSize = pick(14.5f, 13.5f, 13f)
            t.setLineSpacing(dp(pick(4, 2, 1)).toFloat(), 1f)
            margins(t, top = dp(pick(22, 12, 0)))
        }
        margins(v.findViewById(R.id.btn_action), bottom = dp(pick(24, 18, 14)))
        val details = v.findViewById<android.view.ViewGroup>(R.id.step_details) ?: return
        margins(details, top = dp(pick(10, 6, 6)))
        for (i in 0 until details.childCount) {
            val row = details.getChildAt(i)
            val last = i == details.childCount - 1                 // the green privacy line
            val vert = dp(pick(12, 9, 7))
            val side = if (last) dp(pick(12, 12, 12)) else dp(pick(14, 14, 12))
            row.setPadding(side, vert, side, vert)
            margins(row, top = dp(if (last) pick(12, 8, 6) else pick(8, 6, 5)))
            ((row as? android.view.ViewGroup)?.getChildAt(1) as? TextView)?.let {
                it.textSize = if (last) pick(12.5f, 12f, 12f) else pick(13.5f, 13f, 12.5f)
            }
        }
    }

    private fun ask() {
        val ctx = context ?: return
        when (step) {
            PermStep.CONTACTS ->
                // The disclosure is already on this screen and "Agree and allow" is the agreement.
                contactsAsk.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            PermStep.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) notificationsAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                else advance(PermStep.NOTIFICATIONS)
            }
            PermStep.DONE -> advance(PermStep.NOTIFICATIONS)
        }
    }
}
