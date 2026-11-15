package com.viewgrow.app

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

// Help and FAQ, opened from Profile > Support. Tap a question to open its answer; only one is open at a time.
class FaqFragment : Fragment(R.layout.fragment_faq) {

    // Plain words. Payments are described only as Google Play does them (the app is listed on Google Play).
    private val items = listOf(
        "Why haven't I received my contacts today?" to
            "Contacts arrive at the time shown on the Home countdown. Make sure your reshare has been verified, and that Contacts is turned on in Profile.",
        "How do I get verified?" to
            "Reshare the admin's status, then message the admin on WhatsApp so they can check it. Once you are verified, your contacts are sent at the next release time.",
        "What is my contact limit?" to
            "Your contact limit is the most contacts your account can receive. Home shows how many you have used out of your limit.",
        "How do I increase my contact limit?" to
            "Tap Increase my limit on Home and choose a pack: +1,000, +3,000 or +5,000 contacts. Each pack adds to your limit. You pay through Google Play, in naira.",
        "Does buying a pack mean I will get more contacts?" to
            "A pack gives your account room for more contacts. It does not change which contacts you get. The admin decides which contacts are released to you.",
        "I paid but my limit did not go up. What do I do?" to
            "Message the admin on WhatsApp and send your Google Play order number. The admin will check it with you.",
        "How do I get a refund?" to
            "Refunds for Google Play purchases are handled by Google Play. You can ask for one from your order history in the Google Play Store.",
        "How do I save my contacts to my phone?" to
            "Open the app when your contacts are ready and they save automatically. The app also checks in the background every 1, 6, 12 or 24 hours, whichever you chose in Profile. Contacts permission must be on.",
        "How do I stop saving contacts?" to
            "Go to Profile and turn on Pause syncing. Turn it off again to start saving contacts.",
        "Are my contacts shared with anyone?" to
            "The contacts stay on your phone, tagged VGC. ViewGrow does not upload your phone's contacts.",
        "How do I delete the contacts ViewGrow saved?" to
            "Go to Profile and tap Delete My Contacts. This removes only the contacts ViewGrow saved to your phone."
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = view.context
        Fonts.apply(view)
        view.findViewById<View>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val body = view.findViewById<LinearLayout>(R.id.faq_body)
        val intro = TextView(ctx)
        intro.text = "Answers to common questions. Still stuck? Message the admin on WhatsApp, at the bottom of this page."
        intro.textSize = 13f
        intro.setLineSpacing(Ui.dp(ctx, 3).toFloat(), 1f)
        intro.setTextColor(ContextCompat.getColor(ctx, R.color.text2))
        intro.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = Ui.dp(ctx, 14) }
        body.addView(intro)
        val answers = ArrayList<Pair<TextView, View>>()
        val chevrons = ArrayList<ImageView>()
        for ((i, pair) in items.withIndex()) {
            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.VERTICAL
            card.setBackgroundResource(R.drawable.bg_row_card)
            card.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) lp.topMargin = Ui.dp(ctx, 10)
            card.layoutParams = lp

            val head = LinearLayout(ctx)
            head.orientation = LinearLayout.HORIZONTAL
            head.gravity = android.view.Gravity.CENTER_VERTICAL
            val q = TextView(ctx)
            q.tag = "semibold"
            q.text = pair.first
            q.textSize = 14f
            q.setTextColor(ContextCompat.getColor(ctx, R.color.dark))
            q.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val chev = ImageView(ctx)
            chev.setImageResource(R.drawable.ic_chevron_down)
            chev.setColorFilter(ContextCompat.getColor(ctx, R.color.text2))
            chev.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18))
            head.addView(q)
            head.addView(chev)

            val a = TextView(ctx)
            a.text = pair.second
            a.textSize = 13f
            a.setLineSpacing(Ui.dp(ctx, 3).toFloat(), 1f)
            a.setTextColor(ContextCompat.getColor(ctx, R.color.text))
            a.visibility = View.GONE
            a.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(ctx, 8) }

            card.addView(head)
            card.addView(a)
            head.setOnClickListener {
                val open = a.visibility != View.VISIBLE
                // Close every answer, then open the tapped one.
                answers.forEach { it.second.visibility = View.GONE }
                chevrons.forEach { it.rotation = 0f }
                if (open) {
                    a.visibility = View.VISIBLE
                    chev.rotation = 180f
                }
            }
            answers.add(q to a)
            chevrons.add(chev)
            body.addView(card)
        }

        val admin = HomeViews.button(ctx, body, "Message admin on WhatsApp", null)
        admin.setOnClickListener {
            if (!Remote.openAdmin(ctx, "Hi, I need help with ViewGrow. My name is ${Store.name}.")) {
                Ui.toast(view, "Could not open the link.")
            }
        }
        body.addView(admin)
        Fonts.apply(body)
    }
}
