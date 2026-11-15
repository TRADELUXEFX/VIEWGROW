package com.viewgrow.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// The packs this phone bought, for the Purchase history screen. Written only after the database
// confirmed a purchase (BillingManager.verify), so it never shows a payment that did not count.
// Kept on the phone, like HistoryLog: no database change. Each record carries the member's number, so
// another account on the same phone does not see it. Google Play keeps the official receipts.
object PurchaseLog {
    class Item(val pack: Int, val time: Long, val price: String, val orderId: String)

    private const val FILE = "viewgrow_purchases"
    private const val KEY = "items"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(ctx: Context): JSONArray {
        val s = prefs(ctx).getString(KEY, null) ?: return JSONArray()
        return try { JSONArray(s) } catch (e: Exception) { JSONArray() }
    }

    // price may be empty (Google Play had not sent prices yet when a waiting purchase was confirmed).
    @Synchronized
    fun add(ctx: Context, phone: String, pack: Int, time: Long, price: String, orderId: String) {
        if (pack <= 0) return
        val arr = read(ctx)
        if (orderId.isNotEmpty()) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("o") == orderId && o.optString("ph") == phone) return
            }
        }
        arr.put(JSONObject().put("ph", phone).put("k", pack).put("t", time).put("pr", price).put("o", orderId))
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    // This member's purchases, newest first.
    fun list(ctx: Context, phone: String): List<Item> {
        val arr = read(ctx)
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("ph") != phone) continue
            out.add(Item(o.optInt("k"), o.optLong("t"), o.optString("pr"), o.optString("o")))
        }
        return out.sortedByDescending { it.time }
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
