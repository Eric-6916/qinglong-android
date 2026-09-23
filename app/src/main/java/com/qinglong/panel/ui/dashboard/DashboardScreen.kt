package com.qinglong.panel.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinglong.panel.data.model.DashboardSystem
import com.qinglong.panel.data.model.RunningTask
import com.qinglong.panel.data.model.TrendPoint
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.auroraTopAppBarColors
import com.qinglong.panel.ui.component.BentoStatCard
import com.qinglong.panel.ui.component.GlassCard
import com.qinglong.panel.ui.component.InfoRow
import com.qinglong.panel.ui.component.LoadingBox
import com.qinglong.panel.ui.component.StatusPill
import com.qinglong.panel.ui.theme.LocalAuroraColors
import com.qinglong.panel.ui.util.formatElapsed
import com.qinglong.panel.ui.util.formatLoadAvg
import com.qinglong.panel.ui.util.formatTs
import com.qinglong.panel.ui.util.formatUptime
import com.qinglong.panel.ui.util.trendLabel

/**
 * 仪表盘（Bento 网格 + 液态玻璃）：
 * 今日运行 span4 → 运行中/排队 span2 → 成功率/平均耗时 span2 → 趋势 span4 → 服务器 span4。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container.repository))
    val state = vm.state

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("仪表盘") },
                colors = auroraTopAppBarColors(),
                actions = {
                    IconButton(onClick = vm::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            if (state.dashboardPermissionDenied) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "仪表盘统计在当前面板不可用",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "青龙面板的「应用设置」暂未开放 dashboard 数据权限，" +
                            "应用授权模式下无法获取运行统计（这是面板侧限制，不是漏勾权限）。" +
                            "任务、环境变量、脚本、订阅等模块不受影响，可正常使用；" +
                            "如需仪表盘统计，请改用账号密码登录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
            } else if (state.degraded.isNotEmpty()) {
                DegradedBanner("部分数据加载失败：${state.degraded.joinToString("、")}")
                Spacer(Modifier.height(10.dp))
            }

            // ---- Row 1：今日运行（span 4）----
            state.overview?.let { o ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "今日运行",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (o.enabled > 0) StatusPill("${o.enabled} 个启用", LocalAuroraColors.current.status.running)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatItem("总任务", "${o.total}")
                        StatItem("已启用", "${o.enabled}", LocalAuroraColors.current.status.running)
                        StatItem("已禁用", "${o.disabled}")
                        StatItem("运行次数", "${o.todayRuns}")
                    }
                    Spacer(Modifier.height(14.dp))
                    InfoRow("成功 / 失败", "${o.todaySuccess} / ${o.todayFail}")
                    InfoRow("成功率", "${o.successRate}%")
                    InfoRow("平均耗时", "${o.avgTime} ms")
                }
            }

            // ---- Row 2：运行中 / 排队（span 2 × 2）----
            state.runtime?.let { r ->
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    BentoStatCard(
                        title = "运行中",
                        value = "${r.runningCount}",
                        unit = "个",
                        icon = Icons.Filled.PlayArrow,
                        accent = LocalAuroraColors.current.status.running,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    BentoStatCard(
                        title = "排队",
                        value = "${r.queuedCount}",
                        unit = "个",
                        icon = Icons.Filled.Schedule,
                        accent = LocalAuroraColors.current.status.queued,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                GlassCard {
                    Text(
                        "运行时明细",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (r.running.isEmpty()) {
                        Text(
                            "当前没有正在运行的任务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        r.running.forEach { task ->
                            RunningTaskRow(task)
                        }
                    }
                }
            }

            // ---- Row 3：成功率 / 平均耗时（span 2 × 2）----
            state.overview?.let { o ->
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    BentoStatCard(
                        title = "今日成功率",
                        value = "${o.successRate}",
                        unit = "%",
                        icon = Icons.Filled.CheckCircle,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    BentoStatCard(
                        title = "平均耗时",
                        value = "${o.avgTime}",
                        unit = "ms",
                        icon = Icons.Filled.Timer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---- Row 4：执行趋势（span 4）----
            if (state.trend.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                GlassCard {
                    Text(
                        "近 ${state.trend.size} 天执行趋势",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(14.dp))
                    TrendChart(state.trend)
                }
            }

            // ---- Row 5：服务器（span 4）----
            state.system?.let { s ->
                Spacer(Modifier.height(12.dp))
                GlassCard {
                    Text(
                        "服务器",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    SystemCard(s)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DegradedBanner(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RunningTaskRow(task: RunningTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LocalAuroraColors.current.status.running),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name.ifBlank { "#${task.id}" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = "PID ${task.pid}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatElapsed(task.elapsed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SystemCard(s: DashboardSystem) {
    val memPercent = s.memUsagePercent.toFloatOrNull() ?: 0f
    InfoRow("系统", s.platform)
    InfoRow("运行时长", formatUptime(s.uptime))
    InfoRow("CPU 负载", formatLoadAvg(s.loadAvg))
    InfoRow("核心数", "${s.cpus}")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "内存 ${memPercent.toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(92.dp),
        )
        LinearProgressIndicator(
            progress = { (memPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun TrendChart(points: List<TrendPoint>) {
    val max = (points.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { p ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "${p.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.height(72.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val successHeight = (56 * p.success / max).dp
                    val failHeight = (56 * p.fail / max).dp
                    if (successHeight > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(successHeight)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(LocalAuroraColors.current.status.running),
                        )
                    }
                    if (failHeight > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(failHeight)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = trendLabel(p.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LocalAuroraColors.current.status.running),
        )
        Spacer(Modifier.width(4.dp))
        Text("成功", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.error),
        )
        Spacer(Modifier.width(4.dp))
        Text("失败", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
