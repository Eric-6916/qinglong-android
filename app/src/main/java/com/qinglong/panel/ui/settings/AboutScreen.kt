package com.qinglong.panel.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qinglong.panel.BuildConfig
import com.qinglong.panel.ui.component.InfoRow
import com.qinglong.panel.ui.component.SectionCard
import com.qinglong.panel.ui.component.auroraTopAppBarColors

private const val GITHUB_REPO_URL = "https://github.com/Eric-6916/qinglong-android"
private const val GITHUB_ISSUES_URL = "https://github.com/Eric-6916/qinglong-android/issues"
private const val QINGLONG_UPSTREAM_URL = "https://github.com/whyour/qinglong"

/**
 * 关于 / 开源声明页（v1.2.4 新增）。
 *
 * 展示应用标识、作者署名（Eric-6916）、开源仓库地址（点击可跳转浏览器）、
 * 开源协议（MIT）与开源声明（第三方非官方客户端免责说明）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyText(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                colors = auroraTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 应用标识
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Dashboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("青龙管家", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = BuildConfig.APPLICATION_ID,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "第三方青龙面板客户端 · 非官方应用\nJetpack Compose 打造的青龙面板移动端管理工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 开源信息：作者署名 + 仓库地址（可点击跳转 / 复制）
            SectionCard {
                Text("开源信息", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow(label = "作者", value = "Eric-6916")
                InfoRow(label = "开源协议", value = "MIT License")
                Spacer(Modifier.height(8.dp))
                LinkRow(
                    icon = Icons.Filled.Language,
                    title = "开源仓库",
                    subtitle = GITHUB_REPO_URL,
                    onOpen = { openUrl(GITHUB_REPO_URL) },
                    onCopy = { copyText(GITHUB_REPO_URL) },
                )
                LinkRow(
                    icon = Icons.Filled.BugReport,
                    title = "问题反馈",
                    subtitle = "在 GitHub Issues 提交问题或建议",
                    onOpen = { openUrl(GITHUB_ISSUES_URL) },
                    onCopy = { copyText(GITHUB_ISSUES_URL) },
                )
                LinkRow(
                    icon = Icons.Filled.Link,
                    title = "青龙面板开源项目",
                    subtitle = QINGLONG_UPSTREAM_URL,
                    onOpen = { openUrl(QINGLONG_UPSTREAM_URL) },
                    onCopy = { copyText(QINGLONG_UPSTREAM_URL) },
                )
            }

            // 开源声明
            SectionCard {
                Text("开源声明", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "青龙管家（qinglong-android）是由 Eric-6916 开发并维护的第三方开源客户端，" +
                        "与青龙面板官方项目（whyour/qinglong）及其开发团队无任何隶属、赞助或关联关系。\n\n" +
                        "本项目基于 MIT 协议开源，按「原样」提供，不附带任何明示或默示的担保。" +
                        "使用本应用造成的一切后果由使用者自行承担。\n\n" +
                        "本应用不收集、不上传任何用户数据；所有面板连接信息与凭据仅保存在本机沙箱中，" +
                        "仅用于与您自己的青龙面板通信。\n\n" +
                        "「青龙」名称及相关商标权利归其各自所有者所有，此处仅作指代说明之用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "© 2026 Eric-6916 · MIT License",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 链接行：点击打开浏览器，右侧复制按钮 */
@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
