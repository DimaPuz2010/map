package com.example.map.data

import com.example.map.data.network.Api
import com.example.map.data.network.model.RecommendationRequestDto
import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile

class RemoteFirstRecommendationRepository(
    private val api: Api?,
    private val fallback: FakePlacesDataSource,
) : RecommendationRepository {

    override suspend fun getRecommendations(
        location: SelectedLocation,
        profile: UserProfile,
    ): List<Recommendation> {
        val remoteResult = runCatching {
            val service = api ?: error("Recommendation API is not configured")
            service.getRecommendations(
                RecommendationRequestDto(
                    userId = profile.userId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    preferredCategories = profile.preferredCategories,
                    dislikedCategories = profile.dislikedCategories,
                    travelStyle = profile.travelStyle,
                    budgetLevel = profile.budgetLevel,
                    companionType = profile.companionType,
                    language = profile.language,
                    history = profile.history,
                ),
            )

        }.getOrNull()

        return remoteResult?.recommendations?.map {
            Recommendation(
                id = it.id,
                name = it.name,
                category = it.category,
                latitude = it.latitude,
                longitude = it.longitude,
                address = it.address,
                distanceMeters = it.distanceMeters,
                rating = it.rating,
                imageUrl = it.imageUrl,
                reason = it.reason,
                source = it.source,
            )
        } ?: fallback.recommend(location, profile)
    }
}
