package com.viewgrow.app

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
        val rest = if (off.size == 1) "Turn it on so ViewGrow can save your contacts." else off.joinToString(", ") + ". Turn them on so ViewGrow keeps working."
        box.addView(notice(ctx, RED, lead, rest))
        val fix = button(ctx, box, "Fix now", null)
        fix.setOnClickListener { onFix() }
        box.addView(fix)
        return box
    }

    // "Sync now" button on Home: a refresh icon and the words side by side, centred in one green pill.
    // One tap checks the database and saves the contacts at once. The icon turns while it works (spin).
    fun syncButton(ctx: Context, onClick: (View) -> Unit): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setBackgroundResource(R.drawable.bg_pill_fill)
        row.isClickable = true
        row.isFocusable = true
        row.minimumHeight = Ui.dp(ctx, 46)
        row.layoutParams = below(ctx, 12)
        val icon = ImageView(ctx)
        icon.setImageResource(R.drawable.ic_sync)
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        icon.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18)).also { it.marginEnd = Ui.dp(ctx, 8) }
        val label = TextView(ctx)
        label.tag = "semibold"
        label.text = "Sync now"
        label.textSize = 13f
        label.setTextColor(color(ctx, R.color.surface))
        label.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12))
        row.addView(icon)
        row.addView(label)
        row.setOnClickListener { onClick(icon) }
        return row
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
        t.minHeight = Ui.dp(ctx, 44)
        t.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8))
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(ctx, 12) }
        t.setOnClickListener { onClick() }
        return t
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

    // A message banner: coloured gradient, title, text, a small picture and a close button (same as the website).
    // tone: GREEN good news, RED a problem. `key` says which message this is (state + day); once the member
    // closes it, it stays closed on this phone until the key changes (next day or a different state).
    fun banner(ctx: Context, tone: Int, pic: BannerPic.Kind, title: String, text: String, key: String, animate: Boolean = true): View? {
        val prefs = ctx.getSharedPreferences("vg_banners", Context.MODE_PRIVATE)
        if (prefs.getString("closed", "") == key) return null
        val frame = FrameLayout(ctx)
        frame.setBackgroundResource(if (tone == RED) R.drawable.bg_banner_bad else R.drawable.bg_banner_ok)
        frame.clipToOutline = true
        frame.layoutParams = below(ctx, 12)

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 16))

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val t = TextView(ctx)
        t.tag = "bold"
        t.text = title
        t.textSize = 16f
        t.setTextColor(color(ctx, R.color.white))
        t.setPadding(0, 0, Ui.dp(ctx, 22), 0)
        val body = TextView(ctx)
        body.text = text
        body.textSize = 12.5f
        body.alpha = 0.94f
        body.setLineSpacing(Ui.dp(ctx, 2).toFloat(), 1f)
        body.setTextColor(color(ctx, R.color.white))
        body.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.topMargin = Ui.dp(ctx, 3) }
        col.addView(t)
        col.addView(body)

        // The picture draws itself, so its own parts can move (see BannerPic).
        val img = BannerPic(ctx, pic)
        img.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 96), Ui.dp(ctx, 88)).also { it.marginEnd = -Ui.dp(ctx, 2) }

        row.addView(col)
        row.addView(img)
        frame.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val x = TextView(ctx)
        x.text = "\u00D7"
        x.textSize = 18f
        x.gravity = Gravity.CENTER
        x.setTextColor(color(ctx, R.color.white))
        x.setBackgroundResource(R.drawable.bg_banner_close)
        x.contentDescription = "Close"
        x.isClickable = true
        x.isFocusable = true
        val xp = FrameLayout.LayoutParams(Ui.dp(ctx, 28), Ui.dp(ctx, 28), Gravity.TOP or Gravity.END)
        xp.topMargin = Ui.dp(ctx, 8)
        xp.marginEnd = Ui.dp(ctx, 8)
        x.setOnClickListener {
            prefs.edit().putString("closed", key).apply()
            frame.animate().alpha(0f).translationY(-Ui.dp(ctx, 6).toFloat()).setDuration(200).withEndAction {
                (frame.parent as? ViewGroup)?.removeView(frame)
            }.start()
        }
        frame.addView(x, xp)
        if (animate) { Anim.bannerIn(frame); img.play() }
        return frame
    }

    // "How to get new contacts" for members who are not verified yet.
    fun stepsCard(ctx: Context): View {
        val c = card(ctx)
        c.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        val title = TextView(ctx)
        title.tag = "bold"
        title.text = "How to get new contacts"
        title.textSize = 14.5f
        title.gravity = Gravity.CENTER
        title.setTextColor(color(ctx, R.color.dark))
        c.addView(title)
        val steps = listOf(
            Pair("Message the admin on WhatsApp and save his number", R.drawable.ob_step_message),
            Pair("Reshare the admin\u2019s status post on your own status", R.drawable.ob_step_reshare),
            Pair("New status viewers are added to your phone overnight", R.drawable.ob_step_saved)
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
            // A full-colour picture (rounded green square drawn in the picture), no tint or padding.
            icon.setImageResource(s.second)
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
    fun historyRow(ctx: Context, day: String, count: Int, divider: Boolean, onClick: (() -> Unit)? = null): View {
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
        // The arrow shows on every row so the list lines up; only rows with people open (onClick set).
        val arrow = ImageView(ctx)
        arrow.setImageResource(R.drawable.ic_chevron_right)
        arrow.setColorFilter(color(ctx, R.color.text2))
        arrow.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)).also { it.marginStart = Ui.dp(ctx, 6) }
        inner.addView(arrow)
        if (onClick != null) {
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { onClick() }
        }
        row.addView(inner)
        return row
    }

    // The latest three verified days, with "See all" when there are more.
    fun recentCard(ctx: Context, recent: JSONArray, totalDays: Int, onSeeAll: () -> Unit, onDay: ((String, Int) -> Unit)? = null): View {
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
            val dayKey = o.optString("day")
            val cnt = o.optInt("count")
            // +0 days show the arrow too but do nothing when tapped (same as History).
            c.addView(historyRow(ctx, dayKey, cnt, true, if (onDay != null && cnt > 0) ({ onDay(dayKey, cnt) }) else null))
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
