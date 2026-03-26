package com.example.map.ui

import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.DivData
import org.json.JSONArray
import org.json.JSONObject

object DivStateRenderer {

    fun build(uiState: MainUiState): DivData {
        val environment = DivParsingEnvironment(ParsingErrorLogger.LOG)
        environment.parseTemplates(JSONObject())
        return DivData(environment, buildCard(uiState))
    }

    private fun buildCard(uiState: MainUiState): JSONObject {
        return JSONObject()
            .put("log_id", "tour_recommendations")
            .put(
                "states",
                JSONArray().put(
                    JSONObject()
                        .put("state_id", 0)
                        .put("div", buildRoot(uiState)),
                ),
            )
    }

    private fun buildRoot(uiState: MainUiState): JSONObject {
        val items = JSONArray().put(headerBlock(uiState))

        when {
            !uiState.isMapReady -> items.put(messageBlock("MapKit key is missing. Add MAPKIT_API_KEY to ~/.gradle/gradle.properties."))
            uiState.selectedLocation == null -> items.put(messageBlock("Tap on the map to pick a point and get personalized tourist recommendations."))
            uiState.isLoading -> items.put(messageBlock("Searching for places near the selected point."))
            uiState.errorMessage != null -> items.put(messageBlock(uiState.errorMessage))
            else -> {
                items.put(toggleBlock(uiState.isRecommendationsCollapsed))
                if (!uiState.isRecommendationsCollapsed) {
                    uiState.recommendations.forEach {
                        items.put(
                            recommendationBlock(
                                it.name,
                                it.category,
                                it.distanceMeters,
                                it.rating,
                                it.reason,
                                it.latitude,
                                it.longitude,
                            ),
                        )
                    }
                } else {
                    items.put(messageBlock("Recommendations list is collapsed."))
                }
            }
        }

        return JSONObject()
            .put("type", "container")
            .put("orientation", "vertical")
            .put("paddings", edgeInsets(16))
            .put(
                "background",
                JSONArray().put(
                    JSONObject()
                        .put("type", "solid")
                        .put("color", "#F5F1E8"),
                ),
            )
            .put("border", JSONObject().put("corner_radius", 24))
            .put("items", items)
    }

    private fun headerBlock(uiState: MainUiState): JSONObject {
        val selectedLabel = uiState.selectedLocation?.let {
            "Point: %.4f, %.4f".format(it.latitude, it.longitude)
        } ?: "Point is not selected yet"
        val text = "${uiState.profile.displayName}, ${uiState.profile.travelStyle}\n$selectedLabel"
        return JSONObject()
            .put("type", "text")
            .put("text", text)
            .put("font_size", 16)
            .put("font_weight", "bold")
            .put("text_color", "#1C1611")
    }

    private fun recommendationBlock(
        name: String,
        category: String,
        distanceMeters: Int,
        rating: Double,
        reason: String,
        latitude: Double,
        longitude: Double,
    ): JSONObject {
        return JSONObject()
            .put("type", "container")
            .put("orientation", "vertical")
            .put("paddings", edgeInsets(12))
            .put("margins", JSONObject().put("top", 10))
            .put(
                "actions",
                JSONArray().put(
                    JSONObject()
                        .put("url", "map://move?lat=$latitude&lon=$longitude"),
                ),
            )
            .put(
                "background",
                JSONArray().put(
                    JSONObject()
                        .put("type", "solid")
                        .put("color", "#FFFDFC"),
                ),
            )
            .put("border", JSONObject().put("corner_radius", 18))
            .put(
                "items",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", name)
                            .put("font_size", 15)
                            .put("font_weight", "medium")
                            .put("text_color", "#1C1611"),
                    )
                    .put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", "$category | ${distanceMeters} m | rating %.1f".format(rating))
                            .put("font_size", 13)
                            .put("margins", JSONObject().put("top", 4))
                            .put("text_color", "#6A5E52"),
                    )
                    .put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", reason)
                            .put("font_size", 12)
                            .put("margins", JSONObject().put("top", 6))
                            .put("text_color", "#3C332C"),
                    ),
            )
    }

    private fun toggleBlock(isCollapsed: Boolean): JSONObject {
        val text = if (isCollapsed) "Show recommendations" else "Hide recommendations"
        return JSONObject()
            .put("type", "container")
            .put("orientation", "horizontal")
            .put("margins", JSONObject().put("top", 10))
            .put("paddings", edgeInsets(10))
            .put(
                "background",
                JSONArray().put(
                    JSONObject()
                        .put("type", "solid")
                        .put("color", "#E8E0D6"),
                ),
            )
            .put("border", JSONObject().put("corner_radius", 14))
            .put(
                "actions",
                JSONArray().put(
                    JSONObject()
                        .put("url", "app://toggle-cards"),
                ),
            )
            .put(
                "items",
                JSONArray().put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", text)
                        .put("font_size", 13)
                        .put("font_weight", "medium")
                        .put("text_color", "#3C332C"),
                ),
            )
    }

    private fun messageBlock(text: String): JSONObject {
        return JSONObject()
            .put("type", "text")
            .put("text", text)
            .put("font_size", 14)
            .put("margins", JSONObject().put("top", 12))
            .put("text_color", "#4A4038")
    }

    private fun edgeInsets(value: Int): JSONObject {
        return JSONObject()
            .put("top", value)
            .put("bottom", value)
            .put("left", value)
            .put("right", value)
    }
}
