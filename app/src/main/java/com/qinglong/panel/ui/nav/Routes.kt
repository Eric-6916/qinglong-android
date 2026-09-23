package com.qinglong.panel.ui.nav

/** 全局路由表。二级页面参数统一走 query，缺省用默认值（0 / 空串） */
object Routes {
    const val SETUP = "setup"

    /** 添加面板模式下的配置向导（同一 SetupScreen，addMode = true） */
    const val SETUP_ADD = "setup/add"

    /** 面板管理（多面板列表 / 切换 / 删除） */
    const val PANELS = "panels"

    // 底部导航 5 Tab
    const val HOME = "home"
    const val CRON_LIST = "cron"
    const val SCRIPT_LIST = "script"
    const val ENV_LIST = "env"
    const val SUB_LIST = "subscription"

    // 二级页面（route 常量即注册用模式）
    const val SETTINGS = "settings"
    const val CRON_EDIT = "cron/edit?id={id}"
    const val CRON_LOG = "cron/log/{id}?name={name}"
    const val SCRIPT_DETAIL = "script/detail?path={path}&file={file}"
    const val SCRIPT_UPLOAD = "script/upload?path={path}"
    const val ENV_EDIT = "env/edit?id={id}"
    const val SUB_EDIT = "subscription/edit?id={id}"
    const val SUB_LOG = "subscription/log/{id}?name={name}"
    const val SYSTEM = "system"
    const val DEPENDENCE = "dependence"
    const val CONFIGS = "configs"
    const val LOG_FILES = "logfiles"
    const val SYSTEM_LOG = "system/log"

    /** 底部导航项对应的路由（用于控制底部栏显隐） */
    val bottomRoutes = setOf(HOME, CRON_LIST, SCRIPT_LIST, ENV_LIST, SUB_LIST)

    fun cronEditRoute(id: Long? = null): String = "cron/edit?id=${id ?: 0}"

    fun cronLogRoute(id: Long, name: String): String =
        "cron/log/$id?name=${enc(name)}"

    fun scriptDetailRoute(path: String, file: String): String =
        "script/detail?path=${enc(path)}&file=${enc(file)}"

    fun scriptUploadRoute(path: String = ""): String =
        "script/upload?path=${enc(path)}"

    fun envEditRoute(id: Long? = null): String = "env/edit?id=${id ?: 0}"

    fun subEditRoute(id: Long? = null): String = "subscription/edit?id=${id ?: 0}"

    fun subLogRoute(id: Long, name: String): String =
        "subscription/log/$id?name=${enc(name)}"

    private fun enc(s: String): String =
        // URLEncoder 会把空格编码成 "+"，导航查询参数不解码 "+"，需替换为 %20
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
