package com.viewgrow.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject
import java.util.Calendar

// History: the contacts added on each day the member was verified, 20 at a time.
// A summary on top, then the days grouped by month. The list comes from the database
// (web_history), the same as the website.
class HistoryFragment : Fragment(R.layout.fragment_history) {

    private val pageSize = 20
    private var loaded = 0
    private var totalDays = 0
    private var loading = false
    private var footTaps = false
    private var monthKey = ""
    private var monthCard: LinearLayout? = null

    private val dayFull = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val dayShort = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    private val monthShort = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val monthLong = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val foot = view.findViewById<TextView>(R.id.hist_foot)
        foot.setOnClickListener { if (footTaps) load() }
        Fonts.apply(view)
        fillSummary()
        if (Status.last == null) {
            val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
            Api.call("web_status", args) { r ->
                val j = r.json
                if (j != null && j.optBoolean("ok")) Status.last = j
                if (isAdded && this.view != null) fillSummary()
            }
        }
        load()
    }

    // The card on top: total, share of the limit, days verified, contacts left.
    private fun fillSummary() {
        val v = view ?: return
        val j = Status.last
        val total = if (j == null || j.isNull("total")) -1 else j.optInt("total")
        val limit = if (j == null || j.isNull("limit")) -1 else j.optInt("limit")
        v.findViewById<TextView>(R.id.sum_total).text = if (total >= 0) total.toString() else "0"
        v.findViewById<TextView>(R.id.sum_of).text = if (limit > 0) "of $limit contacts" else "contacts received"
        val pct = v.findViewById<TextView>(R.id.sum_pct)
        val bar = v.findViewById<ProgressBar>(R.id.sum_bar)
        if (total >= 0 && limit > 0) {
            val p = minOf(100, total * 100 / limit)
            pct.text = "$p%"
            pct.visibility = View.VISIBLE
            bar.progress = p
            v.findViewById<TextView>(R.id.sum_left).text = maxOf(limit - total, 0).toString()
        } else {
            pct.visibility = View.GONE
            bar.progress = 0
        }
        v.findViewById<TextView>(R.id.sum_days).text = if (totalDays > 0) totalDays.toString() else "\u2014"
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
            val list = v.findViewById<LinearLayout>(R.id.hist_list)
            var added = 0
            if (items != null) {
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    addDay(list, o.optString("day"), o.optInt("count"))
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
            fillSummary()
        }
    }

    // ---- Drawing the days ----

    private fun dateOf(iso: String): Calendar? {
        if (iso.length < 10) return null
        return try {
            val c = Calendar.getInstance()
            c.clear()
            c.set(iso.substring(0, 4).toInt(), iso.substring(5, 7).toInt() - 1, iso.substring(8, 10).toInt())
            c
        } catch (e: Exception) { null }
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    // A new month gets its own small heading and its own card; days follow inside the card.
    private fun addDay(list: LinearLayout, iso: String, count: Int) {
        val ctx = list.context
        val d = dateOf(iso)
        val key = if (d == null) iso else d.get(Calendar.YEAR).toString() + "-" + d.get(Calendar.MONTH)
        if (key != monthKey || monthCard == null) {
            monthKey = key
            val label = TextView(ctx)
            label.tag = "semibold"
            label.text = if (d == null) "" else (monthLong[d.get(Calendar.MONTH)] + " " + d.get(Calendar.YEAR)).uppercase()
            label.textSize = 11f
            label.letterSpacing = 0.08f
            label.setTextColor(ContextCompat.getColor(ctx, R.color.muted))
            val llp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            llp.topMargin = Ui.dp(ctx, if (list.childCount == 0) 0 else 14)
            llp.bottomMargin = Ui.dp(ctx, 8)
            label.layoutParams = llp
            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.VERTICAL
            card.setBackgroundResource(R.drawable.bg_card)
            card.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 2), Ui.dp(ctx, 12), Ui.dp(ctx, 2))
            card.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            Fonts.apply(label)
            list.addView(label)
            list.addView(card)
            monthCard = card
        }
        val card = monthCard ?: return
        if (card.childCount > 0) {
            val line = View(ctx)
            line.setBackgroundColor(ContextCompat.getColor(ctx, R.color.border))
            line.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 1))
            card.addView(line)
        }
        val row = dayRow(ctx, d, iso, count)
        Fonts.apply(row)
        card.addView(row)
    }

    // [ 9 / MON ]  Today            +36
    //              9 Nov
    private fun dayRow(ctx: android.content.Context, d: Calendar?, iso: String, count: Int): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10))

        val tile = LinearLayout(ctx)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.setBackgroundResource(R.drawable.bg_day_tile)
        tile.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 46), Ui.dp(ctx, 46))
        val num = TextView(ctx)
        num.tag = "bold"
        num.text = if (d == null) "" else d.get(Calendar.DAY_OF_MONTH).toString()
        num.textSize = 16f
        num.setTextColor(ContextCompat.getColor(ctx, R.color.green_text))
        val wk = TextView(ctx)
        wk.tag = "semibold"
        wk.text = if (d == null) "" else dayShort[d.get(Calendar.DAY_OF_WEEK) - 1]
        wk.textSize = 8.5f
        wk.setTextColor(ContextCompat.getColor(ctx, R.color.text2))
        tile.addView(num)
        tile.addView(wk)

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val title = when {
            d == null -> iso
            sameDay(d, today) -> "Today"
            sameDay(d, yesterday) -> "Yesterday"
            else -> dayFull[d.get(Calendar.DAY_OF_WEEK) - 1]
        }
        val sub = if (d == null) "" else d.get(Calendar.DAY_OF_MONTH).toString() + " " + monthShort[d.get(Calendar.MONTH)]

        val mid = LinearLayout(ctx)
        mid.orientation = LinearLayout.VERTICAL
        val mlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        mlp.marginStart = Ui.dp(ctx, 12)
        mid.layoutParams = mlp
        val t = TextView(ctx)
        t.tag = "semibold"
        t.text = title
        t.textSize = 14f
        t.setTextColor(ContextCompat.getColor(ctx, R.color.dark))
        val s = TextView(ctx)
        s.text = sub
        s.textSize = 11.5f
        s.setTextColor(ContextCompat.getColor(ctx, R.color.muted))
        mid.addView(t)
        mid.addView(s)

        val n = TextView(ctx)
        n.tag = "bold"
        n.text = "+$count"
        n.textSize = 13f
        n.setBackgroundResource(R.drawable.bg_tag_green)
        n.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 5), Ui.dp(ctx, 12), Ui.dp(ctx, 5))
        n.setTextColor(ContextCompat.getColor(ctx, R.color.green_text))

        row.addView(tile)
        row.addView(mid)
        row.addView(n)
        return row
    }
}
