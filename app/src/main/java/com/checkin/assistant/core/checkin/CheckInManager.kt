package com.checkin.assistant.core.checkin

import android.util.Base64
import com.checkin.assistant.core.location.RealLocationProvider
import com.checkin.assistant.core.security.SecureStore
import com.checkin.assistant.core.util.isSameDay
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.data.CheckInDao
import com.checkin.assistant.data.CheckInRecord
import com.checkin.assistant.data.PrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class CheckInResult {
    data class Success(val msg: String) : CheckInResult()
    data class Failed(val msg: String) : CheckInResult()
    object NeedRelogin : CheckInResult()
}

/** 一次签到尝试的完整现场，用于在界面上排查接口问题 */
data class CheckInOutcome(
    val result: CheckInResult,
    val requestBody: String = "",
    val rawResponse: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    /** 与单位的距离（米），未知为 null */
    val distance: Float? = null,
    /** 超出签到半径时的提示，未超出为 null */
    val rangeWarning: String? = null
)

/**
 * 签到控制器：读真实定位 → 转 GCJ-02 → 按模板拼请求体 → POST → 解析 → 落库。
 *
 * 接口参数全部来自抓包实测：
 *   POST https://&lt;API_HOST&gt;/api/studentapp/Sign
 *   authorization: Bearer <jwt>   platform: h5   content-type: application/json
 *   body: {"Lat":..,"Lng":..}   成功判定 Code == 0
 *
 * 不做任何定位改写，坐标就是手机当前所在位置。
 */
@Singleton
class CheckInManager @Inject constructor(
    private val client: OkHttpClient,
    private val prefs: PrefsRepository,
    private val dao: CheckInDao,
    private val location: RealLocationProvider
) {

    private val json = "application/json;charset=UTF-8".toMediaType()

    private fun newRequest(url: String, body: String, p: AppPrefs): Request =
        Request.Builder()
            .url(url)
            // 粘贴来源常夹带全角冒号、换行、标签文字，OkHttp 会抛
            // "Unexpected char 0xff1a in authorization value"（0xff1a 即全角冒号）
            .addHeader("authorization", "Bearer ${Jwt.extract(p.accessToken)}")
            .addHeader("wxtoken", Jwt.hexOnly(p.wxtoken))
            .addHeader("platform", CheckInConfig.PLATFORM)
            .addHeader("Referer", CheckInConfig.REFERER)
            .addHeader("User-Agent", CheckInConfig.USER_AGENT)
            .post(body.toRequestBody(json))
            .build()
            .also { NetLog.req(url, body, p.accessToken, p.wxtoken) }

    /**
     * 统一出口。
     *
     * OkHttp 的 execute() 是**阻塞**调用，而所有调用方都在 viewModelScope 里，
     * 默认调度器是 Dispatchers.Main.immediate —— 直接 execute() 必抛
     * NetworkOnMainThreadException。所有出网都必须经过这里切到 IO 线程。
     *
     * 返回 (HTTP 状态码, 响应正文)。
     */
    private suspend fun exec(url: String, req: Request): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                NetLog.resp(url, r.code, text)
                r.code to text
            }
        }

    /**
     * 登录专用请求：此时还没有 token，按前端实际行为把 authorization / wxtoken
     * 都发成空字符串（抓包里就是 `authorization: ''`、`wxtoken: ''`）。
     */
    private fun loginRequest(body: String): Request =
        Request.Builder()
            .url(CheckInConfig.LOGIN)
            .addHeader("authorization", "")
            .addHeader("wxtoken", "")
            .addHeader("platform", CheckInConfig.PLATFORM)
            .addHeader("Referer", CheckInConfig.REFERER)
            .addHeader("User-Agent", CheckInConfig.USER_AGENT)
            .post(body.toRequestBody(json))
            .build()
            .also { NetLog.req(CheckInConfig.LOGIN, body, "", "") }

    /**
     * 帐号密码登录，拿一个新的 24 小时 token。
     *
     * 请求体与 pages/login 完全一致：`{"UserName":..,"Password":..}`。
     * 响应 Data 与 WxLogin 同构，成功（含 9901）时前端会把它存进 localStorage。
     *
     * 9901 = 密码太弱：token 仍然给，但进系统后强制改密码。这里如实告知用户。
     */
    suspend fun login(userName: String, password: String): CheckInResult {
        val user = userName.trim()
        if (user.isEmpty() || password.isEmpty()) {
            return CheckInResult.Failed("帐号和密码都不能为空")
        }
        return try {
            val body = JSONObject()
                .put("UserName", user)
                .put("Password", password)
                .toString()
            val (httpCode, text) = exec(CheckInConfig.LOGIN, loginRequest(body))
            val obj = parseEnvelope(text)
                ?: return CheckInResult.Failed("登录响应无法解析：${prettyBody(text).take(200)}")
            val code = obj.optInt("Code", Int.MIN_VALUE)
            val msg = obj.optString("Msg").orEmpty()
            val d = obj.optJSONObject("Data")
            val token = d?.optString("access_token").orEmpty()

            if ((code != 0 && code != 9901) || token.isBlank()) {
                val hint = when (httpCode) {
                    401, 403 -> "帐号或密码不对（HTTP $httpCode）"
                    404 -> "登录接口不存在（HTTP 404），服务端可能换了路径"
                    else -> "HTTP $httpCode · Code=$code · ${msg.ifBlank { prettyBody(text).take(300) }}"
                }
                return CheckInResult.Failed(hint)
            }

            val at = Jwt.extract(token)
            val rt = Jwt.hexOnly(d?.optString("refresh_token").orEmpty())
            // WxLogin 用 Msg 装 wxtoken；Login 若也返回 32 位十六进制，就顺手更新
            val maybeWx = Jwt.hexOnly(msg)
            val wx = if (maybeWx.length == 32) maybeWx else prefs.prefs.first().wxtoken
            val expireAt = Jwt.expiryMillis(at).takeIf { it > 0 }
                ?: (System.currentTimeMillis() + (d?.optLong("expires_in", 86_400L) ?: 86_400L) * 1000L)

            prefs.saveTokens(at, rt, wx, expireAt)
            CheckInResult.Success(
                if (code == 9901) "登录成功，但服务端提示「密码太弱」，请尽快改密码"
                else "登录成功，凭证剩 ${Jwt.hoursLeft(at)} 小时"
            )
        } catch (e: Exception) {
            NetLog.err(e)
            CheckInResult.Failed(friendly(e))
        }
    }

    /** 登录并把账号密码保存下来（密码用 Keystore 加密后落盘） */
    suspend fun loginAndRemember(userName: String, password: String, remember: Boolean): CheckInResult {
        val r = login(userName, password)
        if (r is CheckInResult.Success) {
            if (remember) {
                val blob = SecureStore.encrypt(password)
                if (blob != null) prefs.saveCredentials(userName, blob)
                else return CheckInResult.Success("${r.msg}（本机 Keystore 不可用，密码没保存）")
            } else {
                prefs.clearCredentials()
            }
        }
        return r
    }

    /**
     * 保证手上有一个还能用一阵子的 token。
     *
     * 返回 null 表示可以直接用；否则返回一句给用户看的原因。
     * 只在「token 快过期」时才真的发登录请求，避免每次开 App 都登录一遍。
     */
    suspend fun ensureSession(minHoursLeft: Int = 3): String? {
        val p = prefs.prefs.first()
        val tokenOk = p.accessToken.isNotBlank() &&
            !Jwt.isExpired(p.accessToken) &&
            Jwt.hoursLeft(p.accessToken) > minHoursLeft
        if (tokenOk) return null

        if (!p.hasPassword || p.savedUser.isBlank()) {
            return if (p.accessToken.isBlank() || Jwt.isExpired(p.accessToken))
                "登录凭证已失效，且没有保存账号密码"
            else
                "登录凭证只剩 ${Jwt.hoursLeft(p.accessToken)} 小时，且没有保存账号密码"
        }

        val pass = SecureStore.decrypt(p.passwordBlob)
            ?: return "已保存的密码解不开（可能换过设备或重装过），请重新输入密码"

        NetLog.info("token 即将到期，用已保存的账号 ${p.savedUser} 自动登录")
        return when (val r = login(p.savedUser, pass)) {
            is CheckInResult.Success -> {
                record("AUTO_LOGIN", r.msg)
                null
            }

            is CheckInResult.NeedRelogin -> {
                record("NEED_RELOGIN", "自动登录被拒")
                "自动登录被拒，请手动登录一次"
            }

            is CheckInResult.Failed -> {
                record("FAILED", "自动登录失败：${r.msg}")
                "自动登录失败：${r.msg}"
            }
        }
    }

    /** 从 401 响应头里抠出 token 过期时间，给用户看得懂的原因 */
    private fun describe401(raw: String): String {
        val m = Regex("""The token expired at '([^']+)'""").find(raw)
        return if (m != null) "登录凭证已过期（${m.groupValues[1]}），请重新登录一次"
        else "登录凭证无效或已过期，请重新登录一次"
    }

    /**
     * 解析响应正文。
     *
     * 服务端 200 的正文是 **base64 再包一层** 的 JSON，例如
     *   eyJEYXRhIjp7Li4ufSwiQ29kZSI6MCwiTXNnIjoi5oiQ5YqfIn0=
     * 解出来才是 {"Data":{...},"Code":0,"Msg":"成功"}。
     * base64 编码的 "{" 恒以 eyJ 开头，可据此判断。
     *
     * 401 时正文是纯文本（Bearer error="invalid_token"...），走 JSON 解析会失败，
     * 由调用方单独处理。
     */
    private fun parseEnvelope(text: String): JSONObject? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("eyJ")) {
            val decoded = runCatching {
                String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
            if (decoded != null) {
                runCatching { JSONObject(decoded) }.getOrNull()?.let { return it }
            }
        }
        return runCatching { JSONObject(t) }.getOrNull()
    }

    /** 剥掉 base64 外壳，用于在界面上展示可读的响应 */
    private fun prettyBody(text: String): String {
        val t = text.trim()
        if (t.startsWith("eyJ")) {
            val decoded = runCatching {
                String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
            if (decoded != null && decoded.trimStart().startsWith("{")) return decoded
        }
        return text
    }

    /** 服务端返回 {Code,Data:{...}}，取 Data */
    private fun dataOf(obj: JSONObject): JSONObject = obj.optJSONObject("Data") ?: obj

    /**
     * 拉取实习计划：单位名称/坐标/签到半径/外勤开关/学生姓名。
     * 同时充当 token 有效性校验。
     */
    suspend fun fetchSettings(): CheckInResult {
        val p = prefs.prefs.first()
        if (p.accessToken.isBlank()) return CheckInResult.NeedRelogin
        return try {
            val (httpCode, text) = exec(p.settingsUrl, newRequest(p.settingsUrl, "{}", p))
            if (httpCode == 401 || httpCode == 403) {
                prefs.setLoggedIn(false)
                return CheckInResult.Failed(describe401(text))
            }
            val obj = parseEnvelope(text)
                ?: return CheckInResult.Failed("响应无法解析：${prettyBody(text).take(200)}")
            val code = obj.optInt("Code", Int.MIN_VALUE)
            if (code != 0) {
                return CheckInResult.Failed("Code=$code ${obj.optString("Msg").orEmpty()}".trim())
            }
            val d = dataOf(obj)

            val name = pick(d, "StudentName", "Name")
            val coName = pick(d, "CompanyName")
            val coAddr = pick(d, "CompanyAddress")
            prefs.savePlan(
                planId = pick(d, "PlanId", "Id"),
                planName = pick(d, "PlanName"),
                name = name,
                no = pick(d, "StudentNo", "No"),
                companyName = coName,
                companyAddress = coAddr,
                coLat = pickD(d, "CompanyLat"),
                coLng = pickD(d, "CompanyLng"),
                range = pickD(d, "SignRange", "Range"),
                // 实测返回的是 JSON 布尔 false，不是数字，必须用 optBoolean
                itinerancy = if (d.optBoolean("Itinerancy", false)) 1 else 0
            )
            CheckInResult.Success(
                coName.ifBlank { coAddr }.ifBlank { name }.ifBlank { "已获取实习计划" }
            )
        } catch (e: Exception) {
            NetLog.err(e)
            CheckInResult.Failed(friendly(e))
        }
    }

    /** 拉取今日状态：是否已签到 / 累计签到次数 */
    suspend fun fetchInfo(): CheckInResult {
        val p = prefs.prefs.first()
        if (p.accessToken.isBlank()) return CheckInResult.NeedRelogin
        return try {
            val (httpCode, text) = exec(CheckInConfig.INFO, newRequest(CheckInConfig.INFO, "{}", p))
            if (httpCode == 401 || httpCode == 403) {
                prefs.setLoggedIn(false)
                return CheckInResult.Failed(describe401(text))
            }
            val obj = parseEnvelope(text)
                ?: return CheckInResult.Failed("响应无法解析：${prettyBody(text).take(200)}")
            if (obj.optInt("Code", Int.MIN_VALUE) != 0) {
                return CheckInResult.Failed("Code=${obj.optInt("Code")} ${obj.optString("Msg")}".trim())
            }
            val d = dataOf(obj)
            // 实测服务端 Info 有明显滞后：签到成功后 40 秒仍返回 SignToday=false、
            // TotalSignCount 不变。所以以「本机今天确实签成功过」为准，避免刚签完
            // 界面又跳回「今日未签」；累计次数只增不减。
            val prev = p
            val signedTodayLocally = prev.lastSignAt > 0L && isSameDay(prev.lastSignAt)
            val today = d.optBoolean("SignToday", false) || signedTodayLocally
            val total = maxOf(d.optInt("TotalSignCount", 0), prev.totalSignCount)
            prefs.saveStatus(today, total)
            if (pick(d, "StudentName").isNotBlank()) {
                prefs.savePlan(
                    planId = "", planName = "",
                    name = pick(d, "StudentName"), no = "",
                    coLat = 0.0, coLng = 0.0, range = 0.0
                )
            }
            CheckInResult.Success(if (today) "今日已签到" else "今日未签到")
        } catch (e: Exception) {
            NetLog.err(e)
            CheckInResult.Failed(friendly(e))
        }
    }

    /** 设置页/首页进来时刷新一次计划 + 今日状态 */
    suspend fun refreshAll(): CheckInResult {
        val s = fetchSettings()
        if (s is CheckInResult.Success) fetchInfo()
        return s
    }

    suspend fun checkIn(): CheckInOutcome {
        // token 缺失或已过期时，先用保存的账号密码自动续一个
        val sessionIssue = ensureSession(minHoursLeft = 1)
        var p = prefs.prefs.first()

        if (p.accessToken.isBlank() || Jwt.isExpired(p.accessToken)) {
            val m = sessionIssue ?: "未登录：缺少 access_token"
            record("NEED_RELOGIN", m)
            return CheckInOutcome(CheckInResult.NeedRelogin)
        }

        // 首次签到前先拿一次计划信息（单位坐标 + 签到半径）
        if (p.companyLat == 0.0 || p.companyLng == 0.0) {
            when (val r = fetchSettings()) {
                is CheckInResult.NeedRelogin -> {
                    record("NEED_RELOGIN", "登录已失效")
                    return CheckInOutcome(r)
                }

                is CheckInResult.Failed -> {
                    record("FAILED", "拉取实习计划失败：${r.msg}")
                    return CheckInOutcome(r)
                }

                is CheckInResult.Success -> Unit
            }
            p = prefs.prefs.first()
        }

        val fix = location.currentGcj02()
        if (fix == null) {
            val m = if (!location.hasPermission()) {
                "没有定位权限，无法签到"
            } else if (!location.isLocationEnabled()) {
                "系统定位开关是关的，请打开 GPS 或网络定位"
            } else {
                "定位超时，请到窗边/户外重试"
            }
            record("FAILED", m)
            return CheckInOutcome(CheckInResult.Failed(m))
        }

        prefs.saveLastLocation(fix.lat, fix.lng, fix.accuracy, System.currentTimeMillis())

        val dist = location.distanceTo(fix.lat, fix.lng, p.companyLat, p.companyLng)
        NetLog.info(
            "定位 GCJ-02=%.6f,%.6f 精度=±%dm 距离=%sm 半径=%sm 外勤=%d 今日已签=%s"
                .format(fix.lat, fix.lng, fix.accuracy.toInt(), dist?.toInt(), p.signRange.toInt(), p.itinerancy, p.signToday)
        )
        val warning = when {
            dist == null -> null
            p.signRange <= 0.0 -> null
            p.itinerancy == 1 -> null
            dist > p.signRange ->
                "当前距离单位 ${dist.toInt()}m，超出签到范围 ${p.signRange.toInt()}m。" +
                    "若单位坐标配置有误，请联系指导老师核对，否则可能被后台记为异常。"

            else -> null
        }

        val body = CheckInConfig.renderBody(
            template = p.signBody,
            planId = p.planId,
            studentNo = p.studentNo,
            studentName = p.studentName,
            lat = fix.lat,
            lng = fix.lng,
            timestamp = System.currentTimeMillis()
        )

        return try {
            val (httpCode, text) = exec(p.signUrl, newRequest(p.signUrl, body, p))
            val obj = parseEnvelope(text)
            val code = obj?.optInt("Code", Int.MIN_VALUE) ?: Int.MIN_VALUE
            val msg = obj?.optString("Msg").orEmpty()

            // 服务端「今天已经签过」时返回的是 Code=1 + Msg="2026-10-20 已经签到过了"，
            // 这不是失败，但也不能当成新签一次（累计次数不该 +1）
            val alreadySigned = msg.contains("已经签到") ||
                msg.contains("签到过了") ||
                msg.contains("已签到")

            val result = when {
                httpCode == 401 || httpCode == 403 -> CheckInResult.NeedRelogin
                code == 0 -> CheckInResult.Success(msg.ifBlank { "签到成功" })
                alreadySigned -> CheckInResult.Success(msg.ifBlank { "今日已签到" })
                p.successKeyword.isNotBlank() && text.contains(p.successKeyword) ->
                    CheckInResult.Success(msg.ifBlank { "签到成功" })

                else -> CheckInResult.Failed(
                    "HTTP $httpCode" + if (msg.isNotBlank()) " · $msg" else " · ${prettyBody(text).take(300)}"
                )
            }

            when (result) {
                is CheckInResult.Success -> {
                    record("SUCCESS", result.msg)
                    // 只有真正新签的那一次才 +1。
                    // 注意：不能在这里再调 fetchInfo() 覆盖——实测 Info 的 SignToday /
                    // TotalSignCount 有明显滞后（签到成功后 40 秒仍返回 false / 5），
                    // 覆盖回去反而会把「今日已签」又刷成「未签」。
                    val base = prefs.prefs.first().totalSignCount
                    prefs.saveStatus(
                        true,
                        if (code == 0) base + 1 else base,
                        if (code == 0) System.currentTimeMillis() else 0L
                    )
                }

                is CheckInResult.NeedRelogin -> {
                    record("NEED_RELOGIN", describe401(text))
                    prefs.setLoggedIn(false)
                }

                is CheckInResult.Failed -> record("FAILED", result.msg)
            }

            CheckInOutcome(
                result = result,
                requestBody = body,
                rawResponse = prettyBody(text),
                lat = fix.lat,
                lng = fix.lng,
                accuracy = fix.accuracy,
                distance = dist,
                rangeWarning = warning
            )
        } catch (e: Exception) {
            NetLog.err(e)
            val m = friendly(e)
            record("FAILED", m)
            CheckInOutcome(CheckInResult.Failed(m), body, "", fix.lat, fix.lng, fix.accuracy, dist, warning)
        }
    }

    /**
     * 续期探测已删除。
     *
     * 2026-10-20 实测：`POST /api/studentapp/RefreshToken` 返回 **HTTP 404**，
     * 与前端 bundle 里「没有任何 refresh 接口、401 直接跳微信重新授权」的结论一致。
     * 服务端只认 24 小时 token，续期只能靠账号密码重新登录（见 [login]）。
     */

    /** 把 OkHttp 的底层报错翻译成人话 */
    private fun friendly(e: Exception): String {
        val m = e.message ?: return "网络异常"
        return when {
            m.contains("Unexpected char 0xff1a") ->
                "token 里混进了全角冒号「：」，已自动清理，请重试一次"

            m.contains("Unexpected char") && m.contains("authorization") ->
                "token 里有非法字符，已自动清理，请重试一次"

            m.contains("Unexpected char") && m.contains("wxtoken") ->
                "wxtoken 里有非法字符，应为 32 位十六进制"

            else -> m
        }
    }

    private suspend fun record(status: String, msg: String) {
        dao.insert(
            CheckInRecord(
                timestamp = System.currentTimeMillis(),
                status = status,
                message = msg,
                mode = "MANUAL"
            )
        )
    }

    private fun pick(o: JSONObject?, vararg keys: String): String {
        if (o == null) return ""
        for (k in keys) if (o.has(k)) return o.optString(k, "")
        val it = o.keys()
        while (it.hasNext()) {
            val key = it.next()
            if (keys.any { k -> k.equals(key, ignoreCase = true) }) return o.optString(key, "")
        }
        return ""
    }

    private fun pickD(o: JSONObject?, vararg keys: String): Double {
        val raw = pick(o, *keys)
        return raw.toDoubleOrNull() ?: 0.0
    }
}
