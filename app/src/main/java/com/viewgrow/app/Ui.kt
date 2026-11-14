package com.viewgrow.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.method.LinkMovementMethod
import android.text.method.PasswordTransformationMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

// Poppins for every text. The weight comes from the view's android:tag
// (medium, semibold, bold, extrabold). No tag means regular.
object Fonts {
    private val cache = HashMap<Int, Typeface?>()

    fun apply(root: View) {
        if (root is TextView) {
            root.typeface = face(root, root.tag as? String)
            // Poppins has tall line metrics. Without this, text sits off-centre in buttons and pills.
            root.includeFontPadding = false
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) apply(root.getChildAt(i))
        }
    }

    // A Poppins weight by name, for bold words inside a sentence.
    fun typeface(ctx: Context, weight: String): Typeface? {
        val res = when (weight) {
            "medium" -> R.font.poppins_medium
            "semibold" -> R.font.poppins_semibold
            "bold" -> R.font.poppins_bold
            "extrabold" -> R.font.poppins_extrabold
            else -> R.font.poppins_regular
        }
        return cache.getOrPut(res) { ResourcesCompat.getFont(ctx, res) }
    }

    private fun face(v: View, weight: String?): Typeface? {
        val res = when (weight) {
            "medium" -> R.font.poppins_medium
            "semibold" -> R.font.poppins_semibold
            "bold" -> R.font.poppins_bold
            "extrabold" -> R.font.poppins_extrabold
            else -> R.font.poppins_regular
        }
        return cache.getOrPut(res) { ResourcesCompat.getFont(v.context, res) }
    }
}

// Draws part of a text in another typeface (a bold lead-in inside a notice).
class FaceSpan(private val face: Typeface?) : MetricAffectingSpan() {
    override fun updateDrawState(p: TextPaint) { if (face != null) p.typeface = face }
    override fun updateMeasureState(p: TextPaint) { if (face != null) p.typeface = face }
}

object Ui {
    private var lastToast: Toast? = null

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // "Reshare verified. Your contacts will be ready tonight." The first part bold and coloured.
    fun lead(ctx: Context, lead: String, rest: String, leadColor: Int): CharSequence {
        val s = SpannableString("$lead $rest")
        s.setSpan(FaceSpan(Fonts.typeface(ctx, "bold")), 0, lead.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(leadColor), 0, lead.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    // A new message replaces the one still showing.
    fun toast(view: View, message: String) {
        lastToast?.cancel()
        lastToast = Toast.makeText(view.context.applicationContext, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    // Button waiting for the database: greyed out with "Please wait".
    fun setBusy(btn: Button, busy: Boolean, label: String) {
        btn.isEnabled = !busy
        btn.text = if (busy) "Please wait" else label
    }

    // Shows a red message under a form, or hides it when the message is null.
    fun showError(tv: TextView, message: String?) {
        tv.text = message ?: ""
        tv.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    // Makes parts of a sentence tappable green words, e.g. "Already a member? Log in".
    fun linkText(tv: TextView, full: String, links: List<Pair<String, () -> Any?>>) {
        val s = SpannableString(full)
        val green = ContextCompat.getColor(tv.context, R.color.green_text)
        for ((word, action) in links) {
            val start = full.indexOf(word)
            if (start < 0) continue
            s.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { action() }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = green
                    ds.isUnderlineText = false
                    ds.isFakeBoldText = true
                }
            }, start, start + word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tv.text = s
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
    }

    // The eye button: shows or hides what is typed in a password box.
    fun passwordEye(box: EditText, eye: ImageView) {
        var shown = false
        eye.setOnClickListener {
            shown = !shown
            box.transformationMethod = if (shown) null else PasswordTransformationMethod.getInstance()
            eye.setImageResource(if (shown) R.drawable.ic_eye else R.drawable.ic_eye_off)
            box.setSelection(box.text.length)
        }
    }

    // The small "Verified" / "Not verified" pill in the green header.
    fun setBadge(root: View, verified: Boolean) {
        val badge = root.findViewById<TextView>(R.id.badge)
        badge.visibility = View.VISIBLE
        if (verified) {
            badge.text = "\u2713 Verified"
            badge.setBackgroundResource(R.drawable.bg_hero_chip)
            badge.setTextColor(ContextCompat.getColor(root.context, R.color.white))
        } else {
            badge.text = "Not verified"
            badge.setBackgroundResource(R.drawable.bg_badge_warn)
            badge.setTextColor(ContextCompat.getColor(root.context, R.color.amber_text))
        }
    }

    // First letter of the name, for the round avatar.
    fun initial(name: String): String {
        val c = name.trim().firstOrNull() ?: return "V"
        return c.uppercaseChar().toString()
    }

    // Fills the green header on Home and Profile: initial, name, number and the light/dark button.
    fun bindUserHeader(root: View) {
        val name = Store.name
        root.findViewById<TextView>(R.id.user_name).text = if (name.isEmpty()) "Welcome" else name
        root.findViewById<TextView>(R.id.user_phone).text = Phone.show(Store.phone)
        val btn = root.findViewById<ImageView>(R.id.theme_btn)
        btn.setImageResource(if (AppTheme.isDark(root.context)) R.drawable.ic_sun else R.drawable.ic_moon)
        btn.setOnClickListener { AppTheme.toggle(root.context) }
    }
}
