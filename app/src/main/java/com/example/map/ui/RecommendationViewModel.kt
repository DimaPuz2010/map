package com.example.map.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.map.data.RecommendationRepository
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecommendationViewModel(
    private val repository: RecommendationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onMapReady(isReady: Boolean) {
        _uiState.update { it.copy(isMapReady = isReady) }
    }

    fun onLocationSelected(location: SelectedLocation) {
        _uiState.update {
            it.copy(
                selectedLocation = location,
                isLoading = true,
                errorMessage = null,
            )
        }
        requestRecommendations(location)
    }

    fun setProfile(profile: UserProfile) {
        _uiState.update { it.copy(profile = profile) }

        val selected = _uiState.value.selectedLocation ?: return
        onLocationSelected(selected)
    }

    fun toggleRecommendationsCollapsed() {
        _uiState.update { it.copy(isRecommendationsCollapsed = !it.isRecommendationsCollapsed) }
    }

    private fun requestRecommendations(location: SelectedLocation) {
        viewModelScope.launch {
            val profile = _uiState.value.profile
            Log.i(
                "Recommendations",
                "Requesting recommendations for lat=${location.latitude}, lon=${location.longitude}",
            )
            runCatching { repository.getRecommendations(location, profile) }
                .onSuccess { recommendations ->
                    Log.i(
                        "Recommendations",
                        "Received ${recommendations.size} recommendations",
                    )
                    _uiState.update {
                        it.copy(
                            recommendations = recommendations,
                            isLoading = false,
                            errorMessage = if (recommendations.isEmpty()) {
                                "No suitable places were found nearby. Pick another point on the map."
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { error ->
                    Log.e("Recommendations", "Failed to load recommendations", error)
                    _uiState.update {
                        it.copy(
                            recommendations = emptyList(),
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load recommendations.",
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: RecommendationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecommendationViewModel(repository) as T
        }
    }
}
