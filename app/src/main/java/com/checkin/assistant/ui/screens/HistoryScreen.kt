package com.checkin.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.checkin.assistant.data.CheckInRecord
import com.checkin.assistant.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel()) {
    val records by vm.records.collectAsState()
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Column(Modifier.fillMaxSize()) {
        // 统计头部
        val success = records.count { it.status == "SUCCESS" }
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell("总记录", records.size.toString())
                StatCell("成功", success.toString())
                StatCell("失败", records.count { it.status == "FAILED" }.toString())
                StatCell("待登录", records.count { it.status == "NEED_RELOGIN" }.toString())
            }
        }

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无签到记录")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(records) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(fmt.format(Date(r.timestamp)), style = MaterialTheme.typography.bodyLarge)
                                Text(r.message, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            StatusChip(r.status, r.mode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun StatusChip(status: String, mode: String) {
    val (text, color) = when (status) {
        "SUCCESS" -> "成功" to MaterialTheme.colorScheme.primary
        "NEED_RELOGIN" -> "待登录" to MaterialTheme.colorScheme.tertiary
        else -> "失败" to MaterialTheme.colorScheme.error
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
        Text(if (mode == "AUTO") "自动" else "手动",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
