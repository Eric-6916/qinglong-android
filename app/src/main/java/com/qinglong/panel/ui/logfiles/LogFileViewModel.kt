package com.qinglong.panel.ui.logfiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.model.ScriptFile
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.launch

/** 日志目录列表状态（顶层目录树） */
data class LogFileState(
    val loading: Boolean = true,
    val tree: List<ScriptFile> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

class LogFileViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(LogFileState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            repo.logs().fold(
                onSuccess = { list -> state = state.copy(loading = false, tree = list, error = null) },
                onFailure = { e -> state = state.copy(loading = false, error = QinglongRepository.errorMessage(e)) },
            )
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LogFileViewModel(repo) }
        }
    }
}
