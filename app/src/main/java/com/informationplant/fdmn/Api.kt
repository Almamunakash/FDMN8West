package com.informationplant.fdmn

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String, val code: String) : Exception(message)

object Api {
    /** Address of the PHP API on the hosting. */
    const val BASE = "https://app.informationplant.com/api/index.php"

    @Volatile var token: String = ""
    var onAuthLost: (() -> Unit)? = null

    suspend fun call(action: String, body: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL("$BASE?a=$action").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            if (token.isNotEmpty()) {
                conn.setRequestProperty("X-Auth-Token", token)
                if (!body.has("token")) body.put("token", token)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                ?: throw ApiException("Server error ($code). Please try again.", "http")
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = try {
                JSONObject(text.trim().removePrefix("﻿"))
            } catch (e: Exception) {
                throw ApiException("The server sent an unexpected reply (code $code). Please check the server setup.", "bad")
            }
            if (!json.optBoolean("ok")) {
                val c = json.optString("code", "error")
                if (c == "auth") withContext(Dispatchers.Main) { onAuthLost?.invoke() }
                throw ApiException(json.optString("error", "Something went wrong."), c)
            }
            json
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("No internet connection, or the server could not be reached.", "network")
        } finally {
            conn.disconnect()
        }
    }
}

fun jo(vararg pairs: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    for ((k, v) in pairs) o.put(k, v ?: JSONObject.NULL)
    return o
}

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) out.add(f(getJSONObject(i)))
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("fdmn", Context.MODE_PRIVATE)
    }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v).apply() }

    var userJson: String
        get() = sp.getString("user", "") ?: ""
        set(v) { sp.edit().putString("user", v).apply() }

    var lastEmail: String
        get() = sp.getString("email", "") ?: ""
        set(v) { sp.edit().putString("email", v).apply() }
}
