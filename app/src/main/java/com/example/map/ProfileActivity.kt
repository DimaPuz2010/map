package com.example.map

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.map.data.network.NetworkModule
import com.example.map.data.network.model.Auth
import com.example.map.domain.model.UserProfile
import com.google.gson.Gson
import kotlinx.coroutines.launch
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import android.widget.TextView

class ProfileActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PROFILE_JSON = "profile_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val emailEt = findViewById<EditText>(R.id.emailEt)
        val passwordEt = findViewById<EditText>(R.id.passwordEt)
        val loadBtn = findViewById<MaterialButton>(R.id.loadProfileBtn)
        val statusTv = findViewById<TextView>(R.id.profileStatusTv)

        loadBtn.setOnClickListener {
            val email = emailEt.text?.toString()?.trim().orEmpty()
            val password = passwordEt.text?.toString()?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Введите email и пароль", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            statusTv.text = "Загрузка профиля..."

            lifecycleScope.launch {
                runCatching {
                    val api = NetworkModule.createRecommendationApi()
                        ?: error("Network API is not configured (RECOMMENDATION_BASE_URL missing).")

                    val loginResp = api.logIn(
                        auth = Auth(email = email, password = password),
                    )

                    val authHeader = "Bearer ${loginResp.access_token}"
                    val profileResp = api.getUser(
                        auth = authHeader,
                        id = ("eq."+loginResp.user?.id.toString())
                    )

                    profileResp[0]
                }.onSuccess { profile ->
                    if (profile == null) {
                        throw IllegalStateException("Profile not returned by getUser().")
                    }
                    setResultOk(profile)
                }.onFailure { e ->
                    statusTv.text = "Ошибка: ${e.message}"
                    Log.d("Net",e.message.toString())
                    Toast.makeText(this@ProfileActivity, e.message ?: "Auth failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setResultOk(profile: UserProfile) {
        val json = Gson().toJson(profile)
        val intent = Intent().apply {
            putExtra(EXTRA_PROFILE_JSON, json)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}

