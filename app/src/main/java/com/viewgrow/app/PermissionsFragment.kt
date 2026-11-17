package com.viewgrow.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
// Saying no never blocks anyone: the app always moves on to the next step.
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
                text = "ViewGrow needs this to save your new contacts, and the admin\u2019s number, to your phone. Next, you will see exactly what it does with your contacts."
                button = "Allow Contacts"
            }
            PermStep.NOTIFICATIONS -> {
                v.findViewById<TextView>(R.id.step_counter).text = "STEP 2 OF 2"
                icon = R.drawable.ob_bell
                title = "Stay notified"
                text = "Get a message when new contacts are saved and when the admin’s status is up."
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
        val btn = v.findViewById<MaterialButton>(R.id.btn_action)
        btn.text = button
        btn.setOnClickListener { ask() }
    }

    private fun ask() {
        val ctx = context ?: return
        when (step) {
            PermStep.CONTACTS ->
                // Prominent disclosure first; Android's own box only appears after Agree.
                ContactsDisclosure.show(ctx, onDecline = { advance(PermStep.CONTACTS) }) {
                    contactsAsk.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                }
            PermStep.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) notificationsAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                else advance(PermStep.NOTIFICATIONS)
            }
            PermStep.DONE -> advance(PermStep.NOTIFICATIONS)
        }
    }
}
