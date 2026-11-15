package com.viewgrow.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Calendar

// Profile, Purchase history: every contact pack bought on this phone, newest first.
class PurchaseHistoryFragment : Fragment(R.layout.fragment_purchases) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ImageView>(R.id.back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val ctx = view.context
        val list = view.findViewById<LinearLayout>(R.id.purchase_list)
        val empty = view.findViewById<TextView>(R.id.purchase_empty)
        val items = PurchaseLog.list(ctx, Store.phone)
        val inflater = LayoutInflater.from(ctx)
        for (p in items) {
            val row = inflater.inflate(R.layout.item_purchase, list, false)
            row.findViewById<TextView>(R.id.pur_title).text = "+" + "%,d".format(p.pack) + " contacts"
            row.findViewById<TextView>(R.id.pur_date).text = whenText(p.time)
            val price = row.findViewById<TextView>(R.id.pur_price)
            if (p.price.isEmpty()) price.visibility = View.GONE else price.text = p.price
            val order = row.findViewById<TextView>(R.id.pur_order)
            if (p.orderId.isEmpty()) order.visibility = View.GONE else order.text = "Order " + p.orderId
            Fonts.apply(row)
            list.addView(row)
        }
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        Fonts.apply(view)
    }

    // Mon 9 Nov 2026 · 14:32
    private fun whenText(millis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return DayText.label(DayText.iso(c)) + " \u00b7 " +
            String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
