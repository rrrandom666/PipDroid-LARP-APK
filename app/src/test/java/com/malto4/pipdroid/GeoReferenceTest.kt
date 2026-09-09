package com.malto4.pipdroid

import org.junit.Assert.assertEquals
import org.junit.Test

/** Привязка карты: линейная интерполяция по прямоугольнику и haversine. */
class GeoReferenceTest {

    /** Прямоугольник намеренно неквадратный: 1° широты на 2° долготы, битмап 200×100. */
    private val bounds = MapBounds(
        minLat = 50.0, maxLat = 51.0,
        minLon = 30.0, maxLon = 32.0,
        centerLat = 50.5, centerLon = 31.0,
        zoomLevel = 15.0,
    )
    private val geo = GeoReference(bounds, bitmapWidthPx = 200, bitmapHeightPx = 100)

    @Test
    fun `строка 0 битмапа — север, последняя — юг`() {
        val (northX, northY) = geo.latLonToPixelXY(bounds.maxLat, bounds.minLon)
        assertEquals(0.0, northX, 1e-9)
        assertEquals(0.0, northY, 1e-9)
        val (southX, southY) = geo.latLonToPixelXY(bounds.minLat, bounds.maxLon)
        assertEquals(200.0, southX, 1e-9)
        assertEquals(100.0, southY, 1e-9)
    }

    @Test
    fun `углы битмапа дают углы прямоугольника`() {
        val (topLeftLat, topLeftLon) = geo.pixelToLatLon(0f, 0f)
        assertEquals(51.0, topLeftLat, 1e-9)
        assertEquals(30.0, topLeftLon, 1e-9)
        val (bottomRightLat, bottomRightLon) = geo.pixelToLatLon(200f, 100f)
        assertEquals(50.0, bottomRightLat, 1e-9)
        assertEquals(32.0, bottomRightLon, 1e-9)
    }

    @Test
    fun `перевод в пиксели и обратно возвращает ту же точку`() {
        val lat = 50.25
        val lon = 31.5
        val (x, y) = geo.latLonToPixelXY(lat, lon)
        val (backLat, backLon) = geo.pixelToLatLon(x.toFloat(), y.toFloat())
        assertEquals(lat, backLat, 1e-6)
        assertEquals(lon, backLon, 1e-6)
    }

    @Test
    fun `доля тапа PhotoView считается от размера битмапа`() {
        val (lat, lon) = geo.fractionToLatLon(0.5f, 0.5f)
        assertEquals(50.5, lat, 1e-9)
        assertEquals(31.0, lon, 1e-9)
        assertEquals(geo.pixelToLatLon(50f, 25f), geo.fractionToLatLon(0.25f, 0.25f))
    }

    @Test
    fun `haversine — градус долготы на экваторе, симметрия и ноль`() {
        assertEquals(111194.9, GeoReference.haversineMeters(0.0, 0.0, 0.0, 1.0), 0.1)
        assertEquals(
            GeoReference.haversineMeters(50.0, 30.0, 50.1, 30.1),
            GeoReference.haversineMeters(50.1, 30.1, 50.0, 30.0),
            1e-9,
        )
        assertEquals(0.0, GeoReference.haversineMeters(50.0, 30.0, 50.0, 30.0), 1e-9)
    }

    @Test
    fun `градус долготы на широте полигона короче экваториального`() {
        val atEquator = GeoReference.haversineMeters(0.0, 30.0, 0.0, 31.0)
        val atPolygon = GeoReference.haversineMeters(50.0, 30.0, 50.0, 31.0)
        assertEquals(atEquator * Math.cos(Math.toRadians(50.0)), atPolygon, 1.0)
    }
}
