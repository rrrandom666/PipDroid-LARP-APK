package com.malto4.pipdroid

import kotlin.reflect.KMutableProperty0
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.text.TextUtils
import android.os.PowerManager
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.ParcelUuid
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import com.malto4.pipdroid.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.UUID
import kotlin.jvm.internal.Intrinsics
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import com.chibde.visualizer.LineVisualizer

class MainActivity : AppCompatActivity() {

    /***********************************************************************************************************
     *
     *
     * CONSTANT AND VARIABLE DEFINITIONS
     *
     *
     **********************************************************************************************************/

    private lateinit var bindingMain: ActivityMainBinding

    /***********************************************************************************************************
     * SHARED PREFERENCES
     **********************************************************************************************************/
    val sharedPreferences by lazy {getSharedPreferences("PipDroid_Preferences",Context.MODE_PRIVATE)}
    val playerName_SPKey = "playerName"
    val playerRegion_SPKey = "playerRegion"
    val playerLevel_SPKey = "playerLevel"
    val playerUIColour_SPKey = "playerUIColour"
    val customMusicFolder_SPKey = "customMusicFolder"
    val customMapScaling_SPKey = "customMapScaling"
    val dateFormat_SPKey = "dateFormat"
    val gameYear_SPKey = "gameYear"
    val bluetoothMAC_SPKey = "bluetoothMAC"
    val bluetoothSUUID_SPKey = "bluetoothSUUID"
    val bluetoothRUUID_SPKey = "bluetoothRUUID"
    val bluetoothWUUID_SPKey = "bluetoothWUUID"
    val pipBoyMode_SPKey = "pipBoyMode"
    val appLanguage_SPKey = "appLanguage"
    private var UIColour_Selector = 0
    private var dateFormat_Selector = 0
    private var languageSelector = -1
    private var selected_button = R.drawable.button_selected_green
    // Курсор списка на STATUS (roadmap, "Редизайн STATS/Status — UX-спецификация",
    // фидбек по итогам тестирования) — толще и заметнее selected_button, применяется на
    // строку-контейнер целиком (fill_parent), не на сам Button, поэтому ширина рамки не
    // зависит от длины текста пункта. Меняется вместе с selected_button при смене темы.
    private var selectedRowButton = R.drawable.status_row_selected_green
    private var selectedDateFormat = "MM.dd.yy"
    private var trueFullscreen = false
    // Будильник (roadmap, "Часы — UX-спецификация") — один однократный, время в стенных
    // часах (не игровых — gameCalendar подменяет только YEAR). Сбрасывается при
    // перезапуске процесса (не сохраняется в SharedPreferences) — согласованное решение,
    // не полноценный AlarmManager.
    private var alarmHour = 7
    private var alarmMinute = 0
    private var alarmArmed = false
    private var clockFiredRingtonePlayer: MediaPlayer? = null
    // Таймер (roadmap, "Часы — UX-спецификация") — один, длительность настраивается
    // колёсами, пока IDLE. Отсчёт — по целевому времени в epoch millis, не декрементом
    // счётчика каждый тик, чтобы не копить дрейф от джиттера 300мс-цикла.
    private enum class TimerState { IDLE, RUNNING, PAUSED }
    private var timerHours = 0
    private var timerMinutes = 5
    private var timerSeconds = 0
    private var timerState = TimerState.IDLE
    private var timerTargetEpochMillis = 0L
    private var timerRemainingSecondsAtPause = 0
    // Секундомер (roadmap, "Часы — UX-спецификация") — старт/пауза/сброс, без кругов.
    // Тот же приём с epoch millis, что у таймера, только считаем вверх, а не вниз.
    private enum class StopwatchState { IDLE, RUNNING, PAUSED }
    private var stopwatchState = StopwatchState.IDLE
    private var stopwatchStartEpochMillis = 0L
    private var stopwatchElapsedMillisAtPause = 0L
    // Мелодия звонка (roadmap, "Часы — UX-спецификация") — общий трек на будильник и
    // таймер, один слот. В отличие от состояния будильника/таймера — это скорее настройка,
    // чем разовое состояние, поэтому персистится в SharedPreferences (как тема/имя игрока),
    // не сбрасывается при перезапуске.
    private val selectedRingtone_SPKey = "selectedRingtoneIndex"
    private var melodyFocusedIndex = 0
    private var melodyPreviewPlayer: MediaPlayer? = null
    private var melodyPreviewPlayingIndex: Int? = null
    private val melodyTrackRowViews = ArrayList<TextView>()



    /***********************************************************************************************************
     * LIST DEFINITIONS
     **********************************************************************************************************/
    private var listBottomButtons = ArrayList<Button>()
    private var listItemsClockButtons = ArrayList<Button>()
    private var listStatsSpecials = ArrayList<ConstraintLayout>()
    private var listStatsSkills = ArrayList<ConstraintLayout>()
    private var listDataMisc = ArrayList<ConstraintLayout>()
    private var listDataRadios = ArrayList<ConstraintLayout>()

    /***********************************************************************************************************
     * MEDIA PLAYERS
     **********************************************************************************************************/
    private lateinit var lineVisualizer: LineVisualizer
    private val REQUEST_CODE_PERMISSION_RECORD_AUDIO = 23
    private val REQUEST_CODE_PERMISSION_MEDIA = 123
    private var mediaPlayerCndRadEffList = mutableListOf<MediaPlayer>()
    private var mediaPlayerNewTabList = mutableListOf<MediaPlayer>()
    private var mediaPlayerItemSelectList = mutableListOf<MediaPlayer>()
    private var mediaPlayerErrorList = mutableListOf<MediaPlayer>()
    private var mediaPlayerLightOnOffList = mutableListOf<MediaPlayer>()
    // Фоновый эмбиент и радиостанции живут дольше одного проигрывания (крутятся, пока их
    // явно не остановят/не сменят) — в отличие от одноразовых UI-звуков выше, им нужно
    // хранить ссылку на текущий MediaPlayer, а не только список для release-по-завершении.
    private var mediaPlayerBackGround: MediaPlayer? = null
    private var enclaveRadioMediaPlayer: MediaPlayer? = null
    private var galaxyRadioMediaPlayer: MediaPlayer? = null
    private var newVegasRadioMediaPlayer: MediaPlayer? = null
    private var customRadioMediaPlayer: MediaPlayer? = null
    private var radioEnclaveStateSelected = false
    private var radioGNRStateSelected = false
    private var radioNVRStateSelected = false
    private var radioCustomStateSelected = false
    private var customMP3Files: List<File> = emptyList()
    private var customMP3FilesFound: Boolean = false
    private var currentCustomTrackIndex = 0

    /***********************************************************************************************************
     * MAP (roadmap, этап 6, п.2) — бандл (map.png/map_bounds.json/map_roads.json)
     * импортируется в Settings (см. MapBundleRepository), сам экран карты только читает то,
     * что уже лежит на диске, никаких сетевых проверок/разрешений (INTERNET убран).
     **********************************************************************************************************/
    private val mapBundleRepository by lazy { MapBundleRepository(this) }
    private var mapGeoReference: GeoReference? = null
    private var mapLocationListener: LocationListener? = null
    // Автоцентрирование должно сработать один раз на свежем открытии экрана карты (по
    // первому GPS-фиксу), а не при каждом обновлении позиции — иначе кнопка "Центр" была бы
    // бессмысленна (карту вечно тянуло бы обратно к игроку). Сбрасывается в openMapScreen().
    private var mapHasCenteredOnUser = false
    companion object {
        // Отладочная инъекция BLE-команд без реального ESP32 (roadmap, этап 7,
        // "быстрая отладка логики экранов"). См. registerDebugCommandReceiver().
        private const val ACTION_DEBUG_BLE_COMMAND = "com.malto4.pipdroid.DEBUG_BLE_COMMAND"
        private const val EXTRA_DEBUG_BLE_RAW = "raw"

        // Анимация включения (roadmap, "Видение приложения", п.11). См. playBootSequence().
        private const val BOOT_FRAME_LOGO_DURATION_MS = 2500L
        private const val BOOT_FRAME_CODEWALL_DURATION_MS = 2000L
        private const val BOOT_TERMINAL_CHAR_DELAY_MS = 30L
        private const val BOOT_TERMINAL_END_HOLD_MS = 600L
        private const val BOOT_CURSOR_CHAR = "█" // █ — блочный курсор кадра 3
        // Флейвор-текст "стены кода" — общий терминальный лор загрузки, не привязан ни
        // к конкретному приложению-компаньону, ни к вселенной Fallout специально (см.
        // обсуждение визуальной дистанции от Bethesda, CLAUDE.md). Один блок повторяется
        // много раз, чтобы гарантировать высоту текста намного больше экрана при любом
        // размере шрифта/дисплея — иначе скролл кончится раньше, чем экран проскроллит.
        private const val BOOT_CODEWALL_BLOCK = "* 1 0 0x0000A4 0x0000000000000000 start memory discovery\n" +
            "0 0x0000A4 0x0000000000000000 1 0 0x000014 0x0000000000000000 CPU0 starting cell\n" +
            "relocation0 0x0000A4 0x0000000000000000 1 0 0x000009 0x0000000000000000\n" +
            "CPU0 launch EFI0 0x0000A4 0x0000000000000000 1 0 0x000009 0x00000000000E003D\n" +
            "CPU0 starting EFI0 0x0000A4 0x0000000000000000 1 0 0x0000A4 0x0000000000000000\n"
        private val BOOT_CODEWALL_TEXT = BOOT_CODEWALL_BLOCK.repeat(24)
        // Общий баннер PIP-OS — шапка и загрузочного, и выключающего терминала (см.
        // SHUTDOWN_HEADER_PREFIX ниже), не дублируется отдельной строкой на каждый случай.
        private const val PIP_OS_BANNER = "**************** PIP-OS(R) V7.1.0.8 ****************"
        // Терминальная печать кадра 3 — общий узнаваемый ROBCO/PIP-OS boot-текст,
        // разлитый по всей серии игр Fallout (не решение конкретно приложения-компаньона
        // Bethesda) — год и "DEITRIX 303" сознательно оставлены как флейвор, не завязаны
        // на игровой год/имя игрока из Settings (см. обсуждение спеки).
        private const val BOOT_TERMINAL_TEXT = PIP_OS_BANNER + "\n\n" +
            "COPYRIGHT 2075 ROBCO(R)\n" +
            "LOADER V1.1\n" +
            "EXEC VERSION 41.10\n" +
            "64k RAM SYSTEM\n" +
            "38911 BYTES FREE\n" +
            "NO HOLOTAPE FOUND\n" +
            "LOAD ROM(1): DEITRIX 303"

        // Глитч-эффект — короткие импульсы искажения в случайных точках всей заставки,
        // плюс фоновый режим на всё время работы PipBoy после загрузки (см.
        // startContinuousGlitch()) и во время "остаёмся на экране" в начале выключения.
        // См. scheduleGlitchPulses()/triggerGlitchPulse().
        private const val BOOT_GLITCH_MIN_PULSES = 5
        private const val BOOT_GLITCH_MAX_PULSES = 8
        private const val POST_BOOT_GLITCH_MIN_PULSES = 4
        private const val POST_BOOT_GLITCH_MAX_PULSES = 6
        private const val GLITCH_PULSE_MIN_MS = 60
        private const val GLITCH_PULSE_MAX_MS = 150
        // Интервал между импульсами в фоновом режиме (startContinuousGlitch()) — заметно
        // реже, чем во время самой заставки, иначе это будет мешать игре, а не быть
        // фоновой деталью экрана.
        private const val AMBIENT_GLITCH_MIN_INTERVAL_MS = 4000
        private const val AMBIENT_GLITCH_MAX_INTERVAL_MS = 12000

        // Анимация выключения (roadmap, "Видение приложения", п.11 — довесок). См.
        // playShutdownSequence(). Тайминги/пулы глитча переиспользуют константы выше —
        // окно по длительности близко к POST_BOOT_GLITCH_*, отдельных не заводим.
        private const val SHUTDOWN_STAY_DURATION_MS = 2000L
        private const val SHUTDOWN_FADE_TO_BLACK_MS = 500L
        private const val SHUTDOWN_FINAL_FADE_MS = 500L
        private val SHUTDOWN_HEADER_PREFIX = "$PIP_OS_BANNER\n\n"
        private const val SHUTDOWN_BODY_TEXT = "STOPPING ALL PROCESSES...\n" +
            "DUMPING MEMORY...\n" +
            "DISCONNECTING..."

        // Система ранений/кровотечения (roadmap, "Редизайн STATS/Status —
        // UX-спецификация") — длительности BLEED/BANDAGE и STUNNED.
        private const val WOUND_BLEED_BANDAGE_DURATION_SECONDS = 600
        private const val STUN_DURATION_SECONDS = 300

        // Восстановление состояния после убийства процесса в фоне (roadmap, "Восстановление
        // состояния после убийства процесса — спецификация") — ключи onSaveInstanceState()/
        // onCreate(savedInstanceState). savedInstanceState != null — сигнал именно этого
        // случая, не обычного холодного старта (см. спеку — Bundle, не SharedPreferences).
        private const val KEY_CUR_MENU = "restore_curMenu"
        private const val KEY_ROOT_CURSOR = "restore_rootCursor"
        private const val KEY_PIPBOY_MODE = "restore_pipBoyMode"
        private const val KEY_WOUND_PHASE = "restore_woundPhase"
        private const val KEY_WOUND_SEVERITY = "restore_woundSeverity"
        private const val KEY_TIMER_STATE = "restore_timerState"
        private const val KEY_TIMER_TARGET_EPOCH = "restore_timerTargetEpochMillis"
        private const val KEY_TIMER_REMAINING_AT_PAUSE = "restore_timerRemainingSecondsAtPause"
        private const val KEY_CRIPPLED_HEAD = "restore_crippledHead"
        private const val KEY_CRIPPLED_TORSO = "restore_crippledTorso"
        private const val KEY_CRIPPLED_LEFT_ARM = "restore_crippledLeftArm"
        private const val KEY_CRIPPLED_RIGHT_ARM = "restore_crippledRightArm"
        private const val KEY_CRIPPLED_LEFT_LEG = "restore_crippledLeftLeg"
        private const val KEY_CRIPPLED_RIGHT_LEG = "restore_crippledRightLeg"
        private const val KEY_STATUS_CURSOR_ROW = "restore_statusCursorRow"
    }

    /***********************************************************************************************************
     * BLUETOOTH
     **********************************************************************************************************/
    private val menuNavigator = MenuNavigator()
    private var pipBoyMode: PipBoyMode = PipBoyMode.PHONE
    private var bleService: PipBoyBleService? = null
    private var bleServiceBound = false
    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bound = (service as PipBoyBleService.LocalBinder).getService()
            bleService = bound
            bleServiceBound = true
            bound.onConnectionStateChanged = { status -> runOnUiThread { updateBLEConnected(status) } }
            bound.onCommandReceived = { raw -> runOnUiThread { handleBleCommand(raw) } }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            bleServiceBound = false
        }
    }
    private var debugCommandReceiver: BroadcastReceiver? = null
    /**
     * Пускает строки в тот же handleBleCommand(), что и реальный ESP32 по BLE — только
     * источник команды заменён на adb broadcast с компьютера (roadmap, этап 7, "быстрая
     * отладка логики экранов" вместо программной эмуляции самой BLE-периферии). Только
     * debug-сборки — в релизе приёмник не регистрируется и адрес недостижим.
     *
     * adb shell am broadcast -p com.malto4.pipdroid -a com.malto4.pipdroid.DEBUG_BLE_COMMAND --es raw "ENC:+1"
     */
    private fun registerDebugCommandReceiver() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val raw = intent?.getStringExtra(EXTRA_DEBUG_BLE_RAW) ?: return
                handleBleCommand(raw)
            }
        }
        val filter = IntentFilter(ACTION_DEBUG_BLE_COMMAND)
        // adb broadcast приходит извне приложения — начиная с API 33 контекстно
        // зарегистрированный приёмник обязан явно объявить экспортируемость.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        debugCommandReceiver = receiver
    }
    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Системный диалог разрешений закрылся — убираем временный fullscreen (см.
        // checkPermissions()), возвращаемся к области, настроенной игроком на шаге
        // DISPLAY AREA мастера (актуально, только пока идёт мастер; вне его loadViewState()
        // просто переприменит то же самое, что уже есть).
        loadViewState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val bluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
            val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
            if (granted || (bluetoothScanGranted && bluetoothConnectGranted)) {
                onRequiredPermissionsGranted()
            } else {
                Log.e("MainActivity", "Required permissions are not granted")
            }
        }  else {
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (granted) {
                onRequiredPermissionsGranted()
            } else {
                Log.e("MainActivity", "Required permissions are not granted")
            }
        }
    }
    /**
     * Системный диалог "Разрешить приложению включить Bluetooth?" (ACTION_REQUEST_ENABLE) —
     * roadmap, чтобы игроку не приходилось отдельно идти в системные настройки телефона,
     * если Bluetooth выключен. Не проверяем resultCode отдельно: включился адаптер (OK) или
     * нет (отказ/закрыл диалог) — setupBluetooth() сам перепроверит adapter.isEnabled и
     * либо продолжит (requestIgnoreBatteryOptimizations + startAndBindBleService), либо
     * просто залогирует и остановится, как и раньше.
     */
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        setupBluetooth()
    }
    /**
     * Импорт бандла карты (Settings > Map Data, roadmap, ветка app-map) — SAF-пикер папки.
     * Требует API 21 (minSdk поднят 19->21 именно из-за этого, см. build.gradle). Само
     * копирование — MapBundleRepository.importFromTree() на IO-потоке, результат идёт в
     * статус/строку ошибки подпанели.
     */
    private val openMapBundleTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@registerForActivityResult
        val resultView = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsMapBundle.tvMapBundleImportResult
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mapBundleRepository.importFromTree(treeUri)
            withContext(Dispatchers.Main) {
                refreshMapBundleStatus()
                resultView.text = result.fold(
                    onSuccess = { getString(R.string.map_bundle_import_success) },
                    onFailure = { it.message ?: getString(R.string.map_bundle_import_error_unknown) }
                )
            }
        }
    }
    private fun onRequiredPermissionsGranted() {
        // Если разрешения выдавались с шага PERMISSIONS мастера — режим Телефон
        // заканчивает флоу сразу (BLE-корпус не используется), PipBoy 2000/3000 ведёт
        // дальше, к сопряжению с корпусом, не заставляя жать что-то ещё.
        val wizard = bindingMain.incLayoutPipboy2000Wizard
        val fromWizardPermissions = wizard.root.visibility == View.VISIBLE && wizard.layoutWizardPermissions.visibility == View.VISIBLE
        if (fromWizardPermissions && pipBoyMode == PipBoyMode.PHONE) {
            finishPhoneModeSetup()
            return
        }
        setupBluetooth()
        if (fromWizardPermissions) {
            showWizardStep(PipBoyWizardStep.PAIRING)
        }
    }

    /***********************************************************************************************************
     * SCREEN SIZE
     **********************************************************************************************************/
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var newWidth = 0
    private var newHeight = 0
    private var isResizing = false
    // Доп. минимум размера при пинче во время шага DISPLAY AREA мастера PipBoy — 0 вне
    // мастера (см. ScaleListener.onScale, roadmap "косметические правки").
    private var wizardMinContentWidthPx = 0
    private var wizardMinContentHeightPx = 0
    private var lastX = 0f
    private var lastY = 0f

    /***********************************************************************************************************
     * DISCLAIMER
     **********************************************************************************************************/
    private var showTutorialBool = true

    /***********************************************************************************************************
     * FILTER MODIFICATION
     **********************************************************************************************************/
    private lateinit var filterFrame: FrameLayout
    private lateinit var filteringMenu: String
    private var selectedFilterSTATSPerks = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterDATAMisc = mutableSetOf<String>()  // Set to keep track of selected item IDs
    // Снимок selectedFilterSTATSPerks на момент открытия экрана (roadmap, "Редизайн экрана
    // фильтра — UX-спецификация") — чекбоксы мутируют selectedFilterSTATSPerks сразу по
    // тапу, ещё до Save; Cancel должен откатить эти правки, иначе при повторном открытии
    // экрана (без рестарта приложения) будут видны несохранённые правки прошлой сессии.
    private var filterSelectionSnapshot: MutableSet<String> = mutableSetOf()

    /***********************************************************************************************************
     * LongButtonPresses - EasterEgg + FLASHLIGHT + PlayerDamage
     **********************************************************************************************************/
    private var statsCndPopupIsHolding = false
    private var menuSwipeEnabled = true
    private var isFlashlightOn = false
    private var isFlashlightOff = false
    private var delayIterationCount = 0
    private var delayModify = 500L

    /***********************************************************************************************************
     * STATUS — система ранений/кровотечения (roadmap, "Редизайн STATS/Status —
     * UX-спецификация"). woundPhase/woundSeverity — единый источник истины, одновременно
     * "что сейчас с персонажем" и "на что таймер" (timerState/timerTargetEpochMillis,
     * общий таймер, реализован на этапе "Часы" — переиспользуется, не отдельный механизм).
     **********************************************************************************************************/
    private enum class WoundPhase { NONE, BLEED, BANDAGE, STUNNED, DEAD }
    private enum class WoundSeverity { LIGHT, HEAVY }
    private var woundPhase = WoundPhase.NONE
    private var woundSeverity = WoundSeverity.LIGHT
    private var crippledHead = false
    private var crippledTorso = false
    private var crippledLeftArm = false
    private var crippledRightArm = false
    private var crippledLeftLeg = false
    private var crippledRightLeg = false

    private var selectedSPECIAL = "STRENGTH"
    // Кнопки +/- (roadmap, "Финализация STATS") — действуют на выбранный selectedSPECIAL/
    // selectedSKILL, не на конкретную строку, поэтому один общий флаг на весь экран
    // достаточен (в отличие от старой схемы с отдельным флагом на каждый атрибут/навык).
    private var isSPECIALValueIncreasing = false
    private var isSPECIALValueDecreasing = false

    private var selectedSKILL = "BARTER"
    private var isSKILLValueIncreasing = false
    private var isSKILLValueDecreasing = false

    private lateinit var selectedSubMenu: Button

    private val handler = Handler(Looper.getMainLooper())
    // 300мс-тик (часы/будильник/таймер/секундомер) — заведён в onCreate(), ссылка нужна
    // здесь, чтобы onDestroy() мог его остановить. Раньше был локальной переменной внутри
    // onCreate() и никогда не прерывался — при пересоздании Activity (MIUI регулярно
    // пересоздаёт её при блокировке/разблокировке экрана, подтверждено логом) старый поток
    // продолжал тикать по отвязанному bindingMain и мог всё ещё довести таймер ранения до
    // срабатывания: звук стартовал (MediaPlayer не привязан к View), а оверлей с [Стоп]
    // показать было уже некому — принадлежал уничтоженному экрану.
    private var tickThread: Thread? = null
    private val longPressRunnable = object : Runnable {
        override fun run() {
            if (statsCndPopupIsHolding) {
                bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.root.visibility = View.VISIBLE
                bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.layoutTabStatusCndContent.visibility = View.GONE
                bindingMain.incLayoutFilterModification.root.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
            }
            if (isFlashlightOn){
                playLightOnAudio()
                bindingMain.titleConstraintLayout.visibility = View.GONE
                bindingMain.mainConstraintLayout.visibility = View.GONE
                bindingMain.bottomConstraintLayout.visibility = View.GONE
                bindingMain.flFlashlight.visibility = View.VISIBLE
                isFlashlightOff = false
            }
            if (isFlashlightOff){
                playLightOffAudio()
                bindingMain.titleConstraintLayout.visibility = View.VISIBLE
                bindingMain.mainConstraintLayout.visibility = View.VISIBLE
                bindingMain.bottomConstraintLayout.visibility = View.VISIBLE
                bindingMain.flFlashlight.visibility = View.GONE
                isFlashlightOn = false
            }
            if(isSPECIALValueIncreasing || isSPECIALValueDecreasing){
                adjustSelectedSpecial(if (isSPECIALValueIncreasing) 1 else -1)
                handler.postDelayed(this, 500) // 500 msecond — SPECIAL (1-10) не разгоняется
            }
            if(isSKILLValueIncreasing || isSKILLValueDecreasing){
                adjustSelectedSkill(if (isSKILLValueIncreasing) 1 else -1)
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                delayIterationCount++
                handler.postDelayed(this, delayModify)
            }
        }
    }

    private lateinit var menuGestureDetector: GestureDetector
    private var curMenu = "STATS"

    /**
     * Строка 2 новой шапки (roadmap, "Новая шапка + единый Settings", косметика по образцу
     * референса) — [label] и [onSelect] берутся с уже существующих кнопок второго уровня
     * (btnStatsStatus и т.д., см. statsRow2Items()/dataRow2Items()) — эти
     * кнопки остаются в дереве навсегда GONE, реальная логика переключения экрана (их
     * onClickListener) не трогается вообще.
     */
    private data class Row2Item(val label: CharSequence, val onSelect: () -> Unit)
    private var row2Items: List<Row2Item> = emptyList()
    private var row2Active = 0
    private val row2Views = mutableListOf<TextView>()
    // Счётчик поколений строки 2 — растёт при каждом setupRow2(). alignRow2ToActiveButton()
    // сверяет его перед применением отложенного (post{}) расчёта, чтобы устаревший callback
    // от уже покинутого раздела не подвинул полосу по данным уже отсоединённого View.
    private var row2Generation = 0
    private fun onMenuSwipeLeft() {
        // menuNavigator.resetToRoot() обязателен здесь же, что и на тапах по строке 1 —
        // иначе энкодер после свайпа крутит дерево раздела, с которого свайпнули (roadmap,
        // "Модель навигации энкодером").
        when(curMenu){
            "STATS" -> {
                menuChangeBLE("ITEMS")
                menuNavigator.resetToRoot(itemsMenuRoot())
            }
            "ITEMS" -> {
                menuChangeBLE("DATA")
                menuNavigator.resetToRoot(dataMenuRoot())
            }
            "DATA" -> {
                menuChangeBLE("STATS")
                menuNavigator.resetToRoot(statsMenuRoot())
            }
        }
    }
    private fun onMenuSwipeRight() {
        when(curMenu){
            "STATS" -> {
                menuChangeBLE("DATA")
                menuNavigator.resetToRoot(dataMenuRoot())
            }
            "ITEMS" -> {
                menuChangeBLE("STATS")
                menuNavigator.resetToRoot(statsMenuRoot())
            }
            "DATA" -> {
                menuChangeBLE("ITEMS")
                menuNavigator.resetToRoot(itemsMenuRoot())
            }
        }
    }


    /***********************************************************************************************************
     *
     *
     * FUNCTIONS
     *
     *
     **********************************************************************************************************/

    /***********************************************************************************************************
     * MEDIA PLAYERS + AUDIO
     **********************************************************************************************************/
    private fun checkAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION_RECORD_AUDIO)
    }
    private fun checkCustomMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            result == PackageManager.PERMISSION_GRANTED
        } else {
            val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            result == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun requestCustomMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), REQUEST_CODE_PERMISSION_MEDIA)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_PERMISSION_MEDIA)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION_MEDIA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMp3Files()
                playRandomTrack()
            }
        }
    }
    private fun loadMp3Files() {
        /*
        * Blackberry Passport by default uses /mnt/sdcard as "getExternalStorageDirectory()", but it seems /mnt/sdcard with this current code
        * isn't a directory, but a file. I can't currently find a way to retrieve/access the MP3 files inside /mnt/sdcard, so this is for now paused
        * This issue isn't present on my Samsung Galaxy S23 Ultra, only the Blackberry at the moment.
        *
        * I also don't seem to be able to save the mp3 files anywhere else on the phone other than in File Explorer (/mnt/sdcard).
        * Other APPs like ES File Explorer is able to read this folder and files inside, so it must be possible, but after 2 days
        * of testing and investigating, I am leaving this here for now, to continue on other parts of this project.
        *
        * */
        val musicDir = File(Environment.getExternalStorageDirectory(),
            sharedPreferences.getString(customMusicFolder_SPKey, "Music")!!
        )
        if (musicDir.exists() && musicDir.isDirectory) {
            customMP3Files = findMp3Files(musicDir)
            if(customMP3Files.isNotEmpty()){
                customMP3Files = customMP3Files.shuffled() // Shuffle the list for random order
                customMP3FilesFound = true
            }
        } else {
            customMP3FilesFound = false
        }
    }
    private fun findMp3Files(directory: File): MutableList<File> {
        val mp3List = mutableListOf<File>()
        if (directory.exists()) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        mp3List.addAll(findMp3Files(file))
                    } else if (file.name.endsWith(".mp3", true)) {
                        mp3List.add(file)
                    }
                }
            }
        }
        return mp3List
    }
    private fun playRandomTrack() {
        if (customMP3Files.isNotEmpty()) {
            currentCustomTrackIndex = customMP3Files.indices.random()
            playTrack(customMP3Files[currentCustomTrackIndex])
        }
    }
    private fun playTrack(file: File) {
        customRadioMediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                playNextTrack()
            }
        }
    }
    private fun playNextTrack() {
        currentCustomTrackIndex = (currentCustomTrackIndex + 1) % customMP3Files.size
        playTrack(customMP3Files[currentCustomTrackIndex])
    }
    /**
     * Радиостанции (roadmap, "Рефакторинг кода" — память фонового процесса). Раньше "выключить
     * станцию" значило просто обнулить громкость (setVolume(0,0)) — сама станция продолжала
     * молча крутиться и декодироваться в фоне, поэтому стоило игроку за сессию заглянуть во
     * все станции по разу, все они в итоге оказывались одновременно загружены и работали.
     * Теперь "выключить" — это по-настоящему stop()+release(): в любой момент времени
     * загружена максимум одна станция, и то только если её реально включали.
     */
    private fun releaseRadioPlayer(target: KMutableProperty0<MediaPlayer?>) {
        target.get()?.apply {
            try {
                if (isPlaying) stop()
            } catch (e: IllegalStateException) {
                Log.w("MainActivity", "Радио-MediaPlayer уже был в неподходящем состоянии для stop()", e)
            }
            release()
        }
        target.set(null)
    }
    private fun turnAllRadioOff() {
        // lineVisualizer больше не прячется при остановке — горизонтальная шкала (тот же
        // View, что рисует бегущую волну во время игры) должна оставаться на экране
        // независимо от того, играет музыка или нет.
        releaseRadioPlayer(::galaxyRadioMediaPlayer)
        releaseRadioPlayer(::enclaveRadioMediaPlayer)
        releaseRadioPlayer(::newVegasRadioMediaPlayer)
        if (customMP3FilesFound) {
            releaseRadioPlayer(::customRadioMediaPlayer)
        }
    }
    /** Встроенная станция (Galaxy/Enclave/New Vegas) — лениво создаётся здесь же, по resId,
     * не заранее в onCreate(). Кастомная станция идёт другим путём (playTrack() уже создаёт
     * и стартует её из файла на диске) — см. activateRadioAudio() ниже, общий хвост для обеих. */
    private fun turnRadioOnBuiltIn(resId: Int, target: KMutableProperty0<MediaPlayer?>) {
        turnAllRadioOff()
        val random = Random()
        val player = MediaPlayer.create(applicationContext, resId) ?: return
        player.isLooping = true
        player.seekTo(random.nextInt(player.duration))
        player.start()
        target.set(player)
        activateRadioAudio(player)
    }
    /** Общий хвост turnRadioOnBuiltIn() и клика по Custom (та уже создана и запущена
     * playTrack() к моменту вызова — здесь только громкость и подключение визуализатора,
     * без повторного seekTo()/isLooping, что сломало бы переход на следующий трек по
     * завершении, см. playNextTrack()). */
    private fun activateRadioAudio(player: MediaPlayer) {
        player.setVolume(1.0f, 1.0f)
        val audioSessionId = player.audioSessionId
        if (checkAudioPermission()) {
            if (audioSessionId != -1) {
                // BaseVisualizer.setPlayer() создаёт новый android.media.audiofx.Visualizer
                // поверх старого, не освобождая предыдущий (баг библиотеки) — старый
                // экземпляр остаётся enabled и продолжает слать колбэки от прежней,
                // приглушённой дорожки, гоняясь за новым за один и тот же `bytes`. Из-за
                // этого после первого трека на шкале залипала плоская линия, которая не
                // сменялась новой при следующем воспроизведении. release() перед новым
                // setPlayer() гарантирует, что активен только один Visualizer.
                lineVisualizer.release()
                lineVisualizer.setPlayer(audioSessionId)
                lineVisualizer.visibility = View.VISIBLE
            }
        } else {
            requestAudioPermission()
        }
    }


    /***********************************************************************************************************
     * BLUETOOTH
     **********************************************************************************************************/
    /**
     * Список разрешений зависит от режима (roadmap, "Косметические правки мастера" — экран
     * PERMISSIONS раньше вообще не показывался в режиме Телефон, хотя геопозиция и
     * уведомления нужны и там). Bluetooth (SCAN/CONNECT) — только для PipBoy 2000/3000,
     * в Телефоне BLE-корпус не используется вовсе.
     */
    private fun requiredPermissionsForCurrentMode(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (pipBoyMode != PipBoyMode.PHONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissionsForCurrentMode().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Системный диалог разрешений должен физически поместиться на экране — на миг
            // разворачиваемся на весь экран, сворачиваемся обратно в колбэке
            // permissionRequestLauncher выше сразу после закрытия диалога.
            applyTemporaryFullScreenLayout()
            permissionRequestLauncher.launch(permissionsToRequest.toTypedArray())
        } else if (pipBoyMode != PipBoyMode.PHONE) {
            setupBluetooth()
        }
    }
    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            Log.e("MainActivity", "Bluetooth is not supported")
            return
        }
        if (!adapter.isEnabled) {
            // Просим включить Bluetooth прямо в приложении, не заставляя игрока идти в
            // системные настройки — см. enableBluetoothLauncher. На API 31+ для показа
            // этого диалога нужен уже выданный BLUETOOTH_CONNECT — все вызывающие
            // setupBluetooth() места идут после подтверждения разрешений (проверено).
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        requestIgnoreBatteryOptimizations()
        startAndBindBleService()
    }
    private fun startAndBindBleService() {
        val intent = Intent(this, PipBoyBleService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
    }
    /** Не обязательное разрешение, а рекомендация системы — без него агрессивные
     * вендоры (Xiaomi/Huawei) убивают фоновую BLE-связь за минуты (протокол, раздел 5). */
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e: Exception) {
                Log.w("MainActivity", "Battery optimization settings not available", e)
            }
        }
    }
    private fun sendBLEText(bleText: String) {
        if (bleService?.isConnected() == true) {
            bleService?.sendCommand(bleText)
            Log.i("MainActivity", "Sending text to BLE device")
        } else {
            Log.e("MainActivity", "BluetoothGatt is not connected")
        }
    }
    fun updateBLEConnected(status: String){
        // status - внутренний токен состояния (сравнивается через ==), не текст для показа —
        // локализованная подпись берётся отдельно, из строкового ресурса (roadmap, локализация).
        val displayText = if (status == "CONNECTED") getString(R.string.bluetooth_status_connected) else getString(R.string.bluetooth_status_disconnected)
        bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.textViewBLUETOOTHConnection.text = displayText
        // Индикатор BLE в правом углу row1 (roadmap, "Новая шапка + единый Settings", п.3) —
        // тот же глиф, состояние передаётся альфой, не сменой drawable.
        bindingMain.incLayoutHeaderToplevel.imgHeaderBleStatus.alpha = if (status == "CONNECTED") 1.0f else 0.35f
    }
    private fun disconnectBLE(){
        updateBLEConnected("DISCONNECTED")
        bleService?.disconnect()
    }
    private fun stopBleService() {
        disconnectBLE()
        if (bleServiceBound) {
            unbindService(bleServiceConnection)
            bleServiceBound = false
        }
        stopService(Intent(this, PipBoyBleService::class.java))
    }

    /**
     * Экран выбора режима (roadmap, "Видение приложения", п.1) — показывается первым при
     * каждом запуске (не только на первой установке), поверх буквально всего. PipBoy 3000
     * кликабелен только для показа описания, реально не выбирается — заглушка на будущее.
     */
    private var modeSelectHighlighted = PipBoyMode.PHONE
    private lateinit var modeSelectAdapter: ModeSelectAdapter

    /**
     * Акцентный цвет текущей темы оформления (playerUIColour_SPKey — тот же ключ, что и у
     * applyBackgroundResource()/applyTextColor() для остального интерфейса). Кнопки нового
     * стиля тонируются им же, а не жёстко зелёным, чтобы смена темы в Settings подхватывалась
     * и мастером/экраном выбора режима.
     */
    private fun currentWizardAccentColor(): Int {
        val colorRes = when (sharedPreferences.getInt(playerUIColour_SPKey, 0)) {
            1 -> R.color.themeAmber
            2 -> R.color.themeWhite
            3 -> R.color.themeBlue
            else -> R.color.themeGreen
        }
        return ContextCompat.getColor(this, colorRes)
    }
    /**
     * Ручное управление видом кнопок нового стиля (roadmap, косметические правки —
     * "кнопки должны быть кнопками", активна/неактивна/выбрана должны визуально
     * отличаться). Это и есть тот переиспользуемый "класс": не State­ListDrawable — на
     * реальном устройстве state_activated и пустой catch-all item селектора не
     * подхватывались (проверено, см. историю правок), поэтому фон и цвет текста
     * переключаются явно кодом при каждой смене состояния. Нажатие — отдельно, системный
     * ripple через android:foreground в PipWizardButtonStyle, не через эти функции.
     *
     * backgroundTintList тут — уже не обход бага (AppCompat раньше сам тянул его от
     * colorPrimary поверх нашего drawable, см. историю правок), а осознанное тонирование:
     * сами drawable (pip_wizard_button_bg_*) нейтрального цвета, реальный акцент даёт этот
     * тинт, поэтому смена темы красит и мод-селект, и мастер, без 4 копий каждого drawable.
     */
    private fun setWizardButtonState(button: Button, selected: Boolean) {
        val accent = currentWizardAccentColor()
        button.backgroundTintList = ColorStateList.valueOf(accent)
        if (selected) {
            button.setBackgroundResource(R.drawable.pip_wizard_button_bg_selected)
            button.setTextColor(accent)
        } else {
            button.setBackgroundResource(R.drawable.pip_wizard_button_bg_active)
            button.setTextColor(ContextCompat.getColor(this, R.color.pip_button_text_dark))
        }
    }
    private fun setWizardButtonDisabled(button: Button) {
        val accent = currentWizardAccentColor()
        button.backgroundTintList = ColorStateList.valueOf(accent)
        button.setBackgroundResource(R.drawable.pip_wizard_button_bg_disabled)
        button.setTextColor(ColorUtils.setAlphaComponent(accent, 0x4D))
    }
    /**
     * Кнопки в вертикальном столбце (wrap_content каждая) иначе "скачут" по ширине вслед
     * за длиной своего текста/локали — измеряем натуральную ширину каждой (без реального
     * layout-прохода, unspecified spec) и растягиваем все под самую широкую.
     */
    private fun equalizeButtonWidths(vararg buttons: Button) {
        val widest = buttons.maxOf {
            it.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            it.measuredWidth
        }
        buttons.forEach { it.layoutParams = it.layoutParams.apply { width = widest } }
    }
    /**
     * Раньше была локальной функцией внутри setupModeSelectScreen() — вынесена в метод,
     * т.к. теперь есть второй вызывающий: openModeSelectScreen() (кнопка "Изменить" в
     * Settings, roadmap — "Режим работы" вместо легаси Screen Resize), которому нужно
     * заранее подсветить именно текущий активный режим, а не всегда PHONE.
     */
    private fun showModeDescription(mode: PipBoyMode) {
        val ms = bindingMain.incLayoutTabModeSelect
        modeSelectHighlighted = mode
        ms.tvModeSelectDescription.text = when (mode) {
            PipBoyMode.PHONE -> getString(R.string.mode_description_phone)
            PipBoyMode.PIPBOY_2000 -> getString(R.string.mode_description_pipboy_2000)
            PipBoyMode.PIPBOY_3000 -> getString(R.string.mode_description_pipboy_3000)
        }
        // Подсветка выбранного пункта — тем же приёмом, что у списка Perks (RecyclerView,
        // roadmap "Косметические правки мастера").
        modeSelectAdapter.setSelectedMode(mode)
        // PipBoy 3000 пока нельзя выбрать — можно только прочитать описание. Кнопка
        // остаётся кликабельной, чтобы поймать тап и проиграть звук ошибки.
        if (mode != PipBoyMode.PIPBOY_3000) {
            setWizardButtonState(ms.btnModeSelectConfirm, selected = false)
        } else {
            setWizardButtonDisabled(ms.btnModeSelectConfirm)
        }
    }
    /**
     * Человекочитаемое название режима — переиспользует те же строки, что и кнопки
     * экрана выбора режима, чтобы подпись в Settings ("Режим работы: ...") не расходилась
     * с тем, что игрок видит на самом экране выбора.
     */
    private fun pipBoyModeDisplayName(mode: PipBoyMode): String = when (mode) {
        PipBoyMode.PHONE -> getString(R.string.mode_phone)
        PipBoyMode.PIPBOY_2000 -> getString(R.string.mode_pipboy_2000)
        PipBoyMode.PIPBOY_3000 -> getString(R.string.mode_pipboy_3000)
    }
    /**
     * Строка "Режим работы: <текущий режим>" в Settings (roadmap, косметические правки —
     * заменили легаси Screen Resize) — обновляется и при первой загрузке значений
     * настроек, и сразу после реального выбора режима в selectPipBoyMode(), чтобы не
     * требовать перезапуска приложения для отражения смены режима.
     */
    private fun refreshModeSettingsLabel() {
        val label = "${getString(R.string.settings_4_name)} ${pipBoyModeDisplayName(pipBoyMode)}"
        bindingMain.incLayoutSettingsGlobal.tvSettings4.text = label
    }
    /**
     * Точка входа в "Режим работы" из Settings (кнопка "Изменить") — по выбору пользователя
     * (roadmap, косметические правки) весь поток идёт с самого начала, ровно как при первом
     * запуске приложения: экран выбора режима -> (для PipBoy 2000/3000) весь мастер заново.
     * Подсвечиваем сразу текущий активный режим, а не всегда "Телефон".
     */
    private fun openModeSelectScreen() {
        showModeDescription(pipBoyMode)
        bindingMain.incLayoutTabModeSelect.root.visibility = View.VISIBLE
    }
    private fun setupModeSelectScreen() {
        val ms = bindingMain.incLayoutTabModeSelect

        // Текст описания — тоже акцентом текущей темы, не жёстко зелёным (смысл темы —
        // красить весь экран, не только кнопки, см. currentWizardAccentColor()).
        ms.tvModeSelectDescription.setTextColor(currentWizardAccentColor())

        // Список режимов — тот же паттерн, что у Perks (RecyclerView + описание справа,
        // roadmap "Косметические правки мастера"), вместо трёх отдельных кнопок.
        ms.recyclerModeSelect.layoutManager = LinearLayoutManager(this)
        modeSelectAdapter = ModeSelectAdapter(
            modes = listOf(PipBoyMode.PHONE, PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000),
            modeLabel = { pipBoyModeDisplayName(it) },
            selectedButtonBackground = selected_button,
            selectedMode = PipBoyMode.PHONE
        ) { mode ->
            // Пункт списка (не кнопка/таб) — тот же звук, что у выбора перка/атрибута/
            // навыка на SPECIAL/Skills/Perks (roadmap, "Редизайн экрана фильтра —
            // UX-спецификация"), был ошибочно звук кнопок (playNewTabSelectAudio()).
            playItemSelectAudio()
            showModeDescription(mode)
        }
        ms.recyclerModeSelect.adapter = modeSelectAdapter

        showModeDescription(PipBoyMode.PHONE)
        ms.btnModeSelectConfirm.setOnClickListener {
            if (modeSelectHighlighted == PipBoyMode.PIPBOY_3000) {
                playErrorAudio()
                return@setOnClickListener
            }
            playNewTabSelectAudio()
            selectPipBoyMode(modeSelectHighlighted)
        }
    }
    private fun selectPipBoyMode(mode: PipBoyMode) {
        // Смена режима через Settings ("Изменить") может застать эмбиент уже играющим
        // (предыдущий режим/сессия) — глушим его здесь безусловно, до входа в мастер, а не
        // только в finishPhoneModeSetup()/finishBootSequence(): иначе он звучит через весь
        // мастер настройки и заставку, включая состояние выключенного PipBoy до первого
        // POWER:1, где эмбиенту быть не должно. Для Телефона он тут же запустится заново
        // через finishPhoneModeSetup(), для PipBoy 2000/3000 — только после реального POWER.
        stopAmbientBackgroundSound()
        pipBoyMode = mode
        sharedPreferences.edit().putString(pipBoyMode_SPKey, mode.name).apply()
        refreshModeSettingsLabel()
        bindingMain.incLayoutTabModeSelect.root.visibility = View.GONE

        // Экран выбора режима можно открыть и поверх Settings (кнопка "Изменить" режима
        // работы) — тогда после выбора режима Settings остаётся видимым под ним (просто
        // временно перекрыт), и пользователь видит его вместо мастера/STATS, пока не
        // закроет вручную. Мастер не должен прерываться, поэтому Settings тоже закрываем
        // здесь — как обычным крестиком (btnSettingsClose), с тем же восстановлением
        // нижних кнопок/свайпа, которые открытие Settings отключает.
        if (bindingMain.incLayoutSettingsGlobal.root.visibility == View.VISIBLE) {
            bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
            enableDisableBottomButtons(true, listBottomButtons)
            enableDisableTopSwipe(true)
        }

        // На свежей установке ShowTutorial=true, и есть давно существующий код, который
        // на старте прячет constraintlayoutMain и показывает вместо него Tutorial. Экран
        // выбора режима теперь главный "первый экран" приложения — Tutorial ему больше не
        // предшествует, а весь основной контент (STATS/ITEMS/DATA) должен быть готов под
        // капотом сразу после выбора режима, а не оставаться скрытым.
        bindingMain.constraintlayoutTutorial.visibility = View.GONE
        bindingMain.constraintlayoutMain.visibility = View.VISIBLE

        when (mode) {
            PipBoyMode.PHONE -> {
                // Раньше сразу приземлялись на STATS, вообще не спрашивая разрешения
                // (roadmap, "Косметические правки мастера" — упущение: геопозиция и
                // уведомления нужны и в этом режиме, не только Bluetooth). Теперь идём через
                // тот же экран PERMISSIONS мастера — если уже выданы, showWizardStep() сам
                // пропустит его и сразу вызовет finishPhoneModeSetup().
                bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.VISIBLE
                showWizardStep(PipBoyWizardStep.PERMISSIONS)
            }
            PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000 -> {
                // PIPBOY_3000 пока ведёт себя как PIPBOY_2000 — заглушка на будущее, своя
                // конфигурация внешнего железа появится отдельно (roadmap, видение).
                setPowerOffInstant() // безопасный дефолт OFF, пока не пришёл первый POWER
                bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.VISIBLE
                showWizardStep(PipBoyWizardStep.HARDWARE_INSTRUCTIONS)
            }
        }
    }
    /**
     * Завершение флоу режима Телефон — раньше выполнялось сразу внутри selectPipBoyMode(),
     * теперь отложено до момента, пока не разрешится экран PERMISSIONS (уже был выдан,
     * пропущен автоматически, или выдан только что через системный диалог).
     */
    private fun finishPhoneModeSetup() {
        bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
        resetToFullScreen()
        bindingMain.viewPowerOff.animate().cancel()
        bindingMain.viewPowerOff.visibility = View.GONE
        stopBleService()
        menuChangeBLE("STATS")
        menuNavigator.resetToRoot(statsMenuRoot())
        // Телефонный режим не проходит через POWER/applyPowerState() (нет ни
        // ESP32, ни самой загрузки) — фоновый глитч иначе никогда бы не
        // запустился. cancelBootSequence() на всякий случай гасит чужую цепочку,
        // если до этого игрок был в режиме PipBoy 2000/3000 с уже идущим глитчем.
        cancelBootSequence()
        startContinuousGlitch()
        startAmbientBackgroundSound()
    }

    /**
     * Восстановление после убийства процесса в фоне (roadmap, "Восстановление состояния
     * после убийства процесса — спецификация", этап 15) — вызывается из onCreate() только
     * когда savedInstanceState != null (Android сам гарантирует, что это именно
     * восстановление, не холодный старт). Полностью пропускает дисклеймер и мастер выбора
     * режима/PipBoy 2000/3000 — не трогает их обычный путь показа при первом запуске,
     * только эту отдельную ветку.
     */
    private fun restoreAppState(savedInstanceState: Bundle) {
        bindingMain.constraintlayoutTutorial.visibility = View.GONE
        bindingMain.incLayoutTabModeSelect.root.visibility = View.GONE
        bindingMain.constraintlayoutMain.visibility = View.VISIBLE

        val restoredMode = try {
            PipBoyMode.valueOf(savedInstanceState.getString(KEY_PIPBOY_MODE, PipBoyMode.PHONE.name))
        } catch (e: IllegalArgumentException) {
            PipBoyMode.PHONE
        }
        pipBoyMode = restoredMode
        refreshModeSettingsLabel()
        when (restoredMode) {
            PipBoyMode.PHONE -> finishPhoneModeSetup()
            PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000 -> {
                // Мастер уже был пройден в прошлой (убитой) сессии — не переоткрываем его
                // заново. Экран состояния (ON/OFF) — во власти ESP32 (см. applyPowerState()),
                // мы не можем знать его сейчас без реального BLE-переподключения, поэтому
                // безопасный дефолт тот же, что и при первом входе в мастер (setPowerOffInstant()),
                // а не попытка угадать "было включено". checkPermissions() сам либо пропустит
                // (уже выданы) и переподключит BLE, либо покажет системный диалог.
                bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
                loadViewState()
                setPowerOffInstant()
                checkPermissions()
            }
        }

        // Раздел + вкладка (roadmap, спека, п.3) — тот же путь, что и BLE-команды STATS/
        // ITEMS/DATA/RADIO (handleBleCommand()), но resetToRootAtIndex() вместо resetToRoot():
        // прыжок сразу на сохранённую позицию, без проигрывания onSelect() промежуточных
        // пунктов, через которые пришлось бы пройти повторными moveCursor().
        val restoredMenu = savedInstanceState.getString(KEY_CUR_MENU, "STATS")
            ?.takeIf { it in setOf("STATS", "ITEMS", "DATA", "RADIO") } ?: "STATS"
        val restoredRootCursor = savedInstanceState.getInt(KEY_ROOT_CURSOR, 0)
        val rootNodes = when (restoredMenu) {
            "ITEMS" -> itemsMenuRoot()
            "DATA" -> dataMenuRoot()
            "RADIO" -> radioMenuRoot()
            else -> statsMenuRoot()
        }
        menuChangeBLE(restoredMenu)
        menuNavigator.resetToRootAtIndex(rootNodes, restoredRootCursor)

        // Система ранений (roadmap, спека, п.4) — присваиваем поля напрямую, затем
        // переприменяем визуал уже существующими функциями. CRIPPLED-конечности —
        // отдельно ниже, applyCrippledVisual()/applyDeathVisuals() рисуют "по известному
        // значению", в отличие от toggleCrippled*(), которые бы его инвертировали.
        woundPhase = try {
            WoundPhase.valueOf(savedInstanceState.getString(KEY_WOUND_PHASE, WoundPhase.NONE.name))
        } catch (e: IllegalArgumentException) { WoundPhase.NONE }
        woundSeverity = try {
            WoundSeverity.valueOf(savedInstanceState.getString(KEY_WOUND_SEVERITY, WoundSeverity.LIGHT.name))
        } catch (e: IllegalArgumentException) { WoundSeverity.LIGHT }
        crippledHead = savedInstanceState.getBoolean(KEY_CRIPPLED_HEAD)
        crippledTorso = savedInstanceState.getBoolean(KEY_CRIPPLED_TORSO)
        crippledLeftArm = savedInstanceState.getBoolean(KEY_CRIPPLED_LEFT_ARM)
        crippledRightArm = savedInstanceState.getBoolean(KEY_CRIPPLED_RIGHT_ARM)
        crippledLeftLeg = savedInstanceState.getBoolean(KEY_CRIPPLED_LEFT_LEG)
        crippledRightLeg = savedInstanceState.getBoolean(KEY_CRIPPLED_RIGHT_LEG)
        statusCursorRow = statusCursorRowFromIndex(savedInstanceState.getInt(KEY_STATUS_CURSOR_ROW))
        timerState = try {
            TimerState.valueOf(savedInstanceState.getString(KEY_TIMER_STATE, TimerState.IDLE.name))
        } catch (e: IllegalArgumentException) { TimerState.IDLE }
        timerTargetEpochMillis = savedInstanceState.getLong(KEY_TIMER_TARGET_EPOCH)
        timerRemainingSecondsAtPause = savedInstanceState.getInt(KEY_TIMER_REMAINING_AT_PAUSE)

        applyWoundFace()
        updateWoundButtonsUI()
        updateWoundStatusLine()
        syncClockTimerScreenVisibility()
        updateClockTimerLabel()
        if (woundPhase == WoundPhase.DEAD) {
            applyDeathVisuals()
        } else {
            val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
            applyCrippledVisual(cnd.imgTabStatusCndPipboyHead, cnd.tvTabStatusCndPipboyHeadHpCrippled, crippledHead, R.drawable.man_head, R.drawable.head_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyTorso, cnd.tvTabStatusCndPipboyTorsoHpCrippled, crippledTorso, R.drawable.torso, R.drawable.torso_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftArm, cnd.tvTabStatusCndPipboyLeftArmHpCrippled, crippledLeftArm, R.drawable.man_arm_left, R.drawable.left_arm_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyRightArm, cnd.tvTabStatusCndPipboyRightArmHpCrippled, crippledRightArm, R.drawable.man_arm_right, R.drawable.right_arm_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftLeg, cnd.tvTabStatusCndPipboyLeftLegHpCrippled, crippledLeftLeg, R.drawable.man_leg_left, R.drawable.left_leg_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyRightLeg, cnd.tvTabStatusCndPipboyRightLegHpCrippled, crippledRightLeg, R.drawable.man_leg_right, R.drawable.right_leg_broken)
        }
        // checkTimerFiring() трогать не нужно (roadmap, спека, п.5) — если таймер истёк,
        // пока процесс был мёртв, обычный следующий тик 300мс-цикла обработает это сам.
    }

    /**
     * Мастер настройки PipBoy 2000/3000 (roadmap, UX-спецификация мастера) — 5 шагов
     * поверх тёмного экрана: Hardware Instructions -> Display Area -> Permissions ->
     * Pairing -> подсказка про POWER. Реальный выход из POWER_HINT — только физическое
     * нажатие POWER на корпусе (applyPowerState(true) прячет весь мастер целиком).
     */
    private enum class PipBoyWizardStep { HARDWARE_INSTRUCTIONS, DISPLAY_AREA, PERMISSIONS, PAIRING, POWER_HINT }

    private fun showWizardStep(step: PipBoyWizardStep) {
        val w = bindingMain.incLayoutPipboy2000Wizard
        // Рамка (тонкие линии) — только под шагами 2-5, не под подсказкой POWER: это
        // чёрный экран состояния OFF, а не страница мастера (roadmap "Косметические
        // правки мастера" — рамка не должна была затрагивать этот шаг вообще, но
        // chrome_frame раньше не прятался и оставался виден поверх/позади него).
        w.layoutWizardChromeFrame.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.GONE else View.VISIBLE
        w.layoutWizardHardware.visibility = if (step == PipBoyWizardStep.HARDWARE_INSTRUCTIONS) View.VISIBLE else View.GONE
        w.layoutWizardDisplayArea.visibility = if (step == PipBoyWizardStep.DISPLAY_AREA) View.VISIBLE else View.GONE
        w.layoutWizardPermissions.visibility = if (step == PipBoyWizardStep.PERMISSIONS) View.VISIBLE else View.GONE
        w.layoutWizardPairing.visibility = if (step == PipBoyWizardStep.PAIRING) View.VISIBLE else View.GONE
        w.layoutWizardPowerHint.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.VISIBLE else View.GONE
        w.tvWizardPowerHint.visibility = View.VISIBLE
        w.btnWizardHideHint.visibility = View.VISIBLE

        // Скан идёт только пока реально показан шаг PAIRING — начинаем/останавливаем
        // строго по факту показа шага, не полагаясь на то, что игрок сам нажмёт кнопку.
        if (step == PipBoyWizardStep.PAIRING) {
            startPairingScan()
        } else {
            stopPairingScan()
        }

        // Регулировка рабочей области жестом активна только пока реально показан этот шаг.
        isResizing = (step == PipBoyWizardStep.DISPLAY_AREA)
        if (step == PipBoyWizardStep.DISPLAY_AREA) {
            // Доп. пол на размер при пинче, чтобы собственные заголовок/подсказка/3 кнопки
            // этого шага не могли перестать помещаться и вылезти за границы экрана (см.
            // ScaleListener.onScale) — отчёт по живому тесту, кнопка "Отмена" уезжала.
            val displayMetrics = resources.displayMetrics
            wizardMinContentWidthPx = (displayMetrics.widthPixels * 0.6f).toInt()
            wizardMinContentHeightPx = (displayMetrics.heightPixels * 0.7f).toInt()
            // Персистентный сброс — стартовая точка регулировки. Экран выбора режима
            // показывается при каждом запуске, подхватывать масштаб из прошлой сессии не надо.
            resetToFullScreen()
        } else if (step == PipBoyWizardStep.HARDWARE_INSTRUCTIONS) {
            // До этого шага область ещё не настраивалась в этом прогоне мастера —
            // полноэкранный информационный экран, ничего настроенного тут перезаписать нельзя.
            wizardMinContentWidthPx = 0
            wizardMinContentHeightPx = 0
            applyTemporaryFullScreenLayout()
        } else if (pipBoyMode == PipBoyMode.PHONE) {
            // Режим Телефон не проходит через DISPLAY AREA вообще — рабочая область всегда
            // fullscreen (это концепция только для аппаратного PipBoy 2000/3000), сохранённое
            // loadViewState() тут ни при чём и могло бы ошибочно подставить чужой размер.
            wizardMinContentWidthPx = 0
            wizardMinContentHeightPx = 0
            resetToFullScreen()
        } else {
            // PERMISSIONS/POWER_HINT — идут ПОСЛЕ "Готово" на шаге DISPLAY AREA, область уже
            // настроена игроком и сохранена (см. saveViewState в ScaleListener/resetToFullScreen).
            // Раньше здесь стоял applyTemporaryFullScreenLayout() до реального POWER от
            // железа — из-за этого игрок в отчёте по тесту видел fullscreen сразу после
            // "Готово" вместо настроенной области. Область нужна fullscreen только на миг
            // реального системного диалога разрешений — это делает отдельно
            // checkPermissions()/permissionRequestLauncher, здесь применяем сохранённое.
            wizardMinContentWidthPx = 0
            wizardMinContentHeightPx = 0
            loadViewState()
        }

        if (step == PipBoyWizardStep.PERMISSIONS && hasAllRequiredPermissions()) {
            // Уже выданы раньше — не задерживаем игрока на этом экране.
            if (pipBoyMode == PipBoyMode.PHONE) {
                finishPhoneModeSetup()
            } else {
                setupBluetooth()
                showWizardStep(PipBoyWizardStep.PAIRING)
            }
        }
    }
    private fun applyTemporaryFullScreenLayout() {
        val layoutParams = bindingMain.root.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.leftMargin = 0
        layoutParams.topMargin = 0
        bindingMain.root.layoutParams = layoutParams
    }
    private fun hasAllRequiredPermissions(): Boolean {
        return requiredPermissionsForCurrentMode().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    /**
     * Шаг PAIRING мастера (roadmap) — скан по Service UUID Nordic UART (та же константа,
     * что уже была дефолтом bluetoothSUUID_SPKey в Settings) вместо ручного ввода MAC.
     * Пока у каждого корпуса нет уникального BLE-имени (roadmap, "Периферия" — отложено до
     * серийного производства, сейчас только один тестовый корпус) список может показывать
     * несколько одинаково подписанных устройств — различать по имени пока не требуется.
     */
    private var pairingScanCallback: ScanCallback? = null
    private val pairingFoundAddresses = mutableSetOf<String>()
    private val pairingScanTimeoutRunnable = Runnable { stopPairingScan() }
    private val pairingScanDurationMs = 15000L

    @SuppressLint("MissingPermission")
    private fun startPairingScan() {
        stopPairingScan()
        val w = bindingMain.incLayoutPipboy2000Wizard
        w.layoutWizardPairingDevices.removeAllViews()
        pairingFoundAddresses.clear()
        w.tvWizardPairingStatus.text = getString(R.string.wizard_pairing_scanning)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            w.tvWizardPairingStatus.text = getString(R.string.wizard_pairing_bluetooth_off)
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            w.tvWizardPairingStatus.text = getString(R.string.wizard_pairing_scan_failed)
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                addPairingDevice(result.device.address, result.device.name ?: result.scanRecord?.deviceName)
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e("MainActivity", "BLE scan failed: $errorCode")
                w.tvWizardPairingStatus.text = getString(R.string.wizard_pairing_scan_failed)
            }
        }
        pairingScanCallback = callback
        scanner.startScan(listOf(filter), settings, callback)
        handler.postDelayed(pairingScanTimeoutRunnable, pairingScanDurationMs)
    }

    @SuppressLint("MissingPermission")
    private fun stopPairingScan() {
        handler.removeCallbacks(pairingScanTimeoutRunnable)
        val callback = pairingScanCallback ?: return
        pairingScanCallback = null
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter?.isEnabled == true) {
            adapter.bluetoothLeScanner?.stopScan(callback)
        }
        if (pairingFoundAddresses.isEmpty()) {
            bindingMain.incLayoutPipboy2000Wizard.tvWizardPairingStatus.text = getString(R.string.wizard_pairing_none_found)
        }
    }

    private fun addPairingDevice(address: String, name: String?) {
        if (!pairingFoundAddresses.add(address)) return
        val w = bindingMain.incLayoutPipboy2000Wizard
        w.tvWizardPairingStatus.text = getString(R.string.wizard_pairing_found, pairingFoundAddresses.size)
        val button = Button(this, null, 0, R.style.PipWizardButtonStyle).apply {
            text = name ?: address
            backgroundTintList = ColorStateList.valueOf(currentWizardAccentColor())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            setOnClickListener {
                playNewTabSelectAudio()
                selectPairingDevice(address)
            }
        }
        w.layoutWizardPairingDevices.addView(button)
    }

    /** Игрок выбрал свой корпус из списка — сохраняем MAC и (пере)подключаемся тем же
     * механизмом, что и кнопка Connect в Settings (bleService.reconnectWithCurrentSettings). */
    private fun selectPairingDevice(address: String) {
        stopPairingScan()
        sharedPreferences.edit().putString(bluetoothMAC_SPKey, address).apply()
        val service = bleService
        if (service != null) {
            service.reconnectWithCurrentSettings()
        } else {
            startAndBindBleService()
        }
        showWizardStep(PipBoyWizardStep.POWER_HINT)
    }
    private fun setupPipBoy2000Wizard() {
        val w = bindingMain.incLayoutPipboy2000Wizard

        // Эти кнопки никогда не переключают background программно (setWizardButtonState/
        // setWizardButtonDisabled их не касаются) — они лишь один раз показывают фон,
        // заданный в style="@style/PipWizardButtonStyle" при инфлейте разметки, всегда в
        // виде активной сплошной заливки. Тонируем акцентом текущей темы (см.
        // currentWizardAccentColor()/setWizardButtonState) — тот же приём, что и там, чтобы
        // мастер выглядел согласованно с выбранной темой, а не жёстко зелёным.
        val wizardAccent = currentWizardAccentColor()
        listOf(
            w.btnWizardHardwareBack,
            w.btnWizardHardwareNext,
            w.btnWizardDone,
            w.btnWizardReset,
            w.btnWizardCancel,
            w.btnWizardPermissionsBack,
            w.btnWizardGrantPermissions,
            w.btnWizardPairingBack,
            w.btnWizardPairingRescan,
            w.btnWizardPairingSkipDebug,
            w.btnWizardHideHint
        ).forEach { it.backgroundTintList = ColorStateList.valueOf(wizardAccent) }

        // Заголовки и основной текст шагов мастера — тем же акцентом (смысл темы —
        // красить весь экран, не только кнопки), не жёстко зелёным.
        listOf(
            w.tvWizardHardwareTitle,
            w.tvWizardHardwareText,
            w.tvWizardDisplayAreaTitle,
            w.tvWizardHint,
            w.tvWizardPermissionsTitle,
            w.tvWizardPermissionsText,
            w.tvWizardPairingTitle,
            w.tvWizardPairingStatus,
            w.tvWizardPowerHint
        ).forEach { it.setTextColor(wizardAccent) }

        // Шаг 3: одна ширина у [Готово]/[Сбросить]/[Отмена] в горизонтальном ряду (roadmap,
        // "Косметические правки мастера") — тот же приём, что и у кнопок дисклеймера.
        equalizeButtonWidths(w.btnWizardDone, w.btnWizardReset, w.btnWizardCancel)

        // Шаг 2: Hardware Instructions
        w.btnWizardHardwareBack.setOnClickListener {
            playNewTabSelectAudio()
            w.root.visibility = View.GONE
            bindingMain.incLayoutTabModeSelect.root.visibility = View.VISIBLE
        }
        w.btnWizardHardwareNext.setOnClickListener {
            playNewTabSelectAudio()
            showWizardStep(PipBoyWizardStep.DISPLAY_AREA)
        }

        // Шаг 3: Display Area
        w.btnWizardDone.setOnClickListener {
            playNewTabSelectAudio()
            showWizardStep(PipBoyWizardStep.PERMISSIONS)
        }
        w.btnWizardReset.setOnClickListener {
            playNewTabSelectAudio()
            resetToFullScreen()
        }
        w.btnWizardCancel.setOnClickListener {
            playNewTabSelectAudio()
            showWizardStep(PipBoyWizardStep.HARDWARE_INSTRUCTIONS)
        }

        // Шаг 4: Permissions. В режиме Телефон это первый и единственный шаг мастера
        // (нет ни HARDWARE_INSTRUCTIONS, ни DISPLAY AREA перед ним) — [Назад] ведёт на
        // экран выбора режима, а не на несуществующий для этого режима предыдущий шаг.
        w.btnWizardPermissionsBack.setOnClickListener {
            playNewTabSelectAudio()
            if (pipBoyMode == PipBoyMode.PHONE) {
                w.root.visibility = View.GONE
                bindingMain.incLayoutTabModeSelect.root.visibility = View.VISIBLE
            } else {
                showWizardStep(PipBoyWizardStep.DISPLAY_AREA)
            }
        }
        w.btnWizardGrantPermissions.setOnClickListener {
            playNewTabSelectAudio()
            checkPermissions()
        }

        // Шаг 5: Pairing
        w.btnWizardPairingBack.setOnClickListener {
            playNewTabSelectAudio()
            stopPairingScan()
            showWizardStep(PipBoyWizardStep.PERMISSIONS)
        }
        w.btnWizardPairingRescan.setOnClickListener {
            playNewTabSelectAudio()
            startPairingScan()
        }
        // Обход пейринга в debug-сборках — без реального ESP32 иначе нельзя пройти
        // мастер дальше этого шага вообще (roadmap, этап 7, "быстрая отладка логики
        // экранов"). Не трогает bluetoothMAC_SPKey и не пытается подключиться — просто
        // пропускает шаг, как будто корпус уже выбран.
        w.btnWizardPairingSkipDebug.visibility =
            if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        w.btnWizardPairingSkipDebug.setOnClickListener {
            playNewTabSelectAudio()
            stopPairingScan()
            showWizardStep(PipBoyWizardStep.POWER_HINT)
        }

        // Шаг 6: подсказка про POWER
        w.btnWizardHideHint.setOnClickListener {
            playNewTabSelectAudio()
            w.tvWizardPowerHint.visibility = View.GONE
            w.btnWizardHideHint.visibility = View.GONE
        }
    }


    /***********************************************************************************************************
     * SCREEN SIZE
     **********************************************************************************************************/
    // Helper function to get the height of the status bar
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    // Helper function to get the height of the navigation bar
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var initialSpanX = 0f
        private var initialSpanY = 0f
        private val dampingFactor = 0.04f
        private val minScaleThreshold = 0.003f // Minimum scale factor change to trigger scaling

        private var originalWidth = 0
        private var originalHeight = 0

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            initialSpanX = detector.currentSpanX
            initialSpanY = detector.currentSpanY

            // Store the original dimensions if not already stored
            if (originalWidth == 0 || originalHeight == 0) {
                originalWidth = bindingMain.root.width
                originalHeight = bindingMain.root.height
            }

            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val layoutParams = bindingMain.root.layoutParams as ViewGroup.MarginLayoutParams
            val scaleXChange  = (detector.currentSpanX / initialSpanX - 1) * dampingFactor
            val scaleYChange = (detector.currentSpanY / initialSpanY - 1) * dampingFactor

            // Only proceed with scaling if the change is above the threshold
            if (scaleXChange.absoluteValue > minScaleThreshold || scaleYChange.absoluteValue > minScaleThreshold) {
                val scaleX = 1 + scaleXChange
                val scaleY = 1 + scaleYChange

                newWidth = max((originalWidth * 0.5).toInt(), (bindingMain.root.width * scaleX).toInt())
                newHeight = max((originalHeight * 0.75).toInt(), (bindingMain.root.height * scaleY).toInt())
                // Доп. пол на время шага DISPLAY AREA мастера PipBoy (roadmap, косметические
                // правки) — без него собственный контент этого шага (заголовок, подсказка,
                // 3 кнопки) мог перестать помещаться в уменьшенную область и вылезти за
                // физические границы экрана телефона. В остальное время (обычная настройка
                // области в Settings) оба порога — 0, ни на что не влияют.
                newWidth = max(newWidth, wizardMinContentWidthPx)
                newHeight = max(newHeight, wizardMinContentHeightPx)

                val displayMetrics = resources.displayMetrics
                val statusBarHeight = getStatusBarHeight()
                val navigationBarHeight = getNavigationBarHeight()

                val clampedWidth = min(newWidth, displayMetrics.widthPixels)
                val clampedHeight = min(newHeight, displayMetrics.heightPixels)

                // Держать центр области на месте при масштабировании, а не левый верхний
                // угол — иначе после щипка область "уезжает" в угол экрана вместо того,
                // чтобы сжиматься/расти от текущего положения (потом ещё и драг не мог
                // вернуть её в удобное место, если размер уже упирался в границы экрана).
                val widthDelta = clampedWidth - bindingMain.root.width
                val heightDelta = clampedHeight - bindingMain.root.height
                var newLeftMargin = layoutParams.leftMargin - widthDelta / 2
                var newTopMargin = layoutParams.topMargin - heightDelta / 2
                newLeftMargin = max(0, min(newLeftMargin, displayMetrics.widthPixels - clampedWidth))
                newTopMargin = max(0, min(newTopMargin, displayMetrics.heightPixels - clampedHeight))

                layoutParams.width = clampedWidth
                layoutParams.height = clampedHeight
                layoutParams.leftMargin = newLeftMargin
                layoutParams.topMargin = newTopMargin

                bindingMain.root.layoutParams = layoutParams
                saveViewState(layoutParams)
            }
            return true
        }
    }
    private fun handleTouch(event: MotionEvent) {
        when (event.pointerCount) {
            1 -> handleMove(event)
            2 -> scaleGestureDetector.onTouchEvent(event)
        }
    }
    private fun handleMove(event: MotionEvent) {
        val layoutParams = bindingMain.root.layoutParams as ViewGroup.MarginLayoutParams

        // Check if the view is full screen
        val isFullScreen = layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT && layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT

        // If the view is full screen, do nothing
        if (isFullScreen) return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY

                var newLeftMargin = layoutParams.leftMargin + dx.toInt()
                var newTopMargin = layoutParams.topMargin + dy.toInt()

                val displayMetrics = resources.displayMetrics
                val statusBarHeight = getStatusBarHeight()
                val navigationBarHeight = getNavigationBarHeight()

                newLeftMargin = max(0, min(newLeftMargin, displayMetrics.widthPixels - layoutParams.width))
                newTopMargin = max(0, min(newTopMargin, displayMetrics.heightPixels - layoutParams.height))

                layoutParams.leftMargin = newLeftMargin
                layoutParams.topMargin = newTopMargin

                bindingMain.root.layoutParams = layoutParams

                lastX = event.rawX
                lastY = event.rawY

                saveViewState(layoutParams)
            }
        }
    }
    private fun loadViewState() {
        val width = sharedPreferences.getInt("width", ViewGroup.LayoutParams.MATCH_PARENT)
        val height = sharedPreferences.getInt("height", ViewGroup.LayoutParams.MATCH_PARENT)
        val leftMargin = sharedPreferences.getInt("leftMargin", 0)
        val topMargin = sharedPreferences.getInt("topMargin", 0)

        val layoutParams = bindingMain.root.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = width
        layoutParams.height = height
        layoutParams.leftMargin = leftMargin
        layoutParams.topMargin = topMargin

        bindingMain.root.layoutParams = layoutParams
    }
    private fun resetToFullScreen() {
        val layoutParams = bindingMain.root.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.leftMargin = 0
        layoutParams.topMargin = 0

        bindingMain.root.layoutParams = layoutParams

        saveViewState(layoutParams)
    }

    /***********************************************************************************************************
     * MAP
     **********************************************************************************************************/
    /**
     * Открывает экран карты — читает уже импортированный бандл (Settings > Map Data),
     * никаких сетевых проверок/разрешений больше нет (см. MapBundleRepository). Перекрашивает
     * картинку под текущую тему тем же PorterDuff.MULTIPLY, каким раньше тонировались
     * osmdroid-тайлы (loadLocalMap()) — только теперь поверх статичного PNG: map.png уже
     * чёрно-белый (falloutize_map.py, colorize=False), цвет накладывает исключительно
     * приложение, не сам бандл.
     */
    private fun openMapScreen() {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        if (!mapBundleRepository.hasBundle()) {
            mapScreen.tvPermissionsCheckResult.visibility = View.VISIBLE
            mapScreen.photoViewMap.visibility = View.GONE
            mapScreen.viewMapOverlay.visibility = View.GONE
            mapScreen.layoutMapSidebar.visibility = View.GONE
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(mapBundleRepository.bundleImageFile().absolutePath)
            val bounds = mapBundleRepository.loadBounds()
            withContext(Dispatchers.Main) {
                if (bitmap == null || bounds == null) {
                    mapScreen.tvPermissionsCheckResult.visibility = View.VISIBLE
                    mapScreen.photoViewMap.visibility = View.GONE
                    mapScreen.viewMapOverlay.visibility = View.GONE
                    mapScreen.layoutMapSidebar.visibility = View.GONE
                    return@withContext
                }
                mapGeoReference = GeoReference(bounds, bitmap.width, bitmap.height)
                mapHasCenteredOnUser = false
                mapScreen.photoViewMap.setImageBitmap(bitmap)
                mapScreen.photoViewMap.colorFilter = PorterDuffColorFilter(currentWizardAccentColor(), PorterDuff.Mode.MULTIPLY)
                mapScreen.photoViewMap.visibility = View.VISIBLE
                mapScreen.tvPermissionsCheckResult.visibility = View.GONE
                mapScreen.viewMapOverlay.visibility = View.VISIBLE
                mapScreen.layoutMapSidebar.visibility = View.VISIBLE
                mapScreen.btnMapRecenter.backgroundTintList = ColorStateList.valueOf(currentWizardAccentColor())
                // Оверлей рисует в пространстве экрана, но хранит точки в пространстве
                // битмапа (см. MapOverlayView) — при любом пане/зуме PhotoView пересчитываем
                // её текущую displayMatrix и заново просим перерисоваться.
                mapScreen.photoViewMap.setOnMatrixChangeListener {
                    val matrix = Matrix()
                    mapScreen.photoViewMap.getDisplayMatrix(matrix)
                    mapScreen.viewMapOverlay.displayMatrix = matrix
                    mapScreen.viewMapOverlay.invalidate()
                }
                startMapLocationUpdates()
            }
        }
    }
    /** Разрешение на геолокацию уже запрошено на старте приложения для всех режимов работы
     * (см. requiredPermissionsForCurrentMode()) — здесь только защитная проверка на случай,
     * если игрok отозвал его позже через системные настройки. */
    @SuppressLint("MissingPermission")
    private fun startMapLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (mapLocationListener != null) return
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location -> onMapLocationUpdate(location) }
        mapLocationListener = listener
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 3f, listener)
        } catch (e: Exception) {
            Log.w("MainActivity", "Не удалось подписаться на обновления геолокации карты", e)
        }
        (currentLocationOrNull())?.let { onMapLocationUpdate(it) }
    }
    /** Останавливать при уходе с экрана карты на другую вкладку ITEMS — не жечь GPS без
     * нужды, когда игрок смотрит Clock/Journal/Geiger. */
    private fun stopMapLocationUpdates() {
        val listener = mapLocationListener ?: return
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(listener)
        mapLocationListener = null
    }
    @SuppressLint("MissingPermission")
    private fun currentLocationOrNull(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }
    private fun onMapLocationUpdate(location: Location) {
        val geoReference = mapGeoReference ?: return
        val overlay = bindingMain.incLayoutTabItemsMap.viewMapOverlay
        overlay.userLocationPx = geoReference.latLonToPixel(location.latitude, location.longitude)
        if (!mapHasCenteredOnUser) {
            mapHasCenteredOnUser = true
            recenterMapOnUser()
        }
    }
    /** Строит матрицу вручную (у PhotoView нет прямого "перейти к точке при том же зуме") —
     * масштаб берётся текущий (не сбрасываем зум игрока), сдвиг подбирается так, чтобы
     * GPS-точка (в пространстве битмапа) оказалась по центру экрана. */
    private fun recenterMapOnUser() {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        val userPx = mapScreen.viewMapOverlay.userLocationPx ?: return
        val photoView = mapScreen.photoViewMap
        // getDisplayMatrix() отдаёт ПОЛНУЮ матрицу (базовая "вписать в экран" + supp,
        // накопленный из жестов пользователя) — по ней корректно находим текущую позицию
        // точки игрока на экране.
        val fullMatrix = Matrix()
        photoView.getDisplayMatrix(fullMatrix)
        val screenPoint = floatArrayOf(userPx.x, userPx.y)
        fullMatrix.mapPoints(screenPoint)
        val dx = photoView.width / 2f - screenPoint[0]
        val dy = photoView.height / 2f - screenPoint[1]
        // setDisplayMatrix(), несмотря на название, пишет НЕ в полную матрицу, а напрямую в
        // supp-матрицу (см. исходники PhotoViewAttacher.setDisplayMatrix —
        // mSuppMatrix.set(finalMatrix)), после чего библиотека сама доклеивает базовую
        // матрицу поверх. Передача туда уже готовой ПОЛНОЙ матрицы удваивала применение
        // базовой — карту утаскивало в левый верхний угол. Правильно — взять ТЕКУЩУЮ
        // supp-матрицу и просто сдвинуть её на экранную дельту (postTranslate всегда сдвигает
        // именно в пикселях конечного экрана, вне зависимости от масштаба внутри матрицы) —
        // ровно так же, как это делает сам жест панорамирования внутри библиотеки.
        val suppMatrix = Matrix()
        photoView.getSuppMatrix(suppMatrix)
        suppMatrix.postTranslate(dx, dy)
        photoView.setDisplayMatrix(suppMatrix)
    }
    /** Обновляет статус-строку в Settings > Map Data — импортирован ли бандл, когда и из
     * какой папки. Вызывается при открытии подпанели и сразу после (не)успешного импорта. */
    private fun refreshMapBundleStatus() {
        val statusView = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsMapBundle.tvMapBundleStatus
        if (!mapBundleRepository.hasBundle()) {
            statusView.text = getString(R.string.map_bundle_status_none)
            return
        }
        val dateText = mapBundleRepository.importedAtEpochMillis()?.let {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))
        } ?: "?"
        val folderName = mapBundleRepository.importedSourceFolderName() ?: "?"
        statusView.text = getString(R.string.map_bundle_status_imported, folderName, dateText)
    }

    /***********************************************************************************************************
     * INTERFACE CHANGES
     **********************************************************************************************************/
    /**
     * Мгновенный "выключенный" вид без звука/анимации — безопасный дефолт при входе в
     * мастер PipBoy 2000/3000 (selectPipBoyMode()), до того как реально пришёл первый
     * POWER. Не то же самое, что applyPowerState(false) ниже — та воспроизводит полную
     * театральную анимацию выключения (roadmap, "Видение приложения", п.11), уместную
     * только в ответ на реальный POWER:0/debug-инъекцию, а не на служебную подготовку
     * экрана мастера (баг: до разделения этих двух функций анимация выключения ошибочно
     * запускалась при заходе в мастер).
     */
    private fun setPowerOffInstant() {
        cancelBootSequence()
        val overlay = bindingMain.viewPowerOff
        overlay.animate().cancel()
        overlay.alpha = 1f
        overlay.visibility = View.VISIBLE
    }

    /**
     * État-машина экрана PipBoy (протокол, раздел 3.1): OFF (чёрный экран) <-> ON.
     * ESP32 — хозяин состояния, применяем как есть, не тумблерим локально. Стартовое
     * состояние экрана — OFF (view_power_off видим по умолчанию в разметке), пока не
     * пришёл первый POWER от ESP32.
     *
     * Вызывается только из реального разбора команды POWER (handleBleCommand()) — полная
     * театральная анимация в обе стороны (playBootSequence()/playShutdownSequence(),
     * roadmap "Видение приложения", п.11), не мгновенная служебная заглушка (см.
     * setPowerOffInstant() выше, для входа в мастер).
     */
    private fun applyPowerState(on: Boolean) {
        if (on) {
            cancelBootSequence()
            playBootSequence()
            // Мастер настройки PipBoy 2000/3000 больше не нужен — POWER реально пришёл.
            bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
            // Пока шли Permissions/подсказка про POWER, окно было временно fullscreen (не
            // персистентно, см. showWizardStep/applyTemporaryFullScreenLayout) — теперь
            // применяем реально настроенную на шаге DISPLAY AREA область для игры.
            loadViewState()
        } else {
            cancelBootSequence()
            playShutdownSequence()
        }
    }

    /***********************************************************************************************************
     * AMBIENT BACKGROUND (roadmap, "Рефакторинг кода" — память фонового процесса) —
     * фоновый гул интерфейса. Раньше создавался и настраивался (loop, громкость) в
     * onCreate(), но start() нигде не вызывался — фича по факту никогда не играла. Теперь
     * создаётся лениво в момент, когда интерфейс реально становится доступен игроку
     * (finishPhoneModeSetup() для Телефона, finishBootSequence() для PipBoy 2000/3000 —
     * после анимации загрузки), и останавливается при выключении экрана
     * (playShutdownSequence()) — экран PipBoy выключен, гула тоже быть не должно.
     * Настройки → "Фоновый эмбиент-звук" (AmbientSoundEnabled, по умолчанию включён) —
     * решение целиком на совести игрока, не авторитетная механика. Как и остальные
     * Settings, применяется после перезапуска Activity кнопкой [Сохранить] (см.
     * saveButtonSettings.setOnClickListener), не живым переключением на лету.
     *
     * [ambientShouldBePlaying] — намерение, отдельно от факта существования плеера:
     * true, пока интерфейс логически "включён" и настройка это разрешает (выставляется
     * в startAmbientBackgroundSound(), сбрасывается только в stopAmbientBackgroundSound()
     * — реальные причины остановки: POWER off, смена режима, onDestroy()). onStop()/
     * onStart() — сворачивание приложения/блокировка экрана — используют отдельную пару
     * releaseAmbientPlayer()/startAmbientBackgroundSound() и НЕ трогают это намерение:
     * ambient должен на время уйти (экономия памяти/декодирования, пока игрок не
     * смотрит на экран) и вернуться сам, без необходимости заново решать, разрешён ли
     * он сейчас логикой режима/шага мастера.
     **********************************************************************************************************/
    private var ambientShouldBePlaying = false
    private fun startAmbientBackgroundSound() {
        if (!sharedPreferences.getBoolean("AmbientSoundEnabled", true)) return
        ambientShouldBePlaying = true
        if (mediaPlayerBackGround?.isPlaying == true) return
        mediaPlayerBackGround?.release()
        mediaPlayerBackGround = MediaPlayer.create(applicationContext, R.raw.background)?.apply {
            isLooping = true
            setVolume(0.5f, 0.5f)
            start()
        }
    }
    private fun releaseAmbientPlayer() {
        mediaPlayerBackGround?.apply {
            try { if (isPlaying) stop() } catch (e: IllegalStateException) {
                Log.w("MainActivity", "mediaPlayerBackGround уже был в неподходящем состоянии для stop()", e)
            }
            release()
        }
        mediaPlayerBackGround = null
    }
    private fun stopAmbientBackgroundSound() {
        ambientShouldBePlaying = false
        releaseAmbientPlayer()
    }

    /***********************************************************************************************************
     * BOOT SEQUENCE (roadmap, "Видение приложения", п.11)
     **********************************************************************************************************/
    private var bootSoundPlayer: MediaPlayer? = null
    // Токен для handler.postAtTime()/removeCallbacksAndMessages() — позволяет оборвать
    // именно шаги анимации загрузки, не трогая остальные отложенные задачи на том же
    // общем handler (скан пейринга и т.п.).
    private val bootSequenceToken = Any()

    private fun bootPostDelayed(delayMs: Long, action: () -> Unit) {
        handler.postAtTime(Runnable { action() }, bootSequenceToken, SystemClock.uptimeMillis() + delayMs)
    }

    /** Кадр 1 -> кадр 2 -> кадр 3 -> основной интерфейс. Реальный ESP32 (POWER:1) и
     * debug-инъекция (dev-tools/ble_key_sim.py, клавиша 'p') ведут сюда одинаково. */
    private fun playBootSequence() {
        playBootSwitchSound()
        val boot = bindingMain.incLayoutBootSequence
        val accent = currentWizardAccentColor()
        // Лого кадра 1 — PNG с альфа-маской (ImageView.tint), своей раскраски кодом не
        // требует, см. layout_boot_sequence.xml.
        boot.tvBootCodewall.setTextColor(accent)
        boot.tvBootTerminal.setTextColor(accent)

        bindingMain.viewPowerOff.visibility = View.GONE
        boot.root.visibility = View.VISIBLE
        boot.layoutBootFrameLogo.visibility = View.VISIBLE
        boot.layoutBootFrameCodewall.visibility = View.GONE
        boot.layoutBootFrameTerminal.visibility = View.GONE

        bootPostDelayed(BOOT_FRAME_LOGO_DURATION_MS) { startBootCodewall() }
        scheduleGlitchPulses(bootTotalDurationMs, BOOT_GLITCH_MIN_PULSES..BOOT_GLITCH_MAX_PULSES)
    }

    /** Суммарная длительность всей заставки (кадр 1 + 2 + печать кадра 3 + пауза на
     * блочном курсоре) — считается, а не хардкодится отдельной константой, чтобы не
     * разъезжаться с реальным временем печати при правке BOOT_TERMINAL_TEXT. */
    private val bootTotalDurationMs: Long
        get() = BOOT_FRAME_LOGO_DURATION_MS + BOOT_FRAME_CODEWALL_DURATION_MS +
            BOOT_TERMINAL_TEXT.length * BOOT_TERMINAL_CHAR_DELAY_MS + BOOT_TERMINAL_END_HOLD_MS

    private fun startBootCodewall() {
        val boot = bindingMain.incLayoutBootSequence
        boot.layoutBootFrameLogo.visibility = View.GONE
        boot.layoutBootFrameCodewall.visibility = View.VISIBLE
        boot.tvBootCodewall.text = BOOT_CODEWALL_TEXT
        startBootSound()

        // Ждём прохода layout, чтобы знать реальную высоту кадра/текста на этом экране,
        // а не гадать заранее — экраны PipBoy 2000/3000 настраиваются по-разному
        // (см. шаг DISPLAY AREA мастера).
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
        val boot = bindingMain.incLayoutBootSequence
        boot.tvBootCodewall.animate().cancel()
        boot.layoutBootFrameCodewall.visibility = View.GONE
        boot.layoutBootFrameTerminal.visibility = View.VISIBLE
        typeTerminalText("", BOOT_TERMINAL_TEXT, 0, boot.tvBootTerminal) {
            bootPostDelayed(BOOT_TERMINAL_END_HOLD_MS) { finishBootSequence() }
        }
    }

    /** Посимвольная печать с блочным курсором — курсор всегда сразу за последним
     * напечатанным символом, включая перевод строки как обычный "символ" темпа печати.
     * [prefix] выводится целиком сразу, без анимации (шапка терминала выключения — тот
     * же приём, что и полностью типизированный [BOOT_TERMINAL_TEXT] для загрузки, где
     * [prefix] пустой), печатается только [body]. [onDone] — что делать после того, как
     * курсор допечатал последний символ. */
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
        bootSoundPlayer = MediaPlayer.create(this, R.raw.boot_typing_click)?.apply {
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

    /** Одноразовый звук POWER:1 (симметрично playShutdownSwitchSound() ниже) — сам себя
     * освобождает по завершении, не зацикленный. */
    private fun playBootSwitchSound() {
        bootSwitchSoundPlayer?.release()
        bootSwitchSoundPlayer = MediaPlayer.create(this, R.raw.ui_switch_on)?.apply {
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
        bindingMain.incLayoutBootSequence.root.visibility = View.GONE
        // Глитч больше не ограничен коротким окном после загрузки — фоновый эффект на
        // всё время, пока PipBoy включён, см. startContinuousGlitch().
        startContinuousGlitch()
        startAmbientBackgroundSound()
    }

    /** Обрывает анимацию (загрузки или выключения) на любом шаге — повторный POWER
     * (вкл. debug-инъекцию) посреди воспроизведения не должен оставлять звук/отложенные
     * шаги/недоигранные fade-анимации висеть. Не проставляет конкретные alpha/visibility
     * у view_power_off — эту часть стартового состояния каждый раз явно готовит тот play*,
     * который запускается сразу следом (playBootSequence()/playShutdownSequence()). */
    private fun cancelBootSequence() {
        handler.removeCallbacksAndMessages(bootSequenceToken)
        val boot = bindingMain.incLayoutBootSequence
        boot.tvBootCodewall.animate().cancel()
        boot.root.animate().cancel()
        boot.root.alpha = 1f
        bindingMain.viewPowerOff.animate().cancel()
        stopBootSound()
        stopBootSwitchSound()
        stopShutdownSwitchSound()
        boot.root.visibility = View.GONE
        bindingMain.ivGlitchOverlay.visibility = View.GONE
        bindingMain.ivGlitchOverlay.setImageDrawable(null)
    }

    /***********************************************************************************************************
     * SHUTDOWN SEQUENCE (roadmap, "Видение приложения", п.11 — довесок к анимации включения)
     **********************************************************************************************************/
    private var shutdownSwitchSoundPlayer: MediaPlayer? = null

    /** Шаг 1-2 спеки: звук щелчка выключения сразу, затем ~2с остаёмся на текущем экране
     * (что бы на нём сейчас ни было) с обычными импульсами глитча — тот же
     * scheduleGlitchPulses(), что и во время/после загрузки. */
    private fun playShutdownSequence() {
        playShutdownSwitchSound()
        stopAmbientBackgroundSound()
        scheduleGlitchPulses(SHUTDOWN_STAY_DURATION_MS, POST_BOOT_GLITCH_MIN_PULSES..POST_BOOT_GLITCH_MAX_PULSES)
        bootPostDelayed(SHUTDOWN_STAY_DURATION_MS) { fadeToShutdownTerminal() }
    }

    private fun playShutdownSwitchSound() {
        shutdownSwitchSoundPlayer?.release()
        shutdownSwitchSoundPlayer = MediaPlayer.create(this, R.raw.ui_switch_off)?.apply {
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

    /** Шаг 3 спеки — текущий (уже глючащий) экран гаснет в чёрное через view_power_off,
     * и только когда фон полностью чёрный, поверх него появляется терминал выключения —
     * тот же чёрный фон, что и в обычном OFF, просто с задержкой на fade вместо мгновенной
     * смены. */
    private fun fadeToShutdownTerminal() {
        val overlay = bindingMain.viewPowerOff
        overlay.animate().cancel()
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate()
            .alpha(1f)
            .setDuration(SHUTDOWN_FADE_TO_BLACK_MS)
            .withEndAction { startShutdownTerminal() }
            .start()
    }

    /** Шаг 4 спеки — шапка PIP-OS выводится сразу (без анимации печати, [prefix] у
     * typeTerminalText()), тело печатается посимвольно под "Clicking" (тот же
     * bootSoundPlayer/startBootSound(), что и у стены кода/терминала загрузки). */
    private fun startShutdownTerminal() {
        val boot = bindingMain.incLayoutBootSequence
        boot.tvBootTerminal.setTextColor(currentWizardAccentColor())
        boot.root.alpha = 1f
        boot.root.visibility = View.VISIBLE
        boot.layoutBootFrameLogo.visibility = View.GONE
        boot.layoutBootFrameCodewall.visibility = View.GONE
        boot.layoutBootFrameTerminal.visibility = View.VISIBLE

        startBootSound()
        typeTerminalText(SHUTDOWN_HEADER_PREFIX, SHUTDOWN_BODY_TEXT, 0, boot.tvBootTerminal) {
            stopBootSound()
            bootPostDelayed(BOOT_TERMINAL_END_HOLD_MS) { finishShutdownSequence() }
        }
    }

    /** Шаг 5 спеки — терминал выключения гаснет (fade самого layout_boot_sequence, фон под
     * ним уже сплошной чёрный view_power_off из шага 3), оставляя обычное состояние OFF. */
    private fun finishShutdownSequence() {
        val boot = bindingMain.incLayoutBootSequence
        boot.root.animate()
            .alpha(0f)
            .setDuration(SHUTDOWN_FINAL_FADE_MS)
            .withEndAction {
                boot.root.visibility = View.GONE
                boot.root.alpha = 1f
            }
            .start()
    }

    /***********************************************************************************************************
     * GLITCH EFFECT — довесок к анимации включения (roadmap, "Видение приложения", п.11)
     **********************************************************************************************************/
    private val glitchRandom = Random()
    private fun glitchRandomInt(minInclusive: Int, maxExclusive: Int): Int =
        minInclusive + glitchRandom.nextInt(maxExclusive - minInclusive)

    // Матрицы для хроматической аберрации — оставляют только один канал, остальные
    // обнуляют (альфа не трогается), см. buildGlitchBitmap().
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

    /** Раскидывает случайное число коротких импульсов глитча по случайным моментам
     * внутри окна [windowDurationMs] — используется во время всей заставки и во время
     * "остаёмся на текущем экране" в начале выключения. Живёт на том же bootSequenceToken,
     * что и остальная анимация — cancelBootSequence() обрывает и это. */
    private fun scheduleGlitchPulses(windowDurationMs: Long, pulseCountRange: IntRange) {
        val count = glitchRandomInt(pulseCountRange.first, pulseCountRange.last + 1)
        val latestStart = (windowDurationMs - GLITCH_PULSE_MAX_MS).coerceAtLeast(1L).toInt()
        repeat(count) {
            val triggerAt = glitchRandomInt(0, latestStart).toLong()
            bootPostDelayed(triggerAt) { triggerGlitchPulse() }
        }
    }

    /** Фоновый глитч на всё время, пока PipBoy включён (не ограничен коротким окном после
     * загрузки) — самоподдерживающаяся цепочка: каждый импульс сам планирует следующий
     * через случайный интервал. Живёт на том же bootSequenceToken — POWER в любую сторону
     * (реальный или debug) рвёт цепочку через cancelBootSequence() естественным образом,
     * т.к. следующее звено просто не будет вызвано. Отдельной stopContinuousGlitch() не
     * требуется. */
    private fun startContinuousGlitch() {
        val delay = glitchRandomInt(AMBIENT_GLITCH_MIN_INTERVAL_MS, AMBIENT_GLITCH_MAX_INTERVAL_MS).toLong()
        bootPostDelayed(delay) {
            triggerGlitchPulse()
            startContinuousGlitch()
        }
    }

    /** Один импульс: снимок текущего экрана (что бы на нём сейчас ни было — заставка или
     * уже основной интерфейс) -> та же картинка с полосным сдвигом/аберрацией -> короткий
     * показ поверх всего -> назад к нормальному, неискажённому виду. */
    private fun triggerGlitchPulse() {
        val root = bindingMain.root
        if (root.width <= 0 || root.height <= 0) return
        val snapshot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(snapshot))
        val glitched = buildGlitchBitmap(snapshot)
        snapshot.recycle()

        val overlay = bindingMain.ivGlitchOverlay
        // Тема прописывает android:tint в дефолтном стиле ImageView (styles.xml) — годится
        // для монохромных иконок-силуэтов (см. CLAUDE.md, тематизация), но тут ImageView
        // показывает полноцветный снимок экрана: SRC_IN-тинт иначе заливал бы весь кадр
        // сплошным цветом темы поверх любых реальных сдвигов полос.
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

    /** Полосный сдвиг по случайным горизонтальным срезам + хроматическая аберрация на
     * паре срезов — только Canvas.drawBitmap по прямоугольникам, без ручных пиксельных
     * циклов, чтобы не подвисать при частых вызовах. */
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

    /**
     * Деревья меню для энкодера (roadmap, "Модель навигации энкодером"). [onSelect] у
     * каждого узла — `performClick()` на уже существующей touch-кнопке этого экрана, а не
     * дублирование логики показа/скрытия — гарантирует, что энкодер ведёт себя ровно так
     * же, как палец по экрану в режиме телефона.
     *
     * STATS — секция с реальной вложенностью: Status -> CND/RAD/EFF, SPECIAL -> 7
     * характеристик, Skills -> 13 навыков, General -> 13 фракций — везде выбор пункта
     * внутри листа сейчас работает только по тапу (обновляет описание/картинку, не
     * показывает отдельный экран), у нас это тоже дети узла с тем же performClick().
     *
     * PERKS — исключение: там RecyclerView с динамическим адаптером (PerkAdapter.kt), не
     * фиксированный набор кнопок, тот же приём "провалиться -> кликнуть по кнопке" не
     * подходит напрямую. Пока лист, без вложенности — отдельная задача.
     *
     * У ITEMS/DATA сейчас только плоский список вкладок — их структура целиком поменяется
     * на этапе 6 (перестройка IA, см. видение приложения в roadmap), глубже разбирать их
     * сейчас смысла нет.
     */
    private fun statsMenuRoot(): List<MenuNode> {
        val statusButtons = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons
        val statusNode = MenuNode(
            id = "STATUS",
            children = listOf(
                MenuNode("LIGHT") { statusButtons.btnWoundLight.performClick() },
                MenuNode("HEAVY") { statusButtons.btnWoundHeavy.performClick() },
                MenuNode("STUNNED") { statusButtons.btnStunned.performClick() },
            ),
            onSelect = { bindingMain.incLayoutTabStatsBottom.btnStatsStatus.performClick() }
        )
        val special = bindingMain.incLayoutTabStatsSpecial
        val specialNode = MenuNode(
            id = "SPECIAL",
            children = listOf(
                MenuNode("STRENGTH") { special.layoutTabStatsSpecialStrength.performClick() },
                MenuNode("PERCEPTION") { special.layoutTabSpecialPerception.performClick() },
                MenuNode("ENDURANCE") { special.layoutTabSpecialEndurance.performClick() },
                MenuNode("CHARISMA") { special.layoutTabSpecialCharisma.performClick() },
                MenuNode("INTELLIGENCE") { special.layoutTabSpecialIntelligence.performClick() },
                MenuNode("AGILITY") { special.layoutTabSpecialAgility.performClick() },
                MenuNode("LUCK") { special.layoutTabSpecialLuck.performClick() },
            ),
            onSelect = { bindingMain.incLayoutTabStatsBottom.btnStatsSpecial.performClick() }
        )
        val skills = bindingMain.incLayoutTabStatsSkills
        val skillsNode = MenuNode(
            id = "SKILLS",
            children = listOf(
                MenuNode("BARTER") { skills.layoutTabSkillsBarter.performClick() },
                MenuNode("BIG_GUNS") { skills.layoutTabSkillsBigGuns.performClick() },
                MenuNode("ENERGY_WEAPONS") { skills.layoutTabSkillsEnergyWeapons.performClick() },
                MenuNode("EXPLOSIVES") { skills.layoutTabSkillsExplosives.performClick() },
                MenuNode("LOCKPICK") { skills.layoutTabSkillsLockpick.performClick() },
                MenuNode("MEDICINE") { skills.layoutTabSkillsMedicine.performClick() },
                MenuNode("MELEE_WEAPONS") { skills.layoutTabSkillsMeleeWeapons.performClick() },
                MenuNode("REPAIR") { skills.layoutTabSkillsRepair.performClick() },
                MenuNode("SCIENCE") { skills.layoutTabSkillsScience.performClick() },
                MenuNode("SMALL_GUNS") { skills.layoutTabSkillsSmallGuns.performClick() },
                MenuNode("SNEAK") { skills.layoutTabSkillsSneak.performClick() },
                MenuNode("SPEECH") { skills.layoutTabSkillsSpeech.performClick() },
                MenuNode("UNARMED") { skills.layoutTabSkillsUnarmed.performClick() },
            ),
            onSelect = { bindingMain.incLayoutTabStatsBottom.btnStatsSkills.performClick() }
        )
        val bottom = bindingMain.incLayoutTabStatsBottom
        return listOf(
            statusNode,
            specialNode,
            skillsNode,
            MenuNode("PERKS") { bottom.btnStatsPerks.performClick() },
        )
    }
    /**
     * ITEMS (roadmap, этап 6) — Map (п.2, переехал из DATA/Local Map), Clock (п.3, переехал
     * из списка радиостанций RADIO — был попапом, теперь обычный раздел), Journal (п.4,
     * заглушка "Раздел находится в разработке" — полная реализация с голосовым вводом,
     * видение, п.8).
     */
    private fun itemsMenuRoot(): List<MenuNode> {
        val bottom = bindingMain.incLayoutTabItemsBottom
        val clockButtons = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockButtons
        val clockNode = MenuNode(
            id = "CLOCK",
            children = listOf(
                MenuNode("TIME") { clockButtons.btnClockTime.performClick() },
                MenuNode("ALARM") { clockButtons.btnClockAlarm.performClick() },
                MenuNode("TIMER") { clockButtons.btnClockTimer.performClick() },
                MenuNode("STOPWATCH") { clockButtons.btnClockStopwatch.performClick() },
                MenuNode("MELODY") { clockButtons.btnClockMelody.performClick() },
            ),
            onSelect = { bottom.btnItemsClock.performClick() }
        )
        return listOf(
            MenuNode("MAP") { bottom.btnItemsMap.performClick() },
            clockNode,
            MenuNode("JOURNAL") { bottom.btnItemsJournal.performClick() },
            MenuNode("GEIGER") { bottom.btnItemsGeiger.performClick() },
        )
    }
    private fun dataMenuRoot(): List<MenuNode> {
        val bottom = bindingMain.incLayoutTabDataBottom
        return listOf(
            MenuNode("MISC") { bottom.btnDataMisc.performClick() },
            MenuNode("HOLOTAPES") { bottom.btnDataHolotapes.performClick() },
        )
    }
    /**
     * RADIO — top-level раздел без второго уровня (roadmap, "Новая шапка + единый
     * Settings", п.4/таблица второго уровня) — корень дерева состоит из одного листа,
     * чтобы `ENCBTN`/`ENC` на этом разделе хотя бы не падали, а не потому что там
     * реально есть навигация вглубь.
     */
    private fun radioMenuRoot(): List<MenuNode> {
        return listOf(MenuNode("RADIO") { })
    }
    /**
     * Разбирает входящую BLE-строку по конвенции протокола (PipBoy_BLE_Protocol_v0.2.md,
     * раздел 2: `КЛЮЧ:ЗНАЧЕНИЕ` для параметризованных команд, голое ключевое слово для
     * остальных) и раздаёт по обработчикам. STATS/ITEMS/DATA уходят в уже существующий
     * menuChangeBLE() без изменений — остальные команды пока только логируются, реальная
     * обработка (навигация энкодером, радио) — следующие этапы roadmap.
     */
    private fun handleBleCommand(raw: String) {
        val parts = raw.split(":", limit = 2)
        val key = parts[0]
        val value = parts.getOrNull(1)

        when (key) {
            "STATS" -> { menuChangeBLE(key); menuNavigator.resetToRoot(statsMenuRoot()) }
            "ITEMS" -> { menuChangeBLE(key); menuNavigator.resetToRoot(itemsMenuRoot()) }
            "DATA" -> { menuChangeBLE(key); menuNavigator.resetToRoot(dataMenuRoot()) }
            "POWER" -> applyPowerState(value == "1")
            "ENCBTN" -> { menuNavigator.activateSelected(); syncRow2ActiveFromNavigator() }
            "ENC" -> { menuNavigator.moveCursor(value?.toIntOrNull() ?: 0); syncRow2ActiveFromNavigator() }
            "GEIGER" -> Log.i("BLE", "GEIGER:$value — отображение, roadmap этап 7")
            // RADIOPWR:1 -> переключиться на экран радио (протокол, раздел 3.2). RADIOPWR:0 —
            // остаться на текущем экране, обновление статуса радио — roadmap этап 7.
            "RADIOPWR" -> if (value == "1") { menuChangeBLE("RADIO"); menuNavigator.resetToRoot(radioMenuRoot()) }
            "RADIOFREQ" -> Log.i("BLE", "RADIOFREQ:$value — реальное радио, roadmap этап 7")
            "RADIOTUNE" -> Log.i("BLE", "RADIOTUNE:$value — реальное радио, roadmap этап 7")
            "VOLUME" -> Log.i("BLE", "VOLUME:$value — реальное радио, roadmap этап 7")
            "RADIOTUNEBTN" -> Log.i("BLE", "RADIOTUNEBTN — реальное радио, roadmap этап 7")
            "HOLOTAPE" -> Log.i("BLE", "HOLOTAPE:$value — голодиски, блокируется готовностью USB Host")
            else -> Log.w("BLE", "Неизвестная BLE-команда: $raw")
        }
    }

    fun menuChangeBLE(menu: String){
        // curMenu переключается ДО menuOptionClickedBLE(), не после — setupRow2()/
        // alignRow2ToActiveButton() внутри неё читают curMenu, чтобы найти кнопку строки 1,
        // под которую подровнять активный пункт строки 2. Раньше присваивание шло последней
        // строкой каждой ветки, поэтому выравнивание всегда цеплялось за ПРЕДЫДУЩИЙ раздел
        // (полоса второго уровня уезжала под старую кнопку строки 1).
        when(menu){
            "STATS" -> {
                curMenu = "STATS"
                bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks)
                menuOptionClickedBLE("STATS")
            }
            "ITEMS" -> {
                curMenu = "ITEMS"
                bottomButtonsModify(bindingMain.incLayoutTabItemsBottom.btnItemsMap, bindingMain.incLayoutTabItemsBottom.btnItemsClock, bindingMain.incLayoutTabItemsBottom.btnItemsJournal, bindingMain.incLayoutTabItemsBottom.btnItemsGeiger)
                menuOptionClickedBLE("ITEMS")
            }
            "DATA" -> {
                curMenu = "DATA"
                bottomButtonsModify(bindingMain.incLayoutTabDataBottom.btnDataMisc, bindingMain.incLayoutTabDataBottom.btnDataHolotapes)
                menuOptionClickedBLE("DATA")
            }
            "RADIO" -> {
                curMenu = "RADIO"
                // У RADIO нет второго уровня (roadmap, "Новая шапка + единый Settings",
                // п.4) — listBottomButtons пуст, enableDisableBottomButtons() отработает
                // на пустом списке без ошибок.
                bottomButtonsModify()
                menuOptionClickedBLE("RADIO")
            }
        }
    }
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawableCompat(context: Context, resId: Int): Drawable? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ContextCompat.getDrawable(context, resId)
        } else {
            @Suppress("DEPRECATION")
            context.resources.getDrawable(resId)
        }
    }
    @SuppressLint("DiscouragedPrivateApi")
    private fun setScrollbarThumbDrawable(view: View, drawable: Drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.verticalScrollbarThumbDrawable = drawable
        } else {
            try {
                val scrollCacheField = View::class.java.getDeclaredField("mScrollCache")
                scrollCacheField.isAccessible = true
                val scrollCache = scrollCacheField.get(view)

                val scrollBarField = scrollCache.javaClass.getDeclaredField("scrollBar")
                scrollBarField.isAccessible = true
                val scrollBar = scrollBarField.get(scrollCache)

                val method = scrollBar.javaClass.getDeclaredMethod(
                    "setVerticalThumbDrawable", Drawable::class.java
                )
                method.isAccessible = true
                method.invoke(scrollBar, drawable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyAppTheme(Colour: Int, scrollbarDrawable: Drawable?){
        applyBackgroundResource(Colour)
        applyTextColor(Colour)
        applyScrollBar(scrollbarDrawable)
    }
    private fun applyBackgroundResource(Colour: Int) {
        // Apply background to relevant views
        val backgrounds = listOf(
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.layoutTabSettingsBluetooth,
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.layoutTabStatsCndPopup
            // Часы (ITEMS/Clock, roadmap этап 6 п.3) больше не в этом списке — раньше это
            // был попап со своим фоном-плашкой (settings_menu_background_green), теперь
            // обычный полноэкранный раздел без такого фона, перекрашивать нечего.
            // Settings и экран фильтра тоже убраны (roadmap, "Редизайн экрана фильтра —
            // UX-спецификация") — их корни больше не используют этот бокс-drawable, теперь
            // тонкие линии (ColorTintStyle, самотонируются темой Activity), перекрашивать
            // фон программно не нужно.
            // Add other views as necessary
        )
        var backgroundRes = R.drawable.settings_menu_background_green
        when(Colour){
            0 -> {backgroundRes = R.drawable.settings_menu_background_green
                selected_button = R.drawable.button_selected_green
                selectedRowButton = R.drawable.status_row_selected_green}
            1 -> {backgroundRes = R.drawable.settings_menu_background_amber
                selected_button = R.drawable.button_selected_amber
                selectedRowButton = R.drawable.status_row_selected_amber}
            2 -> {backgroundRes = R.drawable.settings_menu_background_white
                selected_button = R.drawable.button_selected_white
                selectedRowButton = R.drawable.status_row_selected_white}
            3 -> {backgroundRes = R.drawable.settings_menu_background_blue
                selected_button = R.drawable.button_selected_blue
                selectedRowButton = R.drawable.status_row_selected_blue}
        }
        backgrounds.forEach { it.setBackgroundResource(backgroundRes) }
    }
    private fun applyTextColor(Colour: Int){
        // Apply text colors to relevant radio buttons and checkboxes
        val primaryTextViews = listOf(
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat1,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat2,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat3,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat4,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat5,
            bindingMain.incLayoutSettingsGlobal.rbSettingsLanguageRu,
            bindingMain.incLayoutSettingsGlobal.rbSettingsLanguageEn,
            bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome,
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings,
            bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings,
            bindingMain.incLayoutSettingsGlobal.cboxAmbientSoundSettings
            // Add other radio buttons and text views as needed
        )
        var primaryColor = R.color.themeGreen
        when(Colour){
            0 -> {primaryColor = R.color.themeGreen}
            1 -> {primaryColor = R.color.themeAmber}
            2 -> {primaryColor = R.color.themeWhite}
            3 -> {primaryColor = R.color.themeBlue}
        }

        @Suppress("ResourceAsColor")
        primaryTextViews.forEach { it.setTextColor(resources.getColor(primaryColor)) }
        lineVisualizer.setColor(getResources().getColor(primaryColor))
    }
    private fun applyScrollBar(scrollbarDrawable: Drawable?){
        scrollbarDrawable?.let {
            // Apply scrollbar drawable to relevant scroll views
            val scrollViews = listOf(
                bindingMain.incLayoutTabStatsSpecial.scrollTabSpecial,
                bindingMain.incLayoutTabStatsSkills.scrollTabSkills,
                bindingMain.incLayoutTabStatsPerks.recyclerTabPerks,
                bindingMain.incLayoutTabDataMisc.scrollTabDataMisc,
                bindingMain.incLayoutTabDataMisc.scrollTabDataMiscText,
                bindingMain.incLayoutSettingsGlobal.scrollTabSettings,
                bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.scrollTabSettingsBluetooth,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.scrollTutorialWelcomeMain,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.scrollTutorialWhatsnewMain,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.scrollTutorialStatsMain,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.scrollTutorialItemsMain,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.scrollTutorialDataMain,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.scrollTutorialSettingsMain,
                bindingMain.incLayoutFilterModification.scrollFilterModification
                // Add other scroll views as necessary
            )
            scrollViews.forEach { setScrollbarThumbDrawable(it, scrollbarDrawable) }
        }
    }

    private fun setSelectedButton(button: Button?, listArrayListButtons: ArrayList<Button>?) {
        if (button != null) {
            selectedSubMenu = button
        }
        button?.setBackgroundResource(selected_button)
        playNewTabSelectAudio()
        val it: Iterator<Button> = listArrayListButtons!!.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (!Intrinsics.areEqual(next as Any, button as Any)) {
                next.setBackgroundResource(R.drawable.button_unselected)
            }
        }
    }
    private fun setSelectedClockButton(button: Button?, listArrayListButtons: ArrayList<Button>?) {
        button?.setBackgroundResource(selected_button)
        playCNDSelectAudio()
        val it: Iterator<Button> = listArrayListButtons!!.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (!Intrinsics.areEqual(next as Any, button as Any)) {
                next.setBackgroundResource(R.drawable.button_unselected)
            }
        }
    }
    private fun setSelectedSPECIALButton(layout: ConstraintLayout?, listArrayListLayout: ArrayList<ConstraintLayout>?, selectedItem: String) {
        layout?.setBackgroundResource(selected_button)
        playItemSelectAudio()
        val it: Iterator<ConstraintLayout> = listArrayListLayout!!.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (!Intrinsics.areEqual(next as Any, layout as Any)) {
                next.setBackgroundResource(R.drawable.button_unselected)
            }
        }
        selectedSPECIAL = selectedItem
    }
    private fun setSelectedSKILLSButton(layout: ConstraintLayout?, listArrayListLayout: ArrayList<ConstraintLayout>?, selectedItem: String) {
        layout?.setBackgroundResource(selected_button)
        playItemSelectAudio()
        val it: Iterator<ConstraintLayout> = listArrayListLayout!!.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (!Intrinsics.areEqual(next as Any, layout as Any)) {
                next.setBackgroundResource(R.drawable.button_unselected)
            }
        }
        selectedSKILL = selectedItem
    }
    /**
     * Кнопки +/- SPECIAL/Skills (roadmap, "Финализация STATS") — [prefKey]/[SharedPreferences]
     * и [TextView] для текущего selectedSPECIAL/selectedSKILL. Диапазоны и дефолты те же,
     * что были у старой схемы долгого тапа по строке (SPECIAL 1-10/5, Skills 10-100/10),
     * но теперь клампятся на границе, а не зацикливаются — с отдельными кнопками +/-
     * зацикливание было осмысленно только при "можно было исключительно прибавлять".
     */
    private fun specialPrefKeyAndView(name: String): Pair<String, TextView>? {
        val special = bindingMain.incLayoutTabStatsSpecial
        return when (name) {
            "STRENGTH" -> "SPECIAL_S" to special.tvSpecialStrengthValue
            "PERCEPTION" -> "SPECIAL_P" to special.tvSpecialPerceptionValue
            "ENDURANCE" -> "SPECIAL_E" to special.tvSpecialEnduranceValue
            "CHARISMA" -> "SPECIAL_C" to special.tvSpecialCharismaValue
            "INTELLIGENCE" -> "SPECIAL_I" to special.tvSpecialIntelligenceValue
            "AGILITY" -> "SPECIAL_A" to special.tvSpecialAgilityValue
            "LUCK" -> "SPECIAL_L" to special.tvSpecialLuckValue
            else -> null
        }
    }
    private fun adjustSelectedSpecial(delta: Int) {
        val (prefKey, textView) = specialPrefKeyAndView(selectedSPECIAL) ?: return
        val prevValue = sharedPreferences.getInt(prefKey, 5)
        val curValue = (prevValue + delta).coerceIn(1, 10)
        textView.text = curValue.toString()
        sharedPreferences.edit().putInt(prefKey, curValue).apply()
        if (curValue == prevValue) playErrorAudio() else playCNDSelectAudio()
    }
    private fun skillPrefKeyAndView(name: String): Pair<String, TextView>? {
        val skills = bindingMain.incLayoutTabStatsSkills
        return when (name) {
            "BARTER" -> "SKILLS_1" to skills.tvSkillsBarterValue
            "BIGGUNS" -> "SKILLS_2" to skills.tvSkillsBigGunsValue
            "ENERGYWEAPONS" -> "SKILLS_3" to skills.tvSkillsEnergyWeaponsValue
            "EXPLOSIVES" -> "SKILLS_4" to skills.tvSkillsExplosivesValue
            "LOCKPICK" -> "SKILLS_5" to skills.tvSkillsLockpickValue
            "MEDICINE" -> "SKILLS_6" to skills.tvSkillsMedicineValue
            "MELEEWEAPONS" -> "SKILLS_7" to skills.tvSkillsMeleeWeaponsValue
            "REPAIR" -> "SKILLS_8" to skills.tvSkillsRepairValue
            "SCIENCE" -> "SKILLS_9" to skills.tvSkillsScienceValue
            "SMALLGUNS" -> "SKILLS_10" to skills.tvSkillsSmallGunsValue
            "SNEAK" -> "SKILLS_11" to skills.tvSkillsSneakValue
            "SPEECH" -> "SKILLS_12" to skills.tvSkillsSpeechValue
            "UNARMED" -> "SKILLS_13" to skills.tvSkillsUnarmedValue
            else -> null
        }
    }
    private fun adjustSelectedSkill(delta: Int) {
        val (prefKey, textView) = skillPrefKeyAndView(selectedSKILL) ?: return
        val prevValue = sharedPreferences.getInt(prefKey, 10)
        val curValue = (prevValue + delta).coerceIn(10, 100)
        textView.text = curValue.toString()
        sharedPreferences.edit().putInt(prefKey, curValue).apply()
        if (curValue == prevValue) playErrorAudio() else playCNDSelectAudio()
    }
    private fun setSelectedSubMenuButton(layout: ConstraintLayout?, listArrayListLayout: ArrayList<ConstraintLayout>?) {
        layout?.setBackgroundResource(selected_button)
        playItemSelectAudio()
        val it: Iterator<ConstraintLayout> = listArrayListLayout!!.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (!Intrinsics.areEqual(next as Any, layout as Any)) {
                next.setBackgroundResource(R.drawable.button_unselected)
            }
        }
    }
    private fun bottomButtonsModify(vararg buttons: Button){
        listBottomButtons.clear()
        listBottomButtons.addAll(buttons)
    }
    private fun setupSTATS(){
        //Set Selected buttons by default
        // Здоров по умолчанию (woundPhase == NONE) — ни одна из трёх кнопок статуса не
        // выделена, updateWoundButtonsUI() сама так и посчитает.
        updateWoundButtonsUI()
        findViewById<ConstraintLayout>(R.id.layout_tab_stats_special_strength).setBackgroundResource(selected_button)
        findViewById<ConstraintLayout>(R.id.layout_tab_skills_barter).setBackgroundResource(selected_button)
    }
    private fun setupDATA(){
        //Set Selected buttons by default
        findViewById<ConstraintLayout>(R.id.layout_tab_data_misc_entry1).setBackgroundResource(selected_button)
    }
    private fun setupITEMSClock(){
        //Set Selected buttons by default
        findViewById<Button>(R.id.btn_clock_time).setBackgroundResource(selected_button)
    }
    /**
     * Проверка срабатывания будильника (roadmap, "Часы — UX-спецификация") — вызывается
     * из того же 300мс-цикла, что и обновление часов (onCreate), сверяет часы/минуты
     * [gameCalendar] (реальное время, только YEAR игровой) с выставленным будильником.
     * Совпадение сразу разоружает будильник — иначе сработает повторно на следующей
     * итерации цикла в той же самой минуте.
     */
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
        val alarm = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm
        alarm.tvClockAlarmStatus.text = getString(R.string.clock_alarm_status_off)
        alarm.btnClockAlarmToggle.text = getString(R.string.clock_alarm_set)
        bindingMain.incLayoutClockFiredOverlay.tvClockFiredTitle.text = getString(R.string.clock_alarm_fired_title)
        bindingMain.incLayoutClockFiredOverlay.root.visibility = View.VISIBLE
        playClockFiredSound()
    }
    /**
     * Звук срабатывания — выбранный трек из "Мелодия звонка" (roadmap, "Часы —
     * UX-спецификация"), общий слот для будильника и таймера. sharedPreferences хранит
     * только индекс — до первого явного выбора игроком это индекс 0 (первый трек списка),
     * не отсутствие звука вовсе.
     */
    private fun playClockFiredSound() {
        stopClockFiredSound()
        val trackIndex = sharedPreferences.getInt(selectedRingtone_SPKey, 0)
        val uri = Uri.parse("android.resource://$packageName/${ringtoneTracks[trackIndex].rawResId}")
        // Без явных AudioAttributes(USAGE_ALARM) — тот канал управляется отдельным
        // системным регулятором громкости "будильник", независимым от того, которым
        // игрок уже пользуется для остального звука приложения (баг, найденный на
        // устройстве: звук был заметно тише и не следовал системной громкости). Дефолтный
        // канал MediaPlayer — тот же, что у радио/кликов интерфейса.
        clockFiredRingtonePlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
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
    /**
     * Проверка срабатывания таймера (roadmap, "Часы — UX-спецификация") — вызывается из
     * того же 300мс-цикла, что и часы/будильник. Отсчёт — по целевому epoch millis
     * ([timerTargetEpochMillis]), не декрементом счётчика, поэтому просто сверяем текущее
     * время и обновляем отображение остатка, пока RUNNING.
     */
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
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.tvClockTimerCountdown.text =
            String.format("%02d:%02d:%02d", h, m, s)
        if (woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD) {
            updateWoundCountdownText(remainingSeconds)
        }
        updateClockTimerLabel()
    }
    /** Подпись над отсчётом на экране ITEMS/Таймер (roadmap, "Редизайн STATS/Status —
     * UX-спецификация", фидбек по итогам тестирования) — тот же общий таймер, что и на
     * STATUS, поэтому у него может быть та же стадия (Оглушение/Кровотечение/Перевязка).
     * Пустая для обычного запуска с этого экрана (woundPhase == NONE). */
    private fun clockTimerLabelText(): String = when (woundPhase) {
        WoundPhase.STUNNED -> getString(R.string.status_wound_stunned_label)
        WoundPhase.BLEED -> getString(R.string.status_wound_bleeding_label)
        WoundPhase.BANDAGE -> getString(R.string.status_wound_bandage_label)
        else -> ""
    }
    private fun updateClockTimerLabel() {
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.tvClockTimerLabel.text = clockTimerLabelText()
    }
    /**
     * Общий таймер приложения (roadmap, "Часы — UX-спецификация") переиспользуется и
     * системой ранений (roadmap, "Редизайн STATS/Status — UX-спецификация") — один и тот
     * же timerState/timerTargetEpochMillis "принадлежит" либо обычному запуску с экрана
     * ITEMS/Таймер (woundPhase == NONE — прежнее поведение без изменений), либо текущей
     * фазе ранения (woundPhase != NONE — сброс UI экрана ITEMS/Таймер не нужен, там
     * ничего не открывалось, дальнейшую логику берёт fireWoundTimer()).
     */
    private fun fireTimer() {
        timerState = TimerState.IDLE
        if (woundPhase == WoundPhase.NONE) {
            syncClockTimerScreenVisibility()
        } else {
            fireWoundTimer()
        }
        bindingMain.incLayoutClockFiredOverlay.tvClockFiredTitle.text = getString(R.string.clock_timer_fired_title)
        bindingMain.incLayoutClockFiredOverlay.root.visibility = View.VISIBLE
        playClockFiredSound()
    }
    /** Экран ITEMS/Часы/Таймер и таймер ранения на STATUS — один и тот же таймер
     * (roadmap, "Редизайн STATS/Status — UX-спецификация"): setup/running-панели этого
     * экрана просто следуют timerState, независимо от того, кто таймер запустил, чтобы
     * заглянувший на этот экран во время ранения игрок видел тот же отсчёт. */
    private fun syncClockTimerScreenVisibility() {
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        val running = timerState != TimerState.IDLE
        timer.layoutClockTimerRunning.visibility = if (running) View.VISIBLE else View.GONE
        timer.layoutClockTimerSetup.visibility = if (running) View.GONE else View.VISIBLE
    }
    /**
     * Мелодия звонка (roadmap, "Часы — UX-спецификация") — функции уровня класса, не
     * локальные closure в onCreate: openClockMelodyScreen() вызывается из обработчика
     * клика по кнопке "Мелодия звонка" в списке фичей Clock, который регистрируется
     * раньше по тексту onCreate, чем сама секция настройки экрана Мелодии — локальные
     * fun в Kotlin не видны при таком опережающем вызове (в отличие от методов класса).
     */
    private fun updateMelodySelectedLabel() {
        val melody = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody
        val index = sharedPreferences.getInt(selectedRingtone_SPKey, 0)
        melody.tvClockMelodySelectedName.apply {
            text = ringtoneTracks[index].displayName
            isSelected = false // застывшее обрезанное состояние, пока не нажали [Выбрать]
        }
    }
    /** Один проход marquee у названия в строке "Выбрано:" сразу после [Выбрать]
     * (roadmap, "Часы — UX-спецификация") — сброс isSelected перед повторной установкой
     * нужен, иначе TextView считает лимит повторов уже исчерпанным и не скроллит заново,
     * если выбрать тот же самый трек второй раз подряд. */
    private fun playMelodySelectedMarqueeOnce() {
        val nameView = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.tvClockMelodySelectedName
        nameView.isSelected = false
        nameView.post { nameView.isSelected = true }
    }
    private fun highlightMelodyRow() {
        for ((index, row) in melodyTrackRowViews.withIndex()) {
            val isActive = index == melodyFocusedIndex
            row.setBackgroundResource(if (isActive) selected_button else R.drawable.button_unselected)
            // isSelected запускает marquee у активного пункта (нужно read полное название),
            // остальные остаются статично обрезанными многоточием.
            row.isSelected = isActive
        }
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
        val melody = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody
        melodyPreviewPlayer = MediaPlayer.create(this, ringtoneTracks[index].rawResId).apply {
            isLooping = true
            start()
        }
        val audioSessionId = melodyPreviewPlayer?.audioSessionId
        if (checkAudioPermission() && audioSessionId != null && audioSessionId != -1) {
            melody.melodyWave.release()
            melody.melodyWave.setPlayer(audioSessionId)
            melody.melodyWave.visibility = View.VISIBLE
        } else if (!checkAudioPermission()) {
            requestAudioPermission()
        }
    }
    private fun openClockMelodyScreen() {
        val clock = bindingMain.incLayoutTabItemsClock
        clock.layoutTabItemsClockButtonsContainer.visibility = View.GONE
        clock.layoutTabItemsClockContent.visibility = View.GONE
        clock.incLayoutTabItemsClockMelody.root.visibility = View.VISIBLE
        melodyFocusedIndex = sharedPreferences.getInt(selectedRingtone_SPKey, 0)
        highlightMelodyRow()
        updateMelodySelectedLabel()
    }
    private fun closeClockMelodyScreen() {
        stopMelodyPreview()
        val clock = bindingMain.incLayoutTabItemsClock
        clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
        clock.layoutTabItemsClockButtonsContainer.visibility = View.VISIBLE
        clock.layoutTabItemsClockContent.visibility = View.VISIBLE
    }
    /** Обновление отображения секундомера — вызывается из общего 300мс-цикла, пока RUNNING. */
    private fun updateStopwatchDisplay() {
        if (stopwatchState != StopwatchState.RUNNING) return
        val elapsedSeconds = (System.currentTimeMillis() - stopwatchStartEpochMillis) / 1000
        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.tvClockStopwatchElapsed.text =
            String.format("%02d:%02d:%02d", h, m, s)
    }
    /**
     * Строка 1 новой шапки — подсветка активного верхнего раздела (тот же приём, что и у
     * второго уровня в menuOptionClicked/menuOptionClickedBLE: закрашенный фон
     * [selected_button] на активной кнопке, прозрачный на остальных).
     */
    private fun topLevelButtonsModify(menu: String){
        findViewById<Button>(R.id.btn_header_stats).setBackgroundResource(R.drawable.button_unselected)
        findViewById<Button>(R.id.btn_header_items).setBackgroundResource(R.drawable.button_unselected)
        findViewById<Button>(R.id.btn_header_data).setBackgroundResource(R.drawable.button_unselected)
        findViewById<Button>(R.id.btn_header_radio).setBackgroundResource(R.drawable.button_unselected)
        when(menu){
            "STATS" -> findViewById<Button>(R.id.btn_header_stats).setBackgroundResource(selected_button)
            "ITEMS" -> findViewById<Button>(R.id.btn_header_items).setBackgroundResource(selected_button)
            "DATA" -> findViewById<Button>(R.id.btn_header_data).setBackgroundResource(selected_button)
            "RADIO" -> findViewById<Button>(R.id.btn_header_radio).setBackgroundResource(selected_button)
        }
    }
    private fun setupMainContent(menu: String){
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_status).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_cnd_popup).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_special).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_skills).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_perks).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_clock).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_journal).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_geiger).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_holotapes).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_data_misc_main).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_radio).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_filter_modification).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_base).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_welcome).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_whatsnew).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_1_stats).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_2_items).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_3_data).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_4_settings).visibility = View.GONE

        if (menu == "STATS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_status).visibility = View.VISIBLE
        } else if (menu == "ITEMS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_map).visibility = View.VISIBLE
        } else if (menu == "DATA"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.VISIBLE
        } else if (menu == "RADIO"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_radio).visibility = View.VISIBLE
        }
    }
    private fun setupMainContentBLE(menu: String){
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_status).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_cnd_popup).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_special).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_skills).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_perks).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_clock).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_journal).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_geiger).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_holotapes).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_data_misc_main).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_radio).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_filter_modification).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_base).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_welcome).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_whatsnew).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_1_stats).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_2_items).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_3_data).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_4_settings).visibility = View.GONE

        if (menu == "STATS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_status).visibility = View.VISIBLE
        } else if (menu == "ITEMS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_map).visibility = View.VISIBLE
        } else if (menu == "DATA"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.VISIBLE
        } else if (menu == "RADIO"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_radio).visibility = View.VISIBLE
        }
    }
    private fun statsRow2Items(): List<Row2Item> {
        val bottom = bindingMain.incLayoutTabStatsBottom
        return listOf(
            Row2Item(bottom.btnStatsStatus.text) { bottom.btnStatsStatus.performClick() },
            Row2Item(bottom.btnStatsSpecial.text) { bottom.btnStatsSpecial.performClick() },
            Row2Item(bottom.btnStatsSkills.text) { bottom.btnStatsSkills.performClick() },
            Row2Item(bottom.btnStatsPerks.text) { bottom.btnStatsPerks.performClick() },
        )
    }
    private fun itemsRow2Items(): List<Row2Item> {
        // Порядок должен совпадать с itemsMenuRoot() и bottomButtonsModify() выше.
        val bottom = bindingMain.incLayoutTabItemsBottom
        return listOf(
            Row2Item(bottom.btnItemsMap.text) { bottom.btnItemsMap.performClick() },
            Row2Item(bottom.btnItemsClock.text) { bottom.btnItemsClock.performClick() },
            Row2Item(bottom.btnItemsJournal.text) { bottom.btnItemsJournal.performClick() },
            Row2Item(bottom.btnItemsGeiger.text) { bottom.btnItemsGeiger.performClick() },
        )
    }
    private fun dataRow2Items(): List<Row2Item> {
        // Порядок должен совпадать с dataMenuRoot() и bottomButtonsModify() выше.
        val bottom = bindingMain.incLayoutTabDataBottom
        return listOf(
            Row2Item(bottom.btnDataMisc.text) { bottom.btnDataMisc.performClick() },
            Row2Item(bottom.btnDataHolotapes.text) { bottom.btnDataHolotapes.performClick() },
        )
    }
    /** Кнопка строки 1, под которой должен оказаться активный пункт строки 2 (roadmap,
     * "Новая шапка + единый Settings", косметика по образцу референса). */
    private fun currentRow1TargetButton(): View? {
        val row1 = bindingMain.incLayoutHeaderToplevel
        return when(curMenu){
            "STATS" -> row1.btnHeaderStats
            "ITEMS" -> row1.btnHeaderItems
            "DATA" -> row1.btnHeaderData
            "RADIO" -> row1.btnHeaderRadio
            else -> null
        }
    }
    /**
     * Строит с нуля полосу строки 2 под новый раздел (roadmap, там же) — вызывается только
     * при смене верхнего уровня (STATS/ITEMS/DATA/RADIO), не при каждом тапе внутри одного
     * раздела (для этого — renderRow2(), не трогает сами View, только их видимость/alpha/
     * сдвиг). Приём "очистить и построить программно" — по аналогии с addPairingDevice() в
     * мастере PAIRING, уже был в этом же проекте.
     */
    private fun setupRow2(menu: String){
        row2Generation++
        row2Items = when(menu){
            "STATS" -> statsRow2Items()
            "ITEMS" -> itemsRow2Items()
            "DATA" -> dataRow2Items()
            else -> emptyList() // RADIO — второго уровня нет вообще
        }
        row2Active = 0
        val strip = bindingMain.incLayoutHeaderRow2.layoutHeaderRow2Strip
        strip.removeAllViews()
        row2Views.clear()
        for ((index, item) in row2Items.withIndex()){
            // Row2ItemStyle задаёт fontFamily как кастомный (не android:) атрибут — его
            // разбирает только AppCompat-инфлейтер по XML-тегу, а не конструктор обычного
            // TextView, созданного кодом (4-й аргумент defStyleRes для AppCompatTextView
            // недоступен вообще, у него нет такого конструктора) — поэтому шрифт здесь
            // ставится явно, отдельно от остальных атрибутов стиля.
            val tv = TextView(this, null, 0, R.style.Row2ItemStyle).apply {
                text = item.label
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.pipboy_mono)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { if (index > 0) marginStart = (8 * resources.displayMetrics.density).toInt() }
                setOnClickListener {
                    row2Active = index
                    item.onSelect()
                    renderRow2()
                    // Обратная синхронизация к syncRow2ActiveFromNavigator(): без неё
                    // энкодер после тача по строке 2 продолжал бы крутить от прежней
                    // позиции курсора (roadmap, "Модель навигации энкодером").
                    menuNavigator.setRootCursor(index)
                }
            }
            strip.addView(tv)
            row2Views.add(tv)
        }
        renderRow2()
    }
    /**
     * Перекрашивает/показывает-прячет уже построенные View строки 2 под текущий
     * [row2Active] и выравнивает активный пункт под кнопкой строки 1 (roadmap, там же).
     * Затенение — по расстоянию от активного пункта в обе стороны (симметрично, так ведёт
     * себя референс — проверено скриншотами): 0 — обычный цвет, 1 — среднее затенение,
     * 2 — сильное, дальше пункт скрывается совсем (не просто прозрачный — View.GONE, чтобы
     * не мешал измерению ширины полосы).
     */
    private fun renderRow2(){
        // Окно показа асимметричное: слева от активного пункта — максимум один пункт
        // (среднее затенение), справа — как и раньше, до двух (среднее, сильное). Пункты
        // "до" активного рисуются левее кнопки строки 1, под которую выравнивается полоса
        // (см. alignRow2ToActiveButton()) — у первого раздела (STATS) слева от его кнопки
        // почти нет места на экране, два пункта "до" туда физически не помещались.
        for (i in row2Views.indices){
            val view = row2Views[i]
            val dist = i - row2Active
            if (dist < -1 || dist > 2){
                view.visibility = View.GONE
            } else {
                view.visibility = View.VISIBLE
                view.alpha = when(dist){ 0 -> 1.0f; -1, 1 -> 0.55f; else -> 0.25f }
            }
        }
        alignRow2ToActiveButton()
    }
    /**
     * Подтягивает подсветку строки 2 к позиции курсора энкодера (roadmap, "Модель навигации
     * энкодером" — открытый вопрос про влияние переделки шапки). `row2Active` раньше менялся
     * только тапом по самой строке 2 (см. setupRow2()) — `ENC`/`ENCBTN` двигали курсор в
     * `MenuNavigator` и переключали контент через `MenuNode.onSelect()`, но полоса строки 2
     * об этом не узнавала и оставалась на прежнем пункте. `rootCursor()` — позиция именно на
     * уровне строки 2, не текущая глубина стека, поэтому не сбивается, пока курсор гуляет
     * внутри вложенных уровней (CND/RAD/EFF и т.п.).
     */
    private fun syncRow2ActiveFromNavigator(){
        val cursor = menuNavigator.rootCursor()
        if (cursor != row2Active && cursor in row2Views.indices){
            row2Active = cursor
            renderRow2()
        }
    }
    /**
     * Считает translationX полосы АБСОЛЮТНО (не "прибавить к тому, что уже есть") —
     * раньше был баг: `translationX +=` на позиции из getLocationOnScreen(), которая уже
     * учитывает предыдущий сдвиг, копил рассинхрон при каждой смене раздела (заметно на
     * ITEMS/DATA) и улетал далеко вправо после RADIO (там строка пустая, translationX
     * сбрасывался в 0, а следующий вызов всё равно прибавлял поверх). [activeView.left] —
     * координата внутри LinearLayout, translationX самой полосы её не портит, поэтому
     * результат каждый раз пересчитывается с нуля и не зависит от истории.
     *
     * Выравнивание — по ЦЕНТРУ активного пункта под центром кнопки строки 1, не по левому
     * краю: у пункта могут быть один-два предыдущих соседа слева (см. renderRow2()), и
     * центрирование вдвое уменьшает нужный запас места слева от кнопки (иначе, например,
     * Status у первого раздела STATS вылезал за левый край экрана при выборе Special).
     */
    private fun alignRow2ToActiveButton(){
        val strip = bindingMain.incLayoutHeaderRow2.layoutHeaderRow2Strip
        val activeView = row2Views.getOrNull(row2Active) ?: run { strip.translationX = 0f; return }
        val targetButton = currentRow1TargetButton() ?: return
        val generation = row2Generation
        strip.post {
            if (generation != row2Generation) return@post // раздел уже сменился, полоса не та
            val targetLoc = IntArray(2); targetButton.getLocationOnScreen(targetLoc)
            val stripLoc = IntArray(2); strip.getLocationOnScreen(stripLoc)
            val stripBaseX = stripLoc[0] - strip.translationX
            val targetCenter = targetLoc[0] + targetButton.width / 2
            val activeCenter = activeView.left + activeView.width / 2
            var translationX = (targetCenter - (stripBaseX + activeCenter)).toFloat()

            // Центрирование само по себе не гарантирует, что притушенные соседние пункты
            // останутся на экране — это тот же баг, что уже чинили переходом с левого
            // выравнивания на центрирование (см. комментарий выше), но при более широком
            // шрифте, чем был на момент того фикса, он снова достижим. Зажимаем так, чтобы
            // крайний видимый пункт не пересекал границу, которая была безопасна при
            // translationX = 0 (левый край строки = левый край row1, симметрично справа).
            val visible = row2Views.filter { it.visibility == View.VISIBLE }
            if (visible.isNotEmpty()){
                val leftMost = visible.minByOrNull { it.left }!!
                val rightMost = visible.maxByOrNull { it.right }!!
                val screenWidth = resources.displayMetrics.widthPixels
                val minTranslation = -leftMost.left.toFloat()
                val maxTranslation = (screenWidth - stripBaseX - rightMost.right).toFloat()
                if (minTranslation <= maxTranslation) {
                    translationX = translationX.coerceIn(minTranslation, maxTranslation)
                }
            }
            strip.translationX = translationX
        }
    }
    private fun enableDisableBottomButtons(action: Boolean, buttonarray: ArrayList<Button>?){
        if (buttonarray != null) {
            for(button in buttonarray){
                button.setEnabled(action)
            }
        }
    }
    private fun enableDisableTopSwipe(action: Boolean){
        menuSwipeEnabled = action
    }
    private fun menuOptionClicked(menu: String){
        // Тот же звук (cnd_rad_eff.wav), что и playCNDSelectAudio() ниже — раньше здесь был
        // отдельный вечно висящий в памяти MediaPlayer (mediaPlayerCRF) под тот же файл,
        // теперь оба места используют один ленивый create-play-release путь.
        playCNDSelectAudio()
        topLevelButtonsModify(menu)
        setupMainContent(menu)
        setupRow2(menu)
        enableDisableBottomButtons(false, listBottomButtons)
        enableDisableTopSwipe(false)
        sendBLEText(menu)
    }
    private fun menuOptionClickedBLE(menu: String){
        playCNDSelectAudio()
        topLevelButtonsModify(menu)
        setupMainContentBLE(menu)
        setupRow2(menu)
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        sendBLEText(menu)
        // Уход с ITEMS (в т.ч. по BLE-переключению, не только тачем) — не жечь GPS карты,
        // пока игрок смотрит STATS/DATA/RADIO. Возврат на ITEMS>Map сам перезапустит апдейты
        // через openMapScreen().
        if (menu != "ITEMS") {
            stopMapLocationUpdates()
        }
    }
    /**
     * Система ранений/кровотечения (roadmap, "Редизайн STATS/Status — UX-спецификация").
     * woundPhase/woundSeverity — единый источник истины и для лица персонажа, и для
     * подсветки трёх кнопок статуса, и для того, что произойдёт по истечении общего
     * таймера (fireTimer()/checkTimerFiring() — переиспользуются как есть, см. ниже).
     */
    private fun woundFaceDrawable(): Int = when (woundPhase) {
        WoundPhase.NONE -> R.drawable.man_face
        WoundPhase.BLEED, WoundPhase.BANDAGE -> if (woundSeverity == WoundSeverity.LIGHT) R.drawable.face_02 else R.drawable.face_03
        WoundPhase.STUNNED, WoundPhase.DEAD -> R.drawable.face_04
    }
    private fun applyWoundFace() {
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(woundFaceDrawable())
    }
    /**
     * Рамка-курсор вокруг одной из трёх строк статуса (по образцу /SPECIAL, где рамка
     * двигается энкодером между Strength/Perception/и т.д. — тот же смысл: "сюда попадёт
     * нажатие", а не "это сейчас активный статус"). Пока энкодер не подключён к этому
     * экрану — курсор двигает тач (см. onWoundActionTapped()), по умолчанию стоит на
     * "Лёгкое ранение". Полностью независим от disabled/alpha ниже — фидбек по итогам
     * тестирования: раньше подсвеченная кнопка при этом ещё и не гасла вместе с
     * остальными, что читалось как "эта кнопка работает", хотя клик по ней тоже давал
     * ошибку. Рамка/фон — на строке-контейнере (fill_parent), не на самом Button
     * (wrap_content) — иначе ширина рамки скачет от длины текста пункта (тот же фидбек).
     */
    private var statusCursorRow: View? = null
    /** Индекс 0/1/2 (Light/Heavy/Stunned) — [statusCursorRow] сам View, его нельзя
     * положить в Bundle (roadmap, "Восстановление состояния после убийства процесса —
     * спецификация"), поэтому для onSaveInstanceState()/restore нужна пара конверсий. */
    private fun statusCursorRowIndex(): Int {
        val buttons = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons
        return when (statusCursorRow) {
            buttons.layoutTabStatusWoundHeavyRow -> 1
            buttons.layoutTabStatusStunnedRow -> 2
            else -> 0
        }
    }
    private fun statusCursorRowFromIndex(index: Int): View {
        val buttons = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons
        return when (index) {
            1 -> buttons.layoutTabStatusWoundHeavyRow
            2 -> buttons.layoutTabStatusStunnedRow
            else -> buttons.layoutTabStatusWoundLightRow
        }
    }
    private fun updateWoundButtonsUI() {
        val buttons = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons
        val rows = listOf(buttons.layoutTabStatusWoundLightRow, buttons.layoutTabStatusWoundHeavyRow, buttons.layoutTabStatusStunnedRow)
        val cursor = statusCursorRow ?: buttons.layoutTabStatusWoundLightRow
        statusCursorRow = cursor
        for (row in rows) {
            row.setBackgroundResource(if (row === cursor) selectedRowButton else R.drawable.button_unselected)
        }
        // isEnabled остаётся true всегда — иначе Android вообще не даст клику дойти до
        // обработчика, а по нажатию на недоступную сейчас кнопку нужен звук ошибки (см.
        // onWoundActionTapped()), не молчаливое игнорирование. "Задизейбленность" здесь
        // только визуальная (alpha, на строке — гасит и рамку, и текст разом) — и
        // одинаковая для всех трёх строк, включая ту, что под курсором: раз клик по ней
        // тоже ничего не делает, кроме звука ошибки, она не должна визуально выделяться
        // как "рабочая".
        val enabled = woundPhase == WoundPhase.NONE
        val dimAlpha = if (enabled) 1.0f else 0.4f
        for (row in rows) {
            row.alpha = dimAlpha
        }
        // Таймер ранения нельзя ставить на паузу — ни отсюда, ни с экрана ITEMS/Таймер
        // (roadmap, "Редизайн STATS/Status — UX-спецификация"). DEAD — таймера уже нет,
        // Пауза снова доступна (следующий обычный запуск с экрана Таймера). Здесь клик
        // по-настоящему блокируется (isEnabled=false) — эта кнопка не относится к трём
        // кнопкам статуса, звук ошибки для неё не требовался.
        val pauseResume = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.btnClockTimerPauseResume
        pauseResume.isEnabled = woundPhase == WoundPhase.NONE || woundPhase == WoundPhase.DEAD
        pauseResume.alpha = if (pauseResume.isEnabled) 1.0f else 0.4f
    }
    private fun woundStageLabel(): String = when (woundPhase) {
        WoundPhase.BLEED -> getString(R.string.status_wound_bleeding_label)
        WoundPhase.BANDAGE -> getString(R.string.status_wound_bandage_label)
        else -> ""
    }
    /** Панель текста статуса справа от фигуры (roadmap, "Редизайн STATS/Status —
     * UX-спецификация", фидбек по итогам тестирования) — четыре взаимоисключающих текста
     * (здоров/оглушён-ранен со сменным таймером/мёртв). Для оглушён/ранен статичную часть
     * (заголовок, кнопки) выставляет этот метод, текст с таймером — updateWoundCountdownText()
     * сразу следующим вызовом (см. startWoundTimer()). */
    private fun updateWoundStatusLine() {
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        when (woundPhase) {
            WoundPhase.NONE -> {
                cnd.tvTabStatusWoundText.text = getString(R.string.status_text_healthy)
                cnd.layoutTabStatusWoundButtons.visibility = View.GONE
            }
            WoundPhase.DEAD -> {
                cnd.tvTabStatusWoundText.text =
                    getString(R.string.status_text_dead_header) + "\n" + getString(R.string.status_revive_hint)
                cnd.layoutTabStatusWoundButtons.visibility = View.GONE
            }
            else -> {
                cnd.layoutTabStatusWoundButtons.visibility = View.VISIBLE
                cnd.btnTabStatusWoundSkip.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            }
        }
    }
    /** Вызывается из checkTimerFiring() каждый тик, пока woundPhase — таймерная фаза —
     * длительности здесь всегда ≤10 мин, часовая часть не нужна (в отличие от
     * tv_clock_timer_countdown на экране ITEMS/Таймер). */
    private fun updateWoundCountdownText(remainingSeconds: Int) {
        val m = remainingSeconds / 60
        val s = remainingSeconds % 60
        val timerText = String.format("%02d:%02d", m, s)
        val text = when (woundPhase) {
            WoundPhase.STUNNED -> getString(R.string.status_text_stunned) + timerText
            WoundPhase.BLEED, WoundPhase.BANDAGE -> getString(R.string.status_text_wounded) + woundStageLabel() + ": " + timerText
            else -> return
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusWoundText.text = text
    }
    /** Общая точка входа для всех переходов, которые запускают общий таймер под новую
     * "цель" (roadmap — таймер переиспользуется, не отдельный механизм). severity==null
     * оставляет woundSeverity как есть (STUNNED тяжесть не различает). */
    private fun startWoundTimer(phase: WoundPhase, severity: WoundSeverity?, durationSeconds: Int) {
        woundPhase = phase
        if (severity != null) woundSeverity = severity
        // Курсор идёт за фазой и при автоматических переходах (эскалация/рецидив
        // унаследованного таймера), не только за тапом игрока — фидбек по итогам
        // тестирования: после эскалации Light -> Heavy рамка должна сама переехать на
        // "Тяжело ранен", а не оставаться на прежнем пункте.
        val buttons = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons
        statusCursorRow = when {
            phase == WoundPhase.STUNNED -> buttons.layoutTabStatusStunnedRow
            woundSeverity == WoundSeverity.LIGHT -> buttons.layoutTabStatusWoundLightRow
            else -> buttons.layoutTabStatusWoundHeavyRow
        }
        applyWoundFace()
        updateWoundButtonsUI()
        timerState = TimerState.RUNNING
        timerTargetEpochMillis = System.currentTimeMillis() + durationSeconds * 1000L
        syncClockTimerScreenVisibility()
        updateWoundStatusLine()
        updateWoundCountdownText(durationSeconds)
        updateClockTimerLabel()
    }
    /** Общий обработчик тапа по любой из трёх кнопок статуса — двигает курсор туда
     * независимо от исхода, и только потом либо выполняет действие (woundPhase == NONE),
     * либо играет звук ошибки (кнопка "задизейблена" только по alpha/звуку, не по
     * isEnabled — см. updateWoundButtonsUI()). */
    private fun onWoundActionTapped(row: View, action: () -> Unit) {
        statusCursorRow = row
        updateWoundButtonsUI()
        if (woundPhase != WoundPhase.NONE) {
            playErrorAudio()
        } else {
            playNewTabSelectAudio()
            action()
        }
    }
    /** Вылечен — общий финал и для BANDAGE (успели), и для STUNNED (прошло/остановлено):
     * возврат к man_face, таймер снят. CRIPPLED-тоггл по конечностям не трогается — это
     * независимая механика, лечение ранения на неё не влияет. */
    private fun healWoundsToHealthy() {
        woundPhase = WoundPhase.NONE
        applyWoundFace()
        updateWoundButtonsUI()
        updateWoundStatusLine()
        timerState = TimerState.IDLE
        syncClockTimerScreenVisibility()
    }
    /** [Стоп] на STATUS — и обработчик btn_clock_timer_reset на экране ITEMS/Таймер, когда
     * woundPhase != NONE (roadmap: сброс таймера ранения оттуда должен давать те же
     * последствия, что и [Стоп] на STATUS, не тихий обрыв). */
    private fun stopWoundTimerEarly() {
        when (woundPhase) {
            WoundPhase.BLEED -> startWoundTimer(WoundPhase.BANDAGE, woundSeverity, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
            WoundPhase.BANDAGE, WoundPhase.STUNNED -> healWoundsToHealthy()
            else -> {}
        }
    }
    /** Натуральное истечение — вызывается из fireTimer(), когда таймер принадлежит
     * системе ранений (woundPhase != NONE/DEAD). */
    private fun fireWoundTimer() {
        when (woundPhase) {
            WoundPhase.BLEED -> {
                if (woundSeverity == WoundSeverity.LIGHT) {
                    startWoundTimer(WoundPhase.BLEED, WoundSeverity.HEAVY, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
                } else {
                    killCharacter()
                }
            }
            WoundPhase.BANDAGE -> startWoundTimer(WoundPhase.BLEED, woundSeverity, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
            WoundPhase.STUNNED -> healWoundsToHealthy()
            else -> {}
        }
    }
    private fun killCharacter() {
        woundPhase = WoundPhase.DEAD
        applyWoundFace()
        updateWoundButtonsUI()
        updateWoundStatusLine()
        timerState = TimerState.IDLE
        syncClockTimerScreenVisibility()
        applyDeathVisuals()
    }
    /** Revive-жест — тап по фигуре целиком, активен только пока woundPhase == DEAD (см.
     * unified touch-обработчик ниже). Полный сброс — вся система статусов самоучёт
     * игрока, не принудительный контроль. */
    private fun reviveCharacter() {
        if (woundPhase != WoundPhase.DEAD) return
        woundPhase = WoundPhase.NONE
        applyWoundFace()
        updateWoundButtonsUI()
        updateWoundStatusLine()
        applyReviveVisuals()
    }
    /** Смерть — все 6 частей рисуются `*_broken`, но подпись CRIPPLED показывается только
     * на туловище (и то заменяется на DEAD) — остальным пяти отдельная подпись не нужна,
     * само состояние DEAD уже всё говорит (фидбек по итогам тестирования). */
    private fun applyDeathVisuals() {
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        crippledHead = true; crippledTorso = true; crippledLeftArm = true
        crippledRightArm = true; crippledLeftLeg = true; crippledRightLeg = true
        cnd.imgTabStatusCndPipboyHead.setImageResource(R.drawable.head_broken)
        cnd.tvTabStatusCndPipboyHeadHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyTorso.setImageResource(R.drawable.torso_broken)
        cnd.tvTabStatusCndPipboyTorsoHpCrippled.text = getString(R.string.stats_cnd_status_dead)
        cnd.tvTabStatusCndPipboyTorsoHpCrippled.visibility = View.VISIBLE
        cnd.imgTabStatusCndPipboyLeftArm.setImageResource(R.drawable.left_arm_broken)
        cnd.tvTabStatusCndPipboyLeftArmHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyRightArm.setImageResource(R.drawable.right_arm_broken)
        cnd.tvTabStatusCndPipboyRightArmHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyLeftLeg.setImageResource(R.drawable.left_leg_broken)
        cnd.tvTabStatusCndPipboyLeftLegHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyRightLeg.setImageResource(R.drawable.right_leg_broken)
        cnd.tvTabStatusCndPipboyRightLegHpCrippled.visibility = View.GONE
    }
    private fun applyReviveVisuals() {
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        crippledHead = false; crippledTorso = false; crippledLeftArm = false
        crippledRightArm = false; crippledLeftLeg = false; crippledRightLeg = false
        cnd.imgTabStatusCndPipboyHead.setImageResource(R.drawable.man_head)
        cnd.tvTabStatusCndPipboyHeadHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyTorso.setImageResource(R.drawable.torso)
        cnd.tvTabStatusCndPipboyTorsoHpCrippled.text = getString(R.string.stats_cnd_status_crippled)
        cnd.tvTabStatusCndPipboyTorsoHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyLeftArm.setImageResource(R.drawable.man_arm_left)
        cnd.tvTabStatusCndPipboyLeftArmHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyRightArm.setImageResource(R.drawable.man_arm_right)
        cnd.tvTabStatusCndPipboyRightArmHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyLeftLeg.setImageResource(R.drawable.man_leg_left)
        cnd.tvTabStatusCndPipboyLeftLegHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyRightLeg.setImageResource(R.drawable.man_leg_right)
        cnd.tvTabStatusCndPipboyRightLegHpCrippled.visibility = View.GONE
    }
    private fun applyCrippledVisual(bodyPart: ImageView, label: TextView, crippled: Boolean, normalRes: Int, brokenRes: Int) {
        bodyPart.setImageResource(if (crippled) brokenRes else normalRes)
        label.visibility = if (crippled) View.VISIBLE else View.GONE
    }
    /** Независимый тоггл CRIPPLED по одной конечности (короткий тап, работает при любом
     * woundPhase кроме DEAD — см. спеку; пока DEAD короткий тап по любой части фигуры
     * уходит на revive, см. setupFigureTouchTarget()) — не трогает woundPhase/лицо/
     * остальные части. */
    private fun toggleCrippledHead() {
        crippledHead = !crippledHead
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyHead, cnd.tvTabStatusCndPipboyHeadHpCrippled, crippledHead, R.drawable.man_head, R.drawable.head_broken)
    }
    private fun toggleCrippledTorso() {
        crippledTorso = !crippledTorso
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyTorso, cnd.tvTabStatusCndPipboyTorsoHpCrippled, crippledTorso, R.drawable.torso, R.drawable.torso_broken)
    }
    private fun toggleCrippledLeftArm() {
        crippledLeftArm = !crippledLeftArm
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftArm, cnd.tvTabStatusCndPipboyLeftArmHpCrippled, crippledLeftArm, R.drawable.man_arm_left, R.drawable.left_arm_broken)
    }
    private fun toggleCrippledRightArm() {
        crippledRightArm = !crippledRightArm
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyRightArm, cnd.tvTabStatusCndPipboyRightArmHpCrippled, crippledRightArm, R.drawable.man_arm_right, R.drawable.right_arm_broken)
    }
    private fun toggleCrippledLeftLeg() {
        crippledLeftLeg = !crippledLeftLeg
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftLeg, cnd.tvTabStatusCndPipboyLeftLegHpCrippled, crippledLeftLeg, R.drawable.man_leg_left, R.drawable.left_leg_broken)
    }
    private fun toggleCrippledRightLeg() {
        crippledRightLeg = !crippledRightLeg
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyRightLeg, cnd.tvTabStatusCndPipboyRightLegHpCrippled, crippledRightLeg, R.drawable.man_leg_right, R.drawable.right_leg_broken)
    }
    /**
     * Тач-цель на фигуре персонажа (roadmap, "Редизайн STATS/Status — UX-спецификация",
     * фидбек по итогам тестирования) — общий обработчик вешается на все 6 картинок частей
     * тела И на сам контейнер фигуры (для тапов по "пустым" промежуткам), а не только на
     * контейнер: у каждой картинки уже есть свой клик (CRIPPLED-тоггл), и он поглощает
     * touch раньше, чем событие доходит до родителя — старая версия только на контейнере
     * ловила revive/пасхалку лишь в редких пустых зазорах между частями. Короткий тап:
     * revive, если персонаж мёртв, иначе — переданное действие (toggle этой части, либо
     * ничего для самого контейнера). 5-секундный hold — пасхалка, всегда, независимо от
     * того, по какой именно части держали палец.
     */
    private fun setupFigureTouchTarget(view: View, onShortTap: () -> Unit) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    statsCndPopupIsHolding = true
                    handler.postDelayed(longPressRunnable, 5000) // 5 seconds
                }
                MotionEvent.ACTION_UP -> {
                    statsCndPopupIsHolding = false
                    handler.removeCallbacks(longPressRunnable)
                    val popupShown = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.root.visibility == View.VISIBLE
                    if (!popupShown) {
                        if (woundPhase == WoundPhase.DEAD) reviveCharacter() else onShortTap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    statsCndPopupIsHolding = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
    }
    /** [Skip] (debug-only) — принудительно "истекает" текущий таймер ранения прямо сейчас,
     * не дублируя логику fireTimer(): просто переносит целевой epoch в прошлое и даёт
     * checkTimerFiring() увидеть остаток ≤0 на следующей проверке. */
    private fun skipWoundTimer() {
        if (timerState != TimerState.RUNNING) return
        timerTargetEpochMillis = System.currentTimeMillis()
        checkTimerFiring()
    }
    private fun playItemSelectAudio(){
        val mediaPlayerItemSelect = MediaPlayer.create(this, R.raw.item_select)
        mediaPlayerItemSelectList.add(mediaPlayerItemSelect)
        mediaPlayerItemSelect.start()
        mediaPlayerItemSelect.setOnCompletionListener {
            it.release()
            mediaPlayerItemSelectList.remove(it)
        }
    }
    private fun playNewTabSelectAudio(){
        val mediaPlayerNewTab = MediaPlayer.create(applicationContext, R.raw.newtab)
        mediaPlayerNewTabList.add(mediaPlayerNewTab)
        mediaPlayerNewTab.start()
        mediaPlayerNewTab.setOnCompletionListener {
            it.release()
            mediaPlayerNewTabList.remove(it)
        }
    }
    private fun playErrorAudio(){
        val mediaPlayerError = MediaPlayer.create(applicationContext, R.raw.ui_error)
        mediaPlayerErrorList.add(mediaPlayerError)
        mediaPlayerError.start()
        mediaPlayerError.setOnCompletionListener {
            it.release()
            mediaPlayerErrorList.remove(it)
        }
    }
    private fun playCNDSelectAudio(){
        val mediaPlayerCndRadEff = MediaPlayer.create(applicationContext, R.raw.cnd_rad_eff)
        mediaPlayerCndRadEffList.add(mediaPlayerCndRadEff)
        mediaPlayerCndRadEff.start()
        mediaPlayerCndRadEff.setOnCompletionListener {
            it.release()
            mediaPlayerCndRadEffList.remove(it)
        }
    }
    private fun playLightOnAudio(){
        val mediaPlayerLightOn = MediaPlayer.create(applicationContext, R.raw.ui_pipboy_light_on)
        mediaPlayerLightOnOffList.add(mediaPlayerLightOn)
        mediaPlayerLightOn.start()
        mediaPlayerLightOn.setOnCompletionListener {
            it.release()
            mediaPlayerLightOnOffList.remove(it)
        }
    }
    private fun playLightOffAudio(){
        val mediaPlayerLightOff = MediaPlayer.create(applicationContext, R.raw.ui_pipboy_light_off)
        mediaPlayerLightOnOffList.add(mediaPlayerLightOff)
        mediaPlayerLightOff.start()
        mediaPlayerLightOff.setOnCompletionListener {
            it.release()
            mediaPlayerLightOnOffList.remove(it)
        }
    }


    /***********************************************************************************************************
     * BATTERY MONITOR
     **********************************************************************************************************/
    private fun getBatteryPercent(): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = this.registerReceiver(null, ifilter)
        val level = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        return level
    }

    // Hide the system UI (notification bar and navigation bar)
    // Вырез экрана бывает только слева или справа (ориентация зафиксирована landscape,
    // камера на короткой стороне устройства), и на разных устройствах — разной ширины
    // или отсутствует вовсе. Читаем реальный отступ и дублируем его на противоположную
    // сторону, чтобы декоративная 96%-рамка (шапка/футер, дисклеймер, мастер) оставалась
    // симметричной независимо от конкретного телефона игрока (BYOD).
    private fun mirrorDisplayCutoutInset(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val sideInset = maxOf(cutout.left, cutout.right)
            view.setPadding(sideInset, view.paddingTop, sideInset, view.paddingBottom)
            insets
        }
    }

    private fun hideSystemUI(){
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    // Ensure that the system UI remains hidden even when the user interacts with the screen
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Set orientation based on screen dimensions
        if (trueFullscreen){
            if (hasFocus) {
                hideSystemUI()
            }
        }
    }

    /***********************************************************************************************************
     * FILTER MODIFICATIONS
     **********************************************************************************************************/
    private fun listEntries(frameLayout: FrameLayout, items: List<Map<String, String>>){

        frameLayout.removeAllViews()

        // Create a LinearLayout to hold the entries
        val linearLayout = LinearLayout(this)
        linearLayout.orientation = LinearLayout.VERTICAL

        // Iterate over the items and create CheckBox and TextView for each
        for (item in items) {
            val checkBox = CheckBox(this)
            // Рамка/галочка чекбокса акцентом темы — на тёмном фоне экрана фильтра
            // нетематизированный Material-дефолт на грани видимости (roadmap, "Редизайн
            // экрана фильтра — UX-спецификация").
            CompoundButtonCompat.setButtonTintList(checkBox, ColorStateList.valueOf(currentWizardAccentColor()))
            val textView = TextView(this).apply {
                // Set the text for the TextView to the "name" value
                text = item["name"]
                // Set custom font to button
                typeface = TypefaceCache.getPipboyTypeface(context) // Set the loaded typeface
            }

            // Set the CheckBox checked state based on whether the item ID is in selectedItems
            val itemId = item["id"] ?: ""
            when(filteringMenu){
                "PERKS" -> {
                    checkBox.isChecked = selectedFilterSTATSPerks.contains(itemId)
                    // Listen for CheckBox state changes to update selectedItems
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        playItemSelectAudio()
                        if (isChecked) {
                            selectedFilterSTATSPerks.add(itemId)  // Add item ID to selected set
                        } else {
                            selectedFilterSTATSPerks.remove(itemId)  // Remove item ID from selected set
                        }
                    }
                }
            }

            // Add CheckBox and TextView to a horizontal layout
            val entryLayout = LinearLayout(this)
            entryLayout.orientation = LinearLayout.HORIZONTAL
            entryLayout.addView(checkBox)
            entryLayout.addView(textView)

            // Add the entry layout to the main LinearLayout
            linearLayout.addView(entryLayout)
        }

        // Add the LinearLayout with all entries to the FrameLayout
        frameLayout.addView(linearLayout)
    }

    private fun selectClearAllCheckBoxes(frameLayout: FrameLayout, items: List<Map<String, String>>, action: Boolean) {
        val linearLayout = frameLayout.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until linearLayout.childCount) {
            val entryLayout = linearLayout.getChildAt(i) as? LinearLayout
            entryLayout?.let { layout ->
                val checkBox = layout.getChildAt(0) as? CheckBox
                checkBox?.let {
                    if (action){
                        if (!it.isChecked) {
                            it.isChecked = true
                            val itemId = items[i]["id"] ?: ""
                            when(filteringMenu){
                                "PERKS" -> {
                                    selectedFilterSTATSPerks.add(itemId)
                                }
                            }
                        }
                    } else {
                        if (it.isChecked) {
                            it.isChecked = false
                            val itemId = items[i]["id"] ?: ""
                            when(filteringMenu){
                                "PERKS" -> {
                                    selectedFilterSTATSPerks.remove(itemId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun filterList(items: List<Map<String, String>>, searchText: String) {
        // Filter the items list based on searchText, excluding the "id" key
        val filteredItems = items.filter { item ->
            item.any { (key, value) ->
                key == "name" && value.split(" ").any { word ->
                    word.contains(searchText, ignoreCase = true)
                }
            }
        }

        // Display the filtered items in the FrameLayout
        listEntries(filterFrame, filteredItems)
    }
    private fun saveSelectedItems(filterModificationItems: String) {
        var selectedItemsString = ""
        when(filterModificationItems){
            "selectedSTATSPerksArray" -> {
                selectedFilterSTATSPerks = selectedFilterSTATSPerks.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterSTATSPerks.joinToString(",")
            }
            "selectedDATAMiscArray" -> {
                selectedFilterDATAMisc = selectedFilterDATAMisc.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterDATAMisc.joinToString(",")
            }
        }
        if (selectedItemsString.isNullOrEmpty()){
            selectedItemsString = "1"
        }
        sharedPreferences.edit().putString(filterModificationItems, selectedItemsString).apply()
        when(filterModificationItems){
            "selectedSTATSPerksArray" -> {
                STATSPerksSetup(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
            }
        }
    }
    // Make the function suspendable
    suspend fun loadSelectedItems(){
        // Switch to a background thread to read and split data
        withContext(Dispatchers.IO) {
            val selectedSTATSPerksArray = sharedPreferences.getString("selectedSTATSPerksArray", "1")
            val selectedDATAMiscArray = sharedPreferences.getString("selectedDATAMiscArray", "1")

            if (!selectedSTATSPerksArray.isNullOrEmpty()) {selectedSTATSPerksArray?.let { selectedFilterSTATSPerks.addAll(it.split(",")) }}
            if (!selectedDATAMiscArray.isNullOrEmpty()) {selectedDATAMiscArray?.let { selectedFilterDATAMisc.addAll(it.split(",")) }}
        }
    }
    /**
     * Открывает экран фильтра Perks (roadmap, "Финализация STATS") — точка входа
     * `btn_perks_filter` (иконка-воронка в правом верхнем углу экрана Perks), заменяет
     * сломанный долгий тап по row2-вкладке (CLAUDE.md/память).
     */
    private fun openPerksFilter() {
        playNewTabSelectAudio()
        filteringMenu = "PERKS"
        filterSelectionSnapshot = selectedFilterSTATSPerks.toMutableSet()
        listEntries(filterFrame, localizedPerks)
        bindingMain.incLayoutFilterModification.root.visibility = View.VISIBLE
        bindingMain.layoutStats.visibility = View.GONE
        bindingMain.layoutItems.visibility = View.GONE
        bindingMain.layoutData.visibility = View.GONE
        enableDisableBottomButtons(false, listBottomButtons)
        enableDisableTopSwipe(false)
    }
    /**
     * Закрывает экран фильтра, общая часть для Save и Cancel (roadmap, "Редизайн экрана
     * фильтра — UX-спецификация") — раньше был отдельный крестик [X] с этой же логикой,
     * теперь оба выхода [Save]/[Cancel] должны её выполнять.
     */
    private fun closeFilterScreen() {
        bindingMain.incLayoutFilterModification.root.visibility = View.GONE
        bindingMain.layoutStats.visibility = View.VISIBLE
        bindingMain.layoutItems.visibility = View.VISIBLE
        bindingMain.layoutData.visibility = View.VISIBLE
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
    }
    /**
     * Локализованная копия [perks] (roadmap, "Финализация STATS") — `Data.kt` хранит только
     * английский текст (id/name/desc/icon), перевод не встроен в структуру напрямую (иначе
     * пришлось бы городить отдельный тип для 140 записей). Вместо этого имя/описание каждого
     * перка резолвятся через `perk_<id>_name`/`perk_<id>_desc` в strings.xml/values-ru —
     * тот же приём (`getIdentifier` по имени ресурса), что уже используется для иконок
     * перков. Вычисляется один раз: язык интерфейса меняется только через полный рестарт
     * Activity (см. `attachBaseContext()`), а не на лету.
     */
    private val localizedPerks: List<Map<String, String>> by lazy {
        perks.map { perk ->
            val id = perk["id"]
            val nameResId = resources.getIdentifier("perk_${id}_name", "string", packageName)
            val descResId = resources.getIdentifier("perk_${id}_desc", "string", packageName)
            perk + mapOf(
                "name" to if (nameResId != 0) getString(nameResId) else perk["name"].orEmpty(),
                "desc" to if (descResId != 0) getString(descResId) else perk["desc"].orEmpty(),
            )
        }
    }
    private fun STATSPerksSetup(recyclerView: RecyclerView){
        val selectedSTATSPerksString = sharedPreferences.getString("selectedSTATSPerksArray", "1")
        val selectedSTATSPerksArray: Array<String> = selectedSTATSPerksString!!.split(",").toTypedArray()

        // Filter the perk list based on the selected items
        val filteredPerksList = localizedPerks.filter { perk ->
            perk["id"] in selectedSTATSPerksArray
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = PerkAdapter(localizedPerks, selectedSTATSPerksArray, selected_button) { perk ->
            playItemSelectAudio()
            bindingMain.incLayoutTabStatsPerks.tvPerksDescriptionsText.text = (perk["desc"] ?: "No description available")
            bindingMain.incLayoutTabStatsPerks.imgPerksSelected.setImageResource(resources.getIdentifier(perk["icon"], "drawable", packageName))
            // Additional selection handling if necessary
        }

        adapter.updateData(filteredPerksList)

        recyclerView.adapter = adapter

        // Optional: Scroll to a pre-selected item or update UI as needed
        if (localizedPerks.isNotEmpty()) {
            val firstPerk = localizedPerks.find { it["id"] == selectedSTATSPerksArray[0] }
            firstPerk?.let {
                bindingMain.incLayoutTabStatsPerks.tvPerksDescriptionsText.text = (it["desc"] ?: "No description available")
                bindingMain.incLayoutTabStatsPerks.imgPerksSelected.setImageResource(resources.getIdentifier(it["icon"], "drawable", packageName))
            }
        }
    }
    /***********************************************************************************************************
     * SHARED PREFERENCES
     **********************************************************************************************************/
    private fun saveValues(etSettings1: String, etSettings2: Int, etSettings3: String, uiColourID: Int, etSettings5: Float, dateFormat: Int, showTutorial: Boolean, trueFullscreen: Boolean, gameYear: Int, playerRegion: String, languageID: Int, ambientSoundEnabled: Boolean) {
        sharedPreferences.edit().putString(playerName_SPKey, etSettings1).apply()
        sharedPreferences.edit().putString(playerRegion_SPKey, playerRegion).apply()
        sharedPreferences.edit().putInt(playerLevel_SPKey, etSettings2).apply()
        sharedPreferences.edit().putString(customMusicFolder_SPKey, etSettings3).apply()
        sharedPreferences.edit().putInt(playerUIColour_SPKey, uiColourID).apply()
        sharedPreferences.edit().putFloat(customMapScaling_SPKey, etSettings5).apply()
        sharedPreferences.edit().putInt(dateFormat_SPKey, dateFormat).apply()
        sharedPreferences.edit().putBoolean("ShowTutorial", showTutorial).apply()
        sharedPreferences.edit().putBoolean("TrueFullscreen", trueFullscreen).apply()
        sharedPreferences.edit().putInt(gameYear_SPKey, gameYear).apply()
        sharedPreferences.edit().putInt(appLanguage_SPKey, languageID).apply()
        sharedPreferences.edit().putBoolean("AmbientSoundEnabled", ambientSoundEnabled).apply()
    }
    private fun saveBluetoothValues(etBlueMAC: String, etBlueSUUID: String, etBlueRUUID: String, etBlueWUUID: String) {
        sharedPreferences.edit().putString(bluetoothMAC_SPKey, etBlueMAC).apply()
        sharedPreferences.edit().putString(bluetoothSUUID_SPKey, etBlueSUUID).apply()
        sharedPreferences.edit().putString(bluetoothRUUID_SPKey, etBlueRUUID).apply()
        sharedPreferences.edit().putString(bluetoothWUUID_SPKey, etBlueWUUID).apply()
    }
    private fun saveViewState(layoutParams: ViewGroup.MarginLayoutParams) {
        sharedPreferences.edit().putInt("width", layoutParams.width).apply()
        sharedPreferences.edit().putInt("height", layoutParams.height).apply()
        sharedPreferences.edit().putInt("leftMargin", layoutParams.leftMargin).apply()
        sharedPreferences.edit().putInt("topMargin", layoutParams.topMargin).apply()
    }




    /**
     * Язык интерфейса (roadmap, "Видение приложения", п.2, шаг 4) — независимый от
     * системного языка телефона, в отличие от обычного механизма values-ru (который сам
     * по себе продолжает работать как фолбэк, пока язык явно не выбран в Settings).
     * appLanguage_SPKey не задан (-1) на свежей установке — тогда контекст не трогаем
     * вообще, приложение ведёт себя как раньше, языком рулит система.
     */
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("PipDroid_Preferences", Context.MODE_PRIVATE)
        val languageCode = when (prefs.getInt("appLanguage", -1)) {
            0 -> "ru"
            1 -> "en"
            else -> null
        }
        if (languageCode == null) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    /***********************************************************************************************************
     *
     *
     * MAIN
     *
     *
     **********************************************************************************************************/
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Choose APP theme
        when(sharedPreferences.getInt(playerUIColour_SPKey, 0)){
            0 -> {theme.applyStyle(R.style.Theme_PipDroid_GreenUI, true)}
            1 -> {theme.applyStyle(R.style.Theme_PipDroid_AmberUI, true)}
            2 -> {theme.applyStyle(R.style.Theme_PipDroid_WhiteUI, true)}
            3 -> {theme.applyStyle(R.style.Theme_PipDroid_BlueUI, true)}
        }

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val heightAPP = displayMetrics.heightPixels
        val widthAPP = displayMetrics.widthPixels

        // Set orientation based on screen dimensions
        if (widthAPP == heightAPP) {
            // Landscape mode
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        trueFullscreen = sharedPreferences.getBoolean("TrueFullscreen", true)

        if(trueFullscreen){
            //Remove notification bar from APP
            hideSystemUI()
        }

        bindingMain =  ActivityMainBinding.inflate(layoutInflater)
        val viewMain = bindingMain.root
        setContentView(viewMain)
        mirrorDisplayCutoutInset(viewMain)

        //Load saved size and position
        loadViewState()

        //Initialize RadioWave-View
        lineVisualizer = findViewById(R.id.radioWave)

        // Тема (selected_button/selectedRowButton и т.п., applyAppTheme()) должна быть
        // применена ДО setupModeSelectScreen()/setupPipBoy2000Wizard() — экран выбора
        // режима строит ModeSelectAdapter с текущим selected_button сразу при вызове, а не
        // лениво при показе. Раньше блок стоял ниже — selected_button ещё был на
        // компилируемом дефолте (зелёный, см. объявление private var selected_button)
        // независимо от сохранённой темы, подсветка выбранного пункта в мастере оставалась
        // зелёной на всех темах (баг, найден при тесте редизайна экрана фильтра).
        /* CHANGE Drawables / apply theme extras */
        when(sharedPreferences.getInt(playerUIColour_SPKey, 0)){
            //GREEN
            0 -> {
                val scrollBarDrawable = getDrawableCompat(this, R.drawable.scrollbar_custom_green)
                applyAppTheme(0, scrollBarDrawable)
            }
            //AMBER
            1 -> {
                val scrollBarDrawable = getDrawableCompat(this, R.drawable.scrollbar_custom_amber)
                applyAppTheme(1, scrollBarDrawable)
            }
            //WHITE
            2 -> {
                val scrollBarDrawable = getDrawableCompat(this, R.drawable.scrollbar_custom_white)
                applyAppTheme(2, scrollBarDrawable)
            }
            //BLUE
            3 -> {
                val scrollBarDrawable = getDrawableCompat(this, R.drawable.scrollbar_custom_blue)
                applyAppTheme(3, scrollBarDrawable)
            }
        }

        // Экран выбора режима (roadmap, "Видение приложения") — первое, что видит игрок
        setupModeSelectScreen()
        setupPipBoy2000Wizard()
        registerDebugCommandReceiver()

        // Радиостанции теперь создаются лениво (см. turnRadioOnBuiltIn()) — на холодном
        // старте им и так нечего выключать, но вызов оставлен на случай будущих путей входа.
        turnAllRadioOff()

        //Keep phone screen active
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // MEDIA SETUP — намеренно пусто. Все звуки/радиостанции/фоновый эмбиент теперь
        // создаются лениво, в момент реального использования (roadmap, "Рефакторинг кода" —
        // память фонового процесса), а не все разом здесь при каждом старте. См.
        // playCNDSelectAudio()/playNewTabSelectAudio()/playLightOnAudio()/playLightOffAudio()
        // (одноразовые UI-звуки, create-play-release), startAmbientBackgroundSound()/
        // turnRadioOnBuiltIn() (живут дольше одного проигрывания, до явного стопа).

        //BOTTOM BUTTON SETUP (DEFAULT STATUS)
        bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks)


        listStatsSpecials.add(bindingMain.incLayoutTabStatsSpecial.layoutTabStatsSpecialStrength)
        listStatsSpecials.add(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialPerception)
        listStatsSpecials.add(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialEndurance)
        listStatsSpecials.add(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialCharisma)
        listStatsSpecials.add(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialIntelligence)
        listStatsSpecials.add(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialAgility)
        listStatsSpecials.add(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialLuck)

        bindingMain.incLayoutTabStatsSpecial.tvSpecialStrengthValue.text = sharedPreferences.getInt("SPECIAL_S", 5).toString()
        bindingMain.incLayoutTabStatsSpecial.tvSpecialPerceptionValue.text = sharedPreferences.getInt("SPECIAL_P", 5).toString()
        bindingMain.incLayoutTabStatsSpecial.tvSpecialEnduranceValue.text = sharedPreferences.getInt("SPECIAL_E", 5).toString()
        bindingMain.incLayoutTabStatsSpecial.tvSpecialCharismaValue.text = sharedPreferences.getInt("SPECIAL_C", 5).toString()
        bindingMain.incLayoutTabStatsSpecial.tvSpecialIntelligenceValue.text = sharedPreferences.getInt("SPECIAL_I", 5).toString()
        bindingMain.incLayoutTabStatsSpecial.tvSpecialAgilityValue.text = sharedPreferences.getInt("SPECIAL_A", 5).toString()
        bindingMain.incLayoutTabStatsSpecial.tvSpecialLuckValue.text = sharedPreferences.getInt("SPECIAL_L", 5).toString()

        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBarter)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBigGuns)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsEnergyWeapons)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsExplosives)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsLockpick)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMedicine)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMeleeWeapons)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsRepair)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsScience)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSmallGuns)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSneak)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSpeech)
        listStatsSkills.add(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsUnarmed)

        bindingMain.incLayoutTabStatsSkills.tvSkillsBarterValue.text = sharedPreferences.getInt("SKILLS_1", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsBigGunsValue.text = sharedPreferences.getInt("SKILLS_2", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsEnergyWeaponsValue.text = sharedPreferences.getInt("SKILLS_3", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsExplosivesValue.text = sharedPreferences.getInt("SKILLS_4", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsLockpickValue.text = sharedPreferences.getInt("SKILLS_5", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsMedicineValue.text = sharedPreferences.getInt("SKILLS_6", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsMeleeWeaponsValue.text = sharedPreferences.getInt("SKILLS_7", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsRepairValue.text = sharedPreferences.getInt("SKILLS_8", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsScienceValue.text = sharedPreferences.getInt("SKILLS_9", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsSmallGunsValue.text = sharedPreferences.getInt("SKILLS_10", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsSneakValue.text = sharedPreferences.getInt("SKILLS_11", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsSpeechValue.text = sharedPreferences.getInt("SKILLS_12", 10).toString()
        bindingMain.incLayoutTabStatsSkills.tvSkillsUnarmedValue.text = sharedPreferences.getInt("SKILLS_13", 10).toString()

        listDataMisc.add(bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry1)
        listDataMisc.add(bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry2)

        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioGnr)
        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioNvr)
        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclave)
        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioCustom)

        listItemsClockButtons.add(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockButtons.btnClockTime)
        listItemsClockButtons.add(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockButtons.btnClockAlarm)
        listItemsClockButtons.add(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockButtons.btnClockTimer)
        listItemsClockButtons.add(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockButtons.btnClockStopwatch)
        listItemsClockButtons.add(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockButtons.btnClockMelody)

        // SCREEN SCAN ANIMATION
        val translateAnimation: Animation = TranslateAnimation(0, 0.0f, 0, 0.0f, 1, -4.0f, 1, 8.0f)
        translateAnimation.duration = 9000
        translateAnimation.repeatCount = -1
        bindingMain.imgScanline.animation = translateAnimation
        bindingMain.imgScanline.alpha = 0.2f

        //Set Selected buttons by default
        setupSTATS()
        setupDATA()
        setupITEMSClock()
        selectedSubMenu = bindingMain.incLayoutTabStatsBottom.btnStatsStatus
        findViewById<Button>(R.id.btn_stats_status).setBackgroundResource(selected_button)
        topLevelButtonsModify("STATS")

        /***********************************************************************************************************
         * ШАПКА — строка 1 (roadmap, "Новая шапка + единый Settings", п.3): переключатель
         * верхнего уровня STATS/ITEMS/DATA, шестерёнка Settings, индикатор BLE. Один общий
         * инстанс на всё приложение (inc_layout_header_toplevel), не по копии на раздел.
         **********************************************************************************************************/
        // menuNavigator.resetToRoot() — обязательная пара к menuChangeBLE() при любом
        // переключении верхнего уровня, не только по BLE-команде (см. handleBleCommand()):
        // иначе энкодер после тача по STATS/ITEMS/DATA продолжает крутить дерево ПРЕЖНЕГО
        // раздела (roadmap, "Модель навигации энкодером" — проверка после переделки шапки).
        bindingMain.incLayoutHeaderToplevel.btnHeaderStats.setOnClickListener{
            menuChangeBLE("STATS")
            menuNavigator.resetToRoot(statsMenuRoot())
        }
        bindingMain.incLayoutHeaderToplevel.btnHeaderItems.setOnClickListener{
            menuChangeBLE("ITEMS")
            menuNavigator.resetToRoot(itemsMenuRoot())
        }
        bindingMain.incLayoutHeaderToplevel.btnHeaderData.setOnClickListener{
            menuChangeBLE("DATA")
            menuNavigator.resetToRoot(dataMenuRoot())
        }
        bindingMain.incLayoutHeaderToplevel.btnHeaderRadio.setOnClickListener{
            menuChangeBLE("RADIO")
            menuNavigator.resetToRoot(radioMenuRoot())
        }
        bindingMain.incLayoutHeaderToplevel.btnHeaderSettings.setOnClickListener{
            bindingMain.incLayoutSettingsGlobal.root.visibility = View.VISIBLE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
        }
        updateBLEConnected(if (bleService?.isConnected() == true) "CONNECTED" else "DISCONNECTED")

        // Clock time refresh — только дата, время идёт отдельным полем (HH:mm, ниже).
        // Раньше это был один комбинированный формат "<дата>, HH:mm" на одну TextView,
        // общая нижняя панель показывает дату и время как отдельные элементы (roadmap,
        // "Новая шапка + единый Settings").
        when(sharedPreferences.getInt(dateFormat_SPKey, 0)){
            0 -> { selectedDateFormat = "MM.dd.yy"}
            1 -> { selectedDateFormat = "MM.dd.yyyy"}
            2 -> { selectedDateFormat = "dd.MM.yy"}
            3 -> { selectedDateFormat = "dd.MM.yyyy"}
            4 -> { selectedDateFormat = "yyyy.MM.dd"}
        }
        tickThread = object : Thread() {
            @SuppressLint("SimpleDateFormat")
            override fun run() {
                try {
                    while (!this.isInterrupted) {
                        sleep(300)
                        runOnUiThread {
                            // Игровой год (roadmap, "Новая шапка + единый Settings", п.2):
                            // реальные месяц/день/время остаются как есть, подменяется
                            // только YEAR, перед тем как Calendar уйдёт в форматирование.
                            val gameCalendar = Calendar.getInstance()
                            gameCalendar.set(Calendar.YEAR, sharedPreferences.getInt(gameYear_SPKey, 2276))
                            val dateOnly: String = SimpleDateFormat(selectedDateFormat).format(gameCalendar.time)
                            val timeHHmm: String = SimpleDateFormat("HH:mm").format(gameCalendar.time)
                            val timess: String = SimpleDateFormat(":ss").format(gameCalendar.time)
                            bindingMain.incLayoutHeaderBottomCommon.tvBottomDateValue.text = dateOnly
                            bindingMain.incLayoutHeaderBottomCommon.tvBottomTimeValue.text = timeHHmm
                            bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTime.tvClockTimeHm.text = timeHHmm
                            bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTime.tvClockTimeS.text = timess
                            bindingMain.incLayoutHeaderToplevel.tvHeaderBattery.text = getBatteryPercent().toString()
                            checkAlarmFiring(gameCalendar)
                            checkTimerFiring()
                            updateStopwatchDisplay()
                        }
                    }
                } catch (_: InterruptedException) {}
            }
        }
        tickThread?.start()

        // Initialize the GestureDetector
        menuGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            // Detects swiping left or right
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2?.x?.minus(e1!!.x) ?: 0f
                val diffY = e2?.y?.minus(e1!!.y) ?: 0f
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // Swipe was horizontal
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swiped to the right
                            onMenuSwipeRight()
                        } else {
                            // Swiped to the left
                            onMenuSwipeLeft()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bindingMain.titleConstraintLayout.setOnTouchListener{v, event ->
            if(menuSwipeEnabled){
                menuGestureDetector.onTouchEvent(event)
            }
            true
        }


        /***********************************************************************************************************
         * DISCLAIMER (переиспользует старый Welcome-экран Tutorial — см. roadmap
         * "Дисклеймер при запуске — UX-спецификация". Остальные страницы тьюториала
         * (Whatsnew/Stats/Items/Data/Settings) остаются в разметке нетронутыми про запас
         * (roadmap п.20), сейчас не подключены — показывается только Welcome/Disclaimer.
         **********************************************************************************************************/
        if (sharedPreferences.getBoolean("ShowTutorial", true)) {
            bindingMain.constraintlayoutTutorial.visibility = View.VISIBLE
        } else {
            bindingMain.constraintlayoutTutorial.visibility = View.GONE
        }

        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.tvTutorialWelcome.setTextColor(currentWizardAccentColor())
        setWizardButtonState(bindingMain.incLayoutTabTutorialBase.btnTutorialClose, selected = false)
        equalizeButtonWidths(
            bindingMain.incLayoutTabTutorialBase.btnNextpage,
            bindingMain.incLayoutTabTutorialBase.btnTutorialClose
        )

        // [Далее] — визуально задизейблен тем же приёмом, что и [Выбрать] для PipBoy 3000
        // в мод-селекте: кнопка остаётся кликабельной (не android:enabled=false), но по
        // клику ничего не делает, кроме звука ошибки. Задел под будущий многостраничный
        // тьюториал (roadmap п.20) — сейчас у экрана фактически одна страница.
        setWizardButtonDisabled(bindingMain.incLayoutTabTutorialBase.btnNextpage)
        bindingMain.incLayoutTabTutorialBase.btnNextpage.setOnClickListener {
            playErrorAudio()
        }

        bindingMain.incLayoutTabTutorialBase.btnTutorialClose.setOnClickListener {
            playNewTabSelectAudio()
            // Чекбокс на этом экране инвертирован относительно чекбокса в Settings
            // ("Больше не показывать" вместо "Показывать обучение при запуске") — оба
            // читают/пишут один и тот же ключ ShowTutorial, поэтому mirror isChecked()
            // напрямую нельзя, см. roadmap "Дисклеймер при запуске — UX-спецификация".
            showTutorialBool = !bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.isChecked()
            sharedPreferences.edit().putBoolean("ShowTutorial", showTutorialBool).apply()
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings.setChecked(showTutorialBool)
            bindingMain.constraintlayoutTutorial.visibility = View.GONE
        }


        /***********************************************************************************************************
         * FILTER MODIFICATION
         **********************************************************************************************************/

        filterFrame = bindingMain.incLayoutFilterModification.filterModificationFrame
        CoroutineScope(Dispatchers.Main).launch {
            loadSelectedItems()
            // Any UI updates can be done here after the function completes
        }

        // Плейсхолдер "Filter" — то же слабое затенение, что у соседних (не активных)
        // пунктов row2 (renderRow2(), alpha 0.55 для dist ±1), а не дефолтный
        // android:textColorHint темы (тот на ~10% альфы, themeXCND, слишком блёклый) —
        // и явно акцентом темы, не системным серым (roadmap, "Редизайн экрана фильтра —
        // UX-спецификация").
        bindingMain.incLayoutFilterModification.etFilterModificationValue.setHintTextColor(
            ColorUtils.setAlphaComponent(currentWizardAccentColor(), (0.55f * 255).toInt())
        )

        // Тематизация 5 кнопок экрана (roadmap, "Редизайн экрана фильтра —
        // UX-спецификация") — тот же приём, что у мастера/Settings: PipWizardButtonStyle
        // в разметке даёт нейтральную заливку, акцент темы — backgroundTintList кодом.
        val filterAccent = currentWizardAccentColor()
        listOf(
            bindingMain.incLayoutFilterModification.btnFilterModificationCancel,
            bindingMain.incLayoutFilterModification.btnFilterModificationFilter,
            bindingMain.incLayoutFilterModification.btnFilterModificationSelect,
            bindingMain.incLayoutFilterModification.btnFilterModificationClear,
            bindingMain.incLayoutFilterModification.btnFilterModificationSave
        ).forEach { it.backgroundTintList = ColorStateList.valueOf(filterAccent) }

        bindingMain.incLayoutFilterModification.btnFilterModificationCancel.setOnClickListener{
            playNewTabSelectAudio()
            // Откатываем несохранённые правки чекбоксов (см. filterSelectionSnapshot) —
            // saveSelectedItems() не вызывается, персистентные настройки и видимый список
            // Perks не трогаются.
            when(filteringMenu){
                "PERKS" -> selectedFilterSTATSPerks = filterSelectionSnapshot.toMutableSet()
            }
            closeFilterScreen()
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationSelect.setOnClickListener{
            playNewTabSelectAudio()
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, localizedPerks, true)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationClear.setOnClickListener{
            playNewTabSelectAudio()
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, localizedPerks, false)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationFilter.setOnClickListener{
            playNewTabSelectAudio()
            val filterText = bindingMain.incLayoutFilterModification.etFilterModificationValue.text.toString()

            when(filteringMenu){
                "PERKS" -> filterList(localizedPerks, filterText)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationSave.setOnClickListener{
            playNewTabSelectAudio()
            when(filteringMenu){
                "PERKS" -> saveSelectedItems("selectedSTATSPerksArray")
            }
            closeFilterScreen()
        }

        /***********************************************************************************************************
         * STATS
         **********************************************************************************************************/

        /*
        ////////////////////////////////////////////////////////
        STATS - STATUS MENU
        */
        bindingMain.incLayoutTabStatsBottom.btnStatsStatus.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.GONE
        }

        val statusButtonsSetup = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons
        statusButtonsSetup.btnWoundLight.setOnClickListener {
            onWoundActionTapped(statusButtonsSetup.layoutTabStatusWoundLightRow) {
                startWoundTimer(WoundPhase.BLEED, WoundSeverity.LIGHT, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
            }
        }
        statusButtonsSetup.btnWoundHeavy.setOnClickListener {
            onWoundActionTapped(statusButtonsSetup.layoutTabStatusWoundHeavyRow) {
                startWoundTimer(WoundPhase.BLEED, WoundSeverity.HEAVY, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
            }
        }
        statusButtonsSetup.btnStunned.setOnClickListener {
            onWoundActionTapped(statusButtonsSetup.layoutTabStatusStunnedRow) {
                startWoundTimer(WoundPhase.STUNNED, null, STUN_DURATION_SECONDS)
            }
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.btnTabStatusWoundStop.setOnClickListener {
            playNewTabSelectAudio()
            stopWoundTimerEarly()
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.btnTabStatusWoundSkip.setOnClickListener {
            skipWoundTimer()
        }

        // Тач-цели на всей фигуре (roadmap, "Редизайн STATS/Status — UX-спецификация",
        // фидбек по итогам тестирования): каждая часть тела + сам контейнер фигуры (для
        // пустых промежутков) — короткий тап toggle'ит CRIPPLED этой части (или revive,
        // если персонаж мёртв), 5-секундный hold откуда угодно — пасхалка (перенесена
        // сюда с прежнего tv_tab_status_cnd_name). См. setupFigureTouchTarget().
        val cndContentSetup = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        setupFigureTouchTarget(cndContentSetup.layoutTabStatusCndPipboy) {}
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyHead) { toggleCrippledHead() }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyTorso) { toggleCrippledTorso() }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyLeftArm) { toggleCrippledLeftArm() }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyRightArm) { toggleCrippledRightArm() }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyLeftLeg) { toggleCrippledLeftLeg() }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyRightLeg) { toggleCrippledRightLeg() }
        cndContentSetup.incLayoutTabStatsCndPopup.btnTabStatsCndPopupClose.setOnClickListener{
            cndContentSetup.incLayoutTabStatsCndPopup.root.visibility = View.GONE
            cndContentSetup.layoutTabStatusCndContent.visibility = View.VISIBLE
            enableDisableBottomButtons(true, listBottomButtons)
            enableDisableTopSwipe(true)
        }

        // Кнопки таймера ранения должны выглядеть как кнопки — тонируем текущим акцентом
        // темы, тем же приёмом, что кнопки экрана ITEMS/Часы (currentWizardAccentColor()).
        val woundAccentTint = ColorStateList.valueOf(currentWizardAccentColor())
        cndContentSetup.btnTabStatusWoundStop.backgroundTintList = woundAccentTint
        cndContentSetup.btnTabStatusWoundSkip.backgroundTintList = woundAccentTint

        /*
        ////////////////////////////////////////////////////////
        STATS - SPECIAL MENU
        */
        bindingMain.incLayoutTabStatsBottom.btnStatsSpecial.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.GONE
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabStatsSpecialStrength.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabStatsSpecialStrength, listStatsSpecials, "STRENGTH")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_strength)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_strength_description)
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialPerception.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialPerception, listStatsSpecials, "PERCEPTION")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_perception)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_perception_description)
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialEndurance.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialEndurance, listStatsSpecials, "ENDURANCE")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_endurance)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_endurance_description)
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialCharisma.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialCharisma, listStatsSpecials, "CHARISMA")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_charisma)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_charisma_description)
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialIntelligence.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialIntelligence, listStatsSpecials, "INTELLIGENCE")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_intelligence)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_intelligence_description)
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialAgility.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialAgility, listStatsSpecials, "AGILITY")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_agility)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_agility_description)
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialLuck.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialLuck, listStatsSpecials, "LUCK")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_luck)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_luck_description)
        }

        // Кнопки +/- (roadmap, "Финализация STATS") — тап меняет значение выбранной
        // характеристики на 1 (onClick), удержание повторяет через longPressRunnable
        // (onTouch, тот же приём, что раньше был у самой строки). SPECIAL (1-10) без
        // разгона — фиксированный интервал 500мс.
        bindingMain.incLayoutTabStatsSpecial.btnSpecialIncrease.setOnClickListener {
            adjustSelectedSpecial(1)
        }
        bindingMain.incLayoutTabStatsSpecial.btnSpecialIncrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSPECIALValueIncreasing = true
                    handler.postDelayed(longPressRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIALValueIncreasing = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }
        bindingMain.incLayoutTabStatsSpecial.btnSpecialDecrease.setOnClickListener {
            adjustSelectedSpecial(-1)
        }
        bindingMain.incLayoutTabStatsSpecial.btnSpecialDecrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSPECIALValueDecreasing = true
                    handler.postDelayed(longPressRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIALValueDecreasing = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }
        val specialValueButtonsAccentTint = ColorStateList.valueOf(currentWizardAccentColor())
        bindingMain.incLayoutTabStatsSpecial.btnSpecialIncrease.backgroundTintList = specialValueButtonsAccentTint
        bindingMain.incLayoutTabStatsSpecial.btnSpecialDecrease.backgroundTintList = specialValueButtonsAccentTint



        /*
        ////////////////////////////////////////////////////////
        STATS - SKILLS MENU
        */
        bindingMain.incLayoutTabStatsBottom.btnStatsSkills.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsSkills, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.GONE
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBarter.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBarter, listStatsSkills, "BARTER")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_barter)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_barter_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBigGuns.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBigGuns, listStatsSkills, "BIGGUNS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_big_guns)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_big_guns_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsEnergyWeapons.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsEnergyWeapons, listStatsSkills, "ENERGYWEAPONS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_energy_weapons)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_energy_weapons_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsExplosives.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsExplosives, listStatsSkills, "EXPLOSIVES")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_explosives)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_explosives_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsLockpick.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsLockpick, listStatsSkills, "LOCKPICK")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_lockpick)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_lockpick_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMedicine.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMedicine, listStatsSkills, "MEDICINE")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_medicine)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_medicine_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMeleeWeapons.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMeleeWeapons, listStatsSkills, "MELEEWEAPONS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_melee_weapons)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_melee_weapons_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsRepair.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsRepair, listStatsSkills, "REPAIR")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_repair)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_repair_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsScience.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsScience, listStatsSkills, "SCIENCE")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_science)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_science_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSmallGuns.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSmallGuns, listStatsSkills, "SMALLGUNS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_small_guns)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_small_guns_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSneak.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSneak, listStatsSkills, "SNEAK")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_sneak)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_sneak_description)
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSpeech.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSpeech, listStatsSkills, "SPEECH")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_speech)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_speech_description)
        }
        
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsUnarmed.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsUnarmed, listStatsSkills, "UNARMED")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_unarmed)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_unarmed_description)
        }

        // Кнопки +/- (roadmap, "Финализация STATS") — тап меняет значение выбранного
        // навыка на 1 (onClick), удержание повторяет через longPressRunnable (onTouch) с
        // разгоном (интервал 500мс→50мс, как раньше у строки) — диапазон Skills (10-100)
        // большой, без разгона листать неудобно.
        bindingMain.incLayoutTabStatsSkills.btnSkillIncrease.setOnClickListener {
            adjustSelectedSkill(1)
        }
        bindingMain.incLayoutTabStatsSkills.btnSkillIncrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSKILLValueIncreasing = true
                    delayModify = 500L
                    delayIterationCount = 0
                    handler.postDelayed(longPressRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILLValueIncreasing = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }
        bindingMain.incLayoutTabStatsSkills.btnSkillDecrease.setOnClickListener {
            adjustSelectedSkill(-1)
        }
        bindingMain.incLayoutTabStatsSkills.btnSkillDecrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSKILLValueDecreasing = true
                    delayModify = 500L
                    delayIterationCount = 0
                    handler.postDelayed(longPressRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILLValueDecreasing = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }
        val skillValueButtonsAccentTint = ColorStateList.valueOf(currentWizardAccentColor())
        bindingMain.incLayoutTabStatsSkills.btnSkillIncrease.backgroundTintList = skillValueButtonsAccentTint
        bindingMain.incLayoutTabStatsSkills.btnSkillDecrease.backgroundTintList = skillValueButtonsAccentTint


        /*
        ////////////////////////////////////////////////////////
        STATS - PERKS MENU
        */
        bindingMain.incLayoutTabStatsBottom.btnStatsPerks.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsPerks, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.VISIBLE
            STATSPerksSetup(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
        }
        bindingMain.incLayoutTabStatsPerks.btnPerksFilter.setOnClickListener {
            openPerksFilter()
        }


        // Тематизация Close/Bluetooth setup/Save (roadmap, "Редизайн экрана фильтра —
        // UX-спецификация") — те же PipWizardButtonStyle-кнопки, что у мастера: нейтральная
        // заливка в разметке, акцент темы — backgroundTintList кодом. Сохранение всегда идёт
        // через полный перезапуск Activity (см. saveButtonSettings.setOnClickListener ниже),
        // живого повторного тонирования при смене темы не требуется — обычный onCreate-путь.
        val settingsAccent = currentWizardAccentColor()
        listOf(
            bindingMain.incLayoutSettingsGlobal.btnSettingsClose,
            bindingMain.incLayoutSettingsGlobal.btnSettingsMapBundle,
            bindingMain.incLayoutSettingsGlobal.btnSettingsBluetooth,
            bindingMain.incLayoutSettingsGlobal.btnSettingsSave,
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsMapBundle.btnSettingsMapBundleClose,
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsMapBundle.btnMapBundleImport
        ).forEach { it.backgroundTintList = ColorStateList.valueOf(settingsAccent) }
        // Чекбоксы Settings — раньше тонировался только текст-лейбл (applyTextColor()),
        // сама рамка/галочка оставалась нетематизированным Material-дефолтом, на тёмном
        // фоне на грани видимости.
        listOf(
            bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings,
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings,
            bindingMain.incLayoutSettingsGlobal.cboxAmbientSoundSettings
        ).forEach { CompoundButtonCompat.setButtonTintList(it, ColorStateList.valueOf(settingsAccent)) }

        // Единый экран Settings (roadmap, "Новая шапка + единый Settings") — показывается
        // поверх текущей вкладки, не нужно больше прятать её содержимое под собой. Открытие
        // — только через шестерёнку строки 1 (btnHeaderSettings); легаси-кнопки "Settings"
        // на STATS/General, ITEMS/Misc, DATA/Misc убраны вместе с разметкой.
        bindingMain.incLayoutSettingsGlobal.btnSettingsClose.setOnClickListener{
            if(!isResizing){
                bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
        }
        val rg_DateFormat_Settings = bindingMain.incLayoutSettingsGlobal.rgSettingsDateformat
        rg_DateFormat_Settings.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_DateFormat_Settings.getChildAt(0)?.id) -> dateFormat_Selector = 0
                (rg_DateFormat_Settings.getChildAt(1)?.id) -> dateFormat_Selector = 1
                (rg_DateFormat_Settings.getChildAt(2)?.id) -> dateFormat_Selector = 2
                (rg_DateFormat_Settings.getChildAt(3)?.id) -> dateFormat_Selector = 3
                (rg_DateFormat_Settings.getChildAt(4)?.id) -> dateFormat_Selector = 4
            }
        }
        val rg_UIColour_Settings = bindingMain.incLayoutSettingsGlobal.rgSettingsUiColour
        rg_UIColour_Settings.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_UIColour_Settings.getChildAt(0)?.id) -> UIColour_Selector = 0
                (rg_UIColour_Settings.getChildAt(1)?.id) -> UIColour_Selector = 1
                (rg_UIColour_Settings.getChildAt(2)?.id) -> UIColour_Selector = 2
                (rg_UIColour_Settings.getChildAt(3)?.id) -> UIColour_Selector = 3
            }
        }
        val rg_Language_Settings = bindingMain.incLayoutSettingsGlobal.rgSettingsLanguage
        rg_Language_Settings.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_Language_Settings.getChildAt(0)?.id) -> languageSelector = 0 // ru
                (rg_Language_Settings.getChildAt(1)?.id) -> languageSelector = 1 // en
            }
        }


        /***********************************************************************************************************
         * ITEMS — Weapons/Apparel/Aid/Misc/Ammo удалены (roadmap, этап 6, п.1), были
         * игровыми Fallout-механиками, не нужны на полигонной игре. Map (п.2) — первое
         * новое содержимое, переехал из DATA/Local Map как есть, только переименован.
         * Clock/Journal — следующие контрольные точки этого же этапа.
         **********************************************************************************************************/

        /*
        ////////////////////////////////////////////////////////
        ITEMS - MAP MENU
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsMap.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsMap, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            openMapScreen()
        }
        bindingMain.incLayoutTabItemsMap.btnMapRecenter.setOnClickListener {
            recenterMapOnUser()
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK MENU (roadmap, этап 6, п.3) — раньше попап поверх RADIO (пункт "Clock"
        в списке радиостанций), теперь обычный раздел ITEMS, занимает весь экран
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsClock.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsClock, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            stopMapLocationUpdates()
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - список фичей слева (roadmap, "Часы — UX-спецификация"):
        Часы/Будильник/Таймер/Секундомер/Мелодия звонка, справа содержимое выбранной.
        */
        val clock = bindingMain.incLayoutTabItemsClock
        clock.incLayoutTabItemsClockButtons.btnClockTime.setOnClickListener {
            setSelectedClockButton(clock.incLayoutTabItemsClockButtons.btnClockTime, listItemsClockButtons)
            clock.incLayoutTabItemsClockTime.root.visibility = View.VISIBLE
            clock.incLayoutTabItemsClockAlarm.root.visibility = View.GONE
            clock.incLayoutTabItemsClockTimer.root.visibility = View.GONE
            clock.incLayoutTabItemsClockStopwatch.root.visibility = View.GONE
            clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
        }
        clock.incLayoutTabItemsClockButtons.btnClockAlarm.setOnClickListener {
            setSelectedClockButton(clock.incLayoutTabItemsClockButtons.btnClockAlarm, listItemsClockButtons)
            clock.incLayoutTabItemsClockTime.root.visibility = View.GONE
            clock.incLayoutTabItemsClockAlarm.root.visibility = View.VISIBLE
            clock.incLayoutTabItemsClockTimer.root.visibility = View.GONE
            clock.incLayoutTabItemsClockStopwatch.root.visibility = View.GONE
            clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
        }
        clock.incLayoutTabItemsClockButtons.btnClockTimer.setOnClickListener {
            setSelectedClockButton(clock.incLayoutTabItemsClockButtons.btnClockTimer, listItemsClockButtons)
            clock.incLayoutTabItemsClockTime.root.visibility = View.GONE
            clock.incLayoutTabItemsClockAlarm.root.visibility = View.GONE
            clock.incLayoutTabItemsClockTimer.root.visibility = View.VISIBLE
            clock.incLayoutTabItemsClockStopwatch.root.visibility = View.GONE
            clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
        }
        clock.incLayoutTabItemsClockButtons.btnClockStopwatch.setOnClickListener {
            setSelectedClockButton(clock.incLayoutTabItemsClockButtons.btnClockStopwatch, listItemsClockButtons)
            clock.incLayoutTabItemsClockTime.root.visibility = View.GONE
            clock.incLayoutTabItemsClockAlarm.root.visibility = View.GONE
            clock.incLayoutTabItemsClockTimer.root.visibility = View.GONE
            clock.incLayoutTabItemsClockStopwatch.root.visibility = View.VISIBLE
            clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
        }
        clock.incLayoutTabItemsClockButtons.btnClockMelody.setOnClickListener {
            setSelectedClockButton(clock.incLayoutTabItemsClockButtons.btnClockMelody, listItemsClockButtons)
            openClockMelodyScreen()
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - БУДИЛЬНИК (roadmap, "Часы — UX-спецификация") — свайповое колесо
        на часы/минуты (ClockWheelPicker.kt, по образцу системных часов Android, инерция —
        родная физика RecyclerView), заворот на границах, один однократный будильник.
        */
        val clockAccentTint = ColorStateList.valueOf(currentWizardAccentColor())
        val alarm = clock.incLayoutTabItemsClockAlarm
        alarm.btnClockAlarmToggle.backgroundTintList = clockAccentTint

        fun updateAlarmStatusViews() {
            if (alarmArmed) {
                alarm.tvClockAlarmStatus.text = getString(R.string.clock_alarm_status_on, String.format("%02d:%02d", alarmHour, alarmMinute))
                alarm.btnClockAlarmToggle.text = getString(R.string.clock_alarm_cancel)
            } else {
                alarm.tvClockAlarmStatus.text = getString(R.string.clock_alarm_status_off)
                alarm.btnClockAlarmToggle.text = getString(R.string.clock_alarm_set)
            }
        }
        updateAlarmStatusViews()

        ClockWheelPicker(alarm.rvClockAlarmHour, 0..23, alarmHour) { value ->
            alarmHour = value
            updateAlarmStatusViews()
        }
        ClockWheelPicker(alarm.rvClockAlarmMinute, 0..59, alarmMinute) { value ->
            alarmMinute = value
            updateAlarmStatusViews()
        }
        alarm.btnClockAlarmToggle.setOnClickListener {
            playNewTabSelectAudio()
            alarmArmed = !alarmArmed
            updateAlarmStatusViews()
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - ТАЙМЕР (roadmap, "Часы — UX-спецификация") — три колеса ЧЧ:ММ:СС
        (тот же ClockWheelPicker, что у Будильника) + пресеты, один таймер.
        */
        // layout_clock_timer_setup/layout_clock_timer_running — обычные вложенные
        // ConstraintLayout внутри layout_tab_items_clock_timer.xml, не <include>, поэтому
        // ViewBinding кладёт все id этого файла плоско на один timer-объект (без .root у
        // вложенных блоков — то же самое, что и остальные плоские экраны приложения).
        val timer = clock.incLayoutTabItemsClockTimer
        for (btn in listOf(timer.btnClockTimerPreset5, timer.btnClockTimerPreset10, timer.btnClockTimerStart,
            timer.btnClockTimerPauseResume, timer.btnClockTimerReset)) {
            btn.backgroundTintList = clockAccentTint
        }

        val timerHourWheel = ClockWheelPicker(timer.rvClockTimerHour, 0..23, timerHours) { timerHours = it }
        val timerMinuteWheel = ClockWheelPicker(timer.rvClockTimerMinute, 0..59, timerMinutes) { timerMinutes = it }
        val timerSecondWheel = ClockWheelPicker(timer.rvClockTimerSecond, 0..59, timerSeconds) { timerSeconds = it }

        fun addTimerPresetMinutes(minutesToAdd: Int) {
            val totalMinutes = (timerHours * 60 + timerMinutes + minutesToAdd) % (24 * 60)
            timerHours = totalMinutes / 60
            timerMinutes = totalMinutes % 60
            timerHourWheel.scrollToValue(timerHours)
            timerMinuteWheel.scrollToValue(timerMinutes)
        }
        timer.btnClockTimerPreset5.setOnClickListener { playNewTabSelectAudio(); addTimerPresetMinutes(5) }
        timer.btnClockTimerPreset10.setOnClickListener { playNewTabSelectAudio(); addTimerPresetMinutes(10) }

        timer.btnClockTimerStart.setOnClickListener {
            playNewTabSelectAudio()
            val totalSeconds = timerHours * 3600 + timerMinutes * 60 + timerSeconds
            if (totalSeconds > 0) {
                timerTargetEpochMillis = System.currentTimeMillis() + totalSeconds * 1000L
                timerState = TimerState.RUNNING
                timer.btnClockTimerPauseResume.text = getString(R.string.clock_timer_pause)
                timer.layoutClockTimerSetup.visibility = View.GONE
                timer.layoutClockTimerRunning.visibility = View.VISIBLE
                updateClockTimerLabel() // woundPhase == NONE здесь всегда — очищает подпись от предыдущего таймера ранения
            }
        }
        timer.btnClockTimerPauseResume.setOnClickListener {
            playNewTabSelectAudio()
            when (timerState) {
                TimerState.RUNNING -> {
                    timerRemainingSecondsAtPause = ((timerTargetEpochMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
                    timerState = TimerState.PAUSED
                    timer.btnClockTimerPauseResume.text = getString(R.string.clock_timer_resume)
                }
                TimerState.PAUSED -> {
                    timerTargetEpochMillis = System.currentTimeMillis() + timerRemainingSecondsAtPause * 1000L
                    timerState = TimerState.RUNNING
                    timer.btnClockTimerPauseResume.text = getString(R.string.clock_timer_pause)
                }
                TimerState.IDLE -> {}
            }
        }
        timer.btnClockTimerReset.setOnClickListener {
            playNewTabSelectAudio()
            // Если сейчас идёт таймер ранения — "Сброс" здесь равнозначен [Стоп] на
            // STATUS (roadmap, "Редизайн STATS/Status — UX-спецификация"): не тихий обрыв
            // без итога, а те же последствия (перевязка/лечение). Обычный сброс — только
            // когда woundPhase == NONE.
            if (woundPhase != WoundPhase.NONE) {
                stopWoundTimerEarly()
            } else {
                timerState = TimerState.IDLE
                syncClockTimerScreenVisibility()
            }
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - СЕКУНДОМЕР (roadmap, "Часы — UX-спецификация") — старт/пауза/сброс,
        без кругов.
        */
        val stopwatch = clock.incLayoutTabItemsClockStopwatch
        stopwatch.btnClockStopwatchStartPause.backgroundTintList = clockAccentTint
        stopwatch.btnClockStopwatchReset.backgroundTintList = clockAccentTint

        stopwatch.btnClockStopwatchStartPause.setOnClickListener {
            playNewTabSelectAudio()
            when (stopwatchState) {
                StopwatchState.IDLE -> {
                    stopwatchStartEpochMillis = System.currentTimeMillis()
                    stopwatchState = StopwatchState.RUNNING
                    stopwatch.btnClockStopwatchStartPause.text = getString(R.string.clock_timer_pause)
                }
                StopwatchState.RUNNING -> {
                    stopwatchElapsedMillisAtPause = System.currentTimeMillis() - stopwatchStartEpochMillis
                    stopwatchState = StopwatchState.PAUSED
                    stopwatch.btnClockStopwatchStartPause.text = getString(R.string.clock_timer_resume)
                }
                StopwatchState.PAUSED -> {
                    stopwatchStartEpochMillis = System.currentTimeMillis() - stopwatchElapsedMillisAtPause
                    stopwatchState = StopwatchState.RUNNING
                    stopwatch.btnClockStopwatchStartPause.text = getString(R.string.clock_timer_pause)
                }
            }
        }
        stopwatch.btnClockStopwatchReset.setOnClickListener {
            playNewTabSelectAudio()
            stopwatchState = StopwatchState.IDLE
            stopwatchElapsedMillisAtPause = 0L
            stopwatch.tvClockStopwatchElapsed.text = "00:00:00"
            stopwatch.btnClockStopwatchStartPause.text = getString(R.string.clock_timer_start)
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - МЕЛОДИЯ ЗВОНКА (roadmap, "Часы — UX-спецификация") — список строится
        кодом из ringtoneTracks (Data.kt), последний пункт — [Назад]. Клик по треку — превью
        play/stop (визуализатор — тот же LineVisualizer, что у Radio, отдельный инстанс).
        */
        val melody = clock.incLayoutTabItemsClockMelody
        melody.btnClockMelodySelect.backgroundTintList = clockAccentTint
        // Отдельный от Radio инстанс LineVisualizer — applyTextColor() красит только
        // общий lineVisualizer радио, этот без явного setColor() рисует линию дефолтным
        // цветом библиотеки, неотличимым от тёмного фона (баг, найденный на устройстве).
        melody.melodyWave.setColor(currentWizardAccentColor())
        melodyFocusedIndex = sharedPreferences.getInt(selectedRingtone_SPKey, 0)

        melody.layoutClockMelodyTracks.removeAllViews()
        melodyTrackRowViews.clear()
        for ((index, track) in ringtoneTracks.withIndex()) {
            val row = TextView(this, null, 0, R.style.Row2ItemStyle).apply {
                text = track.displayName
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.pipboy_mono)
                setPadding(24, 16, 24, 16)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                // Длинные названия треков раздвигали строку — одна строка + marquee
                // вместо переноса (roadmap, правка после проверки на устройстве). Скроллится
                // только активный (выбранный) пункт — isSelected переключается в
                // highlightMelodyRow(), остальные показывают статичное "...".
                maxLines = 1
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                setOnClickListener {
                    playItemSelectAudio()
                    melodyFocusedIndex = index
                    highlightMelodyRow()
                    if (melodyPreviewPlayingIndex == index) stopMelodyPreview() else startMelodyPreview(index)
                }
            }
            melody.layoutClockMelodyTracks.addView(row)
            melodyTrackRowViews.add(row)
        }
        val backRow = TextView(this, null, 0, R.style.Row2ItemStyle).apply {
            text = getString(R.string.wizard_back)
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.pipboy_mono)
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { playItemSelectAudio(); closeClockMelodyScreen() }
        }
        melody.layoutClockMelodyTracks.addView(backRow)

        melody.btnClockMelodySelect.setOnClickListener {
            playNewTabSelectAudio()
            sharedPreferences.edit().putInt(selectedRingtone_SPKey, melodyFocusedIndex).apply()
            updateMelodySelectedLabel()
            playMelodySelectedMarqueeOnce()
        }

        bindingMain.incLayoutClockFiredOverlay.btnClockFiredStop.backgroundTintList = clockAccentTint
        bindingMain.incLayoutClockFiredOverlay.btnClockFiredStop.setOnClickListener {
            playNewTabSelectAudio()
            stopClockFiredSound()
            bindingMain.incLayoutClockFiredOverlay.root.visibility = View.GONE
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - JOURNAL MENU (roadmap, этап 6, п.4) — заглушка "Раздел находится в
        разработке", полная реализация (голосовой ввод) — видение, п.8
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsJournal.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsJournal, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            stopMapLocationUpdates()
        }
        bindingMain.incLayoutTabItemsBottom.btnItemsGeiger.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsGeiger, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.VISIBLE
            stopMapLocationUpdates()
        }

        /***********************************************************************************************************
         * DATA
         **********************************************************************************************************/

        /*
        ////////////////////////////////////////////////////////
        DATA - MISC MENU
        */
        bindingMain.incLayoutTabDataBottom.btnDataMisc.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataMisc, listBottomButtons)
            bindingMain.incLayoutTabDataMisc.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataHolotapes.root.visibility = View.GONE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
        }

        /*
        ////////////////////////////////////////////////////////
        DATA - HOLOTAPES MENU (roadmap, этап 6, п.6) — заглушка "Раздел находится в
        разработке", реальное чтение голодисков блокируется готовностью USB Host на
        прошивке ESP32-S3
        */
        bindingMain.incLayoutTabDataBottom.btnDataHolotapes.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataHolotapes, listBottomButtons)
            bindingMain.incLayoutTabDataMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabDataHolotapes.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
        }

        bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry1.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry1, listDataMisc)
            bindingMain.incLayoutTabDataMisc.tvDataMiscHolotapeText.setText(R.string.data_misc_entry1_description)
        }
        bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry2.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry2, listDataMisc)
            bindingMain.incLayoutTabDataMisc.tvDataMiscHolotapeText.setText(R.string.data_misc_entry2_description)
        }

        /*
        ////////////////////////////////////////////////////////
        DATA - RADIO MENU
        // RADIO вынесен в top-level раздел строки 1 (roadmap, "Новая шапка + единый
        // Settings", п.4) — кнопка второго уровня DATA, которая раньше открывала этот
        // экран, убрана из разметки; вход теперь через btn_header_radio (см. ниже,
        // "ШАПКА — строка 1"), который идёт по тому же menuChangeBLE("RADIO"), что и
        // остальные разделы, а не отдельным ad-hoc переключением видимости.
        */
        bindingMain.incLayoutTabDataRadio.layoutTabRadioGnr.setOnClickListener{
            if(radioGNRStateSelected){
                if(bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility != View.VISIBLE){
                    turnRadioOnBuiltIn(R.raw.galaxynewsradio, ::galaxyRadioMediaPlayer)
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.VISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.INVISIBLE
                } else {
                    turnAllRadioOff()
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.INVISIBLE
                }
            } else {
                setSelectedSubMenuButton(bindingMain.incLayoutTabDataRadio.layoutTabRadioGnr, listDataRadios)
                radioGNRStateSelected = true
                radioEnclaveStateSelected = false
                radioNVRStateSelected = false
                radioCustomStateSelected = false
            }
        }
        bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclave.setOnClickListener{
            if(radioEnclaveStateSelected){
                if(bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility != View.VISIBLE){
                    turnRadioOnBuiltIn(R.raw.enclaveradio, ::enclaveRadioMediaPlayer)
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.VISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.INVISIBLE
                } else {
                    turnAllRadioOff()
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.INVISIBLE
                }
            } else {
                setSelectedSubMenuButton(bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclave, listDataRadios)
                radioGNRStateSelected = false
                radioEnclaveStateSelected = true
                radioNVRStateSelected = false
                radioCustomStateSelected = false
            }
        }
        bindingMain.incLayoutTabDataRadio.layoutTabRadioNvr.setOnClickListener{
            if(radioNVRStateSelected){
                if(bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility != View.VISIBLE){
                    turnRadioOnBuiltIn(R.raw.newvegasradio, ::newVegasRadioMediaPlayer)
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.VISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.INVISIBLE
                } else {
                    turnAllRadioOff()
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.INVISIBLE
                }
            } else {
                setSelectedSubMenuButton(bindingMain.incLayoutTabDataRadio.layoutTabRadioNvr, listDataRadios)
                radioGNRStateSelected = false
                radioEnclaveStateSelected = false
                radioNVRStateSelected = true
                radioCustomStateSelected = false
            }
        }
        bindingMain.incLayoutTabDataRadio.layoutTabRadioCustom.setOnClickListener{
            if(radioCustomStateSelected){
                if(bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility != View.VISIBLE){
                    if (checkCustomMediaPermission()) {
                        // turnAllRadioOff() ДО создания нового трека — иначе, если бы он шёл
                        // после playRandomTrack()/playTrack() (как раньше, внутри старого
                        // turnRadioOn()), он бы немедленно release()-нул только что созданный
                        // customRadioMediaPlayer вместо предыдущей станции.
                        turnAllRadioOff()
                        loadMp3Files()
                        playRandomTrack()
                        if(customMP3FilesFound){
                            activateRadioAudio(customRadioMediaPlayer!!)
                        }
                    } else {
                        requestCustomMediaPermission()
                    }
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.VISIBLE
                } else {
                    turnAllRadioOff()
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.INVISIBLE
                }
            } else {
                setSelectedSubMenuButton(bindingMain.incLayoutTabDataRadio.layoutTabRadioCustom, listDataRadios)
                radioGNRStateSelected = false
                radioEnclaveStateSelected = false
                radioNVRStateSelected = false
                radioCustomStateSelected = true
            }
        }

        // DataStore for saving Settings
        val saveButtonSettings = bindingMain.incLayoutSettingsGlobal.btnSettingsSave
        val editSettings1 = bindingMain.incLayoutSettingsGlobal.etSettings1Value //PlayerName
        val editSettingsRegion = bindingMain.incLayoutSettingsGlobal.etSettingsRegionValue //PlayerRegion
        val editSettings2 = bindingMain.incLayoutSettingsGlobal.etSettings2Value //PlayerLevel
        val editSettings3 = bindingMain.incLayoutSettingsGlobal.etSettings3Value //MusicFolder
        val editSettings5 = bindingMain.incLayoutSettingsGlobal.etSettings5Value //CustomMapScaling
        var editSettings6 = bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings //ShowTutorial
        var editSettings7 = bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings //Fullscreen
        var editSettings8 = bindingMain.incLayoutSettingsGlobal.cboxAmbientSoundSettings //AmbientSoundEnabled
        val editSettingsYear = bindingMain.incLayoutSettingsGlobal.etSettingsYearValue //GameYear

        saveButtonSettings.setOnClickListener{
            lifecycleScope.launch(Dispatchers.IO) {
                saveValues(editSettings1.text.toString(), editSettings2.text.toString().toInt(), editSettings3.text.toString(), UIColour_Selector, editSettings5.text.toString().toFloat(), dateFormat_Selector, editSettings6.isChecked(), editSettings7.isChecked(), editSettingsYear.text.toString().toInt(), editSettingsRegion.text.toString(), languageSelector, editSettings8.isChecked())
            }
            turnAllRadioOff()
            sendBLEText("STATS")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            finishAffinity() // Close all previous activities
            startActivity(intent)
        }

            // Общая нижняя панель (roadmap, "Новая шапка + единый Settings") — имя и регион
            // выставляются один раз при старте, как и остальные Settings-поля ниже:
            // сохранение настроек всегда идёт через полный перезапуск Activity (см.
            // saveButtonSettings.setOnClickListener выше), живого обновления не требуется.
            bindingMain.incLayoutHeaderBottomCommon.tvBottomNameValue.text = sharedPreferences.getString(playerName_SPKey, "Player")
            bindingMain.incLayoutHeaderBottomCommon.tvBottomRegionValue.text = sharedPreferences.getString(playerRegion_SPKey, "Richmond")
            editSettings1.setText(sharedPreferences.getString(playerName_SPKey, "Player"))
            editSettingsRegion.setText(sharedPreferences.getString(playerRegion_SPKey, "Richmond"))
            editSettings2.setText((sharedPreferences.getInt(playerLevel_SPKey, 1)).toString())
            editSettings3.setText(sharedPreferences.getString(customMusicFolder_SPKey, "Music"))
            editSettings5.setText((sharedPreferences.getFloat(customMapScaling_SPKey, 1f)).toString())
            editSettingsYear.setText((sharedPreferences.getInt(gameYear_SPKey, 2276)).toString())
            // cboxTutorialWelcome ("Больше не показывать") инвертирован относительно
            // editSettings6/ShowTutorial ("Показывать обучение при запуске") — см.
            // roadmap "Дисклеймер при запуске — UX-спецификация".
            bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.setChecked(!sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings6.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings7.setChecked(sharedPreferences.getBoolean("TrueFullscreen", true))
            editSettings8.setChecked(sharedPreferences.getBoolean("AmbientSoundEnabled", true))
            refreshModeSettingsLabel()

            bindingMain.incLayoutSettingsGlobal.rgSettingsDateformat.check(bindingMain.incLayoutSettingsGlobal.rgSettingsDateformat.getChildAt(sharedPreferences.getInt(dateFormat_SPKey, 0)).id)
            bindingMain.incLayoutSettingsGlobal.rgSettingsUiColour.check(bindingMain.incLayoutSettingsGlobal.rgSettingsUiColour.getChildAt(sharedPreferences.getInt(playerUIColour_SPKey, 0)).id)
            // appLanguage_SPKey не задан (-1) на свежей установке — тогда показываем как
            // отмеченный тот пункт, который и так уже действует через системную локаль
            // (см. attachBaseContext()), а не жёстко фиксированный вариант по умолчанию.
            val effectiveLanguageIndex = sharedPreferences.getInt(appLanguage_SPKey, -1).let {
                if (it in 0..1) it else if (Locale.getDefault().language == "ru") 0 else 1
            }
            bindingMain.incLayoutSettingsGlobal.rgSettingsLanguage.check(bindingMain.incLayoutSettingsGlobal.rgSettingsLanguage.getChildAt(effectiveLanguageIndex).id)


        /***********************************************************************************************************
         *
         * MAP DATA (roadmap, ветка app-map) — импорт бандла карты, см. MapBundleRepository/
         * openMapBundleTreeLauncher/refreshMapBundleStatus().
         *
         **********************************************************************************************************/

        val mapBundlePanel = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsMapBundle

        bindingMain.incLayoutSettingsGlobal.btnSettingsMapBundle.setOnClickListener {
            mapBundlePanel.root.visibility = View.VISIBLE
            bindingMain.incLayoutSettingsGlobal.layoutSettingsLayout.visibility = View.GONE
            mapBundlePanel.tvMapBundleImportResult.text = ""
            refreshMapBundleStatus()
        }
        mapBundlePanel.btnSettingsMapBundleClose.setOnClickListener {
            mapBundlePanel.root.visibility = View.GONE
            bindingMain.incLayoutSettingsGlobal.layoutSettingsLayout.visibility = View.VISIBLE
        }
        mapBundlePanel.btnMapBundleImport.setOnClickListener {
            openMapBundleTreeLauncher.launch(null)
        }

        /***********************************************************************************************************
         *
         * BLUETOOTH
         *
         **********************************************************************************************************/

        //BLUETOOTH
        val bluetoothButtonSettings = bindingMain.incLayoutSettingsGlobal.btnSettingsBluetooth

        bluetoothButtonSettings.setOnClickListener{
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.root.visibility = View.VISIBLE
            bindingMain.incLayoutSettingsGlobal.layoutSettingsLayout.visibility = View.GONE
        }

        val bluetoothButtonClose = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnSettingsBluetoothClose

        bluetoothButtonClose.setOnClickListener{
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.root.visibility = View.GONE
            bindingMain.incLayoutSettingsGlobal.layoutSettingsLayout.visibility = View.VISIBLE
        }

        val etBluetoothMAC = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.etMACAddressValue
        val etBluetoothSUUID = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.etServiceUUIDValue
        val etBluetoothRUUID = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.etReadUUIDValue
        val etBluetoothWUUID = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.etWriteUUIDValue
        val bluetoothButtonSave = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnSettingsBluetoothSave

        etBluetoothMAC.setText(sharedPreferences.getString(bluetoothMAC_SPKey, "AA:BB:CC:DD:EE:FF"))
        etBluetoothSUUID.setText(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothRUUID.setText(sharedPreferences.getString(bluetoothRUUID_SPKey, "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothWUUID.setText(sharedPreferences.getString(bluetoothWUUID_SPKey, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))

        bluetoothButtonSave.setOnClickListener{
            saveBluetoothValues(
                etBluetoothMAC.text.toString(),
                etBluetoothSUUID.text.toString(),
                etBluetoothRUUID.text.toString(),
                etBluetoothWUUID.text.toString()
            )
        }

        val bluetoothButtonConnect = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnSettingsConnect

        // Настройки уже сохранены в SharedPreferences кнопкой Save — PipBoyBleService
        // сам перечитывает их при (ре)коннекте, поэтому здесь достаточно просто дать
        // сервису команду подключиться заново, не таская MAC/UUID через поля Activity.
        bluetoothButtonConnect.setOnClickListener {
            if (bleServiceBound) {
                bleService?.reconnectWithCurrentSettings()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkPermissions()
            } else {
                setupBluetooth()
            }
        }

        val bluetoothButtonDisconnect = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnSettingsDisconnect

        bluetoothButtonDisconnect.setOnClickListener{
            disconnectBLE()
        }


        /***********************************************************************************************************
         *
         * РЕЖИМ РАБОТЫ (Settings) — кнопка "Изменить"
         *
         **********************************************************************************************************/

        // Легаси-инструмент "Screen Resize" (отдельный попап Resize/Move + Fullscreen поверх
        // Settings) убран целиком (roadmap, косметические правки) — регулировка рабочей
        // области теперь только через мастер PipBoy 2000/3000 (шаг DISPLAY AREA). Эта кнопка
        // вместо старого попапа заново запускает весь поток с экрана выбора режима.
        bindingMain.incLayoutSettingsGlobal.btnSettingsChangeMode.setOnClickListener {
            playNewTabSelectAudio()
            openModeSelectScreen()
        }

        // isResizing/ScaleListener/handleTouch — общий с мастером механизм (шаг DISPLAY AREA
        // сам включает isResizing на время своего показа, см. showWizardStep()), поэтому
        // остаётся и после удаления легаси-попапа.
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        
        bindingMain.root.setOnTouchListener { _, event ->
            if (isResizing) {
                handleTouch(event)
            }
            true
        }


        /***********************************************************************************************************
         *
         * LongPressFunctions
         *
         **********************************************************************************************************/
        // Долгое нажатие на шапку -> фонарик. Раньше было 3 копии (по одной на
        // tv_title_data STATS/ITEMS/DATA) — row1 теперь один общий инстанс на всё
        // приложение (roadmap, "Новая шапка + единый Settings", п.3), достаточно одного
        // слушателя на его корень.
        bindingMain.incLayoutHeaderToplevel.root.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isFlashlightOn = true
                    handler.postDelayed(longPressRunnable, 1000) // 1 seconds
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFlashlightOn = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.flFlashlight.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isFlashlightOff = true
                    handler.postDelayed(longPressRunnable, 500) // 500 mseconds
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFlashlightOff = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }

        // Восстановление после убийства процесса в фоне (roadmap, "Восстановление состояния
        // после убийства процесса — спецификация", этап 15) — savedInstanceState != null
        // гарантированно означает именно это, не холодный старт (Android сам разводит эти
        // два случая). Последний шаг onCreate() — всё остальное выше уже успело развесить
        // слушатели/собрать биндинги, на которые restoreAppState() опирается.
        if (savedInstanceState != null) {
            restoreAppState(savedInstanceState)
        }
    }

    /**
     * Восстановление состояния после убийства процесса в фоне (roadmap, "Восстановление
     * состояния после убийства процесса — спецификация", этап 15) — сохраняем ровно то,
     * что перечислено в спеке: текущий раздел+вкладка, режим работы, и всё состояние
     * системы ранений (этап 14). Bundle, не SharedPreferences — только он различает
     * "холодный старт" (savedInstanceState == null в onCreate) от восстановления.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CUR_MENU, curMenu)
        outState.putInt(KEY_ROOT_CURSOR, menuNavigator.rootCursor())
        outState.putString(KEY_PIPBOY_MODE, pipBoyMode.name)
        outState.putString(KEY_WOUND_PHASE, woundPhase.name)
        outState.putString(KEY_WOUND_SEVERITY, woundSeverity.name)
        outState.putString(KEY_TIMER_STATE, timerState.name)
        outState.putLong(KEY_TIMER_TARGET_EPOCH, timerTargetEpochMillis)
        outState.putInt(KEY_TIMER_REMAINING_AT_PAUSE, timerRemainingSecondsAtPause)
        outState.putBoolean(KEY_CRIPPLED_HEAD, crippledHead)
        outState.putBoolean(KEY_CRIPPLED_TORSO, crippledTorso)
        outState.putBoolean(KEY_CRIPPLED_LEFT_ARM, crippledLeftArm)
        outState.putBoolean(KEY_CRIPPLED_RIGHT_ARM, crippledRightArm)
        outState.putBoolean(KEY_CRIPPLED_LEFT_LEG, crippledLeftLeg)
        outState.putBoolean(KEY_CRIPPLED_RIGHT_LEG, crippledRightLeg)
        outState.putInt(KEY_STATUS_CURSOR_ROW, statusCursorRowIndex())
    }

    /** Сворачивание приложения или блокировка экрана — Activity перестаёт быть видимой
     * (в отличие от onPause(), который срабатывает и на кратких перекрытиях вроде системных
     * диалогов разрешений, onStop() — именно "игрок больше не смотрит на экран"). Эмбиент
     * освобождается (не просто мьютится — та же логика "по-настоящему стоп", что и у
     * радиостанций, см. releaseRadioPlayer()), но намерение [ambientShouldBePlaying] не
     * трогаем — тикThread/BLE-сервис по-прежнему работают в фоне независимо от этого. */
    override fun onStop() {
        super.onStop()
        releaseAmbientPlayer()
        stopMapLocationUpdates()
    }
    /** Возврат в приложение — как при обычном первом запуске, так и после onStop(). На
     * самом первом запуске ambientShouldBePlaying ещё false (флаг выставляется только
     * внутри startAmbientBackgroundSound(), которая к этому моменту ещё не вызывалась),
     * поэтому лишнего старта здесь не происходит. Карту аналогично не трогаем, если игрок
     * не был на её экране — startMapLocationUpdates() внутри проверяет разрешение сама, а
     * geoReference уже посчитан с прошлого openMapScreen(), пересчитывать не нужно. */
    override fun onStart() {
        super.onStart()
        if (ambientShouldBePlaying) {
            startAmbientBackgroundSound()
        }
        if (bindingMain.incLayoutTabItemsMap.root.visibility == View.VISIBLE) {
            startMapLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickThread?.interrupt()
        turnAllRadioOff()
        stopAmbientBackgroundSound()
        cancelBootSequence()
        // Сервис НЕ останавливаем — он должен продолжать держать BLE-связь и в фоне,
        // это и есть весь смысл foreground service (протокол, раздел 5). Отвязываемся
        // только от локального биндинга, чтобы не утекала ссылка на Activity.
        if (bleServiceBound) {
            unbindService(bleServiceConnection)
            bleServiceBound = false
        }
        debugCommandReceiver?.let {
            unregisterReceiver(it)
            debugCommandReceiver = null
        }
    }

}

object TypefaceCache {
    private var pipboyTypeface: Typeface? = null

    fun getPipboyTypeface(context: Context): Typeface {
        if (pipboyTypeface == null) {
            pipboyTypeface = Typeface.createFromAsset(context.assets, "fonts/pipboy_mono.ttf")
        }
        return pipboyTypeface!!
    }
}