package com.viewgrow.app

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import java.util.Calendar

// The pieces Home is built from. Each one is a plain view the fragment adds to the page.
object HomeViews {
    const val GREEN = 0
    const val WARN = 1
    const val RED = 2

    private fun color(ctx: Context, res: Int) = ContextCompat.getColor(ctx, res)

    private fun below(ctx: Context, top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Ui.dp(ctx, top)
        return lp
    }

    // A coloured message box: bold lead-in, then the rest.
    fun permissionCard(ctx: Context, off: List<String>, onFix: () -> Unit): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val lead = if (off.size == 1) "${off[0]} is off." else "Some permissions are off."
        val rest = if (off.size == 1) "Turn it on so ViewGrow keeps working." else off.joinToString(", ") + ". Turn them on so ViewGrow keeps working."
        box.addView(notice(ctx, RED, lead, rest))
        val fix = button(ctx, box, "Fix in Profile", null)
        fix.setOnClickListener { onFix() }
        box.addView(fix)
        return box
    }

    // "Increase my contact limit" button on Home. Always there for verified members.
    fun upgradeButton(ctx: Context, onClick: () -> Unit): View {
        val t = TextView(ctx)
        t.tag = "semibold"
        t.text = "Increase my contact limit"
        t.gravity = Gravity.CENTER
        t.textSize = 13f
        t.setTextColor(color(ctx, R.color.green_text))
        t.setBackgroundResource(R.drawable.bg_pill_outline)
        t.isClickable = true
        t.isFocusable = true
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 44)).also { it.topMargin = Ui.dp(ctx, 12) }
        t.setOnClickListener { onClick() }
        return t
    }

    // Red bar on Home when the member has used 90% or more of their contact limit.
    fun nearLimitCard(ctx: Context, total: Int, limit: Int, onUpgrade: () -> Unit): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        box.addView(notice(ctx, RED, "Almost at your limit.", "You have used $total of $limit contacts. Upgrade so new contacts keep coming."))
        val b = button(ctx, box, "Increase my contact limit", null)
        b.setOnClickListener { onUpgrade() }
        box.addView(b)
        return box
    }

    fun notice(ctx: Context, kind: Int, lead: String, rest: String): TextView {
        val tv = TextView(ctx)
        val bg = when (kind) { WARN -> R.drawable.bg_notice_warn; RED -> R.drawable.bg_notice_red; else -> R.drawable.bg_notice_green }
        val lead1 = when (kind) { WARN -> R.color.amber_text; RED -> R.color.red_dark; else -> R.color.green_text }
        val rest1 = when (kind) { WARN -> R.color.amber_text; RED -> R.color.red_dark; else -> R.color.text }
        tv.setBackgroundResource(bg)
        tv.setTextColor(color(ctx, rest1))
        tv.textSize = 12.5f
        tv.setLineSpacing(Ui.dp(ctx, 2).toFloat(), 1f)
        tv.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
        tv.text = Ui.lead(ctx, lead, rest, color(ctx, lead1))
        tv.layoutParams = below(ctx, 12)
        return tv
    }

    // "Auto-sync looks stopped" with a button to the phone's auto-start screen.
    fun autostartCard(ctx: Context, onFix: () -> Unit): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        box.addView(notice(ctx, WARN, "Auto-sync looks stopped.", "Your phone may be blocking background sync. Allow auto-start so contacts save by themselves."))
        val fix = button(ctx, box, "Fix auto-start", null)
        fix.setOnClickListener { onFix() }
        box.addView(fix)
        return box
    }

    // Shown while contacts are being saved. Not a button: syncing starts by itself when Home opens.
    fun savingRow(ctx: Context, spin: Spinner): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.bg_notice_green)
        row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
        row.layoutParams = below(ctx, 12)
        val img = ImageView(ctx)
        img.setImageDrawable(spin)
        img.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)))
        val tv = TextView(ctx)
        tv.tag = "semibold"
        tv.text = "Saving your contacts\u2026"
        tv.textSize = 12.5f
        tv.setTextColor(color(ctx, R.color.green_text))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = Ui.dp(ctx, 10)
        row.addView(tv, lp)
        return row
    }

    fun button(ctx: Context, parent: ViewGroup, label: String, icon: Int?): MaterialButton {
        val b = LayoutInflater.from(ctx).inflate(R.layout.part_button, parent, false) as MaterialButton
        b.text = label
        if (icon != null) b.setIconResource(icon)
        return b
    }

    private fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.setBackgroundResource(R.drawable.bg_card)
        c.layoutParams = below(ctx, 12)
        return c
    }

    // Small line under the limit reached button: opens the Help page for anyone who paid and saw no change.
    fun paidHelpLink(ctx: Context, onClick: () -> Unit): View {
        val t = TextView(ctx)
        val lead = "Paid but nothing changed? "
        val link = "Get help"
        val sp = android.text.SpannableString(lead + link)
        sp.setSpan(android.text.style.ForegroundColorSpan(color(ctx, R.color.green_text)), lead.length, sp.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), lead.length, sp.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        t.text = sp
        t.textSize = 12f
        t.gravity = Gravity.CENTER
        t.setTextColor(color(ctx, R.color.text2))
        t.isClickable = true
        t.isFocusable = true
        t.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4))
        t.layoutParams = below(ctx, 8)
        t.setOnClickListener { onClick() }
        return t
    }

    // "Contact limit reached" with the line under it.
    fun limitCard(ctx: Context): View {
        val c = card(ctx)
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 20), Ui.dp(ctx, 18), Ui.dp(ctx, 20))
        val title = TextView(ctx)
        title.tag = "bold"
        title.text = "Contact limit reached"
        title.textSize = 20f
        title.gravity = Gravity.CENTER
        title.setTextColor(color(ctx, R.color.dark))
        val sub = TextView(ctx)
        sub.text = "You will not get new contacts until you increase your limit."
        sub.textSize = 12.5f
        sub.gravity = Gravity.CENTER
        sub.setLineSpacing(Ui.dp(ctx, 4).toFloat(), 1f)
        sub.setTextColor(color(ctx, R.color.text2))
        sub.layoutParams = below(ctx, 10)
        c.addView(title)
        c.addView(sub)
        return c
    }

    // "3 quick steps to new contacts" for members who are not verified yet.
    fun stepsCard(ctx: Context): View {
        val c = card(ctx)
        c.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        val title = TextView(ctx)
        title.tag = "bold"
        title.text = "3 quick steps to new contacts"
        title.textSize = 14.5f
        title.gravity = Gravity.CENTER
        title.setTextColor(color(ctx, R.color.dark))
        c.addView(title)
        val steps = listOf(
            Pair("Message admin on WhatsApp and save his number", R.drawable.ic_chat),
            Pair("View admin\u2019s status and reshare his post", R.drawable.ic_reshare),
            Pair("Come back to the app for your new contacts", R.drawable.ic_download)
        )
        steps.forEachIndexed { i, s ->
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setBackgroundResource(R.drawable.bg_row_card)
            row.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
            row.layoutParams = below(ctx, if (i == 0) 14 else 8)

            val num = TextView(ctx)
            num.tag = "bold"
            num.text = (i + 1).toString()
            num.textSize = 11f
            num.gravity = Gravity.CENTER
            num.setTextColor(color(ctx, R.color.green_text))
            num.setBackgroundResource(R.drawable.bg_step_num)
            num.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22))

            val text = TextView(ctx)
            text.text = s.first
            text.textSize = 13f
            text.setTextColor(color(ctx, R.color.dark))
            val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tlp.marginStart = Ui.dp(ctx, 12)
            tlp.marginEnd = Ui.dp(ctx, 10)
            text.layoutParams = tlp

            val icon = ImageView(ctx)
            icon.setImageResource(s.second)
            icon.setBackgroundResource(R.drawable.bg_step_icon)
            icon.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color(ctx, R.color.green_text)))
            icon.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36))

            row.addView(num)
            row.addView(text)
            row.addView(icon)
            c.addView(row)
        }
        return c
    }

    private val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    // 2026-11-09 -> Mon 9 Nov
    fun dayLabel(iso: String): String {
        if (iso.length < 10) return iso
        return try {
            val c = Calendar.getInstance()
            c.clear()
            c.set(iso.substring(0, 4).toInt(), iso.substring(5, 7).toInt() - 1, iso.substring(8, 10).toInt())
            dayNames[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + " " + monthNames[c.get(Calendar.MONTH)]
        } catch (e: Exception) {
            iso
        }
    }

    // One line of the history: day on the left, +count on the right.
    fun historyRow(ctx: Context, day: String, count: Int, divider: Boolean): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.VERTICAL
        if (divider) {
            val line = View(ctx)
            line.setBackgroundColor(color(ctx, R.color.border))
            line.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 1))
            row.addView(line)
        }
        val inner = LinearLayout(ctx)
        inner.orientation = LinearLayout.HORIZONTAL
        inner.gravity = Gravity.CENTER_VERTICAL
        inner.setPadding(0, Ui.dp(ctx, 11), 0, Ui.dp(ctx, 11))
        val d = TextView(ctx)
        d.text = dayLabel(day)
        d.textSize = 13.5f
        d.setTextColor(color(ctx, R.color.text))
        d.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val n = TextView(ctx)
        n.tag = "bold"
        n.text = "+$count"
        n.textSize = 13.5f
        n.setTextColor(color(ctx, R.color.green_text))
        inner.addView(d)
        inner.addView(n)
        row.addView(inner)
        return row
    }

    // The latest three verified days, with "See all" when there are more.
    fun recentCard(ctx: Context, recent: JSONArray, totalDays: Int, onSeeAll: () -> Unit): View {
        val c = card(ctx)
        c.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 6))
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, 0, 0, Ui.dp(ctx, 8))
        val t = TextView(ctx)
        t.tag = "bold"
        t.text = "Recent"
        t.textSize = 14f
        t.setTextColor(color(ctx, R.color.dark))
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(t)
        if (totalDays > 3) {
            val all = TextView(ctx)
            all.tag = "semibold"
            all.text = "See all"
            all.textSize = 12f
            all.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4))
            all.setTextColor(color(ctx, R.color.green_text))
            all.setOnClickListener { onSeeAll() }
            head.addView(all)
        }
        c.addView(head)
        val n = minOf(3, recent.length())
        for (i in 0 until n) {
            val o = recent.optJSONObject(i) ?: continue
            c.addView(historyRow(ctx, o.optString("day"), o.optInt("count"), true))
        }
        return c
    }

    // Names the database suggests when a name is taken. Tapping one calls onPick.
    fun suggestions(ctx: Context, into: LinearLayout, names: JSONArray?, onPick: (String) -> Unit) {
        into.removeAllViews()
        if (names == null) return
        for (i in 0 until names.length()) {
            val name = names.optString(i)
            if (name.isEmpty()) continue
            val chip = TextView(ctx)
            chip.tag = "semibold"
            chip.text = name
            chip.textSize = 14f
            chip.gravity = Gravity.CENTER
            chip.setTextColor(color(ctx, R.color.green_text))
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 10))
            chip.layoutParams = below(ctx, 6)
            chip.setOnClickListener { onPick(name) }
            into.addView(chip)
        }
    }
}
