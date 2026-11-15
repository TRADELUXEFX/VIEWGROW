package com.viewgrow.app

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

// First screen for a new install: what the app does in three steps, then Sign up or Log in.
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        step(view, R.id.step1, "1", "Sign up", "Create your free account", R.drawable.ic_person, false)
        step(view, R.id.step2, "2", "Reshare and get verified", "Message the admin, then reshare his status post", R.drawable.ic_reshare, false)
        step(view, R.id.step3, "3", "Get new status viewers", "Saved to your phone automatically every day", R.drawable.ic_download, true)
        Fonts.apply(view)

        val main = activity as? MainActivity
        view.findViewById<View>(R.id.btn_signup).setOnClickListener { main?.showSignUp() }
        view.findViewById<View>(R.id.btn_login).setOnClickListener { main?.showLogIn() }
    }

    private fun step(root: View, id: Int, num: String, title: String, sub: String, icon: Int, last: Boolean) {
        val card = root.findViewById<View>(id)
        card.findViewById<TextView>(R.id.step_num).text = num
        card.findViewById<TextView>(R.id.step_title).text = title
        card.findViewById<TextView>(R.id.step_sub).text = sub
        // The line joins each step to the next, so the last step has none.
        if (last) card.findViewById<View>(R.id.step_line).visibility = View.GONE
        val img = card.findViewById<ImageView>(R.id.step_icon)
        img.setImageResource(icon)
        // The icons sit on a light green circle, so tint them all the same green.
        img.setColorFilter(ContextCompat.getColor(root.context, R.color.green_text))
    }
}
