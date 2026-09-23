package com.qinglong.panel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.qinglong.panel.data.local.ThemeMode
import com.qinglong.panel.ui.QinglongApp
import com.qinglong.panel.ui.theme.QinglongTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v1.2.0：移除 FLAG_SECURE 禁截屏——用户需要截图分享面板配置与任务日志，
        // 凭据本身由 EncryptedSharedPreferences 加密存储，不依赖禁截屏保护。
        val container = (application as QinglongApplication).container
        lifecycleScope.launch {
            // 启动时按已存配置重建 Retrofit 基址
            container.initFromPersisted()
        }
        setContent {
            // 主题偏好持久化于 DataStore，默认浅色；设置页切换后即时生效
            val themeMode by container.settingsStore.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            QinglongTheme(darkTheme = themeMode == ThemeMode.DARK) {
                QinglongApp(container)
            }
        }
    }
}
