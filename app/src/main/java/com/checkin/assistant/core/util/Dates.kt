package com.checkin.assistant.core.util

import java.util.Calendar

/** 时间戳是否落在「今天」（本地时区） */
fun isSameDay(ts: Long, now: Long = System.currentTimeMillis()): Boolean {
    if (ts <= 0L) return false
    val a = Calendar.getInstance().apply { timeInMillis = now }
    val b = Calendar.getInstance().apply { timeInMillis = ts }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

/** 距离下一个 hour:minute 还有多少毫秒（今天已过则算明天） */
fun delayToNext(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
    val cur = Calendar.getInstance().apply { timeInMillis = now }
    val target = (cur.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (!target.after(cur)) target.add(Calendar.DAY_OF_MONTH, 1)
    return target.timeInMillis - cur.timeInMillis
}
