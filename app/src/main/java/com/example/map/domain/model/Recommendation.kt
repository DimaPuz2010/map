package com.example.map.domain.model

data class Recommendation(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val distanceMeters: Int,
    val rating: Double,
    val imageUrl: String,
    val reason: String,
    val source: String,
)
