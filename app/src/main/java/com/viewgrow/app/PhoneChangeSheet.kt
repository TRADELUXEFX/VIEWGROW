package com.viewgrow.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

// Profile, Change phone number (design A bottom pop-up). The member asks to move the account to a new number; the admin
// checks the number and approves it on the admin page (Number changes). Database: web_phone_change_mine / _request /
// _cancel (migration 20261121080000_phone_change.sql). Once approved, the old login ends and the member logs in with the
// new number and the same password; other members' saved contacts get the new number (Swaps.kt).
object PhoneChangeSheet {
    private val ERRORS = mapOf(
        "NUMBER_TAKEN" to "That number already has a ViewGrow account, or someone else asked for it.",
        "NUMBER_DELETED" to "That number belonged to a deleted account. Message the admin to let it back in first.",
        "INVALID_PHONE" to "That is not a real phone number. Check the digits.",
        "SAME_NUMBER" to "That is already your number.",
        "TOO_MANY" to "Too many requests today. Try again tomorrow, or message the admin."
    )

    fun show(main: MainActivity) {
        val v = LayoutInflater.from(main).inflate(R.layout.sheet_phone_change, null)
        val loading = v.findViewById<View>(R.id.pc_loading)
        val form = v.findViewById<View>(R.id.pc_form)
        val wait = v.findViewById<View>(R.id.pc_wait)
        val phone = v.findViewById<EditText>(R.id.pc_phone)
        val error = v.findViewById<TextView>(R.id.pc_error)
        val send = v.findViewById<MaterialButton>(R.id.pc_send)
        val country = CountryField(main, v.findViewById(R.id.pc_country), phone)
        v.findViewById<TextView>(R.id.pc_now).text = Phone.show(Store.phone)
        Fonts.apply(v)

        val dialog = BottomSheetDialog(main)
        dialog.setContentView(v)
        // The layout draws its own curved top, so the sheet frame behind it must stay see-through (same as ReportSheet).
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    sheet.setBackgroundColor(Color.TRANSPARENT)
                    sheet.backgroundTintList = null
                    if (sheet.viewTreeObserver.isAlive) sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        v.findViewById<View>(R.id.pc_close).setOnClickListener { dialog.dismiss() }

        fun showError(msg: String?) {
            error.text = msg ?: ""
            error.visibility = if (msg.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        fun showForm(refusedNote: String?) {
            loading.visibility = View.GONE; wait.visibility = View.GONE; form.visibility = View.VISIBLE
            showError(refusedNote?.let { "Your last request was refused" + (if (it.isNotBlank()) ": $it" else ".") })
        }
        fun showWait(req: JSONObject) {
            loading.visibility = View.GONE; form.visibility = View.GONE; wait.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.pc_wait_old).text = Phone.show(req.optString("old_phone"))
            v.findViewById<TextView>(R.id.pc_wait_new).text = Phone.show(req.optString("new_phone"))
            val admin = v.findViewById<View>(R.id.pc_admin)
            admin.visibility = if (Remote.adminExpected()) View.VISIBLE else View.GONE
            admin.setOnClickListener {
                val name = Store.name.trim().ifEmpty { "a member" }
                val msg = "Hi, I'm $name. I asked to move my ViewGrow account from ${Phone.show(req.optString("old_phone"))} " +
                    "to ${Phone.show(req.optString("new_phone"))}. Please check and approve it."
                if (!Remote.openAdmin(main, msg)) Ui.toast(v, "Could not open the chat with the admin.")
            }
        }
        // A database answer that ends the session (banned, logged out...) is handled like everywhere else.
        fun gate(j: JSONObject): Boolean {
            if (AccountGate.handle(main, j, v)) { dialog.dismiss(); return true }
            return false
        }

        val auth = { JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password) }
        Api.call("web_phone_change_mine", auth()) { r ->
            if (!dialog.isShowing) return@call
            val j = r.json
            if (j == null) { dialog.dismiss(); Ui.toast(v, Api.netMessage(r.error)); return@call }
            if (!j.optBoolean("ok")) {
                if (gate(j)) return@call
                // Not installed in the database yet.
                dialog.dismiss(); Ui.toast(v, "This is not ready yet. Please message the admin."); return@call
            }
            val req = j.optJSONObject("request")
            when (req?.optString("status")) {
                "pending" -> showWait(req)
                "refused" -> showForm(req.optString("note"))
                else -> showForm(null)
            }
        }

        send.setOnClickListener {
            showError(null)
            if (phone.text.isNullOrBlank()) { showError("Type your new number."); return@setOnClickListener }
            country.problem()?.let { showError(it); return@setOnClickListener }
            send.isEnabled = false
            Api.call("web_phone_change_request", auth().put("p_new_phone", country.value())) { r ->
                send.isEnabled = true
                if (!dialog.isShowing) return@call
                val j = r.json
                if (j == null) { showError(Api.netMessage(r.error)); return@call }
                if (!j.optBoolean("ok")) {
                    if (gate(j)) return@call
                    showError(ERRORS[j.optString("code")] ?: "Could not send. Please try again.")
                    return@call
                }
                j.optJSONObject("request")?.let { showWait(it) }
                Ui.toast(v, "Sent to the admin.")
            }
        }

        v.findViewById<View>(R.id.pc_cancel).setOnClickListener {
            ConfirmDialog.show(main, "Cancel this request?", "Your number stays as it is now.", "Cancel request", "Keep it") {
                Api.call("web_phone_change_cancel", auth()) { r ->
                    val j = r.json
                    if (j != null && !j.optBoolean("ok") && gate(j)) return@call
                    if (j == null || !j.optBoolean("ok")) { Ui.toast(v, "Could not cancel. Please try again."); return@call }
                    phone.setText("")
                    showForm(null)
                }
            }
        }
        dialog.show()
    }
}
