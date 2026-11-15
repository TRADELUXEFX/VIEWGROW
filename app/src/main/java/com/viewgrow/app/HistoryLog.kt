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

    // One person across all the days this phone has a record of: how many days they were saved and the latest one.
    class Person(val name: String, val number: String, val days: Int, val last: String)

    private const val FILE = "viewgrow_history"
    private const val KEY = "days"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(ctx: Context): JSONObject {
        val s = prefs(ctx).getString(KEY, null) ?: return JSONObject()
        return try { JSONObject(s) } catch (e: Exception) { JSONObject() }
    }

    private fun key(number: String): String = number.filter { it.isDigit() }.takeLast(10)

    // Today as yyyy-MM-dd on the phone's clock.
    fun today(): String = DayText.iso(Calendar.getInstance())

    // Adds the contacts of one sync to that day. The member's own marker contact ("ViewGrow") is left out,
    // and a number already logged for the day is not added twice.
    @Synchronized
    fun add(ctx: Context, cards: List<ContactsSaver.Card>, day: String) {
        val all = read(ctx)
        val arr = all.optJSONArray(day) ?: JSONArray()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) seen.add(key(arr.getJSONObject(i).optString("p")))
        var added = 0
        for (c in cards) {
            if (c.name.equals("ViewGrow", ignoreCase = true)) continue
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
            out.add(Entry(o.optString("n"), o.optString("p")))
        }
        return out
    }

    // Every different person saved on this phone, newest first. from and to (yyyy-MM-dd) limit the days
    // looked at; null means no limit. Someone saved on several days is listed once.
    fun people(ctx: Context, from: String?, to: String?): List<Person> {
        class Acc(var name: String, var number: String, var days: Int, var last: String)
        val all = read(ctx)
        val map = LinkedHashMap<String, Acc>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            if (from != null && day < from) continue
            if (to != null && day > to) continue
            val arr = all.optJSONArray(day) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val number = o.optString("p")
                val k = key(number)
                if (k.isEmpty()) continue
                val name = o.optString("n")
                val a = map[k]
                if (a == null) {
                    map[k] = Acc(name, number, 1, day)
                } else {
                    a.days++
                    if (a.name.isBlank() && name.isNotBlank()) a.name = name
                    if (day > a.last) { a.last = day; a.number = number }
                }
            }
        }
        return map.values.map { Person(it.name, it.number, it.days, it.last) }
            .sortedWith(compareByDescending<Person> { it.last }.thenBy { it.name.lowercase() })
    }

    // True when someone saved on that day has a name or number containing the search.
    fun matches(ctx: Context, day: String, query: String): Boolean = entries(ctx, day).any { matches(it, query) }

    fun matches(e: Entry, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (e.name.contains(q, ignoreCase = true)) return true
        val digits = q.filter { it.isDigit() }
        return digits.isNotEmpty() && e.number.filter { it.isDigit() }.contains(digits)
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

    // 9 Nov
    fun short(iso: String?): String {
        val d = if (iso == null) null else parse(iso)
        return if (d == null) "" else d.get(Calendar.DAY_OF_MONTH).toString() + " " + monthShort[d.get(Calendar.MONTH)]
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
