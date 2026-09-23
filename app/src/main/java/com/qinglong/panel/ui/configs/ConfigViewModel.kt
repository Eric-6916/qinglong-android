package com.qinglong.panel.ui.configs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.model.ConfigFile
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.launch

/** 配置文件列表状态 */
data class ConfigState(
    val loading: Boolean = true,
    val files: List<ConfigFile> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

class ConfigViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(ConfigState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            repo.configFiles().fold(
                onSuccess = { list -> state = state.copy(loading = false, files = list, error = null) },
                onFailure = { e -> state = state.copy(loading = false, error = QinglongRepository.errorMessage(e)) },
            )
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConfigViewModel(repo) }
        }
    }
}
