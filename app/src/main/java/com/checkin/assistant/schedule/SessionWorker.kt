package com.checkin.assistant.schedule

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.checkin.assistant.core.checkin.CheckInManager
import com.checkin.assistant.core.checkin.NetLog
import com.checkin.assistant.data.PrefsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 凭证保活。
 *
 * 服务端 token 只有 24 小时，且没有任何 refresh 接口，401 只能回微信重新授权。
 * 所以只要用户存了账号密码，就每 8 小时检查一次：剩不到 8 小时就用账号密码
 * 重新登录换一个新 token。这样用户永远不用再走微信那串流程。
 *
 * 同样用「一次性任务 + 执行后重排」而不是 PeriodicWork，理由同 ReminderWorker：
 * 避免周期漂移，也方便随时改间隔。
 */
@HiltWorker
class SessionWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PrefsRepository,
    private val manager: CheckInManager
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val p = prefs.prefs.first()
        if (!p.hasPassword || p.savedUser.isBlank()) {
            // 没存账号密码就没法续，直接停掉，避免每 8 小时空跑一次
            NetLog.info("未保存账号密码，凭证保活任务结束")
            return Result.success()
        }
        val issue = manager.ensureSession(minHoursLeft = 8)
        NetLog.info("凭证保活：${issue ?: "token 仍够用，本次未重新登录"}")
        SessionScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}

object SessionScheduler {

    private const val UNIQUE = "session_keepalive"
    private const val INTERVAL_HOURS = 8L

    fun scheduleNext(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<SessionWorker>()
            .setInitialDelay(INTERVAL_HOURS, TimeUnit.HOURS)
            .addTag(UNIQUE)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE)
    }
}
