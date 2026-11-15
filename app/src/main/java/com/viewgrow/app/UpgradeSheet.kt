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
// A short green box says what the limit is and what paying does. Then: pick one pack, see the new limit and the price, press Buy. Google Play then takes over and shows its own
// payment sheet. The prices come from Google Play (BillingManager), never from the app. The middle pack
// carries the "Recommended" badge and starts picked. While prices load they show "...", and if Google Play
// cannot be reached they show a dash and Buy is switched off.
object UpgradeSheet {
    val packs = intArrayOf(1000, 3000, 5000)
    private const val RECOMMENDED = 1
    private const val LOADING = "\u2026"
    private const val NO_PRICE = "\u2014"

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

        // Plain-words summary: what the limit is, and what paying does.
        v.findViewById<TextView>(R.id.up_explain_1).text =
            "Your contact limit is the most contacts your account can receive and save to your phone. " +
            "When you reach it, no new contacts are added to your phone."
        val second = android.text.SpannableStringBuilder(
            "Paying raises your limit so you can receive more contacts. The admin still decides which contacts you get.")
        val w = second.indexOf("which")
        second.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), w, w + 5,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        v.findViewById<TextView>(R.id.up_explain_2).text = second

        val buy = v.findViewById<MaterialButton>(R.id.up_buy)
        var picked = RECOMMENDED // the recommended (middle) pack starts picked
        // Pack size -> price from Google Play. null while loading, empty if Google Play is not available.
        var prices: Map<Int, String>? = null
        var showing = true
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
            for ((i, row) in views.withIndex()) {
                row.findViewById<TextView>(R.id.pack_price).text = prices?.get(packs[i]) ?: if (prices == null) LOADING else NO_PRICE
            }
            val price = prices?.get(packs[picked])
            buy.text = "Buy +" + "%,d".format(packs[picked]) + (if (price != null) " \u00B7 $price" else "")
            buy.isEnabled = price != null
            buy.alpha = if (price != null) 1f else 0.5f
        }

        for ((i, n) in packs.withIndex()) {
            val row = LayoutInflater.from(ctx).inflate(R.layout.item_upgrade_pack, rows, false)
            row.findViewById<TextView>(R.id.pack_name).text = "+" + "%,d".format(n) + " contacts"
            row.findViewById<TextView>(R.id.pack_sub).text =
                if (limit > 0) "Your limit goes from " + "%,d".format(limit) + " to " + "%,d".format(limit + n)
                else "Adds " + "%,d".format(n) + " to your limit"
            row.findViewById<View>(R.id.pack_badge).visibility = if (i == RECOMMENDED) View.VISIBLE else View.GONE
            row.setOnClickListener { picked = i; paint() }
            rows.addView(row)
            views.add(row)
        }
        paint()
        Fonts.apply(v)

        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(v)
        // The pop-up draws its own curved top, so the sheet frame behind it must not show square corners.
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        // Real prices from Google Play, in the member's own currency.
        BillingManager.loadPrices(ctx) { got ->
            if (!showing) return@loadPrices
            prices = got
            paint()
        }
        buy.setOnClickListener {
            val act = activityOf(ctx)
            if (act == null || prices?.get(packs[picked]) == null) {
                Toast.makeText(ctx, "Google Play is not available right now.", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
                BillingManager.buy(act, packs[picked])
            }
        }
        dialog.setOnDismissListener { showing = false }
        v.findViewById<View>(R.id.up_later).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.up_help).setOnClickListener {
            dialog.dismiss()
            activityOf(ctx)?.showFaq()
        }
        dialog.show()
    }
}
