package com.checkin.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.checkin.assistant.core.checkin.Jwt
import com.checkin.assistant.ui.viewmodel.CredentialViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialScreen(
    onBack: () -> Unit,
    vm: CredentialViewModel = hiltViewModel()
) {
    val prefs by vm.appPrefs.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val ctx = LocalContext.current

    var accessToken by remember { mutableStateOf(prefs.accessToken) }
    var wxtoken by remember { mutableStateOf(prefs.wxtoken) }

    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var rememberPwd by remember { mutableStateOf(true) }

    LaunchedEffect(prefs.savedUser) {
        if (prefs.savedUser.isNotBlank() && user.isBlank()) user = prefs.savedUser
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录凭证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (prefs.loginState == "LOGGED_IN") {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        listOf(prefs.studentName, prefs.studentNo)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "已保存 token" }
                    )
                } else {
                    Text("未登录")
                }
            }

            if (prefs.companyName.isNotBlank() || prefs.companyLat != 0.0) {
                Text(
                    "单位：${prefs.companyName.ifBlank { prefs.companyAddress }}\n" +
                        "坐标 %.5f, %.5f".format(prefs.companyLat, prefs.companyLng) +
                        if (prefs.signRange > 0) "　半径 ${prefs.signRange.toInt()}m" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("账号密码自动登录（推荐）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "系统本身就有 /api/studentapp/Login 接口，请求体就是登录页那两个输入框：" +
                            "{\"UserName\":\"...\",\"Password\":\"...\"}，返回的 Data 与微信登录同构。\n" +
                            "注意：这里的帐号是登录页填的那个 16 位帐号，不是学号，两者不一样。\n" +
                            "填一次即可：token 只有 24 小时，App 会在到期前用这组账号密码自动重新登录，" +
                            "以后不用再抓包、也不用再进微信。\n" +
                            "密码用 Android Keystore 加密后存在本机，密钥不可导出，" +
                            "拷走数据文件也解不开，更不会发往任何第三方。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("帐号（登录页里填的那个，不是学号）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberPwd, onCheckedChange = { rememberPwd = it })
                        Text("记住密码，到期自动续", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { vm.loginWithPassword(user, pass, rememberPwd) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "登录中…" else "登录") }

                    Text(
                        if (prefs.hasPassword) "已保存：${prefs.savedUser}（密码为加密密文）"
                        else "未保存密码：凭证到期后需要重新登录",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (prefs.hasPassword) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    if (prefs.hasPassword) {
                        TextButton(onClick = { vm.forgetPassword() }) { Text("删除已保存的密码") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("手动粘贴 token（备用）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. 微信里正常进一次签到页（公众号 → 签到）\n" +
                            "2. 抓包工具里找接口域名下的 POST /api/studentapp/Settings\n" +
                            "3. 打开请求头，复制 authorization 里 Bearer 后面那一整串（eyJ 开头）\n" +
                            "4. 顺便复制同一个请求头里的 wxtoken（32 位十六进制）\n" +
                            "5. 粘贴到下面，点「保存并验证」\n\n" +
                            "token 只有 24 小时有效期，服务端没有续期接口——" +
                            "想让它自动续，用上面那栏填账号密码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("access_token（Bearer 后面那串）") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wxtoken,
                        onValueChange = { wxtoken = it },
                        label = { Text("wxtoken（请求头里的 32 位十六进制）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { vm.saveAndValidate(accessToken, wxtoken, prefs.refreshToken) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "验证中…" else "保存并验证") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("token 有效期", style = MaterialTheme.typography.titleSmall)
                    if (prefs.accessToken.isNotBlank()) {
                        val exp = Jwt.expiryMillis(prefs.accessToken)
                        val hours = Jwt.hoursLeft(prefs.accessToken)
                        Text(
                            if (exp > 0)
                                "到期时间：${
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(exp))
                                }" + if (hours >= 0) "（剩 $hours 小时）" else "（已过期）"
                            else "无法解析到期时间，以服务端返回为准",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hours in 0..6) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                    } else {
                        Text("尚未填写", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text(
                        "实测 WxLogin / Login 都返回 expires_in=86400，JWT 的 exp 也正好是签发后 24 小时。" +
                            "续期接口 /api/studentapp/RefreshToken 已实测返回 404，服务端没有这个能力。" +
                            "所以唯一能让凭证自动续的办法就是用账号密码重新登录——" +
                            "存了密码后 App 每 8 小时检查一次，剩不到 8 小时就换新的，你基本不用管。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            if (prefs.loginState == "LOGGED_IN") {
                TextButton(onClick = { vm.logout() }) { Text("清除登录状态") }
            }
        }
    }
}
