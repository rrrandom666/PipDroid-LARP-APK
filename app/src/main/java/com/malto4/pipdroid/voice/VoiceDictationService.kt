package com.malto4.pipdroid.voice

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

data class CommandChunkResult(val isFinal: Boolean, val text: String)

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
    // Отдельный Recognizer для голосовых команд (roadmap, этап 21 п.4) — та же Model, что и у
    // диктовки, но БЕЗ SpeechService/своего AudioRecord: чанки приходят снаружи (feedCommandAudio),
    // от уже идущего потока WakeWordDetector. См. класс-doc startCommandRecognition().
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

    // feedCommandAudio() зовётся с потока WakeWordCapture (см. WakeWordDetector.rawAudioSink),
    // а start/stopCommandRecognition() — обычно с главного потока (таймаут/совпадение команды,
    // MainActivity). Без единой блокировки на весь Recognizer это гонка на нативной стороне:
    // close() на одном потоке ровно в момент acceptWaveForm() на другом — реальный SIGSEGV/
    // SIGABRT, поймано на устройстве. Каждый метод ниже держит commandLock на всё время своего
    // native-вызова, включая пару feed+извлечь-результат внутри одного feedCommandAudio() —
    // раздельные feed()/getResult() отдельными синхронизированными вызовами оставляли бы
    // ровно то же окно гонки между ними.
    private val commandLock = Any()

    /**
     * Голосовая команда после будческого слова (roadmap, этап 21 п.4) — НЕ создаёт свой
     * AudioRecord, в отличие от startListening()/SpeechService выше. Чанки приходят снаружи
     * через feedCommandAudio(), от того же микрофонного потока, что уже читает WakeWordDetector.
     * Находка на реальном устройстве: пересоздание AudioRecord на стыке будческое-слово->команда
     * (старый вариант — тоже через SpeechService) систематически обрезало/искажало первое слово
     * команды при слитной речи ("лёгкое ранение" -> "я ранения") — второе слово доезжало
     * нормально. Не переключать микрофон вообще — единственный надёжный способ убрать это, не
     * полумеры вида увеличения буфера/уменьшения задержки пересоздания.
     *
     * НЕ пересоздаёт commandRecognizer между попытками (см. отсутствие close() ниже) — тот же
     * симптом (систематически калечится именно ПЕРВОЕ слово, второе почти всегда доезжает)
     * совпадает с задокументированным поведением Kaldi/Vosk: online CMVN-нормализация ещё не
     * стабилизировалась на первых кадрах свежесозданного Recognizer, а chain-модели физически
     * недополучают future-context на самом краю потока (см. Kaldi docs, OnlineCmvn/nnet3
     * context). Раньше объект пересоздавался на КАЖДОЕ срабатывание — то есть этот эффект бил
     * по каждой попытке заново, а не только по первой в сессии приложения. Один и тот же
     * Recognizer теперь живёт между попытками, "прогреваясь" после первого использования —
     * полное закрытие только в release() (конец жизни Activity).
     */
    fun startCommandRecognition() {
        synchronized(commandLock) {
            if (commandRecognizer == null) {
                val loadedModel = model ?: return
                commandRecognizer = Recognizer(loadedModel, SAMPLE_RATE)
            }
        }
    }

    /** Кормит чанк и сразу же атомарно вытаскивает результат — [CommandChunkResult.isFinal]
     * true, если в чанке набралась завершённая фраза ([CommandChunkResult.text] — она; иначе
     * это только предварительный текст). */
    fun feedCommandAudio(chunk: ShortArray, len: Int): CommandChunkResult {
        synchronized(commandLock) {
            val rec = commandRecognizer ?: return CommandChunkResult(isFinal = false, text = "")
            val isFinal = rec.acceptWaveForm(chunk, len)
            val text = if (isFinal) extractField(rec.result, "text") else extractField(rec.partialResult, "partial")
            return CommandChunkResult(isFinal, text)
        }
    }

    /** Принудительно "дожимает" то, что накопилось без естественной паузы в речи — звать по
     * таймауту прослушивания команды, аналог onFinalResult() у SpeechService-версии. */
    fun flushCommandFinalText(): String {
        synchronized(commandLock) {
            return extractField(commandRecognizer?.finalResult, "text")
        }
    }

    /** Между попытками команды Recognizer НЕ закрывается — см. startCommandRecognition()
     * (гипотеза "холодный старт" Kaldi/Vosk). Оставлен как явная точка "сессия команды
     * закончилась" на будущее (например сброс partial-состояния), сейчас no-op. Полное
     * закрытие объекта — closeCommandRecognition(), только из release(). */
    fun stopCommandRecognition() {}

    private fun closeCommandRecognition() {
        synchronized(commandLock) {
            commandRecognizer?.close()
            commandRecognizer = null
        }
    }

    /** Полностью выгружает модель из памяти — звать из onDestroy Activity, не между
     * отдельными сессиями диктовки (см. класс-doc — Model держится дольше одной сессии). */
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
