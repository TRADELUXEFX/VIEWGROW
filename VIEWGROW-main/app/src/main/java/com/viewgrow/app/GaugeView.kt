package com.viewgrow.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

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

    private var filling: ValueAnimator? = null

    fun set(fraction: Float, color: Int, animate: Boolean = false) {
        filling?.cancel()
        arc.color = color
        val to = fraction.coerceIn(0f, 1f)
        if (animate && to > 0f && Anim.on(context)) {
            // First time Home opens: the arc fills up from empty.
            filling = ValueAnimator.ofFloat(0f, to).apply {
                duration = 900
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { this@GaugeView.fraction = it.animatedValue as Float; invalidate() }
                start()
            }
            this.fraction = 0f
        } else {
            this.fraction = to
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        filling?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val half = stroke / 2f
        val d = width - stroke
        box.set(half, half, half + d, half + d)
        canvas.drawArc(box, 180f, 180f, false, track)
        if (fraction > 0f) canvas.drawArc(box, 180f, 180f * fraction, false, arc)
    }
}
