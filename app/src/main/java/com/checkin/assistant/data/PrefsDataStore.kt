package com.checkin.assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.checkin.assistant.core.checkin.CheckInConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "checkin_prefs")

/**
 * 运行时配置与登录态。
 *
 * 设计原则：签到接口的 URL / 请求体模板 / 成功判定 都可以在设置页改，
 * 因为校方接口字段无法事先确定，需要抓一次真实请求后对着调。
 */
data class AppPrefs(
    val loginState: String = "NEED_RELOGIN",       // LOGGED_IN / NEED_RELOGIN
    val accessToken: String = "",
    val refreshToken: String = "",
    val wxtoken: String = "",
    val tokenExpireAt: Long = 0L,

    // 账号密码（密码为 Keystore 加密后的密文，见 SecureStore）
    val savedUser: String = "",
    val passwordBlob: String = "",

    // 实习计划（由 Settings 接口回填）
    val planId: String = "",
    val planName: String = "",
    val studentName: String = "",
    val studentNo: String = "",
    val companyName: String = "",
    val companyAddress: String = "",
    val companyLat: Double = 0.0,
    val companyLng: Double = 0.0,
    val signRange: Double = 0.0,
    val itinerancy: Int = 0,          // 1 = 外勤模式，此时不校验距离

    // 今日状态（由 Info 接口回填）
    val signToday: Boolean = false,
    val totalSignCount: Int = 0,
    /** 本机最后一次「签到成功」的时间戳，用于抵消服务端 Info 的滞后 */
    val lastSignAt: Long = 0L,

    // 最近一次真实定位（仅展示用，不做任何改写）
    val lastLat: Double = 0.0,
    val lastLng: Double = 0.0,
    val lastLocTime: Long = 0L,
    val lastAccuracy: Float = 0f,

    // 每日提醒
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,

    // 每日后台自动签到（不需要打开 App）
    val autoSignEnabled: Boolean = true,
    val autoSignHour: Int = 8,
    val autoSignMinute: Int = 5,

    // 接口配置
    val signUrl: String = CheckInConfig.DEFAULT_SIGN_URL,
    val signBody: String = CheckInConfig.DEFAULT_SIGN_BODY,
    val successKeyword: String = CheckInConfig.DEFAULT_SUCCESS_KEYWORD,
    val settingsUrl: String = CheckInConfig.SETTINGS
) {
    /** 是否已保存可用于自动登录的密码 */
    val hasPassword: Boolean get() = passwordBlob.isNotBlank()
}

@Singleton
class PrefsRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    private object Keys {
        val LOGIN_STATE = stringPreferencesKey("login_state")
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val WXTOKEN = stringPreferencesKey("wxtoken")
        val TOKEN_EXP = longPreferencesKey("token_expire_at")

        val SAVED_USER = stringPreferencesKey("saved_user")
        val PASSWORD_BLOB = stringPreferencesKey("password_blob")

        val PLAN_ID = stringPreferencesKey("plan_id")
        val PLAN_NAME = stringPreferencesKey("plan_name")
        val STU_NAME = stringPreferencesKey("student_name")
        val STU_NO = stringPreferencesKey("student_no")
        val CO_NAME = stringPreferencesKey("company_name")
        val CO_ADDR = stringPreferencesKey("company_address")
        val CO_LAT = doublePreferencesKey("company_lat")
        val CO_LNG = doublePreferencesKey("company_lng")
        val RANGE = doublePreferencesKey("sign_range")
        val ITINERANCY = intPreferencesKey("itinerancy")
        val SIGN_TODAY = booleanPreferencesKey("sign_today")
        val TOTAL_COUNT = intPreferencesKey("total_sign_count")
        val LAST_SIGN_AT = longPreferencesKey("last_sign_at")

        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LNG = doublePreferencesKey("last_lng")
        val LAST_TS = longPreferencesKey("last_loc_time")
        val LAST_ACC = floatPreferencesKey("last_accuracy")

        val REMINDER_ON = booleanPreferencesKey("reminder_enabled")
        val REMINDER_H = intPreferencesKey("reminder_hour")
        val REMINDER_M = intPreferencesKey("reminder_minute")

        val AUTO_SIGN_ON = booleanPreferencesKey("auto_sign_enabled")
        val AUTO_SIGN_H = intPreferencesKey("auto_sign_hour")
        val AUTO_SIGN_M = intPreferencesKey("auto_sign_minute")

        val SIGN_URL = stringPreferencesKey("sign_url")
        val SIGN_BODY = stringPreferencesKey("sign_body")
        val SUCCESS_KEY = stringPreferencesKey("success_keyword")
        val SETTINGS_URL = stringPreferencesKey("settings_url")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            loginState = p[Keys.LOGIN_STATE] ?: "NEED_RELOGIN",
            accessToken = p[Keys.ACCESS] ?: "",
            refreshToken = p[Keys.REFRESH] ?: "",
            wxtoken = p[Keys.WXTOKEN] ?: "",
            tokenExpireAt = p[Keys.TOKEN_EXP] ?: 0L,
            savedUser = p[Keys.SAVED_USER] ?: "",
            passwordBlob = p[Keys.PASSWORD_BLOB] ?: "",
            planId = p[Keys.PLAN_ID] ?: "",
            planName = p[Keys.PLAN_NAME] ?: "",
            studentName = p[Keys.STU_NAME] ?: "",
            studentNo = p[Keys.STU_NO] ?: "",
            companyName = p[Keys.CO_NAME] ?: "",
            companyAddress = p[Keys.CO_ADDR] ?: "",
            companyLat = p[Keys.CO_LAT] ?: 0.0,
            companyLng = p[Keys.CO_LNG] ?: 0.0,
            signRange = p[Keys.RANGE] ?: 0.0,
            itinerancy = p[Keys.ITINERANCY] ?: 0,
            signToday = p[Keys.SIGN_TODAY] ?: false,
            totalSignCount = p[Keys.TOTAL_COUNT] ?: 0,
            lastSignAt = p[Keys.LAST_SIGN_AT] ?: 0L,
            lastLat = p[Keys.LAST_LAT] ?: 0.0,
            lastLng = p[Keys.LAST_LNG] ?: 0.0,
            lastLocTime = p[Keys.LAST_TS] ?: 0L,
            lastAccuracy = p[Keys.LAST_ACC] ?: 0f,
            reminderEnabled = p[Keys.REMINDER_ON] ?: false,
            reminderHour = p[Keys.REMINDER_H] ?: 8,
            reminderMinute = p[Keys.REMINDER_M] ?: 0,
            autoSignEnabled = p[Keys.AUTO_SIGN_ON] ?: true,
            autoSignHour = p[Keys.AUTO_SIGN_H] ?: 8,
            autoSignMinute = p[Keys.AUTO_SIGN_M] ?: 5,
            signUrl = p[Keys.SIGN_URL] ?: CheckInConfig.DEFAULT_SIGN_URL,
            signBody = p[Keys.SIGN_BODY] ?: CheckInConfig.DEFAULT_SIGN_BODY,
            successKeyword = p[Keys.SUCCESS_KEY] ?: CheckInConfig.DEFAULT_SUCCESS_KEYWORD,
            settingsUrl = p[Keys.SETTINGS_URL] ?: CheckInConfig.SETTINGS
        )
    }

    suspend fun update(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }

    suspend fun setLoggedIn(loggedIn: Boolean) =
        update { it[Keys.LOGIN_STATE] = if (loggedIn) "LOGGED_IN" else "NEED_RELOGIN" }

    suspend fun saveTokens(access: String, refresh: String, wxtk: String, expireAt: Long) =
        update {
            it[Keys.ACCESS] = access
            it[Keys.REFRESH] = refresh
            it[Keys.WXTOKEN] = wxtk
            it[Keys.TOKEN_EXP] = expireAt
            it[Keys.LOGIN_STATE] = "LOGGED_IN"
        }

    suspend fun savePlan(
        planId: String,
        planName: String,
        name: String,
        no: String,
        companyName: String = "",
        companyAddress: String = "",
        coLat: Double,
        coLng: Double,
        range: Double,
        itinerancy: Int = 0
    ) = update {
        if (planId.isNotBlank()) it[Keys.PLAN_ID] = planId
        if (planName.isNotBlank()) it[Keys.PLAN_NAME] = planName
        if (name.isNotBlank()) it[Keys.STU_NAME] = name
        if (no.isNotBlank()) it[Keys.STU_NO] = no
        if (companyName.isNotBlank()) it[Keys.CO_NAME] = companyName
        if (companyAddress.isNotBlank()) it[Keys.CO_ADDR] = companyAddress
        if (coLat != 0.0) it[Keys.CO_LAT] = coLat
        if (coLng != 0.0) it[Keys.CO_LNG] = coLng
        if (range != 0.0) it[Keys.RANGE] = range
        it[Keys.ITINERANCY] = itinerancy
    }

    suspend fun saveStatus(signToday: Boolean, total: Int, signAt: Long = 0L) = update {
        it[Keys.SIGN_TODAY] = signToday
        it[Keys.TOTAL_COUNT] = total
        if (signAt > 0L) it[Keys.LAST_SIGN_AT] = signAt
    }

    suspend fun saveLastLocation(lat: Double, lng: Double, accuracy: Float, ts: Long) = update {
        it[Keys.LAST_LAT] = lat
        it[Keys.LAST_LNG] = lng
        it[Keys.LAST_ACC] = accuracy
        it[Keys.LAST_TS] = ts
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = update {
        it[Keys.REMINDER_ON] = enabled
        it[Keys.REMINDER_H] = hour
        it[Keys.REMINDER_M] = minute
    }

    suspend fun setAutoSign(enabled: Boolean, hour: Int, minute: Int) = update {
        it[Keys.AUTO_SIGN_ON] = enabled
        it[Keys.AUTO_SIGN_H] = hour
        it[Keys.AUTO_SIGN_M] = minute
    }

    suspend fun saveApiConfig(
        signUrl: String,
        signBody: String,
        successKeyword: String,
        settingsUrl: String
    ) = update {
        it[Keys.SIGN_URL] = signUrl.trim()
        it[Keys.SIGN_BODY] = signBody
        it[Keys.SUCCESS_KEY] = successKeyword.trim()
        it[Keys.SETTINGS_URL] = settingsUrl.trim()
    }

    suspend fun resetApiConfig() = update {
        it.remove(Keys.SIGN_URL)
        it.remove(Keys.SIGN_BODY)
        it.remove(Keys.SUCCESS_KEY)
        it.remove(Keys.SETTINGS_URL)
    }

    suspend fun clearLogin() = update {
        it.remove(Keys.ACCESS)
        it.remove(Keys.REFRESH)
        it.remove(Keys.WXTOKEN)
        it.remove(Keys.PLAN_ID)
        it.remove(Keys.PLAN_NAME)
        it.remove(Keys.STU_NAME)
        it.remove(Keys.STU_NO)
        it[Keys.LOGIN_STATE] = "NEED_RELOGIN"
    }

    /** 保存账号与（已加密的）密码，用于到期自动重新登录 */
    suspend fun saveCredentials(user: String, passwordBlob: String) = update {
        it[Keys.SAVED_USER] = user.trim()
        it[Keys.PASSWORD_BLOB] = passwordBlob
    }

    /** 只删密码，保留账号名 */
    suspend fun clearCredentials() = update {
        it.remove(Keys.PASSWORD_BLOB)
    }
}
