package com.viewgrow.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

// What this phone saved on each day: the name and number of every contact the sync put in the phone.
// The History day screen reads it. The database still decides who gets which contacts; this is only
// the phone's own record of what it received, kept so the member can see names and numbers per day.
object HistoryLog {
    class Entry(val name: String, val number: String)

    // One person across all the days this phone has a record of, with the latest day they were saved.
    class Person(val name: String, val number: String, val last: String)

    private const val FILE = "viewgrow_history"
    private const val KEY = "days"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(ctx: Context): JSONObject {
        val s = prefs(ctx).getString(KEY, null) ?: return JSONObject()
        return try { JSONObject(s) } catch (e: Exception) { JSONObject() }
    }

    private fun key(number: String): String = number.filter { it.isDigit() }.takeLast(10)

    // The member's own marker contact from the database: named "ViewGrow" or "ViewGrow <id>", with a
    // placeholder number (+234000 and the id). It is only there so Delete My Contacts can find the others.
    // It is never shown anywhere in History, so it is left out when writing and when reading.
    private val markerName = Regex("^ViewGrow(\\s+\\d+)?$", RegexOption.IGNORE_CASE)
    private fun isMarker(name: String, number: String): Boolean =
        markerName.matches(name.trim()) || number.filter { it.isDigit() }.startsWith("234000")

    // Today as yyyy-MM-dd on the phone's clock.
    fun today(): String = DayText.iso(Calendar.getInstance())

    // Adds the contacts of one sync to that day. The member's own marker contact ("ViewGrow" or "ViewGrow <id>") is left out,
    // and a number already logged for the day is not added twice.
    @Synchronized
    fun add(ctx: Context, cards: List<ContactsSaver.Card>, day: String) {
        val all = read(ctx)
        val arr = all.optJSONArray(day) ?: JSONArray()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) seen.add(key(arr.getJSONObject(i).optString("p")))
        var added = 0
        for (c in cards) {
            if (isMarker(c.name, c.number)) continue
            val k = key(c.number)
            if (k.isEmpty() || !seen.add(k)) continue
            arr.put(JSONObject().put("n", if (c.name == c.number) "" else c.name).put("p", c.number))
            added++
        }
        if (added == 0) return
        all.put(day, arr)
        prefs(ctx).edit().putString(KEY, all.toString()).apply()
    }

    fun entries(ctx: Context, day: String): List<Entry> {
        val arr = read(ctx).optJSONArray(day) ?: return emptyList()
        val out = ArrayList<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (isMarker(o.optString("n"), o.optString("p"))) continue
            out.add(Entry(o.optString("n"), o.optString("p")))
        }
        return out
    }

    // Every different person saved on this phone, newest first. Someone saved on several days is listed once.
    fun people(ctx: Context): List<Person> {
        class Acc(var name: String, var number: String, var last: String)
        val all = read(ctx)
        val map = LinkedHashMap<String, Acc>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val arr = all.optJSONArray(day) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val number = o.optString("p")
                val k = key(number)
                if (k.isEmpty()) continue
                val name = o.optString("n")
                if (isMarker(name, number)) continue
                val a = map[k]
                if (a == null) {
                    map[k] = Acc(name, number, day)
                } else {
                    if (a.name.isBlank() && name.isNotBlank()) a.name = name
                    if (day > a.last) { a.last = day; a.number = number }
                }
            }
        }
        return map.values.map { Person(it.name, it.number, it.last) }
            .sortedWith(compareByDescending<Person> { it.last }.thenBy { it.name.lowercase() })
    }

    // How many contacts this phone saved in the last `days` days, today included (for the weekly summary).
    fun countSince(ctx: Context, days: Int): Int {
        val all = read(ctx)
        val from = DayText.iso(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) })
        var n = 0
        val keys = all.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            if (day < from) continue
            val arr = all.optJSONArray(day) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (!isMarker(o.optString("n"), o.optString("p"))) n++
            }
        }
        return n
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    // 0803 411 2290 style for Nigerian numbers. Anything else is shown as it came.
    fun display(number: String): String {
        val d = number.filter { it.isDigit() }
        val local = when {
            d.startsWith("234") && d.length == 13 -> "0" + d.substring(3)
            d.length == 11 && d.startsWith("0") -> d
            else -> return number
        }
        return local.substring(0, 4) + " " + local.substring(4, 7) + " " + local.substring(7)
    }

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
