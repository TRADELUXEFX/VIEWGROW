package com.viewgrow.app

// The in-app text pages. The wording is added in the last update.
object Pages {
    const val TERMS = 0
    const val PRIVACY = 1

    fun title(kind: Int): String = if (kind == PRIVACY) "Privacy Policy" else "Terms and Conditions"

    fun body(kind: Int): String =
        if (kind == PRIVACY) "The Privacy Policy will be added in a later update."
        else "The Terms and Conditions will be added in a later update."
}
