package com.qinglong.panel.data.model

import com.google.gson.annotations.SerializedName

/** 青龙统一响应信封：{ code, message, data } */
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null,
)

/** 分页数据（crons 列表使用） */
data class PagedData<T>(
    val data: List<T> = emptyList(),
    val total: Int = 0,
)

// ---------------------------------------------------------------- 定时任务

data class Cron(
    val id: Long = 0,
    val name: String? = null,
    val command: String = "",
    val schedule: String? = null,
    /** 0=运行中 1=空闲 2=禁用 3=排队 */
    val status: Int = 1,
    val isDisabled: Int = 0,
    val isPinned: Int = 0,
    val pid: Long? = null,
    @SerializedName("log_path") val logPath: String? = null,
    val labels: List<String>? = null,
    @SerializedName("last_running_time") val lastRunningTime: Long = 0,
    @SerializedName("last_execution_time") val lastExecutionTime: Long = 0,
    @SerializedName("task_before") val taskBefore: String? = null,
    @SerializedName("task_after") val taskAfter: String? = null,
    @SerializedName("log_name") val logName: String? = null,
    @SerializedName("sub_id") val subId: Long? = null,
    val createdAt: String? = null,
) {
    val enabled: Boolean get() = isDisabled == 0
    val running: Boolean get() = status == 0 || status == 3
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: command
}

/** 创建 / 更新任务请求体 */
data class CronPayload(
    val id: Long? = null,
    val command: String,
    val schedule: String,
    val name: String? = null,
    val labels: List<String>? = null,
    @SerializedName("task_before") val taskBefore: String? = null,
    @SerializedName("task_after") val taskAfter: String? = null,
    @SerializedName("log_name") val logName: String? = null,
)

data class LabelPayload(
    val ids: List<Long>,
    val labels: List<String>,
)

/** GET /crons/{id}/log 响应（日志内容在 data，其余字段在顶层） */
data class LogChunk(
    val code: Int = 0,
    val message: String? = null,
    val data: String? = null,
    val logStatus: String? = null,
    val offset: Long = 0,
    val nextOffset: Long = 0,
    val total: Long = 0,
    val truncated: Boolean = false,
)

// ---------------------------------------------------------------- 环境变量

data class Env(
    val id: Long = 0,
    val name: String? = null,
    val value: String? = null,
    val remarks: String? = null,
    /** 0=正常 1=禁用 */
    val status: Int = 0,
    val position: Long = 0,
    val isPinned: Int = 0,
    val labels: List<String>? = null,
) {
    val enabled: Boolean get() = status == 0
}

data class EnvPayload(
    val id: Long? = null,
    val name: String,
    val value: String,
    val remarks: String? = null,
)

data class MovePayload(
    val fromIndex: Int,
    val toIndex: Int,
)

// ---------------------------------------------------------------- 脚本

data class ScriptFile(
    val title: String = "",
    val key: String = "",
    /** directory | file */
    val type: String = "file",
    val parent: String = "",
    val createTime: Long = 0,
    val size: Long = 0,
    val children: List<ScriptFile>? = null,
) {
    val isDirectory: Boolean get() = type == "directory"
}

data class ScriptSavePayload(
    val filename: String,
    val path: String? = null,
    val content: String,
)

data class ScriptDeletePayload(
    val filename: String,
    val path: String? = null,
    val type: String? = null,
)

data class ScriptRunPayload(
    val filename: String,
    val path: String? = null,
    val content: String? = null,
)

data class ScriptStopPayload(
    val filename: String,
    val path: String? = null,
    val pid: Long? = null,
)

data class ScriptRenamePayload(
    val filename: String,
    val path: String? = null,
    val newFilename: String,
)

// ---------------------------------------------------------------- 订阅

data class Subscription(
    val id: Long = 0,
    val name: String? = null,
    val alias: String? = null,
    val url: String? = null,
    val type: String? = null,
    @SerializedName("schedule_type") val scheduleType: String? = null,
    val schedule: String? = null,
    @SerializedName("interval_schedule") val intervalSchedule: IntervalSchedule? = null,
    /** 0=运行中 1=空闲 2=禁用 3=排队 */
    val status: Int = 1,
    @SerializedName("is_disabled") val isDisabled: Int = 0,
    val pid: Long? = null,
    @SerializedName("log_path") val logPath: String? = null,
    val branch: String? = null,
    val whitelist: String? = null,
    val blacklist: String? = null,
    val dependences: String? = null,
    val extensions: String? = null,
    @SerializedName("sub_before") val subBefore: String? = null,
    @SerializedName("sub_after") val subAfter: String? = null,
    val proxy: String? = null,
    @SerializedName("autoAddCron") val autoAddCron: Int = 0,
    @SerializedName("autoDelCron") val autoDelCron: Int = 0,
) {
    val enabled: Boolean get() = isDisabled == 0
    val running: Boolean get() = status == 0 || status == 3
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: alias?.takeIf { it.isNotBlank() } ?: url ?: "订阅#$id"
}

data class IntervalSchedule(
    val type: String? = null,
    val value: Long = 0,
)

data class SubscriptionPayload(
    val id: Long? = null,
    val type: String,
    @SerializedName("schedule_type") val scheduleType: String,
    val alias: String,
    val url: String,
    val schedule: String? = null,
    @SerializedName("interval_schedule") val intervalSchedule: IntervalSchedule? = null,
    val name: String? = null,
    val whitelist: String? = null,
    val blacklist: String? = null,
    val branch: String? = null,
    val dependences: String? = null,
    val extensions: String? = null,
    @SerializedName("sub_before") val subBefore: String? = null,
    @SerializedName("sub_after") val subAfter: String? = null,
    val proxy: String? = null,
    @SerializedName("autoAddCron") val autoAddCron: Boolean? = null,
    @SerializedName("autoDelCron") val autoDelCron: Boolean? = null,
)

data class SubLogFile(
    val filename: String = "",
    val directory: String = "",
    val time: Long = 0,
)

// ---------------------------------------------------------------- 依赖

data class Dependence(
    val id: Long = 0,
    val name: String = "",
    /** 0=nodejs 1=python3 2=linux */
    val type: Int = 0,
    /** 0安装中 1已安装 2安装失败 3卸载中 4已卸载 5卸载失败 6排队 7已取消 */
    val status: Int = 0,
    val log: List<String>? = null,
    val remark: String? = null,
    val timestamp: String? = null,
)

data class DependencePayload(
    val name: String,
    val type: Int,
    val remark: String? = null,
)

// ---------------------------------------------------------------- 配置文件

data class ConfigFile(
    val title: String = "",
    val value: String = "",
)

data class ConfigSavePayload(
    val name: String,
    val content: String,
)

// ---------------------------------------------------------------- 系统 / 仪表盘

data class SystemInfo(
    val isInitialized: Boolean = false,
    val version: String = "",
    val publishTime: Long = 0,
    val branch: String = "",
    val changeLog: String? = null,
    val changeLogLink: String? = null,
)

data class SystemConfig(
    val info: ConfigInfo? = null,
)

data class ConfigInfo(
    val logRemoveFrequency: com.google.gson.JsonElement? = null,
    val cronConcurrency: com.google.gson.JsonElement? = null,
    val dependenceProxy: String? = null,
    val nodeMirror: String? = null,
    val pythonMirror: String? = null,
    val linuxMirror: String? = null,
    val panelTitle: String? = null,
    val lang: String? = null,
    val timezone: String? = null,
)

data class NumberPayload(val value: Long?)
data class TextPayload(val value: String?)
data class NotifyPayload(val title: String, val content: String)

data class DashboardOverview(
    val total: Int = 0,
    val enabled: Int = 0,
    val disabled: Int = 0,
    val todayRuns: Int = 0,
    val todaySuccess: Int = 0,
    val todayFail: Int = 0,
    val successRate: String = "0",
    val avgTime: Long = 0,
)

data class RunningTask(
    val instanceId: Long = 0,
    val id: Long = 0,
    val name: String = "",
    val pid: Long = 0,
    val elapsed: Long = 0,
    val logPath: String? = null,
)

data class IdleTask(
    val id: Long = 0,
    val name: String = "",
    val lastRun: String = "-",
)

data class DashboardRuntime(
    val runningCount: Int = 0,
    val queuedCount: Int = 0,
    val running: List<RunningTask> = emptyList(),
    val idleTasks: List<IdleTask> = emptyList(),
)

data class DashboardSystem(
    val platform: String = "",
    val uptime: Long = 0,
    val memTotal: Long = 0,
    val memFree: Long = 0,
    val memUsagePercent: String = "0",
    val heapUsed: Long = 0,
    val heapTotal: Long = 0,
    val loadAvg: List<Double> = emptyList(),
    val cpus: Int = 0,
)

data class TrendPoint(
    val date: String = "",
    val total: Int = 0,
    val success: Int = 0,
    val fail: Int = 0,
)

// ---------------------------------------------------------------- 认证

data class TokenData(
    val token: String = "",
    @SerializedName("token_type") val tokenType: String = "",
    val expiration: Long = 0,
)

data class LoginData(
    val token: String = "",
    val lastip: String? = null,
    val lastaddr: String? = null,
    val lastlogon: String? = null,
    val platform: String? = null,
)
