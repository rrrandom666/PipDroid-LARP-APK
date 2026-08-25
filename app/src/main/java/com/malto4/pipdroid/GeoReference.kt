package com.malto4.pipdroid

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Привязка map.png к координатам — линейная интерполяция по прямоугольнику
 * min/max lat/lon из map_bounds.json. Полигоны LARP небольшие по площади,
 * полноценная картографическая проекция не нужна. Y инвертирован: строка 0
 * картинки — север (max_lat), см. extract_map_from_osm()/ax.set_ylim() в
 * falloutize_map.py — картинка растеризуется в этой же геометрии.
 */
class GeoReference(
    private val bounds: MapBounds,
    private val bitmapWidthPx: Int,
    private val bitmapHeightPx: Int
) {
    fun latLonToPixel(lat: Double, lon: Double): PointF {
        val x = (lon - bounds.minLon) / (bounds.maxLon - bounds.minLon) * bitmapWidthPx
        val y = (bounds.maxLat - lat) / (bounds.maxLat - bounds.minLat) * bitmapHeightPx
        return PointF(x.toFloat(), y.toFloat())
    }

    fun pixelToLatLon(x: Float, y: Float): Pair<Double, Double> {
        val lon = bounds.minLon + (x / bitmapWidthPx) * (bounds.maxLon - bounds.minLon)
        val lat = bounds.maxLat - (y / bitmapHeightPx) * (bounds.maxLat - bounds.minLat)
        return lat to lon
    }

    companion object {
        private const val EARTH_RADIUS_M = 6371000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val dPhi = Math.toRadians(lat2 - lat1)
            val dLambda = Math.toRadians(lon2 - lon1)
            val sinDPhi = sin(dPhi / 2)
            val sinDLambda = sin(dLambda / 2)
            val a = sinDPhi * sinDPhi + cos(phi1) * cos(phi2) * sinDLambda * sinDLambda
            return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
