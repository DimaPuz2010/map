package com.example.map.data.network

import com.example.map.data.network.model.Auth
import com.example.map.data.network.model.AuthResspons
import com.example.map.data.network.model.RecommendationRequestDto
import com.example.map.data.network.model.RecommendationResponseDto
import com.example.map.domain.model.Recommendation
import com.example.map.domain.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface Api {
    @POST("rest/v1//recommendations/nearby")
    suspend fun getRecommendations(
        @Body request: RecommendationRequestDto,
        @Header("Authorization") auth: String = "",
        @Header("apikey") apiKey: String = "sb_publishable_8F_XgYfzMI0Saj6xY7kcAQ_dK4P3vuz"
    ): RecommendationResponseDto

    @POST("auth/v1/signup")
    suspend fun singUp(
        @Header("apikey") apiKey: String = "sb_publishable_8F_XgYfzMI0Saj6xY7kcAQ_dK4P3vuz",
        @Body auth: Auth,
    ): AuthResspons

    @POST("auth/v1/token?grant_type=password")
    suspend fun logIn(
        @Header("apikey") apiKey: String = "sb_publishable_8F_XgYfzMI0Saj6xY7kcAQ_dK4P3vuz",
        @Body auth: Auth,
    ): AuthResspons

    @GET("rest/v1/profils?select=*")
    suspend fun getUser(
        @Header("apikey") apiKey: String = "sb_publishable_8F_XgYfzMI0Saj6xY7kcAQ_dK4P3vuz",
        @Header("Authorization") auth: String = "",
        @Query("id")  id: String = "eq.1"
    ): List<UserProfile>

    @POST("rest/v1/recomindation")
    suspend fun createRecommendation(
        @Header("apikey") apiKey: String = "sb_publishable_8F_XgYfzMI0Saj6xY7kcAQ_dK4P3vuz",
        @Header("Authorization") auth: String = "",
        @Body recommendation: Recommendation
    )

    @DELETE("rest/v1/recommendation")
    suspend fun deleteRecommendation(
        @Header("apikey") apiKey: String = "sb_publishable_8F_XgYfzMI0Saj6xY7kcAQ_dK4P3vuz",
        @Header("Authorization") auth: String = "",
        @Query("id")  id: String = "eq.1"
    )
}
