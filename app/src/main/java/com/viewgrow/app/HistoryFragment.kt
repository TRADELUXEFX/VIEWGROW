package com.viewgrow.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

// History: one card per day the member was verified, with the number of contacts, newest first.
// Tap a day to open it (HistoryDayFragment) and see the people. The Duration chip and the Days | People
// toggle sit in the green header. Days comes from the database (web_history), 20 days at a time.
// People lists everyone this phone saved (HistoryLog), once each, newest first, with a chat button.
class HistoryFragment : Fragment(R.layout.fragment_history) {

    companion object {
        // Kept while the app runs, so the chosen duration is still there when coming back to this tab.
        // preset is null when the dates were picked on the calendar. No dates means All time.
        private var preset: String? = "All time"
        private var from: String? = null
        private var to: String? = null
        // 0 = Days, 1 = People. Kept while the app runs, like the duration.
        private var mode = 0
    }

    private val pageSize = 20
    private val days = ArrayList<Pair<String, Int>>()
    private var totalDays = 0
    private var loading = false
    private var loadedOnce = false
    private var footTaps = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        // Daily, Weekly and Monthly follow today's date each time the tab opens.
        preset?.let { p ->
            if (p != "All time") {
                val r = DurationDialog.rangeFor(p)
                from = r.first
                to = r.second
            }
        }
        view.findViewById<TextView>(R.id.hist_foot).setOnClickListener { if (footTaps) load() }
        view.findViewById<TextView>(R.id.hist_chip).setOnClickListener { openDuration() }
        view.findViewById<View>(R.id.help_btn).setOnClickListener { HelpPopup.show(requireContext(), HelpPopup.HISTORY) }
        view.findViewById<TextView>(R.id.tog_days).setOnClickListener { setMode(0) }
        view.findViewById<TextView>(R.id.tog_people).setOnClickListener { setMode(1) }
        showToggle()
        showChip()
        if (mode == 1) render()
        fillTotal()
        if (Status.last == null) {
            val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
            Api.call("web_status", args) { r ->
                val j = r.json
                if (j != null && j.optBoolean("ok")) Status.last = j
                if (isAdded && this.view != null) fillTotal()
            }
        }
        load()
    }

    // The total pill in the header: 312 and /500.
    private fun fillTotal() {
        val v = view ?: return
        val j = Status.last
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

    private fun showChip() {
        view?.findViewById<TextView>(R.id.hist_chip)?.text = DurationDialog.chipText(preset, from, to)
    }

    private fun openDuration() {
        DurationDialog.show(requireContext(), preset, from, to) { p, f, t ->
            preset = p
            from = f
            to = t
            showChip()
            render()
            loadMoreIfNeeded()
        }
    }

    // A date range looks through every day, so the rest of the days are read too.
    private fun needAll() = from != null

    private fun loadMoreIfNeeded() {
        if (needAll() && loadedOnce && days.size < totalDays) load()
    }

    private fun setFoot(text: String, tappable: Boolean) {
        val foot = view?.findViewById<TextView>(R.id.hist_foot) ?: return
        foot.text = text
        foot.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        foot.isClickable = tappable
        footTaps = tappable
    }

    private fun load() {
        if (loading) return
        loading = true
        setFoot("Loading\u2026", false)
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
                if (code == "WRONG_PIN" || code == "LOCKED" || code == "BANNED" || code == "NOT_FOUND") {
                    Ui.toast(v, Api.loginMessage(j))
                    (activity as? MainActivity)?.onLoggedOut()
                } else {
                    setFoot("Could not load. Tap to try again.", true)
                }
                return@call
            }
            val items = j.optJSONArray("items")
            var added = 0
            if (items != null) {
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    days.add(Pair(o.optString("day"), o.optInt("count")))
                    added++
                }
            }
            totalDays = j.optInt("total_days", days.size)
            loadedOnce = true
            render()
            if (needAll() && added > 0 && days.size < totalDays) load()
        }
    }

    private fun inRange(iso: String): Boolean {
        val f = from
        val t = to
        if (f != null && iso < f) return false
        if (t != null && iso > t) return false
        return true
    }

    private fun render() {
        val v = view ?: return
        val list = v.findViewById<LinearLayout>(R.id.hist_list)
        list.removeAllViews()
        val ctx = v.context
        val inflater = LayoutInflater.from(ctx)
        if (mode == 1) { renderPeople(list, inflater); return }
        val shown = days.filter { inRange(it.first) }
        for ((iso, count) in shown) {
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
        val allLoaded = days.size >= totalDays
        when {
            loading -> {}
            !loadedOnce -> {}
            totalDays == 0 -> setFoot("No verified days yet.", false)
            shown.isEmpty() && allLoaded -> setFoot("No days match.", false)
            !needAll() && !allLoaded -> setFoot("Load more", true)
            else -> setFoot("", false)
        }
    }

    // People: everyone saved on this phone in the chosen duration, once each, with a chat button.
    private fun renderPeople(list: LinearLayout, inflater: LayoutInflater) {
        val people = HistoryLog.people(list.context, from, to)
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
        when {
            people.isNotEmpty() -> setFoot("", false)
            needAll() -> setFoot("No people on this phone in that duration.", false)
            else -> setFoot("Nobody saved on this phone yet. People you sync from now on show here.", false)
        }
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
