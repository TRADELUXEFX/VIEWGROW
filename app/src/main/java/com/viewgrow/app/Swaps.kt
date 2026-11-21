package com.viewgrow.app

import android.content.Context
import org.json.JSONObject

// Members who moved to a new phone number (the admin approved it): the saved "Name VG" contact on this phone gets the
// new number, so this member keeps seeing their status. Database: web_swaps_for_me (migration 20261121080000_phone_change.sql)
// gives only changes for people this member was given, since the last ask. Only contacts ViewGrow saved (VG) change
// (ContactsSaver.swapNumbers). Runs in the background check, next to Departed. Blocking: background threads only.
object Swaps {
    fun apply(ctx: Context) {
        try {
            if (!Store.loggedIn || Store.banned || !ContactsSaver.hasPermission(ctx)) return
            val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
            val since = Store.swapSince
            if (since.isNotEmpty()) args.put("p_since", since)
            val j = Api.request("web_swaps_for_me", args).json ?: return   // no answer, or not installed yet: try next time
            if (!j.optBoolean("ok")) return
            val arr = j.optJSONArray("swaps")
            val pairs = ArrayList<Pair<String, String>>()
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val a = o.optString("old"); val b = o.optString("new")
                if (a.isNotBlank() && b.isNotBlank()) pairs.add(a to b)
            }
            val done = if (pairs.isEmpty()) 0 else ContactsSaver.swapNumbers(ctx, pairs)
            // Only move on when the phone did its part; a failed change is asked again next time.
            if (done >= 0) Store.swapSince = j.optString("now")
        } catch (t: Throwable) {
            // Never break the background check over this.
        }
    }
}
