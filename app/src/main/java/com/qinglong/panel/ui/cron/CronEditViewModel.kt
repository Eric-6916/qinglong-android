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
import kotlinx.coroutines.launch

data class CronEditState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val cronId: Long? = null,
    val name: String = "",
    val command: String = "",
    val schedule: String = "",
    val labels: String = "",
    val taskBefore: String = "",
    val taskAfter: String = "",
    val logName: String = "",
    val error: String? = null,
    val saved: Boolean = false,
    val notFound: Boolean = false,
)

class CronEditViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(CronEditState())
        private set

    fun load(id: Long) {
        if (id <= 0) return
        state = state.copy(loading = true)
        viewModelScope.launch {
            // 青龙无单查接口，列表拉取后定位
            repo.crons(null, page = 0, size = 0).fold(
                onSuccess = { paged ->
                    val cron = paged.data.firstOrNull { it.id == id }
                    if (cron == null) {
                        state = state.copy(loading = false, notFound = true)
                    } else {
                        state = state.copy(
                            loading = false,
                            cronId = cron.id,
                            name = cron.name.orEmpty(),
                            command = cron.command,
                            schedule = cron.schedule.orEmpty(),
                            labels = cron.labels?.joinToString(",").orEmpty(),
                            taskBefore = cron.taskBefore.orEmpty(),
                            taskAfter = cron.taskAfter.orEmpty(),
                            logName = cron.logName.orEmpty(),
                        )
                    }
                },
                onFailure = { e ->
                    state = state.copy(loading = false, error = e.message ?: "加载失败")
                },
            )
        }
    }

    fun onName(v: String) { state = state.copy(name = v) }
    fun onCommand(v: String) { state = state.copy(command = v) }
    fun onSchedule(v: String) { state = state.copy(schedule = v) }
    fun onLabels(v: String) { state = state.copy(labels = v) }
    fun onTaskBefore(v: String) { state = state.copy(taskBefore = v) }
    fun onTaskAfter(v: String) { state = state.copy(taskAfter = v) }
    fun onLogName(v: String) { state = state.copy(logName = v) }

    fun save() {
        val s = state
        if (s.saving) return
        if (s.command.isBlank()) {
            state = s.copy(error = "命令不能为空")
            return
        }
        if (s.schedule.isBlank()) {
            state = s.copy(error = "定时规则不能为空")
            return
        }
        state = s.copy(saving = true, error = null)
        viewModelScope.launch {
            val payload = com.qinglong.panel.data.model.CronPayload(
                id = s.cronId,
                command = s.command.trim(),
                schedule = s.schedule.trim(),
                name = s.name.trim().ifBlank { null },
                labels = s.labels.split(",", "，", " ")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { null },
                taskBefore = s.taskBefore.trim().ifBlank { null },
                taskAfter = s.taskAfter.trim().ifBlank { null },
                logName = s.logName.trim().ifBlank { null },
            )
            val result = if (s.cronId == null) repo.createCron(payload) else repo.updateCron(payload)
            result.fold(
                onSuccess = { state = state.copy(saving = false, saved = true) },
                onFailure = { e ->
                    state = state.copy(saving = false, error = e.message ?: "保存失败")
                },
            )
        }
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CronEditViewModel(repo) }
        }
    }
}
