package com.viewgrow.admin

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// Small helpers that build the app's own screens in code, in the same look as the member app:
// a green header with rounded bottom corners, white cards with a light green border, round green buttons.
object Ui {
    private val fonts = HashMap<Int, Typeface?>()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    fun color(ctx: Context, res: Int): Int = ContextCompat.getColor(ctx, res)

    fun font(ctx: Context, weight: String): Typeface? {
        val res = when (weight) {
            "medium" -> R.font.poppins_medium
            "semibold" -> R.font.poppins_semibold
            "bold" -> R.font.poppins_bold
            "extrabold" -> R.font.poppins_extrabold
            else -> R.font.poppins_regular
        }
        return fonts.getOrPut(res) { ResourcesCompat.getFont(ctx, res) }
    }

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    // Draw behind the status bar on every Android version (Android 15+ does this anyway), so the green header
    // can run right up to the top of the screen, like VG Reshare. Status bar icons stay white.
    fun edgeToEdge(act: Activity) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(act.window, false)
        @Suppress("DEPRECATION")
        act.window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView).isAppearanceLightStatusBars = false
    }

    // Keeps the screen clear of the navigation bar and the keyboard. With `top` (the green header), the header
    // reaches under the status bar and only its content is pushed down; without it, the whole screen is.
    fun fitInsets(root: View, top: View? = null, onTop: ((Int) -> Unit)? = null) {
        val baseTop = top?.paddingTop ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            when {
                top != null -> { v.setPadding(0, 0, 0, s.bottom); top.setPadding(top.paddingLeft, baseTop + s.top, top.paddingRight, top.paddingBottom) }
                onTop != null -> { v.setPadding(0, 0, 0, s.bottom); onTop(s.top) }
                else -> v.setPadding(0, s.top, 0, s.bottom)
            }
            insets
        }
    }

    fun lp(ctx: Context, top: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(ctx, top) }

    fun text(ctx: Context, s: CharSequence, size: Float = 14f, colorRes: Int = R.color.text, weight: String = "", center: Boolean = false, top: Int = 0): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        t.setTextColor(color(ctx, colorRes))
        t.typeface = font(ctx, weight)
        t.includeFontPadding = false
        t.setLineSpacing(dp(ctx, 3).toFloat(), 1f)
        if (center) t.gravity = Gravity.CENTER
        t.layoutParams = lp(ctx, top)
        return t
    }

    fun shape(ctx: Context, fill: Int, radius: Int, stroke: Int = 0, strokeW: Int = 1): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(ctx, radius).toFloat()
        if (stroke != 0) g.setStroke(dp(ctx, strokeW), stroke)
        return g
    }

    fun card(ctx: Context, top: Int = 12, pad: Int = 16): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = shape(ctx, color(ctx, R.color.surface), 16, color(ctx, R.color.border))
        c.setPadding(dp(ctx, pad), dp(ctx, pad), dp(ctx, pad), dp(ctx, pad))
        c.layoutParams = lp(ctx, top)
        return c
    }

    fun button(ctx: Context, label: String, fill: Boolean = true, top: Int = 12, onClick: () -> Unit): TextView {
        val b = text(ctx, label, 15f, if (fill) R.color.white else R.color.green_dark, "semibold", center = true, top = top)
        b.minHeight = dp(ctx, if (fill) 52 else 46)
        b.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        // Filled buttons use the member app's green gradient pill.
        b.background = if (fill) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF30BE6C.toInt(), 0xFF0E7E3E.toInt())).apply { cornerRadius = dp(ctx, 999).toFloat() }
        else shape(ctx, color(ctx, R.color.surface), 999, color(ctx, R.color.green_dark), 2)
        b.isClickable = true
        b.isFocusable = true
        b.setOnClickListener { onClick() }
        return b
    }

    fun setBusy(b: TextView, busy: Boolean, label: String, busyLabel: String = "Please wait") {
        b.isEnabled = !busy
        b.alpha = if (busy) 0.7f else 1f
        b.text = if (busy) busyLabel else label
    }

    // The green header. With onBack, a round back button sits on the left and the title is centred.
    fun hero(act: Activity, title: String, sub: String? = null, onBack: (() -> Unit)? = null, logo: Boolean = false): LinearLayout {
        val h = LinearLayout(act)
        h.orientation = LinearLayout.VERTICAL
        h.gravity = Gravity.CENTER_HORIZONTAL
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color(act, R.color.hero_start), color(act, R.color.green), color(act, R.color.hero_end)))
        val r = dp(act, 32).toFloat()
        g.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        h.background = g
        h.setPadding(dp(act, 18), dp(act, 18), dp(act, 18), dp(act, 24))
        h.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (logo) {
            val img = ImageView(act)
            img.setImageResource(R.drawable.viewgrow_logo)
            img.background = shape(act, color(act, R.color.white), 18)
            img.clipToOutline = true
            img.layoutParams = LinearLayout.LayoutParams(dp(act, 64), dp(act, 64))
            h.addView(img)
        }
        if (onBack != null) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val back = text(act, "‹", 26f, R.color.white, "bold", center = true)
            back.layoutParams = LinearLayout.LayoutParams(dp(act, 40), dp(act, 40))
            back.background = shape(act, 0x33FFFFFF, 999)
            back.setOnClickListener { onBack() }
            val t = text(act, title, 20f, R.color.white, "extrabold", center = true)
            t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val spacer = View(act)
            spacer.layoutParams = LinearLayout.LayoutParams(dp(act, 40), 1)
            row.addView(back); row.addView(t); row.addView(spacer)
            h.addView(row)
        } else {
            h.addView(text(act, title, 26f, R.color.white, "extrabold", center = true, top = if (logo) 14 else 0))
        }
        if (sub != null) h.addView(text(act, sub, 13.5f, R.color.white, center = true, top = 6))
        return h
    }

    // A screen: header on top, then a padded column that scrolls.
    fun page(act: Activity, header: View): Pair<LinearLayout, LinearLayout> {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(color(act, R.color.surface))
        root.addView(header)
        val scroll = android.widget.ScrollView(act)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        val body = LinearLayout(act)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 24))
        scroll.addView(body)
        root.addView(scroll)
        fitInsets(root, header)
        return root to body
    }

    fun field(ctx: Context, hint: String, password: Boolean): EditText {
        val e = EditText(ctx)
        e.hint = hint
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        e.setTextColor(color(ctx, R.color.dark))
        e.typeface = font(ctx, "")
        e.background = shape(ctx, color(ctx, R.color.field), 12, color(ctx, R.color.border))
        e.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14))
        e.isSingleLine = true
        if (password) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.layoutParams = lp(ctx, 6)
        return e
    }

    fun spinner(ctx: Context, sizeDp: Int = 34): ProgressBar {
        val p = ProgressBar(ctx)
        p.isIndeterminate = true
        p.indeterminateTintList = android.content.res.ColorStateList.valueOf(color(ctx, R.color.green))
        p.layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp)).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        return p
    }

    // A small "label: value" row inside a card.
    fun line(ctx: Context, label: String, value: String, top: Int = 10): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = lp(ctx, top)
        val a = text(ctx, label, 13.5f, R.color.text)
        a.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val b = text(ctx, value, 13.5f, R.color.green_dark, "bold")
        b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        r.addView(a); r.addView(b)
        return r
    }
}
