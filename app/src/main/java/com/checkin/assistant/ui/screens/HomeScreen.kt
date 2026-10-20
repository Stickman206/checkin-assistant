package com.checkin.assistant.ui.screens

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.checkin.assistant.core.checkin.CheckInOutcome
import com.checkin.assistant.core.checkin.CheckInResult
import com.checkin.assistant.core.checkin.Jwt
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.ui.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onGoCredential: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val prefs by vm.appPrefs.collectAsState()
    val latest by vm.latest.collectAsState()
    val busy by vm.busy.collectAsState()
    val toast by vm.toast.collectAsState()
    val outcome by vm.outcome.collectAsState()
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }

    var permAsked by rememberSaveable { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }
    val granted = remember(permTick) { vm.hasLocationPermission() }
    val permanentlyDenied = !granted && permAsked &&
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.consumeToast()
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permTick++
        if (grants.values.any { it }) {
            vm.refreshLocation()
        } else {
            Toast.makeText(ctx, "没有定位权限，无法获取签到位置", Toast.LENGTH_LONG).show()
        }
    }

    val appSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permTick++ }

    // 进入首页就主动申请一次，不让用户在毫无提示的情况下卡住
    LaunchedEffect(Unit) {
        if (!vm.hasLocationPermission() && !permAsked) {
            permAsked = true
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val requestLocation: () -> Unit = {
        if (permanentlyDenied) {
            appSettingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")
                )
            )
        } else {
            permAsked = true
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("签到助手", style = MaterialTheme.typography.headlineSmall)
            Text(
                "读取手机真实定位，一键完成签到",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        if (!granted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("需要定位权限", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (permanentlyDenied)
                            "定位权限已被拒绝，系统不会再弹窗。请到系统设置 → 权限 → 位置信息里手动开启。"
                        else
                            "签到需要读取你当前的位置，请允许定位权限。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = requestLocation) {
                        Text(if (permanentlyDenied) "去系统设置 →" else "授予权限 →")
                    }
                }
            }
        }

        StatusCard(
            prefs = prefs,
            lastRecord = latest,
            onGoCredential = onGoCredential,
            onRefresh = { vm.refreshLocation() },
            onRefreshPlan = { vm.refreshPlan() }
        )

        if (prefs.loginState != "LOGGED_IN") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("还没登录", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "填一次账号密码就能用，之后每天自动签到。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onGoCredential) { Text("去登录 →") }
                }
            }
        }

        Button(
            onClick = {
                when {
                    prefs.loginState != "LOGGED_IN" -> {
                        Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show()
                        onGoCredential()
                    }
                    !granted -> requestLocation()
                    else -> vm.manualCheckIn()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text(if (busy) "正在签到…" else "立即签到", style = MaterialTheme.typography.titleMedium)
        }

        outcome?.let { OutcomeCard(it) }

        AutoSignCard(
            prefs = prefs,
            hasForeground = granted,
            hasBackground = remember(permTick) { vm.hasBackgroundLocation() },
            needsBackground = remember(permTick) { vm.needsBackgroundLocation() },
            onToggle = { enabled, h, m ->
                if (enabled && android.os.Build.VERSION.SDK_INT >= 33) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (enabled && !granted) requestLocation() else vm.setAutoSign(enabled, h, m)
            },
            onOpenSettings = {
                appSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
            }
        )

        ReminderCard(
            prefs = prefs,
            onToggle = { enabled, h, m ->
                if (enabled && android.os.Build.VERSION.SDK_INT >= 33) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.setReminder(enabled, h, m)
            }
        )
    }
}

@Composable
private fun StatusCard(
    prefs: AppPrefs,
    lastRecord: com.checkin.assistant.data.CheckInRecord?,
    onGoCredential: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshPlan: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(
                Modifier.fillMaxWidth().clickable(onClick = onGoCredential),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Person, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("登录状态", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        if (prefs.loginState == "LOGGED_IN")
                            listOf(prefs.studentName, prefs.studentNo)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { "已登录" }
                        else "未登录",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val hours = Jwt.hoursLeft(prefs.accessToken)
                    if (prefs.accessToken.isNotBlank()) {
                        Text(
                            when {
                                hours < 0 -> "凭证已过期，请重新登录"
                                hours == 0 -> "凭证有效期未知"
                                hours <= 3 -> "凭证只剩 $hours 小时，尽快签到"
                                else -> "凭证剩 $hours 小时"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hours in 0..3) MaterialTheme.colorScheme.error else Color.Gray
                        )
                    }
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider()

            Row(
                Modifier.fillMaxWidth().clickable(onClick = onRefreshPlan),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("实习单位", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        prefs.companyName.ifBlank { prefs.companyAddress }
                            .ifBlank { "尚未获取，点右侧刷新" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (prefs.signRange > 0) {
                        Text(
                            buildString {
                                append("签到半径 ${formatDistance(prefs.signRange.toFloat())}")
                                if (prefs.itinerancy == 1) append(" · 外勤模式已开")
                                append(" · 累计签到 ${prefs.totalSignCount} 次")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Text(
                    if (prefs.signToday) "今日已签" else "今日未签",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (prefs.signToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider()

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("当前定位（GCJ-02）", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (prefs.lastLocTime == 0L) {
                        Text("尚未获取，点右侧刷新", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(
                            "%.5f, %.5f".format(prefs.lastLat, prefs.lastLng),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        val extra = buildString {
                            append("精度 ±${prefs.lastAccuracy.toInt()}m · ${timeFmt.format(Date(prefs.lastLocTime))}")
                            val d = distanceToCompany(prefs)
                            if (d != null) {
                                append(" · 距单位 ${formatDistance(d)}")
                            }
                        }
                        Text(extra, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                TextButton(onClick = onRefresh) { Text("刷新") }
            }

            HorizontalDivider()

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("上次签到", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        lastRecord?.let { "${dateFmt.format(Date(it.timestamp))} · ${statusCn(it.status)}" }
                            ?: "暂无记录",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (prefs.planId.isNotBlank()) {
                        Text(
                            "计划：${prefs.planName.ifBlank { prefs.planId }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeCard(outcome: CheckInOutcome) {
    val isSuccess = outcome.result is CheckInResult.Success
    val container = if (isSuccess) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer

    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSuccess) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when (val r = outcome.result) {
                        is CheckInResult.Success -> r.msg
                        is CheckInResult.Failed -> r.msg
                        CheckInResult.NeedRelogin -> "登录已失效，请重新填写凭证"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (outcome.requestBody.isNotBlank()) {
                Text("请求体", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                SelectionContainer {
                    Text(
                        outcome.requestBody,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (outcome.rawResponse.isNotBlank()) {
                Text("响应原文", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                SelectionContainer {
                    Text(
                        outcome.rawResponse.take(1500),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (outcome.lat != 0.0 || outcome.lng != 0.0) {
                Text(
                    buildString {
                        append("坐标 %.5f, %.5f · 精度 ±%dm".format(outcome.lat, outcome.lng, outcome.accuracy.toInt()))
                        outcome.distance?.let { append(" · 距单位 ${formatDistance(it)}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            outcome.rangeWarning?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AutoSignCard(
    prefs: AppPrefs,
    hasForeground: Boolean,
    hasBackground: Boolean,
    needsBackground: Boolean,
    onToggle: (Boolean, Int, Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (prefs.autoSignEnabled)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动签到", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        String.format(
                            Locale.US,
                            "每天 %02d:%02d 后台自动签，打开 App 时也会自动补一次",
                            prefs.autoSignHour, prefs.autoSignMinute
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = prefs.autoSignEnabled,
                    onCheckedChange = {
                        onToggle(it, prefs.autoSignHour, prefs.autoSignMinute)
                    }
                )
            }

            TextButton(
                onClick = {
                    TimePickerDialog(
                        ctx,
                        { _, h, m -> onToggle(prefs.autoSignEnabled, h, m) },
                        prefs.autoSignHour,
                        prefs.autoSignMinute,
                        true
                    ).show()
                }
            ) { Text("修改自动签到时间") }

            if (prefs.autoSignEnabled) {
                val (text, color) = when {
                    !hasForeground ->
                        "还缺定位权限，后台取不到位置。" to MaterialTheme.colorScheme.error

                    needsBackground ->
                        "定位权限只给了「使用期间允许」。后台自动签到必须在系统设置里改成「始终允许」。" to
                            MaterialTheme.colorScheme.error

                    hasBackground -> "定位权限已就绪，后台可以正常取位置。" to Color.Gray
                    else -> "定位权限状态未知。" to Color.Gray
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = color)

                if (!hasForeground || needsBackground) {
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("去系统设置里改定位权限 →")
                    }
                    Text(
                        "路径：应用信息 → 权限 → 位置信息 → 始终允许",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Text(
                "说明：\n" +
                    "· 系统省电机制（Doze）可能把任务推迟几分钟到几十分钟，失败会自动重试\n" +
                    "· 国产 ROM 还要允许「自启动」，并把本应用加入电池优化白名单\n" +
                    "· 在系统里「强行停止」本应用会取消所有后台任务，直到你再次打开它\n" +
                    "· 用的是手机真实定位，人不在单位也可能签上（服务端半径 50km），请自己把握",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ReminderCard(prefs: AppPrefs, onToggle: (Boolean, Int, Int) -> Unit) {
    val ctx = LocalContext.current

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("每日提醒", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        String.format(Locale.US, "每天 %02d:%02d 通知你签到", prefs.reminderHour, prefs.reminderMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = prefs.reminderEnabled,
                    onCheckedChange = { onToggle(it, prefs.reminderHour, prefs.reminderMinute) }
                )
            }

            TextButton(
                onClick = {
                    TimePickerDialog(
                        ctx,
                        { _, h, m -> onToggle(prefs.reminderEnabled, h, m) },
                        prefs.reminderHour,
                        prefs.reminderMinute,
                        true
                    ).show()
                }
            ) { Text("修改提醒时间") }

            Text(
                "提醒只负责弹通知。想让它彻底不用管，打开上面的「每日自动签到」。",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

private fun distanceToCompany(prefs: AppPrefs): Float? {
    if (prefs.companyLat == 0.0 && prefs.companyLng == 0.0) return null
    if (prefs.lastLocTime == 0L) return null
    val out = FloatArray(1)
    Location.distanceBetween(prefs.lastLat, prefs.lastLng, prefs.companyLat, prefs.companyLng, out)
    return out[0]
}

private fun formatDistance(meters: Float): String =
    if (meters >= 1000) "%.1fkm".format(meters / 1000) else "${meters.toInt()}m"

private fun statusCn(status: String): String = when (status) {
    "SUCCESS" -> "成功"
    "NEED_RELOGIN" -> "需重新登录"
    "FAILED" -> "失败"
    else -> status
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
