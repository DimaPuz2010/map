package com.example.map

import com.example.map.data.FakePlacesDataSource
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class FakePlacesDataSourceTest {

    @Ignore("Requires Android runtime; run as instrumentation test if needed.")
    @Test
    fun preferredCategoryGetsPrioritized() {
        val profile = UserProfile(
            id = "u1",
            displayName = "Test",
            preferredCategories = "Museum",
            dislikedCategories = "",
            travelStyle = "Cultural",
            budgetLevel = "Medium",
            companionType = "Solo",
            language = "ru",
            history = "",
        )

        val recommendations = FakePlacesDataSource(MainActivity()).recommend(
            location = SelectedLocation(54.9910, 73.3700),
            profile = profile,
        )

        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.first().category == "Museum")
    }
}
