package com.example.map.ui

import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile

data class MainUiState(
    val profile: UserProfile = defaultProfile,
    val selectedLocation: SelectedLocation? = null,
    val recommendations: List<Recommendation> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isMapReady: Boolean = false,
)

val defaultProfile = UserProfile(
    userId = "guest-omsk",
    displayName = "Alexey",
    preferredCategories = listOf("Museum", "Park", "Viewpoint"),
    dislikedCategories = listOf("Nightlife"),
    travelStyle = "Calm city trips",
    budgetLevel = "Medium",
    companionType = "Family",
    language = "ru",
    history = listOf("Museum"),
)
