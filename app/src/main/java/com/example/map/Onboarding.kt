package com.example.map

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton

class Onboarding : AppCompatActivity() {
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var button: MaterialButton
    private lateinit var icon: ImageView
    private lateinit var dots: List<View>
    private lateinit var preference: SharedPreferences
    private var step = 0

    private val pages = listOf(
        "Открой новые места" to "Находи интересные локации рядом с тобой",
        "Просто нажми на карту" to "Выбери точку и получи рекомендации",
        "Настрой профиль" to "Мы подберем места под твой стиль"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        preference = getPreferences(MODE_PRIVATE)
        val isNew = preference.getBoolean("isUserNew", true)
        if (!isNew){
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            finish()
        }

        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        button = findViewById(R.id.onbContinue)
        icon = findViewById(R.id.illustrationIcon)
        dots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3)
        )
        render()

        button.setOnClickListener {
            step++
            if (step >= pages.size) {
                openProfile()
            } else {
                render()
            }
        }
    }

    private fun render() {
        val page = pages[step]
        title.text = page.first
        subtitle.text = page.second

        button.text = if (step == pages.lastIndex) "Начать" else "Далее"

        val iconRes = when (step) {
            0 -> android.R.drawable.ic_menu_compass
            1 -> android.R.drawable.ic_menu_mapmode
            else -> android.R.drawable.ic_menu_myplaces
        }
        updateDots()
        icon.setImageResource(iconRes)
    }

    private fun openProfile() {
        preference.edit(){
            putBoolean("isUserNew", false)
        }
        startActivity(Intent(this, ProfileActivity::class.java))
        finish()
    }
    private fun updateDots() {
        dots.forEachIndexed { index, view ->
            val isActive = index == step

            view.setBackgroundResource(
                if (isActive) R.drawable.dot_active else R.drawable.dot_inactive
            )

            view.animate()
                .scaleX(if (isActive) 1.3f else 1f)
                .scaleY(if (isActive) 1.3f else 1f)
                .setDuration(150)
                .start()
        }
    }
}