package com.example.map.data.network

import com.example.map.data.network.openrouter.OpenRouterApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private const val RECOMMENDATION_BASE_URL = "https://vrfkcrbcaunvrqfpfdzl.supabase.co/"
    private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/"

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

    fun createOpenRouterApi(apiKey: String): OpenRouterApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return Retrofit.Builder()
            .baseUrl(OPENROUTER_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Authorization", apiKey)
                                .header("HTTP-Referer", "https://github.com/example/map")
                                .header("X-Title", "Map Recommendations")
                                .build(),
                        )
                    }
                    .build(),
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenRouterApi::class.java)
    }
}
