package com.example.map

import com.example.map.data.FakePlacesDataSource
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlacesDataSourceTest {

    @Test
    fun preferredCategoryGetsPrioritized() {
        val profile = UserProfile(
            id = "u1",
            displayName = "Test",
            preferredCategories = listOf("Museum"),
            dislikedCategories = emptyList(),
            travelStyle = "Cultural",
            budgetLevel = "Medium",
            companionType = "Solo",
            language = "ru",
            history = emptyList(),
        )

        val recommendations = FakePlacesDataSource().recommend(
            location = SelectedLocation(54.9910, 73.3700),
            profile = profile,
        )

        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.first().category == "Museum")
    }
}
