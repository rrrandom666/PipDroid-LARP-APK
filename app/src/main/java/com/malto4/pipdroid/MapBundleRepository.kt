package com.malto4.pipdroid

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * Привязка map.png к координатам — линейная интерполяция по прямоугольнику
 * min/max lat/lon, готовится скриптом falloutize_map.py (build_road_graph/
 * extract_map_from_osm, save_bounds_json). Ключи в JSON — snake_case (питоновская
 * сторона), поля класса — camelCase (котлиновская конвенция).
 */
data class MapBounds(
    @SerializedName("min_lat") val minLat: Double,
    @SerializedName("max_lat") val maxLat: Double,
    @SerializedName("min_lon") val minLon: Double,
    @SerializedName("max_lon") val maxLon: Double,
    @SerializedName("center_lat") val centerLat: Double,
    @SerializedName("center_lon") val centerLon: Double,
    @SerializedName("zoom_level") val zoomLevel: Double
)

/**
 * Граф пешеходных дорог/троп — готов для Dijkstra/A* как есть, без
 * дополнительной обработки. nodes: id узла -> [lat, lon]. adjacency: id узла ->
 * {id соседа -> расстояние в метрах}, уже неориентированный (рёбра в обе
 * стороны), см. build_road_graph() в falloutize_map.py.
 */
data class RoadGraph(
    val nodes: Map<String, List<Double>>,
    val adjacency: Map<String, Map<String, Double>>
)

private data class MapBundleImportMeta(
    val importedAtEpochMillis: Long,
    val sourceFolderName: String
)

/**
 * Бандл карты (map.png + map_bounds.json + map_roads.json, фиксированные
 * имена) — готовится заранее вне телефона скриптом falloutize_map.py и
 * импортируется игроком через SAF-пикер папки в Settings. Один активный
 * бандл за раз: новый импорт полностью заменяет предыдущий. Никаких сетевых
 * запросов приложение для карты не делает — весь смысл бандла в том, чтобы
 * карта работала на полигоне без интернета.
 */
class MapBundleRepository(private val context: Context) {

    private val gson = Gson()
    private val bundleDir = File(context.filesDir, "map_bundle")
    private val pngFile = File(bundleDir, "map.png")
    private val boundsFile = File(bundleDir, "map_bounds.json")
    private val roadsFile = File(bundleDir, "map_roads.json")
    private val metaFile = File(bundleDir, "import_meta.json")

    fun hasBundle(): Boolean = pngFile.exists() && boundsFile.exists() && roadsFile.exists()

    fun bundleImageFile(): File = pngFile

    fun loadBounds(): MapBounds? = runCatching {
        gson.fromJson(boundsFile.readText(), MapBounds::class.java)
    }.getOrNull()

    fun loadRoadGraph(): RoadGraph? = runCatching {
        gson.fromJson(roadsFile.readText(), RoadGraph::class.java)
    }.getOrNull()

    private fun importMeta(): MapBundleImportMeta? = runCatching {
        gson.fromJson(metaFile.readText(), MapBundleImportMeta::class.java)
    }.getOrNull()

    fun importedAtEpochMillis(): Long? = importMeta()?.importedAtEpochMillis

    fun importedSourceFolderName(): String? = importMeta()?.sourceFolderName

    /**
     * Копирует map.png/map_bounds.json/map_roads.json из папки, выбранной SAF-пикером.
     * Валидирует наличие и парсимость всех трёх файлов ДО того, как трогает уже
     * рабочий бандл — так плохая папка никогда не портит то, что уже было
     * импортировано. Вызывать вне главного потока (файловый и JSON I/O).
     */
    fun importFromTree(treeUri: Uri): Result<Unit> = runCatching {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException(context.getString(R.string.map_bundle_import_error_folder))

        val pngDoc = tree.findFile("map.png")
        val boundsDoc = tree.findFile("map_bounds.json")
        val roadsDoc = tree.findFile("map_roads.json")
        val missing = listOfNotNull(
            "map.png".takeIf { pngDoc == null },
            "map_bounds.json".takeIf { boundsDoc == null },
            "map_roads.json".takeIf { roadsDoc == null },
        )
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException(
                context.getString(R.string.map_bundle_import_error_missing_files, missing.joinToString(", "))
            )
        }

        val boundsText = readDocumentText(boundsDoc!!.uri)
            ?: throw IllegalArgumentException(context.getString(R.string.map_bundle_import_error_read, "map_bounds.json"))
        val roadsText = readDocumentText(roadsDoc!!.uri)
            ?: throw IllegalArgumentException(context.getString(R.string.map_bundle_import_error_read, "map_roads.json"))

        runCatching { gson.fromJson(boundsText, MapBounds::class.java) }.getOrNull()
            ?: throw IllegalArgumentException(context.getString(R.string.map_bundle_import_error_parse, "map_bounds.json"))
        runCatching { gson.fromJson(roadsText, RoadGraph::class.java) }.getOrNull()
            ?: throw IllegalArgumentException(context.getString(R.string.map_bundle_import_error_parse, "map_roads.json"))

        val tmpDir = File(context.filesDir, "map_bundle_tmp")
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()
        try {
            val pngInput = context.contentResolver.openInputStream(pngDoc!!.uri)
                ?: throw IllegalArgumentException(context.getString(R.string.map_bundle_import_error_read, "map.png"))
            pngInput.use { input ->
                File(tmpDir, "map.png").outputStream().use { output -> input.copyTo(output) }
            }
            File(tmpDir, "map_bounds.json").writeText(boundsText)
            File(tmpDir, "map_roads.json").writeText(roadsText)
            File(tmpDir, "import_meta.json").writeText(
                gson.toJson(MapBundleImportMeta(System.currentTimeMillis(), tree.name ?: "?"))
            )
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            throw e
        }

        bundleDir.deleteRecursively()
        if (!tmpDir.renameTo(bundleDir)) {
            // renameTo может не сработать между разными точками монтирования —
            // на filesDir/* этого не бывает на практике, но на всякий случай
            // подстраховываемся явным копированием вместо тихого молчания.
            bundleDir.mkdirs()
            tmpDir.listFiles()?.forEach { it.copyTo(File(bundleDir, it.name), overwrite = true) }
            tmpDir.deleteRecursively()
        }
    }

    private fun readDocumentText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()
}
