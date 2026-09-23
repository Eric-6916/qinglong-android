package com.qinglong.panel.ui.env

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.model.Env
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 环境变量列表 / 编辑共用状态 */
data class EnvListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** 本地搜索关键字（按 name/value/remarks 过滤，不发服务端请求） */
    val searchQuery: String = "",
    val items: List<Env> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val actionInFlight: Boolean = false,
    val saving: Boolean = false,
    /** 编辑页保存成功信号 */
    val saved: Boolean = false,
)

class EnvViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(EnvListState())
        private set

    private var loadJob: Job? = null
    private var loadedOnce = false

    init { load() }

    fun onSearchChange(q: String) {
        state = state.copy(searchQuery = q)
    }

    fun load() {
        loadJob?.cancel()
        val first = !loadedOnce && state.items.isEmpty() && state.error == null
        state = state.copy(loading = first, refreshing = !first)
        loadJob = viewModelScope.launch {
            repo.envs(null).fold(
                onSuccess = { list ->
                    loadedOnce = true
                    state = state.copy(
                        loading = false,
                        refreshing = false,
                        items = sortItems(list),
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(
                        loading = false,
                        refreshing = false,
                        error = QinglongRepository.errorMessage(e),
                    )
                },
            )
        }
    }

    /** 置顶优先（isPinned=1），其后按 position 升序 */
    private fun sortItems(list: List<Env>): List<Env> =
        list.sortedWith(compareByDescending<Env> { it.isPinned == 1 }.thenBy { it.position })

    /** 本地过滤：name / value / remarks 模糊匹配（忽略大小写） */
    val filtered: List<Env>
        get() {
            val q = state.searchQuery.trim()
            if (q.isEmpty()) return state.items
            return state.items.filter { env ->
                env.name?.contains(q, ignoreCase = true) == true
                    || env.value?.contains(q, ignoreCase = true) == true
                    || env.remarks?.contains(q, ignoreCase = true) == true
            }
        }

    /** 从已加载列表定位（青龙无单查接口） */
    fun findById(id: Long): Env? = state.items.firstOrNull { it.id == id }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    private fun act(label: String, block: suspend () -> Result<Unit>) {
        if (state.actionInFlight) return
        state = state.copy(actionInFlight = true)
        viewModelScope.launch {
            block().fold(
                onSuccess = {
                    state = state.copy(actionInFlight = false, message = "$label 成功")
                    load()
                },
                onFailure = { e ->
                    state = state.copy(
                        actionInFlight = false,
                        message = QinglongRepository.errorMessage(e),
                    )
                },
            )
        }
    }

    /** 新建（id=null）或更新环境变量 */
    fun save(id: Long?, name: String, value: String, remarks: String) {
        if (state.saving) return
        // 青龙 envs 的 name 受 Joi 校验 /^[a-zA-Z_][0-9a-zA-Z_]*$/：
        // 中文、连字符、数字开头、空格都会被面板以 HTTP 400 拒绝（Joi 原文是英文），
        // 这里前置拦截并给出中文提示。
        val trimmedName = name.trim()
        if (!ENV_NAME_PATTERN.matches(trimmedName)) {
            state = state.copy(
                message = "变量名只能包含字母、数字、下划线，且必须以字母或下划线开头",
            )
            return
        }
        state = state.copy(saving = true, message = null)
        viewModelScope.launch {
            val result = if (id == null) {
                repo.createEnv(name, value, remarks.ifBlank { null })
            } else {
                repo.updateEnv(id, name, value, remarks.ifBlank { null })
            }
            result.fold(
                onSuccess = { state = state.copy(saving = false, saved = true, message = "保存成功") },
                onFailure = { e ->
                    state = state.copy(saving = false, message = QinglongRepository.errorMessage(e))
                },
            )
        }
    }

    fun delete(ids: List<Long>) = act("删除") { repo.deleteEnvs(ids) }
    fun enable(ids: List<Long>) = act("启用") { repo.enableEnvs(ids) }
    fun disable(ids: List<Long>) = act("禁用") { repo.disableEnvs(ids) }
    fun pin(ids: List<Long>) = act("置顶") { repo.pinEnvs(ids) }
    fun unpin(ids: List<Long>) = act("取消置顶") { repo.unpinEnvs(ids) }

    /** 上传环境变量 JSON 文件（青龙要求 [{name,value,remarks,status?}] 数组） */
    fun uploadEnvJson(fileName: String, bytes: ByteArray) {
        if (state.actionInFlight) return
        state = state.copy(actionInFlight = true)
        viewModelScope.launch {
            repo.uploadEnvFile(fileName, bytes).fold(
                onSuccess = {
                    state = state.copy(actionInFlight = false, message = "上传成功")
                    load()
                },
                onFailure = { e ->
                    state = state.copy(
                        actionInFlight = false,
                        message = QinglongRepository.errorMessage(e),
                    )
                },
            )
        }
    }

    /** 编辑页读取本地文件失败时回显错误 */
    fun reportError(msg: String) {
        state = state.copy(message = msg)
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { EnvViewModel(repo) }
        }
    }
}

/** 青龙 envs 名称规则（back/api/env.ts Joi pattern） */
private val ENV_NAME_PATTERN = Regex("^[a-zA-Z_][0-9a-zA-Z_]*$")
