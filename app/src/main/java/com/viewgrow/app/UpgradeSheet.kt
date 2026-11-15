package com.viewgrow.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

// The "Increase my limit" pop-up, opened from the button on Home.
// It says what is being bought and why, and what the new limit would be for each pack.
// Prices show as a placeholder until Google Play billing is switched on, and the buy button
// does not charge anyone yet (see README, item 17).
object UpgradeSheet {
    val packs = intArrayOf(1000, 3000, 5000)
    private const val PRICE_PLACEHOLDER = "\u20A6 price"

    fun show(ctx: Context, total: Int, limit: Int) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.sheet_upgrade, null)
        Fonts.apply(v)

        v.findViewById<TextView>(R.id.up_sub).text =
            if (limit > 0) "You have $total of $limit contacts used."
            else "You have $total contacts."

        v.findViewById<TextView>(R.id.up_explain).text =
            "Your contact limit is the most contacts your account can receive. " +
            "Each pack adds to that limit, so when the admin releases new contacts, " +
            "there is room for them to reach your phone. " +
            "A pack does not change which contacts you get. The admin decides that. " +
            "You pay through Google Play, in naira."

        val rows = v.findViewById<LinearLayout>(R.id.up_packs)
        for (n in packs) {
            val row = LayoutInflater.from(ctx).inflate(R.layout.item_upgrade_pack, rows, false)
            row.findViewById<TextView>(R.id.pack_name).text = "+" + "%,d".format(n) + " contacts"
            row.findViewById<TextView>(R.id.pack_sub).text =
                if (limit > 0) "Your limit goes from " + "%,d".format(limit) + " to " + "%,d".format(limit + n)
                else "Adds " + "%,d".format(n) + " to your limit"
            row.findViewById<TextView>(R.id.pack_price).text = PRICE_PLACEHOLDER
            rows.addView(row)
        }

        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(v)
        v.findViewById<MaterialButton>(R.id.up_buy).setOnClickListener {
            Toast.makeText(ctx, "Purchases are not switched on yet.", Toast.LENGTH_SHORT).show()
        }
        v.findViewById<View>(R.id.up_later).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
