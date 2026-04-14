package com.example.map

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.map.ProfileActivity.Companion.EXTRA_PROFILE_JSON
import com.example.map.data.Data
import com.example.map.data.Data.categories
import com.example.map.data.network.NetworkModule
import com.example.map.data.network.model.Auth
import com.example.map.domain.model.UserProfile
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.launch

class CreateProfileActivity : AppCompatActivity() {
    private lateinit var etDisplayName: TextInputEditText
    private lateinit var email: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var etTravelStyle: TextInputEditText
    private lateinit var btnCancel: Button
    private lateinit var btnSave: Button
    private lateinit var statusTv: TextView
    private lateinit var preference: SharedPreferences
    private val categoryStates = mutableMapOf<String, State>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        preference = getSharedPreferences("Auth", MODE_PRIVATE)

        initViews()
        setupListeners()

        val chipGroup = findViewById<ChipGroup>(R.id.preferChip)
        categories.forEach { name ->
            categoryStates[name] = State.NONE

            val chip = createChip(this, name)

            chip.setOnClickListener {
                val newState = when (categoryStates[name]) {
                    State.NONE -> State.LIKE
                    State.LIKE -> State.DISLIKE
                    State.DISLIKE -> State.NONE
                    else -> State.NONE
                }

                categoryStates[name] = newState
                updateChipStyle(chip, newState)
            }

            chipGroup.addView(chip)
        }

    }
    private fun createChip(context: Context, text: String): Chip {
        return Chip(context).apply {
            this.text = text
            isClickable = true
            isCheckable = false
            setChipBackgroundColorResource(R.color.gray)
        }
    }

    private fun updateChipStyle(chip: Chip, state: State) {
        val cleanText = chip.text.toString()
            .replace("❤️ ", "")
            .replace("❌ ", "")

        when (state) {
            State.LIKE -> {
                chip.setChipBackgroundColorResource(R.color.white)
                chip.setTextColor(Color.GREEN)
                chip.text = "❤️ $cleanText"
            }
            State.DISLIKE -> {
                chip.setChipBackgroundColorResource(R.color.black)
                chip.setTextColor(Color.RED)
                chip.text = "❌ $cleanText"
            }
            State.NONE -> {
                chip.setChipBackgroundColorResource(R.color.gray)
                chip.setTextColor(Color.BLACK)
                chip.text = cleanText
            }
        }
    }
    private fun initViews() {
        etDisplayName = findViewById(R.id.etDisplayName)
        email = findViewById(R.id.etEmail)
        password = findViewById(R.id.etPassword)
        etTravelStyle = findViewById(R.id.etTravelStyle)
        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)
        statusTv = findViewById(R.id.profileStatusTv)
    }

    private fun setupListeners() {


        btnCancel.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            finish()
        }
        val api = NetworkModule.createRecommendationApi()
            ?: error("Network API is not configured (RECOMMENDATION_BASE_URL missing).")

        btnSave.setOnClickListener {

            if (validateForm()) {
                lifecycleScope.launch {
                    runCatching {
                        btnSave.isActivated = false

                        val loginResp = api.singUp(
                            auth = Auth(email = email.text.toString(), password = password.text.toString()),
                        )
                        val authHeader = "Bearer ${loginResp.access_token}"


                        Data.userAuth = authHeader



                        val profileResp = api.getUser(
                            auth = authHeader,
                            id = ("eq."+loginResp.user?.id)
                        )

                        val liked = categoryStates.filterValues { it == State.LIKE }.keys
                        val disliked = categoryStates.filterValues { it == State.DISLIKE }.keys

                        val likedStr = liked.joinToString(",")
                        val dislikedStr = disliked.joinToString(",")
                        api.changeProfile(
                            auth = authHeader,
                            id = ("eq."+loginResp.user?.id.toString()),
                            userProfile = UserProfile(
                                id = profileResp[0].id,
                                displayName = etDisplayName.text.toString(),
                                preferredCategories = likedStr,
                                dislikedCategories = dislikedStr,
                                travelStyle = etTravelStyle.text.toString(),
                                budgetLevel = profileResp[0].budgetLevel,
                                companionType = profileResp[0].companionType,
                                language = profileResp[0].language,
                                history = profileResp[0].history
                            )
                        )
                        profileResp[0]
                    }.onSuccess { profile ->

                        if (profile == null) {
                            throw IllegalStateException("Profile not returned by getUser().")
                        }
                        setResultOk(profile)
                        btnSave.isActivated = true
                    }.onFailure { e ->
                        statusTv.text = "Ошибка"
                        Log.d("Net",e.message.toString())
                        //Toast.makeText(this@CreateProfileActivity, e.message ?: "Auth failed", Toast.LENGTH_LONG).show()
                        btnSave.isActivated = true
                    }
                }
            }
        }

    }
    private fun setResultOk(profile: UserProfile) {
        val json = Gson().toJson(profile)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_PROFILE_JSON, json)
        }
        preference.edit {
            putString("user_auth", Data.userAuth)
            putString("user_id", "eq." + profile.id)
        }
        Data.userId = "eq."+profile.id
        Data.profile = profile
        startActivity(intent)
        setResult(RESULT_OK, intent)
        finish()
    }
    private fun validateForm(): Boolean {
        val displayName = etDisplayName.text.toString().trim()

        if (displayName.isEmpty()) {
            etDisplayName.error = "Введите имя пользователя"
            etDisplayName.requestFocus()
            return false
        }

        return true
    }
}

