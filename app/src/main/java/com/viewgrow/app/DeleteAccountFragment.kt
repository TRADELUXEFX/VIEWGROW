package com.viewgrow.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

// Delete my account. Required by Google Play: the member deletes inside the app (and the same page is on the website,
// Web.DELETE_ACCOUNT). Only a logged-in member can do it. The app calls web_delete_account
// (supabase/migrations/20261116140000_deleted_numbers.sql): the number goes on the deleted list at once, and from then on it
// cannot log in or sign up (ACCOUNT_DELETED) unless the admin removes it from the list. No approval step.
// The app removes the contacts it saved and all of its own records on the phone, and logs the member out.
class DeleteAccountFragment : Fragment(R.layout.fragment_delete_account) {

    private var busy = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        view.findViewById<View>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val delete = view.findViewById<MaterialButton>(R.id.btn_delete_now)
        // Not logged in (for example the account was locked): only the admin and web routes remain.
        delete.visibility = if (Store.loggedIn) View.VISIBLE else View.GONE
        delete.setOnClickListener {
            if (busy) return@setOnClickListener
            ConfirmDialog.show(
                view.context,
                "Delete your account?",
                "Once you delete it, you cannot get this account back. ViewGrow removes the contacts it saved to your phone and your account is deleted. Your number is kept on a deleted list, so it cannot log in or sign up again unless you ask the admin.",
                "Delete"
            ) { deleteNow(view, delete) }
        }

        val button = view.findViewById<MaterialButton>(R.id.btn_delete_request)
        // Shown from the first moment; hidden only when the admin has set no contact.
        button.visibility = if (Remote.adminExpected()) View.VISIBLE else View.GONE
        Remote.load {
            if (isAdded && view.parent != null) button.visibility = if (Remote.adminExpected()) View.VISIBLE else View.GONE
        }
        view.findViewById<View>(R.id.link_web).setOnClickListener {
            if (!Web.open(requireActivity(), Web.DELETE_ACCOUNT)) Ui.toast(view, "Could not open the link. Check your internet and try again.")
        }
        button.setOnClickListener {
            val msg = "Hi, I would like to delete my ViewGrow account. My name is ${Store.name} and my number is ${Phone.show(Store.phone)}."
            if (!Remote.openAdmin(view.context, msg)) Ui.toast(view, "Could not open the link. Check your internet and try again.")
        }
    }

    private fun deleteNow(view: View, btn: Button) {
        val app = view.context.applicationContext
        val phone = Store.phone   // kept for the Deleted page: Store is cleared below
        busy = true
        Ui.setBusy(btn, true, "Delete my account", "Deleting")
        val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
        Api.call("web_delete_account", args) { r ->
            val j = r.json
            if (j != null && j.optBoolean("ok")) {
                // The database has the request. Now clean the phone, off the main thread, then log out.
                Thread {
                    try {
                        if (ContactsSaver.hasPermission(app)) ContactsSaver.deleteMine(app)
                    } catch (t: Throwable) {
                        // A failed clean-up must not stop the deletion.
                    }
                    PurchaseLog.clear(app)
                    Store.clearAll()
                    view.post {
                        // Same full-screen page as Banned, saying the account was deleted.
                        (activity as? MainActivity)?.let { it.onLoggedOut(goWelcome = false); it.showDeleted(phone) }
                    }
                }.start()
            } else {
                busy = false
                if (isAdded) {
                    Ui.setBusy(btn, false, "Delete my account")
                    val code = j?.optString("code").orEmpty()
                    Ui.toast(
                        view,
                        when {
                            j == null -> Api.netMessage(r.error)
                            code == "BANNED" || code == "LOCKED" -> "This account cannot be deleted from the app right now. Message the admin."
                            else -> "Could not delete the account. Please try again."
                        }
                    )
                }
            }
        }
    }
}
