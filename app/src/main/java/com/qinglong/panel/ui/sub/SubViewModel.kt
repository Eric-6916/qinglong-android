package com.qinglong.panel.ui.sub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.model.Subscription
import com.qinglong.panel.data.model.SubscriptionPayload
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 订阅列表 / 编辑共用状态 */
data class SubListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** 本地搜索关键字（按 alias/name/url 过滤，不发服务端请求） */
    val searchQuery: String = "",
    val items: List<Subscription> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val actionInFlight: Boolean = false,
    val saving: Boolean = false,
    /** 编辑页保存成功信号 */
    val saved: Boolean = false,
)

class SubViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(SubListState())
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
            repo.subscriptions(null).fold(
                onSuccess = { list ->
                    loadedOnce = true
                    state = state.copy(
                        loading = false,
                        refreshing = false,
                        items = list,
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

    /** 本地过滤：alias / name / url 模糊匹配（忽略大小写） */
    val filtered: List<Subscription>
        get() {
            val q = state.searchQuery.trim()
            if (q.isEmpty()) return state.items
            return state.items.filter { sub ->
                sub.alias?.contains(q, ignoreCase = true) == true
                    || sub.name?.contains(q, ignoreCase = true) == true
                    || sub.url?.contains(q, ignoreCase = true) == true
            }
        }

    /** 从已加载列表定位（青龙无单查接口） */
    fun findById(id: Long): Subscription? = state.items.firstOrNull { it.id == id }

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

    /** 新建（subId=null）或更新订阅 */
    fun save(subId: Long?, payload: SubscriptionPayload) {
        if (state.saving) return
        state = state.copy(saving = true, message = null)
        viewModelScope.launch {
            val result = if (subId == null) repo.createSubscription(payload) else repo.updateSubscription(payload)
            result.fold(
                onSuccess = { state = state.copy(saving = false, saved = true, message = "保存成功") },
                onFailure = { e ->
                    state = state.copy(saving = false, message = QinglongRepository.errorMessage(e))
                },
            )
        }
    }

    fun delete(ids: List<Long>, force: Boolean = false) = act("删除") { repo.deleteSubscriptions(ids, force) }
    fun run(ids: List<Long>) = act("运行") { repo.runSubscriptions(ids) }
    fun stop(ids: List<Long>) = act("停止") { repo.stopSubscriptions(ids) }
    fun enable(ids: List<Long>) = act("启用") { repo.enableSubscriptions(ids) }
    fun disable(ids: List<Long>) = act("禁用") { repo.disableSubscriptions(ids) }

    /** 编辑页表单校验失败时回显错误 */
    fun reportError(msg: String) {
        state = state.copy(message = msg)
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SubViewModel(repo) }
        }
    }
}
