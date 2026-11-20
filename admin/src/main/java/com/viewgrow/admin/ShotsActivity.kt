package com.viewgrow.admin

import android.net.Uri
import android.os.Bundle
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import kotlin.math.abs

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
        Ui.edgeToEdge(this)
        if (!Store.signedIn) { finish(); return }
        if (savedInstanceState == null) {
            showMessage("Choose screenshots of your status viewers list.", busy = false)
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else finish()
    }

    // ---------- Screens ----------

    private fun header() = Ui.hero(this, "Log", onBack = { finish() })

    private fun showMessage(msg: String, busy: Boolean, sub: String = "") {
        onResults = false
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
                    else -> { tab = 0; showResults() }
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

    // Always with the country code: +234 801 234 5678, +1 201 555 0123.
    private fun show(key: String) = MemberNumbers.show(applicationContext, key)

    // ---------- Check screen ----------
    // The green header holds "Log", the day (tap to change it) and three tabs: Ready, Check and Skipped.
    // Tap a tab or swipe left / right to move between them. The Log button stays at the bottom.

    private var tab = 0                 // 0 Ready, 1 Check, 2 Skipped
    private var onResults = false       // the check screen is showing (swipes change the tab)

    private val swipe by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (!onResults || e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) < Ui.dp(this@ShotsActivity, 80) || abs(dx) < abs(dy) * 1.5f) return false
                val next = if (dx < 0) tab + 1 else tab - 1
                if (next !in 0..2) return false
                tab = next
                showResults()
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (onResults) swipe.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun showResults() {
        val ready = rows.filter { it.kind == Kind.MATCHED }
        val check = rows.filter { it.kind == Kind.CHECK }
        val skipped = rows.filter { it.kind == Kind.NOT_MEMBER || it.kind == Kind.NOT_FOUND }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.color(this, R.color.surface))
        val header = resultsHeader(ready.size, check.size, skipped.size)
        root.addView(header)
        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.isVerticalScrollBarEnabled = false
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 20))
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll)

        when (tab) {
            0 -> readyTab(body, ready)
            1 -> checkTab(body, check)
            else -> skippedTab(body, skipped)
        }

        // The bottom bar: always there, whichever tab is open.
        val foot = LinearLayout(this)
        foot.orientation = LinearLayout.VERTICAL
        foot.setBackgroundColor(Ui.color(this, R.color.surface))
        foot.elevation = Ui.dp(this, 12).toFloat()
        foot.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 16))
        val hint = when {
            check.isNotEmpty() -> "${check.size} still to check. You can log without " + (if (check.size == 1) "it." else "them.")
            logDay == today -> "Log before 10pm so they get tonight’s contacts."
            else -> ""
        }
        if (hint.isNotEmpty()) foot.addView(Ui.text(this, hint, 12f, R.color.muted, center = true).also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = Ui.dp(this, 8)
        })
        val go = Ui.button(this, "", top = 0) { log() }
        goButton = go
        updateGo()
        foot.addView(go)
        root.addView(foot)

        Ui.fitInsets(root, header)
        onResults = true
        setContentView(root)
    }

    // The green header of the check screen.
    private fun resultsHeader(ready: Int, check: Int, skipped: Int): LinearLayout {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.color(this, R.color.hero_start), Ui.color(this, R.color.green), Ui.color(this, R.color.hero_end)))
        val r = Ui.dp(this, 32).toFloat()
        g.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        h.background = g
        h.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 16))
        h.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Back, "Log" on the left, the day on the right.
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val back = Ui.text(this, "‹", 26f, R.color.white, "bold", center = true)
        back.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40))
        back.background = Ui.shape(this, 0x33FFFFFF, 999)
        back.setOnClickListener { finish() }
        val title = Ui.text(this, "Log", 20f, R.color.white, "extrabold")
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(this, 12) }
        val day = Ui.text(this, dayLabel(logDay), 14f, R.color.green_dark, "bold", center = true)
        // A small calendar icon before the date.
        ContextCompat.getDrawable(this, R.drawable.ic_calendar)?.mutate()?.let { d ->
            d.setTint(Ui.color(this, R.color.green_dark))
            d.setBounds(0, 0, Ui.dp(this, 16), Ui.dp(this, 16))
            day.setCompoundDrawablesRelative(d, null, null, null)
            day.compoundDrawablePadding = Ui.dp(this, 6)
        }
        day.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        day.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7))
        day.background = Ui.shape(this, Ui.color(this, R.color.white), 999)
        day.setOnClickListener { pickDay() }
        row.addView(back); row.addView(title); row.addView(day)
        h.addView(row)

        // Ready / Check / Skipped.
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.background = Ui.shape(this, 0x24000000, 14)
        val p = Ui.dp(this, 4)
        tabs.setPadding(p, p, p, p)
        tabs.layoutParams = Ui.lp(this, 14)
        listOf("Ready $ready", "Check $check", "Skipped $skipped").forEachIndexed { i, label ->
            val s = SpannableStringBuilder(label)
            // An amber dot on Check while something is waiting there.
            if (i == 1 && check > 0) {
                val at = s.length
                s.append("  ●")
                s.setSpan(ForegroundColorSpan(0xFFFFC24A.toInt()), at, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val on = i == tab
            val t = Ui.text(this, s, 13f, if (on) R.color.green_dark else R.color.white, if (on) "bold" else "semibold", center = true)
            if (!on) t.alpha = 0.88f
            t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9))
            if (on) t.background = Ui.shape(this, Ui.color(this, R.color.white), 11)
            t.setOnClickListener { if (tab != i) { tab = i; showResults() } }
            tabs.addView(t)
        }
        h.addView(tabs)
        return h
    }

    private fun readyTab(body: LinearLayout, ready: List<Row>) {
        if (ready.isEmpty()) {
            body.addView(Ui.text(this, "Nobody is ready to log yet.", 14f, R.color.text2, center = true, top = 40))
            return
        }
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.layoutParams = Ui.lp(this, 0)
        val l = Ui.text(this, "Tick who to log", 13f, R.color.text2)
        l.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val all = ready.all { it.ticked }
        val t = Ui.text(this, if (all) "Untick all" else "Tick all", 12.5f, R.color.green_dark, "semibold")
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), 0, Ui.dp(this, 6))
        t.setOnClickListener { ready.forEach { it.ticked = !all }; showResults() }
        top.addView(l); top.addView(t)
        body.addView(top)
        val list = Ui.card(this, 8, 12)
        list.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0)
        ready.forEachIndexed { i, r ->
            if (i > 0) list.addView(divider())
            list.addView(rowView(r))
        }
        body.addView(list)
    }

    private fun checkTab(body: LinearLayout, check: List<Row>) {
        if (check.isEmpty()) {
            body.gravity = Gravity.CENTER_HORIZONTAL
            body.addView(Ui.text(this, "✓", 30f, R.color.green_dark, "bold", center = true).also {
                it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)).also { p -> p.topMargin = Ui.dp(this, 50) }
                it.background = Ui.shape(this, Ui.color(this, R.color.tint), 999)
                it.gravity = Gravity.CENTER
            })
            body.addView(Ui.text(this, "All checked", 18f, R.color.dark, "bold", center = true, top = 14))
            body.addView(Ui.text(this, "Nothing left to check.", 13.5f, R.color.text2, center = true, top = 6))
            return
        }
        body.addView(Ui.text(this, "Not sure who these are. Tap Choose to pick the right member.", 13f, R.color.text2))
        check.forEachIndexed { i, r ->
            val c = LinearLayout(this)
            c.orientation = LinearLayout.HORIZONTAL
            c.gravity = Gravity.CENTER_VERTICAL
            c.background = Ui.shape(this, Ui.color(this, R.color.amber_bg), 16, 0xFFF0D9A8.toInt())
            c.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14))
            c.layoutParams = Ui.lp(this, if (i == 0) 10 else 8)
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            col.addView(Ui.text(this, r.name + "?", 15f, R.color.dark, "semibold"))
            col.addView(Ui.text(this, r.how, 12f, R.color.amber_text, top = 3))
            val b = Ui.text(this, "Choose", 13f, R.color.white, "semibold", center = true)
            b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginStart = Ui.dp(this, 10) }
            b.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8))
            b.background = Ui.shape(this, Ui.color(this, R.color.amber_text), 999)
            c.addView(col); c.addView(b)
            c.setOnClickListener { choose(r) }
            b.setOnClickListener { choose(r) }
            body.addView(c)
        }
    }

    private fun skippedTab(body: LinearLayout, skipped: List<Row>) {
        if (skipped.isEmpty()) {
            body.addView(Ui.text(this, "Nobody was skipped.", 14f, R.color.text2, center = true, top = 40))
            return
        }
        body.addView(Ui.text(this, "These are not ViewGrow members, so they can’t be logged.", 13f, R.color.text2))
        val list = Ui.card(this, 10, 12)
        list.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0)
        skipped.forEachIndexed { i, r ->
            if (i > 0) list.addView(divider())
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 11))
            val x = Ui.text(this, "✕", 11f, R.color.red_dark, "bold", center = true)
            x.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22))
            x.gravity = Gravity.CENTER
            x.background = Ui.shape(this, Ui.color(this, R.color.red_bg), 999)
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(this, 10) }
            col.addView(Ui.text(this, "“" + r.name + "”", 14f, R.color.text2, "semibold"))
            col.addView(Ui.text(this, r.how, 11.5f, R.color.muted, top = 2))
            row.addView(x); row.addView(col)
            list.addView(row)
        }
        body.addView(list)
    }

    private fun divider() = View(this).also {
        it.setBackgroundColor(Ui.color(this, R.color.border))
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1))
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
        val options = r.candidates.map { Ui.Option(it.first, show(it.second)) } + Ui.Option("None of these", muted = true)
        Ui.choose(this, "Who is “${r.name}”?", "Pick the member this name belongs to.", options) { which ->
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
    }

    // The day to log for: a calendar, like on the admin website. Any day up to today.
    private fun pickDay() {
        Ui.pickDate(this, logDay, today) { d -> logDay = d; showResults() }
    }

    // ---------- 4. Log ----------

    private fun log() {
        val numbers = rows.filter { it.ticked && it.kind == Kind.MATCHED }.map { "234" + it.number }.distinct()
        if (numbers.isEmpty()) return
        onResults = false
        goButton?.let { Ui.setBusy(it, true, it.text.toString(), "Logging ${numbers.size}…") }
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
            onResults = false
            val (root, body) = Ui.page(this, header())
            body.addView(Ui.text(this, msg, 16f, R.color.red_dark, "semibold", center = true, top = 40))
            body.addView(Ui.button(this, "Try again", top = 20) { log() })
            body.addView(Ui.button(this, "Back to the list", fill = false, top = 10) { showResults() })
            setContentView(root)
            return
        }
        onResults = false
        val (root, body) = Ui.page(this, Ui.hero(this, "Logged", onBack = { finish() }))
        val card = Ui.card(this, 0, 24)
        card.gravity = Gravity.CENTER_HORIZONTAL
        val tick = Ui.text(this, "✓", 34f, R.color.green_dark, "bold", center = true).also {
            it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 80))
            it.background = Ui.shape(this, Ui.color(this, R.color.tint), 999)
            it.gravity = Gravity.CENTER
        }
        card.addView(tick)
        val loggedN = j.optInt("logged")
        val count = Ui.text(this, "$loggedN logged", 26f, R.color.dark, "extrabold", center = true, top = 14)
        card.addView(count)
        card.addView(Ui.text(this, "for " + dayLabel(logDay), 14f, R.color.text2, center = true, top = 6))
        body.addView(card)
        val info = Ui.card(this, 12, 14)
        info.addView(Ui.line(this, "Already logged that day", "${j.optInt("already")}", 0))
        info.addView(Ui.line(this, "You chose to skip", "${rows.count { !it.ticked && (it.kind == Kind.MATCHED || it.kind == Kind.CHECK) }}"))
        info.addView(Ui.line(this, "Not members", "${rows.count { it.kind == Kind.NOT_MEMBER || it.kind == Kind.NOT_FOUND }}"))
        val extra = listOf("capped" to "Limit full (not counted)", "banned" to "Banned (skipped)", "before_signup" to "Signed up after that day", "not_found" to "Not found by the database")
        extra.forEach { (k, label) -> val n = j.optJSONArray(k)?.length() ?: 0; if (n > 0) info.addView(Ui.line(this, label, "$n")) }
        body.addView(info)
        val done = Ui.button(this, "Done", top = 20) { finish() }
        body.addView(done)
        // A short "done" moment: the tick pops in, the number counts up, the rest slides up.
        if (android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f) {
            tick.scaleX = 0f; tick.scaleY = 0f
            count.text = "0 logged"
            tick.animate().scaleX(1f).scaleY(1f).setStartDelay(200).setDuration(420)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
            android.animation.ValueAnimator.ofInt(0, loggedN).apply {
                duration = 900; startDelay = 450
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { a -> count.text = "${a.animatedValue} logged" }
                start()
            }
            listOf<View>(info, done).forEach { v ->
                v.alpha = 0f; v.translationY = Ui.dp(this, 14).toFloat()
                v.animate().alpha(1f).translationY(0f).setStartDelay(900).setDuration(380).start()
            }
        }
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
