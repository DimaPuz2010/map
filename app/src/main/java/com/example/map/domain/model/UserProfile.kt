package com.example.map.domain.model

data class UserProfile(
    val id: String? = "",
    val displayName: String? = "",
    val preferredCategories: String? = "",
    val dislikedCategories: String? = "",
    val travelStyle: String? = "",
    val budgetLevel: String? = "",
    val companionType: String? = "",
    val language: String? = "ru",
    val history: String? = "",
)
