package com.viewgrow.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

// The half-circle on Home: a grey track with a coloured arc on top showing how much of the contact limit is used.
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val stroke = resources.displayMetrics.density * 11f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.border)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.green)
    }
    private val box = RectF()

    private var fraction = 0f

    fun set(fraction: Float, color: Int) {
        this.fraction = fraction.coerceIn(0f, 1f)
        arc.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val half = stroke / 2f
        val d = width - stroke
        box.set(half, half, half + d, half + d)
        canvas.drawArc(box, 180f, 180f, false, track)
        if (fraction > 0f) canvas.drawArc(box, 180f, 180f * fraction, false, arc)
    }
}
