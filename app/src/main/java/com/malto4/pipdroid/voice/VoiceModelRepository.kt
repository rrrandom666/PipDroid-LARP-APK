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

/** Модель Vosk доставляется SAF-импортом .zip, а не в assets: иначе лишние ~50 МБ у каждого игрока. */
/** Архив обычно оборачивает дерево модели ещё одной папкой — импорт разворачивает фактический
 * корень на уровень modelDir, а не хранит обёртку. */
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

    /** Валидация — ДО подмены рабочей модели, так плохой .zip не портит уже импортированное;
     * звать вне главного потока. */
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
                // renameTo подкаталогов, а не copyRecursively: источник и назначение на одной файловой системе,
                // и rename каталога мгновенен — copyRecursively на большой модели удваивал время импорта.
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

            // Старую модель уводим в сторону, а не удаляем заранее: между удалением и готовностью замены
            // есть окно, в котором прерванный процесс оставил бы игрока вообще без модели.
            val oldModelDir = File(context.filesDir, "vosk_model_old")
            oldModelDir.deleteRecursively()
            val hadOldModel = modelDir.exists() && modelDir.renameTo(oldModelDir)
            if (!stagingDir.renameTo(modelDir)) {
                // renameTo может не сработать между точками монтирования — сначала возвращаем старую модель на место.
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

    /** Ищет корень модели: либо сам каталог, либо один из его прямых подкаталогов. */
    private fun findModelRoot(dir: File): File? {
        if (isValidModelRoot(dir)) return dir
        dir.listFiles { file -> file.isDirectory }?.forEach { child ->
            if (isValidModelRoot(child)) return child
        }
        return null
    }

    /** Распаковка с защитой от zip-slip — путь каждой записи проверяется до записи на диск. */
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
