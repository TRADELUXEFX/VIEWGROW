package com.viewgrow.app

import android.content.Context
import org.json.JSONObject

// Members who left (deleted their account, or were deleted by the admin) or were banned come off this phone.
// Database: web_gone_for_me (migration 20261121040000_remove_departed_from_phones.sql) gives only numbers this member
// was given that have gone since the last ask. Only contacts ViewGrow saved (VG) are removed (ContactsSaver.deleteNumbers).
// Runs in the background check (BackgroundSync.run). Blocking: background threads only.
object Departed {
    fun clean(ctx: Context) {
        try {
            if (!Store.loggedIn || Store.banned || !ContactsSaver.hasPermission(ctx)) return
            val args = JSONObject().put("p_phone", Store.phone).put("p_pin", Store.password)
            val since = Store.goneSince
            if (since.isNotEmpty()) args.put("p_since", since)
            val j = Api.request("web_gone_for_me", args).json ?: return   // no answer, or not installed yet: try next time
            if (!j.optBoolean("ok")) return
            val arr = j.optJSONArray("numbers")
            val numbers = ArrayList<String>()
            if (arr != null) for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { numbers.add(it) }
            val removed = if (numbers.isEmpty()) 0 else ContactsSaver.deleteNumbers(ctx, numbers)
            // Only move on when the phone did its part; a failed removal is asked again next time.
            if (removed >= 0) Store.goneSince = j.optString("now")
        } catch (t: Throwable) {
            // Never break the background check over this.
        }
    }
}
