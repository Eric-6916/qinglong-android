package com.qinglong.panel.ui.dependence

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.model.Dependence
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 依赖管理界面状态 */
data class DependenceState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<Dependence> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val actionInFlight: Boolean = false,
)

class DependenceViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(DependenceState())
        private set

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onQueryChange(q: String) {
        state = state.copy(query = q)
    }

    fun load() {
        loadJob?.cancel()
        viewModelScope.launch {
            repo.dependencies(null).fold(
                onSuccess = { list ->
                    state = state.copy(loading = false, items = list, error = null)
                },
                onFailure = { e ->
                    state = state.copy(loading = false, error = QinglongRepository.errorMessage(e))
                },
            )
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    /** 本地过滤后的列表 */
    val filtered: List<Dependence>
        get() {
            val q = state.query.trim()
            if (q.isBlank()) return state.items
            return state.items.filter { it.name.contains(q, ignoreCase = true) }
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
                    state = state.copy(actionInFlight = false, message = QinglongRepository.errorMessage(e))
                },
            )
        }
    }

    fun reinstall(ids: List<Long>) = act("重新安装", { repo.reinstallDependencies(ids) })
    fun cancel(ids: List<Long>) = act("取消", { repo.cancelDependencies(ids) })
    fun delete(ids: List<Long>) = act("卸载", { repo.deleteDependencies(ids, force = false) })
    fun install(names: String, type: Int) = act("安装", { repo.installDependencies(names, type) })

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DependenceViewModel(repo) }
        }
    }
}
