package com.example.map.llm

import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile

object RecommendationPromptBuilder {
    data class Input(
        val location: SelectedLocation,
        val profile: UserProfile,
        val maxPlaces: Int = 5,
        val maxRadiusMeters: Int = 4_000,
    )

    fun build(input: Input): String {
        val loc = input.location
        val p = input.profile

        return """
Входные данные для генерации туристических точек:

Точка (куда направлен поиск):
- latitude: ${loc.latitude}
- longitude: ${loc.longitude}
- радиус: <= ${input.maxRadiusMeters} метров

Профиль пользователя:
- preferredCategories: ${p.preferredCategories}
- dislikedCategories: ${p.dislikedCategories}
- travelStyle: ${p.travelStyle}
- budgetLevel: ${p.budgetLevel}
- companionType: ${p.companionType}
- language: ${p.language}
- history: ${p.history}

Задача:
1) Определи какой город
2) Предложи до ${input.maxPlaces} туристических мест рядом с точкой.
3) Используй preferredCategories приоритетно.
4) Избегай dislikedCategories.
5) Для каждой точки верни их настоящие координаты, чтобы их можно было сразу поставить на карту.

Формат ответа (строго JSON, без markdown и любых пояснений):
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
{ "places": [] }
""".trimIndent()
    }
}

