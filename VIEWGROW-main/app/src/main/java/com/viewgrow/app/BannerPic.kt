package com.viewgrow.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.PathParser

// Which part of a picture moves: nothing, the part that rises (badge, dot, hub), or the part that pops (tick, hand, needle).
private const val STATIC = 0
private const val RISE = 1
private const val POP = 2

// The picture on the right of a message banner. The drawings are the same as the website's (same shapes, same
// colours, same 120 x 110 grid), drawn here as shapes instead of a flat PNG so their parts can move on their own,
// like the website: the badge or dot rises into place and the tick, clock hand or needle pops in.
// Call play() when the banner slides in. With animations turned off in the phone, nothing moves.
class BannerPic(ctx: Context, private val kind: Kind) : View(ctx) {

    enum class Kind { WAIT, SAVED, DONE, NONE, BAD }

    private class Shape(
        val path: Path,
        val fill: Int = 0,
        val stroke: Int = 0,
        val width: Float = 0f,
        val roundCap: Boolean = false,
        val roundJoin: Boolean = false,
        val dash: FloatArray? = null,
        val layer: Int = STATIC,
        val text: String? = null,
        val tx: Float = 0f,
        val ty: Float = 0f,
        val size: Float = 0f
    ) {
        // The middle of the shape: a popping part grows from here.
        val px: Float
        val py: Float
        init {
            val b = RectF()
            path.computeBounds(b, true)
            px = b.centerX()
            py = b.centerY()
        }
    }

    private val shapes = ArrayList<Shape>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face by lazy { Fonts.typeface(context, "extrabold") }
    private val ease = DecelerateInterpolator()

    // 1 = finished (the normal, still picture). While playing: rise goes 0 to 1, pop is the scale of the popping part.
    private var rise = 1f
    private var pop = 1f
    private var riseAnim: ValueAnimator? = null
    private var popAnim: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        build()
    }

    // Same timing as the website: the rising part starts 0.6 s after the banner appears and takes 0.7 s;
    // the popping part starts at 0.85 s, overshoots to 1.18 and settles in 0.5 s.
    fun play() {
        if (!Anim.on(context)) return
        stopAnims()
        rise = 0f
        pop = 0f
        invalidate()
        riseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 600
            duration = 700
            interpolator = ease
            addUpdateListener { rise = it.animatedValue as Float; invalidate() }
            start()
        }
        popAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 850
            duration = 500
            addUpdateListener { pop = popScale(it.animatedValue as Float); invalidate() }
            start()
        }
    }

    private fun popScale(t: Float): Float =
        if (t < 0.7f) 1.18f * ease.getInterpolation(t / 0.7f)
        else 1.18f - 0.18f * ease.getInterpolation((t - 0.7f) / 0.3f)

    private fun stopAnims() {
        riseAnim?.cancel()
        popAnim?.cancel()
        riseAnim = null
        popAnim = null
    }

    // The screen went away mid-animation: stop, and leave the finished picture.
    override fun onDetachedFromWindow() {
        stopAnims()
        rise = 1f
        pop = 1f
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width / VIEW_W, height / VIEW_H)
        c.save()
        c.translate((width - VIEW_W * s) / 2f, (height - VIEW_H * s) / 2f)
        c.scale(s, s)
        for (sh in shapes) drawShape(c, sh)
        c.restore()
    }

    private fun drawShape(c: Canvas, sh: Shape) {
        var k = 1f
        c.save()
        when (sh.layer) {
            RISE -> { k = rise; c.translate(0f, RISE_FROM * (1f - rise)) }
            POP -> { if (pop <= 0.001f) { c.restore(); return }; c.scale(pop, pop, sh.px, sh.py) }
        }
        if (k > 0.001f) {
            if (sh.text != null) {
                paint.style = Paint.Style.FILL
                paint.pathEffect = null
                paint.typeface = face
                paint.textSize = sh.size
                paint.textAlign = Paint.Align.CENTER
                paint.color = sh.fill
                paint.alpha = ((sh.fill ushr 24) * k + 0.5f).toInt()
                c.drawText(sh.text, sh.tx, sh.ty, paint)
            } else {
                if (sh.fill != 0) {
                    paint.style = Paint.Style.FILL
                    paint.pathEffect = null
                    paint.color = sh.fill
                    paint.alpha = ((sh.fill ushr 24) * k + 0.5f).toInt()
                    c.drawPath(sh.path, paint)
                }
                if (sh.stroke != 0) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = sh.width
                    paint.strokeCap = if (sh.roundCap) Paint.Cap.ROUND else Paint.Cap.BUTT
                    paint.strokeJoin = if (sh.roundJoin) Paint.Join.ROUND else Paint.Join.MITER
                    paint.pathEffect = sh.dash?.let { DashPathEffect(it, 0f) }
                    paint.color = sh.stroke
                    paint.alpha = ((sh.stroke ushr 24) * k + 0.5f).toInt()
                    c.drawPath(sh.path, paint)
                    paint.pathEffect = null
                }
            }
        }
        c.restore()
    }

    private fun add(s: Shape) { shapes.add(s) }

    private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) =
        Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) }

    // The drawings, copied from the website's banner pictures (index.js), in the same order.
    private fun build() {
        when (kind) {
            Kind.WAIT -> {
                add(Shape(circle(60f, 54f, 48f), fill = 0x29FFFFFF.toInt()))
                add(Shape(oval(60f, 102f, 34f, 5f), fill = 0x24000000.toInt()))
                add(Shape(rect(36f, 18f, 48f, 80f, 11f), fill = 0xFFFFFFFF.toInt()))
                add(Shape(rect(42f, 27f, 36f, 58f, 5f), fill = 0xFFE8F7EE.toInt()))
                add(Shape(circle(60f, 52f, 15f), fill = 0xFFFFFFFF.toInt(), stroke = 0xFF1FAA59.toInt(), width = 3f))
                add(Shape(PathParser.createPathFromPathData("M60 44v8l6 4"), stroke = 0xFF158245.toInt(), width = 3f, roundCap = true, layer = POP))
                add(Shape(rect(48f, 72f, 24f, 5f, 2.5f), fill = 0xFFB8DEC6.toInt()))
                add(Shape(circle(90f, 34f, 12f), fill = 0xFFFFC24A.toInt()))
                add(Shape(circle(96f, 30f, 10f), fill = 0xFF2BB765.toInt()))
                add(Shape(circle(20f, 40f, 2.5f), fill = 0xFFFFFFFF.toInt()))
                add(Shape(circle(104f, 52f, 2f), fill = 0xFFFFFFFF.toInt(), layer = RISE))
            }
            Kind.SAVED -> {
                add(Shape(circle(60f, 54f, 48f), fill = 0x29FFFFFF.toInt()))
                add(Shape(oval(60f, 102f, 34f, 5f), fill = 0x24000000.toInt()))
                add(Shape(rect(36f, 18f, 48f, 80f, 11f), fill = 0xFFFFFFFF.toInt()))
                add(Shape(circle(49f, 34f, 4.5f), fill = 0xFFCDEBD8.toInt()))
                add(Shape(rect(57f, 31f, 20f, 6f, 3f), fill = 0xFFE1F5EB.toInt()))
                add(Shape(circle(49f, 48f, 4.5f), fill = 0xFFCDEBD8.toInt()))
                add(Shape(rect(57f, 45f, 20f, 6f, 3f), fill = 0xFFE1F5EB.toInt()))
                add(Shape(circle(49f, 62f, 4.5f), fill = 0xFFCDEBD8.toInt()))
                add(Shape(rect(57f, 59f, 20f, 6f, 3f), fill = 0xFFE1F5EB.toInt()))
                add(Shape(circle(49f, 76f, 4.5f), fill = 0xFFCDEBD8.toInt()))
                add(Shape(rect(57f, 73f, 20f, 6f, 3f), fill = 0xFFE1F5EB.toInt()))
                add(Shape(circle(84f, 80f, 15f), fill = 0xFF1FAA59.toInt(), stroke = 0xFFFFFFFF.toInt(), width = 3f, layer = RISE))
                add(Shape(PathParser.createPathFromPathData("M77 80l5 5 9-10"), stroke = 0xFFFFFFFF.toInt(), width = 3.4f, roundCap = true, roundJoin = true, layer = POP))
            }
            Kind.DONE -> {
                add(Shape(circle(60f, 54f, 48f), fill = 0x29FFFFFF.toInt()))
                add(Shape(oval(60f, 102f, 34f, 5f), fill = 0x24000000.toInt()))
                add(Shape(rect(28f, 24f, 64f, 66f, 10f), fill = 0xFFFFFFFF.toInt()))
                add(Shape(PathParser.createPathFromPathData("M28 40h64"), stroke = 0xFFCDEBD8.toInt(), width = 3f))
                add(Shape(PathParser.createPathFromPathData("M42 18v12M78 18v12"), stroke = 0xFFFFFFFF.toInt(), width = 5f, roundCap = true))
                add(Shape(circle(60f, 64f, 15f), fill = 0xFF1FAA59.toInt(), layer = RISE))
                add(Shape(PathParser.createPathFromPathData("M53 64l5 5 9-10"), stroke = 0xFFFFFFFF.toInt(), width = 3.4f, roundCap = true, roundJoin = true, layer = POP))
            }
            Kind.NONE -> {
                add(Shape(circle(60f, 54f, 48f), fill = 0x29FFFFFF.toInt()))
                add(Shape(oval(60f, 102f, 34f, 5f), fill = 0x24000000.toInt()))
                add(Shape(PathParser.createPathFromPathData("M26 64 38 34h44l12 30v22a6 6 0 0 1-6 6H32a6 6 0 0 1-6-6z"), fill = 0xFFFFFFFF.toInt()))
                add(Shape(PathParser.createPathFromPathData("M26 64h20l4 8h20l4-8h20"), stroke = 0xFFB8DEC6.toInt(), width = 3f, roundJoin = true))
                add(Shape(PathParser.createPathFromPathData("M46 48h28"), stroke = 0xFFE1F5EB.toInt(), width = 4f, roundCap = true, dash = floatArrayOf(2f, 6f), layer = POP))
                add(Shape(circle(96f, 28f, 11f), fill = 0xFFFFFFFF.toInt(), layer = RISE))
                add(Shape(Path(), fill = 0xFF158245.toInt(), text = "?", tx = 96f, ty = 33f, size = 14f, layer = RISE))
            }
            Kind.BAD -> {
                add(Shape(circle(60f, 54f, 48f), fill = 0x29FFFFFF.toInt()))
                add(Shape(oval(60f, 102f, 34f, 5f), fill = 0x24000000.toInt()))
                add(Shape(PathParser.createPathFromPathData("M22 80a38 38 0 0 1 76 0"), stroke = 0xFFFFFFFF.toInt(), width = 14f, roundCap = true))
                add(Shape(PathParser.createPathFromPathData("M60 80 92 68"), stroke = 0xFF7A1F19.toInt(), width = 5f, roundCap = true, layer = POP))
                add(Shape(circle(60f, 80f, 7f), fill = 0xFF7A1F19.toInt(), layer = RISE))
                add(Shape(rect(44f, 88f, 32f, 12f, 6f), fill = 0xFFFFFFFF.toInt()))
                add(Shape(Path(), fill = 0xFFB3413D.toInt(), text = "100%", tx = 60f, ty = 97.5f, size = 9f, layer = STATIC))
            }
        }
    }

    private companion object {
        const val VIEW_W = 120f
        const val VIEW_H = 110f
        const val RISE_FROM = 16f
    }
}
