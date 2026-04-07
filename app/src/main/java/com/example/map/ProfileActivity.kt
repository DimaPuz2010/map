package com.example.map

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.map.data.Data
import com.example.map.data.network.NetworkModule
import com.example.map.data.network.model.Auth
import com.example.map.domain.model.UserProfile
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var preference: SharedPreferences

    val api = NetworkModule.createRecommendationApi()
        ?: error("Network API is not configured (RECOMMENDATION_BASE_URL missing).")
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
        val createTx = findViewById<TextView>(R.id.txCreateProfile)

        preference = getPreferences(MODE_PRIVATE)
        if (preference.getString("user_auth", null) != null && preference.getString("user_id", null) != null) {
            loadBtn.isActivated = false
            createTx.isActivated = false
            statusTv.text = "Попытка входа в аккаунт, ожидайте"

            lifecycleScope.launch {
                runCatching {
                    Log.d("prof", preference.getString("user_auth", null).toString())
                    val profile = api.getUser(
                        id = preference.getString("user_id", null).toString(),
                        auth = preference.getString("user_auth", null).toString()
                    )
                    profile[0]
                }.onSuccess { profile ->
                    if (profile == null) {
                        throw IllegalStateException("Profile not returned by getUser().")
                    }
                    Data.userAuth = "${preference.getString("user_auth", null).toString()}"
                    setResultOk(profile)
                }.onFailure { e ->
                    statusTv.text = "Ошибка: ${e.message}"
                    Log.d("AuthNet",e.message.toString())
                    Toast.makeText(this@ProfileActivity, e.message ?: "Auth failed", Toast.LENGTH_LONG).show()
                    loadBtn.isActivated = true
                    createTx.isActivated = true
                }
            }

        }else{

            createTx.setText("Создать аккаунт")
            createTx.setOnClickListener {
                val intent = Intent(this, CreateProfileActivity::class.java)
                startActivity(intent)
                finish()
            }

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

                        val loginResp = api.logIn(
                            auth = Auth(email = email, password = password),
                        )
                        val authHeader = "Bearer ${loginResp.access_token}"
                        Data.userAuth = authHeader
                        val profileResp = api.getUser(
                            auth = authHeader,
                            id = ("eq." + loginResp.user?.id.toString())
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
    }

    private fun setResultOk(profile: UserProfile) {
        val json = Gson().toJson(profile)
        val intent =  Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_PROFILE_JSON, json)
        }
        Log.d("asd", Data.userAuth)
        preference.edit {
            putString("user_auth", Data.userAuth)
            putString("user_id", "eq."+profile.id)
        }
        Data.userId = "eq."+profile.id

        startActivity(intent)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}

