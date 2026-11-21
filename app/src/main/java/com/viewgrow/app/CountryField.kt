package com.viewgrow.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

// Every country with its flag and calling code. Names come from the phone (in English), flags are made from the country code.
// All the "+1" countries (USA, Canada, Caribbean) share the code 1: the member types the area code and number.
object Countries {
    class C(val iso: String, val dial: String) {
        val name: String = nameOf(iso)
        val flag: String = flagOf(iso)
    }

    private const val RAW = "NG:234,GH:233,AF:93,AL:355,DZ:213,AD:376,AO:244,AR:54,AM:374,AW:297,AU:61,AT:43,AZ:994," +
        "BH:973,BD:880,BY:375,BE:32,BZ:501,BJ:229,BT:975,BO:591,BA:387,BW:267,BR:55,BN:673,BG:359,BF:226,BI:257," +
        "KH:855,CM:237,CV:238,CF:236,TD:235,CL:56,CN:86,CO:57,KM:269,CG:242,CD:243,CK:682,CR:506,CI:225,HR:385," +
        "CU:53,CW:599,CY:357,CZ:420,DK:45,DJ:253,EC:593,EG:20,SV:503,GQ:240,ER:291,EE:372,SZ:268,ET:251,FK:500," +
        "FO:298,FJ:679,FI:358,FR:33,GF:594,PF:689,GA:241,GM:220,GE:995,DE:49,GI:350,GR:30,GL:299,GP:590,GT:502," +
        "GN:224,GW:245,GY:592,HT:509,HN:504,HK:852,HU:36,IS:354,IN:91,ID:62,IQ:964,IE:353,IL:972,IT:39,JP:81," +
        "JO:962,KZ:7,KE:254,KI:686,XK:383,KW:965,KG:996,LA:856,LV:371,LB:961,LS:266,LR:231,LY:218,LI:423,LT:370," +
        "LU:352,MO:853,MG:261,MW:265,MY:60,MV:960,ML:223,MT:356,MH:692,MQ:596,MR:222,MU:230,YT:262,MX:52,FM:691," +
        "MD:373,MC:377,MN:976,ME:382,MA:212,MZ:258,MM:95,NA:264,NR:674,NP:977,NL:31,NC:687,NZ:64,NI:505,NE:227," +
        "NU:683,KP:850,MK:389,NO:47,OM:968,PK:92,PW:680,PS:970,PA:507,PG:675,PY:595,PE:51,PH:63,PL:48,PT:351," +
        "QA:974,RE:262,RO:40,RU:7,RW:250,WS:685,SM:378,ST:239,SA:966,SN:221,RS:381,SC:248,SL:232,SG:65,SK:421," +
        "SI:386,SB:677,SO:252,ZA:27,KR:82,SS:211,ES:34,LK:94,SD:249,SR:597,SE:46,CH:41,SY:963,TW:886,TJ:992," +
        "TZ:255,TH:66,TL:670,TG:228,TK:690,TO:676,TN:216,TR:90,TM:993,TV:688,UG:256,UA:380,AE:971,GB:44,UY:598," +
        "UZ:998,VU:678,VA:379,VE:58,VN:84,WF:681,YE:967,ZM:260,ZW:263," +
        "US:1,CA:1,AS:1,AI:1,AG:1,BS:1,BB:1,BM:1,VG:1,KY:1,DM:1,DO:1,GD:1,GU:1,JM:1,MS:1,MP:1,PR:1,KN:1,LC:1," +
        "VC:1,SX:1,TT:1,TC:1,VI:1,AX:358,BL:590,MF:590,PM:508,SH:290,JE:44,GG:44,IM:44,SJ:47,EH:212,NF:672,CX:61,CC:61,BQ:599"

    private fun nameOf(iso: String): String {
        val n = Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.ENGLISH)
        return if (n.isNullOrEmpty() || n == iso) iso else n
    }

    // NG -> the Nigerian flag, made from two regional indicator letters.
    private fun flagOf(iso: String): String =
        iso.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    // A to Z by name, accents ignored (Åland sits with the A's).
    private val collator: Collator = Collator.getInstance(Locale.ENGLISH).apply { strength = Collator.PRIMARY }

    val alpha: List<C> by lazy {
        RAW.split(",").map { val p = it.split(":"); C(p[0], p[1]) }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    // Nigeria first, then A to Z.
    val all: List<C> by lazy {
        val ng = alpha.first { it.iso == "NG" }
        listOf(ng) + alpha.filter { it !== ng }
    }

    val nigeria: C get() = all.first()

    // The letter a country is filed under in the list (Åland -> A).
    fun letter(c: C): Char {
        val ch = Normalizer.normalize(c.name, Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: '#'
        return if (ch in 'A'..'Z') ch else '#'
    }

    // Countries that share a calling code: the main one wins when a saved number is read back.
    private val preferred = setOf("US", "RU", "GB", "FI", "NO", "MA", "AU", "GP", "CW")

    // The country for a full number's digits (longest calling code wins; 1 is USA and 7 is Russia).
    fun forDigits(digits: String): C? =
        all.filter { digits.startsWith(it.dial) }
            .sortedWith(compareByDescending<C> { it.dial.length }.thenBy { if (it.iso in preferred) 0 else 1 })
            .firstOrNull()
}

// The number box with a country button in front of it (flag, calling code, a list of all countries to pick from).
// Nigeria is the default and works exactly as before (0801 234 5678). value() is what goes to the database.
class CountryField(private val ctx: Context, private val button: TextView, private val box: EditText) {

    private var country: Countries.C = Countries.nigeria

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.createInstance(ctx.applicationContext) }

    // Set while the box is being rewritten here, so the watcher does not react to its own change.
    private var busy = false
    // Backspace (or Delete) that removed only a space: the digit next to it should go too.
    private var dropDigitAt = -1

    init {
        showButton()
        button.setOnClickListener { open() }
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (busy || s == null) return
                dropDigitAt = -1
                // One character deleted, and it was a space: without this the formatting would put the space
                // straight back and the key would seem to do nothing.
                if (count == 1 && after == 0 && start < s.length && !s[start].isDigit() && s[start] != '+') {
                    val cursor = box.selectionStart
                    dropDigitAt = if (cursor == start + 1) {
                        // Backspace: the digit before the space.
                        var j = start - 1
                        while (j >= 0 && !s[j].isDigit()) j--
                        j
                    } else {
                        // Delete key: the digit after the space (it moves up one place once the space is gone).
                        var j = start + 1
                        while (j < s.length && !s[j].isDigit()) j++
                        if (j < s.length) j - 1 else -1
                    }
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (busy || s == null) return
                var now = s.toString()
                var cursor = box.selectionStart.coerceIn(0, now.length)
                if (dropDigitAt in now.indices) {
                    now = now.removeRange(dropDigitAt, dropDigitAt + 1)
                    cursor = dropDigitAt
                }
                dropDigitAt = -1
                val atEnd = cursor >= now.length
                val digitsBefore = now.take(cursor).count { it.isDigit() }
                val out = reformat(now)
                if (out == s.toString()) return
                busy = true
                s.replace(0, s.length, out)
                busy = false
                val c = if (atEnd) out.length else caretAfterDigits(out, digitsBefore)
                box.setSelection(c.coerceIn(0, box.text.length))
            }
        })
    }

    // The cursor goes back after the same number of digits, so typing or deleting in the middle does not jump to the end.
    private fun caretAfterDigits(s: String, n: Int): Int {
        if (n <= 0) return if (s.startsWith("+")) 1 else 0
        var seen = 0
        for (i in s.indices) if (s[i].isDigit() && ++seen == n) return i + 1
        return s.length
    }

    // Nigeria (and anything typed with a +): the usual spaces. Other countries: the number is spaced the way that
    // country writes it, and no more digits are accepted than that country has.
    private fun reformat(now: String): String {
        if (country.iso == "NG" || now.trimStart().startsWith("+")) return Phone.format(now)
        var d = now.filter { it.isDigit() }.take(15 - country.dial.length)
        while (d.isNotEmpty() && tooLong(d)) d = d.dropLast(1)
        return spaceDigits(d)
    }

    // How a country spaces its numbers, taken once from its real example number and then used for every digit typed,
    // so the spaces never jump around (UK: 07400 123456, US: 201 555 0123, Ghana: 024 123 4567 or 24 123 4567).
    // The library's own as-you-type formatter is not used: for many countries it gives no spaces until the number is
    // complete (Ghana, Kenya, South Africa, the UK typed without its 0) and then changes the whole layout at once.
    private class Groups(val pre: String, val withPre: List<Int>, val noPre: List<Int>)
    private val groupCache = HashMap<String, Groups>()

    private fun groupsFor(iso: String): Groups = groupCache.getOrPut(iso) {
        val plain = listOf(3, 3, 4)
        try {
            val n = util.getExampleNumberForType(iso, PhoneNumberUtil.PhoneNumberType.MOBILE)
                ?: return@getOrPut Groups("", plain, plain)
            val nat = util.format(n, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            val nsn = util.getNationalSignificantNumber(n)
            val all = nat.filter { it.isDigit() }
            if (!all.endsWith(nsn)) return@getOrPut Groups("", plain, plain)
            val parts = nat.split(Regex("\\D+")).filter { it.isNotEmpty() }.map { it.length }
            val pre = all.substring(0, all.length - nsn.length)
            var cut = pre.length
            val rest = ArrayList<Int>()
            for (len in parts) {
                val k = minOf(cut, len); cut -= k
                if (len - k > 0) rest.add(len - k)
            }
            Groups(pre, parts, if (rest.isEmpty()) plain else rest)
        } catch (e: Exception) {
            Groups("", plain, plain)
        }
    }

    // Digits -> spaced the country's way. A space appears as soon as the next group starts (201 5, not 2015).
    // Digits past the last group stay on the last group.
    private fun spaceDigits(d: String, iso: String = country.iso): String {
        val g = groupsFor(iso)
        val groups = if (g.pre.isNotEmpty() && d.startsWith(g.pre)) g.withPre else g.noPre
        val sb = StringBuilder()
        var i = 0
        for (k in groups.indices) {
            if (i >= d.length) break
            val len = if (k == groups.lastIndex) d.length - i else minOf(groups[k], d.length - i)
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(d, i, i + len)
            i += len
        }
        return sb.toString()
    }

    private fun tooLong(digits: String): Boolean = try {
        util.isPossibleNumberWithReason(util.parse(digits, country.iso)) == PhoneNumberUtil.ValidationResult.TOO_LONG
    } catch (e: NumberParseException) {
        false
    }

    // A real example for the country, shown in the empty box (UK: 07400 123456, US: 201 555 0123, no brackets or dashes).
    private fun exampleFor(c: Countries.C): String = try {
        val n = util.getExampleNumberForType(c.iso, PhoneNumberUtil.PhoneNumberType.MOBILE)
        // Shown without the leading 0 / 8, spaced the same way the box spaces what the member types.
        if (n == null) "Phone number" else spaceDigits(util.getNationalSignificantNumber(n), c.iso)
    } catch (e: Exception) {
        "Phone number"
    }

    // null when the number has a possible length for the chosen country, else a message for the member.
    fun problem(): String? {
        val t = box.text.toString().trim()
        if (t.isEmpty() || t.startsWith("+") || country.iso == "NG") return null
        return try {
            val n = util.parse(value(), null)
            if (util.isPossibleNumber(n)) null else "That does not look like a " + country.name + " number. Check the digits."
        } catch (e: Exception) {
            "That does not look like a " + country.name + " number. Check the digits."
        }
    }

    private fun showButton() {
        button.text = country.flag + " +" + country.dial + " \u25BE"
        box.hint = if (country.iso == "NG") "801 234 5678" else exampleFor(country)
    }

    // A new country starts with an empty box: a number typed for one country is not a number for another.
    private fun pick(c: Countries.C) {
        val changed = c.iso != country.iso
        country = c
        showButton()
        if (changed) box.setText("")
    }

    // Fill from a saved number: the right country and the rest of the number.
    fun set(stored: String) {
        val full = Phone.forServer(stored)
        val digits = full.filter { it.isDigit() }
        if (full.startsWith("+") && !digits.startsWith("234")) {
            val c = Countries.forDigits(digits)
            if (c != null) {
                country = c
                showButton()
                box.setText(digits.substring(c.dial.length))
                box.setSelection(box.text.length)
                return
            }
        }
        country = Countries.nigeria
        showButton()
        box.setText(Phone.local(stored))
        box.setSelection(box.text.length)
    }

    // What the database gets. Nigeria: as typed. A number typed with + is kept. Another country: + calling code + number
    // (a leading 0, like 07911 in the UK, is dropped).
    fun value(): String {
        val t = box.text.toString().trim()
        if (t.isEmpty() || t.startsWith("+")) return t
        var d = t.filter { it.isDigit() }
        // Nigeria typed without the 0 (810 870 9629): the 0 goes back on, so the database gets 0810 870 9629 as always.
        if (country.iso == "NG") {
            return if (d.length == 10 && !d.startsWith("0")) "0" + d.take(3) + " " + d.substring(3, 6) + " " + d.substring(6) else t
        }
        if (country.dial != "1") {
            // The library removes the country's own leading digits (0, 8, 06, 80) and keeps a 0 that belongs to the number (Benin, Ivory Coast).
            try {
                val n = util.parse(d, country.iso)
                if (n.countryCode.toString() == country.dial) return "+" + country.dial + util.getNationalSignificantNumber(n)
            } catch (e: Exception) { }
            if (d.startsWith("0")) d = d.drop(1)
        }
        return "+" + country.dial + d
    }

    private class Row(val c: Countries.C, val selected: Boolean)

    // Full-screen country page: green header with Back, a floating search box, one card per country
    // (the chosen country first, with a check), and an A to Z strip on the right to jump through the list.
    private fun open() {
        val inflater = LayoutInflater.from(ctx)
        val v = inflater.inflate(R.layout.dialog_country, null)
        val hero = v.findViewById<View>(R.id.country_hero)
        val back = v.findViewById<View>(R.id.country_back)
        val search = v.findViewById<EditText>(R.id.country_search)
        val clear = v.findViewById<View>(R.id.country_clear)
        val list = v.findViewById<ListView>(R.id.country_list)
        val empty = v.findViewById<TextView>(R.id.country_empty)
        val rail = v.findViewById<LinearLayout>(R.id.country_rail)
        Fonts.apply(v)

        var rows: List<Row> = emptyList()
        var browsing = true

        fun build(q: String): List<Row> {
            val s = q.trim().lowercase().removePrefix("+")
            browsing = s.isEmpty()
            if (browsing) {
                return listOf(Row(country, true)) + Countries.alpha.filter { it.iso != country.iso }.map { Row(it, false) }
            }
            return Countries.alpha
                .filter { it.name.lowercase().contains(s) || it.dial.startsWith(s) || it.iso.lowercase() == s }
                .map { Row(it, it.iso == country.iso) }
        }

        val dialog = Dialog(ctx, R.style.Theme_ViewGrow_FullScreen)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = convertView ?: inflater.inflate(R.layout.item_country, parent, false).also { Fonts.apply(it) }
                val r = rows[position]
                item.findViewById<TextView>(R.id.cty_flag).text = r.c.flag
                item.findViewById<TextView>(R.id.cty_name).text = r.c.name
                item.findViewById<TextView>(R.id.cty_code).text = "+" + r.c.dial
                item.findViewById<View>(R.id.cty_check).visibility = if (r.selected) View.VISIBLE else View.GONE
                item.findViewById<View>(R.id.cty_card)
                    .setBackgroundResource(if (r.selected) R.drawable.bg_country_row_on else R.drawable.bg_country_row)
                return item
            }
        }

        // A to Z strip.
        val letters = ('A'..'Z').toList()
        val tips = letters.map { ch ->
            TextView(ctx).apply {
                text = ch.toString()
                textSize = 10.5f
                gravity = Gravity.CENTER
                tag = "semibold"
                setTextColor(ContextCompat.getColor(ctx, R.color.muted))
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 20), 0, 1f)
            }
        }
        tips.forEach { rail.addView(it) }
        Fonts.apply(rail)
        var marked = ' '
        fun mark(ch: Char) {
            if (ch == marked) return
            marked = ch
            tips.forEachIndexed { i, t ->
                val on = letters[i] == ch
                t.setBackgroundResource(if (on) R.drawable.bg_green_circle else 0)
                t.setTextColor(ContextCompat.getColor(ctx, if (on) R.color.white else R.color.muted))
                // The new letter pops in (small to full size) instead of just appearing.
                t.animate().cancel()
                if (on) {
                    t.scaleX = 0.6f; t.scaleY = 0.6f
                    t.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                } else {
                    t.scaleX = 1f; t.scaleY = 1f
                }
            }
        }
        fun jump(ch: Char) {
            val pos = rows.indices.firstOrNull { it > 0 && Countries.letter(rows[it].c) >= ch } ?: rows.lastIndex
            if (pos < 0) return
            list.setSelectionFromTop(pos, 0)
            mark(Countries.letter(rows[pos].c))
        }
        // Big letter bubble beside the strip while the finger is on it. It fades in and out.
        val bubble = TextView(ctx).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            tag = "bold"
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_green_circle)
            alpha = 0f
            visibility = View.GONE
            layoutParams = android.widget.FrameLayout.LayoutParams(Ui.dp(ctx, 64), Ui.dp(ctx, 64)).also {
                it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                it.marginEnd = Ui.dp(ctx, 48)
            }
        }
        Fonts.apply(bubble)
        (rail.parent as? android.widget.FrameLayout)?.addView(bubble)
        fun showBubble(ch: Char) {
            bubble.text = ch.toString()
            if (bubble.visibility != View.VISIBLE) {
                bubble.visibility = View.VISIBLE
                bubble.scaleX = 0.7f; bubble.scaleY = 0.7f
            }
            bubble.animate().cancel()
            bubble.animate().setStartDelay(0).withEndAction(null).alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        fun hideBubble() {
            bubble.animate().cancel()
            bubble.animate().alpha(0f).setStartDelay(250).setDuration(200)
                .withEndAction { if (bubble.alpha == 0f) bubble.visibility = View.GONE }.start()
        }
        rail.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val i = ((ev.y / view.height) * letters.size).toInt().coerceIn(0, letters.size - 1)
                    jump(letters[i])
                    showBubble(letters[i])
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hideBubble()
            }
            true
        }
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                if (browsing && rows.isNotEmpty()) mark(if (first == 0) 'A' else Countries.letter(rows[first.coerceAtMost(rows.lastIndex)].c))
            }
        })

        fun refill(q: String) {
            rows = build(q)
            (list.adapter as BaseAdapter).notifyDataSetChanged()
            list.setSelection(0)
            rail.visibility = if (browsing) View.VISIBLE else View.GONE
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            clear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
            if (browsing) mark('A')
        }

        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in rows.indices) pick(rows[pos].c)
            dialog.dismiss()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refill(s?.toString().orEmpty()) }
        })
        clear.setOnClickListener { search.setText("") }
        back.setOnClickListener { dialog.dismiss() }
        refill("")

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(v)
        val w = dialog.window!!
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        // Edge to edge, like the rest of the app: the green header runs behind the status bar.
        WindowCompat.setDecorFitsSystemWindows(w, false)
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = ContextCompat.getColor(ctx, R.color.surface)
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val controller = WindowCompat.getInsetsController(w, w.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = !AppTheme.isDark(ctx)
        val heroTop = hero.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            hero.setPadding(hero.paddingLeft, heroTop + bars.top, hero.paddingRight, hero.paddingBottom)
            view.setPadding(0, 0, 0, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        dialog.show()
    }
}
