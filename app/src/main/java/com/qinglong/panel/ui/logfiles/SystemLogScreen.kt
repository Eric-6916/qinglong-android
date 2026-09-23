package com.qinglong.panel.ui.logfiles

import androidx.compose.runtime.Composable
import com.qinglong.panel.data.model.LogChunk
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.theme.LogSurfaceTheme

/**
 * 系统日志：复用 [LogView] 查看器。
 * loader 取 repo.systemLog()（纯文本），包装为 LogChunk 后交给查看器。
 * 自动刷新间隔 15 秒。日志面极光渐变底跟随全局主题（v1.0.6 起）。
 */
@Composable
fun SystemLogScreen(container: AppContainer, onBack: () -> Unit) {
    LogSurfaceTheme {
        LogView(
            title = "系统日志",
            loader = { container.repository.systemLog().map { LogChunk(data = it) } },
            onBack = onBack,
            autoRefreshIntervalMs = 15000L,
        )
    }
}
