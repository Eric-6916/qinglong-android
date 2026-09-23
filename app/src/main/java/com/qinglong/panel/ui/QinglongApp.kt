package com.qinglong.panel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qinglong.panel.data.remote.AuthEvent
import com.qinglong.panel.di.AppContainer
import com.qinglong.panel.ui.component.AuroraBackground
import com.qinglong.panel.ui.configs.ConfigsScreen
import com.qinglong.panel.ui.cron.CronEditScreen
import com.qinglong.panel.ui.cron.CronListScreen
import com.qinglong.panel.ui.dashboard.DashboardScreen
import com.qinglong.panel.ui.dependence.DependenceScreen
import com.qinglong.panel.ui.env.EnvEditScreen
import com.qinglong.panel.ui.env.EnvListScreen
import com.qinglong.panel.ui.log.LogViewerScreen
import com.qinglong.panel.ui.logfiles.LogFilesScreen
import com.qinglong.panel.ui.logfiles.SystemLogScreen
import com.qinglong.panel.ui.nav.Routes
import com.qinglong.panel.ui.script.ScriptDetailScreen
import com.qinglong.panel.ui.script.ScriptListScreen
import com.qinglong.panel.ui.script.ScriptUploadScreen
import com.qinglong.panel.ui.setup.SetupScreen
import com.qinglong.panel.ui.settings.AboutScreen
import com.qinglong.panel.ui.settings.PanelsScreen
import com.qinglong.panel.ui.settings.SettingsScreen
import com.qinglong.panel.ui.sub.SubEditScreen
import com.qinglong.panel.ui.sub.SubscriptionListScreen
import com.qinglong.panel.ui.system.SystemScreen
import com.qinglong.panel.ui.theme.LocalAuroraColors
import com.qinglong.panel.ui.theme.LogSurfaceTheme
import kotlinx.coroutines.launch

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "首页", Icons.Filled.Home),
    BottomTab(Routes.CRON_LIST, "任务", Icons.Filled.Schedule),
    BottomTab(Routes.SCRIPT_LIST, "脚本", Icons.Filled.Code),
    BottomTab(Routes.ENV_LIST, "环境", Icons.Filled.Storage),
    BottomTab(Routes.SUB_LIST, "订阅", Icons.Filled.Sync),
)

@Composable
fun QinglongApp(container: AppContainer) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    var sessionExpired by remember { mutableStateOf(false) }

    // 启动路由：MainActivity 已在 onCreate 中触发 initFromPersisted()（静默恢复会话）。
    // sessionRestored 为 null 时展示启动占位，避免闪一下向导页误导用户重新登录；
    // true → 直达首页；false → 进配置向导。
    val sessionRestored by container.sessionRestored.collectAsState()
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sessionRestored) {
        if (startDestination == null) {
            sessionRestored?.let { restored ->
                startDestination = if (restored) Routes.HOME else Routes.SETUP
            }
        }
    }

    // 会话失效：无论当前在哪一页，统一回到配置向导
    LaunchedEffect(Unit) {
        container.authEvents.collect { event ->
            when (event) {
                is AuthEvent.SessionExpired -> sessionExpired = true
                is AuthEvent.TwoFactorRequired -> Unit
            }
        }
    }
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            // 先跳转再提示：snackbar 由外层 Scaffold 持有，导航后仍可正常展示
            navController.navigate(Routes.SETUP) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            snackbarHostState.showSnackbar("登录已过期，请重新连接")
            sessionExpired = false
        }
    }

    val startRoute = startDestination
    if (startRoute == null) {
        // 会话恢复中：只展示启动占位，不渲染任何页面，
        // 避免闪一下配置向导让用户误以为要重新登录
        AuroraBackground {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    AuroraBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute in Routes.bottomRoutes) {
                    val aurora = LocalAuroraColors.current
                    // 浅色用柔和投影替代辉光（8dp 板岩 @10%）；暗色无投影
                    val islandShadow =
                        if (aurora.isDark) Color.Transparent else Color(0x1A0F172A)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth()
                            .shadow(
                                8.dp,
                                RoundedCornerShape(28.dp),
                                ambientColor = islandShadow,
                                spotColor = islandShadow,
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .background(aurora.glassFillStrong)
                            .border(1.dp, aurora.glassStroke, RoundedCornerShape(28.dp)),
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                        ) {
                            bottomTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        if (currentRoute != tab.route) {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    // 显式配色：选中 teal（主色血统）+ primary@12% 指示胶囊，
                                    // 未选中 onSurfaceVariant；避免 M3 默认淡紫指示器
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.padding(padding),
            ) {
            composable(Routes.SETUP) {
                SetupScreen(
                    container = container,
                    onConnected = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            // 添加面板：同一向导页的 addMode，连接成功后切回首页（新面板已激活）
            composable(Routes.SETUP_ADD) {
                SetupScreen(
                    container = container,
                    addMode = true,
                    onConnected = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                DashboardScreen(
                    container = container,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CRON_LIST) {
                CronListScreen(
                    container = container,
                    onEditCron = { id -> navController.navigate(Routes.cronEditRoute(id)) },
                    onOpenLog = { id, name -> navController.navigate(Routes.cronLogRoute(id, name)) },
                )
            }
            composable(
                route = Routes.CRON_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                CronEditScreen(
                    container = container,
                    cronId = if (id > 0) id else null,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.CRON_LOG,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "任务日志" },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val name = entry.arguments?.getString("name").orEmpty().ifBlank { "任务日志" }
                LogSurfaceTheme {
                    LogViewerScreen(
                        title = name,
                        loader = { container.repository.cronLog(id) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // ---- 以下模块由各模块界面落地后替换占位 ----
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onLogout = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onReconfigure = {
                        // 清空本地全部面板配置与凭据，回到配置向导重新录入
                        scope.launch {
                            container.resetAll()
                            navController.navigate(Routes.SETUP) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenPanels = { navController.navigate(Routes.PANELS) },
                    onOpenSystem = { navController.navigate(Routes.SYSTEM) },
                    onOpenDependence = { navController.navigate(Routes.DEPENDENCE) },
                    onOpenConfigs = { navController.navigate(Routes.CONFIGS) },
                    onOpenLogFiles = { navController.navigate(Routes.LOG_FILES) },
                    onOpenSystemLog = { navController.navigate(Routes.SYSTEM_LOG) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.PANELS) {
                PanelsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onAddPanel = { navController.navigate(Routes.SETUP_ADD) },
                    onSwitched = {
                        // 切换成功：重置整个返回栈回首页，各页面按新面板重新加载数据
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SCRIPT_LIST) {
                ScriptListScreen(
                    container = container,
                    onOpenDetail = { path, file ->
                        navController.navigate(Routes.scriptDetailRoute(path, file))
                    },
                    onOpenUpload = { path ->
                        navController.navigate(Routes.scriptUploadRoute(path))
                    },
                )
            }
            composable(Routes.ENV_LIST) {
                EnvListScreen(
                    container = container,
                    onEdit = { id -> navController.navigate(Routes.envEditRoute(id)) },
                )
            }
            composable(Routes.SUB_LIST) {
                SubscriptionListScreen(
                    container = container,
                    onEdit = { id -> navController.navigate(Routes.subEditRoute(id)) },
                    onOpenLog = { id, name -> navController.navigate(Routes.subLogRoute(id, name)) },
                )
            }
            composable(
                route = Routes.SCRIPT_DETAIL,
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    navArgument("file") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val path = entry.arguments?.getString("path").orEmpty()
                val file = entry.arguments?.getString("file").orEmpty()
                ScriptDetailScreen(
                    container = container,
                    path = path,
                    file = file,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SCRIPT_UPLOAD,
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val path = entry.arguments?.getString("path").orEmpty()
                ScriptUploadScreen(
                    container = container,
                    initialPath = path,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ENV_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                EnvEditScreen(
                    container = container,
                    envId = if (id > 0) id else null,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SUB_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                SubEditScreen(
                    container = container,
                    subId = if (id > 0) id else null,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SUB_LOG,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "订阅日志" },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val name = entry.arguments?.getString("name").orEmpty().ifBlank { "订阅日志" }
                LogSurfaceTheme {
                    LogViewerScreen(
                        title = name,
                        loader = { container.repository.subscriptionLog(id) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.SYSTEM) {
                SystemScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.DEPENDENCE) {
                DependenceScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CONFIGS) {
                ConfigsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LOG_FILES) {
                LogFilesScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SYSTEM_LOG) {
                SystemLogScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
    }
}
