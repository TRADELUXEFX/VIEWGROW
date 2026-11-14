package com.viewgrow.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

// A page of plain text with a Back arrow: Terms and Conditions, Privacy Policy.
class TextPageFragment : Fragment(R.layout.fragment_text_page) {

    companion object {
        fun newInstance(kind: Int): TextPageFragment {
            val f = TextPageFragment()
            f.arguments = Bundle().apply { putInt("kind", kind) }
            return f
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val kind = arguments?.getInt("kind", Pages.TERMS) ?: Pages.TERMS
        view.findViewById<TextView>(R.id.title).text = Pages.title(kind)
        view.findViewById<TextView>(R.id.body).text = Pages.body(kind)
        Fonts.apply(view)
        view.findViewById<View>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
