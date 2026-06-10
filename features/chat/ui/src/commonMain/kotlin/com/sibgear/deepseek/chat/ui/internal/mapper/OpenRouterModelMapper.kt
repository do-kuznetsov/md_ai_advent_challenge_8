package com.sibgear.deepseek.chat.ui.internal.mapper

import com.sibgear.deepseek.chat.domain.model.AiModel

internal fun selectOpenRouterModels(
    models: List<AiModel>,
    filter: String,
): List<AiModel> {
    val words = filter.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    val filteredModels = models.filter { model ->
        words.all { word -> model.searchText.contains(word, ignoreCase = true) }
    }

    return listOfNotNull(
        filteredModels.bestModel { it.modelSizeBillions?.let { size -> size in 7.0..14.0 } == true }
            ?.withTier(OpenRouterModelTier.Low),
        filteredModels.bestModel { it.modelSizeBillions?.let { size -> size in 30.0..70.0 } == true }
            ?.withTier(OpenRouterModelTier.Mid),
        filteredModels.bestModel {
            it.isReasoningModel || (it.isMixtureOfExperts && (it.modelSizeBillions ?: 0.0) >= 200.0)
        }?.withTier(OpenRouterModelTier.High),
    ).distinctBy { it.id }
}

private enum class OpenRouterModelTier(val label: String) {
    Low("low"),
    Mid("mid"),
    High("high"),
}

private fun AiModel.withTier(tier: OpenRouterModelTier): AiModel =
    copy(displayName = "[${tier.label}] $displayName")

private fun List<AiModel>.bestModel(predicate: (AiModel) -> Boolean): AiModel? =
    firstOrNull(predicate)

private val AiModel.searchText: String
    get() = "$id $displayName $description"

private val AiModel.isMixtureOfExperts: Boolean
    get() {
        val text = searchText
        return text.contains("moe", ignoreCase = true) ||
            text.contains("mixture-of-experts", ignoreCase = true) ||
            text.contains("experts", ignoreCase = true)
    }

private val AiModel.isReasoningModel: Boolean
    get() {
        val text = searchText
        return supportedParameters.any { it.equals("reasoning", ignoreCase = true) } ||
            text.contains("reasoning", ignoreCase = true) ||
            text.contains("reasoner", ignoreCase = true) ||
            text.contains("thinking", ignoreCase = true) ||
            Regex("(^|[^a-z0-9])r1([^a-z0-9]|$)", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

private val AiModel.modelSizeBillions: Double?
    get() {
        val text = searchText
        val moeSizes = Regex("""(\d+(?:\.\d+)?)\s*x\s*(\d+(?:\.\d+)?)\s*b""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { match -> match.groupValues[1].toDouble() * match.groupValues[2].toDouble() }
        val sizes = Regex("""(\d+(?:\.\d+)?)\s*(?:b|billion)""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { match -> match.groupValues[1].toDouble() }

        return (moeSizes + sizes).maxOrNull()
    }
