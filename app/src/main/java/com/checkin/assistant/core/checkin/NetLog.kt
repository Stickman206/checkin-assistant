package com.checkin.assistant.core.checkin

import android.util.Base64
import android.util.Log

/**
 * 网络日志统一入口。
 *
 * Logcat 里过滤 `tag:CheckInNet` 就能看到每一次请求和响应的完整内容，
 * 不用在滚动日志里翻。
 *
 * 只打印**脱敏后**的凭证（前 12 位 + 长度），避免 token 明文进日志。
 */
object NetLog {

    const val TAG = "CheckInNet"

    private const val MAX = 1500

    /**
     * 请求体里的密码 / refresh_token 必须打码。
     *
     * Logcat 是全系统可读的，adb、厂商的「用户反馈」工具、甚至一些清理类 App
     * 都能抓到。登录请求体里有明文密码，绝不能原样落进日志。
     */
    private val SECRET_RE = Regex(
        """("(?i)(password|refreshtoken|refresh_token|access_token)"\s*:\s*")([^"]*)(")"""
    )

    private fun redact(body: String): String =
        SECRET_RE.replace(body) { m ->
            m.groupValues[1] + "***" + "(" + m.groupValues[3].length + "位)" + m.groupValues[4]
        }

    fun req(url: String, body: String, token: String, wxtoken: String) {
        Log.d(TAG, "→ $url")
        Log.d(TAG, "   body    = ${clip(redact(body))}")
        Log.d(TAG, "   token   = ${mask(token)}")
        Log.d(TAG, "   wxtoken = ${mask(wxtoken)}")
    }

    fun resp(url: String, code: Int, raw: String) {
        Log.d(TAG, "← HTTP $code  $url")
        Log.d(TAG, "   raw = ${clip(redact(raw))}")
        // 200 的正文是 base64 包一层的 JSON，顺手解出来，省得手动解
        decode(raw)?.let { Log.d(TAG, "   json= ${clip(redact(it))}") }
    }

    fun err(e: Throwable) {
        Log.e(TAG, "✗ ${e.javaClass.simpleName}: ${e.message}")
    }

    fun info(msg: String) = Log.d(TAG, msg)

    private fun mask(s: String): String {
        if (s.isBlank()) return "(空)"
        val head = s.take(12)
        return "$head…(共 ${s.length} 位)"
    }

    private fun clip(s: String): String {
        val one = s.replace("\n", " ").replace("\r", " ")
        return if (one.length <= MAX) one else one.take(MAX) + "…(共 ${s.length} 字符)"
    }

    private fun decode(raw: String): String? {
        val t = raw.trim()
        if (!t.startsWith("eyJ")) return null
        val d = runCatching {
            String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return if (d.trimStart().startsWith("{")) d else null
    }
}
