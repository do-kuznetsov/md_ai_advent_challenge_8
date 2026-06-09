package com.sibgear.deepseek.chat.data.openrouter.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelDto> = emptyList(),
)

@Serializable
internal data class OpenRouterModelDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("supported_parameters")
    val supportedParameters: List<String>? = null,
)

@Serializable
internal data class OpenRouterModelsApiErrorResponse(
    val error: OpenRouterModelsApiError? = null,
)

@Serializable
internal data class OpenRouterModelsApiError(
    val message: String? = null,
)
