package com.malto4.pipdroid

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class MapMarker(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val createdAtEpochMillis: Long
)

private data class MarkerListFile(val markers: List<MapMarker>)

/**
 * Маркеры игрока на карте — полный локальный функционал (Фаза E). Источник истины —
 * `markers.json` (Gson), не сами `.md`-файлы: `.md` только для экспорта (см.
 * exportToMarkdown), приложение их обратно не читает. Импорт с голодиска — заглушка,
 * см. HolotapeMarkerSource, реальный BLE-интерфейс голодиска ещё не реализован.
 */
class MarkerRepository(private val context: Context) {

    private val gson = Gson()
    private val markersFile = File(context.filesDir, "markers.json")

    fun loadAll(): List<MapMarker> = runCatching {
        gson.fromJson(markersFile.readText(), MarkerListFile::class.java).markers
    }.getOrDefault(emptyList())

    fun add(marker: MapMarker) {
        val updated = loadAll() + marker
        saveAll(updated)
    }

    fun delete(id: String) {
        val updated = loadAll().filterNot { it.id == id }
        saveAll(updated)
    }

    /** Переименование существующей отметки (Редактировать на карточке деталей) — совпадение
     * по id, остальные поля берутся из переданного marker как есть. */
    fun update(marker: MapMarker) {
        val updated = loadAll().map { if (it.id == marker.id) marker else it }
        saveAll(updated)
    }

    private fun saveAll(markers: List<MapMarker>) {
        markersFile.writeText(gson.toJson(MarkerListFile(markers)))
    }

    /**
     * Простой текстовый формат (не YAML-frontmatter — решение пользователя): заголовок с
     * именем + список координат/даты. Приложение сейчас его не читает, только пишет.
     */
    fun exportToMarkdown(marker: MapMarker): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val safeName = marker.name.ifBlank { "marker" }.replace(Regex("[^A-Za-z0-9А-Яа-яЁё _-]"), "_")
        val file = File(exportDir, "marker_${safeName}_${marker.id.take(8)}.md")
        file.writeText(markdownFor(marker))
        return file
    }

    fun exportAllToMarkdown(): List<File> = loadAll().map { exportToMarkdown(it) }

    private fun markdownFor(marker: MapMarker): String {
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(marker.createdAtEpochMillis))
        return buildString {
            appendLine("# ${marker.name}")
            appendLine()
            appendLine("- Latitude: ${marker.lat}")
            appendLine("- Longitude: ${marker.lon}")
            appendLine("- Created: $created")
        }
    }
}

/**
 * Заглушка под будущий импорт `.md`-маркеров с голодиска (BLE-протокол,
 * `HOLOTAPE:LIST`/`HOLOTAPE:READ:<n>`, см. PipBoy_BLE_Protocol_v0.2.md) — интерфейс без
 * реализации, реальный голодиск-интерфейс ещё не построен. Резервирует место в UI
 * (задизейбленная кнопка "Импорт с голодиска" в списке маркеров), чтобы позже не
 * перекраивать экран под неё.
 */
interface HolotapeMarkerSource {
    fun listAvailableFiles(): List<String>
    fun readMarkerFile(name: String): String
}
