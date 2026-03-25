package com.example.map.data

import androidx.lifecycle.lifecycleScope
import com.example.map.MainActivity
import com.example.map.data.network.NetworkModule.createRecommendationApi
import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class FakePlacesDataSource(
    private val context: MainActivity
) {
    // Используем StateFlow для реактивного обновления данных
    private val _places = MutableStateFlow<List<Recommendation>>(emptyList())
    val places: StateFlow<List<Recommendation>> = _places.asStateFlow()

    private var isLoading = false
    private var loadAttempted = false

    init {
        // Загружаем данные при инициализации
        loadPlacesFromDatabase()
    }

    /**
     * Загружает точки из базы данных через API
     */
    fun loadPlacesFromDatabase() {
        if (isLoading || loadAttempted) return

        isLoading = true
        context.lifecycleScope.launch {
            try {
                val api = createRecommendationApi()
                if (api != null) {
                    val recommendations = api.getRecommendationsFromDb(Data.userAuth)
                    _places.value = recommendations
                    loadAttempted = true
                } else {
                    println("API is null, cannot load recommendations from database")
                }
            } catch (e: Exception) {
                println("Error loading recommendations from database: ${e.message}")
                _places.value = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Получает рекомендации на основе локации и профиля пользователя
     */
    fun recommend(
        location: SelectedLocation,
        profile: UserProfile,
    ): List<Recommendation> {
        // Если данные еще не загружены, пытаемся загрузить синхронно
        if (_places.value.isEmpty() && !loadAttempted) {
            loadPlacesFromDatabase()
        }

        val allPlaces = _places.value
        if (allPlaces.isEmpty()) {
            return emptyList()
        }

        return allPlaces
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

    /**
     * Обновляет данные из базы данных (можно вызвать принудительно)
     */
    suspend fun refreshPlaces() {
        try {
            val api = createRecommendationApi()
            if (api != null) {
                val recommendations = api.getRecommendationsFromDb(Data.userAuth)
                _places.value = recommendations
                loadAttempted = true
            }
        } catch (e: Exception) {
            println("Error refreshing recommendations: ${e.message}")
        }
    }
    private fun score(recommendation: Recommendation, profile: UserProfile): Double {
        val preferredBoost = if (recommendation.category in profile.preferredCategories) 2.5 else 0.0
        val dislikedPenalty = if (recommendation.category in profile.dislikedCategories) 3.0 else 0.0
        val historyBoost = if (profile.history.none { it.equals(recommendation.category)}) 0.8 else 0.0
        val distanceFactor = 5.0 - recommendation.distanceMeters / 1_500.0
        return recommendation.rating + preferredBoost + historyBoost + distanceFactor - dislikedPenalty
    }

    private fun buildReason(category: String, profile: UserProfile, distance: Int): String {
        val categoryReason = when {
            category in profile.preferredCategories -> "Matches the preferred ${category.lowercase()} category."
            category in profile.dislikedCategories -> "Kept only as a nearby fallback option."
            else -> "Fits the ${profile.travelStyle.lowercase()} profile."
        }
        return "$categoryReason Around $distance m from the selected point."
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
}