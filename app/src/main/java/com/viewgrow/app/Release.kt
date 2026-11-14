package com.viewgrow.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// When the next batch of contacts is released. The database decides (web_next_release); the app only counts down.
object Release {
    private var nextAt = 0L          // milliseconds, 0 when not known
    private var nextDay = ""         // yyyy-MM-dd
    private var daysAway = -1

    private val weekdays = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun load(done: () -> Unit) {
        Api.call("web_next_release", JSONObject()) { r ->
            val j = r.json
            if (j != null) {
                val at = parseTime(j.optString("next_release_at"))
                if (at > 0L) {
                    nextAt = at
                    nextDay = j.optString("next_release_day")
                    daysAway = j.optInt("days_away", -1)
                }
            }
            done()
        }
    }

    fun clear() { nextAt = 0L; nextDay = ""; daysAway = -1 }

    // True when the release time the database gave has passed, so the status should be fetched again.
    fun expired(): Boolean = nextAt > 0L && nextAt <= System.currentTimeMillis()

    // Seconds to the next release. If the database has not answered, counts to the next 10pm Lagos time
    // (Lagos is UTC+1 and the day rolls at 10pm, so +3 hours), the same fallback as the website.
    fun secondsLeft(): Long {
        val now = System.currentTimeMillis()
        if (nextAt > now) return (nextAt - now) / 1000L
        val t = now + 10800000L
        return ((t / 86400000L + 1L) * 86400000L - t) / 1000L
    }

    fun format(left: Long): String {
        val h = left / 3600L
        val m = (left % 3600L) / 60L
        val s = left % 60L
        fun two(x: Long) = if (x < 10) "0$x" else x.toString()
        return two(h) + ":" + two(m) + ":" + two(s)
    }

    // "tonight at 10:00 pm", "tomorrow at 10:00 pm", or "Friday 20 Nov at 10:00 pm".
    fun whenText(): String {
        if (daysAway == 0) return "tonight at 10:00 pm"
        if (daysAway == 1) return "tomorrow at 10:00 pm"
        if (daysAway < 0 || nextDay.length < 10) return "at 10:00 pm"
        return try {
            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            c.clear()
            c.set(nextDay.substring(0, 4).toInt(), nextDay.substring(5, 7).toInt() - 1, nextDay.substring(8, 10).toInt())
            weekdays[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + " " + months[c.get(Calendar.MONTH)] + " at 10:00 pm"
        } catch (e: Exception) {
            "at 10:00 pm"
        }
    }

    // Reads a timestamp like 2026-11-16T21:00:00+00:00, with or without fractions, "Z" or a short offset.
    private fun parseTime(raw: String): Long {
        if (raw.isEmpty()) return 0L
        var s = raw.trim().replace(' ', 'T').replace(Regex("\\.\\d+"), "")
        if (Regex("[+-]\\d\\d$").containsMatchIn(s)) s += ":00"
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            f.parse(s)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

// The last status the database sent, so Home can show it at once when you come back to the tab.
object Status {
    var last: JSONObject? = null
}
