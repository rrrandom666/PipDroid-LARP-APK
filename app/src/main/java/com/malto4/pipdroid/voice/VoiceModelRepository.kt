package com.malto4.pipdroid.voice

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.malto4.pipdroid.R
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

private data class VoiceModelImportMeta(
    val importedAtEpochMillis: Long,
    val sourceFileName: String
)

/**
 * Офлайн-модель Vosk (STT-слой голосовых команд после будческого слова, roadmap этап 21) —
 * доставляется тем же принципом, что и бандл карты (MapBundleRepository): не через assets
 * APK (лишние ~50 МБ у каждого игрока, даже если голосовые команды ему не нужны), а
 * SAF-выбором одного .zip-файла (vosk-model-small-ru и подобные) в Settings, с распаковкой
 * во внутреннее хранилище. Один активный набор моделей за раз, новый импорт полностью
 * заменяет предыдущий.
 *
 * В отличие от бандла карты (плоские 3 файла) модель Vosk — это дерево подпапок
 * (am/conf/graph/ivector), и архив обычно оборачивает их ещё одной папкой с именем модели
 * (vosk-model-small-ru-0.22/...) — importFromZip() определяет фактический корень модели
 * внутри распакованного дерева (сам tmp-каталог либо один из его прямых подкаталогов) и
 * разворачивает его на уровень modelDir/, а не хранит лишнюю обёртку.
 */
class VoiceModelRepository(private val context: Context) {

    private val gson = Gson()
    private val modelDir = File(context.filesDir, "vosk_model")
    private val metaFile = File(modelDir, "import_meta.json")

    fun modelDir(): File = modelDir

    fun hasModel(): Boolean = isValidModelRoot(modelDir)

    private fun importMeta(): VoiceModelImportMeta? = runCatching {
        gson.fromJson(metaFile.readText(), VoiceModelImportMeta::class.java)
    }.getOrNull()

    fun importedAtEpochMillis(): Long? = importMeta()?.importedAtEpochMillis

    fun importedSourceFileName(): String? = importMeta()?.sourceFileName

    /**
     * Распаковывает выбранный .zip во временный каталог, ищет в нём корень модели (по
     * наличию am/final.mdl, conf/mfcc.conf, .fst-файлов в graph/), затем атомарно подменяет им
     * modelDir. Валидация — ДО подмены рабочей модели, так плохой .zip никогда не портит
     * то, что уже было импортировано. Вызывать вне главного потока (файловый и zip I/O).
     */
    fun importFromZip(zipUri: Uri): Result<Unit> = runCatching {
        val sourceFileName = DocumentFile.fromSingleUri(context, zipUri)?.name ?: "?"

        val unzipDir = File(context.filesDir, "vosk_model_unzip_tmp")
        unzipDir.deleteRecursively()
        unzipDir.mkdirs()
        try {
            val input = context.contentResolver.openInputStream(zipUri)
                ?: throw IllegalArgumentException(context.getString(R.string.voice_model_import_error_open))
            input.use { unzip(it, unzipDir) }

            val modelRoot = findModelRoot(unzipDir)
                ?: throw IllegalArgumentException(context.getString(R.string.voice_model_import_error_structure))

            val stagingDir = File(context.filesDir, "vosk_model_tmp")
            stagingDir.deleteRecursively()
            if (modelRoot == unzipDir) {
                unzipDir.renameTo(stagingDir)
            } else {
                // renameTo для каждого подкаталога (am/conf/graph/...), не copyRecursively —
                // источник и назначение на одном и том же filesystem (оба под filesDir), а
                // rename() каталога на POSIX — мгновенная смена записи в родителе, НЕ копирование
                // содержимого, независимо от размера. Раньше здесь стоял copyRecursively —
                // на модели в ~2 ГБ это означало ВТОРОЕ полное копирование поверх уже сделанного
                // при распаковке (первое — zip-энтри в unzipDir), что на практике удваивало
                // время импорта и было прямой причиной находки ниже (импорт с большой моделью
                // не укладывался в терпение игрока и обрывался до конца).
                stagingDir.mkdirs()
                modelRoot.listFiles()?.forEach { child ->
                    if (!child.renameTo(File(stagingDir, child.name))) {
                        child.copyRecursively(File(stagingDir, child.name), overwrite = true)
                    }
                }
            }
            File(stagingDir, "import_meta.json").writeText(
                gson.toJson(VoiceModelImportMeta(System.currentTimeMillis(), sourceFileName))
            )

            // Подмена — переименованием старой модели в сторону, а не предварительным
            // deleteRecursively() рабочей modelDir (как было раньше): между удалением и
            // гарантированной готовностью замены есть окно, и для GB-модели это не
            // теоретический риск — процесс/корутина вполне может быть прервана посреди
            // многоминутного явного копирования (Activity ушла в фон, ОС порезала процесс
            // по памяти и т.п.), и тогда старая модель уже удалена, а новая не успела
            // встать на место — NO модели вообще. renameTo() каталога, как и выше, —
            // мгновенная операция независимо от размера, а не копирование, так что
            // "переложить старое в сторону, положить новое на место, удалить старое" стоит
            // примерно как deleteRecursively(), просто без окна беззащитности между ними.
            val oldModelDir = File(context.filesDir, "vosk_model_old")
            oldModelDir.deleteRecursively()
            val hadOldModel = modelDir.exists() && modelDir.renameTo(oldModelDir)
            if (!stagingDir.renameTo(modelDir)) {
                // renameTo может не сработать между разными точками монтирования — на
                // filesDir/* этого не бывает на практике, но на всякий случай подстраховываемся
                // явным копированием. Сначала возвращаем старую модель на место (если уводили),
                // чтобы она не потерялась, даже если fallback-копирование тоже не сработает.
                if (hadOldModel) oldModelDir.renameTo(modelDir)
                modelDir.mkdirs()
                stagingDir.listFiles()?.forEach { it.copyRecursively(File(modelDir, it.name), overwrite = true) }
                stagingDir.deleteRecursively()
            }
            oldModelDir.deleteRecursively()
        } finally {
            unzipDir.deleteRecursively()
        }
    }

    private fun isValidModelRoot(dir: File): Boolean {
        val hasAcousticModel = File(dir, "am/final.mdl").exists()
        val hasConfig = File(dir, "conf/mfcc.conf").exists()
        val graphDir = File(dir, "graph")
        val hasGraph = File(graphDir, "HCLG.fst").exists() ||
            (File(graphDir, "HCLr.fst").exists() && File(graphDir, "Gr.fst").exists())
        return hasAcousticModel && hasConfig && hasGraph
    }

    /** Ищет корень модели в распакованном дереве — либо сам каталог, либо один из его
     * прямых подкаталогов (архив с папкой-обёрткой вида vosk-model-small-ru-0.22/). */
    private fun findModelRoot(dir: File): File? {
        if (isValidModelRoot(dir)) return dir
        dir.listFiles { file -> file.isDirectory }?.forEach { child ->
            if (isValidModelRoot(child)) return child
        }
        return null
    }

    /** Распаковка с защитой от zip-slip — путь каждой записи проверяется на выход за
     * пределы targetDir ДО записи на диск (мало ли архив с подделанными именами вида
     * "../../etc/..."). */
    private fun unzip(input: InputStream, targetDir: File) {
        val canonicalTargetPath = targetDir.canonicalPath + File.separator
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                val canonicalOutPath = outFile.canonicalPath
                if (!canonicalOutPath.startsWith(canonicalTargetPath)) {
                    throw SecurityException("Zip entry вне целевого каталога: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
