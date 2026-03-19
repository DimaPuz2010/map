package com.example.map.data

import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile

interface RecommendationRepository {
    suspend fun getRecommendations(
        location: SelectedLocation,
        profile: UserProfile,
    ): List<Recommendation>
}
