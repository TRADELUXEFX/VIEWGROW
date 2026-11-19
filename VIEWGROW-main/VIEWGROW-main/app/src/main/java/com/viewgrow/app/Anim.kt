package com.viewgrow.app

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

// The small animations (same ones as the website). All of them are skipped when the phone has
// animations turned off (Settings > Accessibility > Remove animations), so nothing moves for those members.
object Anim {
    fun on(ctx: Context): Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    private fun dp(v: View, n: Int) = n * v.resources.displayMetrics.density

    // A message banner slides in from the top with a little bounce. Its picture is a BannerPic: it animates its
    // own parts (the website does the same), so the whole picture no longer rises.
    fun bannerIn(banner: View) {
        if (!on(banner.context)) return
        banner.alpha = 0f
        banner.translationY = -dp(banner, 14)
        banner.scaleX = 0.98f
        banner.scaleY = 0.98f
        banner.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setStartDelay(200).setDuration(500).setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    // History: the cards come in one after another.
    fun cardsIn(list: ViewGroup) {
        if (!on(list.context)) return
        for (i in 0 until list.childCount) {
            val c = list.getChildAt(i)
            c.alpha = 0f
            c.translationY = dp(c, 16)
            c.animate().alpha(1f).translationY(0f)
                .setStartDelay(minOf(i, 8) * 70L).setDuration(350).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    // A number counting up from 0. The caller draws each step (so it can keep its own styling).
    fun countUp(ctx: Context, to: Int, ms: Long, draw: (Int) -> Unit): ValueAnimator? {
        if (!on(ctx) || to <= 0) { draw(to); return null }
        val a = ValueAnimator.ofInt(0, to)
        a.duration = ms
        a.interpolator = DecelerateInterpolator(1.6f)
        a.addUpdateListener { draw(it.animatedValue as Int) }
        a.start()
        return a
    }

    // The countdown: each second the digits drop in by a few pixels.
    fun tick(v: View) {
        if (!on(v.context)) return
        v.translationY = -dp(v, 3)
        v.alpha = 0.45f
        v.animate().translationY(0f).alpha(1f).setDuration(250).start()
    }
}
