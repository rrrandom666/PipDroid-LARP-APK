package com.malto4.pipdroid

import java.util.PriorityQueue

/**
 * Пеший маршрут по графу дорог/троп из бандла (map_roads.json, см. MapBundleRepository) —
 * A* с haversine-эвристикой. Чистый Kotlin-класс без Android-зависимостей (только
 * распарсенный RoadGraph + GeoReference для дистанций) — юнит-тестируем в изоляции, хотя
 * тестовой инфраструктуры в проекте пока нет.
 */
class PedestrianRouter(private val graph: RoadGraph) {

    /** Линейный перебор — для масштаба полигона (сотни-низкие тысячи узлов) спейшл-индекс
     * не нужен. */
    fun nearestNode(lat: Double, lon: Double): String? {
        var bestId: String? = null
        var bestDist = Double.MAX_VALUE
        for ((id, latLon) in graph.nodes) {
            val dist = GeoReference.haversineMeters(lat, lon, latLon[0], latLon[1])
            if (dist < bestDist) {
                bestDist = dist
                bestId = id
            }
        }
        return bestId
    }

    /** null — нет пути (несвязный граф) либо рядом с одной из точек вообще нет узлов графа. */
    fun route(startLat: Double, startLon: Double, destLat: Double, destLon: Double): List<Pair<Double, Double>>? {
        val startNode = nearestNode(startLat, startLon) ?: return null
        val destNode = nearestNode(destLat, destLon) ?: return null
        val destLatLon = graph.nodes[destNode] ?: return null

        if (startNode == destNode) {
            return listOf(destLatLon[0] to destLatLon[1])
        }

        fun heuristic(nodeId: String): Double {
            val latLon = graph.nodes[nodeId] ?: return 0.0
            return GeoReference.haversineMeters(latLon[0], latLon[1], destLatLon[0], destLatLon[1])
        }

        val gScore = HashMap<String, Double>().apply { put(startNode, 0.0) }
        val cameFrom = HashMap<String, String>()
        val visited = HashSet<String>()
        val open = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        open.add(startNode to heuristic(startNode))

        while (true) {
            val (current, _) = open.poll() ?: break
            if (!visited.add(current)) continue
            if (current == destNode) {
                val path = mutableListOf(current)
                var node = current
                while (cameFrom.containsKey(node)) {
                    node = cameFrom.getValue(node)
                    path.add(node)
                }
                path.reverse()
                return path.mapNotNull { id -> graph.nodes[id]?.let { it[0] to it[1] } }
            }
            val currentG = gScore[current] ?: continue
            val neighbors = graph.adjacency[current] ?: continue
            for ((neighbor, weight) in neighbors) {
                if (neighbor in visited) continue
                val tentativeG = currentG + weight
                if (tentativeG < (gScore[neighbor] ?: Double.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeG
                    open.add(neighbor to (tentativeG + heuristic(neighbor)))
                }
            }
        }
        return null
    }
}
