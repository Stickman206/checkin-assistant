package com.checkin.assistant.core.checkin

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 简单的持久化 CookieJar，按 host 存一串 name=value。
 * 注意：每条 cookie 必须单独 build，不能复用同一个 Builder。
 */
@Singleton
class CookieStore @Inject constructor(@ApplicationContext ctx: Context) : CookieJar {

    private val sp: SharedPreferences =
        ctx.getSharedPreferences("okhttp_cookies", Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val raw = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        sp.edit().putString(url.host, raw).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = sp.getString(url.host, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        val expiresAt = System.currentTimeMillis() + 24 * 3600_000L
        return raw.split("; ")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            .mapNotNull { (n, v) ->
                runCatching {
                    Cookie.Builder()
                        .name(n)
                        .value(v)
                        .domain(url.host)
                        .path("/")
                        .expiresAt(expiresAt)
                        .build()
                }.getOrNull()
            }
    }
}
