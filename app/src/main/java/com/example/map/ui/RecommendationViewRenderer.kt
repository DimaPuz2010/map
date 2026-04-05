import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.map.domain.model.Recommendation
import com.example.map.ui.MainUiState

class RecommendationViewRenderer(
    private val context: Context,
    private val onMoveToPoint: (Double, Double) -> Unit,
    private val onToggle: () -> Unit,
) {

    fun render(container: FrameLayout, state: MainUiState) {
        container.removeAllViews()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBg("#F5F1E8", 24f)
//            elevation = dp(6).toFloat()
        }

        root.addView(header(state))

        when {
            !state.isMapReady -> root.addView(message("Добавь MapKit API ключ"))
            state.selectedLocation == null -> root.addView(message("Нажми на карту ✨"))
            state.isLoading -> root.addView(message("Ищем места поблизости..."))
            state.errorMessage != null -> root.addView(message(state.errorMessage))
            else -> {
                root.addView(toggle(state))

                if (!state.isRecommendationsCollapsed) {
                    state.recommendations.forEach {
                        root.addView(recommendationCard(it))
                    }
                }
            }
        }

        container.addView(root)
    }


    private fun header(state: MainUiState): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(context).apply {
                text = state.profile.displayName ?: "Guest"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1611"))
            })

            addView(TextView(context).apply {
                text = state.profile.travelStyle ?: ""
                textSize = 13f
                setTextColor(Color.parseColor("#6A5E52"))
            })
        }
    }


    private fun recommendationCard(rec: Recommendation): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))

            background = roundedBg("#FFFFFF", 18f)
//            elevation = dp(4).toFloat()

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(10)
            layoutParams = lp

            addView(TextView(context).apply {
                text = rec.name
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1611"))
            })

            addView(TextView(context).apply {
                text = rec.category
                textSize = 13f
                setTextColor(Color.parseColor("#6A5E52"))
            })

            addView(TextView(context).apply {
                text = "⭐ %.1f  •  ${rec.distanceMeters} м".format(rec.rating)
                textSize = 12f
                setTextColor(Color.parseColor("#3C332C"))
            })

            addView(TextView(context).apply {
                text = rec.reason
                textSize = 12f
                setTextColor(Color.parseColor("#4A4038"))
                setPadding(0, dp(6), 0, 0)
            })

            setOnClickListener {
                onMoveToPoint(rec.latitude, rec.longitude)
            }
        }
    }


    private fun toggle(state: MainUiState): View {
        return TextView(context).apply {
            text = if (state.isRecommendationsCollapsed)
                "Показать места"
            else
                "Скрыть список"

            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#3C332C"))

            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBg("#E8E0D6", 50f)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(10)
            layoutParams = lp

            setOnClickListener { onToggle() }
        }
    }


    private fun message(text: String?): View {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#4A4038"))
            setPadding(0, dp(12), 0, 0)
        }
    }


    private fun roundedBg(color: String, radius: Float): Drawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius.toInt()).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}