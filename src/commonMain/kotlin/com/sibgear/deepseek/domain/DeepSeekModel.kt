package com.sibgear.deepseek.domain

data class DeepSeekModel(
    val id: String,
)

object DeepSeekModels {
    val Default = DeepSeekModel("deepseek-v4-flash")

    val Available = listOf(
        Default,
        DeepSeekModel("deepseek-v4-pro"),
        DeepSeekModel("deepseek-chat"),
        DeepSeekModel("deepseek-reasoner"),
    )
}
