package com.example.map.data

import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class FakePlacesDataSource {

    fun recommend(
        location: SelectedLocation,
        profile: UserProfile,
    ): List<Recommendation> {
        return seedPlaces
            .map { place ->
                val distance = distanceMeters(
                    lat1 = location.latitude,
                    lon1 = location.longitude,
                    lat2 = place.latitude,
                    lon2 = place.longitude,
                )
                place.copy(
                    distanceMeters = distance,
                    reason = buildReason(place.category, profile, distance),
                )
            }
            .filter { it.distanceMeters <= 8_000 }
            .sortedByDescending { score(it, profile) }
            .take(5)
    }

    private fun score(recommendation: Recommendation, profile: UserProfile): Double {
        val preferredBoost = if (recommendation.category in profile.preferredCategories) 2.5 else 0.0
        val dislikedPenalty = if (recommendation.category in profile.dislikedCategories) 3.0 else 0.0
        val historyBoost = if (profile.history.none { it == recommendation.category }) 0.8 else 0.0
        val distanceFactor = 5.0 - recommendation.distanceMeters / 1_500.0
        return recommendation.rating + preferredBoost + historyBoost + distanceFactor - dislikedPenalty
    }

    private fun buildReason(category: String, profile: UserProfile, distance: Int): String {
        val categoryReason = when {
            category in profile.preferredCategories -> "Matches the preferred ${category.lowercase()} category."
            category in profile.dislikedCategories -> "Kept only as a nearby fallback option."
            else -> "Fits the ${profile.travelStyle.lowercase()} profile."
        }
        return "$categoryReason Around ${distance} m from the selected point."
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Int {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val targetLat = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(originLat) * cos(targetLat)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).roundToInt()
    }

    companion object {
        private val seedPlaces = listOf(
            Recommendation(
                id = "museum-1",
                name = "Historic Museum",
                category = "Museum",
                latitude = 54.9918,
                longitude = 73.3715,
                address = "Lenina St, 12",
                distanceMeters = 0,
                rating = 4.8,
                imageUrl = "https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b",
                reason = "",
                source = "local-fallback",
            ),
            Recommendation(
                id = "park-1",
                name = "Riverside Park",
                category = "Park",
                latitude = 54.9840,
                longitude = 73.3670,
                address = "Embankment, 5",
                distanceMeters = 0,
                rating = 4.6,
                imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb",
                reason = "",
                source = "local-fallback",
            ),
            Recommendation(
                id = "view-1",
                name = "City Viewpoint",
                category = "Viewpoint",
                latitude = 54.9982,
                longitude = 73.3820,
                address = "Tourist St, 3",
                distanceMeters = 0,
                rating = 4.7,
                imageUrl = "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee",
                reason = "",
                source = "local-fallback",
            ),
            Recommendation(
                id = "cafe-1",
                name = "Traveler Coffee",
                category = "Cafe",
                latitude = 54.9890,
                longitude = 73.3560,
                address = "Marksa Ave, 44",
                distanceMeters = 0,
                rating = 4.5,
                imageUrl = "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085",
                reason = "",
                source = "local-fallback",
            ),
            Recommendation(
                id = "family-1",
                name = "Family Ethno Park",
                category = "Family",
                latitude = 55.0042,
                longitude = 73.3955,
                address = "Parkovaya St, 18",
                distanceMeters = 0,
                rating = 4.4,
                imageUrl = "https://images.unsplash.com/photo-1511497584788-876760111969",
                reason = "",
                source = "local-fallback",
            ),
            Recommendation(
                id = "art-1",
                name = "Modern Art Gallery",
                category = "Art",
                latitude = 54.9790,
                longitude = 73.3785,
                address = "Chekhova St, 9",
                distanceMeters = 0,
                rating = 4.9,
                imageUrl = "https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b",
                reason = "",
                source = "local-fallback",
            ),
        )
    }
}
