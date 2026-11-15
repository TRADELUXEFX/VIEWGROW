package com.viewgrow.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject

// Home: countdown, the contacts number, and whatever the database says is happening now.
// The database decides the state (web_status). This screen only shows it.
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val ui = Handler(Looper.getMainLooper())
    private var status: JSONObject? = Status.last
    private var syncing = false
    private var generation = 0
    private var lastLoad = 0L
    private var lastAuto = 0L
    private var syncError: String? = null
    private var savingSpinner: Spinner? = null
    private var permShown: String? = null

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
        val buttonBefore = Remote.adminExpected()
        Remote.load {
            // The admin's contact is now known: refresh the "Reshare" notification so its tap opens WhatsApp.
            context?.let { ReshareReminder.show(it) }
            // Draw the screen again only if the admin button actually changed, so nothing flickers.
            if (isAdded && view.parent != null && Remote.adminExpected() != buttonBefore) render()
        }
        if (status != null) render() else showLoading()
    }

    // ---- Loading ring ----

    private var ring: Spinner? = null

    // Until the database has answered, only the turning green ring shows. Nothing else, so no wrong state flashes.
    private fun showLoading() {
        val v = view ?: return
        v.findViewById<View>(R.id.home_scroll).visibility = View.INVISIBLE
        val img = v.findViewById<ImageView>(R.id.home_loading)
        if (ring == null) {
            val d = Spinner(v.context, ContextCompat.getColor(v.context, R.color.green), 36, 4)
            ring = d
            img.setImageDrawable(d)
        }
        img.visibility = View.VISIBLE
        ring?.start()
    }

    private fun hideLoading() {
        val v = view ?: return
        ring?.stop()
        v.findViewById<View>(R.id.home_loading).visibility = View.GONE
        v.findViewById<View>(R.id.home_scroll).visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // Back from the phone's Settings with Contacts turned on or off: update the banner at once.
        val c = context
        if (c != null && status != null && permShown != PermissionWatch.missing(c).joinToString()) render()
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
        ring?.stop()
        ring = null
        savingSpinner?.stop()
        savingSpinner = null
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
        if (status == null) showLoading()
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
            // Banned and Blocked get their own full screens.
            if (AccountGate.handle(activity as? MainActivity, j)) return
            if (code == "WRONG_PIN" || code == "NOT_FOUND" || code == "INVALID_PHONE") {
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
        maybeAutoSync()
    }

    private fun showProblem(message: String) {
        val v = view ?: return
        hideLoading()
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
        hideLoading()
        val ctx = v.context
        val body = v.findViewById<LinearLayout>(R.id.body)
        body.removeAllViews()
        savingSpinner?.stop()
        savingSpinner = null
        val top = v.findViewById<LinearLayout>(R.id.top_slot)
        top.removeAllViews()

        val state = j.optString("state")
        val verified = state != "NOT_VERIFIED"
        // Not verified: no countdown. The "Not verified yet" box takes its place at the top.
        v.findViewById<View>(R.id.countdown).visibility = if (verified) View.VISIBLE else View.GONE
        setBadge(v, verified)
        // The warning box sits above the contacts card for members who are not verified yet.
        (v.findViewById<View>(R.id.stats_card).layoutParams as LinearLayout.LayoutParams).topMargin =
            if (verified) 0 else Ui.dp(v.context, 12)

        val total = if (j.isNull("total")) 0 else j.optInt("total")
        val limit = if (j.isNull("limit")) "\u2014" else j.optInt("limit").toString()
        setStats(v, total, limit, state == "LIMIT_REACHED")

        // The automatic sync looks dead on a phone brand that blocks background apps.
        if (OemAutostart.needsPrompt()) {
            body.addView(HomeViews.autostartCard(ctx) { activity?.let { OemAutostart.openSettings(it) } })
        }

        // A permission turned off in the phone (Contacts, Notifications, Battery): say so on Home.
        val off = PermissionWatch.missing(ctx)
        permShown = off.joinToString()
        if (off.isNotEmpty() && verified) {
            body.addView(HomeViews.permissionCard(ctx, off) { (activity as? MainActivity)?.fixPermission() })
        }

        // 90% of the contact limit or more: the red bar. The limit reached card below takes over at 100%.
        // Based on the total and limit the database sends, so an upgrade makes the bar go away on its own.
        val limitN = if (j.isNull("limit")) 0 else j.optInt("limit")
        Store.lastTotal = total
        Store.lastLimit = limitN
        val nearLimit = verified && state != "LIMIT_REACHED" && limitN > 0 && total * 10 >= limitN * 9
        if (nearLimit) {
            // The red bar has its own big button, so the outline "Increase my contact limit" button is not shown as well.
            body.addView(HomeViews.nearLimitCard(ctx, total, limitN) {
                UpgradeSheet.show(ctx, total, limitN)
            })
        }

        // "Increase my contact limit": always there for verified members, even well under the limit.
        // Not shown when the red bar or the limit reached card already has the big button.
        if (verified && !nearLimit && state != "LIMIT_REACHED") {
            body.addView(HomeViews.upgradeButton(ctx) { UpgradeSheet.show(ctx, total, limitN) })
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
                if (Remote.adminExpected()) {
                    val b = HomeViews.button(ctx, body, "Message admin on WhatsApp", null)
                    b.setOnClickListener { openAdmin(msg) }
                    body.addView(b)
                }
            }
            "READY" -> {
                // Contacts save by themselves when Home opens. This only shows the progress, or why it failed.
                val err = syncError
                if (syncing) {
                    val sp = Spinner(ctx, ContextCompat.getColor(ctx, R.color.green_text), 16, 2)
                    savingSpinner = sp
                    body.addView(HomeViews.savingRow(ctx, sp))
                    sp.start()
                } else if (err != null) {
                    body.addView(HomeViews.notice(ctx, HomeViews.WARN, "Couldn\u2019t save contacts.", err))
                }
            }
            "LIMIT_REACHED" -> {
                // The same Google Play pop-up as everywhere else, so nobody is sent to WhatsApp to pay.
                body.addView(HomeViews.limitCard(ctx))
                val b = HomeViews.button(ctx, body, "Increase my contact limit", null)
                b.setOnClickListener { UpgradeSheet.show(ctx, total, limitN) }
                body.addView(b)
            }
            "PREPARING" -> {
                // Verified, waiting for the release. The countdown already says when. Before the first contacts, say what happens next.
                if ((j.optJSONArray("recent")?.length() ?: 0) == 0) {
                    body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "No contacts yet.", "Your first contacts arrive after the next release."))
                }
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
                    body.addView(HomeViews.notice(ctx, HomeViews.GREEN, "No contacts today.", "Nothing new to save. You\u2019re all set."))
                }
            }
            else -> {
                body.addView(HomeViews.notice(ctx, HomeViews.WARN, "Nothing to save right now.", "Check back later."))
            }
        }

        val recent = j.optJSONArray("recent")
        if (state != "NOT_VERIFIED" && recent != null && recent.length() > 0) {
            val days = j.optInt("days", recent.length())
            body.addView(HomeViews.recentCard(ctx, recent, days,
                { (activity as? MainActivity)?.showTab(1) },
                { day, count -> (activity as? MainActivity)?.showHistoryDay(day, count) }))
        }
        Fonts.apply(body)
    }

    private fun setBadge(v: View, verified: Boolean) = Ui.setBadge(v, verified)

    // 312/500 with the /500 smaller, and the gauge on the right. The gauge turns red at 90% and over.
    private fun setStats(v: View, total: Int, limit: String, atLimit: Boolean) {
        val ctx = v.context
        val label = "$total/$limit"
        val s = SpannableString(label)
        val cut = total.toString().length
        s.setSpan(RelativeSizeSpan(0.55f), cut, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.muted)), cut, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        v.findViewById<TextView>(R.id.stat_received).text = s
        val lim = limit.toIntOrNull() ?: 0
        val pct = if (lim > 0) minOf(100, total * 100 / lim) else 0
        // Green, then amber from 75%, then red from 90%, so the limit never comes as a surprise.
        val level = if (atLimit || pct >= 90) 2 else if (pct >= 75) 1 else 0
        val color = ContextCompat.getColor(ctx, intArrayOf(R.color.green, R.color.amber, R.color.red_dark)[level])
        v.findViewById<GaugeView>(R.id.gauge).set(pct / 100f, color)
        v.findViewById<TextView>(R.id.gauge_pct).apply {
            this.text = "$pct%"
            setTextColor(ContextCompat.getColor(ctx, intArrayOf(R.color.green_text, R.color.amber, R.color.red_dark)[level]))
        }
    }

    // ---- WhatsApp ----

    private fun openAdmin(text: String) {
        val link = Remote.adminLink(text)
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

    // ---- Saving contacts to the phone ----

    // Home saves the contacts by itself as soon as the database says they are ready.
    // Paused means paused: nothing is saved on open either.
    private fun maybeAutoSync() {
        val ctx = context ?: return
        if (syncing || status?.optString("state") != "READY") return
        if (Store.syncPaused || Store.banned) return
        val now = System.currentTimeMillis()
        if (now - lastAuto < 20000L) return
        // Contacts permission off: the banner at the top of Home already says so.
        if (!ContactsSaver.hasPermission(ctx)) return
        lastAuto = now
        startSync()
    }

    private fun startSync() {
        val ctx = context ?: return
        syncing = true
        syncError = null
        render()
        Sync.start(ctx) { res ->
            syncing = false
            if (!isAdded || view == null) return@start
            if (res.banned) {
                (activity as? MainActivity)?.showBanned()
                return@start
            }
            if (res.ok) {
                syncError = null
                reload()
            } else {
                // Another sync (the timer) already running is not an error worth showing.
                syncError = if (res.message.startsWith("Already syncing")) null else res.message
                render()
            }
        }
    }
}
