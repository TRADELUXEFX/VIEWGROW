package com.viewgrow.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors

// The answer to one database call: either the JSON the database sent back, or an error word
// (NETWORK, TIMEOUT, HTTP_500 ...).
class ApiResult(val json: JSONObject?, val error: String?)

// Talks to the ViewGrow database the same way the website does: each call is a POST to
// /rest/v1/rpc/<function> with the number and password inside it. There is no session to expire.
object Api {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    // Runs the call on a background thread and gives the answer back on the main thread.
    fun call(fn: String, args: JSONObject = JSONObject(), onDone: (ApiResult) -> Unit) {
        pool.execute {
            val res = request(fn, args)
            main.post { onDone(res) }
        }
    }

    // The contact file can take a while to build on the server, so it gets longer.
    private fun readTimeout(fn: String): Int = if (fn == "web_prepare_download") 60000 else if (fn == "web_verify_purchase") 30000 else 15000

    // Blocking. Only for background threads (the sync worker); screens use call().
    fun request(fn: String, args: JSONObject): ApiResult {
        var conn: HttpURLConnection? = null
        var result = ApiResult(null, "ERROR")
        try {
            // Purchases are checked with Google by a Supabase Edge Function; everything else is a database function.
            val path = if (fn == "web_verify_purchase") "/functions/v1/verify-purchase" else "/rest/v1/rpc/$fn"
            val url = URL(Config.SUPABASE_URL.trimEnd('/') + path)
            val c = url.openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = readTimeout(fn)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY)
            c.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY)
            c.outputStream.use { it.write(args.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            result = if (code in 200..299) {
                val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                ApiResult(JSONObject(text), null)
            } else {
                ApiResult(null, "HTTP_$code")
            }
        } catch (e: SocketTimeoutException) {
            result = ApiResult(null, "TIMEOUT")
        } catch (e: IOException) {
            result = ApiResult(null, "NETWORK")
        } catch (e: Exception) {
            result = ApiResult(null, "ERROR")
        } finally {
            conn?.disconnect()
        }
        return result
    }

    // Plain-language message for a failed call (no answer from the database).
    fun netMessage(error: String?): String = when (error) {
        "NETWORK" -> "No connection. Check your data and try again."
        "TIMEOUT" -> "The connection is too slow. Please try again."
        else -> "Something went wrong. Please try again."
    }

    // Plain-language message when the database answered "ok: false" to a log in.
    fun loginMessage(j: JSONObject): String {
        val code = j.optString("code")
        val left = j.optInt("attempts_left", 0)
        return when {
            code == "WRONG_PIN" && left > 0 ->
                "Wrong password. $left " + (if (left == 1) "try" else "tries") + " left before it locks."
            code == "WRONG_PIN" -> "Wrong password, or this account cannot log in right now. Message the admin for help."
            code == "INVALID_PHONE" -> "Enter a valid Nigerian number."
            code == "NOT_FOUND" -> "This number is not registered yet. Tap Sign up below."
            code == "BANNED" -> "This account has been banned."
            code == "SESSION_EXPIRED" -> "You were logged out. Please log in again."
            code == "ACCOUNT_DELETED" -> "This account was deleted. To use this number again, message the admin to reactivate it."
            code == "LOCKED" -> "This password is locked after too many wrong tries. Message the admin from your registered number to get a new one."
            else -> "Could not check. Please try again."
        }
    }
}
