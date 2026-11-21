package com.viewgrow.app

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

// WhatsApp numbers: how the boxes format them while typing, and how they are shown.
// The database accepts both 0801 234 5678 and +234 801 234 5678 and stores 2348012345678.
// A number from another country must start with + and the country code (e.g. +447911123456); it is stored as digits.
object Phone {

    // Spaces are added as you type. Local: 0XXX XXX XXXX (11 digits). International: +234 XXX XXX XXXX (13 digits).
    fun format(raw: String): String {
        val plus = raw.trimStart().startsWith("+")
        var d = raw.filter { it.isDigit() }
        // Another country (+44..., +233...): kept as typed, up to 15 digits, no spaces (the country code length differs).
        if (plus && !d.startsWith("234")) return "+" + d.take(15)
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
        // With the 0 (0810 870 9629): groups of 4, 3, 4 and 11 digits. Without it (810 870 9629): groups of 3, 3, 4 and 10 digits.
        if (d.startsWith("0")) {
            d = d.take(11)
            val sb = StringBuilder(d.take(4))
            if (d.length > 4) sb.append(' ').append(d.substring(4, minOf(7, d.length)))
            if (d.length > 7) sb.append(' ').append(d.substring(7))
            return sb.toString()
        }
        d = d.take(10)
        val sb = StringBuilder(d.take(3))
        if (d.length > 3) sb.append(' ').append(d.substring(3, minOf(6, d.length)))
        if (d.length > 6) sb.append(' ').append(d.substring(6))
        return sb.toString()
    }

    // What the phone keeps and sends to the database. A Nigerian number stays as it is (0801..., 234801...). A number from
    // another country always gets its + back, because the database only accepts a foreign number that starts with +
    // (the database answers sign-up with plain digits, so without this every later call would be INVALID_PHONE).
    fun forServer(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty() || t.startsWith("+")) return t
        val d = t.filter { it.isDigit() }
        return if (d.length >= 11 && !d.startsWith("234") && !d.startsWith("0")) "+$d" else t
    }

    // The full number in digits, with its country code: 08012345678 -> 2348012345678, +1 201... -> 1201...
    // Empty when it cannot be told (too short).
    fun fullDigits(stored: String): String {
        val d = stored.filter { it.isDigit() }
        return when {
            d.length == 11 && d.startsWith("0") -> "234" + d.substring(1)
            d.length >= 11 && !d.startsWith("0") -> d
            else -> ""
        }
    }

    private var app: android.content.Context? = null
    private val util: io.michaelrocks.libphonenumber.android.PhoneNumberUtil? by lazy {
        app?.let { io.michaelrocks.libphonenumber.android.PhoneNumberUtil.createInstance(it) }
    }

    // Called once when the app opens, so numbers can be spaced the way each country writes them.
    fun init(ctx: android.content.Context) { if (app == null) app = ctx.applicationContext }

    // How a number is shown everywhere in the app: always with its country code, so it is clear where the person is from.
    // 2348012345678 or 0801 234 5678 -> +234 801 234 5678, 12015550123 -> +1 201 555 0123.
    // Anything that is not a full number is shown as it came.
    fun show(stored: String): String {
        // Already stored the way it is shown (+234 803 123 4567): shown exactly as the database has it.
        val raw = stored.trim()
        if (Regex("^\\+\\d+ \\d.*").matches(raw)) return raw
        val d = fullDigits(stored)
        if (d.isEmpty()) return stored.trim()
        if (d.startsWith("234") && d.length == 13) {
            return "+234 " + d.substring(3, 6) + " " + d.substring(6, 9) + " " + d.substring(9)
        }
        try {
            val u = util
            if (u != null) {
                val n = u.parse("+$d", null)
                val out = u.format(n, io.michaelrocks.libphonenumber.android.PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
                // Only spaces between the groups (no dashes or brackets), the same look as Nigerian numbers.
                return out.replace(Regex("[^0-9+]+"), " ").trim()
            }
        } catch (e: Exception) { }
        val c = Countries.forDigits(d) ?: return "+$d"
        val rest = d.substring(c.dial.length)
        val sb = StringBuilder("+").append(c.dial)
        var i = 0
        while (i < rest.length) {
            val len = if (rest.length - i <= 4) rest.length - i else 3
            sb.append(' ').append(rest, i, i + len)
            i += len
        }
        return sb.toString()
    }

    // A number in a people list: the country's flag in front, then the number (🇳🇬  +234 803 123 4567).
    // The country comes from the number itself; a number whose country cannot be told is shown without a flag.
    fun withFlag(stored: String): String {
        val shown = show(stored)
        val d = fullDigits(stored)
        if (d.isEmpty()) return shown
        val u = util
        val fromLib: String? = if (u == null) null else try {
            u.getRegionCodeForNumber(u.parse("+$d", null))?.takeIf { it.length == 2 && it != "ZZ" }
        } catch (e: Exception) { null }
        val iso = fromLib ?: Countries.forDigits(d)?.iso
        if (iso == null) return shown
        val flag = iso.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        return "$flag  $shown"
    }

    // For the number box on the Nigeria setting: 2348012345678 -> 0801 234 5678 (what a member types).
    fun local(stored: String): String {
        var d = stored.filter { it.isDigit() }
        if (stored.trim().startsWith("+") && !d.startsWith("234")) return stored.trim()
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
