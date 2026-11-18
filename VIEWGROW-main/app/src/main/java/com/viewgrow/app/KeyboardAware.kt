package com.viewgrow.app

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

// Sign up and Log in have a big green header. When the keyboard opens it takes most of the screen,
// so this shrinks the header while the keyboard is up and puts it back exactly as it was when it
// closes. It listens on the screen's own view, so it stops by itself when the screen goes away.
// Pair it with android:windowSoftInputMode="adjustResize" (MainActivity already has it).
// (Idea from VG Contact.)
object KeyboardAware {

    // Calls onChange(true) when the keyboard opens and onChange(false) when it closes.
    fun watch(root: View, onChange: (Boolean) -> Unit) {
        var open = false
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val visible = Rect()
            root.getWindowVisibleDisplayFrame(visible)
            val screen = root.rootView.height
            val nowOpen = screen > 0 && (screen - visible.bottom) > screen * 0.15f
            if (nowOpen != open) {
                open = nowOpen
                onChange(nowOpen)
            }
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        })
    }

    // Hides the header's contents and trims its bottom padding while the keyboard is open.
    // The top padding stays, because it is what fills the space behind the phone's status bar.
    fun compactHeader(root: View, hero: ViewGroup) {
        val kids = (0 until hero.childCount).map { hero.getChildAt(it) }
        val saved = HashMap<View, Int>()
        var bottom = hero.paddingBottom
        watch(root) { open ->
            if (open) {
                bottom = hero.paddingBottom
                kids.forEach { saved[it] = it.visibility; it.visibility = View.GONE }
                hero.setPadding(hero.paddingLeft, hero.paddingTop, hero.paddingRight, Ui.dp(hero.context, 12))
            } else {
                kids.forEach { it.visibility = saved[it] ?: View.VISIBLE }
                saved.clear()
                hero.setPadding(hero.paddingLeft, hero.paddingTop, hero.paddingRight, bottom)
            }
        }
    }
}
