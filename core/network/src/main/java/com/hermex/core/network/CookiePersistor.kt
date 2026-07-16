package com.hermex.core.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists OkHttp cookies to SharedPreferences so session cookies
 * survive app restarts. Used by [NetworkCookieJar] as its backing store.
 */
class CookiePersistor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("hermex_cookies", Context.MODE_PRIVATE)

    /** Load all persisted cookies for a given host. */
    fun load(host: String): List<Cookie> {
        val json = prefs.getString(host, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                cookieFromJson(arr.getJSONObject(i))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Persist cookies for a host. Overwrites any previous cookies for that host. */
    fun save(host: String, cookies: List<Cookie>) {
        if (cookies.isEmpty()) {
            prefs.edit().remove(host).apply()
            return
        }
        val arr = JSONArray()
        for (cookie in cookies) {
            arr.put(cookieToJson(cookie))
        }
        prefs.edit().putString(host, arr.toString()).apply()
    }

    /** Remove all persisted cookies. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun cookieToJson(c: Cookie): JSONObject = JSONObject().apply {
        put("name", c.name)
        put("value", c.value)
        put("expiresAt", c.expiresAt)
        put("domain", c.domain)
    }

    private fun cookieFromJson(o: JSONObject): Cookie {
        val domain = o.optString("domain", "")
        return Cookie.Builder()
            .name(o.getString("name"))
            .value(o.getString("value"))
            .expiresAt(o.getLong("expiresAt"))
            .apply { if (domain.isNotEmpty()) domain(domain) }
            .build()
    }
}
