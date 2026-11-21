package com.viewgrow.app

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONObject

// Report or remove a person from History (the "⋮" on each row). Database: web_report (migration
// 20261121050000_reports_and_deleted_list.sql): only people this member was given can be reported; when enough
// different members report the same person they are banned automatically; "Under 18" bans at once until the admin
// checks. "Also remove them from my phone" (on by default) and "Only remove" take the ViewGrow contact off this phone
// and keep it off (Store.blocked, used by the restore).
object ReportSheet {
    private val REASONS = listOf(
        "spam" to "Spam or unwanted messages",
        "scam" to "Scam or asking for money",
        "harassment" to "Harassment or threats",
        "sexual" to "Sexual or offensive content",
        "under18" to "They are under 18",
        "other" to "Something else"
    )

    // One picture per reason, same order as REASONS.
    private val ICONS = listOf(
        R.drawable.ic_rp_spam, R.drawable.ic_rp_scam, R.drawable.ic_rp_harass,
        R.drawable.ic_rp_sexual, R.drawable.ic_rp_under18, R.drawable.ic_rp_other
    )

    // Report sheet (design A): a bottom pop-up with one column of reasons (icon, label, round tick). Tap one to choose it.
    fun show(anchor: View, name: String, number: String) {
        val ctx = anchor.context
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_report, null)
        val shown = if (name.isBlank()) Phone.show(number) else name
        v.findViewById<TextView>(R.id.rp_title).text = "Report $shown"
        val list = v.findViewById<LinearLayout>(R.id.rp_list)
        val ok = v.findViewById<TextView>(R.id.rp_ok)
        val block = v.findViewById<SwitchCompat>(R.id.rp_block)
        v.findViewById<View>(R.id.rp_block_row).setOnClickListener { block.toggle() }
        val flag = ContextCompat.getDrawable(ctx, R.drawable.ic_flag)?.mutate()
        flag?.setBounds(0, 0, Ui.dp(ctx, 18), Ui.dp(ctx, 18))
        flag?.setTint(Color.WHITE)
        ok.setCompoundDrawablesRelative(flag, null, null, null)

        val iconOff = ContextCompat.getColor(ctx, R.color.text2)
        val rows = ArrayList<LinearLayout>()
        val pics = ArrayList<ImageView>()
        val ticks = ArrayList<View>()
        var chosen = -1
        fun choose(i: Int) {
            chosen = i
            rows.forEachIndexed { j, r ->
                val on = j == i
                if (on) r.setBackgroundResource(R.drawable.bg_option_on) else r.background = null
                pics[j].setBackgroundResource(if (on) R.drawable.bg_rp_icon_sq_on else R.drawable.bg_rp_icon_sq)
                pics[j].imageTintList = ColorStateList.valueOf(if (on) Color.WHITE else iconOff)
                ticks[j].setBackgroundResource(if (on) R.drawable.bg_radio_on else R.drawable.bg_radio_off)
                r.isSelected = on
            }
            ok.alpha = 1f
        }
        REASONS.forEachIndexed { i, (_, label) ->
            val pic = ImageView(ctx).apply {
                setBackgroundResource(R.drawable.bg_rp_icon_sq)
                setImageResource(ICONS[i])
                imageTintList = ColorStateList.valueOf(iconOff)
                setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36))
                contentDescription = null
            }
            val text = TextView(ctx).apply {
                this.text = label; textSize = 14f; tag = "medium"
                setTextColor(ContextCompat.getColor(ctx, R.color.dark))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = Ui.dp(ctx, 12); marginEnd = Ui.dp(ctx, 10) }
            }
            val tick = View(ctx).apply {
                setBackgroundResource(R.drawable.bg_radio_off)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20))
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(ctx, 52)
                setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7))
                isClickable = true; isFocusable = true
                contentDescription = label
                addView(pic); addView(text); addView(tick)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = Ui.dp(ctx, 2) }
                setOnClickListener { choose(i) }
            }
            rows.add(row); pics.add(pic); ticks.add(tick); list.addView(row)
        }
        Fonts.apply(v)

        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(v)
        // The layout draws its own curved top, so the sheet frame behind it must stay see-through (same fix as UpgradeSheet).
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    sheet.setBackgroundColor(Color.TRANSPARENT)
                    sheet.backgroundTintList = null
                    if (sheet.viewTreeObserver.isAlive) sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        v.findViewById<View>(R.id.rp_back).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.rp_only_remove).setOnClickListener {
            dialog.dismiss()
            removeFromPhone(ctx, number)
            Ui.toast(anchor, "Removed from your phone.")
        }
        ok.setOnClickListener {
            if (chosen < 0) { Ui.toast(anchor, "Choose what happened."); return@setOnClickListener }
            val reason = REASONS[chosen].first
            val alsoRemove = block.isChecked
            ok.isEnabled = false
            val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
                .put("p_target", number).put("p_reason", reason).put("p_block", alsoRemove)
            Api.call("web_report", args) { r ->
                ok.isEnabled = true
                val j = r.json
                if (j == null) { Ui.toast(anchor, Api.netMessage(r.error)); return@call }
                if (!j.optBoolean("ok")) {
                    Ui.toast(anchor, if (j.optString("code") == "NOT_YOUR_CONTACT") "You can only report people ViewGrow gave you." else "Could not send. Please try again.")
                    return@call
                }
                dialog.dismiss()
                if (alsoRemove) removeFromPhone(ctx, number)
                val first = shown.trim().split(" ").firstOrNull().orEmpty().ifBlank { "They" }
                sent(ctx, if (alsoRemove) "Thank you. The admin will look at it.\n$first was also removed from your phone." else "Thank you. The admin will look at it.")
            }
        }
        dialog.show()
    }

    // "Report sent" pop-up in the middle of the screen, with a Done button.
    private fun sent(ctx: Context, message: String) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_report_sent, null)
        v.findViewById<TextView>(R.id.rs_message).text = message
        Fonts.apply(v)
        val d = Dialog(ctx, R.style.Theme_ViewGrow_Dialog)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(ctx.resources.displayMetrics.widthPixels - Ui.dp(ctx, 56), WindowManager.LayoutParams.WRAP_CONTENT)
        d.window?.setGravity(Gravity.CENTER)
        v.findViewById<View>(R.id.rs_done).setOnClickListener { d.dismiss() }
        d.show()
    }

    // Takes the ViewGrow contact off the phone (never one the member saved) and keeps it off.
    private fun removeFromPhone(ctx: Context, number: String) {
        Store.addBlocked(number)
        val app = ctx.applicationContext
        Thread { ContactsSaver.deleteNumbers(app, listOf(number)) }.start()
    }
}
