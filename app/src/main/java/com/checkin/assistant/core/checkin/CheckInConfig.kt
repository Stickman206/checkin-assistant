package com.checkin.assistant.core.checkin

import com.checkin.assistant.BuildConfig
import java.util.Locale

/**
 * 接口常量与默认值。
 *
 * ★ 域名不写进源码。
 *   真实值放在 local.properties（已被 .gitignore 排除）：
 *
 *     api.base=https://<API_HOST>/api
 *     h5.base=https://<H5_HOST>
 *
 *   构建时注入成 BuildConfig.API_BASE / BuildConfig.H5_BASE，
 *   仓库里只有占位值，clone 下来不改任何东西也能编译。
 *
 * 接口路径（来自抓包 + 站点 JS bundle 交叉验证，不是推断）：
 *
 *   签到          POST /api/studentapp/Sign        body {"Lat":..,"Lng":..}
 *   计划信息      POST /api/studentapp/Settings    body {}
 *   学生状态      POST /api/studentapp/Info        body {}
 *   帐号密码登录  POST /api/studentapp/Login       body {"UserName":..,"Password":..}
 *   微信登录      POST /api/studentapp/WxLogin     body {"MPCode":..,"MPState":"zlink"}
 *
 * 请求头实测：authorization: Bearer <jwt> / platform: h5 / wxtoken: <32位十六进制>
 * 成功判定：响应 JSON 的 Code == 0
 * 响应外壳：正文是一层 base64，解出来才是 {Code, Data, Msg}
 * 坐标系：GCJ-02，提交前需由 WGS-84 转换
 *
 * ★ wxtoken 就是 WxLogin 响应里的 Msg，形如 <32 位大写十六进制>，
 *   同一账号多次登录值不变，可以长期复用。
 *
 * ★ token 有效期只有 24 小时（expires_in=86400，JWT 的 exp 完全吻合），
 *   且前端没有任何 refresh 接口——401 时是跳回微信重新授权。
 */
object CheckInConfig {

    /** 形如 https://<API_HOST>/api */
    val API_BASE: String = BuildConfig.API_BASE

    /** 形如 https://<H5_HOST>，只用于拼 Referer */
    val H5_BASE: String = BuildConfig.H5_BASE

    const val PLATFORM = "h5"
    val REFERER: String = "$H5_BASE/"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; MicroMessenger/8.0.49) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** 实习计划：单位名称/坐标/签到半径/是否外勤/学生姓名 */
    val SETTINGS: String = "$API_BASE/studentapp/Settings"

    /** 学生状态：SignToday / TotalSignCount / StudentName */
    val INFO: String = "$API_BASE/studentapp/Info"

    /** 签到提交 */
    val DEFAULT_SIGN_URL: String = "$API_BASE/studentapp/Sign"

    /**
     * 微信登录接口。需要微信 OAuth 回调里的 MPCode，App 内拿不到，
     * 代码里已不再调用，常量留着只为记录接口形状。
     */
    val WX_LOGIN: String = "$API_BASE/studentapp/WxLogin"

    /**
     * 帐号密码登录。来自 pages/login 的打包代码（js/1.<hash>.js，模块 165）：
     *
     *   var i = t.detail.value;              // 表单值，字段名就是 UserName / Password
     *   Object(E.b)(x.o, i)                  // x.o === "/api/studentapp/Login"
     *
     * 响应与 WxLogin 同构：Data 里是 {access_token, expires_in, token_type, refresh_token}，
     * 前端在 Code==0 时把它整体存进 localStorage.token，authorization 用
     * token_type + " " + access_token 拼出来。
     * Code==9901 表示「当前密码太弱」，必须先改密码。
     */
    val LOGIN: String = "$API_BASE/studentapp/Login"

    /** 登录请求体：表单值原样 JSON 化，字段名大小写敏感 */
    const val DEFAULT_LOGIN_BODY = """{"UserName":"{user}","Password":"{pass}"}"""

    /**
     * 续期接口——**实测不存在**。
     *
     * 用真实 token 发 POST /api/studentapp/RefreshToken，服务端返回 HTTP 404（空正文）。
     * 与前端 bundle 的结论一致：401 时直接跳 mpredirect 重走微信授权。
     * 常量留着只为记录这个负面结论，代码里已不再调用。
     */
    val REFRESH_PROBE: String = "$API_BASE/studentapp/RefreshToken"

    /**
     * 签到请求体模板。抓包与 JS 源码均确认只有两个字段：
     *   Object(y.b)(w.w, {"Lat": l.latitude, "Lng": l.longitude})
     *
     * 支持占位符：{lat} {lng} {studentNo} {studentName} {timestamp}
     * （{planId} 保留兼容，但真实接口不需要）
     */
    const val DEFAULT_SIGN_BODY = """{"Lat":{lat},"Lng":{lng}}"""

    /** 兜底判定关键字；正常路径走 JSON 的 Code==0 */
    const val DEFAULT_SUCCESS_KEYWORD = "\"Code\":0"

    fun renderBody(
        template: String,
        planId: String,
        studentNo: String,
        studentName: String,
        lat: Double,
        lng: Double,
        timestamp: Long
    ): String = template
        .replace("{planId}", planId)
        .replace("{studentNo}", studentNo)
        .replace("{studentName}", studentName)
        .replace("{lat}", fmt(lat))
        .replace("{lng}", fmt(lng))
        .replace("{timestamp}", timestamp.toString())

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)
}
