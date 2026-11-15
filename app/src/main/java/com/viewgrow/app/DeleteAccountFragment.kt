package com.viewgrow.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

// Delete my account. Accounts are only removed by the admin (a member who deletes and signs up again would get a fresh
// contact limit), so this page explains that and opens a chat with the admin, with the request already written.
class DeleteAccountFragment : Fragment(R.layout.fragment_delete_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
        view.findViewById<View>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
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
}
