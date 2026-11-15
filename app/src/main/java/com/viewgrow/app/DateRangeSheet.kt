package com.viewgrow.app

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// The calendar pop-up behind Duration > Pick dates (idea from VG Reshare): a single day or a range,
// weeks start on Monday, future days are locked, Reset clears the choice. Dates are yyyy-MM-dd text,
// so plain text comparison puts them in order. Same card and buttons as the other pop-ups.
class DateRangeSheet(
    private val ctx: Context,
    initialFrom: String?,
    initialTo: String?,
    private val onApply: (from: String?, to: String?) -> Unit
) {
    private var from: String? = initialFrom
    private var to: String? = initialTo
    private var single: Boolean = initialFrom != null && initialFrom == initialTo
    private val today: String = DayText.iso(Calendar.getInstance())
    private val shown: Calendar = monthStart(initialFrom ?: today)

    private val dialog = Dialog(ctx, R.style.Theme_ViewGrow_Dialog)
    private val v: View = LayoutInflater.from(ctx).inflate(R.layout.dialog_calendar, null)

    private val dlgLabel: TextView = v.findViewById(R.id.dlg_label)
    private val dlgValue: TextView = v.findViewById(R.id.dlg_value)
    private val modeSingle: TextView = v.findViewById(R.id.mode_single)
    private val modeRange: TextView = v.findViewById(R.id.mode_range)
    private val monthPrev: TextView = v.findViewById(R.id.month_prev)
    private val monthNext: TextView = v.findViewById(R.id.month_next)
    private val monthTitle: TextView = v.findViewById(R.id.month_title)
    private val daysGrid: LinearLayout = v.findViewById(R.id.days_grid)
    private val resetBtn: TextView = v.findViewById(R.id.reset_btn)
    private val applyBtn: TextView = v.findViewById(R.id.apply_btn)

    init {
        Fonts.apply(v)
        modeSingle.setOnClickListener { setSingle(true) }
        modeRange.setOnClickListener { setSingle(false) }
        monthPrev.setOnClickListener {
            shown.add(Calendar.MONTH, -1)
            render()
        }
        monthNext.setOnClickListener {
            if (canGoNext()) {
                shown.add(Calendar.MONTH, 1)
                render()
            }
        }
        // Reset clears the choice; Apply then sends it back. Tap outside or Back to close with no change.
        resetBtn.setOnClickListener {
            from = null
            to = null
            render()
        }
        applyBtn.setOnClickListener {
            val f = from
            onApply(f, if (f == null) null else (to ?: f))
            dialog.dismiss()
        }
    }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ctx.resources.displayMetrics.widthPixels - Ui.dp(ctx, 44), WindowManager.LayoutParams.WRAP_CONTENT)
        render()
        dialog.show()
    }

    private fun monthStart(day: String): Calendar =
        (DayText.parse(day) ?: Calendar.getInstance()).apply { set(Calendar.DAY_OF_MONTH, 1) }

    private fun monthKey(c: Calendar): Int = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)

    private fun canGoNext(): Boolean = monthKey(shown) < monthKey(Calendar.getInstance())

    private fun setSingle(on: Boolean) {
        single = on
        if (on) to = from
        render()
    }

    private fun render() {
        segment(modeSingle, single)
        segment(modeRange, !single)
        // Nothing picked yet: the big line already says what to do, so the small label hides.
        dlgLabel.text = if (single) "SELECT DATE" else "SELECT DATES"
        dlgLabel.visibility = if (from == null) View.GONE else View.VISIBLE
        dlgValue.text = valueText()
        monthTitle.text = SimpleDateFormat("MMMM yyyy", Locale.US).format(shown.time)
        monthNext.alpha = if (canGoNext()) 1f else 0.3f
        buildGrid()
    }

    private fun valueText(): String {
        val f = from ?: return if (single) "Select a date" else "Select dates"
        if (single) return DayText.label(f)
        val t = to ?: return DayText.label(f) + " \u2013 \u2026"
        return DayText.label(f) + " \u2013 " + DayText.label(t)
    }

    // The chosen side is filled green with white text, like the History toggle and the Profile interval.
    private fun segment(tv: TextView, on: Boolean) {
        tv.setBackgroundResource(if (on) R.drawable.bg_seg_on else 0)
        tv.setTextColor(color(if (on) R.color.white else R.color.text2))
    }

    private fun buildGrid() {
        daysGrid.removeAllViews()
        val cursor = shown.clone() as Calendar
        // Weeks start on Monday: DAY_OF_WEEK is Sun=1..Sat=7, so Monday has 0 days of lead.
        val lead = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cursor.add(Calendar.DAY_OF_MONTH, -lead)
        val month = shown.get(Calendar.MONTH)
        val lo = from
        val hi = to ?: from
        // Only the rows this month needs.
        val rows = (lead + shown.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7
        repeat(rows) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            repeat(7) {
                val iso = DayText.iso(cursor)
                row.addView(
                    dayCell(
                        label = cursor.get(Calendar.DAY_OF_MONTH).toString(),
                        iso = iso,
                        inMonth = cursor.get(Calendar.MONTH) == month,
                        future = iso > today,
                        lo = lo,
                        hi = hi
                    )
                )
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
            daysGrid.addView(row)
        }
    }

    private fun dayCell(
        label: String, iso: String, inMonth: Boolean, future: Boolean, lo: String?, hi: String?
    ): TextView {
        val end = lo != null && (iso == lo || iso == hi)
        val inside = lo != null && hi != null && iso > lo && iso < hi

        val tv = TextView(ctx)
        tv.layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 40), 1f)
        tv.gravity = Gravity.CENTER
        tv.text = label
        tv.textSize = 14f
        tv.tag = if (end) "semibold" else null
        tv.setTextColor(
            color(
                when {
                    future -> R.color.locked_text
                    !inMonth -> R.color.out_muted
                    end -> R.color.white
                    else -> R.color.dark
                }
            )
        )
        when {
            end -> tv.setBackgroundResource(R.drawable.bg_day_on)
            inside -> tv.setBackgroundColor(color(R.color.range_tint))
        }
        if (!future) tv.setOnClickListener { onDayTapped(iso) }
        Fonts.apply(tv)
        return tv
    }

    private fun onDayTapped(iso: String) {
        if (single) {
            from = iso
            to = iso
        } else {
            val f = from
            when {
                f == null || to != null -> {
                    from = iso
                    to = null
                }
                iso < f -> {
                    to = f
                    from = iso
                }
                else -> to = iso
            }
        }
        render()
    }

    private fun color(id: Int): Int = ContextCompat.getColor(ctx, id)
}
