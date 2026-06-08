package com.sibgear.deepseek.ui

import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.AiProvider

object ChatDefaults {
    val DefaultModel = AiModel(
        id = "deepseek-v4-flash",
        provider = AiProvider.DeepSeek,
    )
}
