package com.example.map.data.network.model

import com.example.map.domain.model.UserProfile

data class AuthResspons(
    val access_token: String? = null,
    val profile: UserProfile? = null,
    val user: UserProfile? = null
)
