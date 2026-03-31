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
import com.example.map.data.network.NetworkModule
import com.example.map.data.network.model.Auth
import com.example.map.domain.model.UserProfile
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.collections.forEach
enum class State {
    LIKE, DISLIKE, NONE
}
class CreateProfileActivity : AppCompatActivity() {
    private lateinit var etDisplayName: TextInputEditText
    private lateinit var email: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var etPreferredCategories: TextInputEditText
    private lateinit var etDislikedCategories: TextInputEditText
    private lateinit var etTravelStyle: TextInputEditText
    private lateinit var btnCancel: Button
    private lateinit var btnSave: Button
    private lateinit var statusTv: TextView
    private lateinit var preference: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_profile)

        preference = getPreferences(MODE_PRIVATE)

        initViews()
        setupListeners()

        val categories = listOf("Музыка", "Спорт", "Фильмы", "Игры")


        val chipGroup = findViewById<ChipGroup>(R.id.preferChip)
        val categoryStates = mutableMapOf<String, State>()
        categories.forEach { name ->
            categoryStates[name] = State.NONE

            val chip = createChip(this, name)

            chip.setOnClickListener {
                val current = categoryStates[name]

                val newState = when (current) {
                    State.NONE -> State.LIKE
                    State.LIKE -> State.DISLIKE
                    State.DISLIKE -> State.NONE
                    null -> State.NONE
                }

                categoryStates[name] = newState
                updateChipStyle(chip, newState)
            }

            chipGroup.addView(chip)
        }
    }
    fun createChip(context: Context, text: String): Chip {
        return Chip(context).apply {
            this.text = text
            isClickable = true
            isCheckable = false
            setChipBackgroundColorResource(R.color.gray)
        }
    }
    fun updateChipStyle(chip: Chip, state: State) {
        when (state) {
            State.LIKE -> {
                chip.setChipBackgroundColorResource(R.color.white)
                chip.setTextColor(Color.GREEN)
                chip.text = "❤️ ${chip.text}"
            }
            State.DISLIKE -> {
                chip.setChipBackgroundColorResource(R.color.black)
                chip.setTextColor(Color.RED)
                chip.text = "❌ ${chip.text}"
            }
            State.NONE -> {
                chip.setChipBackgroundColorResource(R.color.gray)
                chip.setTextColor(Color.BLACK)
                chip.text = chip.text.toString().replace("❤️ ", "").replace("❌ ", "")
            }
        }
    }
    private fun initViews() {
        etDisplayName = findViewById(R.id.etDisplayName)
        email = findViewById(R.id.etEmail)
        password = findViewById(R.id.etPassword)
//        etPreferredCategories = findViewById(R.id.etPreferredCategories)
        etDislikedCategories = findViewById(R.id.etDislikedCategories)
        etTravelStyle = findViewById(R.id.etTravelStyle)
        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)
        statusTv = findViewById(R.id.createStatusTv)
    }

    private fun setupListeners() {


        btnCancel.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            finish()
        }
        val api = NetworkModule.createRecommendationApi()
            ?: error("Network API is not configured (RECOMMENDATION_BASE_URL missing).")

        // Кнопка сохранения
        btnSave.setOnClickListener {

            if (validateForm()) {
                lifecycleScope.launch {
                    runCatching {
                        btnSave.isActivated = false

                        var loginResp = api.singUp(
                            auth = Auth(email = email.text.toString(), password = password.text.toString()),
                        )
                        var authHeader = "${loginResp.access_token}"


                        Data.userAuth = authHeader



                        val profileResp = api.getUser(
                            auth = authHeader,
                            id = ("eq."+loginResp.user?.id.toString())
                        )
                        val changedProfile = api.changeProfile(
                            auth = authHeader,
                            id = ("eq."+loginResp.user?.id.toString()),
                            userProfile = UserProfile(
                                id = profileResp[0].id,
                                displayName = etDisplayName.text.toString(),
                                preferredCategories = /*etPreferredCategories.text.toString()*/"",
                                dislikedCategories = etDislikedCategories.text.toString(),
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
                        statusTv.text = "Ошибка: ${e.message}"
                        Log.d("Net",e.message.toString())
                        Toast.makeText(this@CreateProfileActivity, e.message ?: "Auth failed", Toast.LENGTH_LONG).show()
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
        setResult(Activity.RESULT_OK, intent)
        startActivity(intent)
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

