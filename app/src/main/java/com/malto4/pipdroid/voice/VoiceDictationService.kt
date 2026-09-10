package com.malto4.pipdroid.voice

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

data class CommandChunkResult(val isFinal: Boolean, val text: String)

interface DictationListener {
    /** Промежуточный, ещё не подтверждённый результат — только для живого превью. */
    fun onPartialText(text: String)
    /** Подтверждённый кусок текста; приходит и по паузе в речи, и хвостом при остановке. */
    fun onFinalText(text: String)
    fun onError(message: String)
}

/** Диктовка поверх импортированной модели Vosk; захват микрофона делает штатный SpeechService. */
/** Model — тяжёлый объект, грузится один раз вне главного потока и живёт, пока жив вызывающий. */
class VoiceDictationService {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    // Отдельный Recognizer для команд: та же Model, но чанки приходят снаружи, без своего AudioRecord.
    private var commandRecognizer: Recognizer? = null

    fun isModelLoaded(): Boolean = model != null

    /** Блокирующая загрузка модели в память — вызывать вне главного потока (Dispatchers.IO). */
    fun loadModel(modelPath: String) {
        model = Model(modelPath)
    }

    fun startListening(listener: DictationListener) {
        val loadedModel = model
        if (loadedModel == null) {
            listener.onError("Model not loaded")
            return
        }
        stopListening()
        val rec = Recognizer(loadedModel, SAMPLE_RATE)
        recognizer = rec
        val service = SpeechService(rec, SAMPLE_RATE)
        speechService = service
        service.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                listener.onPartialText(extractField(hypothesis, "partial"))
            }
            override fun onResult(hypothesis: String?) {
                val text = extractField(hypothesis, "text")
                if (text.isNotBlank()) listener.onFinalText(text)
            }
            override fun onFinalResult(hypothesis: String?) {
                val text = extractField(hypothesis, "text")
                if (text.isNotBlank()) listener.onFinalText(text)
            }
            override fun onError(exception: Exception?) {
                listener.onError(exception?.message ?: "Recognition error")
            }
            override fun onTimeout() {}
        })
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        recognizer?.close()
        recognizer = null
    }

    // feedCommandAudio() зовётся с потока захвата, а start/stop — с главного: без общей блокировки
    // close() в момент acceptWaveForm() даёт реальный SIGSEGV на нативной стороне.
    private val commandLock = Any()

    /** Голосовая команда не создаёт свой AudioRecord: чанки идут от того же потока, что читает
     * детектор — пересоздание микрофона на стыке систематически калечило первое слово команды. */
    /** Recognizer между попытками не пересоздаётся: у свежего объекта online CMVN ещё не
     * стабилизировалась, и страдало первое слово каждой попытки; закрывается только в release(). */
    fun startCommandRecognition() {
        synchronized(commandLock) {
            if (commandRecognizer == null) {
                val loadedModel = model ?: return
                commandRecognizer = Recognizer(loadedModel, SAMPLE_RATE)
            }
        }
    }

    /** Кормит чанк и атомарно забирает результат; isFinal — в чанке набралась завершённая фраза. */
    fun feedCommandAudio(chunk: ShortArray, len: Int): CommandChunkResult {
        synchronized(commandLock) {
            val rec = commandRecognizer ?: return CommandChunkResult(isFinal = false, text = "")
            val isFinal = rec.acceptWaveForm(chunk, len)
            val text = if (isFinal) extractField(rec.result, "text") else extractField(rec.partialResult, "partial")
            return CommandChunkResult(isFinal, text)
        }
    }

    /** Дожимает накопленное без естественной паузы — звать по таймауту прослушивания. */
    fun flushCommandFinalText(): String {
        synchronized(commandLock) {
            return extractField(commandRecognizer?.finalResult, "text")
        }
    }

    /** Recognizer здесь не закрывается — явная точка "сессия закончилась" на будущее, сейчас no-op. */
    fun stopCommandRecognition() {}

    private fun closeCommandRecognition() {
        synchronized(commandLock) {
            commandRecognizer?.close()
            commandRecognizer = null
        }
    }

    /** Полностью выгружает модель — звать из onDestroy, не между сессиями диктовки. */
    fun release() {
        stopListening()
        closeCommandRecognition()
        model?.close()
        model = null
    }

    private fun extractField(json: String?, field: String): String {
        if (json.isNullOrBlank()) return ""
        return runCatching { JSONObject(json).optString(field, "") }.getOrDefault("")
    }

    companion object {
        private const val SAMPLE_RATE = 16000.0f
    }
}
