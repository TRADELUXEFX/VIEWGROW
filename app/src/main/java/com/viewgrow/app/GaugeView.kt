package com.viewgrow.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

// Half-circle gauge for the Home "Contacts received" card: how much of the limit is used.
// Green normally, red when the limit is reached. The percentage sits under the arc.
class GaugeView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val d = resources.displayMetrics.density
    private val stroke = 10f * d
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = stroke
        color = ContextCompat.getColor(ctx, R.color.track)
    }
    private val fill = Paint(track)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(ctx, R.font.poppins_bold)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }
    private val box = RectF()
    private var frac = 0f
    private var label = "0%"

    fun set(fraction: Float, atLimit: Boolean) {
        frac = fraction.coerceIn(0f, 1f)
        label = "${(frac * 100).toInt()}%"
        fill.color = ContextCompat.getColor(context, if (atLimit) R.color.red_dark else R.color.green)
        text.color = ContextCompat.getColor(context, if (atLimit) R.color.red_dark else R.color.green_text)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val half = stroke / 2f
        val radius = (width - stroke) / 2f
        box.set(half, half, width - half, half + 2f * radius)
        c.drawArc(box, 180f, 180f, false, track)
        if (frac > 0f) c.drawArc(box, 180f, 180f * frac, false, fill)
        c.drawText(label, width / 2f, height - 3f * d, text)
    }
}
