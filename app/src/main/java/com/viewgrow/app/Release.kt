package com.viewgrow.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

// When the next batch of contacts is released. The database decides (web_next_release); the app only counts down.
object Release {
    private var nextAt = 0L          // milliseconds, 0 when not known

    fun load(done: () -> Unit) {
        Api.call("web_next_release", JSONObject()) { r ->
            val j = r.json
            if (j != null) {
                val at = parseTime(j.optString("next_release_at"))
                if (at > 0L) nextAt = at
            }
            done()
        }
    }

    fun clear() { nextAt = 0L }

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
