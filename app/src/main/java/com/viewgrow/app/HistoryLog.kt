package com.viewgrow.app

import java.util.Calendar
import java.util.Locale

// History reads everything from the database (web_history, web_day_contacts, web_people). Nothing about
// who was received is kept on the phone. This object only holds small helpers for showing numbers.
object HistoryLog {
    class Entry(val name: String, val number: String, val banned: Boolean = false)

    // One person with the day they were received.
    class Person(val name: String, val number: String, val last: String, val banned: Boolean = false)

    // Today as yyyy-MM-dd on the phone's clock.
    fun today(): String = DayText.iso(Calendar.getInstance())

    // Always with the country code (+234 803 411 2290, +1 201 555 0123), the same as everywhere else in the app.
    fun display(number: String): String = Phone.show(number)

    // The WhatsApp chat link for a number. A number starting with 0 is a Nigerian number.
    fun chatUrl(number: String): String? {
        val d = number.filter { it.isDigit() }
        if (d.isEmpty()) return null
        val full = if (d.startsWith("0")) "234" + d.substring(1) else d
        return "https://wa.me/$full"
    }
}

// Dates written the way the History screens show them.
object DayText {
    private val dayShort = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val monthShort = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun iso(c: Calendar): String =
        String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    fun parse(iso: String): Calendar? {
        if (iso.length < 10) return null
        return try {
            val c = Calendar.getInstance()
            c.clear()
            c.set(iso.substring(0, 4).toInt(), iso.substring(5, 7).toInt() - 1, iso.substring(8, 10).toInt())
            c
        } catch (e: Exception) { null }
    }

    // Mon 9 Nov 2026
    fun label(iso: String): String {
        val d = parse(iso) ?: return iso
        return dayShort[d.get(Calendar.DAY_OF_WEEK) - 1] + " " + d.get(Calendar.DAY_OF_MONTH) + " " +
            monthShort[d.get(Calendar.MONTH)] + " " + d.get(Calendar.YEAR)
    }

    // 9th Nov: the title of an opened day (no weekday, no year, so a bigger contacts badge never squeezes it).
    fun ordinal(iso: String): String {
        val d = parse(iso) ?: return iso
        val n = d.get(Calendar.DAY_OF_MONTH)
        val suffix = if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return n.toString() + suffix + " " + monthShort[d.get(Calendar.MONTH)]
    }

    // "Today", "Yesterday", or nothing.
    fun relative(day: String): String {
        val today = iso(Calendar.getInstance())
        val yesterday = iso(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) })
        return when (day) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> ""
        }
    }
}
