package com.example.auth

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class NetworkCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(httpUrl: HttpUrl, cookies: List<Cookie>) {
        val domain = httpUrl.host
        cookieStore[domain] = cookies.toMutableList()
    }

    override fun loadForRequest(httpUrl: HttpUrl): List<Cookie> {
        val cookies: MutableList<Cookie> = ArrayList()
        val domain = httpUrl.host
        cookieStore[domain]?.let {
            cookies.addAll(it)
        }
        return cookies
    }
}