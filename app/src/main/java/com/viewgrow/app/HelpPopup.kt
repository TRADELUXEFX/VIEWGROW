package com.viewgrow.app

import android.app.Dialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// A scroll area that never grows past 55% of the screen, so the Got it button always stays on screen.
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST))
    }
}

class HelpRow(val icon: Int, val name: String, val desc: String)
class HelpTopic(val title: String, val sub: String, val rows: List<HelpRow>, val tip: String)

// The ? icon in the green header and its help pop-up (idea from VG Reshare).
// History and Profile each have their own topic below. Same card and buttons as the other pop-ups.
object HelpPopup {

    val HISTORY = HelpTopic(
        "Your history",
        "Everyone ViewGrow saved to this phone.",
        listOf(
            HelpRow(R.drawable.ic_history, "Days", "Each day you got contacts, and how many. Tap a day to see their names and numbers."),
            HelpRow(R.drawable.ic_person, "People", "Everyone saved on this phone, once each, newest first."),
            HelpRow(R.drawable.ic_chat, "Chat button", "Opens a WhatsApp chat with that person."),
            HelpRow(R.drawable.ic_chevron_down, "Duration", "Pick Daily, Weekly, Monthly or All time, or choose your own dates on the calendar.")
        ),
        "History is kept on this phone, so it clears when you log out."
    )

    val PROFILE = HelpTopic(
        "Profile settings",
        "Control how contacts reach your phone.",
        listOf(
            HelpRow(R.drawable.ic_sync, "Sync every", "How often the phone checks for new contacts: every 1, 6, 12 or 24 hours."),
            HelpRow(R.drawable.ic_download, "Pause syncing", "Stops contacts saving by themselves until you turn it back on."),
            HelpRow(R.drawable.ic_bell, "Permissions", "Contacts, Notifications and Battery keep automatic saving working."),
            HelpRow(R.drawable.ic_person, "Delete My Contacts", "Removes the contacts ViewGrow saved. They do not come back unless the admin approves a new download.")
        ),
        "If contacts stop saving by themselves, check that Battery says Unrestricted."
    )

    fun show(ctx: Context, topic: HelpTopic) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_help, null)
        v.findViewById<TextView>(R.id.help_title).text = topic.title
        v.findViewById<TextView>(R.id.help_sub).text = topic.sub
        v.findViewById<TextView>(R.id.help_tip).text = topic.tip
        val green = ctx.resources.getColor(R.color.green_text, null)
        v.findViewById<ImageView>(R.id.help_tip_icon).setColorFilter(green)
        val box = v.findViewById<LinearLayout>(R.id.help_rows)
        val inflater = LayoutInflater.from(ctx)
        for ((i, r) in topic.rows.withIndex()) {
            val row = inflater.inflate(R.layout.item_help_row, box, false)
            row.findViewById<View>(R.id.hr_line).visibility = if (i == 0) View.GONE else View.VISIBLE
            row.findViewById<ImageView>(R.id.hr_icon).apply {
                setImageResource(r.icon)
                setColorFilter(green)
            }
            row.findViewById<TextView>(R.id.hr_name).text = r.name
            row.findViewById<TextView>(R.id.hr_desc).text = r.desc
            box.addView(row)
        }
        Fonts.apply(v)
        val dialog = Dialog(ctx, R.style.Theme_ViewGrow_Dialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ctx.resources.displayMetrics.widthPixels - Ui.dp(ctx, 44), WindowManager.LayoutParams.WRAP_CONTENT)
        v.findViewById<View>(R.id.help_close).setOnClickListener { dialog.dismiss() }
        v.findViewById<View>(R.id.help_ok).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
