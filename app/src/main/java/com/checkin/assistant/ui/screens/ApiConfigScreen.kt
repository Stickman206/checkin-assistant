package com.checkin.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.checkin.assistant.ui.viewmodel.ApiConfigViewModel

/**
 * 接口配置（二级页面）。
 *
 * 这些字段正常情况下永远不用改——默认值已按 2026-10 抓包结果校准。
 * 放在二级页是为了让设置首页只留真正会用的东西。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(
    onBack: () -> Unit,
    vm: ApiConfigViewModel = hiltViewModel()
) {
    val prefs by vm.appPrefs.collectAsState()
    val message by vm.message.collectAsState()
    val ctx = LocalContext.current

    var signUrl by remember { mutableStateOf(prefs.signUrl) }
    var signBody by remember { mutableStateOf(prefs.signBody) }
    var successKeyword by remember { mutableStateOf(prefs.successKeyword) }
    var settingsUrl by remember { mutableStateOf(prefs.settingsUrl) }

    LaunchedEffect(prefs.signUrl, prefs.signBody, prefs.successKeyword, prefs.settingsUrl) {
        signUrl = prefs.signUrl
        signBody = prefs.signBody
        successKeyword = prefs.successKeyword
        settingsUrl = prefs.settingsUrl
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("接口配置") },
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("平时不用改这里", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "默认值已按 2026-10 抓包结果校准：\n" +
                            "POST /api/studentapp/Sign，请求体 {\"Lat\":..,\"Lng\":..}，Code==0 为成功。\n" +
                            "只有学校改版、签到开始报错时，才需要在这里对着调。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            OutlinedTextField(
                value = signUrl,
                onValueChange = { signUrl = it },
                label = { Text("签到接口 URL") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            OutlinedTextField(
                value = signBody,
                onValueChange = { signBody = it },
                label = { Text("请求体模板") },
                supportingText = {
                    Text(
                        "可用占位符：{lat} {lng} {studentNo} {studentName} {timestamp}（{planId} 保留兼容）",
                        fontSize = 11.sp
                    )
                },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            )

            OutlinedTextField(
                value = successKeyword,
                onValueChange = { successKeyword = it },
                label = { Text("成功判定关键字") },
                supportingText = { Text("先按 JSON Code==0 判断，失败再匹配这段文字", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            OutlinedTextField(
                value = settingsUrl,
                onValueChange = { settingsUrl = it },
                label = { Text("计划信息接口（校验 token 用）") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.save(signUrl, signBody, successKeyword, settingsUrl) },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
                OutlinedButton(
                    onClick = { vm.reset() },
                    modifier = Modifier.weight(1f)
                ) { Text("恢复默认") }
            }
        }
    }
}
