package com.malto4.pipdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A* по графу дорог бандла: веса рёбер — реальные расстояния, как их готовит питоновская сторона. */
class PedestrianRouterTest {

    /** Ромб: прямая A-B-C по одной широте и северный крюк A-D-C. Плюс изолированный E. */
    private val a = 50.000 to 30.000
    private val b = 50.000 to 30.002
    private val c = 50.000 to 30.004
    private val d = 50.010 to 30.002
    private val e = 51.000 to 31.000

    private fun dist(from: Pair<Double, Double>, to: Pair<Double, Double>) =
        GeoReference.haversineMeters(from.first, from.second, to.first, to.second)

    private val graph = RoadGraph(
        nodes = mapOf(
            "A" to listOf(a.first, a.second),
            "B" to listOf(b.first, b.second),
            "C" to listOf(c.first, c.second),
            "D" to listOf(d.first, d.second),
            "E" to listOf(e.first, e.second),
        ),
        adjacency = mapOf(
            "A" to mapOf("B" to dist(a, b), "D" to dist(a, d)),
            "B" to mapOf("A" to dist(a, b), "C" to dist(b, c)),
            "C" to mapOf("B" to dist(b, c), "D" to dist(d, c)),
            "D" to mapOf("A" to dist(a, d), "C" to dist(d, c)),
            "E" to emptyMap(),
        ),
    )
    private val router = PedestrianRouter(graph)

    @Test
    fun `ближайший узел — по расстоянию, а не по порядку в графе`() {
        assertEquals("B", router.nearestNode(50.0001, 30.0021))
        assertEquals("E", router.nearestNode(50.9, 30.9))
    }

    @Test
    fun `из двух путей выбирается географически короткий, а не с меньшим числом рёбер`() {
        val path = router.route(a.first, a.second, c.first, c.second)
        assertEquals(listOf(a, b, c), path)
    }

    @Test
    fun `концы маршрута притягиваются к узлам графа, а не к запрошенным координатам`() {
        val path = router.route(49.9995, 29.9995, 50.0001, 30.0039)
        assertEquals(listOf(a, b, c), path)
    }

    @Test
    fun `несвязная вершина недостижима`() {
        assertNull(router.route(a.first, a.second, e.first, e.second))
    }

    @Test
    fun `старт и финиш в одном узле дают путь из одной точки`() {
        val path = router.route(50.0001, 30.0001, 49.9999, 29.9999)
        assertEquals(listOf(a), path)
    }

    @Test
    fun `пустой граф не даёт маршрута`() {
        val empty = PedestrianRouter(RoadGraph(nodes = emptyMap(), adjacency = emptyMap()))
        assertNull(empty.nearestNode(50.0, 30.0))
        assertNull(empty.route(50.0, 30.0, 50.1, 30.1))
    }
}
