package com.example.map

import android.Manifest
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.FrameLayout
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.map.data.Data
import com.example.map.data.FakePlacesDataSource
import com.example.map.domain.model.SelectedLocation
import com.example.map.llm.AssetModelInstaller
import com.example.map.llm.DefaultSystemPrompt
import com.example.map.llm.LlamaChatTemplate
import com.example.map.llm.LocalLlamaClient
import com.example.map.data.LocalLlmRecommendationRepository
import com.example.map.data.network.NetworkModule.createRecommendationApi
import com.example.map.data.network.SearchingMapPlace
import com.example.map.domain.model.SearchPoint
import com.example.map.domain.model.UserProfile
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
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), InputListener {
    private val MAPKIT_API_KEY = "a6f0b6af-0e69-4781-8782-5fa1061829f7"

    private lateinit var client: LocalLlamaClient
    private lateinit var mapHost: FrameLayout
    private lateinit var divContainer: FrameLayout
    private lateinit var loadingBar: View
    private var mapView: MapView? = null
    private lateinit var divView: Div2View
    private var selectedPlacemark: PlacemarkMapObject? = null
    private val llamaClientDeferred = CompletableDeferred<LocalLlamaClient>()
    private var recommendationMarkers: List<PlacemarkMapObject> = emptyList()
    private var lastRecommendationsSignature: String = ""
    private lateinit var profile: UserProfile
    private var resultSerch: List<SearchPoint> = listOf()
    private var api = createRecommendationApi()
    private var mapKitStarted: Boolean = false
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>

    private val viewModel: RecommendationViewModel by viewModels {
        RecommendationViewModel.Factory(
            LocalLlmRecommendationRepository(
                llamaClientDeferred = llamaClientDeferred,
                fallback = FakePlacesDataSource(this@MainActivity),
            ),
        )
    }

    private fun refreshRecommendationsFromDatabase() {
        lifecycleScope.launch {
            val dataSource = FakePlacesDataSource(this@MainActivity)
            dataSource.refreshPlaces()
            // Если есть выбранная локация, обновляем рекомендации
            viewModel.uiState.value.selectedLocation?.let { location ->
                viewModel.onLocationSelected(location)
            }
        }
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
        loadingBar = findViewById(R.id.loadingBar)

        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
                if (isMapKitConfigured) startMapKitSafely()
            }
        requestLocationPermissionIfNeeded()

        divView = createDivView()
        divContainer.addView(divView)

        if (isMapKitConfigured) {
            setupMap()
        }
        viewModel.onMapReady(isMapKitConfigured)

        val profileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val json = result.data?.getStringExtra(ProfileActivity.EXTRA_PROFILE_JSON).orEmpty()
            if (json.isBlank()) return@registerForActivityResult

            profile = Gson().fromJson(json, UserProfile::class.java)
            viewModel.setProfile(profile)
        }

        findViewById<MaterialButton>(R.id.profileBtn).setOnClickListener {
            profileLauncher.launch(Intent(this, ProfileActivity::class.java))
        }

        // Установка модели из assets во внутреннее хранилище и загрузка llama.cpp.
        // Положи GGUF файл в: app/src/main/assets/models/model.gguf
        lifecycleScope.launch {
            runCatching {
                client = LocalLlamaClient(
                    modelPath = getModelPath("models/model.gguf", "model.gguf"),
                    template = LlamaChatTemplate.CHATML,
                    systemPrompt = DefaultSystemPrompt.TOUR_GUIDE_RU,
                ).also { it.load() }
                client.load()
                client.startNewSession()
                client
            }.onSuccess { llamaClientDeferred.complete(it) }
                .onFailure { e -> llamaClientDeferred.completeExceptionally(e) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    divView.setData(DivStateRenderer.build(uiState), DivDataTag("main_state"))
                    loadingBar.visibility = if (uiState.isLoading) View.VISIBLE else View.GONE
                    if (!uiState.isMapReady || uiState.isLoading || uiState.errorMessage != null) return@collect

                    val signature = uiState.recommendations.joinToString("|") { "${it.id}:${it.latitude},${it.longitude}" }
                    if (signature != lastRecommendationsSignature) {
                        lastRecommendationsSignature = signature
                        renderRecommendationsOnMap(uiState.recommendations)
                    }
                }
            }
        }
    }
    fun getModelPath(assetPath: String, fileName: String): String {
        val outFile = File(filesDir, fileName)

        if (!outFile.exists()) {
            assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output) // важно: не readBytes()
                }
            }
        }

        return outFile.absolutePath
    }

    fun setSearchResult(r :List<SearchPoint>){
        resultSerch = r
    }

    private fun renderRecommendationsOnMap(recommendations: List<com.example.map.domain.model.Recommendation>) {
        val map = mapView?.mapWindow?.map ?: return

        recommendationMarkers.forEach { marker ->
            map.mapObjects.remove(marker)
        }
        recommendationMarkers = recommendations.map { rec ->
            map.mapObjects.addPlacemark(Point(rec.latitude, rec.longitude))
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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) return
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun startMapKitSafely() {
        if (mapKitStarted) return
        // Важно: карта и маркеры должны работать даже без гео-пермишенов.
        mapView?.onStart()
        runCatching { MapKitFactory.getInstance().onStart() }
            .onFailure { e ->
                // На некоторых прошивках/SDK попытка старта может триггерить LocationSubscription.
                // Не падаем — геолокационные фичи просто не будут доступны.
                Log.w("MapKit", "MapKit onStart failed (location permission?)", e)
            }
        mapKitStarted = true
    }

    override fun onStart() {
        super.onStart()
        if (MAPKIT_API_KEY.isNotBlank()) startMapKitSafely()
    }

    override fun onStop() {
        if (MAPKIT_API_KEY.isNotBlank() && mapKitStarted) {
            mapView?.onStop()
            runCatching { MapKitFactory.getInstance().onStop() }
            mapKitStarted = false
        }
        if (llamaClientDeferred.isCompleted) {
            runCatching { llamaClientDeferred.getCompleted().close() }
        }
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
        lifecycleScope.launch{
            val sea = SearchingMapPlace()
            Log.d("adfsadsads", resultSerch.toString())
        }
    }

    override fun onMapLongTap(map: Map, point: Point) = Unit
}
