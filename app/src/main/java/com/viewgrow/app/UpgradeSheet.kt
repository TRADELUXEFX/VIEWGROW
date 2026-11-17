package com.viewgrow.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

// The "Increase my contact limit" pop-up. Opened from the button on Home, from the red bar and the limit reached card,
// and from the Help page.
// Used so far, then: pick one pack, see the price, press Buy. Google Play then takes over and shows its own
// payment sheet. The prices come from Google Play (BillingManager), never from the app. The middle pack
// carries the "Recommended" badge and starts picked. While prices load they show "...", and if Google Play
// cannot be reached they show a dash and Buy is switched off.
object UpgradeSheet {
    val packs = intArrayOf(1000, 3000, 5000)
    private const val RECOMMENDED = 1
    private const val LOADING = "\u2026"
    private const val NO_PRICE = "\u2014"

    // The paywall always looks like the other mode: the app in dark mode shows it light, the app in light mode shows it dark.
    // Only the pop-up is flipped; every other screen keeps the member's choice. The original context is still used to find the
    // activity and to talk to Google Play, because this wrapper only changes how colours are looked up.
    private fun opposite(ctx: Context): Context {
        val night = if (AppTheme.isDark(ctx)) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        val cfg = Configuration(ctx.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        val wrapped = ContextThemeWrapper(ctx, R.style.Theme_ViewGrow)
        wrapped.applyOverrideConfiguration(cfg)
        return wrapped
    }

    private fun activityOf(ctx: Context): MainActivity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is MainActivity) return c
            c = c.baseContext
        }
        return null
    }

    fun show(ctx: Context, total: Int, limit: Int) {
        val ui = opposite(ctx) // colours, sizes and layouts of the pop-up come from this one
        val v = LayoutInflater.from(ui).inflate(R.layout.sheet_upgrade, null)

        // How much of the limit is used.
        val used = v.findViewById<TextView>(R.id.up_used)
        val bar = v.findViewById<ProgressBar>(R.id.up_bar)
        if (limit > 0) {
            used.text = "%,d of %,d contacts".format(total, limit)
            bar.progress = (total * 100L / limit).toInt().coerceIn(0, 100)
            if (total >= limit) {
                bar.progressDrawable = ContextCompat.getDrawable(ui, R.drawable.bg_progress_red)
                bar.progress = 100
                used.setTextColor(ContextCompat.getColor(ui, R.color.red_dark))
            }
        } else {
            used.text = "%,d contacts".format(total)
            bar.visibility = View.GONE
        }

        val buy = v.findViewById<MaterialButton>(R.id.up_buy)
        var picked = RECOMMENDED // the recommended (middle) pack starts picked
        // Pack size -> price from Google Play. null while loading, empty if Google Play is not available.
        var prices: Map<Int, String>? = null
        var showing = true
        val rows = v.findViewById<LinearLayout>(R.id.up_packs)
        val views = ArrayList<View>()

        // Everything paint() needs is looked up once, now, in the pop-up's own mode. Tapping a pack then only swaps these
        // ready drawables and never asks Android for colours again, so a tap cannot bring the member's mode back in.
        val cardOn = ContextCompat.getDrawable(ui, R.drawable.bg_option_on)!!.constantState!!
        val cardOff = ContextCompat.getDrawable(ui, R.drawable.bg_row_card)!!.constantState!!
        val dotOn = ContextCompat.getDrawable(ui, R.drawable.bg_green_circle)!!.constantState!!
        val dotOff = ContextCompat.getDrawable(ui, R.drawable.bg_radio_off)!!.constantState!!
        val tick = ContextCompat.getDrawable(ui, R.drawable.ic_check_white)!!.constantState!!

        fun paint() {
            for ((i, row) in views.withIndex()) {
                val on = i == picked
                row.findViewById<View>(R.id.pack_card).background = (if (on) cardOn else cardOff).newDrawable()
                val dot = row.findViewById<ImageView>(R.id.pack_dot)
                if (on) {
                    dot.background = dotOn.newDrawable()
                    dot.setImageDrawable(tick.newDrawable())
                } else {
                    dot.background = dotOff.newDrawable()
                    dot.setImageDrawable(null)
                }
            }
            for ((i, row) in views.withIndex()) {
                row.findViewById<TextView>(R.id.pack_price).text = prices?.get(packs[i]) ?: if (prices == null) LOADING else NO_PRICE
            }
            val price = prices?.get(packs[picked])
            buy.text = "Raise limit +" + "%,d".format(packs[picked]) + (if (price != null) " \u00B7 $price" else "")
            buy.isEnabled = price != null
            buy.alpha = if (price != null) 1f else 0.5f
        }

        for ((i, n) in packs.withIndex()) {
            val row = LayoutInflater.from(ui).inflate(R.layout.item_upgrade_pack, rows, false)
            row.findViewById<TextView>(R.id.pack_name).text = "Limit +" + "%,d".format(n)
            val sub = row.findViewById<TextView>(R.id.pack_sub)
            if (limit > 0) sub.text = "Your limit goes from %,d to %,d".format(limit, limit + n) else sub.visibility = View.GONE
            if (i == RECOMMENDED) {
                // The tab sits half outside the top edge of the card, so the card is pushed down to make room.
                row.findViewById<View>(R.id.pack_badge).visibility = View.VISIBLE
                row.findViewById<View>(R.id.pack_card).let { c ->
                    (c.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin = Ui.dp(ui, 9)
                }
            }
            row.setOnClickListener { picked = i; paint() }
            rows.addView(row)
            views.add(row)
        }
        paint()
        Fonts.apply(v)

        val dialog = BottomSheetDialog(ui)
        // Keep the pop-up in the opposite mode for its whole life. A dialog is an AppCompat window, and AppCompat quietly
        // switches the colours of the context it is given back to the member's own mode (dark). That is invisible while the
        // pop-up first draws, because everything was already inflated light. It shows up on the first tap on a pack, when
        // paint() looks up the card and tick drawables again and gets the dark ones; it also leaves the bottom strip dark, and
        // it can spoil the next opening of the pop-up. Telling the dialog its own mode stops that switch.
        dialog.delegate.localNightMode =
            if (AppTheme.isDark(ctx)) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        dialog.setContentView(v)
        // The pop-up draws its own curved top, so the sheet frame behind it must not show square corners.
        // The sheet frame paints its own rounded background when it is first laid out, which is what showed as grey edges
        // beside our curves. So it is cleared again right after that first layout.
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            sheet.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    sheet.backgroundTintList = null
                    if (sheet.viewTreeObserver.isAlive) sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
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
