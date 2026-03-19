package com.example.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.map.data.RecommendationRepository
import com.example.map.domain.model.SelectedLocation
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

        viewModelScope.launch {
            val profile = _uiState.value.profile
            runCatching { repository.getRecommendations(location, profile) }
                .onSuccess { recommendations ->
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
