package com.viewgrow.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

private enum class PermStep { CONTACTS, NOTIFICATIONS, BATTERY, DONE }

// Shown right after Sign up or Log in, before Home. One thing per screen, in this order:
//   Step 1: Contacts (so ViewGrow can save your contacts)
//   Step 2: Notifications (Android 13 and up only)
//   Step 3: Battery (so the automatic sync is not stopped by battery saver)
// A step that is already allowed is skipped, and when nothing is left the app goes straight to Home.
// Saying no never blocks anyone: the app always moves on to the next step.
class PermissionsFragment : Fragment(R.layout.fragment_permissions) {

    companion object {
        private fun notificationsAsked(ctx: Context): Boolean =
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        private fun batteryRestricted(ctx: Context): Boolean {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !pm.isIgnoringBatteryOptimizations(ctx.packageName)
        }

        private fun needed(ctx: Context, s: PermStep): Boolean = when (s) {
            PermStep.CONTACTS -> !ContactsSaver.hasPermission(ctx)
            PermStep.NOTIFICATIONS -> notificationsAsked(ctx)
            PermStep.BATTERY -> batteryRestricted(ctx)
            PermStep.DONE -> true
        }

        // True when at least one of the three steps still has something to ask.
        fun anythingToAsk(ctx: Context): Boolean =
            PermStep.values().any { it != PermStep.DONE && needed(ctx, it) }
    }

    private var step = PermStep.CONTACTS

    // Whatever the answer (Allow or Deny), the app moves on.
    private val contactsAsk = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        advance(PermStep.CONTACTS)
    }
    private val notificationsAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        advance(PermStep.NOTIFICATIONS)
    }
    private val batteryAsk = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        advance(PermStep.BATTERY)
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
                v.findViewById<TextView>(R.id.step_counter).text = "STEP 1 OF 3"
                icon = R.drawable.ic_contacts
                title = "Save your contacts"
                text = "Allow Contacts so ViewGrow can save the contacts you receive straight to your phone."
                button = "Allow Contacts"
            }
            PermStep.NOTIFICATIONS -> {
                v.findViewById<TextView>(R.id.step_counter).text = "STEP 2 OF 3"
                icon = R.drawable.ic_bell
                title = "Stay notified"
                text = "Get a message on your phone when your new contacts are saved."
                button = "Allow Notifications"
            }
            else -> {
                v.findViewById<TextView>(R.id.step_counter).text = "STEP 3 OF 3"
                icon = R.drawable.ic_sync
                title = "Keep syncing"
                text = "Allow ViewGrow to run in the background so your contacts keep saving, even when the app is closed."
                button = "Allow Background Sync"
            }
        }
        v.findViewById<TextView>(R.id.step_title).text = title
        v.findViewById<TextView>(R.id.step_text).text = text
        val img = v.findViewById<ImageView>(R.id.step_icon)
        img.setImageResource(icon)
        ImageViewCompat.setImageTintList(img, ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.green_text)))
        val btn = v.findViewById<MaterialButton>(R.id.btn_action)
        btn.text = button
        btn.setOnClickListener { ask() }
    }

    private fun ask() {
        val ctx = context ?: return
        when (step) {
            PermStep.CONTACTS -> contactsAsk.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            PermStep.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) notificationsAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                else advance(PermStep.NOTIFICATIONS)
            }
            PermStep.BATTERY -> try {
                batteryAsk.launch(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + ctx.packageName))
                )
            } catch (e: Exception) {
                // The phone refused the pop-up. Never block the person.
                advance(PermStep.BATTERY)
            }
            PermStep.DONE -> advance(PermStep.BATTERY)
        }
    }
}
