package com.example.map.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private val RECOMMENDATION_BASE_URL = "https://vrfkcrbcaunvrqfpfdzl.supabase.co/"
    fun createRecommendationApi(): Api? {
        val baseUrl = RECOMMENDATION_BASE_URL.trim()
        if (baseUrl.isBlank()) return null

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build(),
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }
}
