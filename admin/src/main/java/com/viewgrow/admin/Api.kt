package com.viewgrow.admin

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

// One answer from the database: the JSON, or an error word (NETWORK, TIMEOUT, HTTP_404, ERROR).
class ApiResult(val json: JSONObject?, val error: String?)

// Calls the same database functions the admin website uses, with the public key. Blocking: background threads only.
object Api {
    fun request(fn: String, args: JSONObject): ApiResult {
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(Config.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/" + fn).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            // Admin calls and the full member list can take a while on the server.
            c.readTimeout = 130000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY)
            c.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY)
            c.outputStream.use { it.write(args.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code in 200..299) {
                val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                ApiResult(JSONObject(text), null)
            } else ApiResult(null, "HTTP_$code")
        } catch (e: SocketTimeoutException) {
            ApiResult(null, "TIMEOUT")
        } catch (e: IOException) {
            ApiResult(null, "NETWORK")
        } catch (e: Exception) {
            ApiResult(null, "ERROR")
        } finally {
            conn?.disconnect()
        }
    }

    // An admin call: adds the admin key.
    fun admin(fn: String, args: JSONObject = JSONObject()): ApiResult = request(fn, args.put("p_key", Store.key))

    fun netMessage(error: String?): String = when (error) {
        "NETWORK" -> "No connection. Check your data and try again."
        "TIMEOUT" -> "The connection is too slow. Please try again."
        else -> "Something went wrong. Please try again."
    }

    // The admin key was refused (changed on the website, or too many wrong tries).
    fun keyRefused(j: JSONObject?): Boolean {
        val code = j?.optString("code") ?: return false
        return !j.optBoolean("ok") && (code == "UNAUTHORIZED" || code == "LOCKED")
    }
}
