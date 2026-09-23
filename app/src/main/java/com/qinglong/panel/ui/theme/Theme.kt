package com.qinglong.panel.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.qinglong.panel.ui.component.AuroraBackground

// ============================================================
// Aurora Glass 扩展色板（玻璃/极光背景等 Material3 色板之外的视觉要素）
// ============================================================

/** 任务状态语义色（运行/排队/禁用/失败/空闲，随主题切换深浅档）
 *  v1.2.1：禁用由灰改红（用户需求）；新增 idle 中性色 */
@Immutable
data class AuroraStatusColors(
    val running: Color,
    val queued: Color,
    val disabled: Color,
    val failed: Color,
    val idle: Color,
)

@Immutable
data class AuroraColors(
    val isDark: Boolean,
    /** 极光背景渐变（上→下） */
    val bgTop: Color,
    val bgBottom: Color,
    /** 极光光晕（teal / purple / cyan / peach，径向渐变；peach 仅浅色主题，暗色为透明） */
    val orbTeal: Color,
    val orbPurple: Color,
    val orbCyan: Color,
    val orbPeach: Color,
    /** 玻璃卡填充（弱/强） */
    val glassFill: Color,
    val glassFillStrong: Color,
    /** 玻璃描边（1dp，顶部高亮） */
    val glassStroke: Color,
    /** 顶部高光渐变起点色（终点为透明） */
    val glassHighlight: Color,
    /** 遮罩 */
    val scrim: Color,
    /** 不透明底（API < 31 降级用） */
    val surfaceSolid: Color,
    /** 任务状态语义色（运行/排队/禁用/失败） */
    val status: AuroraStatusColors,
    /** 市场涨跌语义（涨红跌绿） */
    val marketUp: Color,
    val marketDown: Color,
)

val AuroraDarkColors = AuroraColors(
    isDark = true,
    bgTop = Color(0xFF0B1322),
    bgBottom = Color(0xFF06090F),
    orbTeal = Color(0x5214B8A6),    // #14B8A6 @ 32%
    orbPurple = Color(0x427C6CF0),  // #7C6CF0 @ 26%
    orbCyan = Color(0x2422D3EE),    // #22D3EE @ 14%
    orbPeach = Color.Transparent,   // 暗色不引入暖色光晕，保持深夜极光原样（零回归）
    glassFill = Color(0x0FFFFFFF),      // #FFFFFF @ 6%
    glassFillStrong = Color(0x1AFFFFFF), // #FFFFFF @ 10%
    glassStroke = Color(0x1FFFFFFF),     // #FFFFFF @ 12%
    glassHighlight = Color(0x47FFFFFF),  // #FFFFFF @ 28% → 0%
    scrim = DarkScrim,
    surfaceSolid = DarkSurface,
    status = AuroraStatusColors(
        running = StatusRunning,
        queued = StatusQueued,
        disabled = StatusDisabled,
        failed = StatusFailed,
        idle = StatusIdle,
    ),
    marketUp = MarketUp,
    marketDown = MarketDown,
)

val AuroraLightColors = AuroraColors(
    isDark = false,
    bgTop = Color(0xFFFBFCFE),      // 近白冷光天幕
    bgBottom = Color(0xFFE6EDF7),   // 晨雾蓝
    orbTeal = Color(0x2E14B8A6),    // #14B8A6 @ 18%
    orbPurple = Color(0x247C6CF0),  // #7C6CF0 @ 14%
    orbCyan = Color(0x1A22D3EE),    // #22D3EE @ 10%
    orbPeach = Color(0x29FDBA74),   // #FDBA74 @ 16%（暖色晨曦，mesh 点睛）
    glassFill = Color(0xCCF6FAFB),       // 冷白 @ 80%
    glassFillStrong = Color(0xEEF8FAFC), // 冷白 @ 93%
    glassStroke = Color(0x1A0F172A),     // #0F172A @ 10%
    glassHighlight = Color(0x0F0F172A),  // #0F172A @ 6% → 0%（浅色高光逻辑反转：压暗顶缘）
    scrim = LightScrim,
    surfaceSolid = LightSurface,
    status = AuroraStatusColors(
        running = LightStatusRunning,
        queued = LightStatusQueued,
        disabled = LightStatusDisabled,
        failed = LightStatusFailed,
        idle = LightStatusIdle,
    ),
    marketUp = LightMarketUp,
    marketDown = LightMarketDown,
)

val LocalAuroraColors = staticCompositionLocalOf { AuroraDarkColors }

// ============================================================
// 双色板
// ============================================================
private val QlDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
)

private val QlLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
)

private val QlShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * 全局主题：Aurora Glass（极光玻璃）设计系统。
 *
 * 浅色为默认形态；[darkTheme] 置 true 切换到暗色方案。
 * 主题偏好由设置页持久化（SettingsStore.themeMode），MainActivity 注入。
 */
@Composable
fun QinglongTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) QlDarkColorScheme else QlLightColorScheme
    val auroraColors = if (darkTheme) AuroraDarkColors else AuroraLightColors
    CompositionLocalProvider(LocalAuroraColors provides auroraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QlTypography,
            shapes = QlShapes,
            content = content,
        )
    }
}

/**
 * 日志查看器页面容器。
 *
 * v1.2.3：复用全站 [AuroraBackground]（垂直渐变底 + 四色 mesh 光晕）——
 * 此前日志系列页面（日志目录 / 任务日志 / 订阅日志 / 系统日志）只有垂直渐变底，
 * 没有 mesh 光晕，背景与首页、任务页不一致（用户反馈"日志页顶部颜色不一致"）。
 * 现与全站统一，顶栏透明后露出同一片极光。
 *
 * v1.0.4 曾按设计规范 §7.2 将日志面恒定为暗底（「内容即终端」），
 * 用户反馈浅色主题下日志页应跟随主题——后改为主题感知（v1.0.6），
 * 不再覆写 colorScheme，日志内容面板（surfaceVariant 语义色）随主题自动切换。
 */
@Composable
fun LogSurfaceTheme(content: @Composable () -> Unit) {
    AuroraBackground {
        content()
    }
}
