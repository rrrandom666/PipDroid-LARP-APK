package com.malto4.pipdroid

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.malto4.pipdroid.databinding.ActivityMainBinding
import java.util.Calendar

/** Экран ITEMS/Часы целиком: время, будильник, таймер, секундомер, мелодия звонка. */
/** Контроллер владеет состоянием экрана, его боковым меню, деревом энкодера и оверлеем срабатывания;
 * MainActivity остаётся слоем навигации. Общий таймер делится с системой ранений — она видна отсюда
 * только через колбэки isWoundActive/timerLabelText/onWound*, самой фазы ранения контроллер не знает. */
internal class ClockController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val prefs: SharedPreferences,
    private val navigator: MenuNavigator,
    private val mode: () -> PipBoyMode,
    private val accentColor: () -> Int,
    private val selectedButtonRes: () -> Int,
    private val scrollbarThumbRes: () -> Int,
    private val itemsMenuRoot: () -> List<MenuNode>,
    private val backSidebarItem: () -> SidebarMenuItem<String>,
    private val menuBackNode: (onHighlight: () -> Unit, onBeforePop: () -> Unit) -> List<MenuNode>,
    private val playTick: () -> Unit,
    private val playButton: () -> Unit,
    private val playConfirm: () -> Unit,
    private val playError: () -> Unit,
    private val suppressTickAround: (block: () -> Unit) -> Unit,
    private val syncRow2Active: () -> Unit,
    private val hasAudioPermission: () -> Boolean,
    private val requestAudioPermission: () -> Unit,
    private val isWoundActive: () -> Boolean,
    private val timerLabelText: () -> String,
    private val onWoundTimerFired: () -> Unit,
    private val onWoundStopRequested: () -> Unit,
    private val onWoundCountdownTick: (remainingSeconds: Int) -> Unit,
) {
    private var alarmHour = 7
    private var alarmMinute = 0
    private var alarmArmed = false
    private var clockFiredRingtonePlayer: MediaPlayer? = null
    private lateinit var alarmHourWheel: ClockWheelPicker
    private lateinit var alarmMinuteWheel: ClockWheelPicker
    private enum class TimerState { IDLE, RUNNING, PAUSED }
    private var timerHours = 0
    private var timerMinutes = 5
    private var timerSeconds = 0
    private var timerState = TimerState.IDLE
    private lateinit var timerHourWheel: ClockWheelPicker
    private lateinit var timerMinuteWheel: ClockWheelPicker
    private lateinit var timerSecondWheel: ClockWheelPicker
    private var timerTargetEpochMillis = 0L
    private var timerRemainingSecondsAtPause = 0
    private var timerPauseAllowed = true
    private enum class StopwatchState { IDLE, RUNNING, PAUSED }
    private var stopwatchState = StopwatchState.IDLE
    private var stopwatchStartEpochMillis = 0L
    private var stopwatchElapsedMillisAtPause = 0L
    private val selectedRingtone_SPKey = "selectedRingtoneIndex"
    private var melodyFocusedIndex = 0
    private var melodyPreviewPlayer: MediaPlayer? = null
    private var melodyPreviewPlayingIndex: Int? = null
    /** payload — индекс в ringtoneTracks. */
    private lateinit var melodyAdapter: SidebarMenuAdapter<Int?>
    private data class ClockFeatureMeta(val key: String, val labelRes: Int)
    private val clockMeta = listOf(
        ClockFeatureMeta("TIME", R.string.clock_feature_time),
        ClockFeatureMeta("ALARM", R.string.clock_feature_alarm),
        ClockFeatureMeta("TIMER", R.string.clock_feature_timer),
        ClockFeatureMeta("STOPWATCH", R.string.clock_feature_stopwatch),
        ClockFeatureMeta("MELODY", R.string.clock_feature_melody),
    )
    private lateinit var clockAdapter: SidebarMenuAdapter<String>

    // ===== ПУБЛИЧНАЯ ПОВЕРХНОСТЬ =====

    /** Ветка CLOCK дерева энкодера — её строит itemsMenuRoot() активности. */
    fun childrenNodes(): List<MenuNode> = clockChildrenNodes()

    /** Общий 300мс-цикл активности: свои часы, будильник, таймер и секундомер экран обновляет отсюда. */
    fun onTick(gameCalendar: Calendar, timeHHmm: String, timeSs: String) {
        binding.incLayoutTabItemsClock.incLayoutTabItemsClockTime.tvClockTimeHm.text = timeHHmm
        binding.incLayoutTabItemsClock.incLayoutTabItemsClockTime.tvClockTimeS.text = timeSs
        checkAlarmFiring(gameCalendar)
        checkTimerFiring()
        updateStopwatchDisplay()
    }

    /** Режим стал известен после onCreate(): пересобираем боковой список и кнопки "назад". */
    fun refreshModeGating() {
        clockAdapter.setItems(clockSidebarItems(), resetSelection = false)
        refreshClockAlarmBackButtonVisibility()
        refreshClockTimerBackButtonsVisibility()
        refreshClockStopwatchBackButtonVisibility()
        refreshClockMelodyBackButtonVisibility()
    }

    /** Курсор ещё на узле CLOCK и не провалился в боковое меню — рамку гасим целиком. */
    fun clearSidebarSelection() = clockAdapter.clearSelection()

    /** Голосовая команда открывает конкретный пункт часов тем же путём, что тап по нему. */
    fun selectFeature(key: String) = clockAdapter.selectPosition(clockRootIndex(key))

    /** Оверлей срабатывания глобальный, вне дерева MenuNavigator: пока он виден, ENCBTN закрывает именно его. */
    val isFiredOverlayVisible: Boolean
        get() = binding.incLayoutClockFiredOverlay.root.visibility == View.VISIBLE

    fun activateFiredOverlayStop() {
        flashButtonPressThenRun(binding.incLayoutClockFiredOverlay.btnClockFiredStop) {
            playButton()
            dismissClockFiredOverlay()
        }
    }

    val isTimerIdle: Boolean
        get() = timerState == TimerState.IDLE

    /** Таймер ранения занимает тот же общий таймер, что и запуск с экрана Таймера. */
    fun startWoundTimer(durationSeconds: Int) {
        timerState = TimerState.RUNNING
        timerTargetEpochMillis = System.currentTimeMillis() + durationSeconds * 1000L
        syncClockTimerScreenVisibility()
        updateClockTimerLabel()
    }

    /** Ранение закончилось снаружи (вылечен, погиб) — общий таймер снимается без оверлея срабатывания. */
    fun stopTimer() {
        timerState = TimerState.IDLE
        syncClockTimerScreenVisibility()
    }

    /** Отладочный [Пропустить] на STATUS — доводит отсчёт до нуля обычным путём. */
    fun skipTimerToEnd() {
        if (timerState != TimerState.RUNNING) return
        timerTargetEpochMillis = System.currentTimeMillis()
        checkTimerFiring()
    }

    /** Таймер ранения нельзя ставить на паузу. Затенение — только визуальное, как у пунктов
     * бокового меню: тап доезжает до обработчика и отвечает звуком ошибки. */
    fun setTimerPauseAllowed(allowed: Boolean) {
        timerPauseAllowed = allowed
        binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.btnClockTimerPauseResume.alpha =
            if (allowed) 1.0f else 0.4f
    }

    fun saveState(outState: Bundle) {
        outState.putString(KEY_TIMER_STATE, timerState.name)
        outState.putLong(KEY_TIMER_TARGET_EPOCH, timerTargetEpochMillis)
        outState.putInt(KEY_TIMER_REMAINING_AT_PAUSE, timerRemainingSecondsAtPause)
    }

    /** Истёкший за время простоя таймер обработает обычный следующий тик 300мс-цикла. */
    fun restoreState(savedInstanceState: Bundle) {
        timerState = try {
            TimerState.valueOf(savedInstanceState.getString(KEY_TIMER_STATE, TimerState.IDLE.name))
        } catch (e: IllegalArgumentException) { TimerState.IDLE }
        timerTargetEpochMillis = savedInstanceState.getLong(KEY_TIMER_TARGET_EPOCH)
        timerRemainingSecondsAtPause = savedInstanceState.getInt(KEY_TIMER_REMAINING_AT_PAUSE)
        syncClockTimerScreenVisibility()
        updateClockTimerLabel()
    }

    // ===== ПОДКЛЮЧЕНИЕ ЭКРАНА =====

    /** Единственная точка входа из onCreate(); порядок блоков внутри тот же, что был в активности. */
    fun setup() {
        val clock = binding.incLayoutTabItemsClock
        clockAdapter = SidebarMenuAdapter(
            items = clockSidebarItems(),
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация плюс один звук на весь тап — тик из onHighlight цели глушится.
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // Путь до самого узла CLOCK — тот же смысл, что popLevel(), но не зависит от прежней позиции курсора.
                    playConfirm()
                    syncClockEncoderPath(emptyList())
                    syncRow2Active()
                } else if (item.payload == "MELODY") {
                    // "+ 0" — тап равносилен ENCBTN на MELODY: курсор садится на первый трек, и его onHighlight сам откроет экран.
                    playConfirm()
                    suppressTickAround { syncClockEncoderPath(listOf(position, 0)) }
                } else if (item.payload == "TIME") {
                    // Единственный лист без детей — проваливаться некуда, курсор остаётся на пункте.
                    playConfirm()
                    suppressTickAround { syncClockEncoderPath(listOf(position)) }
                    showClockContentPanel(item.payload)
                } else {
                    // ALARM/TIMER/STOPWATCH — тоже "+ 0": ожидание игрока попасть сразу на первый орган управления.
                    playConfirm()
                    suppressTickAround { syncClockEncoderPath(listOf(position, 0)) }
                    showClockContentPanel(item.payload)
                }
            },
        )
        clock.incLayoutTabItemsClockButtons.recyclerTabItemsClockButtons.layoutManager = LinearLayoutManager(activity)
        clock.incLayoutTabItemsClockButtons.recyclerTabItemsClockButtons.adapter = clockAdapter

        // ===== БУДИЛЬНИК =====
        val clockAccentTint = ColorStateList.valueOf(accentColor())
        val alarm = clock.incLayoutTabItemsClockAlarm
        alarm.btnClockAlarmToggle.backgroundTintList = clockAccentTint
        alarm.btnClockAlarmBack.backgroundTintList = clockAccentTint
        // Была текстовая кнопка, стала иконкой — тот же сброс imageTintList.
        alarm.btnClockAlarmBack.imageTintList = null
        alarm.viewClockAlarmHourFocus.backgroundTintList = clockAccentTint
        alarm.viewClockAlarmMinuteFocus.backgroundTintList = clockAccentTint
        alarm.viewClockAlarmSetFocus.backgroundTintList = clockAccentTint
        alarm.viewClockAlarmBackFocus.backgroundTintList = clockAccentTint
        updateAlarmStatusViews()

        alarmHourWheel = ClockWheelPicker(
            alarm.rvClockAlarmHour, 0..23, alarmHour,
            onValueSettled = { value -> alarmHour = value; updateAlarmStatusViews() },
            // Свайп по колесу подтягивает курсор энкодера на HOUR из любой ветки дерева.
            onUserAdjusted = { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 0)) },
        )
        alarmMinuteWheel = ClockWheelPicker(
            alarm.rvClockAlarmMinute, 0..59, alarmMinute,
            onValueSettled = { value -> alarmMinute = value; updateAlarmStatusViews() },
            onUserAdjusted = { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 1)) },
        )
        alarm.btnClockAlarmToggle.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 2)) }
            playButton()
            toggleAlarmArmed()
        }
        alarm.btnClockAlarmBack.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 3)) }
            playConfirm()
            setClockAlarmBackFocused(false)
            navigator.popLevel()
        }
        refreshClockAlarmBackButtonVisibility()

        // ===== ТАЙМЕР =====
        // Setup и running — вложенные ConstraintLayout, а не <include>: ViewBinding кладёт их id плоско.
        val timer = clock.incLayoutTabItemsClockTimer
        for (btn in listOf(timer.btnClockTimerPreset5, timer.btnClockTimerPreset10, timer.btnClockTimerStart,
            timer.btnClockTimerPauseResume, timer.btnClockTimerReset, timer.btnClockTimerSetupBack, timer.btnClockTimerRunningBack)) {
            btn.backgroundTintList = clockAccentTint
        }
        // Были текстовыми кнопками, стали иконками — тот же сброс imageTintList.
        timer.btnClockTimerSetupBack.imageTintList = null
        timer.btnClockTimerRunningBack.imageTintList = null
        for (view in listOf(timer.viewClockTimerHourFocus, timer.viewClockTimerMinuteFocus, timer.viewClockTimerSecondFocus,
            timer.viewClockTimerPreset5Focus, timer.viewClockTimerPreset10Focus, timer.viewClockTimerStartFocus, timer.viewClockTimerSetupBackFocus,
            timer.viewClockTimerPauseResumeFocus, timer.viewClockTimerResetFocus, timer.viewClockTimerRunningBackFocus)) {
            view.backgroundTintList = clockAccentTint
        }

        timerHourWheel = ClockWheelPicker(
            timer.rvClockTimerHour, 0..23, timerHours,
            onValueSettled = { timerHours = it },
            onUserAdjusted = { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 0)) },
        )
        timerMinuteWheel = ClockWheelPicker(
            timer.rvClockTimerMinute, 0..59, timerMinutes,
            onValueSettled = { timerMinutes = it },
            onUserAdjusted = { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 1)) },
        )
        timerSecondWheel = ClockWheelPicker(
            timer.rvClockTimerSecond, 0..59, timerSeconds,
            onValueSettled = { timerSeconds = it },
            onUserAdjusted = { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 2)) },
        )

        timer.btnClockTimerPreset5.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 3)) }
            playButton()
            addTimerPresetMinutes(5)
        }
        timer.btnClockTimerPreset10.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 4)) }
            playButton()
            addTimerPresetMinutes(10)
        }
        timer.btnClockTimerStart.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 5)) }
            playButton()
            startPlainTimer(timerHours * 3600 + timerMinutes * 60 + timerSeconds)
        }
        timer.btnClockTimerSetupBack.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 6)) }
            playConfirm()
            setClockTimerSetupBackFocused(false)
            navigator.popLevel()
        }
        timer.btnClockTimerPauseResume.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 0)) }
            requestPauseResume()
        }
        timer.btnClockTimerReset.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 1)) }
            playButton()
            resetTimer()
        }
        timer.btnClockTimerRunningBack.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 2)) }
            playConfirm()
            setClockTimerRunningBackFocused(false)
            navigator.popLevel()
        }
        refreshClockTimerBackButtonsVisibility()

        // ===== СЕКУНДОМЕР =====
        val stopwatch = clock.incLayoutTabItemsClockStopwatch
        stopwatch.btnClockStopwatchStartPause.backgroundTintList = clockAccentTint
        stopwatch.btnClockStopwatchReset.backgroundTintList = clockAccentTint
        stopwatch.btnClockStopwatchBack.backgroundTintList = clockAccentTint
        // Была текстовая кнопка, заменена на иконку (roadmap, этап 29).
        stopwatch.btnClockStopwatchBack.imageTintList = null
        stopwatch.viewClockStopwatchStartPauseFocus.backgroundTintList = clockAccentTint
        stopwatch.viewClockStopwatchResetFocus.backgroundTintList = clockAccentTint
        stopwatch.viewClockStopwatchBackFocus.backgroundTintList = clockAccentTint

        stopwatch.btnClockStopwatchStartPause.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 0)) }
            playButton()
            toggleStopwatchStartPause()
        }
        stopwatch.btnClockStopwatchReset.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 1)) }
            playButton()
            resetStopwatch()
        }
        stopwatch.btnClockStopwatchBack.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 2)) }
            playConfirm()
            setClockStopwatchBackFocused(false)
            navigator.popLevel()
        }
        refreshClockStopwatchBackButtonVisibility()

        // ===== МЕЛОДИЯ ЗВОНКА =====
        val melody = clock.incLayoutTabItemsClockMelody
        melody.btnClockMelodySelect.backgroundTintList = clockAccentTint
        melody.btnClockMelodyBack.backgroundTintList = clockAccentTint
        // Была текстовая кнопка, заменена на иконку (roadmap, этап 29).
        melody.btnClockMelodyBack.imageTintList = null
        melody.viewClockMelodySelectFocus.backgroundTintList = clockAccentTint
        melody.viewClockMelodyBackFocus.backgroundTintList = clockAccentTint
        // applyTextColor() эту LineVisualizer не красит — без явного setColor() линия сливается с фоном.
        melody.melodyWave.setColor(accentColor())
        melodyFocusedIndex = prefs.getInt(selectedRingtone_SPKey, 0)

        // [Назад] — обычный последний пункт того же списка, не отдельная кнопка.
        val melodyItems: List<SidebarMenuItem<Int?>> = ringtoneTracks.indices.map { index ->
            SidebarMenuItem<Int?>(payload = index, label = ringtoneTracks[index].displayName)
        } + SidebarMenuItem(payload = null, label = activity.getString(R.string.wizard_back))
        melodyAdapter = SidebarMenuAdapter(
            items = melodyItems,
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            initialSelectedPosition = melodyFocusedIndex,
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                val index = item.payload
                playConfirm()
                if (index == null) {
                    // Назад — безусловно на сам узел: тач мог случиться из любой ветки.
                    suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("MELODY"), position)) }
                    navigator.popLevel()
                    closeClockMelodyScreen()
                } else {
                    // Глубину читаем ДО обновления melodyFocusedIndex, иначе искали бы уже новый трек вместо прежнего.
                    val childDepth = navigator.cursorIfParent("TRACK_$melodyFocusedIndex")
                    if (childDepth != null) {
                        // Цель — Select/Back нового трека: onHighlight красит прицел, глушим только звук.
                        suppressTickAround {
                            syncClockEncoderPath(listOf(clockRootIndex("MELODY"), position, childDepth))
                        }
                    } else {
                        // Полностью тихий путь: onHighlight трека сам запускает превью и конфликтует с тумблером ниже.
                        syncClockEncoderPathSilently(listOf(clockRootIndex("MELODY"), position))
                    }
                    melodyFocusedIndex = index
                    if (melodyPreviewPlayingIndex == index) stopMelodyPreview() else startMelodyPreview(index)
                }
            },
        )
        melody.recyclerClockMelodyTracks.layoutManager = LinearLayoutManager(activity)
        melody.recyclerClockMelodyTracks.adapter = melodyAdapter

        melody.btnClockMelodySelect.setOnClickListener {
            // Цель сама играет тик в onHighlight и задвоила бы звук кнопки.
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("MELODY"), melodyFocusedIndex, 0)) }
            playButton()
            commitMelodySelection()
        }
        melody.btnClockMelodyBack.setOnClickListener {
            suppressTickAround { syncClockEncoderPath(listOf(clockRootIndex("MELODY"), melodyFocusedIndex, 1)) }
            playConfirm()
            setClockMelodyBackFocused(false)
            navigator.popLevel()
        }
        refreshClockMelodyBackButtonVisibility()

        binding.incLayoutClockFiredOverlay.btnClockFiredStop.backgroundTintList = clockAccentTint
        binding.incLayoutClockFiredOverlay.viewClockFiredStopFocus.backgroundTintList = clockAccentTint
        binding.incLayoutClockFiredOverlay.btnClockFiredStop.setOnClickListener {
            playButton()
            dismissClockFiredOverlay()
        }
    }

    // ===== ПРИЦЕЛЫ ЭНКОДЕРА =====
    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) =
        applyFocusBrackets(bracketsView, mode(), visible)
    private fun setEncoderOnlyVisible(vararg views: View) = applyEncoderOnlyVisible(mode(), *views)

    /** Прицелы Будильника — часы/минуты/Set/Back. */
    private fun setClockAlarmHourFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmHourFocus, focused)
    }
    private fun setClockAlarmMinuteFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmMinuteFocus, focused)
    }
    private fun setClockAlarmSetFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmSetFocus, focused)
    }
    private fun setClockAlarmBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmBackFocus, focused)
    }
    private fun setAllClockAlarmFocusesHidden() {
        setClockAlarmHourFocused(false)
        setClockAlarmMinuteFocused(false)
        setClockAlarmSetFocused(false)
        setClockAlarmBackFocused(false)
    }
    /** Прицелы Таймера, панель настройки. */
    private fun setClockTimerHourFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerHourFocus, focused)
    }
    private fun setClockTimerMinuteFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerMinuteFocus, focused)
    }
    private fun setClockTimerSecondFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerSecondFocus, focused)
    }
    private fun setClockTimerPreset5Focused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerPreset5Focus, focused)
    }
    private fun setClockTimerPreset10Focused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerPreset10Focus, focused)
    }
    private fun setClockTimerStartFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerStartFocus, focused)
    }
    private fun setClockTimerSetupBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerSetupBackFocus, focused)
    }
    private fun setAllClockTimerSetupFocusesHidden() {
        setClockTimerHourFocused(false)
        setClockTimerMinuteFocused(false)
        setClockTimerSecondFocused(false)
        setClockTimerPreset5Focused(false)
        setClockTimerPreset10Focused(false)
        setClockTimerStartFocused(false)
        setClockTimerSetupBackFocused(false)
    }
    /** Прицелы Таймера, панель обратного отсчёта. */
    private fun setClockTimerPauseResumeFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerPauseResumeFocus, focused)
    }
    private fun setClockTimerResetFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerResetFocus, focused)
    }
    private fun setClockTimerRunningBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerRunningBackFocus, focused)
    }
    private fun setAllClockTimerRunningFocusesHidden() {
        setClockTimerPauseResumeFocused(false)
        setClockTimerResetFocused(false)
        setClockTimerRunningBackFocused(false)
    }
    /** Прицелы Секундомера. */
    private fun setClockStopwatchStartPauseFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.viewClockStopwatchStartPauseFocus, focused)
    }
    private fun setClockStopwatchResetFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.viewClockStopwatchResetFocus, focused)
    }
    private fun setClockStopwatchBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.viewClockStopwatchBackFocus, focused)
    }
    private fun setAllClockStopwatchFocusesHidden() {
        setClockStopwatchStartPauseFocused(false)
        setClockStopwatchResetFocused(false)
        setClockStopwatchBackFocused(false)
    }
    /** Прицелы Мелодий — Select и Back под конкретным треком. */
    private fun setClockMelodySelectFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.viewClockMelodySelectFocus, focused)
    }
    private fun setClockMelodyBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.viewClockMelodyBackFocus, focused)
    }
    /** Фокус энкодера на Stop оверлея срабатывания. */
    private fun setClockFiredStopFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutClockFiredOverlay.viewClockFiredStopFocus, focused)
    }
    /** Back-кнопки экранов Часов — обычные кнопки экрана, не элементы адаптера. */
    private fun refreshClockAlarmBackButtonVisibility() = setEncoderOnlyVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.btnClockAlarmBack)
    private fun refreshClockTimerBackButtonsVisibility() {
        val timer = binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        setEncoderOnlyVisible(timer.btnClockTimerSetupBack, timer.btnClockTimerRunningBack)
    }
    private fun refreshClockStopwatchBackButtonVisibility() = setEncoderOnlyVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.btnClockStopwatchBack)
    private fun refreshClockMelodyBackButtonVisibility() = setEncoderOnlyVisible(binding.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.btnClockMelodyBack)

    // ===== БОКОВОЕ МЕНЮ И ДЕРЕВО ЭНКОДЕРА =====
    /** Позиция пункта часов по ключу — для syncClockEncoderPath(). */
    private fun clockRootIndex(key: String): Int = clockMeta.indexOfFirst { it.key == key }
    /** Безусловно ставит курсор энкодера по [path] от детей узла CLOCK. */
    /** Громкий setPath(), а не Silently: прицел обязан рисоваться там, где реально стоит курсор.
     * Инвариант для любого нового узла Clock — onHighlight не должен звать громкий selectPosition() своего адаптера. */
    private fun syncClockEncoderPath(path: List<Int>) = syncClockEncoderPath(path, loud = true)
    /** Тихий вариант нужен ровно треку в Мелодиях: его onHighlight запускает превью, конфликтующее с тумблером в onSelect. */
    private fun syncClockEncoderPathSilently(path: List<Int>) = syncClockEncoderPath(path, loud = false)
    private fun syncClockEncoderPath(path: List<Int>, loud: Boolean) {
        val rootNodes = itemsMenuRoot()
        val rootIndex = rootNodes.indexOfFirst { it.id == "CLOCK" }
        if (rootIndex == -1) return
        val fullPath = listOf(rootIndex) + path
        if (loud) navigator.setPath(rootNodes, fullPath) else navigator.setPathSilently(rootNodes, fullPath)
    }
    /** Фиксированный список, "В меню" последним пунктом. */
    private fun clockSidebarItems(): List<SidebarMenuItem<String>> {
        val items = clockMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = activity.getString(meta.labelRes)) }
        return if (mode() != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    /** Дети CLOCK: контент следует за курсором, панель показывается на каждый шаг листания. */
    /** onHighlight зовёт setSelectedPositionSilently(), а не selectPosition(): громкий вариант рекурсивно
     * продавливал курсор на уровень глубже, чем показано на экране. */
    private fun clockChildrenNodes(): List<MenuNode> {
        return clockMeta.mapIndexed { index, meta ->
            when (meta.key) {
                "TIME" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTick()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                    },
                    onActivate = {},
                )
                "ALARM" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTick()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                        // Курсор стоит на самом ALARM — прицел внутреннего узла с прошлого визита должен погаснуть.
                        setAllClockAlarmFocusesHidden()
                    },
                    children = alarmChildrenNodes(),
                )
                "TIMER" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTick()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                        // Прячем оба набора безусловно — какой из них видим, знает только timerState.
                        setAllClockTimerSetupFocusesHidden()
                        setAllClockTimerRunningFocusesHidden()
                    },
                    // childrenProvider, а не статичные children: состав детей зависит от timerState.
                    childrenProvider = { timerChildrenNodes() },
                )
                "STOPWATCH" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTick()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                        setAllClockStopwatchFocusesHidden()
                    },
                    children = stopwatchChildrenNodes(),
                )
                else -> MenuNode( // "MELODY"
                    id = meta.key,
                    onHighlight = { playTick(); clockAdapter.setSelectedPositionSilently(index) },
                    children = melodyChildrenNodes(),
                )
            }
        } + menuBackNode(
            { clockAdapter.setSelectedPositionSilently(clockMeta.size) },
            { clockAdapter.flashPressAnimation(clockMeta.size) },
        )
    }
    /** Дети ALARM: часы и минуты через ValueEditor, Set, Back; панель коммитит первый ребёнок HOUR. */
    private fun alarmChildrenNodes(): List<MenuNode> {
        val alarm = binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm
        return listOfNotNull(
            MenuNode(
                id = "HOUR",
                onHighlight = {
                    playTick()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmHourFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta -> playTick(); alarmHourWheel.scrollToValue(alarmHourWheel.currentValue() + delta) },
                    onEnter = { playConfirm() },
                    onExit = { playTick() },
                ),
            ),
            MenuNode(
                id = "MINUTE",
                onHighlight = {
                    playTick()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmMinuteFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta -> playTick(); alarmMinuteWheel.scrollToValue(alarmMinuteWheel.currentValue() + delta) },
                    onEnter = { playConfirm() },
                    onExit = { playTick() },
                ),
            ),
            MenuNode(
                id = "SET",
                onHighlight = {
                    playTick()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmSetFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(alarm.btnClockAlarmToggle) {
                        playButton()
                        toggleAlarmArmed()
                    }
                },
            ),
            if (mode() != PipBoyMode.PHONE) MenuNode(
                id = "BACK",
                onHighlight = {
                    playTick()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(alarm.btnClockAlarmBack) {
                        playConfirm()
                        setClockAlarmBackFocused(false)
                        navigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Дети TIMER ветвятся по timerState: в IDLE колёса и пресеты, иначе Pause/Resume и Reset. */
    private fun timerChildrenNodes(): List<MenuNode> {
        val timer = binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        return if (timerState == TimerState.IDLE) {
            listOfNotNull(
                MenuNode(
                    id = "HOUR",
                    onHighlight = {
                        playTick()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerHourFocused(true)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta -> playTick(); timerHourWheel.scrollToValue(timerHourWheel.currentValue() + delta) },
                        onEnter = { playConfirm() },
                        onExit = { playTick() },
                    ),
                ),
                MenuNode(
                    id = "MINUTE",
                    onHighlight = {
                        playTick()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerMinuteFocused(true)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta -> playTick(); timerMinuteWheel.scrollToValue(timerMinuteWheel.currentValue() + delta) },
                        onEnter = { playConfirm() },
                        onExit = { playTick() },
                    ),
                ),
                MenuNode(
                    id = "SECOND",
                    onHighlight = {
                        playTick()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerSecondFocused(true)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta -> playTick(); timerSecondWheel.scrollToValue(timerSecondWheel.currentValue() + delta) },
                        onEnter = { playConfirm() },
                        onExit = { playTick() },
                    ),
                ),
                MenuNode(
                    id = "PRESET5",
                    onHighlight = {
                        playTick()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerPreset5Focused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerPreset5) {
                            playButton()
                            addTimerPresetMinutes(5)
                        }
                    },
                ),
                MenuNode(
                    id = "PRESET10",
                    onHighlight = {
                        playTick()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerPreset10Focused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerPreset10) {
                            playButton()
                            addTimerPresetMinutes(10)
                        }
                    },
                ),
                MenuNode(
                    id = "START",
                    onHighlight = {
                        playTick()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerStartFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerStart) {
                            playButton()
                            startPlainTimer(timerHours * 3600 + timerMinutes * 60 + timerSeconds)
                        }
                    },
                ),
                if (mode() != PipBoyMode.PHONE) MenuNode(
                    id = "BACK",
                    onHighlight = {
                        playTick()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerSetupBackFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerSetupBack) {
                            playConfirm()
                            setClockTimerSetupBackFocused(false)
                            navigator.popLevel()
                        }
                    },
                ) else null,
            )
        } else {
            listOfNotNull(
                MenuNode(
                    id = "PAUSE_RESUME",
                    onHighlight = {
                        playTick()
                        setAllClockTimerRunningFocusesHidden()
                        setClockTimerPauseResumeFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerPauseResume) { requestPauseResume() }
                    },
                ),
                MenuNode(
                    id = "RESET",
                    onHighlight = {
                        playTick()
                        setAllClockTimerRunningFocusesHidden()
                        setClockTimerResetFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerReset) {
                            playButton()
                            resetTimer()
                        }
                    },
                ),
                if (mode() != PipBoyMode.PHONE) MenuNode(
                    id = "BACK",
                    onHighlight = {
                        playTick()
                        setAllClockTimerRunningFocusesHidden()
                        setClockTimerRunningBackFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerRunningBack) {
                            playConfirm()
                            setClockTimerRunningBackFocused(false)
                            navigator.popLevel()
                        }
                    },
                ) else null,
            )
        }
    }
    /** Живая пересборка узла TIMER при смене timerState; no-op, если курсор сейчас не внутри TIMER. */
    private fun refreshClockTimerEncoderChildren() {
        navigator.replaceChildrenOf("TIMER", timerChildrenNodes())
    }
    /** Дети STOPWATCH — статичный список, от stopwatchState зависит только текст кнопки. */
    private fun stopwatchChildrenNodes(): List<MenuNode> {
        val stopwatch = binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch
        return listOfNotNull(
            MenuNode(
                id = "START_PAUSE",
                onHighlight = {
                    playTick()
                    setAllClockStopwatchFocusesHidden()
                    setClockStopwatchStartPauseFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(stopwatch.btnClockStopwatchStartPause) {
                        playButton()
                        toggleStopwatchStartPause()
                    }
                },
            ),
            MenuNode(
                id = "RESET",
                onHighlight = {
                    playTick()
                    setAllClockStopwatchFocusesHidden()
                    setClockStopwatchResetFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(stopwatch.btnClockStopwatchReset) {
                        playButton()
                        resetStopwatch()
                    }
                },
            ),
            if (mode() != PipBoyMode.PHONE) MenuNode(
                id = "BACK",
                onHighlight = {
                    playTick()
                    setAllClockStopwatchFocusesHidden()
                    setClockStopwatchBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(stopwatch.btnClockStopwatchBack) {
                        playConfirm()
                        setClockStopwatchBackFocused(false)
                        navigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Дети MELODY — трек за треком с автопрослушиванием; ENCBTN проваливается в [Select, Back]. */
    private fun melodyChildrenNodes(): List<MenuNode> {
        val trackNodes = ringtoneTracks.indices.map { i ->
            MenuNode(
                id = "TRACK_$i",
                onHighlight = {
                    // Панель коммитит только первый трек, и через setSelectedPositionSilently() — громкий вариант зациклился бы.
                    playTick()
                    if (i == 0) {
                        clockAdapter.setSelectedPositionSilently(4)
                        openClockMelodyScreen()
                    }
                    melodyAdapter.setSelectedPositionSilently(i)
                    melodyFocusedIndex = i
                    startMelodyPreview(i)
                },
                children = melodySelectBackChildrenNodes(),
            )
        }
        val backNode = MenuNode(
            id = "MELODY_LIST_BACK",
            onHighlight = { playTick(); melodyAdapter.setSelectedPositionSilently(ringtoneTracks.size) },
            // popLevel() здесь не дублировать: melodyAdapter.selectPosition() уже зовёт его через onSelect.
            onActivate = {
                melodyAdapter.selectPosition(ringtoneTracks.size)
                melodyAdapter.flashPressAnimation(ringtoneTracks.size)
            },
        )
        return trackNodes + backNode
    }
    /** Select и Back под треком: Select коммитит melodyFocusedIndex, Back поднимает на уровень. */
    private fun melodySelectBackChildrenNodes(): List<MenuNode> {
        val melody = binding.incLayoutTabItemsClock.incLayoutTabItemsClockMelody
        return listOfNotNull(
            MenuNode(
                id = "SELECT",
                onHighlight = {
                    playTick()
                    setClockMelodyBackFocused(false)
                    setClockMelodySelectFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(melody.btnClockMelodySelect) {
                        playButton()
                        commitMelodySelection()
                    }
                },
            ),
            if (mode() != PipBoyMode.PHONE) MenuNode(
                id = "BACK",
                onHighlight = {
                    playTick()
                    setClockMelodySelectFocused(false)
                    setClockMelodyBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(melody.btnClockMelodyBack) {
                        playConfirm()
                        setClockMelodyBackFocused(false)
                        navigator.popLevel()
                    }
                },
            ) else null,
        )
    }

    // ===== БУДИЛЬНИК =====
    /** Проверка будильника из того же 300мс-цикла, что и часы; совпадение сразу разоружает его. */
    private fun updateAlarmStatusViews() {
        val alarm = binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm
        if (alarmArmed) {
            alarm.tvClockAlarmStatus.text = activity.getString(R.string.clock_alarm_status_on, String.format("%02d:%02d", alarmHour, alarmMinute))
            alarm.btnClockAlarmToggle.text = activity.getString(R.string.clock_alarm_cancel)
        } else {
            alarm.tvClockAlarmStatus.text = activity.getString(R.string.clock_alarm_status_off)
            alarm.btnClockAlarmToggle.text = activity.getString(R.string.clock_alarm_set)
        }
    }
    private fun toggleAlarmArmed() {
        alarmArmed = !alarmArmed
        updateAlarmStatusViews()
    }
    private fun checkAlarmFiring(gameCalendar: Calendar) {
        if (!alarmArmed) return
        val hour = gameCalendar.get(Calendar.HOUR_OF_DAY)
        val minute = gameCalendar.get(Calendar.MINUTE)
        if (hour == alarmHour && minute == alarmMinute) {
            fireAlarm()
        }
    }
    private fun fireAlarm() {
        alarmArmed = false
        val alarm = binding.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm
        alarm.tvClockAlarmStatus.text = activity.getString(R.string.clock_alarm_status_off)
        alarm.btnClockAlarmToggle.text = activity.getString(R.string.clock_alarm_set)
        binding.incLayoutClockFiredOverlay.tvClockFiredTitle.text = activity.getString(R.string.clock_alarm_fired_title)
        binding.incLayoutClockFiredOverlay.root.visibility = View.VISIBLE
        setClockFiredStopFocused(true)
        playClockFiredSound()
    }
    /** Закрытие оверлея — общее для тапа по Stop и для ENCBTN. */
    private fun dismissClockFiredOverlay() {
        stopClockFiredSound()
        setClockFiredStopFocused(false)
        binding.incLayoutClockFiredOverlay.root.visibility = View.GONE
    }
    /** Звук срабатывания — выбранный трек; до первого выбора игроком это индекс 0, а не тишина. */
    private fun playClockFiredSound() {
        stopClockFiredSound()
        val trackIndex = prefs.getInt(selectedRingtone_SPKey, 0)
        val uri = Uri.parse("android.resource://${activity.packageName}/${ringtoneTracks[trackIndex].rawResId}")
        // Без AudioAttributes(USAGE_ALARM): тот канал живёт на отдельном системном регуляторе громкости.
        clockFiredRingtonePlayer = MediaPlayer().apply {
            setDataSource(activity, uri)
            isLooping = true
            prepare()
            start()
        }
    }
    private fun stopClockFiredSound() {
        clockFiredRingtonePlayer?.stop()
        clockFiredRingtonePlayer?.release()
        clockFiredRingtonePlayer = null
    }

    // ===== ТАЙМЕР =====
    /** Проверка таймера из того же 300мс-цикла; отсчёт по целевому epoch, а не декрементом. */
    private fun checkTimerFiring() {
        if (timerState != TimerState.RUNNING) return
        val remainingMs = timerTargetEpochMillis - System.currentTimeMillis()
        if (remainingMs <= 0) {
            fireTimer()
            return
        }
        val remainingSeconds = (remainingMs / 1000).toInt()
        val h = remainingSeconds / 3600
        val m = (remainingSeconds % 3600) / 60
        val s = remainingSeconds % 60
        binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.tvClockTimerCountdown.text =
            String.format("%02d:%02d:%02d", h, m, s)
        onWoundCountdownTick(remainingSeconds)
        updateClockTimerLabel()
    }
    /** Подпись над отсчётом: у таймера может быть стадия ранения, для обычного запуска она пустая. */
    private fun updateClockTimerLabel() {
        binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.tvClockTimerLabel.text = timerLabelText()
    }
    /** Общий таймер принадлежит либо запуску с экрана Таймера, либо текущей фазе ранения. */
    private fun fireTimer() {
        timerState = TimerState.IDLE
        if (!isWoundActive()) {
            syncClockTimerScreenVisibility()
        } else {
            onWoundTimerFired()
        }
        binding.incLayoutClockFiredOverlay.tvClockFiredTitle.text = activity.getString(R.string.clock_timer_fired_title)
        binding.incLayoutClockFiredOverlay.root.visibility = View.VISIBLE
        setClockFiredStopFocused(true)
        playClockFiredSound()
    }
    /** Панели экрана Таймера следуют timerState независимо от того, кто таймер запустил. */
    private fun syncClockTimerScreenVisibility() {
        val timer = binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        val running = timerState != TimerState.IDLE
        timer.layoutClockTimerRunning.visibility = if (running) View.VISIBLE else View.GONE
        timer.layoutClockTimerSetup.visibility = if (running) View.GONE else View.VISIBLE
        // Общая точка пересборки дерева для сброса, срабатывания, таймера ранения и восстановления.
        refreshClockTimerEncoderChildren()
    }
    /** Общий старт для кнопки [Старт] и голосовой команды; на нуле секунд — no-op. */
    fun startPlainTimer(totalSeconds: Int) {
        if (totalSeconds <= 0) return
        val timer = binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        timerTargetEpochMillis = System.currentTimeMillis() + totalSeconds * 1000L
        timerState = TimerState.RUNNING
        timer.btnClockTimerPauseResume.text = activity.getString(R.string.clock_timer_pause)
        timer.layoutClockTimerSetup.visibility = View.GONE
        timer.layoutClockTimerRunning.visibility = View.VISIBLE
        updateClockTimerLabel() // ранения здесь всегда нет — очищает подпись от предыдущего таймера ранения
        // startPlainTimer() — единственный переход IDLE->RUNNING мимо syncClockTimerScreenVisibility().
        refreshClockTimerEncoderChildren()
    }
    /** Пресеты +5/+10 минут; метод класса, а не closure — нужен из timerChildrenNodes(). */
    private fun addTimerPresetMinutes(minutesToAdd: Int) {
        val totalMinutes = (timerHours * 60 + timerMinutes + minutesToAdd) % (24 * 60)
        timerHours = totalMinutes / 60
        timerMinutes = totalMinutes % 60
        timerHourWheel.scrollToValue(timerHours)
        timerMinuteWheel.scrollToValue(timerMinutes)
    }
    /** Тело кнопки [Пауза] — общее для тача и ENCBTN; звук выбирается по разрешению паузы.
     * Голосовая команда сюда не идёт: она проверяет то же условие сама и зовёт pauseResumeTimer(). */
    private fun requestPauseResume() {
        if (!timerPauseAllowed) {
            playError()
            return
        }
        playButton()
        pauseResumeTimer()
    }
    /** Общая пауза/возобновление — кнопка [Пауза] и голосовая команда "пауза"/"продолжи". */
    fun pauseResumeTimer() {
        val timer = binding.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        when (timerState) {
            TimerState.RUNNING -> {
                timerRemainingSecondsAtPause = ((timerTargetEpochMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
                timerState = TimerState.PAUSED
                timer.btnClockTimerPauseResume.text = activity.getString(R.string.clock_timer_resume)
            }
            TimerState.PAUSED -> {
                timerTargetEpochMillis = System.currentTimeMillis() + timerRemainingSecondsAtPause * 1000L
                timerState = TimerState.RUNNING
                timer.btnClockTimerPauseResume.text = activity.getString(R.string.clock_timer_pause)
            }
            TimerState.IDLE -> {}
        }
    }
    /** Общий сброс: при активном таймере ранения равнозначен [Стоп] на STATUS, а не тихому обрыву. */
    fun resetTimer() {
        if (isWoundActive()) {
            onWoundStopRequested()
        } else {
            timerState = TimerState.IDLE
            syncClockTimerScreenVisibility()
        }
    }

    // ===== СЕКУНДОМЕР =====
    /** Методы класса, а не closure — нужны из stopwatchChildrenNodes(). */
    private fun toggleStopwatchStartPause() {
        val stopwatch = binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch
        when (stopwatchState) {
            StopwatchState.IDLE -> {
                stopwatchStartEpochMillis = System.currentTimeMillis()
                stopwatchState = StopwatchState.RUNNING
                stopwatch.btnClockStopwatchStartPause.text = activity.getString(R.string.clock_timer_pause)
            }
            StopwatchState.RUNNING -> {
                stopwatchElapsedMillisAtPause = System.currentTimeMillis() - stopwatchStartEpochMillis
                stopwatchState = StopwatchState.PAUSED
                stopwatch.btnClockStopwatchStartPause.text = activity.getString(R.string.clock_timer_resume)
            }
            StopwatchState.PAUSED -> {
                stopwatchStartEpochMillis = System.currentTimeMillis() - stopwatchElapsedMillisAtPause
                stopwatchState = StopwatchState.RUNNING
                stopwatch.btnClockStopwatchStartPause.text = activity.getString(R.string.clock_timer_pause)
            }
        }
    }
    private fun resetStopwatch() {
        val stopwatch = binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch
        stopwatchState = StopwatchState.IDLE
        stopwatchElapsedMillisAtPause = 0L
        stopwatch.tvClockStopwatchElapsed.text = "00:00:00"
        stopwatch.btnClockStopwatchStartPause.text = activity.getString(R.string.clock_timer_start)
    }
    /** Обновление отображения секундомера — вызывается из общего 300мс-цикла, пока RUNNING. */
    private fun updateStopwatchDisplay() {
        if (stopwatchState != StopwatchState.RUNNING) return
        val elapsedSeconds = (System.currentTimeMillis() - stopwatchStartEpochMillis) / 1000
        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        binding.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.tvClockStopwatchElapsed.text =
            String.format("%02d:%02d:%02d", h, m, s)
    }

    // ===== МЕЛОДИЯ ЗВОНКА =====
    /** Мелодия звонка — методы класса, а не closure: кнопка регистрируется в setup() раньше секции экрана. */
    private fun updateMelodySelectedLabel() {
        val melody = binding.incLayoutTabItemsClock.incLayoutTabItemsClockMelody
        val index = prefs.getInt(selectedRingtone_SPKey, 0)
        melody.tvClockMelodySelectedName.apply {
            text = ringtoneTracks[index].displayName
            isSelected = false // застывшее обрезанное состояние, пока не нажали [Выбрать]
        }
    }
    /** Тело кнопки [Выбрать] — общее для тача и ENCBTN. */
    private fun commitMelodySelection() {
        prefs.edit().putInt(selectedRingtone_SPKey, melodyFocusedIndex).apply()
        updateMelodySelectedLabel()
        playMelodySelectedMarqueeOnce()
    }
    /** Один проход marquee; сброс isSelected нужен, иначе повторный выбор того же трека не прокрутится. */
    private fun playMelodySelectedMarqueeOnce() {
        val nameView = binding.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.tvClockMelodySelectedName
        nameView.isSelected = false
        nameView.post { nameView.isSelected = true }
    }
    private fun stopMelodyPreview() {
        melodyPreviewPlayer?.stop()
        melodyPreviewPlayer?.release()
        melodyPreviewPlayer = null
        melodyPreviewPlayingIndex = null
    }
    private fun startMelodyPreview(index: Int) {
        stopMelodyPreview()
        melodyPreviewPlayingIndex = index
        val melody = binding.incLayoutTabItemsClock.incLayoutTabItemsClockMelody
        melodyPreviewPlayer = MediaPlayer.create(activity, ringtoneTracks[index].rawResId).apply {
            isLooping = true
            start()
        }
        val audioSessionId = melodyPreviewPlayer?.audioSessionId
        if (hasAudioPermission() && audioSessionId != null && audioSessionId != -1) {
            melody.melodyWave.release()
            melody.melodyWave.setPlayer(audioSessionId)
            melody.melodyWave.visibility = View.VISIBLE
        } else if (!hasAudioPermission()) {
            requestAudioPermission()
        }
    }

    // ===== ПАНЕЛИ ЭКРАНА =====
    /** Переключает видимую панель справа; MELODY сюда не входит — это отдельный полноэкранный переход. */
    /** Контейнеры восстанавливаются здесь же: с энкодера можно уйти с Мелодии мимо её кнопки [Назад]. */
    private fun showClockContentPanel(key: String) {
        stopMelodyPreview()
        val clock = binding.incLayoutTabItemsClock
        clock.layoutTabItemsClockButtonsContainer.visibility = View.VISIBLE
        clock.layoutTabItemsClockContent.visibility = View.VISIBLE
        clock.incLayoutTabItemsClockTime.root.visibility = if (key == "TIME") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockAlarm.root.visibility = if (key == "ALARM") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockTimer.root.visibility = if (key == "TIMER") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockStopwatch.root.visibility = if (key == "STOPWATCH") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
    }
    private fun openClockMelodyScreen() {
        val clock = binding.incLayoutTabItemsClock
        clock.layoutTabItemsClockButtonsContainer.visibility = View.GONE
        clock.layoutTabItemsClockContent.visibility = View.GONE
        clock.incLayoutTabItemsClockMelody.root.visibility = View.VISIBLE
        // Рамка обязана совпадать с курсором энкодера, а тот при входе в MELODY всегда на первом треке,
        // а не на ранее подтверждённом.
        melodyFocusedIndex = 0
        // Молча (без звука) — восстановление состояния экрана при входе, не выбор игрока.
        melodyAdapter.setSelectedPositionSilently(melodyFocusedIndex)
        updateMelodySelectedLabel()
    }
    private fun closeClockMelodyScreen() {
        stopMelodyPreview()
        val clock = binding.incLayoutTabItemsClock
        clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
        clock.layoutTabItemsClockButtonsContainer.visibility = View.VISIBLE
        clock.layoutTabItemsClockContent.visibility = View.VISIBLE
    }

    companion object {
        // Восстановление состояния после убийства процесса в фоне
        private const val KEY_TIMER_STATE = "restore_timerState"
        private const val KEY_TIMER_TARGET_EPOCH = "restore_timerTargetEpochMillis"
        private const val KEY_TIMER_REMAINING_AT_PAUSE = "restore_timerRemainingSecondsAtPause"
    }
}
