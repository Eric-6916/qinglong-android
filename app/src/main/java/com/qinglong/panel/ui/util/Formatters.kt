package com.qinglong.panel.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmtDateTime = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private val fmtDate = SimpleDateFormat("MM-dd", Locale.getDefault())
private val fmtTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/** Unix 秒 → "MM-dd HH:mm" */
fun formatTs(seconds: Long): String {
    if (seconds <= 0) return "-"
    return fmtDateTime.format(Date(seconds * 1000))
}

/** Unix 毫秒 → "HH:mm:ss" */
fun formatMs(ms: Long): String {
    if (ms <= 0) return "-"
    return fmtTime.format(Date(ms))
}

fun formatDate(dateStr: String?): String = dateStr?.take(10).orEmpty().ifEmpty { "-" }

/** 毫秒时长 → "2m 05s" / "1h 03m" */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "-"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
        m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
        else -> "${s}s"
    }
}

/** 运行秒数（青龙 elapsed 为毫秒/秒混用场景，按毫秒处理，>= 1e12 视为毫秒时间戳忽略） */
fun formatElapsed(ms: Long): String = formatDuration(ms)

/** 字节 → "1.2 GB" */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    return if (idx == 0) "${bytes} B"
    else String.format(Locale.US, "%.1f %s", value, units[idx])
}

/** 秒 → "3天4小时" */
fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "-"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return buildString {
        if (d > 0) append("${d}天")
        if (h > 0) append("${h}小时")
        if (d == 0L && m > 0) append("${m}分")
    }.ifEmpty { "$seconds 秒" }
}

/** loadAvg 列表 → "0.12 0.08 0.05" */
fun formatLoadAvg(list: List<Double>): String =
    if (list.isEmpty()) "-" else list.joinToString("  ") { String.format(Locale.US, "%.2f", it) }

/** 趋势图日期 "2024-06-01" → "06-01" */
fun trendLabel(date: String): String = date.takeLast(5)
