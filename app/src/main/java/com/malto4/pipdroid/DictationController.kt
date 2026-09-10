package com.malto4.pipdroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.malto4.pipdroid.voice.DictationListener
import com.malto4.pipdroid.voice.VoiceDictationService
import com.malto4.pipdroid.voice.VoiceModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Диктовка в одно поле ввода: кнопка-микрофон, строка статуса и состояние сессии. */
/** Один экземпляр на экран (попап имени отметки, редактор записи Журнала), но
 * [VoiceDictationService] один на всё приложение — одновременно диктует только один. */
internal class DictationController(
    private val activity: AppCompatActivity,
    private val micButton: ImageButton,
    private val statusView: TextView,
    private val editText: EditText,
    private val permissionRequestCode: Int,
    private val logTag: String,
    private val dictation: VoiceDictationService,
    private val models: VoiceModelRepository,
    private val accentColor: () -> Int,
    private val isVoiceCommandBusy: () -> Boolean,
    private val replaceFirstSegment: () -> Boolean,
    private val playButtonSound: () -> Unit,
    private val playErrorSound: () -> Unit,
) {
    enum class State { IDLE, LOADING, LISTENING }

    var state: State = State.IDLE
        private set

    val isIdle: Boolean get() = state == State.IDLE

    /** Сбрасывает микрофон и строку статуса к покою при каждом открытии экрана. */
    fun refreshAvailability() {
        state = State.IDLE
        setStatus("")
        micButton.alpha = if (models.hasModel()) 1f else 0.4f
        updateVisual(recording = false)
    }

    fun setStatus(text: String) {
        statusView.text = text
    }

    /** Слушающее состояние — акцент темы, осветлённый блендом с белым; иконка меняется на "стоп". */
    fun updateVisual(recording: Boolean) {
        val accent = accentColor()
        val tint = if (recording) ColorUtils.blendARGB(accent, Color.WHITE, 0.4f) else accent
        micButton.backgroundTintList = ColorStateList.valueOf(tint)
        micButton.setImageResource(if (recording) R.drawable.ic_stop else R.drawable.ic_mic)
        ImageViewCompat.setImageTintList(micButton, null)
    }

    /** Общее тело тапа по микрофону — для тача и для ENCBTN. */
    fun handleMicTap() {
        when (state) {
            State.IDLE -> {
                if (isVoiceCommandBusy()) {
                    // VoiceDictationService занят голосовой командой — не отбирать его.
                    playErrorSound()
                    return
                }
                if (!models.hasModel()) {
                    playErrorSound()
                    setStatus(activity.getString(R.string.journal_mic_status_no_model))
                    return
                }
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequestCode
                    )
                    return
                }
                // Звук только на реальный исход, не на ошибках выше.
                playButtonSound()
                start()
            }
            State.LOADING -> { /* повторный тап/ENCBTN во время загрузки модели игнорируется */ }
            State.LISTENING -> {
                playButtonSound()
                stop()
            }
        }
    }

    /** Тап 1 по микрофону; состояние LOADING защищает от гонки, если экран закрыли посреди загрузки модели. */
    fun start() {
        if (dictation.isModelLoaded()) {
            beginListening()
            return
        }
        state = State.LOADING
        setStatus(activity.getString(R.string.journal_mic_status_loading))
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { dictation.loadModel(models.modelDir().absolutePath) }
            withContext(Dispatchers.Main) {
                if (state != State.LOADING) return@withContext
                if (result.isFailure) {
                    state = State.IDLE
                    setStatus(activity.getString(R.string.journal_mic_status_error))
                    return@withContext
                }
                beginListening()
            }
        }
    }

    /** Тап 2 по микрофону и штатное закрытие экрана — stopListening() отдаёт хвост фразы до возврата. */
    fun stop() {
        if (state == State.LISTENING) dictation.stopListening()
        state = State.IDLE
        setStatus("")
        updateVisual(recording = false)
    }

    private fun beginListening() {
        state = State.LISTENING
        // Сброс на каждый новый старт прослушивания, а не на каждый onFinalText.
        replacedThisSession = false
        setStatus(activity.getString(R.string.journal_mic_status_listening))
        updateVisual(recording = true)
        dictation.startListening(object : DictationListener {
            override fun onPartialText(text: String) {
                android.util.Log.d(logTag, "partial: \"$text\"")
                activity.runOnUiThread {
                    // Гейт по состоянию: колбэк мог встать в очередь до тапа на Стоп и затереть уже обнулённую строку.
                    if (state != State.LISTENING) return@runOnUiThread
                    setStatus(text.ifBlank { activity.getString(R.string.journal_mic_status_listening) })
                }
            }

            override fun onFinalText(text: String) {
                android.util.Log.d(logTag, "final: \"$text\"")
                activity.runOnUiThread {
                    if (state != State.LISTENING) return@runOnUiThread
                    if (!replacedThisSession && replaceFirstSegment()) {
                        replaceText(text)
                        replacedThisSession = true
                    } else {
                        appendText(text)
                    }
                }
            }

            override fun onError(message: String) {
                android.util.Log.d(logTag, "error: $message")
                activity.runOnUiThread {
                    setStatus(activity.getString(R.string.journal_mic_status_error))
                    stop()
                }
            }
        })
    }

    /** Первый сегмент сессии заменяет прежнее содержимое поля целиком, последующие — дописываются. */
    private var replacedThisSession = false

    private fun replaceText(text: String) {
        if (text.isBlank()) return
        editText.setText(text)
    }

    private fun appendText(text: String) {
        if (text.isBlank()) return
        val current = editText.text?.toString().orEmpty()
        val separator = if (current.isNotEmpty() && !current.endsWith(" ") && !current.endsWith("\n")) " " else ""
        editText.append(separator + text)
    }
}
