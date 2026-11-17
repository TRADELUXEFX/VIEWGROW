package com.viewgrow.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject

// History: one card per day the member was verified, with the number of contacts, newest first.
// Three kinds of day. Green: the member reshared and contacts joined (+36). Amber: the member reshared but
// nobody joined that day (+0, "You reshared · No new members that day"); the database sends that day with a count of 0.
// Red: the member did not reshare at all (+0, "You did not reshare"); the day is missing from the database,
// so withMissedDays fills it in.
// Tap a day to open it (HistoryDayFragment) and see the people. The Days | People
// toggle sits in the green header. Days comes from the database (web_history), 20 days at a time.
// People lists everyone this phone saved (HistoryLog), once each, newest first, with a chat button.
class HistoryFragment : Fragment(R.layout.fragment_history) {

    companion object {
        // 0 = Days, 1 = People. Kept while the app runs.
        private var mode = 0
    }

    private val pageSize = 20
    private val MISSED = -1 // a filled-in day with no reshare at all
    private val days = ArrayList<Pair<String, Int>>()
    private var totalDays = 0
    private var loading = false
    private var loadedOnce = false
    private var footTaps = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        view.findViewById<TextView>(R.id.hist_foot).setOnClickListener { if (footTaps) load() }
        view.findViewById<TextView>(R.id.tog_days).setOnClickListener { setMode(0) }
        view.findViewById<TextView>(R.id.tog_people).setOnClickListener { setMode(1) }
        showToggle()
        if (mode == 1) render()
        fillTotal()
        if (Status.last == null) {
            val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
            Api.call("web_status", args) { r ->
                val j = r.json
                if (j != null && j.optBoolean("ok")) Status.last = j
                // History is for verified members. If this answer says not verified, go back to Home.
                if (isAdded && j != null && j.optString("state") == "NOT_VERIFIED") {
                    (activity as? MainActivity)?.showTab(1)
                    return@call
                }
                if (isAdded && this.view != null) fillTotal()
            }
        }
        load()
    }

    // The total pill in the header: 312 and /500.
    private fun fillTotal() {
        val v = view ?: return
        val j = Status.last
        // No number until the database has answered, so a wrong 0 never flashes.
        v.findViewById<View>(R.id.hist_total_pill).visibility = if (j == null) View.INVISIBLE else View.VISIBLE
        val total = if (j == null || j.isNull("total")) 0 else j.optInt("total")
        val limit = if (j == null || j.isNull("limit")) 0 else j.optInt("limit")
        v.findViewById<TextView>(R.id.hist_total).text = total.toString()
        v.findViewById<TextView>(R.id.hist_limit).text = if (limit > 0) "/$limit" else ""
    }

    private fun setMode(m: Int) {
        if (mode == m) return
        mode = m
        showToggle()
        render()
    }

    // The selected side is a white pill with dark green text; the other side is plain white text on the glass.
    private fun showToggle() {
        val v = view ?: return
        val ctx = v.context
        val sides = listOf(v.findViewById<TextView>(R.id.tog_days), v.findViewById<TextView>(R.id.tog_people))
        for ((i, t) in sides.withIndex()) {
            val on = i == mode
            if (on) t.setBackgroundResource(R.drawable.bg_tog_on) else t.background = null
            t.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, if (on) R.color.tog_on_text else R.color.white))
        }
    }

    private var footSpinner: Spinner? = null

    private fun setFoot(text: String, tappable: Boolean) {
        val foot = view?.findViewById<TextView>(R.id.hist_foot) ?: return
        footSpinner?.stop()
        footSpinner = null
        foot.setCompoundDrawables(null, null, null, null)
        foot.text = text
        foot.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        foot.isClickable = tappable
        footTaps = tappable
    }

    // The green spinning ring under the list while the days load (no text).
    private fun showLoading() {
        val foot = view?.findViewById<TextView>(R.id.hist_foot) ?: return
        setFoot("", false)
        val d = Spinner(foot.context, ContextCompat.getColor(foot.context, R.color.green), 28, 3)
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        foot.setCompoundDrawables(null, d, null, null)
        foot.visibility = View.VISIBLE
        footSpinner = d
        d.start()
    }

    private fun load() {
        if (loading) return
        loading = true
        showLoading()
        val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
            .put("p_offset", days.size).put("p_limit", pageSize)
        Api.call("web_history", args) { r ->
            loading = false
            val v = view
            if (!isAdded || v == null) return@call
            val j = r.json
            if (j == null) { setFoot(Api.netMessage(r.error) + " Tap to try again.", true); return@call }
            if (!j.optBoolean("ok")) {
                val code = j.optString("code")
                if (AccountGate.handle(activity as? MainActivity, j)) return@call
                if (code == "WRONG_PIN" || code == "NOT_FOUND" || code == "ACCOUNT_DELETED") {
                    Ui.toast(v, Api.loginMessage(j))
                    (activity as? MainActivity)?.onLoggedOut()
                } else {
                    setFoot("Could not load. Tap to try again.", true)
                }
                return@call
            }
            val items = j.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    days.add(Pair(o.optString("day"), o.optInt("count")))
                }
            }
            totalDays = j.optInt("total_days", days.size)
            loadedOnce = true
            render()
        }
    }

    private fun dayBefore(iso: String): String {
        val c = DayText.parse(iso) ?: return iso
        c.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return DayText.iso(c)
    }

    // The days from the database, newest first, plus a +0 entry for every day in between that has no contacts.
    // Today is never filled in (the day is not over). Days before the oldest loaded day are not filled in either,
    // and one gap is filled for at most 90 days.
    private fun withMissedDays(): List<Pair<String, Int>> {
        if (days.isEmpty()) return days
        val out = ArrayList<Pair<String, Int>>()
        var newer = HistoryLog.today()
        for (d in days) {
            var gap = dayBefore(newer)
            var n = 0
            while (gap > d.first && n < 90) {
                out.add(Pair(gap, MISSED))
                gap = dayBefore(gap)
                n++
            }
            out.add(d)
            if (d.first < newer) newer = d.first
        }
        return out
    }

    private var cardsPlayed = false

    private fun render() {
        val v = view ?: return
        val list = v.findViewById<LinearLayout>(R.id.hist_list)
        list.removeAllViews()
        val ctx = v.context
        val inflater = LayoutInflater.from(ctx)
        if (mode == 1) { renderPeople(list, inflater); return }
        val shown = withMissedDays()
        for ((iso, count) in shown) {
            if (count == 0) {
                // The member reshared (the database has the day) but nobody joined: amber card, not tappable.
                val quiet = inflater.inflate(R.layout.item_history_quiet, list, false)
                quiet.findViewById<TextView>(R.id.h_date).text = DayText.label(iso)
                val r = DayText.relative(iso)
                quiet.findViewById<TextView>(R.id.h_sub).text =
                    if (r.isEmpty()) "You reshared \u00B7 No new members that day" else "$r \u00B7 You reshared \u00B7 No new members that day"
                Fonts.apply(quiet)
                list.addView(quiet)
                continue
            }
            if (count < 0) {
                // A day the member did not reshare: red card, not tappable.
                val miss = inflater.inflate(R.layout.item_history_missed, list, false)
                miss.findViewById<TextView>(R.id.h_date).text = DayText.label(iso)
                val r = DayText.relative(iso)
                miss.findViewById<TextView>(R.id.h_sub).text =
                    if (r.isEmpty()) "You did not reshare" else "$r \u00B7 You did not reshare"
                Fonts.apply(miss)
                list.addView(miss)
                continue
            }
            val row = inflater.inflate(R.layout.item_history_day, list, false)
            row.findViewById<TextView>(R.id.h_date).text = DayText.label(iso)
            val rel = DayText.relative(iso)
            row.findViewById<TextView>(R.id.h_sub).apply {
                text = rel
                visibility = if (rel.isEmpty()) View.GONE else View.VISIBLE
            }
            row.findViewById<TextView>(R.id.h_count).text = "+$count"
            row.setOnClickListener { (activity as? MainActivity)?.showHistoryDay(iso, count) }
            Fonts.apply(row)
            list.addView(row)
        }
        // The cards come in one after another, the first time the list is drawn.
        if (!cardsPlayed && list.childCount > 0) {
            cardsPlayed = true
            Anim.cardsIn(list)
        }
        val allLoaded = days.size >= totalDays
        when {
            loading -> {}
            !loadedOnce -> {}
            totalDays == 0 -> setFoot("No verified days yet.", false)
            !allLoaded -> setFoot("Load more", true)
            else -> setFoot("", false)
        }
    }

    // People: everyone saved on this phone, once each, with a chat button.
    private fun renderPeople(list: LinearLayout, inflater: LayoutInflater) {
        val people = HistoryLog.people(list.context)
        for (p in people) {
            val row = inflater.inflate(R.layout.item_history_contact, list, false)
            row.findViewById<TextView>(R.id.c_name).text = if (p.name.isBlank()) HistoryLog.display(p.number) else p.name
            TextFit.shrinkToFit(row.findViewById(R.id.c_name), 14)
            TextFit.shrinkToFit(row.findViewById(R.id.c_number), 12)
            val num = row.findViewById<TextView>(R.id.c_number)
            if (p.name.isBlank()) num.visibility = View.GONE else num.text = HistoryLog.display(p.number)
            val chat = View.OnClickListener { openChat(p.number) }
            row.findViewById<View>(R.id.c_chat).setOnClickListener(chat)
            row.setOnClickListener(chat)
            Fonts.apply(row)
            list.addView(row)
        }
        if (people.isNotEmpty()) setFoot("", false) else setFoot("No one saved on this phone yet.", false)
    }

    // Opens the WhatsApp chat (wa.me), in WhatsApp or WhatsApp Business, whichever the phone has.
    private fun openChat(number: String) {
        val url = HistoryLog.chatUrl(number)
        val v = view ?: return
        if (url == null) { Ui.toast(v, "No number to chat with."); return }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Ui.toast(v, "Could not open WhatsApp.")
        }
    }
}
