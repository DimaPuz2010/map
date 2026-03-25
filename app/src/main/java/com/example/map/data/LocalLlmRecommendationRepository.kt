package com.example.map.data

import com.example.map.data.RecommendationRepository
import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import com.example.map.llm.LlmRecommendationsResponse
import com.example.map.llm.LocalLlamaClient
import com.example.map.llm.LlmPlaceDto
import com.example.map.llm.RecommendationPromptBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.atan2

class LocalLlmRecommendationRepository(
    private val llamaClientDeferred: Deferred<LocalLlamaClient>,
    private val fallback: FakePlacesDataSource,
    private val maxPlaces: Int = 5,
) : RecommendationRepository {

    private val generationMutex = Mutex()
    private val gson = Gson()

    override suspend fun getRecommendations(
        location: SelectedLocation,
        profile: UserProfile,
    ): List<Recommendation> = withContext(Dispatchers.Default) {
        runCatching {
            val llama = llamaClientDeferred.await()

            generationMutex.withLock {
                llama.startNewSession()

                val prompt = RecommendationPromptBuilder.build(
                    RecommendationPromptBuilder.Input(
                        location = location,
                        profile = profile,
                        maxPlaces = maxPlaces,
                        maxRadiusMeters = 8_000,
                    ),
                )

                val raw = llama.generate(prompt)
                val parsedPlaces = parsePlacesJson(raw)
                if (parsedPlaces.isEmpty()) {
                    fallback.recommend(location, profile)
                } else {
                    parsedPlaces.mapIndexed { idx, placeDto ->
                        placeToRecommendation(
                            place = placeDto,
                            selectedLocation = location,
                            index = idx,
                        )
                    }
                }
            }
        }.getOrElse {
            fallback.recommend(location, profile)
        }
    }

    private fun parsePlacesJson(raw: String): List<LlmPlaceDto> {
        val json = extractJsonObject(raw) ?: return emptyList()

        return runCatching {
            val response = gson.fromJson(json, LlmRecommendationsResponse::class.java)
            response.places
        }.getOrElse { emptyList() }
    }

    private fun placeToRecommendation(
        place: LlmPlaceDto,
        selectedLocation: SelectedLocation,
        index: Int,
    ): Recommendation {
        val computedDistance = haversineMeters(
            lat1 = selectedLocation.latitude,
            lon1 = selectedLocation.longitude,
            lat2 = place.latitude,
            lon2 = place.longitude,
        ).takeIf { it > 0 } ?: place.distanceMeters

        return Recommendation(
            id = "llm-$index-${place.latitude}-${place.longitude}".lowercase(Locale.US),
            name = place.name,
            category = place.category,
            latitude = place.latitude,
            longitude = place.longitude,
            address = place.address,
            distanceMeters = computedDistance,
            rating = place.rating,
            imageUrl = "",
            reason = place.reason,
            source = "local-llm",
        )
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun haversineMeters(
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

