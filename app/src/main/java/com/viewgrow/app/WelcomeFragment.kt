package com.viewgrow.app

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment

// First screen for a new install: what the app does in three steps, then Sign up or Log in.
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        step(view, R.id.step1, "1", "Sign up", "Create your free account", R.drawable.ic_person)
        step(view, R.id.step2, "2", "Reshare and get verified", "Message the admin, then reshare his status", R.drawable.ic_reshare)
        step(view, R.id.step3, "3", "Get contacts", "Saved to your phone by the app at 10pm", R.drawable.ic_download)
        Fonts.apply(view)

        val main = activity as? MainActivity
        view.findViewById<View>(R.id.btn_signup).setOnClickListener { main?.showSignUp() }
        view.findViewById<View>(R.id.btn_login).setOnClickListener { main?.showLogIn() }
    }

    private fun step(root: View, id: Int, num: String, title: String, sub: String, icon: Int) {
        val card = root.findViewById<View>(id)
        card.findViewById<TextView>(R.id.step_num).text = num
        card.findViewById<TextView>(R.id.step_title).text = title
        card.findViewById<TextView>(R.id.step_sub).text = sub
        val img = card.findViewById<ImageView>(R.id.step_icon)
        img.setImageResource(icon)
        // The icons are drawn white (person is green by default), so tint them all white here.
        img.setColorFilter(android.graphics.Color.WHITE)
    }
}
