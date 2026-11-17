package com.viewgrow.admin

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import java.util.concurrent.Executors

// Contacts sync: opened by tapping the green line on Today.
// Turn the sync on or off, turn the "saved" notes on or off, see the numbers, check now. The history icon shows who was saved lately.
// (Sign out lives in Settings, not here.)
class SyncActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val EVERY = listOf(15 to "15 minutes", 60 to "1 hour", 720 to "12 hours", 1440 to "24 hours")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Ui.edgeToEdge(this)
        render()
    }

    private fun render() {
        val (root, body) = Ui.page(this, Ui.hero(this, "Contacts sync", onBack = { finish() }, action = R.drawable.ic_history to { showRecent() }))

        body.addView(switchRow("Save new members to this phone", "", Store.syncOn) { on ->
            Store.syncOn = on
            if (on) MemberSync.schedule(this) else MemberSync.cancel(this)
            render()
        })
        // How often the background check runs.
        if (Store.syncOn) {
            val every = Ui.card(this, 10, 14)
            every.orientation = LinearLayout.HORIZONTAL
            every.gravity = Gravity.CENTER_VERTICAL
            val lbl = Ui.text(this, "Check every", 15f, R.color.dark, "semibold")
            lbl.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val value = Ui.text(this, EVERY.firstOrNull { it.first == Store.syncEveryMin }?.second.orEmpty().ifEmpty { "15 minutes" } + "  ▾", 14f, R.color.green_dark, "semibold")
            value.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            every.addView(lbl); every.addView(value)
            every.isClickable = true
            every.isFocusable = true
            every.setOnClickListener {
                Ui.choose(this, "Check every", options = EVERY.map { Ui.Option(it.second, selected = it.first == Store.syncEveryMin) }) { i ->
                    Store.syncEveryMin = EVERY[i].first
                    MemberSync.cancel(this)
                    MemberSync.schedule(this)
                    render()
                }
            }
            body.addView(every)
        }
        body.addView(switchRow("Tell me when members are saved", "", Store.notifyOn) { on -> Store.notifyOn = on })

        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        tiles.layoutParams = Ui.lp(this, 12)
        tiles.addView(tile("${MemberList.all(this).size}", "Total members", end = true))
        tiles.addView(tile("${Store.savedToday()}", "Saved today", end = false))
        body.addView(tiles)

        // The last check, in a small card: label on the left, value on the right, so nothing wraps awkwardly.
        val status = Ui.card(this, 12, 14)
        status.addView(Ui.line(this, "Last check", if (Store.lastCheckAt == 0L) "Not yet" else MemberSync.ago(Store.lastCheckAt).replaceFirstChar { it.uppercase() }, top = 0))
        body.addView(status)
        if (Store.lastProblem.isNotEmpty()) body.addView(Ui.text(this, Store.lastProblem, 13f, R.color.red_dark, "semibold", top = 10))

        lateinit var check: TextView
        check = Ui.button(this, "Check now", top = 16, icon = R.drawable.ic_refresh) {
            Ui.setBusy(check, true, "Check now", "Checking")
            io.execute {
                val r = MemberSync.run(applicationContext)
                runOnUiThread {
                    Ui.toast(this, r.problem ?: if (r.saved.isEmpty()) "No new members." else "${r.saved.size} saved to your phone.")
                    render()
                }
            }
        }
        body.addView(check)
        setContentView(root)
    }

    // The history icon in the header: who was saved lately, in a pop-up (instead of a long list on this screen).
    private fun showRecent() {
        val rows = Store.recent().map { (name, at) -> name to MemberSync.ago(at) }
        Ui.listPopup(this, "Recently saved", rows, "Nobody has been saved yet.")
    }

    private fun switchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val c = Ui.card(this, 10, 14)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        col.addView(Ui.text(this, title, 15f, R.color.dark, "semibold"))
        if (sub.isNotEmpty()) col.addView(Ui.text(this, sub, 12f, R.color.muted, top = 2))
        val sw = SwitchCompat(this)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        c.addView(col); c.addView(sw)
        return c
    }

    private fun tile(n: String, label: String, end: Boolean): LinearLayout {
        val t = Ui.card(this, 0, 14)
        t.gravity = Gravity.CENTER
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = if (end) Ui.dp(this, 8) else 0 }
        t.addView(Ui.text(this, n, 24f, R.color.green_dark, "extrabold", center = true))
        t.addView(Ui.text(this, label, 12f, R.color.text2, center = true, top = 2))
        return t
    }
}
