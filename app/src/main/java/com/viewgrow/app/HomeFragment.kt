package com.viewgrow.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject

// Home: countdown, the two numbers, and whatever the database says you can do now.
// The database decides the state (web_status). This screen only shows it.
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val ui = Handler(Looper.getMainLooper())
    private var status: JSONObject? = Status.last
    private var syncing = false
    private var generation = 0
    private var lastLoad = 0L

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionResult()
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Ui.bindUserHeader(view)
        Fonts.apply(view)
        // The admin's contact (for the WhatsApp buttons) comes from the website's settings.
        Remote.load { if (isAdded && view.parent != null) render() }
        if (status != null) render()
    }

    override fun onResume() {
        super.onResume()
        tick()
        ui.postDelayed(ticker, 1000)
        reload()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ui.removeCallbacksAndMessages(null)
    }

    // ---- Countdown ----

    private fun tick() {
        val v = view ?: return
        v.findViewById<TextView>(R.id.timer).text = Release.format(Release.secondsLeft())
        // The release time has passed: ask the database again.
        if (Release.expired() && System.currentTimeMillis() - lastLoad > 15000) reload()
    }

    // ---- Loading ----

    private fun reload() {
        if (!Store.loggedIn) return
        lastLoad = System.currentTimeMillis()
        val mine = ++generation
        var pending = 2
        var result: ApiResult? = null
        fun finish() {
            pending--
            if (pending > 0) return
            val r = result ?: return
            if (mine != generation || !isAdded || view == null) return
            handle(r)
        }
        Release.load { finish() }
        val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
        Api.call("web_status", args) { r ->
            result = r
            finish()
        }
    }

    private fun handle(r: ApiResult) {
        val v = view ?: return
        val j = r.json
        if (j == null) {
            if (status == null) showProblem(Api.netMessage(r.error)) else Ui.toast(v, Api.netMessage(r.error))
            return
        }
        if (!j.optBoolean("ok")) {
            val code = j.optString("code")
            if (code == "WRONG_PIN" || code == "LOCKED" || code == "BANNED" || code == "NOT_FOUND" || code == "INVALID_PHONE") {
                Ui.toast(v, Api.loginMessage(j))
                (activity as? MainActivity)?.onLoggedOut()
            } else if (status == null) {
                showProblem("Could not check. Please try again.")
            }
            return
        }
        status = j
        Status.last = j
        val name = j.optString("name")
        if (name.isNotEmpty() && name != Store.name) {
            Store.name = name
            Ui.bindUserHeader(v)
        }
        render()
    }

    private fun showProblem(message: String) {
        val v = view ?: return
        val ctx = v.context
        val body = v.findViewById<LinearLayout>(R.id.body)
        body.removeAllViews()
        body.addView(HomeViews.notice(ctx, HomeViews.WARN, "Could not load.", message))
        val retry = HomeViews.button(ctx, body, "Try again", null)
        retry.setOnClickListener { reload() }
        body.addView(retry)
        Fonts.apply(body)
    }

    // ---- Drawing the state ----

    private fun render() {
        val v = view ?: return
        val j = status ?: return
        val ctx = v.context
        val body = v.findViewById<LinearLayout>(R.id.body)
        body.removeAllViews()
        val top = v.findViewById<LinearLayout>(R.id.top_slot)
        top.removeAllViews()

        val state = j.optString("state")
        val verified = state != "NOT_VERIFIED"
        // Not verified: no countdown. The "Not verified yet" box takes its place at the top.
        v.findViewById<View>(R.id.countdown).visibility = if (verified) View.VISIBLE else View.GONE
        setBadge(v, verified)

        val total = if (j.isNull("total")) 0 else j.optInt("total")
        val limit = if (j.isNull("limit")) "\u2014" else j.optInt("limit").toString()
        val available = if (state == "READY") j.optInt("count") else 0
        setStats(v, total, limit, available, state == "LIMIT_REACHED")

        // The automatic sync looks dead on a phone brand that blocks background apps.
        if (OemAutostart.needsPrompt()) {
            body.addView(HomeViews.autostartCard(ctx) { activity?.let { OemAutostart.openSettings(it) } })
        }

        val name = Store.name
        val recentSync = Store.lastSyncAt > 0L && Store.lastSyncCount > 0 &&
            System.currentTimeMillis() - Store.lastSyncAt < 24L * 3600L * 1000L

        when (state) {
            "NOT_VERIFIED" -> {
                val warn = HomeViews.notice(ctx, HomeViews.WARN, "Not verified yet.", "Reshare the admin\u2019s status to unlock your contacts.")
                (warn.layoutParams as LinearLayout.LayoutParams).topMargin = 0
                top.addView(warn)
                Fonts.apply(top)
                body.addView(HomeViews.stepsCard(ctx))
                val msg = "Hi, I just registered on ViewGrow. My name is $name."
                if (Remote.adminLink(msg) != null) {
                    val b = HomeViews.button(ctx, body, "Message admin on WhatsApp", null)
                    b.setOnClickListener { openAdmin(msg) }
                    body.addView(b)
                }
            }
            "READY" -> {
                if (j.optBoolean("full")) {
                    body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "Re-download approved.", "You\u2019ll get every contact you\u2019ve earned."))
                }
                val b = HomeViews.button(ctx, body, if (syncing) "Syncing" else "Sync Contacts to Phone", if (syncing) null else R.drawable.ic_sync)
                if (syncing) Ui.showSpinner(b)
                b.setOnClickListener { onSyncTap() }
                b.isClickable = !syncing
                body.addView(b)
            }
            "LIMIT_REACHED" -> {
                body.addView(HomeViews.limitCard(ctx))
                val msg = "Hi, I have reached my contact limit on ViewGrow. My name is $name. I would like to pay to upgrade my limit so I can receive new contacts."
                if (Remote.adminLink(msg) != null) {
                    val b = HomeViews.button(ctx, body, "UPGRADE CONTACT LIMIT", null)
                    b.setOnClickListener { openAdmin(msg) }
                    body.addView(b)
                }
            }
            "PREPARING" -> {
                body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "Reshare verified.", "Your contacts will be ready " + Release.whenText() + ". Check back then."))
            }
            "ALREADY_TODAY", "NOTHING_NEW", "NO_POOL" -> {
                if (recentSync) {
                    val n = Store.lastSyncCount
                    body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "Contacts saved.", (if (n == 1) "1 new contact is" else "$n new contacts are") + " now in your phone."))
                    } else if (state == "ALREADY_TODAY") {
                    body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "Already downloaded today.", "You\u2019ve used today\u2019s download. Come back tomorrow, after your reshare is verified."))
                } else if (state == "NOTHING_NEW") {
                    body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "All caught up.", "You\u2019ve received all your contacts for now. New ones arrive after your next verified reshare."))
                } else {
                    body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "No contacts today.", "Nobody else was verified in time for this release, so there\u2019s nothing to save. You\u2019re all set."))
                }
            }
            else -> {
                body.addView(HomeViews.notice(ctx, HomeViews.WARN, "Nothing to save right now.", "Check back later."))
            }
        }

        val recent = j.optJSONArray("recent")
        if (state != "NOT_VERIFIED" && recent != null && recent.length() > 0) {
            val days = j.optInt("days", recent.length())
            body.addView(HomeViews.recentCard(ctx, recent, days) { (activity as? MainActivity)?.showTab(1) })
        }
        Fonts.apply(body)
    }

    private fun setBadge(v: View, verified: Boolean) = Ui.setBadge(v, verified)

    // 312/500 with the /500 smaller. The total goes red when the limit is reached.
    private fun setStats(v: View, total: Int, limit: String, available: Int, atLimit: Boolean) {
        val ctx = v.context
        val text = "$total/$limit"
        val s = SpannableString(text)
        val cut = total.toString().length
        s.setSpan(RelativeSizeSpan(0.55f), cut, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.muted)), cut, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (atLimit) s.setSpan(ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.red_dark)), 0, cut, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        v.findViewById<TextView>(R.id.stat_received).text = s
        v.findViewById<TextView>(R.id.stat_available).text = available.toString()
    }

    // ---- WhatsApp ----

    private fun openAdmin(text: String) {
        val link = Remote.adminLink(text) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (e: ActivityNotFoundException) {
            view?.let { Ui.toast(it, "Could not open the link.") }
        }
    }

    // ---- Saving contacts to the phone ----

    private fun onSyncTap() {
        if (syncing) return
        val ctx = context ?: return
        if (!ContactsSaver.hasPermission(ctx)) {
            val wanted = arrayListOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.launch(wanted.toTypedArray())
            return
        }
        startSync()
    }

    private fun onPermissionResult() {
        val ctx = context ?: return
        if (ContactsSaver.hasPermission(ctx)) {
            startSync()
            return
        }
        view?.let { Ui.toast(it, "Allow Contacts so ViewGrow can save your contacts.") }
        // Denied for good: the only way left is the phone's own settings.
        if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
            } catch (e: ActivityNotFoundException) {
                // No settings screen to open.
            }
        }
    }

    private fun startSync() {
        val ctx = context ?: return
        syncing = true
        render()
        Sync.start(ctx) { res ->
            syncing = false
            if (!isAdded || view == null) return@start
            if (res.ok) {
                reload()
            } else {
                Ui.toast(requireView(), res.message)
                render()
            }
        }
    }
}
