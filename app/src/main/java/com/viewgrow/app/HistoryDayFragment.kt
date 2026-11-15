package com.viewgrow.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

// One day opened from History: the people saved that day, each with a name, a number and a chat button
// that opens a WhatsApp chat with them.
class HistoryDayFragment : Fragment(R.layout.fragment_history_day) {

    companion object {
        fun newInstance(day: String, count: Int): HistoryDayFragment {
            val f = HistoryDayFragment()
            f.arguments = Bundle().apply {
                putString("day", day)
                putInt("count", count)
            }
            return f
        }
    }

    private var people: List<HistoryLog.Entry> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val day = arguments?.getString("day").orEmpty()
        val count = arguments?.getInt("count", 0) ?: 0
        view.findViewById<TextView>(R.id.day_title).text = DayText.label(day)
        view.findViewById<TextView>(R.id.day_count).text = if (count == 1) "1 contact" else "$count contacts"
        view.findViewById<ImageView>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        people = HistoryLog.entries(requireContext(), day)
        Fonts.apply(view)
        render()
    }

    private fun render() {
        val v = view ?: return
        val list = v.findViewById<LinearLayout>(R.id.day_list)
        list.removeAllViews()
        val ctx = v.context
        val inflater = LayoutInflater.from(ctx)
        for (p in people) {
            val row = inflater.inflate(R.layout.item_history_contact, list, false)
            row.findViewById<TextView>(R.id.c_name).text = if (p.name.isBlank()) HistoryLog.display(p.number) else p.name
            TextFit.shrinkToFit(row.findViewById(R.id.c_name), 14)
            TextFit.shrinkToFit(row.findViewById(R.id.c_number), 12)
            val num = row.findViewById<TextView>(R.id.c_number)
            if (p.name.isBlank()) num.visibility = View.GONE else num.text = HistoryLog.display(p.number)
            val chat = View.OnClickListener { openChat(p.number) }
            row.findViewById<View>(R.id.c_chat).setOnClickListener(chat)
            row.setOnClickListener(chat)
            Fonts.apply(row)
            list.addView(row)
        }
        val empty = v.findViewById<TextView>(R.id.day_empty)
        if (people.isEmpty()) {
            empty.text = "The names and numbers for this day are not on this phone.\nDays saved from now on will show them here."
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
        }
        Fonts.apply(empty)
    }

    // Opens the WhatsApp chat (wa.me), in WhatsApp or WhatsApp Business, whichever the phone has.
    private fun openChat(number: String) {
        val url = HistoryLog.chatUrl(number)
        if (url == null) { Ui.toast(requireView(), "No number to chat with."); return }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Ui.toast(requireView(), "Could not open WhatsApp.")
        }
    }
}
