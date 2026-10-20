package com.checkin.assistant.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.assistant.core.checkin.CheckInManager
import com.checkin.assistant.core.checkin.CheckInResult
import com.checkin.assistant.core.checkin.Jwt
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.data.PrefsRepository
import com.checkin.assistant.schedule.SessionScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CredentialViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    private val manager: CheckInManager,
    @ApplicationContext private val app: Context
) : ViewModel() {

    val appPrefs: StateFlow<AppPrefs> =
        prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPrefs())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 保存 token 并立刻打一次 Settings 接口验证 */
    fun saveAndValidate(accessToken: String, wxtoken: String, refreshToken: String) {
        // 粘贴内容常夹带全角冒号、换行、标签文字，这里只把 JWT 本体抠出来
        val at = Jwt.extract(accessToken)
        val wx = Jwt.hexOnly(wxtoken)
        val rt = Jwt.hexOnly(refreshToken)

        if (at.isBlank()) {
            _message.value = "access_token 不能为空"
            return
        }
        if (at.count { it == '.' } != 2) {
            _message.value = "没认出 JWT：应有 3 段、2 个点，提取后长度 ${at.length}。" +
                "请只复制 eyJ 开头到最后一个点后面那段"
            return
        }
        val dirty = accessToken.filter { !it.isWhitespace() } != at

        viewModelScope.launch {
            _busy.value = true
            // 实测服务端 JWT 有效期为 24 小时，直接从 payload 的 exp 读取
            val exp = Jwt.expiryMillis(at)
            prefs.saveTokens(at, rt, wx, exp)
            _message.value =
                if (dirty) "已自动去掉多余字符（长度 ${at.length}），正在验证…" else "已保存，正在验证…"
            when (val r = manager.refreshAll()) {
                is CheckInResult.Success -> {
                    val left = Jwt.hoursLeft(at)
                    _message.value = buildString {
                        append("验证成功：${r.msg}")
                        if (exp > 0) {
                            append("；凭证有效至 ")
                            append(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(exp)))
                            append("（剩 $left 小时）")
                        }
                    }
                }

                is CheckInResult.NeedRelogin -> _message.value = "验证失败：token 无效或已过期"
                is CheckInResult.Failed -> _message.value = friendly(r.msg)
            }
            _busy.value = false
        }
    }

    /** 把 OkHttp 的底层报错翻译成人话 */
    private fun friendly(msg: String): String = when {
        msg.contains("Unexpected char 0xff1a") ->
            "token 里混进了全角冒号「：」，已自动清理，请重试一次"

        msg.contains("Unexpected char") && msg.contains("authorization") ->
            "token 里有非法字符，已自动清理，请重试一次"

        msg.contains("Unexpected char") && msg.contains("wxtoken") ->
            "wxtoken 里有非法字符，应为 32 位十六进制"

        else -> "验证失败：$msg"
    }

    fun logout() = viewModelScope.launch {
        prefs.clearLogin()
        _message.value = "已清除登录状态"
    }

    /**
     * 帐号密码登录。
     *
     * 成功后会顺手拉一次实习计划与今日状态，并把「凭证保活」排上，
     * 这样以后 token 到期 App 会自己续，不用再抓包。
     */
    fun loginWithPassword(user: String, pass: String, remember: Boolean) {
        if (user.isBlank() || pass.isBlank()) {
            _message.value = "帐号和密码都要填"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            when (val r = manager.loginAndRemember(user, pass, remember)) {
                is CheckInResult.Success -> {
                    val refreshed = manager.refreshAll()
                    _message.value = buildString {
                        append(r.msg)
                        if (refreshed is CheckInResult.Success) append("；${refreshed.msg}")
                        if (remember) append("；已记住账号密码，凭证到期会自动续")
                    }
                    if (remember) {
                        // 保活任务排上；失败不影响登录结果
                        runCatching { SessionScheduler.scheduleNext(app) }
                    }
                }

                is CheckInResult.NeedRelogin -> _message.value = "登录失败：需要重新授权"
                is CheckInResult.Failed -> _message.value = "登录失败：${r.msg}"
            }
            _busy.value = false
        }
    }

    /** 只删密码，保留帐号名 */
    fun forgetPassword() = viewModelScope.launch {
        prefs.clearCredentials()
        SessionScheduler.cancel(app)
        _message.value = "已删除保存的密码，凭证到期后需要手动登录"
    }

    fun consumeMessage() {
        _message.value = null
    }
}
