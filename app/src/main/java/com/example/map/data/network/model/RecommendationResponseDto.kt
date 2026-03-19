package com.example.map.data.network.model

data class RecommendationResponseDto(
    val recommendations: List<RecommendationDto>,
)

data class RecommendationDto(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val distanceMeters: Int,
    val rating: Double,
    val imageUrl: String,
    val reason: String,
    val source: String,
)
