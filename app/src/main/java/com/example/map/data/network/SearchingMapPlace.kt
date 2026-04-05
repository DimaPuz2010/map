package com.example.map.data.network

import com.example.map.MainActivity
import com.example.map.domain.model.SearchPoint
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.Session
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.cos

class SearchingMapPlace {
    val searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    val searchOptions = SearchOptions().apply {
        searchTypes = 2
        resultPageSize = 32
    }

    suspend fun search(response: String, cont: MainActivity): List<SearchPoint> = suspendCoroutine { continuation ->
        val points = ArrayList<SearchPoint>()

        val searchSessionListener = object : Session.SearchListener {
            override fun onSearchResponse(response: Response) {
                response.collection.children.forEach { item ->
                    points.add(
                        SearchPoint(
                            name = item.obj?.name ?: "",
                            address = item.obj?.descriptionText ?: "",
                            lat = item.obj?.geometry?.getOrNull(0)?.point?.latitude ?: 0.0,
                            lon = item.obj?.geometry?.getOrNull(0)?.point?.longitude ?: 0.0
                        )
                    )
                }
                continuation.resume(points)
                cont.setSearchResult(points)
            }

            override fun onSearchError(error: com.yandex.runtime.Error) {

            }
        }

        searchManager.submit(
            response,
            Geometry.fromBoundingBox(createBoundingBox(Point(54.9885, 73.3242), 1000.0f)),
            searchOptions,
            searchSessionListener,
        )
    }
}

private fun createBoundingBox(center: Point, radiusMeters: Float): BoundingBox {
    val lat = center.latitude
    val lon = center.longitude

    val dLat = radiusMeters / 111000.0
    val dLon = radiusMeters / (111000.0 * cos(Math.toRadians(lat)))

    val southWest = Point(lat - dLat, lon - dLon)
    val northEast = Point(lat + dLat, lon + dLon)

    return BoundingBox(southWest, northEast)
}