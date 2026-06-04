package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sibgear.deepseek.ui.DeepSeekAppScreen
import com.sibgear.deepseek.ui.DeepSeekAppViewModel

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) {
        DeepSeekAppViewModel(coroutineScope = scope)
    }

    DeepSeekAppScreen(
        state = viewModel.state,
        onEvent = viewModel::onEvent,
    )
}
