package com.viewgrow.app

import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

// The "Increase my contact limit" pop-up. Opened from the button on Home, from the red bar and the limit reached card,
// and from the Help page.
// Steps: pick one pack, see the new limit and the price, press Buy. Google Play then takes over and shows its own
// payment sheet. Prices show as a placeholder until Google Play billing is switched on, and the buy button
// does not charge anyone yet (see README, items 17 and 18).
object UpgradeSheet {
    val packs = intArrayOf(1000, 3000, 5000)
    private const val PRICE_PLACEHOLDER = "\u20A6 price"

    private fun activityOf(ctx: Context): MainActivity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is MainActivity) return c
            c = c.baseContext
        }
        return null
    }

    fun show(ctx: Context, total: Int, limit: Int) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.sheet_upgrade, null)

        // How much of the limit is used.
        val used = v.findViewById<TextView>(R.id.up_used)
        val bar = v.findViewById<ProgressBar>(R.id.up_bar)
        if (limit > 0) {
            used.text = "%,d of %,d".format(total, limit)
            bar.progress = (total * 100L / limit).toInt().coerceIn(0, 100)
            if (total >= limit) {
                bar.progressDrawable = ContextCompat.getDrawable(ctx, R.drawable.bg_progress_red)
                bar.progress = 100
                used.setTextColor(ContextCompat.getColor(ctx, R.color.red_dark))
            }
        } else {
            used.text = "%,d contacts".format(total)
            bar.visibility = View.GONE
        }

        // Three short facts.
        val facts = v.findViewById<LinearLayout>(R.id.up_facts)
        val lines = listOf(
            "One-time payment, not a subscription",
            "Paid through Google Play, in naira",
            "Adds room only. The admin still decides which contacts you get"
        )
        for ((i, t) in lines.withIndex()) {
            val tv = TextView(ctx)
            tv.text = t
            tv.textSize = 12f
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.text))
            tv.compoundDrawablePadding = Ui.dp(ctx, 8)
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_green, 0, 0, 0)
            tv.gravity = android.view.Gravity.CENTER_VERTICAL
            if (i > 0) tv.setPadding(0, Ui.dp(ctx, 6), 0, 0)
            facts.addView(tv)
        }

        val buy = v.findViewById<MaterialButton>(R.id.up_buy)
        var picked = 1 // the middle pack starts picked
        val rows = v.findViewById<LinearLayout>(R.id.up_packs)
        val views = ArrayList<View>()

        fun paint() {
            for ((i, row) in views.withIndex()) {
                val on = i == picked
                row.setBackgroundResource(if (on) R.drawable.bg_option_on else R.drawable.bg_row_card)
                val dot = row.findViewById<ImageView>(R.id.pack_dot)
                if (on) {
                    dot.setBackgroundResource(R.drawable.bg_green_circle)
                    dot.setImageResource(R.drawable.ic_check_white)
                } else {
                    dot.setBackgroundResource(R.drawable.bg_radio_off)
                    dot.setImageDrawable(null)
                }
            }
            buy.text = "Buy +" + "%,d".format(packs[picked]) + " \u00B7 " + PRICE_PLACEHOLDER
        }

        for ((i, n) in packs.withIndex()) {
            val row = LayoutInflater.from(ctx).inflate(R.layout.item_upgrade_pack, rows, false)
            row.findViewById<TextView>(R.id.pack_name).text = "+" + "%,d".format(n) + " contacts"
            row.findViewById<TextView>(R.id.pack_sub).text =
                if (limit > 0) "Limit becomes " + "%,d".format(limit + n)
                else "Adds " + "%,d".format(n) + " to your limit"
            row.findViewById<TextView>(R.id.pack_price).text = PRICE_PLACEHOLDER
            row.setOnClickListener { picked = i; paint() }
            rows.addView(row)
            views.add(row)
        }
        paint()
        Fonts.apply(v)

        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(v)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        buy.setOnClickListener {
            Toast.makeText(ctx, "Purchases are not switched on yet.", Toast.LENGTH_SHORT).show()
        }
        v.findViewById<View>(R.id.up_later).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.up_help).setOnClickListener {
            dialog.dismiss()
            activityOf(ctx)?.showFaq()
        }
        dialog.show()
    }
}
