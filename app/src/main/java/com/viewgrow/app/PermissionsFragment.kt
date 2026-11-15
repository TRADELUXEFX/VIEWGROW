package com.viewgrow.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

// Set up, the same way VG Reshare does it. Shown after Sign up or Log in when something is still off:
//   Welcome: what the three steps are
//   1. Contacts: a short explanation, then the system popup
//   2. Notifications: the system popup
//   3. Battery: phone settings, set ViewGrow to Don't optimise
//   Done
// The screen shown is always the first step not done yet, and it is checked again every time the
// person comes back from the phone's settings. "Skip for now" is on every step, so nobody is stuck.
class PermissionsFragment : Fragment(R.layout.fragment_permissions) {

    private enum class Step { WELCOME, CONTACTS, NOTIFICATIONS, BATTERY, DONE }

    companion object {
        private fun contactsOn(ctx: Context) = ContactsSaver.hasPermission(ctx)
        private fun notificationsOn(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        private fun batteryOk(ctx: Context): Boolean {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(ctx.packageName)
        }

        // True when at least one step still has something to turn on.
        fun needsAny(ctx: Context): Boolean = !contactsOn(ctx) || !notificationsOn(ctx) || !batteryOk(ctx)
    }

    private var welcomeOpen = true
    private var contactsDenied = false
    private var notificationsDenied = false
    private val skipped = HashSet<Step>()

    private val contactsPopup = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        contactsDenied = !ContactsSaver.hasPermission(requireContext())
        render()
    }

    // The phone's one-tap battery popup. When it closes the step is checked again.
    private val batteryPopup = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { render() }

    private val notificationsPopup = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsDenied = !granted
        render()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        view.findViewById<MaterialButton>(R.id.btn_primary).setOnClickListener { onPrimary() }
        view.findViewById<View>(R.id.btn_skip).setOnClickListener { onSkip() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun currentStep(ctx: Context): Step = when {
        welcomeOpen -> Step.WELCOME
        !contactsOn(ctx) && Step.CONTACTS !in skipped -> Step.CONTACTS
        !notificationsOn(ctx) && Step.NOTIFICATIONS !in skipped -> Step.NOTIFICATIONS
        !batteryOk(ctx) && Step.BATTERY !in skipped -> Step.BATTERY
        else -> Step.DONE
    }

    private fun render() {
        val v = view ?: return
        val ctx = context ?: return
        val step = currentStep(ctx)

        // Progress inside the green header: done = solid white, current = half white, next = faint.
        val done = when (step) {
            Step.WELCOME, Step.CONTACTS -> 0
            Step.NOTIFICATIONS -> 1
            Step.BATTERY -> 2
            Step.DONE -> 3
        }
        v.findViewById<View>(R.id.progress).visibility = if (step == Step.WELCOME) View.GONE else View.VISIBLE
        listOf(R.id.bar_1, R.id.bar_2, R.id.bar_3).forEachIndexed { i, id ->
            v.findViewById<View>(id).alpha = when {
                i < done -> 1f
                i == done -> 0.6f
                else -> 0.25f
            }
        }

        val title = v.findViewById<TextView>(R.id.header_title)
        val desc = v.findViewById<TextView>(R.id.step_desc)
        val welcomeCard = v.findViewById<View>(R.id.welcome_card)
        val cards = v.findViewById<View>(R.id.step_cards)
        val note = v.findViewById<TextView>(R.id.step_note)
        val primary = v.findViewById<MaterialButton>(R.id.btn_primary)
        val skip = v.findViewById<View>(R.id.btn_skip)

        welcomeCard.visibility = if (step == Step.WELCOME) View.VISIBLE else View.GONE
        cards.visibility = if (step == Step.WELCOME) View.VISIBLE else View.GONE
        desc.visibility = if (step == Step.WELCOME) View.GONE else View.VISIBLE
        note.visibility = View.GONE
        skip.visibility = if (step == Step.WELCOME || step == Step.DONE) View.GONE else View.VISIBLE

        when (step) {
            Step.WELCOME -> {
                title.text = "Set up ViewGrow"
                v.findViewById<TextView>(R.id.welcome_text).text =
                    "ViewGrow saves your new contacts straight to your phone. It needs three quick permissions to do that."
                primary.text = "Get started"
            }
            Step.CONTACTS -> {
                title.text = "Contacts"
                desc.text = "Allow contacts so ViewGrow can save the contacts you receive straight to your phone."
                note.text = if (contactsDenied) {
                    "Contacts access is off. Tap the button, allow Contacts for ViewGrow in your phone's settings, then come back."
                } else {
                    "A permission popup opens. Tap Allow."
                }
                note.visibility = View.VISIBLE
                primary.text = if (contactsDenied) "Open settings" else "Allow contacts"
            }
            Step.NOTIFICATIONS -> {
                title.text = "Notifications"
                desc.text = "Get a message when new contacts have been saved to your phone."
                val viaSettings = notificationsDenied || Build.VERSION.SDK_INT < 33
                note.text = if (viaSettings) {
                    "Notifications are off. Tap the button, turn them on for ViewGrow, then come back."
                } else {
                    "A permission popup opens. Tap Allow."
                }
                note.visibility = View.VISIBLE
                primary.text = if (viaSettings) "Open settings" else "Allow notifications"
            }
            Step.BATTERY -> {
                title.text = "Battery"
                desc.text = "Let ViewGrow run in the background, so your phone does not stop the background sync."
                note.text = "A popup opens. Tap Allow. If no popup appears, your phone opens its battery list: choose All apps, find ViewGrow and set it to Don't optimise."
                note.visibility = View.VISIBLE
                primary.text = "Allow background running"
            }
            Step.DONE -> {
                title.text = "You're all set"
                desc.text = if (skipped.isEmpty()) {
                    "ViewGrow will save your contacts to this phone automatically."
                } else {
                    "You can turn on the rest any time in Profile."
                }
                primary.text = "Open Home"
            }
        }
    }

    private fun onSkip() {
        val ctx = context ?: return
        skipped.add(currentStep(ctx))
        render()
    }

    private fun onPrimary() {
        val ctx = requireContext()
        when (currentStep(ctx)) {
            Step.WELCOME -> {
                welcomeOpen = false
                render()
            }
            Step.CONTACTS -> {
                if (contactsDenied) {
                    openAppSettings()
                } else {
                    ConfirmDialog.show(
                        ctx,
                        "Contacts",
                        "ViewGrow saves the contacts you receive to your phone, tagged VGC. " +
                            "Nothing goes to any Google account.\n\n" +
                            "You can remove them any time with Delete My Contacts in Profile.",
                        "Agree and continue",
                        "Not now",
                        danger = false
                    ) { contactsPopup.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) }
                }
            }
            Step.NOTIFICATIONS -> {
                if (notificationsDenied || Build.VERSION.SDK_INT < 33) openNotificationSettings()
                else notificationsPopup.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Step.BATTERY -> openBatterySettings()
            Step.DONE -> (activity as? MainActivity)?.onPermissionsDone()
        }
    }

    // ---- The phone's own settings screens ----

    private fun openAppSettings() {
        val ctx = context ?: return
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        } catch (e: ActivityNotFoundException) {
            // No settings screen to open.
        }
    }

    private fun openNotificationSettings() {
        val ctx = context ?: return
        try {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        } catch (e: ActivityNotFoundException) {
            openAppSettings()
        }
    }

    // First the one-tap popup. Some phones do not show it, so the battery list and then the app's own
    // settings are the fallbacks.
    private fun openBatterySettings() {
        val ctx = context ?: return
        try {
            batteryPopup.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                openAppSettings()
            }
        }
    }
}
