package com.viewgrow.app

import android.util.TypedValue
import android.widget.TextView
import androidx.core.widget.TextViewCompat

// Phone numbers and names must never be cut short on phones that use a big font size.
// Call this on a one-line TextView whose width is limited (0dp with a weight, or match_parent in a
// limited column): the text keeps its normal size when it fits and shrinks, down to minSp, when it does not.
// maxSp is the size the text normally has, so it never grows. (Idea from VG Contact.)
object TextFit {
    fun shrinkToFit(tv: TextView, maxSp: Int, minSp: Int = 10) {
        tv.maxLines = 1
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tv, minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP
        )
    }
}
