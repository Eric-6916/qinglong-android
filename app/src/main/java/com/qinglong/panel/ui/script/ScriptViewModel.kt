package com.qinglong.panel.ui.script

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
import java.text.Collator
import java.util.Locale

/** 脚本列表/操作统一状态 */
data class ScriptListState(
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    /** 运行/删除等操作进行中 */
    val actionInFlight: Boolean = false,
    /** 当前所在目录路径，空串表示根目录 */
    val currentPath: String = "",
    val entries: List<ScriptFile> = emptyList(),
    /** 本地过滤关键字（按文件名） */
    val searchQuery: String = "",
)

class ScriptViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(ScriptListState())
        private set

    init {
        load()
    }

    // ------------------------------------------------------------ 列表与导航

    /** 加载指定目录（空串=根目录） */
    fun load(path: String = state.currentPath) {
        state = state.copy(currentPath = path, loading = true, error = null)
        viewModelScope.launch {
            repo.scripts(path.ifBlank { null }).fold(
                onSuccess = { raw ->
                    state = state.copy(
                        loading = false,
                        entries = raw.sortedWith(SCRIPT_ORDER),
                    )
                },
                onFailure = { e ->
                    state = state.copy(
                        loading = false,
                        error = QinglongRepository.errorMessage(e),
                    )
                },
            )
        }
    }

    /** 进入子目录（dirKey 为该目录的完整 key 路径） */
    fun navigateInto(dirKey: String) {
        load(dirKey)
    }

    /** 回到上一级目录 */
    fun navigateUp() {
        if (state.currentPath.isBlank()) return
        val parent = state.currentPath.substringBeforeLast("/", "")
        load(parent)
    }

    /** 跳转到面包屑指定的任意层目录 */
    fun navigateTo(path: String) {
        load(path)
    }

    fun onSearchChange(q: String) {
        state = state.copy(searchQuery = q)
    }

    fun clearSearch() {
        state = state.copy(searchQuery = "")
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    // ------------------------------------------------------------ 详情读取

    /** 读取脚本内容，返回原始文本 */
    suspend fun readDetail(path: String, file: String): Result<String> =
        repo.scriptDetail(path.ifBlank { null }, file)

    // ------------------------------------------------------------ 写操作

    fun run(filename: String, path: String, after: () -> Unit = {}) =
        act("运行", { repo.runScript(filename, path.ifBlank { null }) }, after)

    fun stop(filename: String, path: String, pid: Long? = null, after: () -> Unit = {}) =
        act("停止", { repo.stopScript(filename, path.ifBlank { null }, pid) }, after)

    fun delete(filename: String, path: String, type: String?, after: () -> Unit = {}) =
        act("删除", { repo.deleteScript(filename, path.ifBlank { null }, type) }, after)

    fun rename(filename: String, path: String, newFilename: String, after: () -> Unit = {}) =
        act("重命名", { repo.renameScript(filename, path.ifBlank { null }, newFilename) }, after)

    fun saveContent(filename: String, path: String, content: String, after: () -> Unit = {}) =
        act("保存", { repo.saveScript(filename, path.ifBlank { null }, content) }, after)

    fun uploadContent(fileName: String, path: String, content: String, directory: String?, after: () -> Unit = {}) =
        act("上传", { repo.uploadScriptContent(fileName, path, content, directory?.ifBlank { null }) }, after)

    fun uploadFile(fileName: String, path: String, bytes: ByteArray, directory: String?, after: () -> Unit = {}) =
        act("上传", { repo.uploadScriptFile(fileName, path, bytes, directory?.ifBlank { null }) }, after)

    // ------------------------------------------------------------ 内部

    /** 统一收敛写操作结果到 message，并可选地执行后续动作（如刷新/返回） */
    private fun act(label: String, block: suspend () -> Result<Unit>, after: () -> Unit) {
        if (state.actionInFlight) return
        state = state.copy(actionInFlight = true)
        viewModelScope.launch {
            block().fold(
                onSuccess = {
                    state = state.copy(actionInFlight = false, message = "$label 成功")
                    after()
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

    companion object {
        /** 目录优先、名称升序（忽略大小写） */
        private val SCRIPT_ORDER = compareBy<ScriptFile> { it.type != "directory" }
            .thenBy(Collator.getInstance(Locale.CHINESE)) { it.title }

        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScriptViewModel(repo) }
        }
    }
}
