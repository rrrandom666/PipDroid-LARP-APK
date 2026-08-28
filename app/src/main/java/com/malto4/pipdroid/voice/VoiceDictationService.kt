package com.malto4.pipdroid.voice

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

interface DictationListener {
    /** Промежуточный, ещё не подтверждённый результат — обновляется по ходу произнесения
     * фразы, использовать только для живого превью, не дописывать в поле записи. */
    fun onPartialText(text: String)
    /** Подтверждённый (финализированный) кусок распознанного текста — можно дописывать
     * в поле записи. Приходит и по ходу паузы в речи (SpeechService сам режет на фразы),
     * и один раз в момент stopListening() с "хвостом", что не успел стать onResult. */
    fun onFinalText(text: String)
    fun onError(message: String)
}

/**
 * Голосовой ввод (диктовка, roadmap этап 21 п.2 — Журнал) поверх модели Vosk, уже
 * импортированной в Settings > Voice Model (см. VoiceModelRepository). В отличие от
 * WakeWordDetector (свой AudioRecord-цикл под ONNX-конвейер openWakeWord) — здесь захват
 * микрофона и стриминг в распознаватель уже инкапсулированы штатным
 * org.vosk.android.SpeechService, собственного AudioRecord-кода не нужно.
 *
 * Model — тяжёлый объект (акустическая модель + граф целиком в памяти), грузится один раз
 * (loadModel(), блокирующий вызов — звать вне главного потока) и держится, пока жив вызывающий
 * (обычно на время жизни Activity), не пересоздаётся на каждое открытие попапа Журнала.
 */
class VoiceDictationService {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

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

    /** Полностью выгружает модель из памяти — звать из onDestroy Activity, не между
     * отдельными сессиями диктовки (см. класс-doc — Model держится дольше одной сессии). */
    fun release() {
        stopListening()
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
