package com.example.map.llm

data class LlmPlaceDto(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val distanceMeters: Int = 0,
    val rating: Double = 0.0,
    val reason: String = "",
)

data class LlmRecommendationsResponse(
    val places: List<LlmPlaceDto> = emptyList(),
)

