package com.example.map.data.network.openrouter

import com.example.map.data.network.openrouter.dto.OpenRouterRequest
import com.example.map.data.network.openrouter.dto.OpenRouterResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun chatCompletion(@Body request: OpenRouterRequest): OpenRouterResponse
}
