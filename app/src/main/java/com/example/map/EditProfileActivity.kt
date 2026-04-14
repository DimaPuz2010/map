package com.example.map

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.map.data.Data
import com.example.map.data.Data.categories
import com.example.map.data.network.NetworkModule
import com.example.map.domain.model.UserProfile
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etTravelStyle: TextInputEditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var btnSave: MaterialButton
    private lateinit var progress: View
    private lateinit var btnBack: ImageButton
    private val api = NetworkModule.createRecommendationApi()
        ?: error("API not configured")
    private var currentProfile: UserProfile? = null
    private val categoryStates = mutableMapOf<String, State>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        initViews()
        loadProfile()

        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        btnSave.setOnClickListener {
            if (validate()) {
                saveProfile()
            }
        }
    }

    private fun initViews() {
        etName = findViewById(R.id.etName)
        etTravelStyle = findViewById(R.id.etTravelStyle)
        chipGroup = findViewById(R.id.chipGroup)
        btnSave = findViewById(R.id.btnSave)
        progress = findViewById(R.id.progress)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun loadProfile() {
        val id = Data.userId
        val auth = Data.userAuth

        showLoading(true)

        lifecycleScope.launch {
            runCatching {
                api.getUser(auth = auth, id = id)[0]
            }.onSuccess { profile ->
                currentProfile = profile

                etName.setText(profile.displayName)
                etTravelStyle.setText(profile.travelStyle)

                setupChips(profile)

            }.onFailure {
                toast("Ошибка загрузки профиля")
            }

            showLoading(false)
        }
    }

    private fun setupChips(profile: UserProfile) {
        chipGroup.removeAllViews()
        categoryStates.clear()

        val likedSet = parseCategories(profile.preferredCategories)
        val dislikedSet = parseCategories(profile.dislikedCategories)

        categories.forEach { name ->

            val chip = createChip(this, name)

            val state = when {
                name in likedSet -> State.LIKE
                name in dislikedSet -> State.DISLIKE
                else -> State.NONE
            }

            categoryStates[name] = state
            updateChipStyle(chip, state)

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

    private fun parseCategories(str: String?): Set<String> {
        return str
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    private fun saveProfile() {
        val auth = Data.userAuth
        val id = Data.userId
        val old = currentProfile ?: return

        val liked = categoryStates.filterValues { it == State.LIKE }.keys
        val disliked = categoryStates.filterValues { it == State.DISLIKE }.keys

        val updated = old.copy(
            displayName = etName.text.toString(),
            travelStyle = etTravelStyle.text.toString(),
            preferredCategories = liked.joinToString(","),
            dislikedCategories = disliked.joinToString(","),
        )

        showLoading(true)
        val intent = Intent(this, MainActivity::class.java)
        lifecycleScope.launch {
            runCatching {
                api.changeProfile(
                    auth = auth,
                    id = id,
                    userProfile = updated
                )
                Data.profile = updated
            }.onSuccess {
                toast("Сохранено ✅")
                startActivity(intent)
                finish()
            }.onFailure {
                toast("Ошибка сохранения")
            }

            showLoading(false)
        }
    }

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
    }

    private fun validate(): Boolean {
        if (etName.text.isNullOrBlank()) {
            etName.error = "Введите имя"
            return false
        }
        return true
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
}