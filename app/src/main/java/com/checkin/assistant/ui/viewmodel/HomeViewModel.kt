package com.checkin.assistant.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.assistant.core.checkin.CheckInManager
import com.checkin.assistant.core.checkin.CheckInOutcome
import com.checkin.assistant.core.checkin.CheckInResult
import com.checkin.assistant.core.location.RealLocationProvider
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.data.CheckInDao
import com.checkin.assistant.data.CheckInRecord
import com.checkin.assistant.data.PrefsRepository
import com.checkin.assistant.schedule.AutoSignScheduler
import com.checkin.assistant.schedule.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: PrefsRepository,
    private val dao: CheckInDao,
    private val manager: CheckInManager,
    private val location: RealLocationProvider
) : ViewModel() {

    val appPrefs: StateFlow<AppPrefs> =
        prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPrefs())

    val latest: StateFlow<CheckInRecord?> =
        dao.latest().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _outcome = MutableStateFlow<CheckInOutcome?>(null)
    val outcome: StateFlow<CheckInOutcome?> = _outcome.asStateFlow()

    fun hasLocationPermission(): Boolean = location.hasPermission()

    fun hasBackgroundLocation(): Boolean = location.hasBackgroundLocation()

    fun needsBackgroundLocation(): Boolean = location.needsBackgroundLocation()

    init {
        // 启动即跑一遍完整流程：保活 → 拉计划/状态 → 今天还没签就直接签
        viewModelScope.launch {
            val p = prefs.prefs.first()
            if (p.accessToken.isBlank() && !p.hasPassword) return@launch

            manager.ensureSession(minHoursLeft = 3)
            manager.refreshAll()

            val now = prefs.prefs.first()
            if (now.autoSignEnabled && !now.signToday && now.loginState == "LOGGED_IN") {
                if (!location.hasPermission()) {
                    _toast.value = "开了自动签到，但没有定位权限，请先授权"
                    return@launch
                }
                _busy.value = true
                val o = manager.checkIn()
                _outcome.value = o
                _toast.value = when (val r = o.result) {
                    is CheckInResult.Success -> r.msg
                    is CheckInResult.NeedRelogin -> "自动签到：登录已失效，请重新登录"
                    is CheckInResult.Failed -> "自动签到失败：${r.msg}"
                }
                _busy.value = false
            }
        }
    }

    fun refreshPlan() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val r = manager.refreshAll()) {
                is CheckInResult.Success -> _toast.value = "已更新：${r.msg}"
                is CheckInResult.NeedRelogin -> _toast.value = "登录已失效，请重新填写凭证"
                is CheckInResult.Failed -> _toast.value = r.msg
            }
            _busy.value = false
        }
    }

    fun manualCheckIn() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val o = manager.checkIn()
            _outcome.value = o
            _toast.value = when (val r = o.result) {
                is CheckInResult.Success -> r.msg
                is CheckInResult.NeedRelogin -> "登录已失效，请到「登录凭证」重新填写"
                is CheckInResult.Failed -> "签到失败：${r.msg}"
            }
            _busy.value = false
        }
    }

    /** 只刷新定位展示，不签到 */
    fun refreshLocation() {
        viewModelScope.launch {
            val fix = location.currentGcj02()
            if (fix == null) {
                _toast.value = when {
                    !location.hasPermission() -> "没有定位权限"
                    !location.isLocationEnabled() -> "系统定位开关未打开"
                    else -> "定位超时，请重试"
                }
                return@launch
            }
            prefs.saveLastLocation(fix.lat, fix.lng, fix.accuracy, System.currentTimeMillis())
            _toast.value = "定位已刷新"
        }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute)
            if (enabled) ReminderScheduler.scheduleNext(ctx, hour, minute)
            else ReminderScheduler.cancel(ctx)
        }
    }

    /** 开关「每日后台自动签到」，顺带重排/取消后台任务 */
    fun setAutoSign(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setAutoSign(enabled, hour, minute)
            if (enabled) AutoSignScheduler.scheduleNext(ctx, hour, minute)
            else AutoSignScheduler.cancel(ctx)
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
