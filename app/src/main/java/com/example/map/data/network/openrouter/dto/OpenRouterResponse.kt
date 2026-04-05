package com.example.map.data.network.openrouter.dto

data class OpenRouterResponse(
    val choices: List<OpenRouterChoice> = emptyList(),
)

data class OpenRouterChoice(
    val message: OpenRouterAssistantMessage,
    @com.google.gson.annotations.SerializedName("finish_reason")
    val finishReason: String? = null,
)

data class OpenRouterAssistantMessage(
    val role: String = "assistant",
    val content: String? = null,
    val reasoning: String? = null,
)
