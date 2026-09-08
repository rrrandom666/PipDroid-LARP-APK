package com.malto4.pipdroid

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class JournalEntry(
    val id: String,
    val text: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long? = null
)

private data class JournalEntryListFile(val entries: List<JournalEntry>)

/** Записи Журнала: один JSON в filesDir через Gson, полная перезапись при каждой мутации. */
class JournalRepository(private val context: Context) {

    private val gson = Gson()
    private val entriesFile = File(context.filesDir, "journal.json")

    fun loadAll(): List<JournalEntry> = runCatching {
        gson.fromJson(entriesFile.readText(), JournalEntryListFile::class.java).entries
    }.getOrDefault(emptyList())

    fun add(entry: JournalEntry) {
        saveAll(loadAll() + entry)
    }

    fun delete(id: String) {
        saveAll(loadAll().filterNot { it.id == id })
    }

    fun update(entry: JournalEntry) {
        saveAll(loadAll().map { if (it.id == entry.id) entry else it })
    }

    private fun saveAll(entries: List<JournalEntry>) {
        entriesFile.writeText(gson.toJson(JournalEntryListFile(entries)))
    }
}
