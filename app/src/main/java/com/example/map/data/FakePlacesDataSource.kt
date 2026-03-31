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
import android.util.Log
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
    private val seedPlaces: List<Recommendation> = buildSeedPlaces()

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
                    _places.value = if (recommendations.isNotEmpty()) recommendations else seedPlaces
                    loadAttempted = true
                    Log.i(
                        "Recommendations",
                        "Loaded ${recommendations.size} places from DB, seedUsed=${recommendations.isEmpty()}",
                    )
                } else {
                    Log.w("Recommendations", "API is null, using seed places")
                    _places.value = seedPlaces
                    loadAttempted = true
                }
            } catch (e: Exception) {
                Log.e("Recommendations", "Error loading recommendations from DB", e)
                _places.value = seedPlaces
                loadAttempted = true
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
        Log.i(
            "Recommendations",
            "Fallback recommend for lat=${location.latitude}, lon=${location.longitude}",
        )
        // Если данные еще не загружены, пытаемся загрузить синхронно
        if (_places.value.isEmpty() && !loadAttempted) {
            loadPlacesFromDatabase()
        }

        val allPlaces = _places.value
        if (allPlaces.isEmpty()) {
            return emptyList()
        }

        val preferred = tokenizeList(profile.preferredCategories?:"")
        val disliked = tokenizeList(profile.dislikedCategories?:"")
        val history = tokenizeList(profile.history?:"")

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
                    reason = buildReason(place.category, profile, preferred, disliked, distance),
                )
            }
            .filter { it.distanceMeters <= 8_000 }
            .sortedByDescending { score(it, preferred, disliked, history) }
            .take(5)
            .also { Log.i("Recommendations", "Fallback result size=${it.size}") }
    }


    private fun score(
        recommendation: Recommendation,
        preferred: Set<String>,
        disliked: Set<String>,
        history: Set<String>,
    ): Double {
        val category = recommendation.category.lowercase()
        val preferredBoost = if (preferred.contains(category)) 2.5 else 0.0
        val dislikedPenalty = if (disliked.contains(category)) 3.0 else 0.0
        val historyBoost = if (!history.contains(category)) 0.8 else 0.0
        val distanceFactor = 5.0 - recommendation.distanceMeters / 1_500.0
        return recommendation.rating + preferredBoost + historyBoost + distanceFactor - dislikedPenalty
    }

    private fun buildReason(
        category: String,
        profile: UserProfile,
        preferred: Set<String>,
        disliked: Set<String>,
        distance: Int,
    ): String {
        val categoryKey = category.lowercase()
        val categoryReason = when {
            preferred.contains(categoryKey) -> "Matches your preferred ${category.lowercase()} category."
            disliked.contains(categoryKey) -> "Kept only as a nearby fallback option."
            else -> "Fits the ${profile.travelStyle?.lowercase()} profile."
        }
        return "$categoryReason Around $distance m from the selected point."
    }

    private fun tokenizeList(text: String): Set<String> {
        return text
            .split(',', ';', '|', '\n', '\t', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
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

    private fun buildSeedPlaces(): List<Recommendation> {
        // Офлайн-датасет вокруг Омска (чтобы основной сценарий работал без логина/бэкенда).
        return listOf(
            Recommendation(
                id = "seed-omsk-1",
                name = "Омская крепость",
                category = "Museum",
                latitude = 54.9917,
                longitude = 73.3638,
                address = "Омск",
                distanceMeters = 0,
                rating = 4.6,
                imageUrl = "",
                reason = "",
                source = "seed",
            ),
            Recommendation(
                id = "seed-omsk-2",
                name = "Парк культуры и отдыха",
                category = "Park",
                latitude = 54.9969,
                longitude = 73.3682,
                address = "Омск",
                distanceMeters = 0,
                rating = 4.5,
                imageUrl = "",
                reason = "",
                source = "seed",
            ),
            Recommendation(
                id = "seed-omsk-3",
                name = "Набережная Иртыша",
                category = "Viewpoint",
                latitude = 54.9879,
                longitude = 73.3688,
                address = "Омск",
                distanceMeters = 0,
                rating = 4.7,
                imageUrl = "",
                reason = "",
                source = "seed",
            ),
            Recommendation(
                id = "seed-omsk-4",
                name = "Театр драмы",
                category = "Theatre",
                latitude = 54.9896,
                longitude = 73.3680,
                address = "Омск",
                distanceMeters = 0,
                rating = 4.8,
                imageUrl = "",
                reason = "",
                source = "seed",
            ),
            Recommendation(
                id = "seed-omsk-5",
                name = "Музей искусств",
                category = "Museum",
                latitude = 54.9908,
                longitude = 73.3714,
                address = "Омск",
                distanceMeters = 0,
                rating = 4.7,
                imageUrl = "",
                reason = "",
                source = "seed",
            ),
        )
    }
}
