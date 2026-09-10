package com.malto4.pipdroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.media.MediaPlayer
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.malto4.pipdroid.databinding.ActivityMainBinding
import java.util.Random

/** Театральная анимация включения и выключения экрана PipBoy плюс фоновый глитч. */
/** Наружу ходит только за биндингом, общим handler и акцентом темы; всё, что происходит
 * ПОСЛЕ загрузки (строка 2, курсор энкодера, эмбиент), отдано в колбэки — контроллер про это
 * не знает. */
internal class BootSequenceController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val handler: Handler,
    private val accentColor: () -> Int,
    private val onGlareShouldUpdate: () -> Unit,
    private val onBootFinished: () -> Unit,
    private val ambient: (start: Boolean) -> Unit,
) {
    // ===== АНИМАЦИЯ ВКЛЮЧЕНИЯ =====
    private var bootSoundPlayer: MediaPlayer? = null
    // Токен позволяет оборвать именно шаги анимации, не трогая прочие задачи на том же handler.
    private val bootSequenceToken = Any()

    private fun bootPostDelayed(delayMs: Long, action: () -> Unit) {
        handler.postAtTime(Runnable { action() }, bootSequenceToken, SystemClock.uptimeMillis() + delayMs)
    }

    /** Кадр 1 -> кадр 2 -> кадр 3 -> основной интерфейс; реальный POWER:1 и debug-инъекция ведут сюда одинаково. */
    fun playBootSequence() {
        playBootSwitchSound()
        val boot = binding.incLayoutBootSequence
        val accent = accentColor()
        // Лого кадра 1 — PNG с альфа-маской, своей раскраски кодом не требует.
        boot.tvBootCodewall.setTextColor(accent)
        boot.tvBootTerminal.setTextColor(accent)

        binding.viewPowerOff.visibility = View.GONE
        onGlareShouldUpdate()
        boot.root.visibility = View.VISIBLE
        boot.layoutBootFrameLogo.visibility = View.VISIBLE
        boot.layoutBootFrameCodewall.visibility = View.GONE
        boot.layoutBootFrameTerminal.visibility = View.GONE

        bootPostDelayed(BOOT_FRAME_LOGO_DURATION_MS) { startBootCodewall() }
        scheduleGlitchPulses(bootTotalDurationMs, BOOT_GLITCH_MIN_PULSES..BOOT_GLITCH_MAX_PULSES)
    }

    /** Суммарная длительность заставки считается, а не хардкодится — она зависит от ширины экрана. */
    private val bootTotalDurationMs: Long
        get() = BOOT_FRAME_LOGO_DURATION_MS + BOOT_FRAME_CODEWALL_DURATION_MS +
            buildBootTerminalText().length * BOOT_TERMINAL_CHAR_DELAY_MS + BOOT_TERMINAL_END_HOLD_MS

    private fun startBootCodewall() {
        val boot = binding.incLayoutBootSequence
        boot.layoutBootFrameLogo.visibility = View.GONE
        boot.layoutBootFrameCodewall.visibility = View.VISIBLE
        boot.tvBootCodewall.text = BOOT_CODEWALL_TEXT
        startBootSound()

        // Ждём проход layout: реальная высота кадра на разных экранах разная.
        boot.tvBootCodewall.post {
            val frameHeight = boot.layoutBootFrameCodewall.height.toFloat()
            val textHeight = boot.tvBootCodewall.height.toFloat()
            boot.tvBootCodewall.translationY = frameHeight
            boot.tvBootCodewall.animate()
                .translationY(-textHeight)
                .setDuration(BOOT_FRAME_CODEWALL_DURATION_MS)
                .setInterpolator(LinearInterpolator())
                .start()
        }

        bootPostDelayed(BOOT_FRAME_CODEWALL_DURATION_MS) { startBootTerminal() }
    }

    private fun startBootTerminal() {
        val boot = binding.incLayoutBootSequence
        boot.tvBootCodewall.animate().cancel()
        boot.layoutBootFrameCodewall.visibility = View.GONE
        boot.layoutBootFrameTerminal.visibility = View.VISIBLE
        typeTerminalText("", buildBootTerminalText(), 0, boot.tvBootTerminal) {
            bootPostDelayed(BOOT_TERMINAL_END_HOLD_MS) { finishBootSequence() }
        }
    }

    /** Баннер PIP-OS с числом звёздочек по фактической ширине терминала. */
    /** Первая прикидка — по ширине одного "*", но окончательная проверка измеряет собранную строку
     * целиком: measureText() одного символа расходится с реальной шириной строки той же длины. */
    private fun buildPipOsBanner(): String {
        val boot = binding.incLayoutBootSequence
        val paint = boot.tvBootTerminal.paint
        val marginsPx = 2 * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, activity.resources.displayMetrics)
        val availableWidthPx = (
            binding.root.width - binding.root.paddingLeft - binding.root.paddingRight - marginsPx
        ).coerceAtLeast(0f)
        fun assemble(totalChars: Int): String {
            val starsTotal = (totalChars - PIP_OS_LABEL.length).coerceAtLeast(0)
            val starsLeft = starsTotal / 2
            val starsRight = starsTotal - starsLeft
            return "*".repeat(starsLeft) + PIP_OS_LABEL + "*".repeat(starsRight)
        }
        val charWidthPx = paint.measureText("*").coerceAtLeast(1f)
        var totalChars = (availableWidthPx / charWidthPx).toInt().coerceAtLeast(PIP_OS_LABEL.length)
        while (totalChars > PIP_OS_LABEL.length && paint.measureText(assemble(totalChars)) > availableWidthPx) {
            totalChars--
        }
        return assemble(totalChars)
    }
    private fun buildBootTerminalText(): String = buildPipOsBanner() + "\n\n" + BOOT_TERMINAL_INFO_TEXT
    private fun buildShutdownHeaderPrefix(): String = buildPipOsBanner() + "\n\n"

    /** Посимвольная печать с блочным курсором; [prefix] выводится целиком сразу, печатается только [body]. */
    private fun typeTerminalText(prefix: String, body: String, charIndex: Int, tv: TextView, onDone: () -> Unit) {
        if (charIndex >= body.length) {
            tv.text = prefix + body + BOOT_CURSOR_CHAR
            onDone()
            return
        }
        tv.text = prefix + body.substring(0, charIndex + 1) + BOOT_CURSOR_CHAR
        bootPostDelayed(BOOT_TERMINAL_CHAR_DELAY_MS) { typeTerminalText(prefix, body, charIndex + 1, tv, onDone) }
    }

    private fun startBootSound() {
        bootSoundPlayer?.release()
        bootSoundPlayer = MediaPlayer.create(activity, R.raw.boot_typing_click)?.apply {
            isLooping = true
            start()
        }
    }

    private fun stopBootSound() {
        bootSoundPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (e: IllegalStateException) {
                Log.w("BootSequence", "MediaPlayer уже был в неподходящем состоянии для stop()", e)
            }
            player.release()
        }
        bootSoundPlayer = null
    }

    private var bootSwitchSoundPlayer: MediaPlayer? = null

    /** Одноразовый звук POWER:1, сам себя освобождает по завершении. */
    private fun playBootSwitchSound() {
        bootSwitchSoundPlayer?.release()
        bootSwitchSoundPlayer = MediaPlayer.create(activity, R.raw.ui_switch_on)?.apply {
            setOnCompletionListener {
                it.release()
                bootSwitchSoundPlayer = null
            }
            start()
        }
    }

    private fun stopBootSwitchSound() {
        bootSwitchSoundPlayer?.release()
        bootSwitchSoundPlayer = null
    }

    private fun finishBootSequence() {
        stopBootSound()
        binding.incLayoutBootSequence.root.visibility = View.GONE
        onBootFinished()
        // Глитч не ограничен окном после загрузки — фоновый эффект на всё время, пока PipBoy включён.
        startContinuousGlitch()
        ambient(true)
    }

    /** Обрывает анимацию на любом шаге; стартовое состояние view_power_off готовит следующий play*. */
    fun cancelBootSequence() {
        handler.removeCallbacksAndMessages(bootSequenceToken)
        val boot = binding.incLayoutBootSequence
        boot.tvBootCodewall.animate().cancel()
        boot.root.animate().cancel()
        boot.root.alpha = 1f
        binding.viewPowerOff.animate().cancel()
        stopBootSound()
        stopBootSwitchSound()
        stopShutdownSwitchSound()
        boot.root.visibility = View.GONE
        binding.ivGlitchOverlay.visibility = View.GONE
        binding.ivGlitchOverlay.setImageDrawable(null)
    }

    // ===== АНИМАЦИЯ ВЫКЛЮЧЕНИЯ =====
    private var shutdownSwitchSoundPlayer: MediaPlayer? = null

    /** Щелчок выключения сразу, затем ~2с на текущем экране с импульсами глитча. */
    fun playShutdownSequence() {
        playShutdownSwitchSound()
        ambient(false)
        scheduleGlitchPulses(SHUTDOWN_STAY_DURATION_MS, POST_BOOT_GLITCH_MIN_PULSES..POST_BOOT_GLITCH_MAX_PULSES)
        bootPostDelayed(SHUTDOWN_STAY_DURATION_MS) { fadeToShutdownTerminal() }
    }

    private fun playShutdownSwitchSound() {
        shutdownSwitchSoundPlayer?.release()
        shutdownSwitchSoundPlayer = MediaPlayer.create(activity, R.raw.ui_switch_off)?.apply {
            setOnCompletionListener {
                it.release()
                shutdownSwitchSoundPlayer = null
            }
            start()
        }
    }

    private fun stopShutdownSwitchSound() {
        shutdownSwitchSoundPlayer?.release()
        shutdownSwitchSoundPlayer = null
    }

    /** Экран гаснет в чёрное, и только на полностью чёрном фоне появляется терминал выключения. */
    private fun fadeToShutdownTerminal() {
        val overlay = binding.viewPowerOff
        overlay.animate().cancel()
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        onGlareShouldUpdate()
        overlay.animate()
            .alpha(1f)
            .setDuration(SHUTDOWN_FADE_TO_BLACK_MS)
            .withEndAction { startShutdownTerminal() }
            .start()
    }

    /** Шапка PIP-OS выводится сразу, тело печатается посимвольно под тот же звук набора. */
    private fun startShutdownTerminal() {
        val boot = binding.incLayoutBootSequence
        boot.tvBootTerminal.setTextColor(accentColor())
        boot.root.alpha = 1f
        boot.root.visibility = View.VISIBLE
        boot.layoutBootFrameLogo.visibility = View.GONE
        boot.layoutBootFrameCodewall.visibility = View.GONE
        boot.layoutBootFrameTerminal.visibility = View.VISIBLE

        startBootSound()
        typeTerminalText(buildShutdownHeaderPrefix(), SHUTDOWN_BODY_TEXT, 0, boot.tvBootTerminal) {
            stopBootSound()
            bootPostDelayed(BOOT_TERMINAL_END_HOLD_MS) { finishShutdownSequence() }
        }
    }

    /** Терминал гаснет, оставляя обычное состояние OFF. */
    private fun finishShutdownSequence() {
        val boot = binding.incLayoutBootSequence
        boot.root.animate()
            .alpha(0f)
            .setDuration(SHUTDOWN_FINAL_FADE_MS)
            .withEndAction {
                boot.root.visibility = View.GONE
                boot.root.alpha = 1f
            }
            .start()
    }

    // ===== ГЛИТЧ-ЭФФЕКТ =====
    private val glitchRandom = Random()
    private fun glitchRandomInt(minInclusive: Int, maxExclusive: Int): Int =
        minInclusive + glitchRandom.nextInt(maxExclusive - minInclusive)

    // Матрицы хроматической аберрации: оставляют один канал, остальные обнуляют.
    private val glitchRedChannelMatrix = ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
    private val glitchBlueChannelMatrix = ColorMatrix(floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    /** Раскидывает случайные импульсы глитча по окну; живёт на bootSequenceToken и рвётся вместе с анимацией. */
    private fun scheduleGlitchPulses(windowDurationMs: Long, pulseCountRange: IntRange) {
        val count = glitchRandomInt(pulseCountRange.first, pulseCountRange.last + 1)
        val latestStart = (windowDurationMs - GLITCH_PULSE_MAX_MS).coerceAtLeast(1L).toInt()
        repeat(count) {
            val triggerAt = glitchRandomInt(0, latestStart).toLong()
            bootPostDelayed(triggerAt) { triggerGlitchPulse() }
        }
    }

    /** Фоновый глитч, пока PipBoy включён: каждый импульс сам планирует следующий, отдельного стопа не нужно. */
    fun startContinuousGlitch() {
        val delay = glitchRandomInt(AMBIENT_GLITCH_MIN_INTERVAL_MS, AMBIENT_GLITCH_MAX_INTERVAL_MS).toLong()
        bootPostDelayed(delay) {
            triggerGlitchPulse()
            startContinuousGlitch()
        }
    }

    /** Импульс: снимок экрана -> та же картинка со сдвигом полос и аберрацией -> короткий показ -> назад. */
    private fun triggerGlitchPulse() {
        val root = binding.root
        if (root.width <= 0 || root.height <= 0) return
        val snapshot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(snapshot))
        val glitched = buildGlitchBitmap(snapshot)
        snapshot.recycle()

        val overlay = binding.ivGlitchOverlay
        // Тинт темы сбрасываем: ImageView показывает полноцветный снимок экрана, SRC_IN залил бы весь кадр.
        overlay.imageTintList = null
        overlay.setImageBitmap(glitched)
        overlay.visibility = View.VISIBLE
        val pulseDuration = glitchRandomInt(GLITCH_PULSE_MIN_MS, GLITCH_PULSE_MAX_MS).toLong()
        bootPostDelayed(pulseDuration) {
            overlay.visibility = View.GONE
            overlay.setImageDrawable(null)
            glitched.recycle()
        }
    }

    /** Полосный сдвиг и аберрация только через Canvas.drawBitmap, без пиксельных циклов. */
    private fun buildGlitchBitmap(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val bandCount = glitchRandomInt(7, 16)
        val bandHeight = (source.height / bandCount).coerceAtLeast(1)
        for (i in 0 until bandCount) {
            if (glitchRandom.nextFloat() > 0.5f) continue
            val top = i * bandHeight
            val bottom = if (i == bandCount - 1) source.height else (top + bandHeight)
            val offset = glitchRandomInt(-50, 50)
            canvas.drawBitmap(
                source,
                Rect(0, top, source.width, bottom),
                Rect(offset, top, source.width + offset, bottom),
                null
            )
        }

        repeat(glitchRandomInt(1, 4)) {
            val bandH = glitchRandomInt(6, 26)
            val top = glitchRandomInt(0, (source.height - bandH).coerceAtLeast(1))
            val bottom = (top + bandH).coerceAtMost(source.height)
            val shift = glitchRandomInt(8, 22)
            val srcRect = Rect(0, top, source.width, bottom)

            val redPaint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(glitchRedChannelMatrix)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            val bluePaint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(glitchBlueChannelMatrix)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            canvas.drawBitmap(source, srcRect, Rect(shift, top, source.width + shift, bottom), redPaint)
            canvas.drawBitmap(source, srcRect, Rect(-shift, top, source.width - shift, bottom), bluePaint)
        }

        return result
    }


    companion object {
        // Анимация включения (roadmap, "Видение приложения", п.11). См. playBootSequence().
        private const val BOOT_FRAME_LOGO_DURATION_MS = 2500L
        private const val BOOT_FRAME_CODEWALL_DURATION_MS = 2000L
        private const val BOOT_TERMINAL_CHAR_DELAY_MS = 30L
        private const val BOOT_TERMINAL_END_HOLD_MS = 600L
        private const val BOOT_CURSOR_CHAR = "█" // █ — блочный курсор кадра 3
        // Флейвор-текст "стены кода"
        private const val BOOT_CODEWALL_BLOCK = "* 1 0 0x0000A4 0x0000000000000000 start memory discovery\n" +
            "0 0x0000A4 0x0000000000000000 1 0 0x000014 0x0000000000000000 CPU0 starting cell\n" +
            "relocation0 0x0000A4 0x0000000000000000 1 0 0x000009 0x0000000000000000\n" +
            "CPU0 launch EFI0 0x0000A4 0x0000000000000000 1 0 0x000009 0x00000000000E003D\n" +
            "CPU0 starting EFI0 0x0000A4 0x0000000000000000 1 0 0x0000A4 0x0000000000000000\n"
        private val BOOT_CODEWALL_TEXT = BOOT_CODEWALL_BLOCK.repeat(24)
        // Баннер PIP-OS без звёздочек — их число считает buildPipOsBanner() под ширину экрана.
        private const val PIP_OS_LABEL = " PIP-OS(R) V7.1.0.8 "
        // Текст терминала после баннера; сам баннер собирает buildBootTerminalText().
        private const val BOOT_TERMINAL_INFO_TEXT =
            "COPYRIGHT 2075 ROBCO(R)\n" +
            "LOADER V1.1\n" +
            "EXEC VERSION 41.10\n" +
            "64k RAM SYSTEM\n" +
            "38911 BYTES FREE\n" +
            "NO HOLOTAPE FOUND\n" +
            "LOAD ROM(1): DEITRIX 303"

        // Глитч-эффект
        private const val BOOT_GLITCH_MIN_PULSES = 5
        private const val BOOT_GLITCH_MAX_PULSES = 8
        private const val POST_BOOT_GLITCH_MIN_PULSES = 4
        private const val POST_BOOT_GLITCH_MAX_PULSES = 6
        private const val GLITCH_PULSE_MIN_MS = 60
        private const val GLITCH_PULSE_MAX_MS = 150
        private const val AMBIENT_GLITCH_MIN_INTERVAL_MS = 4000
        private const val AMBIENT_GLITCH_MAX_INTERVAL_MS = 12000

        // Анимация выключения
        private const val SHUTDOWN_STAY_DURATION_MS = 2000L
        private const val SHUTDOWN_FADE_TO_BLACK_MS = 500L
        private const val SHUTDOWN_FINAL_FADE_MS = 500L
        // Тело терминала выключения; шапку собирает buildShutdownHeaderPrefix().
        private const val SHUTDOWN_BODY_TEXT = "STOPPING ALL PROCESSES...\n" +
            "DUMPING MEMORY...\n" +
            "DISCONNECTING..."
    }
}
