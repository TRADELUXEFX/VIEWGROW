package com.viewgrow.admin

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

// Log from screenshots.
//   1. Pick: the phone's photo picker (no storage permission needed).
//   2. Read: the text in each screenshot is read on the phone (ML Kit). Only the rows that say "Reshared" count.
//      The row reader is the one tested in VG Reshare (copied, not shared).
//   3. Check: each name is matched to a member:
//        - a number on the screenshot (someone not saved in the phone) is checked against the member list;
//        - a name you matched before is remembered;
//        - otherwise the name is looked up in the phone contacts (whatever name you saved them under, for example
//          "Team Limitless") and the number decides whether it is a member;
//        - a name not in the phone is compared with the member names (small reading mistakes are allowed; unsure ones
//          are left for you to decide).
//      Ticked = sure. Unticked = you decide (tap to choose). Not members are skipped.
//   4. Log: the same database functions as the website's Log reshares, for the day you pick (today by default).
class ShotsActivity : AppCompatActivity() {

    private enum class Kind { MATCHED, CHECK, NOT_MEMBER, NOT_FOUND }

    private class Row(
        val name: String,
        var kind: Kind,
        var number: String = "",                          // number key (10 digits) of the member
        var member: String = "",                          // the member's name in ViewGrow
        var how: String = "",                             // how it was matched, in plain words
        var candidates: List<Pair<String, String>> = emptyList(), // (member name, number key) to choose from
        var ticked: Boolean = false
    )

    private val io = Executors.newSingleThreadExecutor()
    private val rows = ArrayList<Row>()
    private var shotCount = 0
    private var today = ""            // the database's day (it rolls at 10pm Lagos)
    private var logDay = ""

    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNullOrEmpty()) finish() else read(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        if (!Store.signedIn) { finish(); return }
        if (savedInstanceState == null) {
            showMessage("Choose the screenshots of your status viewers list.", busy = false)
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else finish()
    }

    // ---------- Screens ----------

    private fun header() = Ui.hero(this, "Log from screenshots", onBack = { finish() })

    private fun showMessage(msg: String, busy: Boolean, sub: String = "") {
        val (root, body) = Ui.page(this, header())
        body.gravity = Gravity.CENTER_HORIZONTAL
        if (busy) body.addView(Ui.spinner(this).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(this, 60) })
        body.addView(Ui.text(this, msg, 18f, R.color.dark, "bold", center = true, top = 18))
        if (sub.isNotEmpty()) body.addView(Ui.text(this, sub, 13.5f, R.color.text2, center = true, top = 8))
        if (!busy) {
            body.addView(Ui.button(this, "Choose screenshots", top = 28) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
        setContentView(root)
    }

    // ---------- 2. Read ----------

    private fun read(uris: List<Uri>) {
        shotCount = uris.size
        showMessage("Reading ${uris.size} screenshot" + (if (uris.size == 1) "" else "s") + "…", busy = true, sub = "This happens on your phone. Nothing is uploaded.")
        val app = applicationContext
        io.execute {
            // The member list must be there to tell members from other contacts.
            if (MemberList.all(app).isEmpty()) MemberSync.run(app, full = true)
            val stats = Api.admin("web_admin_stats").json
            today = stats?.optString("today").orEmpty().ifEmpty { lagosDay() }
            logDay = today
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val found = ArrayList<String>()
            var failed = 0
            for (u in uris) {
                try {
                    val res = Tasks.await(recognizer.process(InputImage.fromFilePath(app, u)))
                    val lines = res.textBlocks.flatMap { it.lines }
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        .map { it.text }
                    parse(lines).forEach { n -> if (found.none { it.equals(n, true) }) found.add(n) }
                } catch (e: Exception) {
                    failed++
                }
            }
            recognizer.close()
            val matched = found.map { match(it) }
            runOnUiThread {
                rows.clear()
                // One row per member (the first time they appear); unknown names once each.
                val seen = HashSet<String>()
                matched.forEach { r -> if (r.number.isEmpty() || seen.add(r.number)) rows.add(r) }
                when {
                    failed == uris.size -> showMessage("Couldn't read the screenshots.", busy = false, sub = "If this is your first time, your phone may still be downloading the text reader from Google Play. Wait a minute with internet on, then try again.")
                    rows.isEmpty() -> showMessage("No reshares found.", busy = false, sub = "Screenshot your status → viewers list, where rows say “Reshared”.")
                    else -> showResults()
                }
            }
        }
    }

    private val reshareRow = Regex("(\\d{1,2}):(\\d{2}).*reshared", RegexOption.IGNORE_CASE)
    private val agoRow = Regex("(\\d+)\\s*(second|minute|hour)s?\\s*ago.*reshared", RegexOption.IGNORE_CASE)
    private val nowRow = Regex("(just now|\\bnow\\b).*reshared", RegexOption.IGNORE_CASE)

    // The names on the "time • Reshared" rows. The name is on the same line before the time, or on the nearest
    // line above (up to two lines) that is not a screen label. Same reader as VG Reshare.
    private fun parse(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        for (i in lines.indices) {
            if (!isRow(lines[i])) continue
            val name = nameBefore(lines[i])
                ?: (i - 1 downTo maxOf(0, i - 2)).map { lines[it].trim() }.firstOrNull { it.isNotEmpty() && !isLabel(it) && !isRow(it) }
                ?: continue
            val clean = cleanName(name)
            if (clean.isNotEmpty()) out.add(clean)
        }
        return out
    }

    private fun isRow(line: String) = reshareRow.containsMatchIn(line) || agoRow.containsMatchIn(line) || nowRow.containsMatchIn(line)

    private fun nameBefore(line: String): String? {
        val m = listOf(reshareRow, agoRow, nowRow).mapNotNull { it.find(line) }.minByOrNull { it.range.first } ?: return null
        val before = line.substring(0, m.range.first).trim().trimEnd('•', '*', '·', '-', ':', '&', ',').trim()
            .replace(Regex("(?i)\\b(today|yesterday)\\s*$"), "").trim().trimEnd(',').trim()
        return before.takeIf { it.length >= 2 && !isLabel(it) }
    }

    // Removes stray letters the reader adds at the end of a name, e.g. "theelimitlez a" -> "theelimitlez".
    private fun cleanName(raw: String): String {
        val tokens = raw.trim().split(Regex("\\s+")).toMutableList()
        while (tokens.size > 1 && tokens.last().length <= 2 && tokens.last().all { it.isLowerCase() } && tokens[tokens.size - 2].length >= 3) {
            tokens.removeAt(tokens.size - 1)
        }
        return tokens.joinToString(" ").trim { !it.isLetterOrDigit() && it != '+' }
    }

    private fun isLabel(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return listOf("views not available", "my status", "reshared", "viewed by", "today", "yesterday").any { t.contains(it) }
    }

    // ---------- 3. Match ----------

    private fun match(name: String): Row {
        val app = applicationContext
        val members = MemberList.all(app)
        // A number on the screenshot: someone you have not saved.
        val digits = name.filter { it.isDigit() }
        if (digits.length >= 10 && name.count { it.isLetter() } == 0) {
            val k = numberKey(digits)
            val m = members[k]
            return if (m != null) Row(name, Kind.MATCHED, k, m, "Number on the screenshot", ticked = true)
            else Row(name, Kind.NOT_MEMBER, how = "This number is not a ViewGrow member")
        }
        // A name you matched by hand before.
        Store.rememberedPick(name)?.let { k ->
            members[k]?.let { return Row(name, Kind.MATCHED, k, it, "You chose this before", ticked = true) }
        }
        // The name as saved in your phone. The number decides who it is.
        val numbers = PhoneContacts.numbersFor(app, name)
        if (numbers.isNotEmpty()) {
            val hits = numbers.map { numberKey(it) }.distinct().filter { members.containsKey(it) }
            return when {
                hits.size == 1 -> {
                    val k = hits[0]
                    val m = members[k] ?: ""
                    val how = if (sameName(name, m)) "Saved in your phone" else "Saved in your phone as “$name” (${show(k)})"
                    Row(name, Kind.MATCHED, k, m, how, ticked = true)
                }
                hits.size > 1 -> Row(name, Kind.CHECK, how = "More than one member saved under this name",
                    candidates = hits.map { (members[it] ?: it) to it })
                else -> Row(name, Kind.NOT_MEMBER, how = "In your phone, but not a ViewGrow member")
            }
        }
        // Not in the phone: compare with the member names.
        val want = norm(name)
        val exact = members.filter { norm(it.value) == want }
        if (exact.size == 1) {
            val e = exact.entries.first()
            return Row(name, Kind.MATCHED, e.key, e.value, "Same name as a member", ticked = true)
        }
        if (exact.size > 1) return Row(name, Kind.CHECK, how = "Several members have this name",
            candidates = exact.map { it.value to it.key })
        if (want.length >= 4) {
            val close = members.entries.map { it to distance(want, norm(it.value)) }
                .filter { it.second <= maxOf(1, want.length / 5) }
                .sortedBy { it.second }
            if (close.size == 1 && close[0].second <= 1 && want.length >= 6) {
                val e = close[0].first
                return Row(name, Kind.MATCHED, e.key, e.value, "Closest member (read as “$name”)", ticked = true)
            }
            if (close.isNotEmpty()) return Row(name, Kind.CHECK, how = "Read as “$name”. Is it one of these?",
                candidates = close.take(3).map { it.first.value to it.first.key })
        }
        return Row(name, Kind.NOT_FOUND, how = "Not in your phone and not a member name")
    }

    // Lower case, letters and digits only, without the VG ending (the reader sometimes sees "V6" or "VC").
    private fun norm(s: String): String =
        s.lowercase(Locale.ROOT).replace(Regex("\\s+v[g6c9q]\\s*$"), "").filter { it.isLetterOrDigit() }

    private fun sameName(a: String, b: String) = norm(a) == norm(b)

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 3) return 99
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    private fun show(key: String) = if (key.length == 10) "0" + key.substring(0, 3) + " " + key.substring(3, 6) + " " + key.substring(6) else key

    // ---------- Check screen ----------

    private fun showResults() {
        val (root, body) = Ui.page(this, header())
        val matched = rows.count { it.kind == Kind.MATCHED }
        val check = rows.count { it.kind == Kind.CHECK }
        val skipped = rows.count { it.kind == Kind.NOT_MEMBER || it.kind == Kind.NOT_FOUND }
        body.addView(Ui.text(this, "$shotCount screenshot" + (if (shotCount == 1) "" else "s") + " read. Tick who to log.", 13f, R.color.text2))

        // The day to log for. Today (the database's day) unless you change it.
        val dayRow = LinearLayout(this)
        dayRow.orientation = LinearLayout.HORIZONTAL
        dayRow.gravity = Gravity.CENTER_VERTICAL
        dayRow.background = Ui.shape(this, Ui.color(this, R.color.tint), 12)
        dayRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10))
        dayRow.layoutParams = Ui.lp(this, 12)
        val l = Ui.text(this, "Log for", 13.5f, R.color.text)
        l.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val d = Ui.text(this, dayLabel(logDay) + " ▾", 13.5f, R.color.green_dark, "bold")
        d.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dayRow.addView(l); dayRow.addView(d)
        dayRow.setOnClickListener { pickDay() }
        body.addView(dayRow)

        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        tiles.layoutParams = Ui.lp(this, 12)
        tiles.addView(tile("$matched", "Matched", R.color.tint, R.color.green_dark))
        tiles.addView(tile("$check", "Check", R.color.amber_bg, R.color.amber_text))
        tiles.addView(tile("$skipped", "Not members", R.color.red_bg, R.color.red_dark))
        body.addView(tiles)

        val list = Ui.card(this, 12, 12)
        val order = rows.sortedBy { when (it.kind) { Kind.CHECK -> 0; Kind.MATCHED -> 1; else -> 2 } }
        order.forEachIndexed { i, r ->
            if (i > 0) list.addView(View(this).also {
                it.setBackgroundColor(Ui.color(this, R.color.border))
                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1))
            })
            list.addView(rowView(r))
        }
        body.addView(list)

        val go = Ui.button(this, "", top = 16) { log() }
        goButton = go
        updateGo()
        body.addView(go)
        if (logDay == today) body.addView(Ui.text(this, "Log before 10pm so they get tonight’s contacts.", 12f, R.color.muted, center = true, top = 10))
        setContentView(root)
    }

    private var goButton: TextView? = null

    // "Log 21 for Mon 16 Nov", updated as boxes are ticked.
    private fun updateGo() {
        val b = goButton ?: return
        val n = rows.count { it.ticked && it.kind == Kind.MATCHED }
        b.text = "Log $n for " + dayLabel(logDay)
        b.isEnabled = n > 0
        b.alpha = if (n > 0) 1f else 0.5f
    }

    private fun tile(n: String, label: String, bg: Int, fg: Int): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.background = Ui.shape(this, Ui.color(this, bg), 12)
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10))
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = Ui.dp(this, 6) }
        t.addView(Ui.text(this, n, 20f, fg, "extrabold", center = true))
        t.addView(Ui.text(this, label, 11.5f, fg, "semibold", center = true, top = 2))
        return t
    }

    private fun rowView(r: Row): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10))
        row.layoutParams = Ui.lp(this, 0)
        val box = CheckBox(this)
        box.isChecked = r.ticked
        box.isEnabled = r.kind == Kind.MATCHED || r.kind == Kind.CHECK
        box.buttonTintList = android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.green))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(this, 6) }
        val title = when (r.kind) {
            Kind.MATCHED -> r.member
            Kind.CHECK -> r.name + "?"
            else -> "“" + r.name + "”"
        }
        val sub = when (r.kind) {
            Kind.MATCHED -> r.how
            Kind.CHECK -> r.how + " Tap to choose."
            Kind.NOT_MEMBER -> r.how + ": skipped"
            Kind.NOT_FOUND -> r.how + ": skipped"
        }
        col.addView(Ui.text(this, title, 14f, if (r.kind == Kind.MATCHED || r.kind == Kind.CHECK) R.color.dark else R.color.muted, "semibold"))
        col.addView(Ui.text(this, sub, 11.5f, if (r.kind == Kind.CHECK) R.color.amber_text else R.color.muted, top = 2))
        row.addView(box); row.addView(col)
        box.setOnCheckedChangeListener { _, on ->
            if (r.kind == Kind.CHECK && on) { box.isChecked = false; choose(r) } else { r.ticked = on; updateGo() }
        }
        if (r.kind == Kind.CHECK) row.setOnClickListener { choose(r) }
        return row
    }

    // "Is it one of these?": the admin picks the member. The choice is remembered for this name.
    private fun choose(r: Row) {
        val labels = r.candidates.map { it.first + "  ·  " + show(it.second) } + "None of these"
        AlertDialog.Builder(this)
            .setTitle("Who is “${r.name}”?")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < r.candidates.size) {
                    val (m, k) = r.candidates[which]
                    // Already in the list as someone else: just remove this row's doubt.
                    if (rows.any { it !== r && it.number == k }) rows.remove(r) else {
                        r.kind = Kind.MATCHED; r.number = k; r.member = m; r.how = "You chose this"; r.ticked = true
                    }
                    Store.rememberPick(r.name, k)
                } else {
                    r.kind = Kind.NOT_FOUND; r.how = "You said none of these"; r.ticked = false
                }
                showResults()
            }
            .show()
    }

    private fun pickDay() {
        val days = (0..6).map { addDays(today, -it) }
        AlertDialog.Builder(this)
            .setTitle("Log for which day?")
            .setItems(days.mapIndexed { i, s -> dayLabel(s) + if (i == 0) "  (today)" else "" }.toTypedArray()) { _, which ->
                logDay = days[which]; showResults()
            }
            .show()
    }

    // ---------- 4. Log ----------

    private fun log() {
        val numbers = rows.filter { it.ticked && it.kind == Kind.MATCHED }.map { "234" + it.number }.distinct()
        if (numbers.isEmpty()) return
        showMessage("Logging ${numbers.size}…", busy = true)
        io.execute {
            // The day may have changed since the screen opened (10pm passed): ask again, so the right function is used.
            val now = Api.admin("web_admin_stats").json?.optString("today").orEmpty().ifEmpty { today }
            val args = JSONObject().put("p_ids", JSONArray()).put("p_numbers", JSONArray(numbers))
            val r = if (logDay == now) Api.admin("web_admin_log_verified", args)
            else Api.admin("web_admin_log_verified_day", args.put("p_day", logDay))
            runOnUiThread { done(r, numbers.size) }
        }
    }

    private fun done(r: ApiResult, sent: Int) {
        val j = r.json
        if (j == null || !j.optBoolean("ok")) {
            val msg = when {
                j == null -> Api.netMessage(r.error)
                Api.keyRefused(j) -> "The admin key was refused. Open the app and sign in again."
                j.optString("code") == "INVALID_DAY" -> "That day cannot be logged."
                else -> "Could not log."
            }
            val (root, body) = Ui.page(this, header())
            body.addView(Ui.text(this, msg, 16f, R.color.red_dark, "semibold", center = true, top = 40))
            body.addView(Ui.button(this, "Try again", top = 20) { log() })
            body.addView(Ui.button(this, "Back to the list", fill = false, top = 10) { showResults() })
            setContentView(root)
            return
        }
        val (root, body) = Ui.page(this, Ui.hero(this, "Logged", onBack = { finish() }))
        val card = Ui.card(this, 0, 24)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(Ui.text(this, "✓", 34f, R.color.green_dark, "bold", center = true).also {
            it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 80))
            it.background = Ui.shape(this, Ui.color(this, R.color.tint), 999)
            it.gravity = Gravity.CENTER
        })
        card.addView(Ui.text(this, "${j.optInt("logged")} logged", 26f, R.color.dark, "extrabold", center = true, top = 14))
        card.addView(Ui.text(this, "for " + dayLabel(logDay), 14f, R.color.text2, center = true, top = 6))
        body.addView(card)
        val info = Ui.card(this, 12, 14)
        info.addView(Ui.line(this, "Already logged that day", "${j.optInt("already")}", 0))
        info.addView(Ui.line(this, "You chose to skip", "${rows.count { !it.ticked && (it.kind == Kind.MATCHED || it.kind == Kind.CHECK) }}"))
        info.addView(Ui.line(this, "Not members", "${rows.count { it.kind == Kind.NOT_MEMBER || it.kind == Kind.NOT_FOUND }}"))
        val extra = listOf("capped" to "Limit full (not counted)", "banned" to "Banned (skipped)", "before_signup" to "Signed up after that day", "not_found" to "Not found by the database")
        extra.forEach { (k, label) -> val n = j.optJSONArray(k)?.length() ?: 0; if (n > 0) info.addView(Ui.line(this, label, "$n")) }
        body.addView(info)
        body.addView(Ui.button(this, "Done", top = 20) { finish() })
        body.addView(Ui.button(this, "Log more screenshots", fill = false, top = 10) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        setContentView(root)
    }

    // ---------- Days ----------

    private fun lagosDay(): String {
        // The database day rolls at 10pm Lagos: 10pm-midnight already counts as the next day.
        val c = Calendar.getInstance(TimeZone.getTimeZone("Africa/Lagos"))
        if (c.get(Calendar.HOUR_OF_DAY) >= 22) c.add(Calendar.DAY_OF_YEAR, 1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Africa/Lagos") }.format(c.time)
    }

    private fun addDays(day: String, n: Int): String = try {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.time = f.parse(day)!!
        c.add(Calendar.DAY_OF_YEAR, n)
        f.format(c.time)
    } catch (e: Exception) { day }

    private fun dayLabel(day: String): String = try {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        SimpleDateFormat("EEE d MMM", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(f.parse(day)!!)
    } catch (e: Exception) { day }
}
