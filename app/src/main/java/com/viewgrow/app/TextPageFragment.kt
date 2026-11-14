package com.viewgrow.app

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        view.findViewById<TextView>(R.id.body).text = styled(view, Pages.body(kind))
        Fonts.apply(view)
        view.findViewById<View>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // Centred text: the first line of each block is its heading, drawn bold and darker.
    private fun styled(view: View, text: String): CharSequence {
        val ctx = view.context
        val bold = Fonts.typeface(ctx, "bold")
        val dark = ContextCompat.getColor(ctx, R.color.dark)
        val out = SpannableStringBuilder()
        text.split("\n\n").forEachIndexed { i, block ->
            if (i > 0) out.append("\n\n")
            val nl = block.indexOf('\n')
            val headEnd = if (nl < 0) block.length else nl
            val start = out.length
            out.append(block)
            out.setSpan(FaceSpan(bold), start, start + headEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(dark), start, start + headEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }
}
