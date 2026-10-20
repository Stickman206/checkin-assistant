package com.checkin.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.checkin.assistant.ui.viewmodel.CredentialViewModel

/**
 * 首次使用的引导页。
 *
 * 目标只有一个：让用户在这里填一次账号密码，之后就再也不用手动签到了。
 * 所以不摆任何接口术语，只讲「填什么、为什么、填完会怎样」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onDone: () -> Unit,
    onSkip: () -> Unit,
    vm: CredentialViewModel = hiltViewModel()
) {
    val prefs by vm.appPrefs.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    // 登录成功后 loginState 会变成 LOGGED_IN，这里统一负责跳转
    LaunchedEffect(prefs.loginState) {
        if (prefs.loginState == "LOGGED_IN") onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            "实习签到助手",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "填一次账号密码，以后每天自动签到。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(4.dp))

        StepRow("1", "用学校实习系统的账号密码登录", "就是登录页那两个输入框，不是微信授权")
        StepRow("2", "允许定位", "签到要用你手机当前的位置，不会伪造")
        StepRow("3", "什么都不用做了", "每天到点自动签，手机上会收到一条回执")

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("帐号") },
            placeholder = { Text("登录页上填的那个帐号") },
            supportingText = {
                Text(
                    "就是学校实习系统登录页填的那个帐号，和学号不一样。" +
                        "不确定的话，在微信里进一次实习系统，登录页上能看到。",
                    fontSize = 11.sp
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { vm.loginWithPassword(user, pass, remember = true) },
            enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text("登录中…")
            } else {
                Text("登录并开始", style = MaterialTheme.typography.titleMedium)
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Text(
            "密码用 Android Keystore 加密后只存在这台手机上，密钥不可导出，" +
                "不会上传到任何地方，也不会用于签到以外的用途。",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )

        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("先随便看看，稍后再说")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepRow(index: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    index,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
