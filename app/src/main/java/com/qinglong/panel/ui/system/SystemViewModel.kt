package com.qinglong.panel.ui.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
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
                onSuccess = { state = state.copy(config = it.info) },
                onFailure = { state = state.copy(error = QinglongRepository.errorMessage(it)) },
            )
            state = state.copy(loading = false)
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    // -------------------------------------------------- 单项保存

    fun saveLogRemoveFrequency(value: Long) = saveItem("日志清理频率") { repo.updateLogRemoveFrequency(value) }
    fun saveCronConcurrency(value: Long) = saveItem("任务并发数") { repo.updateCronConcurrency(value) }
    fun saveDependenceProxy(value: String) = saveItem("依赖代理") { repo.updateDependenceProxy(value) }
    fun saveNodeMirror(value: String) = saveItem("Node 镜像") { repo.updateNodeMirror(value) }
    fun savePythonMirror(value: String) = saveItem("Python 镜像") { repo.updatePythonMirror(value) }
    fun saveLinuxMirror(value: String) = saveItem("Linux 镜像") { repo.updateLinuxMirror(value) }
    fun savePanelTitle(value: String) = saveItem("面板标题") { repo.updatePanelTitle(value) }

    /** 统一保存单项配置 */
    private fun saveItem(label: String, block: suspend () -> Result<Unit>) {
        if (state.saving) return
        state = state.copy(saving = true)
        viewModelScope.launch {
            block().fold(
                onSuccess = { state = state.copy(saving = false, message = "$label 已保存") },
                onFailure = { e -> state = state.copy(saving = false, message = QinglongRepository.errorMessage(e)) },
            )
        }
    }

    /** 一次性保存全部配置 */
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
            val steps = listOf<Pair<String, suspend () -> Result<Unit>>>(
                "日志清理频率" to { repo.updateLogRemoveFrequency(logRemoveFrequency) },
                "任务并发数" to { repo.updateCronConcurrency(cronConcurrency) },
                "依赖代理" to { repo.updateDependenceProxy(dependenceProxy) },
                "Node 镜像" to { repo.updateNodeMirror(nodeMirror) },
                "Python 镜像" to { repo.updatePythonMirror(pythonMirror) },
                "Linux 镜像" to { repo.updateLinuxMirror(linuxMirror) },
                "面板标题" to { repo.updatePanelTitle(panelTitle) },
            )
            val errors = mutableListOf<String>()
            steps.forEach { (label, block) ->
                block().onFailure { errors.add("$label：${QinglongRepository.errorMessage(it)}") }
            }
            state = state.copy(
                saving = false,
                message = if (errors.isEmpty()) "全部配置已保存" else "有 ${errors.size} 项保存失败：${errors.joinToString("；")}",
            )
        }
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SystemViewModel(repo) }
        }
    }
}
