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
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment

// Shown once, right after Sign up or Log in, before Home. One goal per screen, in this order:
//   Contacts (needed to save contacts), Notifications (to hear when contacts arrive),
//   Battery (so the phone does not stop the background sync).
// A step that is already allowed is skipped. Saying no never blocks anything: every step has
// "Not now", and a refused step is not asked again on this screen (Profile can turn it on later).
class PermissionsFragment : Fragment(R.layout.fragment_permissions) {

    private enum class Step { CONTACTS, NOTIFICATIONS, BATTERY }

    companion object {
        private fun needed(ctx: Context, step: Step): Boolean = when (step) {
            Step.CONTACTS -> !ContactsSaver.hasPermission(ctx)
            Step.NOTIFICATIONS -> !NotificationManagerCompat.from(ctx).areNotificationsEnabled()
            Step.BATTERY -> {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                !pm.isIgnoringBatteryOptimizations(ctx.packageName)
            }
        }

        // True when at least one step still has something to ask.
        fun needsAny(ctx: Context): Boolean = Step.values().any { needed(ctx, it) }
    }

    private var plan: List<Step> = emptyList()
    private val done = HashSet<Step>()
    private var current: Step? = null

    private val contactsAsk = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishStep(Step.CONTACTS) }
    private val notificationsAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finishStep(Step.NOTIFICATIONS) }
    private val settingsOpened = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        current?.let { finishStep(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = view.context
        // Fixed when the screen opens, so "Step 2 of 3" does not change while going through.
        plan = Step.values().filter { needed(ctx, it) }
        Fonts.apply(view)
        view.findViewById<View>(R.id.btn_allow).setOnClickListener { onAllow() }
        view.findViewById<View>(R.id.btn_later).setOnClickListener { current?.let { finishStep(it) } }
        showNext()
    }

    // The next step in the plan that is not done and still needed, or Home when there is none.
    private fun showNext() {
        val v = view ?: return
        val ctx = v.context
        val next = plan.firstOrNull { it !in done && needed(ctx, it) }
        if (next == null) {
            (activity as? MainActivity)?.onPermissionsDone()
            return
        }
        current = next
        val (title, text, button, icon) = when (next) {
            Step.CONTACTS -> Copy(
                "Save your contacts",
                "ViewGrow saves the contacts you receive straight to your phone. Nothing goes to any Google account.",
                "Allow Contacts", R.drawable.ic_contacts
            )
            Step.NOTIFICATIONS -> Copy(
                "Know when contacts arrive",
                "Get a message when new contacts have been saved to your phone.",
                "Allow Notifications", R.drawable.ic_chat
            )
            Step.BATTERY -> Copy(
                "Keep syncing alive",
                "Find ViewGrow in the list and set it to Not optimised, so your phone does not stop the background sync.",
                "Open battery settings", R.drawable.ic_sync
            )
        }
        v.findViewById<TextView>(R.id.step_counter).text = "STEP " + (plan.indexOf(next) + 1) + " OF " + plan.size
        v.findViewById<TextView>(R.id.step_title).text = title
        v.findViewById<TextView>(R.id.step_text).text = text
        v.findViewById<TextView>(R.id.btn_allow).text = button
        v.findViewById<ImageView>(R.id.step_icon).setImageResource(icon)
    }

    private class Copy(val title: String, val text: String, val button: String, val icon: Int) {
        operator fun component1() = title
        operator fun component2() = text
        operator fun component3() = button
        operator fun component4() = icon
    }

    private fun finishStep(step: Step) {
        done.add(step)
        if (isAdded && view != null) showNext()
    }

    private fun onAllow() {
        when (current ?: return) {
            Step.CONTACTS -> contactsAsk.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            Step.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) notificationsAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openOrSkip(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName))
            }
            Step.BATTERY -> openOrSkip(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // Opens one of the phone's settings screens. When the person comes back the step is done.
    private fun openOrSkip(intent: Intent) {
        try {
            settingsOpened.launch(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                settingsOpened.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null)))
            } catch (e2: ActivityNotFoundException) {
                current?.let { finishStep(it) }
            }
        }
    }
}
