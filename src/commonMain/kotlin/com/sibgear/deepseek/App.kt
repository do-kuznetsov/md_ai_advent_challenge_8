package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sibgear.deepseek.data.KtorDeepSeekRepository
import com.sibgear.deepseek.ui.DeepSeekScreen
import com.sibgear.deepseek.ui.DeepSeekViewModel

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) {
        DeepSeekViewModel(
            repository = KtorDeepSeekRepository(),
            coroutineScope = scope,
        )
    }

    DeepSeekScreen(
        state = viewModel.state,
        onEvent = viewModel::onEvent,
    )
}
