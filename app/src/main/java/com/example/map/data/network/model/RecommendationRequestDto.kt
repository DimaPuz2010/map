package com.example.map.data.network.model

data class RecommendationRequestDto(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val preferredCategories: List<String>,
    val dislikedCategories: List<String>,
    val travelStyle: String,
    val budgetLevel: String,
    val companionType: String,
    val language: String,
    val history: List<String>,
)
