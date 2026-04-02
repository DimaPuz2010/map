package com.example.map

import RecommendationViewRenderer
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.map.data.FakePlacesDataSource
import com.example.map.data.OpenRouterRecommendationRepository
import com.example.map.data.network.NetworkModule
import com.example.map.data.network.SearchingMapPlace
import com.example.map.domain.model.SearchPoint
import com.example.map.domain.model.SelectedLocation
import com.example.map.domain.model.UserProfile
import com.example.map.ui.RecommendationViewModel
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), InputListener {
    private val MAPKIT_API_KEY = "a6f0b6af-0e69-4781-8782-5fa1061829f7"
    private lateinit var mapHost: FrameLayout
    private lateinit var divContainer: FrameLayout
    private lateinit var loadingBar: View
    private var mapView: MapView? = null
    private var selectedPlacemark: PlacemarkMapObject? = null
    private var recommendationMarkers: List<PlacemarkMapObject> = emptyList()
    private var lastRecommendationsSignature: String = ""
    private lateinit var profile: UserProfile
    private var resultSerch: List<SearchPoint> = listOf()
    private var mapKitStarted: Boolean = false
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var recommendationPinProvider: ImageProvider? = null
    private var selectedPinProvider: ImageProvider? = null
    private lateinit var renderer: RecommendationViewRenderer
    private val recommendationTapListener = object : MapObjectTapListener {
        override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
            val recommendation = mapObject.userData as? com.example.map.domain.model.Recommendation
            val message = if (recommendation != null) {
                buildString {
                    append(recommendation.name)
                    if (recommendation.category.isNotBlank()) {
                        append(" • ")
                        append(recommendation.category)
                    }
                    if (recommendation.rating > 0.0) {
                        append(" • ")
                        append(String.format(Locale.getDefault(), "%.1f", recommendation.rating))
                    }
                    if (recommendation.address.isNotBlank()) {
                        appendLine()
                        append(recommendation.address)
                    }
                    if (recommendation.distanceMeters > 0) {
                        appendLine()
                        append("≈ ")
                        append(recommendation.distanceMeters)
                        append(" м")
                    }
                    if (recommendation.reason.isNotBlank()) {
                        appendLine()
                        append(recommendation.reason)
                    }
                }
            } else {
                "Метка: ${formatCoords(point.latitude, point.longitude)}"
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            return true
        }
    }
    private val selectedLocationTapListener = object : MapObjectTapListener {
        override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
            val location = mapObject.userData as? SelectedLocation
            val coords = if (location != null) {
                formatCoords(location.latitude, location.longitude)
            } else {
                formatCoords(point.latitude, point.longitude)
            }
            Toast.makeText(applicationContext, "Выбранная точка: $coords", Toast.LENGTH_SHORT).show()
            return true
        }
    }

    private val viewModel: RecommendationViewModel by viewModels {
        RecommendationViewModel.Factory(
            OpenRouterRecommendationRepository(
                api = NetworkModule.createOpenRouterApi(/*BuildConfig.OPENROUTER_API_KEY*/ "Bearer sk-or-v1-60b80336bf4c5b7b23ef7"+"37de5b5da7167e14c52a3940462e2cf9a7bd622166f"),
                fallback = FakePlacesDataSource(this@MainActivity),
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
        loadingBar = findViewById(R.id.loadingBar)



        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
                if (isMapKitConfigured) startMapKitSafely()
            }
        requestLocationPermissionIfNeeded()


        if (isMapKitConfigured) {
            setupMap()
        }
        viewModel.onMapReady(isMapKitConfigured)
        renderer = RecommendationViewRenderer(
            context = this,
            onMoveToPoint = { lat, lon -> moveToPoint(lat, lon) },
            onToggle = { viewModel.toggleRecommendationsCollapsed() }
        )
        val profileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val json = result.data?.getStringExtra(ProfileActivity.EXTRA_PROFILE_JSON).orEmpty()
            if (json.isBlank()) return@registerForActivityResult

            profile = Gson().fromJson(json, UserProfile::class.java)
            viewModel.setProfile(profile)
        }

        findViewById<MaterialButton>(R.id.profileBtn).setOnClickListener {
            profileLauncher.launch(Intent(this, EditProfileActivity::class.java))
        }


        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    renderer.render(divContainer, uiState)
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
    fun setSearchResult(r :List<SearchPoint>){
        resultSerch = r
    }

    private fun renderRecommendationsOnMap(recommendations: List<com.example.map.domain.model.Recommendation>) {
        val map = mapView?.mapWindow?.map ?: return
        Log.d("MapMarkers", "Rendering ${recommendations.size} recommendation markers")
        val pin = recommendationPinProvider ?: run {
            val bmp = createBitmapFromVector(R.drawable.ic_pin_blue_svg)
            val provider = bmp?.let { ImageProvider.fromBitmap(it) }
            recommendationPinProvider = provider
            provider
        }

        recommendationMarkers.forEach { marker ->
            map.mapObjects.remove(marker)
        }
        recommendationMarkers = recommendations.map { rec ->
            val point = Point(rec.latitude, rec.longitude)
            Log.d(
                "MapMarkers",
                "Add marker name=${rec.name} lat=${rec.latitude}, lon=${rec.longitude}",
            )
            val placemark = if (pin != null) {
                map.mapObjects.addPlacemark(point, pin)
            } else {
                map.mapObjects.addPlacemark(point)
            }
            placemark.userData = rec
            placemark.addTapListener(recommendationTapListener)
            placemark
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
        super.onStop()
    }

    override fun onMapTap(map: Map, point: Point) {
        Log.i("LocationFlow", "Map tap at lat=${point.latitude}, lon=${point.longitude}")
        selectedPlacemark?.let { map.mapObjects.remove(it) }
        val selectedPin = selectedPinProvider ?: run {
            val bmp = createBitmapFromVector(R.drawable.ic_pin_red_svg)
            val provider = bmp?.let { ImageProvider.fromBitmap(it) }
            selectedPinProvider = provider
            provider
        }
        selectedPlacemark = if (selectedPin != null) {
            map.mapObjects.addPlacemark(point, selectedPin)
        } else {
            map.mapObjects.addPlacemark(point)
        }
        selectedPlacemark?.userData = SelectedLocation(
            latitude = point.latitude,
            longitude = point.longitude,
        )
        selectedPlacemark?.addTapListener(selectedLocationTapListener)
        Log.i(
            "LocationFlow",
            "SelectedLocation set to lat=${point.latitude}, lon=${point.longitude}",
        )
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

    private fun createBitmapFromVector(art: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, art) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun formatCoords(latitude: Double, longitude: Double): String {
        return String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
    }

    private fun moveToPoint(latitude: Double, longitude: Double) {
        val map = mapView?.mapWindow?.map ?: return
        val point = Point(latitude, longitude)
        map.move(
            CameraPosition(point, 16f, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0.6f),
            null,
        )
    }
}
