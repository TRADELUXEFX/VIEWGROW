package com.viewgrow.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

// Home. In this first update it shows the header only; the countdown, contacts and sync come next.
class HomeFragment : Fragment(R.layout.fragment_home) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Ui.bindUserHeader(view)
        Fonts.apply(view)
    }
}
