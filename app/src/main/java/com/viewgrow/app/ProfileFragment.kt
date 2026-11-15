package com.viewgrow.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

// Profile: how often the phone checks for contacts, the legal pages,
// Delete my account, Delete My Contacts. Log out is the button in the green header.
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val hours = intArrayOf(1, 6, 12, 24)
    private val segIds = intArrayOf(R.id.seg_1, R.id.seg_6, R.id.seg_12, R.id.seg_24)
    private val main = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = view.context
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        view.findViewById<TextView>(R.id.version).text = "ViewGrow \u00b7 App version $version"

        // Sync every 1h / 6h / 12h / 24h
        for (i in hours.indices) {
            view.findViewById<View>(segIds[i]).setOnClickListener {
                Store.syncHours = hours[i]
                SyncScheduler.schedule(ctx)
                refresh()
            }
        }

        view.findViewById<SwitchCompat>(R.id.sw_pause).setOnClickListener {
            Store.syncPaused = (it as SwitchCompat).isChecked
            SyncScheduler.schedule(ctx)
            refresh()
        }

        view.findViewById<View>(R.id.row_purchases).setOnClickListener { (activity as? MainActivity)?.showPurchases() }
        view.findViewById<View>(R.id.row_help).setOnClickListener { (activity as? MainActivity)?.showFaq() }
        view.findViewById<View>(R.id.row_terms).setOnClickListener { (activity as? MainActivity)?.showPage(Pages.TERMS) }
        view.findViewById<View>(R.id.row_privacy).setOnClickListener { (activity as? MainActivity)?.showPage(Pages.PRIVACY) }
        view.findViewById<View>(R.id.row_delete_account).setOnClickListener { (activity as? MainActivity)?.showDeleteAccount() }
        view.findViewById<View>(R.id.btn_logout).setOnClickListener { (activity as? MainActivity)?.onLoggedOut() }
        view.findViewById<View>(R.id.btn_delete).setOnClickListener { confirmDelete() }

        Fonts.apply(view)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // Shows what is true right now: the saved choices and the phone's permissions.
    private fun refresh() {
        val v = view ?: return
        val ctx = v.context
        v.findViewById<SwitchCompat>(R.id.sw_pause).isChecked = Store.syncPaused
        // While syncing is paused the interval does nothing, so its card is dimmed.
        v.findViewById<View>(R.id.row_interval).alpha = if (Store.syncPaused) 0.5f else 1f

        val selected = if (Store.syncHours in hours) Store.syncHours else 24
        for (i in hours.indices) {
            val seg = v.findViewById<TextView>(segIds[i])
            val on = hours[i] == selected
            seg.setBackgroundResource(if (on) R.drawable.bg_seg_on else 0)
            seg.setTextColor(ContextCompat.getColor(ctx, if (on) R.color.white else R.color.text2))
        }
    }

    // ---- Delete My Contacts ----

    private fun confirmDelete() {
        val ctx = context ?: return
        if (!ContactsSaver.hasPermission(ctx)) {
            view?.let { Ui.toast(it, "Contacts permission is off. Fix it on the Home screen first.") }
            return
        }
        ConfirmDialog.show(
            ctx,
            "Delete my contacts?",
            "This removes every contact ViewGrow saved to your phone. Your own ViewGrow contact stays. They will not come back unless the admin approves a new download.",
            "Delete"
        ) { deleteNow() }
    }

    private fun deleteNow() {
        val app = context?.applicationContext ?: return
        Thread {
            val n = ContactsSaver.deleteMine(app)
            main.post {
                if (n >= 0) Store.lastSyncAt = 0L
                val v = view
                if (isAdded && v != null) {
                    Ui.toast(
                        v,
                        when {
                            n < 0 -> "Could not delete. Please try again."
                            n == 0 -> "No ViewGrow contacts found on this phone."
                            n == 1 -> "1 contact deleted."
                            else -> "$n contacts deleted."
                        }
                    )
                }
            }
        }.start()
    }
}
