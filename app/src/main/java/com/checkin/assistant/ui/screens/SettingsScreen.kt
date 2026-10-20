package com.checkin.assistant.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.checkin.assistant.core.checkin.Jwt
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.ui.viewmodel.SettingsViewModel

/**
 * 设置首页。
 *
 * 只放真正会用到的东西：账号、接口配置入口、后台运行、关于。
 * 接口配置那一堆技术字段收进二级页 [ApiConfigScreen]，避免首页像调试面板。
 */
@Composable
fun SettingsScreen(
    onGoCredential: () -> Unit,
    onGoApiConfig: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val prefs by vm.appPrefs.collectAsState()
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // ── 账号 ──────────────────────────────
        SettingGroup("账号") {
            SettingRow(
                icon = Icons.Filled.Person,
                title = if (prefs.loginState == "LOGGED_IN") "已登录" else "未登录",
                subtitle = accountSummary(prefs),
                onClick = onGoCredential
            )
        }

        // ── 签到偏好 ──────────────────────────
        SettingGroup("签到") {
            SettingRow(
                icon = Icons.Filled.Alarm,
                title = "自动签到",
                subtitle = if (prefs.autoSignEnabled)
                    String.format("已开启 · 每天 %02d:%02d 后台自动签", prefs.autoSignHour, prefs.autoSignMinute)
                else "已关闭",
                onClick = null
            )
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                "开关和时间在首页的「自动签到」卡片里改。",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }

        // ── 高级 ──────────────────────────────
        SettingGroup("高级") {
            SettingRow(
                icon = Icons.Filled.Tune,
                title = "接口配置",
                subtitle = "签到地址、请求体模板、成功判定",
                onClick = onGoApiConfig
            )
        }

        // ── 后台运行 ──────────────────────────
        SettingGroup("后台运行") {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("电池优化", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "系统在省电模式下会推迟后台任务。把本应用加入电池优化白名单，" +
                        "自动签到才会更准时。国产 ROM 还需要在系统设置里允许自启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedButton(
                    onClick = { requestIgnoreBattery(ctx) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("关闭电池优化") }
            }
        }

        // ── 关于 ──────────────────────────────
        SettingGroup("关于") {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "签到助手 ${appVersion(ctx)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    "只读取设备真实定位，不做任何坐标改写。" +
                        "签到由你手动点击，或由你开启的自动签到触发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun accountSummary(prefs: AppPrefs): String {
    if (prefs.loginState != "LOGGED_IN") {
        return if (prefs.hasPassword) "已保存账号 ${prefs.savedUser}，凭证待续"
        else "点这里填账号密码"
    }
    val who = listOf(prefs.studentName, prefs.studentNo)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val hours = Jwt.hoursLeft(prefs.accessToken)
    val left = when {
        prefs.accessToken.isBlank() -> ""
        hours < 0 -> " · 凭证已过期"
        hours == 0 -> ""
        else -> " · 凭证剩 $hours 小时"
    }
    return who.ifBlank { prefs.savedUser }.ifBlank { "已登录" } + left
}

private fun appVersion(ctx: Context): String = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
}.getOrNull() ?: "1.0"

private fun requestIgnoreBattery(ctx: Context) {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
        Toast.makeText(ctx, "已经在白名单里了", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
