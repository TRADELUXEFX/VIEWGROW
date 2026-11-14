package com.viewgrow.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

// Profile. In this first update: the app version and Log out. Settings come in a later update.
class ProfileFragment : Fragment(R.layout.fragment_profile) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Ui.bindUserHeader(view)
        val version = try {
            val ctx = requireContext()
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        view.findViewById<TextView>(R.id.version).text = "App version $version"
        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
            (activity as? MainActivity)?.onLoggedOut()
        }
        Fonts.apply(view)
    }
}
