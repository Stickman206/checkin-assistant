package com.checkin.assistant.schedule

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.checkin.assistant.core.notify.Notifications
import com.checkin.assistant.data.PrefsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日提醒。用「一次性任务 + 每次执行后重排下一次」的方式，
 * 而不是 PeriodicWork，避免 24h 周期逐日漂移。
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PrefsRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val p = prefs.prefs.first()
        if (!p.reminderEnabled) return Result.success()
        Notifications.showReminder(applicationContext)
        ReminderScheduler.scheduleNext(applicationContext, p.reminderHour, p.reminderMinute)
        return Result.success()
    }
}

object ReminderScheduler {

    private const val UNIQUE = "daily_checkin_reminder"

    fun scheduleNext(ctx: Context, hour: Int, minute: Int) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayTo(hour, minute), TimeUnit.MILLISECONDS)
            .addTag(UNIQUE)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE)
    }

    private fun delayTo(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
