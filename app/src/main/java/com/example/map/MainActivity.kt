package com.example.map

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.example.map.data.FakePlacesDataSource
import com.example.map.data.RemoteFirstRecommendationRepository
import com.example.map.data.network.NetworkModule
import com.example.map.domain.model.SelectedLocation
import com.example.map.llm.AssetModelInstaller
import com.example.map.llm.DefaultSystemPrompt
import com.example.map.llm.LlamaChatTemplate
import com.example.map.llm.LocalLlamaClient
import com.example.map.ui.DivStateRenderer
import com.example.map.ui.RecommendationViewModel
import com.yandex.div.DivDataTag
import com.yandex.div.coil.CoilDivImageLoader
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import com.yandex.div.core.view2.Div2View
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), InputListener {
    private val MAPKIT_API_KEY = "a6f0b6af-0e69-4781-8782-5fa1061829f7"

    private lateinit var mapHost: FrameLayout
    private lateinit var divContainer: FrameLayout
    private var mapView: MapView? = null
    private lateinit var divView: Div2View
    private var selectedPlacemark: PlacemarkMapObject? = null
    private var llama: LocalLlamaClient? = null

    private val viewModel: RecommendationViewModel by viewModels {
        RecommendationViewModel.Factory(
            RemoteFirstRecommendationRepository(
                api = NetworkModule.createRecommendationApi(),
                fallback = FakePlacesDataSource(),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isMapKitConfigured = MAPKIT_API_KEY.isNotBlank()
        if (isMapKitConfigured) {
            MapKitFactory.setApiKey(MAPKIT_API_KEY)
            MapKitFactory.initialize(this)
        }

        setContentView(R.layout.activity_main)
        mapHost = findViewById(R.id.mapHost)
        divContainer = findViewById(R.id.divContainer)

        divView = createDivView()
        divContainer.addView(divView)

        if (isMapKitConfigured) {
            setupMap()
        }
        viewModel.onMapReady(isMapKitConfigured)

        // Установка модели из assets во внутреннее хранилище и загрузка llama.cpp.
        // Положи GGUF файл в: app/src/main/assets/models/<имя>.gguf
        lifecycleScope.launch {
            val installed = AssetModelInstaller.ensureInstalled(
                context = this@MainActivity,
                assetPath = "models/model.gguf",
                targetFileName = "model.gguf",
            )

            llama = LocalLlamaClient(
                modelPath = installed.file.absolutePath,
                template = LlamaChatTemplate.CHATML,
                systemPrompt = DefaultSystemPrompt.TOUR_GUIDE_RU,
            ).also { it.load() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    divView.setData(DivStateRenderer.build(uiState), DivDataTag("main_state"))
                }
            }
        }
    }

    private fun createDivView(): Div2View {
        val configuration = DivConfiguration.Builder(CoilDivImageLoader(this))
            .actionHandler(NotificationDivActionHandler())
            .build()

        return Div2View(
            Div2Context(
                baseContext = this,
                configuration = configuration,
                lifecycleOwner = this,
            ),
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun setupMap() {
        val localMapView = MapView(this)
        mapHost.addView(
            localMapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        mapView = localMapView

        val startPoint = Point(54.9885, 73.3242)
        localMapView.mapWindow.map.move(
            CameraPosition(startPoint, 12.5f, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0.6f),
            null,
        )
        localMapView.mapWindow.map.addInputListener(this)
    }

    override fun onStart() {
        super.onStart()
        if (MAPKIT_API_KEY.isNotBlank()) {
            MapKitFactory.getInstance().onStart()
            mapView?.onStart()
        }
    }

    override fun onStop() {
        if (MAPKIT_API_KEY.isNotBlank()) {
            mapView?.onStop()
            MapKitFactory.getInstance().onStop()
        }
        llama?.close()
        llama = null
        super.onStop()
    }

    override fun onMapTap(map: Map, point: Point) {
        selectedPlacemark?.let { map.mapObjects.remove(it) }
        selectedPlacemark = map.mapObjects.addPlacemark(point)
        viewModel.onLocationSelected(
            SelectedLocation(
                latitude = point.latitude,
                longitude = point.longitude,
            ),
        )
    }

    override fun onMapLongTap(map: Map, point: Point) = Unit
}
