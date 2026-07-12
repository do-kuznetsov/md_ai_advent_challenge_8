package com.sibgear.deepseek.chat.data.ollama.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class OllamaTagsResponse(
    val models: List<OllamaTagModelDto> = emptyList(),
)

@Serializable
internal data class OllamaTagModelDto(
    val name: String? = null,
    val model: String? = null,
    val details: OllamaModelDetailsDto? = null,
)

@Serializable
internal data class OllamaShowRequest(
    val model: String,
    val verbose: Boolean = false,
)

@Serializable
internal data class OllamaShowResponse(
    val capabilities: List<String> = emptyList(),
    val details: OllamaModelDetailsDto? = null,
    @SerialName("model_info")
    val modelInfo: Map<String, JsonElement> = emptyMap(),
)

@Serializable
internal data class OllamaModelDetailsDto(
    val family: String? = null,
    val families: List<String>? = null,
    @SerialName("parameter_size")
    val parameterSize: String? = null,
    @SerialName("quantization_level")
    val quantizationLevel: String? = null,
)

@Serializable
internal data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val think: Boolean? = null,
    val options: OllamaChatOptions? = null,
)

@Serializable
internal data class OllamaChatMessage(
    val role: String = "",
    val content: String = "",
    val thinking: String? = null,
)

@Serializable
internal data class OllamaChatOptions(
    val temperature: Float? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("num_ctx")
    val numCtx: Int? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    val seed: Int? = null,
    @SerialName("repeat_penalty")
    val repeatPenalty: Float? = null,
    val stop: List<String>? = null,
)

@Serializable
internal data class OllamaChatResponse(
    val message: OllamaChatMessage? = null,
    val done: Boolean = false,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
)

@Serializable
internal data class OllamaApiErrorResponse(
    val error: String? = null,
)
