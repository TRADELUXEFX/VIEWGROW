package com.viewgrow.admin

import android.app.Activity
import android.content.Context
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ScrollView
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

    // The last system bar sizes seen, so a screen that is swapped in later (Logged, Check list...) starts with the
    // right spacing at once instead of waiting for Android to send the sizes again.
    private var lastTop = 0
    private var lastBottom = 0

    // Keeps the screen clear of the navigation bar and the keyboard. With `top` (the green header), the header
    // reaches under the status bar and only its content is pushed down; without it, the whole screen is.
    // `onTop` is told the status bar height.
    fun fitInsets(root: View, top: View? = null, onTop: ((Int) -> Unit)? = null) {
        val baseTop = top?.paddingTop ?: 0
        fun place(t: Int, b: Int) {
            lastTop = t; lastBottom = b
            root.setPadding(0, if (top == null && onTop == null) t else 0, 0, b)
            top?.setPadding(top.paddingLeft, baseTop + t, top.paddingRight, top.paddingBottom)
            onTop?.invoke(t)
        }
        val win = (root.context as? Activity)?.window?.decorView
        val now = if (win != null) ViewCompat.getRootWindowInsets(win) else null
        if (now != null) {
            val s = now.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            place(s.top, s.bottom)
        } else place(lastTop, lastBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            place(s.top, s.bottom)
            insets
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { ViewCompat.requestApplyInsets(v) }
            override fun onViewDetachedFromWindow(v: View) {}
        })
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

    fun button(ctx: Context, label: String, fill: Boolean = true, top: Int = 12, icon: Int = 0, onClick: () -> Unit): TextView {
        val b = text(ctx, label, 15f, if (fill) R.color.white else R.color.green_dark, "semibold", center = true, top = top)
        b.minHeight = dp(ctx, if (fill) 52 else 46)
        b.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        // Filled buttons use the member app's green gradient pill.
        b.background = if (fill) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF30BE6C.toInt(), 0xFF0E7E3E.toInt())).apply { cornerRadius = dp(ctx, 999).toFloat() }
        else shape(ctx, color(ctx, R.color.surface), 999, color(ctx, R.color.green_dark), 2)
        if (icon != 0) {
            // A small icon before the label, in the label's colour.
            val d = ContextCompat.getDrawable(ctx, icon)?.mutate()
            d?.setTint(color(ctx, if (fill) R.color.white else R.color.green_dark))
            d?.setBounds(0, 0, dp(ctx, 20), dp(ctx, 20))
            b.setCompoundDrawablesRelative(d, null, null, null)
            b.compoundDrawablePadding = dp(ctx, 10)
        }
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
    fun hero(act: Activity, title: String, sub: String? = null, onBack: (() -> Unit)? = null, logo: Boolean = false, action: Pair<Int, () -> Unit>? = null): LinearLayout {
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
            // On the right: a round icon button when there is an action (e.g. history), otherwise empty space so the title stays centred.
            val right: View
            if (action != null) {
                val ic = ImageView(act)
                ic.setImageResource(action.first)
                ic.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ic.setPadding(dp(act, 9), dp(act, 9), dp(act, 9), dp(act, 9))
                ic.background = shape(act, 0x33FFFFFF, 999)
                ic.layoutParams = LinearLayout.LayoutParams(dp(act, 40), dp(act, 40))
                ic.isClickable = true
                ic.isFocusable = true
                ic.setOnClickListener { action.second() }
                right = ic
            } else {
                right = View(act)
                right.layoutParams = LinearLayout.LayoutParams(dp(act, 40), 1)
            }
            row.addView(back); row.addView(t); row.addView(right)
            h.addView(row)
        } else {
            h.addView(text(act, title, 26f, R.color.white, "extrabold", center = true, top = if (logo) 14 else 0))
        }
        if (sub != null) h.addView(text(act, sub, 13.5f, R.color.white, center = true, top = 6))
        return h
    }

    // A screen: the green header stays fixed at the top and only the content below it scrolls (the content is clipped in
    // a straight line under the header), so the header and its title are never cut or pushed under the status bar.
    fun page(act: Activity, header: View): Pair<LinearLayout, LinearLayout> {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(color(act, R.color.surface))
        (header.parent as? ViewGroup)?.removeView(header)
        root.addView(header)
        val scroll = ScrollView(act)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.isVerticalScrollBarEnabled = false
        val body = LinearLayout(act)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 24))
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll)
        // The header reaches under the status bar; only its content is pushed down.
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

    // ---------- Pop-ups ----------
    // One look for every pop-up in the app: a soft rounded white card on a dimmed screen, Poppins text,
    // round green buttons. (The plain system dialog is not used anywhere.)

    class Option(val label: String, val sub: String = "", val muted: Boolean = false, val selected: Boolean = false)

    private fun popup(act: Activity, onCancel: (() -> Unit)?, build: (card: LinearLayout, close: () -> Unit) -> Unit): Dialog? {
        if (act.isFinishing || act.isDestroyed) return null
        val d = Dialog(act)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.background = shape(act, color(act, R.color.surface), 28)
        card.setPadding(dp(act, 24), dp(act, 26), dp(act, 24), dp(act, 20))
        val frame = FrameLayout(act)
        val w = minOf(act.resources.displayMetrics.widthPixels - dp(act, 48), dp(act, 400))
        frame.addView(card, FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        d.setContentView(frame)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.55f)
        }
        d.setCanceledOnTouchOutside(true)
        d.setOnCancelListener { onCancel?.invoke() }
        build(card) { d.dismiss() }
        card.alpha = 0f; card.scaleX = 0.94f; card.scaleY = 0.94f
        d.setOnShowListener { card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start() }
        d.show()
        return d
    }

    private fun pill(act: Activity, label: String, filled: Boolean, danger: Boolean, top: Int, onClick: () -> Unit): TextView {
        val fg = if (filled) R.color.white else if (danger) R.color.red_dark else R.color.green_dark
        val b = text(act, label, 15f, fg, "semibold", center = true, top = top)
        b.minHeight = dp(act, if (filled) 52 else 46)
        b.setPadding(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 8))
        b.gravity = Gravity.CENTER
        b.background = when {
            filled && danger -> shape(act, color(act, R.color.red_dark), 999)
            filled -> GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF30BE6C.toInt(), 0xFF0E7E3E.toInt())).apply { cornerRadius = dp(act, 999).toFloat() }
            else -> null
        }
        b.isClickable = true
        b.isFocusable = true
        b.setOnClickListener { onClick() }
        return b
    }

    private fun heading(act: Activity, card: LinearLayout, title: String, message: String) {
        if (title.isNotEmpty()) card.addView(text(act, title, 19f, R.color.dark, "extrabold"))
        if (message.isNotEmpty()) card.addView(text(act, message, 14f, R.color.text2, top = if (title.isEmpty()) 0 else 8))
    }

    // A list of choices. onPick gets the index of the tapped one; tapping outside or Cancel does nothing.
    fun choose(act: Activity, title: String, message: String = "", options: List<Option>, cancelLabel: String = "Cancel", onPick: (Int) -> Unit) {
        popup(act, null) { card, close ->
            heading(act, card, title, message)
            val scroll = object : ScrollView(act) {
                override fun onMeasure(w: Int, h: Int) {
                    super.onMeasure(w, View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * 0.5).toInt(), View.MeasureSpec.AT_MOST))
                }
            }
            scroll.overScrollMode = View.OVER_SCROLL_NEVER
            scroll.isVerticalScrollBarEnabled = false
            scroll.layoutParams = lp(act, 16)
            val list = LinearLayout(act)
            list.orientation = LinearLayout.VERTICAL
            options.forEachIndexed { i, o ->
                val row = LinearLayout(act)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(act, 16), dp(act, 13), dp(act, 16), dp(act, 13))
                val base = if (o.selected) shape(act, color(act, R.color.tint), 16, color(act, R.color.green), 2)
                else shape(act, color(act, R.color.surface), 16, color(act, R.color.border))
                row.background = RippleDrawable(ColorStateList.valueOf(0x331FAA59), base, null)
                row.layoutParams = lp(act, if (i == 0) 0 else 8)
                row.isClickable = true
                row.isFocusable = true
                val col = LinearLayout(act)
                col.orientation = LinearLayout.VERTICAL
                col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                col.addView(text(act, o.label, 15f, if (o.muted) R.color.text2 else R.color.dark, "semibold"))
                if (o.sub.isNotEmpty()) col.addView(text(act, o.sub, 12f, R.color.muted, top = 2))
                row.addView(col)
                if (o.selected) {
                    val tick = text(act, "✓", 17f, R.color.green_dark, "bold")
                    tick.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    row.addView(tick)
                }
                row.setOnClickListener { close(); onPick(i) }
                list.addView(row)
            }
            scroll.addView(list)
            card.addView(scroll)
            card.addView(pill(act, cancelLabel, filled = false, danger = false, top = 10) { close() })
        }
    }

    // Yes / No. `danger` turns the main button red (sign out, delete).
    fun confirm(act: Activity, title: String, message: String = "", okLabel: String = "OK", cancelLabel: String = "Cancel",
                danger: Boolean = false, onCancel: (() -> Unit)? = null, onOk: () -> Unit) {
        popup(act, onCancel) { card, close ->
            heading(act, card, title, message)
            card.addView(pill(act, okLabel, filled = true, danger = danger, top = 22) { close(); onOk() })
            card.addView(pill(act, cancelLabel, filled = false, danger = false, top = 8) { close(); onCancel?.invoke() })
        }
    }

    // A note with one button.
    fun notice(act: Activity, title: String, message: String = "", okLabel: String = "OK", onClose: (() -> Unit)? = null) {
        popup(act, onClose) { card, close ->
            heading(act, card, title, message)
            card.addView(pill(act, okLabel, filled = true, danger = false, top = 22) { close(); onClose?.invoke() })
        }
    }

    // A read-only list in a pop-up: a name with a small line under it (e.g. who was saved, and when).
    fun listPopup(act: Activity, title: String, rows: List<Pair<String, String>>, empty: String, closeLabel: String = "Close") {
        popup(act, null) { card, close ->
            heading(act, card, title, "")
            if (rows.isEmpty()) card.addView(text(act, empty, 14f, R.color.text2, top = 12))
            else {
                val scroll = object : ScrollView(act) {
                    override fun onMeasure(w: Int, h: Int) {
                        super.onMeasure(w, View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * 0.55).toInt(), View.MeasureSpec.AT_MOST))
                    }
                }
                scroll.overScrollMode = View.OVER_SCROLL_NEVER
                scroll.isVerticalScrollBarEnabled = false
                scroll.layoutParams = lp(act, 14)
                val list = LinearLayout(act)
                list.orientation = LinearLayout.VERTICAL
                // One line per person: the name on the left, when on the right. Compact, so a long list stays short to scroll.
                rows.forEachIndexed { i, (name, sub) ->
                    if (i > 0) list.addView(View(act).also {
                        it.setBackgroundColor(color(act, R.color.border))
                        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 1))
                    })
                    val row = LinearLayout(act)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER_VERTICAL
                    row.setPadding(0, dp(act, 10), 0, dp(act, 10))
                    val n = text(act, name, 14f, R.color.dark, "semibold")
                    n.maxLines = 1
                    n.ellipsize = android.text.TextUtils.TruncateAt.END
                    n.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    val w = text(act, sub, 12f, R.color.muted)
                    w.maxLines = 1
                    w.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { p -> p.marginStart = dp(act, 10) }
                    row.addView(n); row.addView(w)
                    list.addView(row)
                }
                scroll.addView(list)
                card.addView(scroll)
            }
            card.addView(pill(act, closeLabel, filled = true, danger = false, top = 20) { close() })
        }
    }
}
