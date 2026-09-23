package com.qinglong.panel.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 兼容色（旧屏幕直接引用，随浅色主题同步为玉青系）
// ============================================================
val BrandTeal = Color(0xFF0A7D69)
val BrandTealDark = Color(0xFF075E4E)
val BrandTealLight = Color(0xFFC5F2E7)

// ============================================================
// 暗色语义色（任务状态五态 + 市场涨跌，深底亮档）
// 经 AuroraDarkColors 接入主题切换，屏幕不直接引用
// v1.2.1：禁用态由灰改红（用户需求「禁用应该显示红色」）；
//         新增 idle 中性色（空闲/已卸载/已取消等非问题终态）
// ============================================================
val StatusRunning = Color(0xFF4CAF50)  // v1.2.1：暗底提亮（原 #1E8E3E 在 surface 上仅 4.23:1）
val StatusQueued = Color(0xFFE8710A)
val StatusDisabled = Color(0xFFFF8A80)  // v1.2.1：禁用=红（暗色亮红档；原灰 #80868B）
val StatusFailed = Color(0xFFFF5252)   // v1.2.1：暗底提亮（原 #D93025 仅 3.73:1，pill 3.47:1）
val StatusIdle = Color(0xFF94A3B8)     // v1.2.1：中性 slate（暗色亮档）

// ============================================================
// Aurora Glass 暗色方案
// ============================================================
val DarkPrimary = Color(0xFF2FD6C0)
val DarkOnPrimary = Color(0xFF00201C)
val DarkPrimaryContainer = Color(0xFF10312C)
val DarkOnPrimaryContainer = Color(0xFFA7F3E4)

val DarkSecondary = Color(0xFF8B7CF6)
val DarkOnSecondary = Color(0xFFFFFFFF)
val DarkSecondaryContainer = Color(0xFF241F45)
val DarkOnSecondaryContainer = Color(0xFFDDD6FE)

val DarkTertiary = Color(0xFF4CC9F0)
val DarkOnTertiary = Color(0xFF00363F)
val DarkTertiaryContainer = Color(0xFF0F2E3A)
val DarkOnTertiaryContainer = Color(0xFFBEEAFB)

val DarkError = Color(0xFFFF6B72)
val DarkOnError = Color(0xFF3F0A0E)
val DarkErrorContainer = Color(0xFF4A1218)
val DarkOnErrorContainer = Color(0xFFFFDADC)

val DarkBackground = Color(0xFF070B12)
val DarkOnBackground = Color(0xFFE9F0F7)
val DarkSurface = Color(0xFF101826)
val DarkOnSurface = Color(0xFFE9F0F7)
val DarkSurfaceVariant = Color(0xFF1A2436)
val DarkOnSurfaceVariant = Color(0xFF93A1B5)
val DarkOutline = Color(0x24FFFFFF)        // #FFFFFF @ 14%
val DarkOutlineVariant = Color(0x43FFFFFF) // #FFFFFF @ 26%
val DarkScrim = Color(0x8C000000)          // #000000 @ 55%

// 市场涨跌语义（遵循涨红跌绿）
val MarketUp = Color(0xFFFF5A5F)
val MarketDown = Color(0xFF34D67A)

// ============================================================
// Aurora Glass 亮色方案（晨曜极光 Aurora Dawn，默认形态）
// v1.2.0 重设计：近白冷光天幕 + 四色 mesh 极光 + 玉青主色
// 规范：deliverables/design/qlapp-light-theme-spec.md §2
// ============================================================
val LightPrimary = Color(0xFF0A7D69)          // 玉青（白字 5.06:1；teal 光晕卡面上 4.69:1，WCAG AA）
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFC5F2E7) // 薄荷
val LightOnPrimaryContainer = Color(0xFF00291F)

val LightSecondary = Color(0xFF6D5BD0)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE7E1FF)
val LightOnSecondaryContainer = Color(0xFF221B4E)

val LightTertiary = Color(0xFF0B7FA8)         // 亮蓝（渐变按钮副色，白字 4.6:1）
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFD3F0FB)
val LightOnTertiaryContainer = Color(0xFF00293A)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF6F9FC)       // 与极光天幕同调的近白冷光
val LightOnBackground = Color(0xFF16212B)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF16212B)
val LightSurfaceVariant = Color(0xFFE3EBF6)   // 冷雾蓝（与背景分层）
val LightOnSurfaceVariant = Color(0xFF4A5866)
val LightOutline = Color(0x290F172A)        // #0F172A @ 16%
val LightOutlineVariant = Color(0x140F172A) // #0F172A @ 8%
val LightScrim = Color(0x730B1322)          // #0B1322 @ 45%

// ============================================================
// 浅色语义色（浅底深档，规范 §5）
// 经 AuroraLightColors 接入主题切换，屏幕不直接引用
// v1.2.1：禁用态由灰 #566475 改红；新增 idle 中性色
// ============================================================
val LightStatusRunning = Color(0xFF13723A)
val LightStatusQueued = Color(0xFF9A5205)
val LightStatusDisabled = Color(0xFFB71C1C)  // v1.2.1：禁用=红（深红；pill 12% 容器上 5.0:1）
val LightStatusFailed = Color(0xFFBE2424)
val LightStatusIdle = Color(0xFF475569)      // v1.2.1：中性 slate（浅底深档；pill 上 5.9:1）
val LightMarketUp = Color(0xFFC62828)   // 涨红（浅色深档）
val LightMarketDown = Color(0xFF0B7442) // 跌绿（浅色深档；surfaceVariant 上 4.87:1）
