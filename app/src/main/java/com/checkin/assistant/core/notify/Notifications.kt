package com.checkin.assistant.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.checkin.assistant.MainActivity
import com.checkin.assistant.R

object Notifications {

    private const val CHANNEL_ID = "checkin_reminder"
    private const val CHANNEL_AUTO = "checkin_auto"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "签到提醒", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "每日实习签到提醒与失败告警" }
        )
        // 成功通知单独一个低打扰渠道，不弹横幅、不出声
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AUTO, "自动签到结果", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "后台自动签到成功后的静默回执" }
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showReminder(ctx: Context) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("该签到了")
            .setContentText("点开应用，一键完成今天的实习签到")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }

    /**
     * 后台自动签到的结果通知。
     *
     * 成功用低打扰（静默、可自动消失），失败用高优先级 + 常驻，
     * 因为失败意味着今天没签上，用户必须知道。
     */
    fun showAutoSignResult(ctx: Context, success: Boolean, text: String) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(
            ctx,
            if (success) CHANNEL_AUTO else CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (success) "自动签到成功" else "自动签到失败")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)

        if (success) {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID + 1, builder.build()) }
    }
}
