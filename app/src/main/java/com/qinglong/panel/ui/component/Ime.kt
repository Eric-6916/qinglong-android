package com.qinglong.panel.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * IME 感知修饰符：输入框聚焦、软键盘升起后，自动把输入框滚入可见区域。
 *
 * 背景：targetSdk 35 边到边（edge-to-edge）下窗口不再随软键盘收缩
 * （manifest 的 adjustResize 失效），需由内容侧消费 IME insets。
 * 配套用法：滚动容器加 `Modifier.imePadding()`，输入框加本修饰符，
 * 两者配合保证聚焦输入框不被软键盘遮挡。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.imeAware(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            // 等待软键盘升起动画结束后再滚动，确保视野已收缩到位
            delay(350)
            runCatching { requester.bringIntoView() }
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.isFocused }
}
