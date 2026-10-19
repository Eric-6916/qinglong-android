package com.qinglong.panel.ui.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.qinglong.panel.data.model.ConfigInfo
import com.qinglong.panel.data.model.SystemInfo
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.launch

/** 系统配置界面状态 */
data class SystemState(
    val loading: Boolean = true,
    val error: String? = null,
    val info: SystemInfo? = null,
    val config: ConfigInfo? = null,
    val saving: Boolean = false,
    val message: String? = null,
)

class SystemViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(SystemState())
        private set

    /** 已落库配置基线：用于「保存全部」只提交变更项，避免无谓触发 pnpm/apt 任务 */
    private var baseline: ConfigInfo? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            repo.systemInfo().fold(
                onSuccess = { state = state.copy(info = it) },
                onFailure = { state = state.copy(error = QinglongRepository.errorMessage(it)) },
            )
            repo.systemConfig().fold(
                onSuccess = {
                    state = state.copy(config = it.info)
                    baseline = it.info
                },
                onFailure = { state = state.copy(error = QinglongRepository.errorMessage(it)) },
            )
            state = state.copy(loading = false)
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    // -------------------------------------------------- 单项保存

    fun saveLogRemoveFrequency(value: Long) = saveItem(
        "日志清理频率",
        { it.copy(logRemoveFrequency = JsonPrimitive(value)) },
    ) { repo.updateLogRemoveFrequency(value) }

    fun saveCronConcurrency(value: Long) = saveItem(
        "任务并发数",
        { it.copy(cronConcurrency = JsonPrimitive(value)) },
    ) { repo.updateCronConcurrency(value) }

    fun saveDependenceProxy(value: String) = saveItem(
        "依赖代理",
        { it.copy(dependenceProxy = value) },
    ) { repo.updateDependenceProxy(value) }

    fun saveNodeMirror(value: String) = saveItem(
        "Node 镜像",
        { it.copy(nodeMirror = value) },
    ) { repo.updateNodeMirror(value) }

    fun savePythonMirror(value: String) = saveItem(
        "Python 镜像",
        { it.copy(pythonMirror = value) },
    ) { repo.updatePythonMirror(value) }

    fun saveLinuxMirror(value: String) = saveItem(
        "Linux 镜像",
        { it.copy(linuxMirror = value) },
    ) { repo.updateLinuxMirror(value) }

    fun savePanelTitle(value: String) = saveItem(
        "面板标题",
        { it.copy(panelTitle = value) },
    ) { repo.updatePanelTitle(value) }

    /** 统一保存单项配置；成功后同步基线，供「保存全部」做变更比较 */
    private fun saveItem(
        label: String,
        apply: (ConfigInfo) -> ConfigInfo,
        block: suspend () -> Result<Unit>,
    ) {
        if (state.saving) return
        state = state.copy(saving = true)
        viewModelScope.launch {
            block().fold(
                onSuccess = {
                    baseline = baseline?.let(apply) ?: apply(ConfigInfo())
                    state = state.copy(saving = false, message = "$label 已保存")
                },
                onFailure = { e ->
                    state = state.copy(saving = false, message = QinglongRepository.errorMessage(e))
                },
            )
        }
    }

    /** 一次性保存全部配置：只提交与基线不同的项 */
    fun saveAll(
        logRemoveFrequency: Long,
        cronConcurrency: Long,
        dependenceProxy: String,
        nodeMirror: String,
        pythonMirror: String,
        linuxMirror: String,
        panelTitle: String,
    ) {
        if (state.saving) return
        state = state.copy(saving = true)
        viewModelScope.launch {
            val base = baseline
            val steps = buildList {
                if (base == null || base.logRemoveFrequency.longOrNull() != logRemoveFrequency) {
                    add(
                        SaveStep(
                            "日志清理频率",
                            { it.copy(logRemoveFrequency = JsonPrimitive(logRemoveFrequency)) },
                        ) { repo.updateLogRemoveFrequency(logRemoveFrequency) },
                    )
                }
                if (base == null || base.cronConcurrency.longOrNull() != cronConcurrency) {
                    add(
                        SaveStep(
                            "任务并发数",
                            { it.copy(cronConcurrency = JsonPrimitive(cronConcurrency)) },
                        ) { repo.updateCronConcurrency(cronConcurrency) },
                    )
                }
                if (base == null || base.dependenceProxy.orEmpty() != dependenceProxy) {
                    add(
                        SaveStep("依赖代理", { it.copy(dependenceProxy = dependenceProxy) }) {
                            repo.updateDependenceProxy(dependenceProxy)
                        },
                    )
                }
                if (base == null || base.nodeMirror.orEmpty() != nodeMirror) {
                    add(
                        SaveStep("Node 镜像", { it.copy(nodeMirror = nodeMirror) }) {
                            repo.updateNodeMirror(nodeMirror)
                        },
                    )
                }
                if (base == null || base.pythonMirror.orEmpty() != pythonMirror) {
                    add(
                        SaveStep("Python 镜像", { it.copy(pythonMirror = pythonMirror) }) {
                            repo.updatePythonMirror(pythonMirror)
                        },
                    )
                }
                if (base == null || base.linuxMirror.orEmpty() != linuxMirror) {
                    add(
                        SaveStep("Linux 镜像", { it.copy(linuxMirror = linuxMirror) }) {
                            repo.updateLinuxMirror(linuxMirror)
                        },
                    )
                }
                if (base == null || base.panelTitle.orEmpty() != panelTitle) {
                    add(
                        SaveStep("面板标题", { it.copy(panelTitle = panelTitle) }) {
                            repo.updatePanelTitle(panelTitle)
                        },
                    )
                }
            }
            if (steps.isEmpty()) {
                state = state.copy(saving = false, message = "配置未变更")
                return@launch
            }
            val errors = mutableListOf<String>()
            steps.forEach { step ->
                step.run().fold(
                    onSuccess = { baseline = baseline?.let(step.apply) ?: step.apply(ConfigInfo()) },
                    onFailure = { errors.add("${step.label}：${QinglongRepository.errorMessage(it)}") },
                )
            }
            state = state.copy(
                saving = false,
                message = if (errors.isEmpty()) "已保存 ${steps.size} 项配置"
                else "有 ${errors.size} 项保存失败：${errors.joinToString("；")}",
            )
        }
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SystemViewModel(repo) }
        }
    }
}

/** 「保存全部」的单项步骤：label + 成功后对基线字段的更新 + 实际请求 */
private data class SaveStep(
    val label: String,
    val apply: (ConfigInfo) -> ConfigInfo,
    val run: suspend () -> Result<Unit>,
)

/** JsonElement（数字或数字字符串）转 Long，缺失/非法返回 null */
private fun JsonElement?.longOrNull(): Long? {
    if (this == null || !isJsonPrimitive) return null
    return runCatching { asJsonPrimitive.asLong }.getOrNull()
        ?: runCatching { asJsonPrimitive.asFloat.toLong() }.getOrNull()
}
