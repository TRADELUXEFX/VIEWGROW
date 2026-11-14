package com.viewgrow.app

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject

// History: the contacts added on each day the member was verified, 20 at a time.
// The list comes from the database (web_history), the same as the website.
class HistoryFragment : Fragment(R.layout.fragment_history) {

    private val pageSize = 20
    private var loaded = 0
    private var totalDays = 0
    private var loading = false
    private var footTaps = false
    private var totalRow: LinearLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val foot = view.findViewById<TextView>(R.id.hist_foot)
        foot.setOnClickListener { if (footTaps) load() }
        Fonts.apply(view)
        addTotalRow(view)
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

    // "Total received   312 / 500", the first line of the card.
    private fun addTotalRow(view: View) {
        val ctx = view.context
        val card = view.findViewById<LinearLayout>(R.id.hist_card)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12))
        val label = TextView(ctx)
        label.tag = "semibold"
        label.text = "Total received"
        label.textSize = 13.5f
        label.setTextColor(ContextCompat.getColor(ctx, R.color.muted))
        label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val value = TextView(ctx)
        value.id = R.id.hist_total
        value.tag = "bold"
        value.textSize = 13.5f
        value.setTextColor(ContextCompat.getColor(ctx, R.color.dark))
        row.addView(label)
        row.addView(value)
        card.addView(row)
        totalRow = row
        Fonts.apply(row)
        fillTotal()
    }

    private fun fillTotal() {
        val v = view ?: return
        val value = v.findViewById<TextView>(R.id.hist_total) ?: return
        val j = Status.last
        if (j == null || j.isNull("total")) { value.text = ""; return }
        val limit = if (j.isNull("limit")) "\u2014" else j.optInt("limit").toString()
        value.text = j.optInt("total").toString() + " / " + limit
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
            .put("p_offset", loaded).put("p_limit", pageSize)
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
            val card = v.findViewById<LinearLayout>(R.id.hist_card)
            var added = 0
            if (items != null) {
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val row = HomeViews.historyRow(card.context, o.optString("day"), o.optInt("count"), true)
                    Fonts.apply(row)
                    card.addView(row)
                    added++
                }
            }
            loaded += added
            totalDays = j.optInt("total_days", loaded)
            when {
                totalDays == 0 -> setFoot("No verified days yet.", false)
                loaded < totalDays -> setFoot("Load more", true)
                else -> setFoot("", false)
            }
            fillTotal()
        }
    }
}
