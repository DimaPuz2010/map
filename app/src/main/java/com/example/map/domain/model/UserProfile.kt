package com.example.map.domain.model

data class UserProfile(
    val userId: String,
    val displayName: String,
    val preferredCategories: List<String>,
    val dislikedCategories: List<String>,
    val travelStyle: String,
    val budgetLevel: String,
    val companionType: String,
    val language: String,
    val history: List<String>,
)
