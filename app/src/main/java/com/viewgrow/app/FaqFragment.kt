package com.viewgrow.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

// Frequently asked questions, opened from Profile > Support. Only questions here: chatting with the admin is the floating
// chat button on Home, History and Profile. Questions sit under three headings.
// Tap a question to open its answer; only one is open at a time. How-to answers are numbered steps, and some
// have a button that does the next thing (open Increase my contact limit, or message the admin).
class FaqFragment : Fragment(R.layout.fragment_faq) {

    private class Faq(
        val q: String,
        val text: String? = null,
        val steps: List<String> = emptyList(),
        val note: String? = null,
        val action: String? = null
    )

    // Plain words. Payments are described only as Google Play does them (the app is listed on Google Play).
    private val groups = listOf(
        "GETTING YOUR CONTACTS" to listOf(
            Faq("When do my contacts arrive?",
                text = "Every day at the time on your Home countdown. Open the app after that and they save to your phone by themselves."),
            Faq("Why didn\u2019t I get contacts today?",
                steps = listOf(
                    "Check that Home says Verified. If it does not, reshare the admin\u2019s status and message the admin.",
                    "Check that no red permission box is showing on Home. If it is, tap Fix now.",
                    "Check that Pause syncing is off in Profile."),
                note = "Still nothing? There may be no new contacts for you yet. Try again at the next release."),
            Faq("What decides the contacts I get?",
                text = "Your reshares. The more you reshare the admin\u2019s status, the more contacts you get."),
            Faq("How do I get verified?",
                steps = listOf(
                    "Reshare the admin\u2019s status.",
                    "Message the admin on WhatsApp so they can check it.",
                    "Once you are verified, your contacts are sent at the next release time.")),
            Faq("How do I save my contacts to my phone?",
                text = "Open the app when your contacts are ready and they save automatically. The app also checks in the background every 1, 6, 12 or 24 hours, whichever you chose in Profile. Contacts permission must be on.")
        ),
        "YOUR LIMIT AND PAYMENTS" to listOf(
            Faq("What is my contact limit?",
                text = "The most contacts your account can receive. Home shows how many you have used out of your limit, for example 312/500."),
            Faq("How do I increase my limit?",
                steps = listOf(
                    "Tap Increase my contact limit on Home.",
                    "Pick +1,000, +3,000 or +5,000.",
                    "Pay with Google Play, in naira. Your limit goes up once Google confirms the payment."),
                action = "upgrade"),
            Faq("Will a bigger limit give me more contacts?",
                text = "A bigger limit only gives your account room for more contacts. Your reshares decide how many you get."),
            Faq("I paid but my limit didn\u2019t go up",
                steps = listOf(
                    "Wait a few minutes, then close and reopen the app.",
                    "Still the same? Message the admin and send your Google Play order number."),
                action = "admin_paid"),
            Faq("How do I get a refund?",
                text = "Google Play handles refunds for Google Play purchases. Open your order in your Google Play order history and ask for one there.")
        ),
        "YOUR PHONE AND PRIVACY" to listOf(
            Faq("How do I stop saving contacts?",
                text = "Go to Profile and turn on Pause syncing. Turn it off again to start saving contacts."),
            Faq("Are my contacts shared with anyone?",
                text = "The contacts stay on your phone, tagged VGC. ViewGrow does not upload your phone\u2019s contacts."),
            Faq("How do I delete my account?",
                text = "Go to Profile, then Delete my account, and confirm. Your account is deleted and you cannot get it back. Your number stays on a deleted list, so it cannot log in or sign up again; to use it again, message the admin. If you cannot log in, message the admin on WhatsApp or email teamlimitlessaffliate@gmail.com from your registered number."),
            Faq("How do I delete the contacts ViewGrow saved?",
                text = "Go to Profile and tap Delete My Contacts. This removes only the contacts ViewGrow saved to your phone.")
        )
    )

    private fun lp(top: Int = 0, ctx: android.content.Context) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.topMargin = Ui.dp(ctx, top) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = view.context
        Fonts.apply(view)
        view.findViewById<View>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val body = view.findViewById<LinearLayout>(R.id.faq_body)

        val answers = ArrayList<View>()
        val chevrons = ArrayList<ImageView>()
        val cards = ArrayList<View>()

        for ((title, items) in groups) {
            val label = TextView(ctx)
            label.tag = "semibold"
            label.text = title
            label.textSize = 11f
            label.letterSpacing = 0.08f
            label.setTextColor(ContextCompat.getColor(ctx, R.color.muted))
            label.layoutParams = lp(14, ctx).also { it.bottomMargin = Ui.dp(ctx, 8) }
            body.addView(label)

            for (item in items) {
                val card = LinearLayout(ctx)
                card.orientation = LinearLayout.VERTICAL
                card.setBackgroundResource(R.drawable.bg_row_card)
                card.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
                card.layoutParams = lp(8, ctx)

                val head = LinearLayout(ctx)
                head.orientation = LinearLayout.HORIZONTAL
                head.gravity = Gravity.CENTER_VERTICAL
                val q = TextView(ctx)
                q.tag = "semibold"
                q.text = item.q
                q.textSize = 14f
                q.setTextColor(ContextCompat.getColor(ctx, R.color.dark))
                q.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val chev = ImageView(ctx)
                chev.setImageResource(R.drawable.ic_chevron_down)
                chev.setColorFilter(ContextCompat.getColor(ctx, R.color.text2))
                chev.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18))
                head.addView(q)
                head.addView(chev)

                // The answer: a short paragraph, or numbered steps, then an optional note and button.
                val a = LinearLayout(ctx)
                a.orientation = LinearLayout.VERTICAL
                a.visibility = View.GONE
                a.layoutParams = lp(8, ctx)
                if (item.text != null) a.addView(answerText(item.text))
                for ((i, s) in item.steps.withIndex()) a.addView(stepRow(i + 1, s))
                if (item.note != null) a.addView(answerText(item.note).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(ctx, 10) })
                if (item.action != null) a.addView(actionButton(item.action))

                card.addView(head)
                card.addView(a)
                head.setOnClickListener {
                    val open = a.visibility != View.VISIBLE
                    // Close every answer, then open the tapped one.
                    answers.forEach { it.visibility = View.GONE }
                    chevrons.forEach { it.rotation = 0f }
                    cards.forEach { it.setBackgroundResource(R.drawable.bg_row_card) }
                    if (open) {
                        a.visibility = View.VISIBLE
                        chev.rotation = 180f
                        card.setBackgroundResource(R.drawable.bg_option_on)
                    }
                }
                answers.add(a)
                chevrons.add(chev)
                cards.add(card)
                body.addView(card)
            }
        }

        Fonts.apply(body)
    }

    private fun answerText(text: String): TextView {
        val ctx = requireContext()
        val a = TextView(ctx)
        a.text = text
        a.textSize = 13f
        a.setLineSpacing(Ui.dp(ctx, 3).toFloat(), 1f)
        a.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        a.layoutParams = lp(0, ctx)
        return a
    }

    private fun stepRow(n: Int, text: String): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = lp(if (n == 1) 0 else 8, ctx)
        val num = TextView(ctx)
        num.tag = "bold"
        num.text = n.toString()
        num.textSize = 11f
        num.gravity = Gravity.CENTER
        num.setTextColor(ContextCompat.getColor(ctx, R.color.green_text))
        num.setBackgroundResource(R.drawable.bg_tint_circle)
        num.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22))
        val t = answerText(text)
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(ctx, 10) }
        row.addView(num)
        row.addView(t)
        return row
    }

    // A button inside an answer. "upgrade" opens Increase my contact limit; "admin_paid" opens WhatsApp with a message ready.
    private fun actionButton(kind: String): View {
        val ctx = requireContext()
        val t = TextView(ctx)
        t.tag = "semibold"
        t.gravity = Gravity.CENTER
        t.textSize = 13f
        t.setTextColor(ContextCompat.getColor(ctx, R.color.green_text))
        t.setBackgroundResource(R.drawable.bg_pill_outline)
        t.isClickable = true
        t.isFocusable = true
        t.minHeight = Ui.dp(ctx, 44)
        t.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8))
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(ctx, 14) }
        if (kind == "upgrade") {
            t.text = "Open Increase my contact limit"
            t.setOnClickListener { UpgradeSheet.show(ctx, Store.lastTotal, Store.lastLimit) }
        } else {
            t.text = "Message admin with my order"
            t.setOnClickListener {
                val msg = "Hi, I paid for a contact pack on ViewGrow but my limit did not go up. My name is ${Store.name}. My Google Play order number is: "
                if (!Remote.openAdmin(ctx, msg)) Ui.toast(requireView(), "Could not open the link.")
            }
        }
        return t
    }
}
