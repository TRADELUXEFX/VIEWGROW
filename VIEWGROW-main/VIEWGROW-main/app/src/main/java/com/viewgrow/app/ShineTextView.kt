package com.viewgrow.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

// The "Verified" pill in the green header. shine() sweeps one soft white streak across it, once.
class ShineTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var progress = -1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val band = Path()
    private var animator: ValueAnimator? = null

    fun shine() {
        if (!Anim.on(context)) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 700
            duration = 1100
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { progress = -1f; invalidate() }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress < 0f) return
        val w = width.toFloat()
        val h = height.toFloat()
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, Path.Direction.CW)
        val bandW = w * 0.4f
        val x = -w * 0.6f + w * 1.9f * progress
        val lean = h * 0.36f
        band.reset()
        band.moveTo(x + lean, 0f)
        band.lineTo(x + bandW + lean, 0f)
        band.lineTo(x + bandW, h)
        band.lineTo(x, h)
        band.close()
        paint.shader = LinearGradient(x, 0f, x + bandW, 0f,
            intArrayOf(0x00FFFFFF, 0xBFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawPath(band, paint)
        canvas.restore()
    }
}
