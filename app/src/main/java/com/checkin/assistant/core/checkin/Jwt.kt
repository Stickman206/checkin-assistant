package com.checkin.assistant.core.checkin

import android.util.Base64
import org.json.JSONObject

/**
 * 解析服务端签发的 JWT（不验签，只读 payload）。
 *
 * 实测样本的 payload（已脱敏）：
 * {"sub":"<16 位帐号>","exp":1792580525,"iss":"http://localhost","aud":"http://localhost"}
 *
 * - sub  = 登录帐号（16 位数字，**不是**学号）
 * - exp  = 过期时间戳（秒）
 * - 登录接口同时返回 expires_in = 86400，与 exp 完全吻合：**有效期 24 小时**
 */
object Jwt {

    /** 标准 JWT：三段 base64url，以 eyJ 开头（即 JSON 的 `{`） */
    private val JWT_RE = Regex("""eyJ[A-Za-z0-9_\-]*\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+""")

    private fun isB64Url(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'

    /**
     * 从任意粘贴文本里抠出 JWT。
     *
     * 复制来源常常夹带东西：`authorization：Bearer eyJ...`、全角冒号、
     * 换行、引号、甚至中文标签。OkHttp 会直接抛
     * "Unexpected char 0xff1a in authorization value"（0xff1a 是全角冒号）。
     * 这里只认 base64url 字符和点，其它一律丢弃。
     */
    fun extract(raw: String): String {
        JWT_RE.find(raw)?.value?.let { return it }
        return raw.filter { isB64Url(it) || it == '.' }
    }

    /** wxtoken 是 32 位十六进制，只保留 hex 字符 */
    fun hexOnly(raw: String): String =
        raw.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(32)

    private fun payload(token: String): JSONObject? {
        val raw = extract(token)
        val parts = raw.split(".")
        if (parts.size < 2) return null
        return runCatching {
            val bytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            JSONObject(String(bytes, Charsets.UTF_8))
        }.getOrNull()
    }

    /** 过期时间（毫秒），解析失败返回 0 */
    fun expiryMillis(token: String): Long {
        val exp = payload(token)?.optLong("exp", 0L) ?: 0L
        return if (exp > 0) exp * 1000L else 0L
    }

    /** 学号 */
    fun subject(token: String): String = payload(token)?.optString("sub").orEmpty()

    /** 剩余天数，向上取整；已过期返回负数 */
    fun daysLeft(token: String): Int {
        val exp = expiryMillis(token)
        if (exp == 0L) return 0
        val diff = exp - System.currentTimeMillis()
        return if (diff <= 0) -1 else ((diff + 86_399_999L) / 86_400_000L).toInt()
    }

    /** 剩余小时数，向上取整；已过期返回负数。24 小时有效期的凭证看这个更直观 */
    fun hoursLeft(token: String): Int {
        val exp = expiryMillis(token)
        if (exp == 0L) return 0
        val diff = exp - System.currentTimeMillis()
        return if (diff <= 0) -1 else ((diff + 3_599_999L) / 3_600_000L).toInt()
    }

    /** 是否已过期；无法解析时不判定为过期，交给服务端 401 兜底 */
    fun isExpired(token: String): Boolean {
        val exp = expiryMillis(token)
        return exp != 0L && exp < System.currentTimeMillis()
    }
}
