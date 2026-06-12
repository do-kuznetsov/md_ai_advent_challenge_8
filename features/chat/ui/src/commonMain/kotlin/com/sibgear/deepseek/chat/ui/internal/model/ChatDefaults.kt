package com.sibgear.deepseek.chat.ui.internal.model

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider

internal object ChatDefaults {
    val DefaultModel = AiModel(
        id = "deepseek-v4-flash",
        provider = AiProvider.DeepSeek,
        contextLength = DefaultDeepSeekContextLength,
    )

    private const val DefaultDeepSeekContextLength = 1_000_000
}
