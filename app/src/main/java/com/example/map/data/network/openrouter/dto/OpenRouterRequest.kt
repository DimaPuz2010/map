package com.example.map.data.network.openrouter.dto

import com.google.gson.annotations.SerializedName

data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

data class OpenRouterMessage(
    val role: String,
    val content: String,
)
