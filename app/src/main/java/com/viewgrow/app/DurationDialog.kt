package com.viewgrow.app

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import java.util.Calendar

// The Duration popup, the same choices as VG Reshare: Daily, Weekly, Monthly, All time or Pick dates.
// Weeks run Monday to Sunday. All time means no dates.
object DurationDialog {

    // The dates a preset covers. All time (or anything else) is no dates.
    fun rangeFor(name: String): Pair<String?, String?> {
        val c = Calendar.getInstance()
        return when (name) {
            "Daily" -> DayText.iso(c) to DayText.iso(c)
            "Weekly" -> {
                val w = c.clone() as Calendar
                w.add(Calendar.DAY_OF_MONTH, -((w.get(Calendar.DAY_OF_WEEK) + 5) % 7))
                val start = DayText.iso(w)
                w.add(Calendar.DAY_OF_MONTH, 6)
                start to DayText.iso(w)
            }
            "Monthly" -> {
                val m = c.clone() as Calendar
                m.set(Calendar.DAY_OF_MONTH, 1)
                val start = DayText.iso(m)
                m.set(Calendar.DAY_OF_MONTH, m.getActualMaximum(Calendar.DAY_OF_MONTH))
                start to DayText.iso(m)
            }
            else -> null to null
        }
    }

    // What the chip on the History header says.
    fun chipText(preset: String?, from: String?, to: String?): String = when {
        preset != null -> preset
        from == null || to == null -> "All time"
        from == to -> DayText.short(from)
        else -> DayText.short(from) + " \u2013 " + DayText.short(to)
    }

    // onApply gets the preset (null when the dates were picked) and the dates (null dates = All time).
    fun show(ctx: Context, preset: String?, from: String?, to: String?, onApply: (String?, String?, String?) -> Unit) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_duration, null)
        Fonts.apply(v)
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ctx.resources.displayMetrics.widthPixels - Ui.dp(ctx, 44), WindowManager.LayoutParams.WRAP_CONTENT)

        var p: String? = preset
        var f: String? = from
        var t: String? = to
        val options = listOf(
            R.id.opt_daily to "Daily",
            R.id.opt_weekly to "Weekly",
            R.id.opt_monthly to "Monthly",
            R.id.opt_all to "All time"
        )
        val pick = v.findViewById<TextView>(R.id.pick_dates)

        fun paint() {
            for ((id, name) in options) {
                val tv = v.findViewById<TextView>(id)
                val on = p == name
                tv.setBackgroundResource(if (on) R.drawable.bg_option_on else R.drawable.bg_card)
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (on) R.drawable.ic_check_green else 0, 0)
            }
            val custom = p == null && f != null && t != null
            pick.setBackgroundResource(if (custom) R.drawable.bg_option_on else R.drawable.bg_card)
            pick.text = if (custom) DurationDialog.chipText(null, f, t) else "Pick dates\u2026"
            pick.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (custom) R.drawable.ic_check_green else 0, 0)
        }

        for ((id, name) in options) {
            v.findViewById<TextView>(id).setOnClickListener {
                p = name
                val r = rangeFor(name)
                f = r.first
                t = r.second
                paint()
            }
        }

        pick.setOnClickListener {
            val now = Calendar.getInstance()
            DatePickerDialog(ctx, { _, y1, m1, d1 ->
                val start = String.format(java.util.Locale.US, "%04d-%02d-%02d", y1, m1 + 1, d1)
                DatePickerDialog(ctx, { _, y2, m2, d2 ->
                    val end = String.format(java.util.Locale.US, "%04d-%02d-%02d", y2, m2 + 1, d2)
                    p = null
                    f = if (end < start) end else start
                    t = if (end < start) start else end
                    paint()
                }, y1, m1, d1).apply { setTitle("To") }.show()
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                .apply { setTitle("From") }.show()
        }

        v.findViewById<TextView>(R.id.cancel_btn).setOnClickListener {
            dialog.dismiss()
            onApply("All time", null, null)
        }
        v.findViewById<TextView>(R.id.apply_btn).setOnClickListener {
            dialog.dismiss()
            onApply(p, f, t)
        }
        paint()
        dialog.show()
    }
}
