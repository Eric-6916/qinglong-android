package com.qinglong.panel.ui.cron

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.model.Cron
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CronListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val query: String = "",
    val items: List<Cron> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val actionInFlight: Boolean = false,
)

class CronViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(CronListState())
        private set

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onQueryChange(q: String) {
        state = state.copy(query = q)
        load(debounce = true)
    }

    fun clearQuery() {
        state = state.copy(query = "")
        load()
    }

    fun load(debounce: Boolean = false) {
        loadJob?.cancel()
        val first = state.items.isEmpty() && state.error == null
        state = state.copy(loading = first && !debounce, refreshing = !first)
        loadJob = viewModelScope.launch {
            if (debounce) delay(350)
            repo.crons(state.query.ifBlank { null }, page = 0, size = 0).fold(
                onSuccess = { paged ->
                    state = state.copy(
                        loading = false,
                        refreshing = false,
                        items = paged.data,
                        error = null,
                    )
                },
                onFailure = { e ->
                    state = state.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "加载失败",
                    )
                },
            )
        }
    }

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
                        message = e.message ?: "$label 失败",
                    )
                },
            )
        }
    }

    fun run(ids: List<Long>) = act("运行") { repo.runCrons(ids) }
    fun stop(ids: List<Long>) = act("停止") { repo.stopCrons(ids) }
    fun enable(ids: List<Long>) = act("启用") { repo.enableCrons(ids) }
    fun disable(ids: List<Long>) = act("禁用") { repo.disableCrons(ids) }
    fun delete(ids: List<Long>) = act("删除") { repo.deleteCrons(ids) }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CronViewModel(repo) }
        }
    }
}
