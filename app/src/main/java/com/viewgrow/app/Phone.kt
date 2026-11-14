package com.viewgrow.app

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

// WhatsApp numbers: how the boxes format them while typing, and how they are shown.
// The database accepts both 0801 234 5678 and +234 801 234 5678 and stores 2348012345678.
object Phone {

    // Spaces are added as you type. Local: 0XXX XXX XXXX (11 digits). International: +234 XXX XXX XXXX (13 digits).
    fun format(raw: String): String {
        val plus = raw.trimStart().startsWith("+")
        var d = raw.filter { it.isDigit() }
        if (plus || d.startsWith("234")) {
            d = d.take(13)
            val cc = d.take(3)
            val rest = d.drop(3)
            val sb = StringBuilder()
            if (plus) sb.append('+')
            sb.append(cc)
            if (rest.isNotEmpty()) {
                sb.append(' ').append(rest.take(3))
                if (rest.length > 3) sb.append(' ').append(rest.substring(3, minOf(6, rest.length)))
                if (rest.length > 6) sb.append(' ').append(rest.substring(6))
            }
            return sb.toString()
        }
        d = d.take(11)
        val sb = StringBuilder(d.take(4))
        if (d.length > 4) sb.append(' ').append(d.substring(4, minOf(7, d.length)))
        if (d.length > 7) sb.append(' ').append(d.substring(7))
        return sb.toString()
    }

    // 2348012345678 -> 0801 234 5678. Anything that is not a Nigerian number is shown as it is.
    fun show(stored: String): String {
        var d = stored.filter { it.isDigit() }
        if (d.startsWith("234") && d.length == 13) d = "0" + d.substring(3)
        return if (d.length == 11) d.substring(0, 4) + " " + d.substring(4, 7) + " " + d.substring(7) else stored
    }

    // Makes a number box add its spaces as the member types.
    fun attach(box: EditText) {
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val now = s.toString()
                val out = format(now)
                if (out != now) s.replace(0, s.length, out)
            }
        })
    }
}
