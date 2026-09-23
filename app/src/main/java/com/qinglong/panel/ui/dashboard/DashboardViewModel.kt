package com.qinglong.panel.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.qinglong.panel.data.model.DashboardOverview
import com.qinglong.panel.data.model.DashboardRuntime
import com.qinglong.panel.data.model.DashboardSystem
import com.qinglong.panel.data.model.TrendPoint
import com.qinglong.panel.data.repository.ApiException
import com.qinglong.panel.data.repository.QinglongRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val overview: DashboardOverview? = null,
    val runtime: DashboardRuntime? = null,
    val system: DashboardSystem? = null,
    val trend: List<TrendPoint> = emptyList(),
    /** 部分卡片加载失败时做降级提示，不阻塞其他卡片 */
    val degraded: List<String> = emptyList(),
    /**
     * 面板未开放 dashboard 数据权限时为 true。
     * 青龙 master 线面板的「应用设置」没有 dashboard 权限项（AppScope 仅八种），
     * /open/dashboard/ 下的接口对任何应用都返回 401「暂无权限」——这不是用户漏勾权限，
     * 需要向用户说明而非展示一堆报错卡片。
     */
    val dashboardPermissionDenied: Boolean = false,
)

class DashboardViewModel(private val repo: QinglongRepository) : ViewModel() {

    var state by mutableStateOf(DashboardUiState())
        private set

    init {
        load()
    }

    fun load() {
        val first = state.overview == null
        state = state.copy(loading = first, refreshing = !first)
        viewModelScope.launch {
            // 四路并行，单路失败只降级对应卡片
            // 注意：不能用 listOf(async{...}) —— 各 Deferred 返回类型不同会被推断为 List<Any>
            val deferredOverview = async { repo.overview() }
            val deferredRuntime = async { repo.runtime() }
            val deferredSystem = async { repo.dashboardSystem() }
            val deferredTrend = async { repo.trend() }
            val degraded = mutableListOf<String>()
            var permissionDenied = false
            fun noteFailure(e: Throwable): String? {
                if (e is ApiException && e.message?.startsWith("应用权限不足") == true) {
                    permissionDenied = true
                }
                return e.message
            }
            var next = state

            deferredOverview.await().fold(
                onSuccess = { value -> next = next.copy(overview = value) },
                onFailure = { e -> noteFailure(e); degraded += "概览" },
            )
            deferredRuntime.await().fold(
                onSuccess = { value -> next = next.copy(runtime = value) },
                onFailure = { e -> noteFailure(e); degraded += "运行时" },
            )
            deferredSystem.await().fold(
                onSuccess = { value -> next = next.copy(system = value) },
                onFailure = { e -> noteFailure(e); degraded += "系统" },
            )
            deferredTrend.await().fold(
                onSuccess = { value -> next = next.copy(trend = value) },
                onFailure = { e -> noteFailure(e); degraded += "趋势" },
            )

            state = next.copy(
                loading = false,
                refreshing = false,
                degraded = degraded,
                dashboardPermissionDenied = permissionDenied,
            )
        }
    }

    companion object {
        fun factory(repo: QinglongRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(repo) }
        }
    }
}
