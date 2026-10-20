package com.checkin.assistant.schedule

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.checkin.assistant.core.checkin.CheckInManager
import com.checkin.assistant.core.checkin.CheckInResult
import com.checkin.assistant.core.checkin.Jwt
import com.checkin.assistant.core.checkin.NetLog
import com.checkin.assistant.core.location.RealLocationProvider
import com.checkin.assistant.core.notify.Notifications
import com.checkin.assistant.core.util.delayToNext
import com.checkin.assistant.core.util.isSameDay
import com.checkin.assistant.data.PrefsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 每日后台自动签到：**不需要打开 App**。
 *
 * 流程与首页手动签到完全一致（同一套 CheckInManager），区别只是由 WorkManager
 * 在后台定时唤起：
 *
 *   到点 → 检查是否已签 → 必要时用账号密码重新登录 → 读真实定位 → POST /Sign → 通知
 *
 * 几个现实约束（代码里都做了处理，但用户必须知道）：
 *  - Android 10+ 后台读定位需要「始终允许」定位权限，否则 worker 里拿不到任何位置
 *  - Doze 模式下 WorkManager 会被推迟，可能晚几分钟到几十分钟；失败会自动退避重试
 *  - 用户「强行停止」应用后，所有后台任务都会被系统取消，直到下次手动打开
 *
 * 定位失败会重试（最多 3 次，间隔 30/60 分钟），其他失败直接通知并排明天。
 */
@HiltWorker
class AutoSignWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PrefsRepository,
    private val manager: CheckInManager,
    private val location: RealLocationProvider
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val p = prefs.prefs.first()
        if (!p.autoSignEnabled) {
            NetLog.info("自动签到已关闭，本次不执行")
            return Result.success()
        }

        // 今天已经签过（手动或自动）就直接排明天，避免重复请求
        if (isSameDay(p.lastSignAt)) {
            NetLog.info("自动签到：今天已经签过了，跳过")
            scheduleTomorrow(p.autoSignHour, p.autoSignMinute)
            return Result.success()
        }

        // 没有任何凭证：静默跳过。App 里本来就没法用，每天推一条通知只会烦人。
        if (p.accessToken.isBlank() && !p.hasPassword) {
            NetLog.info("自动签到：没有可用凭证，跳过")
            scheduleTomorrow(p.autoSignHour, p.autoSignMinute)
            return Result.success()
        }

        // 权限类问题也静默跳过——首页卡片会用红字提示，后台不必每天弹一次
        if (!location.hasPermission() || !location.hasBackgroundLocation() ||
            !location.isLocationEnabled()
        ) {
            NetLog.info(
                "自动签到：定位不可用（前台=${location.hasPermission()} " +
                    "后台=${location.hasBackgroundLocation()} 开关=${location.isLocationEnabled()}），跳过"
            )
            scheduleTomorrow(p.autoSignHour, p.autoSignMinute)
            return Result.success()
        }

        // 凭证快到期时先自动续一个（Login）
        val issue = manager.ensureSession(minHoursLeft = 1)
        val fresh = prefs.prefs.first()
        if (fresh.accessToken.isBlank() || Jwt.isExpired(fresh.accessToken)) {
            notify(false, issue ?: "登录凭证已失效，请打开 App 重新登录一次。")
            scheduleTomorrow(p.autoSignHour, p.autoSignMinute)
            return Result.success()
        }

        NetLog.info("自动签到开始（第 ${runAttemptCount + 1} 次尝试）")
        val outcome = manager.checkIn()

        return when (val r = outcome.result) {
            is CheckInResult.Success -> {
                val detail = buildString {
                    append(r.msg)
                    if (outcome.lat != 0.0) {
                        append("　坐标 %.5f, %.5f".format(outcome.lat, outcome.lng))
                    }
                    outcome.distance?.let { append("，距单位 ${it.toInt()}m") }
                }
                notify(true, detail)
                scheduleTomorrow(p.autoSignHour, p.autoSignMinute)
                Result.success()
            }

            is CheckInResult.NeedRelogin -> {
                notify(false, "登录失效，自动签到没做成。打开 App 重新登录一次即可。")
                scheduleTomorrow(p.autoSignHour, p.autoSignMinute)
                Result.success()
            }

            is CheckInResult.Failed -> {
                // 定位类失败值得再试几次（可能刚好在室内、GPS 还没热）
                val locationFailed = outcome.lat == 0.0
                if (locationFailed && runAttemptCount < 2) {
                    NetLog.info("自动签到失败（定位），30 分钟后重试：${r.msg}")
                    Result.retry()
                } else {
                    notify(false, r.msg)
                    scheduleTomorrow(p.autoSignHour, p.autoSignMinute)
                    Result.success()
                }
            }
        }
    }

    private fun notify(success: Boolean, text: String) {
        NetLog.info("自动签到结果：${if (success) "成功" else "失败"} · $text")
        runCatching { Notifications.showAutoSignResult(applicationContext, success, text) }
    }

    private fun scheduleTomorrow(hour: Int, minute: Int) {
        runCatching { AutoSignScheduler.scheduleNext(applicationContext, hour, minute) }
    }
}

object AutoSignScheduler {

    private const val UNIQUE = "auto_sign_daily"

    /**
     * 排下一次。
     *
     * 用「一次性任务 + 执行后重排」而不是 PeriodicWork：
     * PeriodicWork 的最小间隔是 15 分钟且周期从入队时刻算起，跑久了会逐日漂移，
     * 一两个月后可能从 8:05 漂到 9:30。重排能保证每天都是同一个钟点。
     */
    fun scheduleNext(ctx: Context, hour: Int, minute: Int) {
        val request = OneTimeWorkRequestBuilder<AutoSignWorker>()
            .setInitialDelay(delayToNext(hour, minute), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .addTag(UNIQUE)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE)
    }
}
