package com.viewgrow.admin

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import java.util.concurrent.Executors

// Contacts sync: opened by tapping the green line on Today.
// Turn the sync on or off, turn the "saved" notes on or off, see the numbers, check now, and sign out of this phone.
class SyncActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        render()
    }

    private fun render() {
        val (root, body) = Ui.page(this, Ui.hero(this, "Contacts sync", onBack = { finish() }))

        body.addView(switchRow("Save new members", "Checks about every 15 minutes when you have data", Store.syncOn) { on ->
            Store.syncOn = on
            if (on) MemberSync.schedule(this) else MemberSync.cancel(this)
            render()
        })
        body.addView(switchRow("Note when members are saved", "A quiet notification", Store.notifyOn) { on -> Store.notifyOn = on })

        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        tiles.layoutParams = Ui.lp(this, 12)
        tiles.addView(tile("${MemberList.all(this).size}", "Members known"))
        tiles.addView(tile("${Store.savedToday()}", "Saved today"))
        body.addView(tiles)

        val last = if (Store.lastCheckAt == 0L) "Not checked yet" else "Last check: " + MemberSync.ago(Store.lastCheckAt)
        val already = if (Store.alreadyInPhone > 0) " · ${Store.alreadyInPhone} members were already in your phone, some under another name" else ""
        body.addView(Ui.text(this, last + already, 12.5f, R.color.muted, top = 10))
        if (Store.lastProblem.isNotEmpty()) body.addView(Ui.text(this, Store.lastProblem, 13f, R.color.red_dark, "semibold", top = 8))

        lateinit var check: TextView
        check = Ui.button(this, "Check now", top = 14) {
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

        val recent = Store.recent()
        if (recent.isNotEmpty()) {
            body.addView(Ui.text(this, "RECENTLY SAVED", 11f, R.color.muted, "semibold", top = 20))
            val card = Ui.card(this, 8, 14)
            recent.forEachIndexed { i, (name, at) ->
                card.addView(Ui.text(this, name, 14f, R.color.dark, "semibold", top = if (i == 0) 0 else 12))
                card.addView(Ui.text(this, "Saved " + MemberSync.ago(at), 12f, R.color.muted, top = 2))
            }
            body.addView(card)
        }

        body.addView(Ui.button(this, "Sign out of this phone", fill = false, top = 24) {
            AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("The app stops saving new members. Contacts already saved stay in your phone.")
                .setPositiveButton("Sign out") { _, _ ->
                    MemberSync.cancel(this)
                    MemberList.clear(this)
                    Store.signOut()
                    // Back to the main screen, which now shows Sign in.
                    startActivity(android.content.Intent(this, MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        setContentView(root)
    }

    private fun switchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val c = Ui.card(this, 10, 14)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        col.addView(Ui.text(this, title, 15f, R.color.dark, "semibold"))
        col.addView(Ui.text(this, sub, 12f, R.color.muted, top = 2))
        val sw = SwitchCompat(this)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        c.addView(col); c.addView(sw)
        return c
    }

    private fun tile(n: String, label: String): LinearLayout {
        val t = Ui.card(this, 0, 14)
        t.gravity = Gravity.CENTER
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = Ui.dp(this, 8) }
        t.addView(Ui.text(this, n, 24f, R.color.green_dark, "extrabold", center = true))
        t.addView(Ui.text(this, label, 12f, R.color.text2, center = true, top = 2))
        return t
    }
}
