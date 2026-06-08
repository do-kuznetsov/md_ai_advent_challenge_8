package com.sibgear.deepseek.domain

object DeepSeekModels {
    val Default = AiModel("deepseek-v4-flash")

    val Available = listOf(
        Default,
        AiModel("deepseek-v4-pro"),
        AiModel("deepseek-chat"),
        AiModel("deepseek-reasoner"),
    )
}
