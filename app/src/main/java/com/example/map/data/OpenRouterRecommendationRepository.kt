package com.example.map.data

import android.util.Log
import com.example.map.data.network.openrouter.OpenRouterApi
import com.example.map.data.network.openrouter.dto.OpenRouterMessage
import com.example.map.data.network.openrouter.dto.OpenRouterRequest
import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import com.example.map.llm.LlmPlaceDto
import com.example.map.llm.LlmRecommendationsResponse
import com.example.map.llm.RecommendationPromptBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class OpenRouterRecommendationRepository(
    private val api: OpenRouterApi,
    private val fallback: FakePlacesDataSource,
    private val model: String = "nvidia/nemotron-3-nano-30b-a3b:free",
    private val maxPlaces: Int = 5,
) : RecommendationRepository {

    private val gson = Gson()

    companion object {
        private const val TAG = "OpenRouterRepo"
        private const val SYSTEM_PROMPT = """Ты — локальный ассистент внутри Android-приложения с картой.
Отвечай по-русски.

Требование: возвращай СТРОГО валидный JSON и больше ничего (никакого markdown, никаких комментариев, никаких дополнительных полей).

Схема ответа:
{
  "places": [
    {
      "name": "string",
      "category": "string",
      "latitude": 0.0,
      "longitude": 0.0,
      "address": "string",
      "distanceMeters": 0,
      "rating": 0.0,
      "reason": "string"
    }
  ]
}

Если подходящих точек придумать нельзя, верни:
{ "places": [] }"""
    }

    override suspend fun getRecommendations(
        location: SelectedLocation,
        profile: UserProfile,
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        runCatching {
            val userPrompt = RecommendationPromptBuilder.build(
                RecommendationPromptBuilder.Input(
                    location = location,
                    profile = profile,
                    maxPlaces = maxPlaces,
                    maxRadiusMeters = 8_000,
                ),
            )

            Log.i(TAG, "=== OpenRouter START === model=$model lat=${location.latitude} lon=${location.longitude}")
            Log.d(TAG, "--- PROMPT (${userPrompt.length} chars) ---\n$userPrompt")

            val startMs = System.currentTimeMillis()
            val response = api.chatCompletion(
                OpenRouterRequest(
                    model = model,
                    messages = listOf(
                        OpenRouterMessage(role = "system", content = SYSTEM_PROMPT),
                        OpenRouterMessage(role = "user", content = userPrompt),
                    ),
                ),
            )
            val elapsedMs = System.currentTimeMillis() - startMs

            val choice = response.choices.firstOrNull()
            val content = choice?.message?.content.orEmpty()
            val reasoning = choice?.message?.reasoning.orEmpty()
            val finishReason = choice?.finishReason
            Log.i(TAG, "--- RAW RESPONSE finish=$finishReason content=${content.length}chars reasoning=${reasoning.length}chars (${elapsedMs}ms) ---")
            content.chunked(3000).forEachIndexed { i, chunk -> Log.d(TAG, "[content:$i] $chunk") }

            val raw = if (extractJsonObject(content) != null) content else reasoning.ifBlank { content }
            val extracted = extractJsonObject(raw)
            if (extracted == null) {
                Log.w(TAG, "--- JSON EXTRACT FAILED: no {...} found in content or reasoning ---")
            } else {
                Log.d(TAG, "--- EXTRACTED JSON (${extracted.length} chars) ---\n$extracted")
            }

            val places = parsePlaces(extracted)
            if (places.isEmpty()) {
                Log.w(TAG, "=== OpenRouter FALLBACK === 0 places → FakePlacesDataSource")
                fallback.recommend(location, profile)
            } else {
                Log.i(TAG, "=== OpenRouter SUCCESS === ${places.size} places:")
                places.forEachIndexed { i, p ->
                    Log.i(TAG, "  [${i + 1}] ${p.name} (${p.category}) lat=${p.latitude} lon=${p.longitude} rating=${p.rating}")
                }
                places.mapIndexed { idx, p -> p.toRecommendation(location, idx) }
            }
        }.getOrElse { e ->
            if (e is HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                Log.e(TAG, "=== OpenRouter HTTP ${e.code()} === body=$body", e)
            } else {
                Log.e(TAG, "=== OpenRouter ERROR === ${e::class.simpleName}: ${e.message}", e)
            }
            fallback.recommend(location, profile)
        }
    }

    private fun parsePlaces(extracted: String?): List<LlmPlaceDto> {
        if (extracted == null) return emptyList()
        return runCatching {
            gson.fromJson(extracted, LlmRecommendationsResponse::class.java).places
        }.getOrElse { e ->
            Log.w(TAG, "--- JSON PARSE FAILED: ${e.message} ---")
            emptyList()
        }
    }

    private fun extractJsonObject(text: String): String? {
        val stripped = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
    }

    private fun LlmPlaceDto.toRecommendation(origin: SelectedLocation, index: Int): Recommendation {
        val dist = haversineMeters(origin.latitude, origin.longitude, latitude, longitude)
            .takeIf { it > 0 } ?: distanceMeters
        return Recommendation(
            id = "or-$index-$latitude-$longitude".lowercase(Locale.US),
            name = name,
            category = category,
            latitude = latitude,
            longitude = longitude,
            address = address,
            distanceMeters = dist,
            rating = rating,
            imageUrl = "",
            reason = reason,
            source = "openrouter",
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2))
        return (r * 2 * atan2(sqrt(a), sqrt(1 - a))).roundToInt()
    }
}
