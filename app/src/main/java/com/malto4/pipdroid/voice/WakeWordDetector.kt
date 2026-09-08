package com.malto4.pipdroid.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/** Обнаружение будческого слова офлайн по схеме openWakeWord: mel -> embedding -> classifier. */
/** ONNX Runtime, не TFLite: TFLite-версии тех же моделей падают в конструкторе Interpreter. */
/** Константы окон и страйдов взяты из исходников апстрима — менять только вместе со сверкой с ним. */
class WakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit,
) {
    companion object {
        private const val TAG = "WakeWordDetector"

        private const val SAMPLE_RATE = 16_000
        // 80мс — рекомендация автора openWakeWord (кратно 80мс для эффективности/задержки).
        private const val CHUNK_SAMPLES = 1_280

        private const val MEL_BINS = 32
        private const val EMBED_WINDOW = 76
        private const val EMBED_STRIDE = 8
        private const val EMBED_DIM = 96
        private const val FEATURE_FRAMES = 16
        private const val FEATURE_BUFFER_MAX = 120

        private const val DETECTION_THRESHOLD = 0.5f
        private const val DETECTION_COOLDOWN_MS = 2_000L
        // Pre-roll компенсирует задержку детектора; 2с оказались слишком много — в захват попадало
        // само будческое слово и засоряло распознавание. Подбирается на реальном устройстве.
        private const val PREROLL_CHUNKS = 6

        private const val MEL_MODEL_ASSET = "models/wakeword/melspectrogram.onnx"
        private const val EMBED_MODEL_ASSET = "models/wakeword/embedding_model.onnx"
        private const val WAKEWORD_MODEL_ASSET = "models/wakeword/wakeword_classifier.onnx"
    }

    // OrtEnvironment — процессный синглтон, закрывать его самим не нужно.
    private var env: OrtEnvironment? = null
    private var melSession: OrtSession? = null
    private var embedSession: OrtSession? = null
    private var wakeSession: OrtSession? = null

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var running = false

    /** Точка врезки для голосовых команд: чанки дополнительно уходят сюда без пересоздания AudioRecord. */
    /** Ставить и снимать только через armCommandSink()/disarmCommandSink() — те прогоняют pre-roll. */
    /** Зовётся на потоке захвата, не на главном. */
    @Volatile private var rawAudioSink: ((ShortArray, Int) -> Unit)? = null

    // Кольцевой буфер pre-roll читается и пишется с разных потоков, отсюда preRollLock.
    private val preRollLock = Any()
    private val preRollBuffer = ArrayDeque<ShortArray>()

    // Буфер мел-фреймов между вызовами melspectrogram.onnx и embedding_model.onnx.
    private val melBuffer = ArrayList<FloatArray>()
    // Буфер эмбеддингов между embedding_model.onnx и классификатором.
    private val embedBuffer = ArrayList<FloatArray>()

    private var lastDetectionAtMs = 0L

    /** true, если все три модели успешно загружены и можно звать [start]. */
    fun isReady(): Boolean = melSession != null && embedSession != null && wakeSession != null

    fun load(): Boolean {
        return try {
            val environment = OrtEnvironment.getEnvironment()
            env = environment
            melSession = environment.createSession(loadModelBytes(MEL_MODEL_ASSET))
            embedSession = environment.createSession(loadModelBytes(EMBED_MODEL_ASSET))
            wakeSession = environment.createSession(loadModelBytes(WAKEWORD_MODEL_ASSET))
            Log.d(TAG, "Модели загружены: mel in=${melSession!!.inputInfo.keys} out=${melSession!!.outputInfo.keys}, " +
                "embed in=${embedSession!!.inputInfo.keys} out=${embedSession!!.outputInfo.keys}, " +
                "wake in=${wakeSession!!.inputInfo.keys} out=${wakeSession!!.outputInfo.keys}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось загрузить ONNX-модели wake-word", e)
            release()
            false
        }
    }

    /** Требует уже выданное разрешение RECORD_AUDIO — проверка на стороне вызывающего кода. */
    fun start() {
        if (running) return
        if (!isReady() && !load()) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize вернул $minBufferSize, микрофон недоступен")
            return
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, CHUNK_SAMPLES * 2 * 4)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord не инициализирован")
            record.release()
            return
        }

        audioRecord = record
        melBuffer.clear()
        embedBuffer.clear()
        running = true
        record.startRecording()

        captureThread = Thread({ captureLoop() }, "WakeWordCapture").apply { start() }
        Log.d(TAG, "Прослушивание будческого слова запущено")
    }

    fun stop() {
        running = false
        captureThread?.join(500)
        captureThread = null
        audioRecord?.let {
            try { it.stop() } catch (e: IllegalStateException) { /* уже остановлен */ }
            it.release()
        }
        audioRecord = null
        Log.d(TAG, "Прослушивание будческого слова остановлено")
    }

    fun release() {
        stop()
        melSession?.close(); melSession = null
        embedSession?.close(); embedSession = null
        wakeSession?.close(); wakeSession = null
    }

    /** Ставит точку врезки: сначала прогоняет pre-roll, потом переключает на живой поток. */
    fun armCommandSink(sink: (ShortArray, Int) -> Unit) {
        val preRoll = synchronized(preRollLock) { preRollBuffer.toList() }
        for (preRollChunk in preRoll) {
            sink(preRollChunk, preRollChunk.size)
        }
        rawAudioSink = sink
    }

    /** Обрабатывается на потоке захвата — буферы не потокобезопасны, чистить их с главного нельзя. */
    @Volatile private var pendingClassifierReset = false

    fun disarmCommandSink() {
        // Без сброса классификатор при возобновлении видит замороженный хвост от самого будческого
        // слова и может ложно сработать на нём повторно.
        pendingClassifierReset = true
        rawAudioSink = null
    }

    private fun captureLoop() {
        val chunk = ShortArray(CHUNK_SAMPLES)
        val record = audioRecord ?: return
        while (running) {
            val read = record.read(chunk, 0, chunk.size)
            if (read <= 0) continue
            val chunkCopy = chunk.copyOf(read)
            synchronized(preRollLock) {
                preRollBuffer.addLast(chunkCopy)
                if (preRollBuffer.size > PREROLL_CHUNKS) preRollBuffer.removeFirst()
            }
            val sink = rawAudioSink
            if (sink != null) {
                // Во время команды классификатор не гоняем вовсе: тройная инференция вместе с decode-шагом
                // Vosk не укладывалась в 80мс на слитной речи, и поток отставал от живого звука.
                sink(chunk, read)
                continue
            }
            if (pendingClassifierReset) {
                pendingClassifierReset = false
                melBuffer.clear()
                embedBuffer.clear()
            }
            // Приведение к float32 без нормализации — так же, как в openWakeWord; деление сменило бы масштаб.
            val floatChunk = FloatArray(read) { chunk[it].toFloat() }
            try {
                processChunk(floatChunk)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка в конвейере wake-word, пропускаю чанк", e)
            }
        }
    }

    private fun processChunk(audio: FloatArray) {
        val melFrames = runMelspectrogram(audio)
        if (melFrames.isEmpty()) return
        melBuffer.addAll(melFrames)

        var processed = 0
        while (processed + EMBED_WINDOW <= melBuffer.size) {
            val window = Array(EMBED_WINDOW) { melBuffer[processed + it] }
            val embedding = runEmbedding(window)
            embedBuffer.add(embedding)
            processed += EMBED_STRIDE
        }
        // Всё до processed уже вошло в окно — следующее стартует ровно оттуда.
        if (processed > 0) {
            melBuffer.subList(0, processed).clear()
        }

        if (embedBuffer.size > FEATURE_BUFFER_MAX) {
            embedBuffer.subList(0, embedBuffer.size - FEATURE_BUFFER_MAX).clear()
        }

        if (embedBuffer.size >= FEATURE_FRAMES) {
            val score = runClassifier(embedBuffer.subList(embedBuffer.size - FEATURE_FRAMES, embedBuffer.size))
            val now = System.currentTimeMillis()
            if (score >= DETECTION_THRESHOLD && now - lastDetectionAtMs >= DETECTION_COOLDOWN_MS) {
                lastDetectionAtMs = now
                Log.d(TAG, "Wake-word сработал, score=$score")
                onDetected()
            }
        }
    }

    private fun runMelspectrogram(audio: FloatArray): List<FloatArray> {
        val session = melSession ?: return emptyList()
        val environment = env ?: return emptyList()
        val inputName = session.inputNames.iterator().next()
        OnnxTensor.createTensor(environment, arrayOf(audio)).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { result ->
                val outputTensor = result.iterator().next().value as OnnxTensor
                // Реальный ранг выхода 4D вместо задокументированных 2-3D: разворачиваем в плоский список и режем по MEL_BINS.
                val flat = ArrayList<Float>()
                flattenFloats(outputTensor.value, flat)
                val nFrames = flat.size / MEL_BINS
                val frames = ArrayList<FloatArray>(nFrames)
                for (i in 0 until nFrames) {
                    // Масштаб x/10 + 2 — постобработка мел-спектра из openWakeWord, не часть ONNX-графа.
                    frames.add(FloatArray(MEL_BINS) { flat[i * MEL_BINS + it] / 10f + 2f })
                }
                return frames
            }
        }
    }

    private fun runEmbedding(window: Array<FloatArray>): FloatArray {
        val session = embedSession ?: return FloatArray(EMBED_DIM)
        val environment = env ?: return FloatArray(EMBED_DIM)
        val inputName = session.inputNames.iterator().next()
        // [1, 76, 32, 1]
        val input = arrayOf(Array(EMBED_WINDOW) { i -> Array(MEL_BINS) { j -> floatArrayOf(window[i][j]) } })
        OnnxTensor.createTensor(environment, input).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { result ->
                val outputTensor = result.iterator().next().value as OnnxTensor
                val flat = ArrayList<Float>()
                flattenFloats(outputTensor.value, flat)
                return flat.toFloatArray()
            }
        }
    }

    private fun runClassifier(embeddings: List<FloatArray>): Float {
        val session = wakeSession ?: return 0f
        val environment = env ?: return 0f
        val inputName = session.inputNames.iterator().next()
        // [1, 16, 96]
        val input = arrayOf(Array(FEATURE_FRAMES) { i -> embeddings[i] })
        OnnxTensor.createTensor(environment, input).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { result ->
                val outputTensor = result.iterator().next().value as OnnxTensor
                val flat = ArrayList<Float>()
                flattenFloats(outputTensor.value, flat)
                return flat[0]
            }
        }
    }

    /** Разворачивает произвольно вложенный массив в плоский список — формы выходов не совпали с апстримом. */
    private fun flattenFloats(value: Any, out: MutableList<Float>) {
        when (value) {
            is FloatArray -> for (f in value) out.add(f)
            is Array<*> -> for (v in value) flattenFloats(v!!, out)
            else -> throw IllegalStateException("Неожиданный тип выхода ONNX-модели: ${value::class}")
        }
    }

    private fun loadModelBytes(assetPath: String): ByteArray {
        return context.assets.open(assetPath).use { it.readBytes() }
    }
}
