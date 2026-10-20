package com.checkin.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.checkin.assistant.data.PrefsRepository
import com.checkin.assistant.schedule.AutoSignScheduler
import com.checkin.assistant.schedule.SessionScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CheckInApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var prefs: PrefsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 每次启动都把后台任务重排一遍：
        //  - 存过账号密码 → 排「凭证保活」
        //  - 开了自动签到   → 排「每日自动签到」
        // WorkManager 的任务本身是持久化的，但用户可能在系统里强停过应用、
        // 或者换过设置，这里重排一次成本极低，能保证状态一定是对的。
        appScope.launch {
            val p = prefs.prefs.first()
            if (p.hasPassword) SessionScheduler.scheduleNext(this@CheckInApplication)
            if (p.autoSignEnabled) {
                AutoSignScheduler.scheduleNext(
                    this@CheckInApplication, p.autoSignHour, p.autoSignMinute
                )
            }
        }
    }
}
