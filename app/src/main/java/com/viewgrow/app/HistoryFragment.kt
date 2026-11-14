package com.viewgrow.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

// History: contacts added on each day the member was verified. Built in a later update.
class HistoryFragment : Fragment(R.layout.fragment_history) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Fonts.apply(view)
    }
}
