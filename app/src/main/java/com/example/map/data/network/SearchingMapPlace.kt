package com.example.map.data.network

import android.util.Log
import com.example.map.domain.model.SearchPoint
import com.example.map.llm.LocalLlamaClient
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.Session
import kotlin.math.cos

class SearchingMapPlace {
    private var llama: LocalLlamaClient? = null
    val searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    val searchOptions = SearchOptions().apply {
        searchTypes = 2
        resultPageSize = 32
    }



    fun search(response:String): List<SearchPoint>{
        val points: ArrayList<SearchPoint> = ArrayList<SearchPoint>()
        val searchSessionListener = object : Session.SearchListener {
            override fun onSearchResponse(response: Response) {
                response.collection.children.forEach {
                    points.add(SearchPoint(
                        name = it.obj!!.name?:"",
                        address = it.obj!!.descriptionText?: "",
                        lat = it.obj!!.geometry[0].point!!.latitude,
                        lon = it.obj!!.geometry[0].point!!.latitude
                    ))
                }
            }

            override fun onSearchError(error: com.yandex.runtime.Error) {
                // Handle search error.
            }
        }
        searchManager.submit(
            "кофе/веган",
            Geometry.fromBoundingBox(createBoundingBox(Point(54.9885, 73.3242), 1000.0f)),
            searchOptions,
            searchSessionListener,
        )
        return points;
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