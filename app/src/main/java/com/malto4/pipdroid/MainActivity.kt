package com.malto4.pipdroid

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
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
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
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    val playerUIColour_SPKey = "playerUIColour"
    val dateFormat_SPKey = "dateFormat"
    val gameYear_SPKey = "gameYear"
    val bluetoothMAC_SPKey = "bluetoothMAC"
    val bluetoothSUUID_SPKey = "bluetoothSUUID"
    val bluetoothRUUID_SPKey = "bluetoothRUUID"
    val bluetoothWUUID_SPKey = "bluetoothWUUID"
    val pipBoyMode_SPKey = "pipBoyMode"
    val appLanguage_SPKey = "appLanguage"
    val geigerDose_SPKey = "geigerDose"
    val radioLastFrequency_SPKey = "radioLastFrequency"
    private var UIColour_Selector = 0
    private var dateFormat_Selector = 0
    private var languageSelector = -1
    private var selected_button = R.drawable.button_selected_green
    private var selectedRowButton = R.drawable.status_row_selected_green
    private var selectedDateFormat = "MM.dd.yy"
    private var trueFullscreen = false
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
    private enum class StopwatchState { IDLE, RUNNING, PAUSED }
    private var stopwatchState = StopwatchState.IDLE
    private var stopwatchStartEpochMillis = 0L
    private var stopwatchElapsedMillisAtPause = 0L
    private val selectedRingtone_SPKey = "selectedRingtoneIndex"
    private var melodyFocusedIndex = 0
    private var melodyPreviewPlayer: MediaPlayer? = null
    private var melodyPreviewPlayingIndex: Int? = null
    /** payload — индекс в ringtoneTracks
     */
    private lateinit var melodyAdapter: SidebarMenuAdapter<Int?>

    /***********************************************************************************************************
     * LIST DEFINITIONS
     **********************************************************************************************************/
    private var listBottomButtons = ArrayList<Button>()
    private data class ClockFeatureMeta(val key: String, val labelRes: Int)
    private val clockMeta = listOf(
        ClockFeatureMeta("TIME", R.string.clock_feature_time),
        ClockFeatureMeta("ALARM", R.string.clock_feature_alarm),
        ClockFeatureMeta("TIMER", R.string.clock_feature_timer),
        ClockFeatureMeta("STOPWATCH", R.string.clock_feature_stopwatch),
        ClockFeatureMeta("MELODY", R.string.clock_feature_melody),
    )
    private lateinit var clockAdapter: SidebarMenuAdapter<String>
    private data class DataFileMeta(val key: String, val nameRes: Int, val descriptionRes: Int)
    private val dataFilesMeta = listOf(
        DataFileMeta("ENTRY1", R.string.data_misc_entry1_name, R.string.data_misc_entry1_description),
        DataFileMeta("ENTRY2", R.string.data_misc_entry2_name, R.string.data_misc_entry2_description),
    )
    private lateinit var dataFilesAdapter: SidebarMenuAdapter<String>
    private var radioVolume = RADIO_VOLUME_DEFAULT

    /***********************************************************************************************************
     * MEDIA PLAYERS
     **********************************************************************************************************/
    private val REQUEST_CODE_PERMISSION_RECORD_AUDIO = 23
    private val REQUEST_CODE_PERMISSION_WAKE_WORD = 24
    private var wakeWordDetector: com.malto4.pipdroid.voice.WakeWordDetector? = null
    private var mediaPlayerCndRadEffList = mutableListOf<MediaPlayer>()
    private var mediaPlayerNewTabList = mutableListOf<MediaPlayer>()
    private var mediaPlayerItemSelectList = mutableListOf<MediaPlayer>()
    private var mediaPlayerErrorList = mutableListOf<MediaPlayer>()
    private var mediaPlayerBackGround: MediaPlayer? = null

    /***********************************************************************************************************
     * MAP
     **********************************************************************************************************/
    private val mapBundleRepository by lazy { MapBundleRepository(this) }
    private val voiceModelRepository by lazy { com.malto4.pipdroid.voice.VoiceModelRepository(this) }
    private var mapGeoReference: GeoReference? = null
    private var mapLocationListener: LocationListener? = null
    private var mapHasCenteredOnUser = false
    private var pedestrianRouter: PedestrianRouter? = null
    private val markerRepository by lazy { MarkerRepository(this) }
    private var markers: MutableList<MapMarker> = mutableListOf()
    private var mapMenuState = MapMenuState.ROOT
    private var mapMenuListReturnState = MapMenuState.ROOT
    private var selectedMarkerForDetail: MapMarker? = null
        set(value) {
            field = value
            updateMapMarkerFocus()
        }
    private var pendingMarkerLatLon: Pair<Double, Double>? = null
    private var editingMarkerId: String? = null
    private var pendingTapChoiceLatLon: Pair<Double, Double>? = null
    /***********************************************************************************************************
     * JOURNAL
     **********************************************************************************************************/
    private val journalRepository by lazy { JournalRepository(this) }
    private var journalEntries: MutableList<JournalEntry> = mutableListOf()
    private var selectedJournalEntryForDetail: JournalEntry? = null
    private var editingJournalEntryId: String? = null
    private var journalEditorOpenFor: String? = null
    private sealed class JournalSidebarEntry {
        object NewEntry : JournalSidebarEntry()
        data class Existing(val entry: JournalEntry) : JournalSidebarEntry()
        object Menu : JournalSidebarEntry()
    }
    private val voiceDictationService by lazy { com.malto4.pipdroid.voice.VoiceDictationService() }
    private enum class JournalDictationState { IDLE, LOADING, LISTENING }
    private var journalDictationState = JournalDictationState.IDLE
    private val REQUEST_CODE_PERMISSION_JOURNAL_DICTATION = 25
    private val REQUEST_CODE_PERMISSION_BLUETOOTH_SETTINGS_SCAN = 26
    private enum class MapRouteState { NONE, BUILT, ACTIVE }
    private var mapRouteState = MapRouteState.NONE
    private var mapRouteDestination: Pair<Double, Double>? = null
    private var mapRouteLatLonPath: List<Pair<Double, Double>> = emptyList()
    private var pendingMapReadyAction: (() -> Unit)? = null
    private enum class MapTapMode { NONE, PLACE_MARKER, ROUTE_TO_POINT }
    private var mapTapMode = MapTapMode.NONE
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
        // Флейвор-текст "стены кода"
        private const val BOOT_CODEWALL_BLOCK = "* 1 0 0x0000A4 0x0000000000000000 start memory discovery\n" +
            "0 0x0000A4 0x0000000000000000 1 0 0x000014 0x0000000000000000 CPU0 starting cell\n" +
            "relocation0 0x0000A4 0x0000000000000000 1 0 0x000009 0x0000000000000000\n" +
            "CPU0 launch EFI0 0x0000A4 0x0000000000000000 1 0 0x000009 0x00000000000E003D\n" +
            "CPU0 starting EFI0 0x0000A4 0x0000000000000000 1 0 0x0000A4 0x0000000000000000\n"
        private val BOOT_CODEWALL_TEXT = BOOT_CODEWALL_BLOCK.repeat(24)
        // Общий баннер PIP-OS
        private const val PIP_OS_BANNER = "**************** PIP-OS(R) V7.1.0.8 ****************"
        // Терминальная печать кадра
        private const val BOOT_TERMINAL_TEXT = PIP_OS_BANNER + "\n\n" +
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
        private val SHUTDOWN_HEADER_PREFIX = "$PIP_OS_BANNER\n\n"
        private const val SHUTDOWN_BODY_TEXT = "STOPPING ALL PROCESSES...\n" +
            "DUMPING MEMORY...\n" +
            "DISCONNECTING..."

        // Система ранений/кровотечения
        private const val WOUND_BLEED_BANDAGE_DURATION_SECONDS = 600
        private const val STUN_DURATION_SECONDS = 300

        // Восстановление состояния после убийства процесса в фоне
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

        // Пункт "В меню" в боковых списках
        private const val SIDEBAR_BACK_PAYLOAD = "BACK"

        // journalEditorOpenFor
        private const val JOURNAL_NEW_ENTRY_SENTINEL = "JOURNAL_NEW_ENTRY"

        // Прокрутка длинной записи энкодером
        private const val SIDEBAR_RECORD_SCROLL_STEP_DP = 60f

        // Карта
        private const val MAP_ZOOM_STEP_FACTOR = 1.4f
        private const val MAP_MARKER_TAP_RADIUS_DP = 28f
        private const val MAP_ROUTE_REROUTE_THRESHOLD_M = 30.0
        // Отступ от краёв видимой области при автоматическом центрировании на построенном
        // маршруте (roadmap, этап 27) — начальная/конечная точка не должны прилипать к
        // самому краю экрана.
        private const val MAP_ROUTE_FIT_PADDING_DP = 28f
        // Шаг панорамирования уголками энкодера (roadmap, этап 27, энкодер-эргономика
        // карты, п.2) — в экранных dp, не в пикселях битмапа карты (то же пространство, что
        // и panMapBy()/postTranslate — не зависит от текущего зума).
        private const val MAP_PAN_STEP_DP = 80f

        // Счётчик радиации
        private const val GEIGER_LETHAL_DOSE_RAD = 1000
        private const val GEIGER_SCALE_START_BIAS = 0.2489f
        private const val GEIGER_SCALE_END_BIAS = 0.9522f

        // Реальное радио
        private const val RADIO_VOLUME_MIN = 0
        private const val RADIO_VOLUME_MAX = 100
        private const val RADIO_VOLUME_DEFAULT = 50
        private const val RADIO_FREQUENCY_DEFAULT = 999
    }

    /***********************************************************************************************************
     * BLUETOOTH
     **********************************************************************************************************/
    private val menuNavigator = MenuNavigator()
    /**
     * true — пока [simulateEncoderTabHighlight] прогоняет `performClick()` узла меню 2
     * уровня (Status/SPECIAL/.../Map/Journal/Clock/Geiger/Files) ради его `onHighlight`
     * (roadmap, этап 27 — доработка энкодер-эргономики: курсор `ENC` просто перебирает эти
     * узлы, не должен при этом проваливаться в боковое меню и подсвечивать его первый
     * пункт). Реальный тап по той же кнопке (`setOnClickListener` ниже) видит флаг false и
     * доводит дело до конца — синхронизирует курсор энкодера и сразу проваливается на
     * первый дочерний узел (`menuNavigator.activateSelected()`), т.к. тап равносилен
     * `ENCBTN`. Один флаг на всё приложение — оба пути (`performClick()`/реальный тач)
     * всегда выполняются синхронно и никогда не пересекаются.
     */
    private var encoderTabHighlight = false
    /** Общий вызов для `onHighlight` узлов меню 2 уровня — см. [encoderTabHighlight]. */
    private fun simulateEncoderTabHighlight(button: Button) {
        encoderTabHighlight = true
        button.performClick()
        encoderTabHighlight = false
    }
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
     * Пускает строки в тот же handleBleCommand(), что и реальный ESP32 по BLE
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
     * Системный диалог "Разрешить приложению включить Bluetooth?"
     */
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        setupBluetooth()
    }
    /**
     * Импорт бандла карты
     */
    private val openMapBundleTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@registerForActivityResult
        val resultView = bindingMain.incLayoutSettingsGlobal.tvMapBundleImportResult
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mapBundleRepository.importFromTree(treeUri)
            withContext(Dispatchers.Main) {
                refreshMapBundleStatus()
                resultView.visibility = View.VISIBLE
                resultView.text = result.fold(
                    onSuccess = { getString(R.string.map_bundle_import_success) },
                    onFailure = { it.message ?: getString(R.string.map_bundle_import_error_unknown) }
                )
            }
        }
    }
    /**
     * Импорт офлайн-модели Vosk
     */
    private val openVoiceModelZipLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { zipUri ->
        if (zipUri == null) return@registerForActivityResult
        val resultView = bindingMain.incLayoutSettingsGlobal.tvVoiceModelImportResult
        lifecycleScope.launch(Dispatchers.IO) {
            val result = voiceModelRepository.importFromZip(zipUri)
            withContext(Dispatchers.Main) {
                refreshVoiceModelStatus()
                resultView.visibility = View.VISIBLE
                resultView.text = result.fold(
                    onSuccess = { getString(R.string.voice_model_import_success) },
                    onFailure = { it.message ?: getString(R.string.voice_model_import_error_unknown) }
                )
                if (result.isSuccess) startWakeWordIfPermitted()
            }
        }
    }
    private fun onRequiredPermissionsGranted() {
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
     * DISCLAIMER / TUTORIAL
     **********************************************************************************************************/
    private var showTutorialBool = true
    // Индекс текущей страницы тьюториала внутри tutorialPageStringRes (roadmap, этап 25).
    // -1 значит "сейчас показан Welcome/дисклеймер, страницы контента ещё не открыты".
    private var tutorialPageIndex = -1
    private val tutorialPageStringRes = listOf(
        R.string.tutorial_page_whatsnew,
        R.string.tutorial_page_modes,
        R.string.tutorial_page_stats,
        R.string.tutorial_page_items,
        R.string.tutorial_page_data,
        R.string.tutorial_page_radio,
        R.string.tutorial_page_voice,
    )

    /***********************************************************************************************************
     * FILTER MODIFICATION
     **********************************************************************************************************/
    private lateinit var filterFrame: FrameLayout
    private lateinit var filteringMenu: String
    private var selectedFilterSTATSPerks = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterDATAMisc = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var filterSelectionSnapshot: MutableSet<String> = mutableSetOf()

    /***********************************************************************************************************
     * LongButtonPresses - EasterEgg + PlayerDamage
     **********************************************************************************************************/
    private var statsCndPopupIsHolding = false
    private var menuSwipeEnabled = true
    private var delayIterationCount = 0
    private var delayModify = 500L

    /***********************************************************************************************************
     * STATUS
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
    private var isSPECIALValueIncreasing = false
    private var isSPECIALValueDecreasing = false

    /** Метаданные 7 характеристик SPECIAL — тот же приём, что у skillsMeta (см. выше),
     * единственный источник, из которого строится SidebarMenuAdapter. */
    private data class SpecialMeta(
        val key: String,
        val labelRes: Int,
        val prefKey: String,
        val imageRes: Int,
        val descriptionRes: Int,
    )
    private val specialMeta = listOf(
        SpecialMeta("STRENGTH", R.string.stats_special_strength, "SPECIAL_S", R.drawable.special_strength, R.string.special_strength_description),
        SpecialMeta("PERCEPTION", R.string.special_perception, "SPECIAL_P", R.drawable.special_perception, R.string.special_perception_description),
        SpecialMeta("ENDURANCE", R.string.special_endurance, "SPECIAL_E", R.drawable.special_endurance, R.string.special_endurance_description),
        SpecialMeta("CHARISMA", R.string.special_charisma, "SPECIAL_C", R.drawable.special_charisma, R.string.special_charisma_description),
        SpecialMeta("INTELLIGENCE", R.string.special_intelligence, "SPECIAL_I", R.drawable.special_intelligence, R.string.special_intelligence_description),
        SpecialMeta("AGILITY", R.string.special_agility, "SPECIAL_A", R.drawable.special_agility, R.string.special_agility_description),
        SpecialMeta("LUCK", R.string.special_luck, "SPECIAL_L", R.drawable.special_luck, R.string.special_luck_description),
    )
    private lateinit var specialAdapter: SidebarMenuAdapter<String>

    private var selectedSKILL = "BARTER"
    private var isSKILLValueIncreasing = false
    private var isSKILLValueDecreasing = false

    /** Метаданные 13 навыков Skills
     */
    private data class SkillMeta(
        val key: String,
        val labelRes: Int,
        val prefKey: String,
        val imageRes: Int,
        val descriptionRes: Int,
    )
    private val skillsMeta = listOf(
        SkillMeta("BARTER", R.string.skill_barter, "SKILLS_1", R.drawable.skills_barter, R.string.skill_barter_description),
        SkillMeta("BIGGUNS", R.string.skill_big_guns, "SKILLS_2", R.drawable.skills_big_guns, R.string.skill_big_guns_description),
        SkillMeta("ENERGYWEAPONS", R.string.skill_energy_weapons, "SKILLS_3", R.drawable.skills_energy_weapons, R.string.skill_energy_weapons_description),
        SkillMeta("EXPLOSIVES", R.string.skill_explosives, "SKILLS_4", R.drawable.skills_explosives, R.string.skill_explosives_description),
        SkillMeta("LOCKPICK", R.string.skill_lockpick, "SKILLS_5", R.drawable.skills_lockpick, R.string.skill_lockpick_description),
        SkillMeta("MEDICINE", R.string.skill_medicine, "SKILLS_6", R.drawable.skills_medicine, R.string.skill_medicine_description),
        SkillMeta("MELEEWEAPONS", R.string.skill_melee_weapons, "SKILLS_7", R.drawable.skills_melee_weapons, R.string.skill_melee_weapons_description),
        SkillMeta("REPAIR", R.string.skill_repair, "SKILLS_8", R.drawable.skills_repair, R.string.skill_repair_description),
        SkillMeta("SCIENCE", R.string.skill_science, "SKILLS_9", R.drawable.skills_science, R.string.skill_science_description),
        SkillMeta("SMALLGUNS", R.string.skill_small_guns, "SKILLS_10", R.drawable.skills_small_guns, R.string.skill_small_guns_description),
        SkillMeta("SNEAK", R.string.skill_sneak, "SKILLS_11", R.drawable.skills_sneak, R.string.skill_sneak_description),
        SkillMeta("SPEECH", R.string.skill_speech, "SKILLS_12", R.drawable.skills_speech, R.string.skill_speech_description),
        SkillMeta("UNARMED", R.string.skill_unarmed, "SKILLS_13", R.drawable.skills_unarmed, R.string.skill_unarmed_description),
    )
    private lateinit var skillsAdapter: SidebarMenuAdapter<String>

    // Perks
    private lateinit var perksAdapter: SidebarMenuAdapter<Map<String, String>>
    private var perksRealItemCount = 0

    private lateinit var selectedSubMenu: Button

    private val handler = Handler(Looper.getMainLooper())
    // 300мс-тик (часы/будильник/таймер/секундомер) — заведён в onCreate(), ссылка нужна
    // здесь, чтобы onDestroy() мог его остановить.
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
     * Строка 2 новой шапки
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION_WAKE_WORD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                wakeWordDetector?.start()
            }
        }
        if (requestCode == REQUEST_CODE_PERMISSION_JOURNAL_DICTATION) {
            val popupVisible = bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.root.visibility == View.VISIBLE
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (popupVisible) startJournalDictation()
            } else {
                playErrorAudio()
            }
        }
        if (requestCode == REQUEST_CODE_PERMISSION_BLUETOOTH_SETTINGS_SCAN) {
            val bluetoothPanelVisible = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.root.visibility == View.VISIBLE
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED } && bluetoothPanelVisible) {
                startBluetoothPairingScan()
            }
        }
    }
    /***********************************************************************************************************
     * ГОЛОСОВЫЕ КОМАНДЫ / WAKE-WORD (roadmap, этап 19/21)
     **********************************************************************************************************/
    private fun initWakeWordDetector() {
        wakeWordDetector = com.malto4.pipdroid.voice.WakeWordDetector(this) {
            runOnUiThread { onWakeWordTriggered() }
        }
        startWakeWordIfPermitted()
    }
    private fun startWakeWordIfPermitted() {
        if (!voiceModelRepository.hasModel()) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            wakeWordDetector?.start()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION_WAKE_WORD)
        }
    }
    private var awaitingVoiceCommand = false
    private val voiceCommandTimeoutHandler = Handler(Looper.getMainLooper())
    private val voiceCommandTimeoutRunnable = Runnable {
        if (!awaitingVoiceCommand) return@Runnable
        // Дожимаем то, что накопилось без естественной паузы в речи, прежде чем сдаваться —
        // короткая команда может уложиться в таймаут раньше, чем Vosk сам решит, что фраза
        // закончилась.
        val flushed = voiceDictationService.flushCommandFinalText()
        Log.d("VoiceCommand", "final (timeout flush): \"$flushed\"")
        handleVoiceCommandText(flushed)
        if (awaitingVoiceCommand) cancelVoiceCommandListening(matched = false)
    }
    private val VOICE_COMMAND_TIMEOUT_MS = 6000L
    /** Срабатывает на WakeWordCapture-потоке через runOnUiThread (initWakeWordDetector).
     * Уступает микрофон диктовке Журнала, если та уже идёт (VoiceDictationService — общий
     * на оба сценария, отдавать его на середине записи в Журнал нельзя). */
    private fun onWakeWordTriggered() {
        if (awaitingVoiceCommand || journalDictationState != JournalDictationState.IDLE) return
        if (!voiceModelRepository.hasModel()) return
        awaitingVoiceCommand = true
        Toast.makeText(this, getString(R.string.voice_command_listening), Toast.LENGTH_SHORT).show()
        voiceCommandTimeoutHandler.postDelayed(voiceCommandTimeoutRunnable, VOICE_COMMAND_TIMEOUT_MS)
        if (voiceDictationService.isModelLoaded()) {
            beginVoiceCommandListening()
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching { voiceDictationService.loadModel(voiceModelRepository.modelDir().absolutePath) }
                withContext(Dispatchers.Main) {
                    if (!awaitingVoiceCommand) return@withContext
                    if (result.isFailure) {
                        cancelVoiceCommandListening(matched = false)
                        return@withContext
                    }
                    beginVoiceCommandListening()
                }
            }
        }
    }
    private fun beginVoiceCommandListening() {
        voiceDictationService.startCommandRecognition()
        // Дедупликация partial-лога по значению
        var lastLoggedPartial = ""
        wakeWordDetector?.armCommandSink { chunk, len ->
            val chunkResult = voiceDictationService.feedCommandAudio(chunk, len)
            if (chunkResult.isFinal) {
                Log.d("VoiceCommand", "final: \"${chunkResult.text}\"")
                if (chunkResult.text.isNotBlank()) runOnUiThread { handleVoiceCommandText(chunkResult.text) }
            } else if (chunkResult.text.isNotBlank() && chunkResult.text != lastLoggedPartial) {
                lastLoggedPartial = chunkResult.text
                Log.d("VoiceCommand", "partial: \"${chunkResult.text}\"")
            }
        }
    }
    /** Полный словарь голосовых команд
     */
    private fun handleVoiceCommandText(text: String) {
        if (!awaitingVoiceCommand) return
        val normalized = text.lowercase().replace('ё', 'е')

        // Ранение (лёгкое/тяжёлое), опционально с частью тела — общий таймер плюс,
        // если названа часть тела, ещё и CRIPPLED-отметка этой части (независимая
        // механика, см. setCrippled*() выше).
        val woundSeverity = when {
            !normalized.contains("ранен") -> null
            normalized.contains("тяжел") -> WoundSeverity.HEAVY
            normalized.contains("легк") -> WoundSeverity.LIGHT
            else -> null
        }
        if (woundSeverity != null) {
            if (woundPhase != WoundPhase.NONE) {
                playErrorAudio()
            } else {
                playTickAudio()
                startWoundTimer(WoundPhase.BLEED, woundSeverity, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
                matchBodyPartSetter(normalized)?.invoke(true)
            }
            finishVoiceCommand(text)
            return
        }
        // Оглушение/контузия — та же общая система, третья фаза ранения.
        if (normalized.contains("оглуш") || normalized.contains("контуз")) {
            if (woundPhase != WoundPhase.NONE) {
                playErrorAudio()
            } else {
                playTickAudio()
                startWoundTimer(WoundPhase.STUNNED, null, STUN_DURATION_SECONDS)
            }
            finishVoiceCommand(text)
            return
        }
        // Возвращение в строй — только пока персонаж мёртв (тот же гвард, что у тач-жеста
        // по фигуре, см. setupFigureTouchTarget()).
        if (normalized.contains("очнул") || normalized.contains("ожил")) {
            if (woundPhase == WoundPhase.DEAD) {
                playTickAudio()
                reviveCharacter()
            } else {
                playErrorAudio()
            }
            finishVoiceCommand(text)
            return
        }
        if (normalized.contains("таймер") && (normalized.contains("стоп") || normalized.contains("останов"))) {
            playButtonAudio()
            resetTimer()
            finishVoiceCommand(text)
            return
        }
        if (normalized.contains("пауз") || normalized.contains("продолж") || normalized.contains("возобнов")) {
            val allowed = woundPhase == WoundPhase.NONE || woundPhase == WoundPhase.DEAD
            if (!allowed || timerState == TimerState.IDLE) {
                playErrorAudio()
            } else {
                playButtonAudio()
                pauseResumeTimer()
            }
            finishVoiceCommand(text)
            return
        }
        if (normalized.contains("таймер") && normalized.contains("минут")) {
            val minutes = parseRussianNumber(normalized)
            if (minutes == null || minutes <= 0 || timerState != TimerState.IDLE) {
                playErrorAudio()
            } else {
                playButtonAudio()
                startPlainTimer(minutes * 60)
            }
            finishVoiceCommand(text)
            return
        }
        if (normalized.contains("маршрут")) {
            if (normalized.contains("отмен")) {
                playButtonAudio()
                cancelActiveRoute()
            } else {
                val queryTokens = normalized.substringAfter("маршрут").trim()
                    .split(Regex("\\s+"))
                    .filterNot { it.isBlank() || it in ROUTE_FILLER_WORDS }
                val candidates = markerRepository.loadAll().filter { matchesMarkerQuery(queryTokens, it.name) }
                if (candidates.size != 1) {
                    playErrorAudio()
                } else {
                    playTickAudio()
                    val destination = candidates[0]
                    pendingMapReadyAction = { routeTo(destination.lat, destination.lon) }
                    navigateToItemsSection("MAP")
                }
            }
            finishVoiceCommand(text)
            return
        }
        // Журнал — новая запись
        if (normalized.contains("нов") && normalized.contains("запис")) {
            playTickAudio()
            navigateToItemsSection("JOURNAL")
            showJournalEntryEditorForNew()
            finishVoiceCommand(text)
            return
        }
        // Навигация по разделам/экранам
        if (normalized.contains("статус")) {
            menuChangeBLE("STATS"); menuNavigator.resetToRoot(statsMenuRoot())
            finishVoiceCommand(text); return
        }
        if (normalized.contains("модул")) {
            menuChangeBLE("ITEMS"); menuNavigator.resetToRoot(itemsMenuRoot())
            finishVoiceCommand(text); return
        }
        if (normalized.contains("данн")) {
            menuChangeBLE("DATA"); menuNavigator.resetToRoot(dataMenuRoot())
            finishVoiceCommand(text); return
        }
        if (normalized.contains("журнал")) {
            navigateToItemsSection("JOURNAL")
            finishVoiceCommand(text); return
        }
        if (normalized.contains("карт")) {
            navigateToItemsSection("MAP")
            finishVoiceCommand(text); return
        }
        if (normalized.contains("гейгер")) {
            navigateToItemsSection("GEIGER")
            finishVoiceCommand(text); return
        }
        if (normalized.contains("секундомер")) {
            navigateToItemsSection("CLOCK")
            clockAdapter.selectPosition(clockMeta.indexOfFirst { it.key == "STOPWATCH" })
            finishVoiceCommand(text); return
        }
        if (normalized.contains("час")) {
            navigateToItemsSection("CLOCK")
            clockAdapter.selectPosition(clockMeta.indexOfFirst { it.key == "TIME" })
            finishVoiceCommand(text); return
        }
    }
    /** Часть тела для команды "лёгкое/тяжёлое ранение в <часть>"
     */
    private fun matchBodyPartSetter(normalized: String): ((Boolean) -> Unit)? = when {
        normalized.contains("голов") -> ::setCrippledHead
        normalized.contains("торс") || normalized.contains("груд") || normalized.contains("тулов") -> ::setCrippledTorso
        normalized.contains("рук") && normalized.contains("лев") -> ::setCrippledRightArm
        normalized.contains("рук") && normalized.contains("прав") -> ::setCrippledLeftArm
        normalized.contains("ног") && normalized.contains("лев") -> ::setCrippledRightLeg
        normalized.contains("ног") && normalized.contains("прав") -> ::setCrippledLeftLeg
        else -> null
    }
    /** Разбор произвольного числа минут из речи
     */
    private fun parseRussianNumber(text: String): Int? {
        Regex("\\d+").find(text)?.value?.toIntOrNull()?.let { return it }
        val units = mapOf(
            "один" to 1, "одна" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4,
            "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
        )
        val teens = mapOf(
            "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13,
            "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17,
            "восемнадцать" to 18, "девятнадцать" to 19,
        )
        val tens = mapOf("двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50)
        val tokens = text.split(Regex("\\s+"))
        for (i in tokens.indices) {
            tens[tokens[i]]?.let { tensValue -> return tensValue + (units[tokens.getOrNull(i + 1)] ?: 0) }
            teens[tokens[i]]?.let { return it }
            units[tokens[i]]?.let { return it }
        }
        return null
    }
    /** Переключение на один из top-level узлов ITEMS/МОДУЛИ по символическому id узла
     */
    private fun navigateToItemsSection(nodeId: String) {
        val roots = itemsMenuRoot()
        val index = roots.indexOfFirst { it.id == nodeId }
        if (index < 0) return
        menuChangeBLE("ITEMS")
        menuNavigator.resetToRootAtIndex(roots, index)
    }
    private val ROUTE_FILLER_WORDS = setOf("до", "к", "на", "в")
    /** Сопоставление имени отметки с запросом голосовой команды
     */
    private fun matchesMarkerQuery(queryTokens: List<String>, markerName: String): Boolean {
        if (queryTokens.isEmpty()) return false
        val markerStems = markerName.lowercase().replace('ё', 'е')
            .split(Regex("\\s+")).filter { it.isNotBlank() }.map { russianStem(it) }
        if (markerStems.isEmpty()) return false
        return queryTokens.map { russianStem(it) }.all { queryStem ->
            markerStems.any { markerStem -> markerStem.contains(queryStem) || queryStem.contains(markerStem) }
        }
    }
    /** Лёгкий стеммер
     */
    private fun russianStem(word: String): String {
        val suffixes = listOf(
            "иями", "иях", "ями", "ами", "его", "ого", "ему", "ому", "ыми", "ими",
            "ия", "ие", "ых", "их", "ев", "ов", "ей", "ой", "ый", "ая", "яя", "ую", "юю",
            "а", "я", "о", "е", "и", "ы", "у", "ю", "й", "ь",
        )
        for (suffix in suffixes) {
            if (word.length - suffix.length >= 3 && word.endsWith(suffix)) return word.dropLast(suffix.length)
        }
        return word
    }
    /** Общий хвост распознанной (не обязательно успешно исполненной — см. playErrorAudio()
     * по месту вызова) команды — тост + остановка прослушивания. */
    private fun finishVoiceCommand(recognizedText: String) {
        Toast.makeText(this, getString(R.string.voice_command_recognized, recognizedText), Toast.LENGTH_SHORT).show()
        cancelVoiceCommandListening(matched = true)
    }
    private fun cancelVoiceCommandListening(matched: Boolean) {
        voiceCommandTimeoutHandler.removeCallbacks(voiceCommandTimeoutRunnable)
        wakeWordDetector?.disarmCommandSink()
        voiceDictationService.stopCommandRecognition()
        awaitingVoiceCommand = false
        if (!matched) {
            Toast.makeText(this, getString(R.string.voice_command_not_recognized), Toast.LENGTH_SHORT).show()
        }
    }

    /***********************************************************************************************************
     * BLUETOOTH
     **********************************************************************************************************/
    private fun requiredPermissionsForCurrentMode(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        // Запрашивается вместе с FINE (roadmap, этап 28, найденное расхождение) — обе
        // объявлены в манифесте безусловно, но раньше мастер просил только FINE.
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        // Голосовые команды/диктовка (Journal) — общая фича, не завязанная на физический
        // режим, поэтому запрашивается всегда, не только для не-Phone (roadmap, этап 28,
        // найденное расхождение) — раньше мастер вообще её не просил, RECORD_AUDIO
        // запрашивался только по месту первого реального использования функции.
        add(Manifest.permission.RECORD_AUDIO)
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
    /** Не обязательное разрешение, а рекомендация системы
     */
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
     * Экран выбора режима
     */
    private var modeSelectHighlighted = PipBoyMode.PHONE
    private val modeSelectList = listOf(PipBoyMode.PHONE, PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000)
    private lateinit var modeSelectAdapter: SidebarMenuAdapter<PipBoyMode>

    /**
     * Акцентный цвет текущей темы оформления
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
    private fun equalizeButtonWidths(vararg buttons: Button) {
        val widest = buttons.maxOf {
            it.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            it.measuredWidth
        }
        buttons.forEach { it.layoutParams = it.layoutParams.apply { width = widest } }
    }
    private fun showModeDescription(mode: PipBoyMode) {
        val ms = bindingMain.incLayoutTabModeSelect
        modeSelectHighlighted = mode
        ms.tvModeSelectDescription.text = when (mode) {
            PipBoyMode.PHONE -> getString(R.string.mode_description_phone)
            PipBoyMode.PIPBOY_2000 -> getString(R.string.mode_description_pipboy_2000)
            PipBoyMode.PIPBOY_3000 -> getString(R.string.mode_description_pipboy_3000)
        }
        val modeIndex = modeSelectList.indexOf(mode)
        if (modeIndex >= 0) modeSelectAdapter.setSelectedPositionSilently(modeIndex)
        // PipBoy 3000 пока нельзя выбрать — можно только прочитать описание. Кнопка
        // остаётся кликабельной, чтобы поймать тап и проиграть звук ошибки.
        if (mode != PipBoyMode.PIPBOY_3000) {
            setWizardButtonState(ms.btnModeSelectConfirm, selected = false)
        } else {
            setWizardButtonDisabled(ms.btnModeSelectConfirm)
        }
    }
    private fun pipBoyModeDisplayName(mode: PipBoyMode): String = when (mode) {
        PipBoyMode.PHONE -> getString(R.string.mode_phone)
        PipBoyMode.PIPBOY_2000 -> getString(R.string.mode_pipboy_2000)
        PipBoyMode.PIPBOY_3000 -> getString(R.string.mode_pipboy_3000)
    }
    private fun refreshModeSettingsLabel() {
        val label = "${getString(R.string.settings_4_name)} ${pipBoyModeDisplayName(pipBoyMode)}"
        bindingMain.incLayoutSettingsGlobal.tvSettings4.text = label
    }
    private fun openModeSelectScreen() {
        showModeDescription(pipBoyMode)
        bindingMain.incLayoutTabModeSelect.root.visibility = View.VISIBLE
    }
    private fun setupModeSelectScreen() {
        val ms = bindingMain.incLayoutTabModeSelect

        ms.tvModeSelectDescription.setTextColor(currentWizardAccentColor())

        ms.recyclerModeSelect.layoutManager = LinearLayoutManager(this)
        modeSelectAdapter = SidebarMenuAdapter(
            items = modeSelectList.map { mode -> SidebarMenuItem(payload = mode, label = pipBoyModeDisplayName(mode)) },
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { _, item -> showModeDescription(item.payload) },
        )
        ms.recyclerModeSelect.adapter = modeSelectAdapter

        showModeDescription(PipBoyMode.PHONE)
        ms.btnModeSelectConfirm.setOnClickListener {
            if (modeSelectHighlighted == PipBoyMode.PIPBOY_3000) {
                playErrorAudio()
                return@setOnClickListener
            }
            playButtonAudio()
            selectPipBoyMode(modeSelectHighlighted)
        }
    }
    private fun applyModeGating() {
        val header = bindingMain.incLayoutHeaderToplevel
        val visibility = if (pipBoyMode == PipBoyMode.PHONE) View.GONE else View.VISIBLE
        header.btnHeaderRadio.visibility = visibility
        header.spaceHeaderRadioGap.visibility = visibility
    }
    private fun selectPipBoyMode(mode: PipBoyMode) {
        stopAmbientBackgroundSound()
        pipBoyMode = mode
        sharedPreferences.edit().putString(pipBoyMode_SPKey, mode.name).apply()
        refreshModeSettingsLabel()
        applyModeGating()
        refreshSidebarBackItems()
        bindingMain.incLayoutTabModeSelect.root.visibility = View.GONE

        // НЕ enableDisableBottomButtons(true, ...)/enableDisableTopSwipe(true) здесь (roadmap,
        // этап 28, найденный баг) — мастер PipBoy 2000/3000 показывается сразу следом (см.
        // ветку when(mode) ниже), а не главный экран: раздизейбленные тут кнопки шапки/футера
        // тут же становились кликабельны СКВОЗЬ мастер (тот сам не clickable/не перехватывает
        // тач фоном) на любой тап мимо его собственных элементов. Кнопки/свайп остаются
        // задизейбленными (как и были, пока было открыто Settings) до реального завершения
        // всего потока — см. finishPhoneModeSetup()/applyPowerState(true)/
        // skipWizardToMainScreenDebug(), единственные места, где мастер закрывается взаправду.
        if (bindingMain.incLayoutSettingsGlobal.root.visibility == View.VISIBLE) {
            bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
        }

        bindingMain.constraintlayoutTutorial.visibility = View.GONE
        bindingMain.constraintlayoutMain.visibility = View.VISIBLE

        when (mode) {
            PipBoyMode.PHONE -> {

                bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.VISIBLE
                showWizardStep(PipBoyWizardStep.PERMISSIONS)
            }
            PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000 -> {
                setPowerOffInstant()
                bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.VISIBLE
                showWizardStep(PipBoyWizardStep.HARDWARE_INSTRUCTIONS)
            }
        }
    }
    private fun finishPhoneModeSetup() {
        bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
        // Мастер реально закрывается — вернуть кнопки шапки/футера и свайп, задизейбленные
        // на весь мастер начиная с openModeSelectScreen() (roadmap, этап 28, см. комментарий
        // в selectPipBoyMode()).
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        resetToFullScreen()
        bindingMain.viewPowerOff.animate().cancel()
        bindingMain.viewPowerOff.visibility = View.GONE
        updateScreenGlareVisibility()
        stopBleService()
        menuChangeBLE("STATS")
        menuNavigator.resetToRoot(statsMenuRoot())
        // Стартовое положение курсора энкодера при включении (roadmap, этап 27 —
        // доработка энкодер-эргономики) — не сам узел STATUS (строка 2), а его первый
        // дочерний пункт бокового меню (Light Wound), тот же приём, что у handleBleCommand()
        // ("STATS" с активным ранением ниже) — Status всегда индекс 0 в statsMenuRoot().
        menuNavigator.activateSelected()
        cancelBootSequence()
        startContinuousGlitch()
        startAmbientBackgroundSound()
    }

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
        applyModeGating()
        refreshSidebarBackItems()
        when (restoredMode) {
            PipBoyMode.PHONE -> finishPhoneModeSetup()
            PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000 -> {
                bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
                loadViewState()
                setPowerOffInstant()
                checkPermissions()
            }
        }

        val restoredMenu = savedInstanceState.getString(KEY_CUR_MENU, "STATS")
            ?.takeIf { it in setOf("STATS", "ITEMS", "DATA", "RADIO") } ?: "STATS"
        val restoredRootCursor = savedInstanceState.getInt(KEY_ROOT_CURSOR, 0)
        menuChangeBLE(restoredMenu)
        menuNavigator.resetToRootAtIndex(menuRootNodesFor(restoredMenu), restoredRootCursor)

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
        statusAdapter.setSelectedPositionSilently(savedInstanceState.getInt(KEY_STATUS_CURSOR_ROW))
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
     * Мастер настройки PipBoy 2000/3000
     */
    private enum class PipBoyWizardStep { HARDWARE_INSTRUCTIONS, DISPLAY_AREA, PERMISSIONS, PAIRING, POWER_HINT }

    private fun showWizardStep(step: PipBoyWizardStep, allowAutoAdvance: Boolean = true) {
        val w = bindingMain.incLayoutPipboy2000Wizard
        w.layoutWizardChromeFrame.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.GONE else View.VISIBLE
        w.layoutWizardHardware.visibility = if (step == PipBoyWizardStep.HARDWARE_INSTRUCTIONS) View.VISIBLE else View.GONE
        w.layoutWizardDisplayArea.visibility = if (step == PipBoyWizardStep.DISPLAY_AREA) View.VISIBLE else View.GONE
        w.layoutWizardPermissions.visibility = if (step == PipBoyWizardStep.PERMISSIONS) View.VISIBLE else View.GONE
        w.layoutWizardPairing.visibility = if (step == PipBoyWizardStep.PAIRING) View.VISIBLE else View.GONE
        w.layoutWizardPowerHint.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.VISIBLE else View.GONE
        w.tvWizardPowerHint.visibility = View.VISIBLE
        w.btnWizardHideHint.visibility = View.VISIBLE
        updateScreenGlareVisibility()

        // Скан идёт только пока реально показан шаг PAIRING — начинаем/останавливаем
        // строго по факту показа шага, не полагаясь на то, что игрок сам нажмёт кнопку.
        if (step == PipBoyWizardStep.PAIRING) {
            startPairingScan(w.layoutWizardPairingDevices, w.tvWizardPairingStatus) { address -> selectPairingDevice(address) }
        } else {
            stopPairingScan()
        }

        // Регулировка рабочей области жестом активна только пока реально показан этот шаг.
        isResizing = (step == PipBoyWizardStep.DISPLAY_AREA)
        if (step == PipBoyWizardStep.DISPLAY_AREA) {
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
            wizardMinContentWidthPx = 0
            wizardMinContentHeightPx = 0
            resetToFullScreen()
        } else {
            wizardMinContentWidthPx = 0
            wizardMinContentHeightPx = 0
            loadViewState()
        }

        if (step == PipBoyWizardStep.PERMISSIONS && allowAutoAdvance && hasAllRequiredPermissions()) {
            // Уже выданы раньше — не задерживаем игрока на этом экране. allowAutoAdvance —
            // roadmap, этап 28, найденный баг: без него Back с PAIRING на PERMISSIONS сразу
            // же отскакивал обратно на PAIRING этой же веткой (разрешения уже выданы), кнопка
            // Back выглядела нерабочей. false — только у явного возврата назад (см.
            // btnWizardPairingBack ниже), вперёд (первый вход на шаг) всегда true.
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
    private var pairingScanCallback: ScanCallback? = null
    private val pairingFoundAddresses = mutableSetOf<String>()
    private val pairingScanTimeoutRunnable = Runnable { stopPairingScan() }
    private val pairingScanDurationMs = 15000L
    private var pairingDevicesContainer: LinearLayout? = null
    private var pairingStatusView: TextView? = null
    private var pairingOnSelect: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    private fun startPairingScan(devicesContainer: LinearLayout, statusView: TextView, onSelect: (String) -> Unit) {
        stopPairingScan()
        pairingDevicesContainer = devicesContainer
        pairingStatusView = statusView
        pairingOnSelect = onSelect
        devicesContainer.removeAllViews()
        pairingFoundAddresses.clear()
        statusView.text = getString(R.string.wizard_pairing_scanning)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            statusView.text = getString(R.string.wizard_pairing_bluetooth_off)
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            statusView.text = getString(R.string.wizard_pairing_scan_failed)
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
                pairingStatusView?.text = getString(R.string.wizard_pairing_scan_failed)
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
            pairingStatusView?.text = getString(R.string.wizard_pairing_none_found)
        }
    }

    private fun addPairingDevice(address: String, name: String?) {
        if (!pairingFoundAddresses.add(address)) return
        val container = pairingDevicesContainer ?: return
        val statusView = pairingStatusView ?: return
        statusView.text = getString(R.string.wizard_pairing_found, pairingFoundAddresses.size)
        val button = Button(this, null, 0, R.style.PipWizardButtonStyle).apply {
            text = name ?: address
            backgroundTintList = ColorStateList.valueOf(currentWizardAccentColor())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            setOnClickListener {
                playButtonAudio()
                pairingOnSelect?.invoke(address)
            }
        }
        container.addView(button)
    }

    private fun applyPairedDevice(address: String) {
        stopPairingScan()
        sharedPreferences.edit().putString(bluetoothMAC_SPKey, address).apply()
        val service = bleService
        if (service != null) {
            service.reconnectWithCurrentSettings()
        } else {
            startAndBindBleService()
        }
    }

    private fun selectPairingDevice(address: String) {
        applyPairedDevice(address)
        showWizardStep(PipBoyWizardStep.POWER_HINT)
    }

    private fun selectBluetoothSettingsPairingDevice(address: String) {
        applyPairedDevice(address)
        refreshBluetoothCurrentDevice()
    }
    private fun startBluetoothPairingScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_CODE_PERMISSION_BLUETOOTH_SETTINGS_SCAN
            )
            return
        }
        val bt = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth
        startPairingScan(bt.layoutBluetoothPairingDevices, bt.tvBluetoothPairingStatus) { address ->
            selectBluetoothSettingsPairingDevice(address)
        }
    }
    /** Показывает сохранённый сейчас MAC (или "не выбрано", если пейринга ещё не было). */
    private fun refreshBluetoothCurrentDevice() {
        val value = sharedPreferences.getString(bluetoothMAC_SPKey, null)
        bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.tvBluetoothCurrentMac.text =
            value ?: getString(R.string.bluetooth_mac_not_set)
    }
    private fun setupPipBoy2000Wizard() {
        val w = bindingMain.incLayoutPipboy2000Wizard

        val wizardAccent = currentWizardAccentColor()
        listOf(
            w.btnWizardHardwareBack,
            w.btnWizardHardwareSkipDebug,
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

        // Заголовки и основной текст шагов мастера — тем же акцентом
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

        // Шаг 3: одна ширина у [Готово]/[Сбросить]/[Отмена]
        equalizeButtonWidths(w.btnWizardDone, w.btnWizardReset, w.btnWizardCancel)

        // Шаг 2: Hardware Instructions
        w.btnWizardHardwareBack.setOnClickListener {
            playButtonAudio()
            w.root.visibility = View.GONE
            bindingMain.incLayoutTabModeSelect.root.visibility = View.VISIBLE
        }
        w.btnWizardHardwareNext.setOnClickListener {
            playButtonAudio()
            showWizardStep(PipBoyWizardStep.DISPLAY_AREA)
        }
        // Обход всего мастера + анимации загрузки в debug-сборках (roadmap, этап 27 — "много
        // времени уходит на протапывание и просмотр мультика") — тот же приём, что и у
        // btnWizardPairingSkipDebug ниже, но с шага 1 и до полностью готового главного
        // экрана, а не просто до следующего шага мастера.
        w.btnWizardHardwareSkipDebug.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        w.btnWizardHardwareSkipDebug.setOnClickListener {
            playButtonAudio()
            skipWizardToMainScreenDebug()
        }

        // Шаг 3: Display Area
        w.btnWizardDone.setOnClickListener {
            playButtonAudio()
            showWizardStep(PipBoyWizardStep.PERMISSIONS)
        }
        w.btnWizardReset.setOnClickListener {
            playButtonAudio()
            resetToFullScreen()
        }
        w.btnWizardCancel.setOnClickListener {
            playButtonAudio()
            showWizardStep(PipBoyWizardStep.HARDWARE_INSTRUCTIONS)
        }

        // Шаг 4: Permissions
        w.btnWizardPermissionsBack.setOnClickListener {
            playButtonAudio()
            if (pipBoyMode == PipBoyMode.PHONE) {
                w.root.visibility = View.GONE
                bindingMain.incLayoutTabModeSelect.root.visibility = View.VISIBLE
            } else {
                showWizardStep(PipBoyWizardStep.DISPLAY_AREA)
            }
        }
        w.btnWizardGrantPermissions.setOnClickListener {
            playButtonAudio()
            checkPermissions()
        }

        // Шаг 5: Pairing
        w.btnWizardPairingBack.setOnClickListener {
            playButtonAudio()
            stopPairingScan()
            // allowAutoAdvance = false (roadmap, этап 28, найденный баг) — разрешения на
            // этот момент уже выданы (иначе сюда, на PAIRING, было бы не попасть), обычный
            // showWizardStep(PERMISSIONS) тут же отскакивал бы обратно на PAIRING.
            showWizardStep(PipBoyWizardStep.PERMISSIONS, allowAutoAdvance = false)
        }
        w.btnWizardPairingRescan.setOnClickListener {
            playButtonAudio()
            startPairingScan(w.layoutWizardPairingDevices, w.tvWizardPairingStatus) { address -> selectPairingDevice(address) }
        }
        // Обход пейринга в debug-сборках
        w.btnWizardPairingSkipDebug.visibility =
            if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        w.btnWizardPairingSkipDebug.setOnClickListener {
            playButtonAudio()
            stopPairingScan()
            showWizardStep(PipBoyWizardStep.POWER_HINT)
        }

        // Шаг 6: подсказка про POWER
        w.btnWizardHideHint.setOnClickListener {
            playButtonAudio()
            w.tvWizardPowerHint.visibility = View.GONE
            w.btnWizardHideHint.visibility = View.GONE
        }
    }
    private fun skipWizardToMainScreenDebug() {
        stopPairingScan()
        cancelBootSequence()
        bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
        // См. finishPhoneModeSetup() — тот же откат дизейбла кнопок/свайпа, этот путь тоже
        // закрывает мастер по-настоящему (debug-обход).
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        bindingMain.viewPowerOff.animate().cancel()
        bindingMain.viewPowerOff.visibility = View.GONE
        updateScreenGlareVisibility()
        loadViewState()
        if (row2Views.isEmpty()) {
            menuChangeBLE(curMenu)
            menuNavigator.resetToRoot(menuRootNodesFor(curMenu))
            // Стартовое положение курсора энкодера (roadmap, этап 27 — доработка энкодер-
            // эргономики) — первый дочерний узел бокового меню, не сам узел строки 2, тот
            // же приём, что у finishPhoneModeSetup()/finishBootSequence().
            menuNavigator.activateSelected()
        }
        startContinuousGlitch()
        startAmbientBackgroundSound()
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
                newWidth = max(newWidth, wizardMinContentWidthPx)
                newHeight = max(newHeight, wizardMinContentHeightPx)

                val displayMetrics = resources.displayMetrics
                val statusBarHeight = getStatusBarHeight()
                val navigationBarHeight = getNavigationBarHeight()

                val clampedWidth = min(newWidth, displayMetrics.widthPixels)
                val clampedHeight = min(newHeight, displayMetrics.heightPixels)

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
    private fun openMapScreen() {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        if (!mapBundleRepository.hasBundle()) {
            mapScreen.tvPermissionsCheckResult.visibility = View.VISIBLE
            mapScreen.photoViewMap.visibility = View.GONE
            mapScreen.viewMapOverlay.visibility = View.GONE
            mapScreen.layoutMapMenuContainer.visibility = View.GONE
            pendingMapReadyAction = null
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(mapBundleRepository.bundleImageFile().absolutePath)
            val bounds = mapBundleRepository.loadBounds()
            val roadGraph = mapBundleRepository.loadRoadGraph()
            withContext(Dispatchers.Main) {
                if (bitmap == null || bounds == null) {
                    mapScreen.tvPermissionsCheckResult.visibility = View.VISIBLE
                    mapScreen.photoViewMap.visibility = View.GONE
                    mapScreen.viewMapOverlay.visibility = View.GONE
                    mapScreen.layoutMapMenuContainer.visibility = View.GONE
                    pendingMapReadyAction = null
                    return@withContext
                }
                mapGeoReference = GeoReference(bounds, bitmap.width, bitmap.height)
                pedestrianRouter = roadGraph?.let { PedestrianRouter(it) }
                if (roadGraph == null) {
                    Log.w("MainActivity", "map_roads.json не распарсился — маршрутизация недоступна")
                } else {
                    Log.d("MainActivity", "Граф дорог загружен: ${roadGraph.nodes.size} узлов")
                }
                mapHasCenteredOnUser = false
                mapTapMode = MapTapMode.NONE
                pendingTapChoiceLatLon = null
                mapRouteState = MapRouteState.NONE
                mapRouteDestination = null
                mapRouteLatLonPath = emptyList()
                mapScreen.layoutMapTapChoice.visibility = View.GONE
                mapScreen.layoutMapRouteControls.visibility = View.GONE
                markers = markerRepository.loadAll().toMutableList()
                mapScreen.photoViewMap.setImageBitmap(bitmap)
                mapScreen.photoViewMap.colorFilter = PorterDuffColorFilter(currentWizardAccentColor(), PorterDuff.Mode.MULTIPLY)
                mapScreen.photoViewMap.visibility = View.VISIBLE
                mapScreen.tvPermissionsCheckResult.visibility = View.GONE
                mapScreen.viewMapOverlay.visibility = View.VISIBLE
                mapScreen.viewMapOverlay.routePx = emptyList()
                mapScreen.layoutMapMenuContainer.visibility = View.VISIBLE
                mapScreen.incLayoutTabItemsMapNamePopup.root.visibility = View.GONE
                // PipWizardButtonStyle-кнопки (не CNDEFFRADButtonStyle, тот уже тематизирован
                // через тему Activity сам) — тонируются вручную кодом, тот же приём, что у
                // Settings (currentWizardAccentColor()).
                val mapAccentColor = currentWizardAccentColor()
                val mapAccent = ColorStateList.valueOf(mapAccentColor)
                listOf(
                    mapScreen.btnMapMarkerDetailEdit,
                    mapScreen.btnMapMarkerDetailRoute,
                    mapScreen.btnMapMarkerDetailDelete,
                    mapScreen.btnMapMarkerDetailBack,
                    mapScreen.incLayoutTabItemsMapNamePopup.btnMarkerNamePopupCancel,
                    mapScreen.incLayoutTabItemsMapNamePopup.btnMarkerNamePopupSave,
                    mapScreen.btnMapZoomIn,
                    mapScreen.btnMapZoomOut,
                    mapScreen.btnMapCenter,
                    mapScreen.btnMapControlBack,
                    mapScreen.btnMapTapChoiceRoute,
                    mapScreen.btnMapTapChoiceMarker,
                    mapScreen.btnMapTapChoiceCancel,
                    mapScreen.btnMapRouteStart,
                    mapScreen.btnMapRouteCancel,
                    mapScreen.btnMapRouteStop
                ).forEach { it.backgroundTintList = mapAccent }
                // Уголки панорамирования (roadmap, этап 27, п.2) — "без фона", только цвет
                // текста, тот же приём, что и остальная тематизация вручную кодом (см.
                // CLAUDE.md, "Архитектурный принцип: тематизация интерфейса").
                listOf(
                    mapScreen.btnMapPanUp,
                    mapScreen.btnMapPanDown,
                    mapScreen.btnMapPanLeft,
                    mapScreen.btnMapPanRight,
                ).forEach { it.setTextColor(mapAccentColor) }
                // Прицелы энкодера (focus_corner_brackets) — цвет темы везде на карте, КРОМЕ
                // крестообразного прицела в центре (view_map_crosshair_focus) и прицела над
                // отметкой из списка (view_map_marker_focus) — те красные для контраста с
                // пёстрой картой, задано фиксированно в XML (roadmap, доработка после
                // фидбека, п.1; см. общий приём backgroundTintList в CLAUDE.md). Без этого
                // прицел красился в белую заглушку из самого drawable.
                val mapFocusAccent = ColorStateList.valueOf(mapAccentColor)
                listOf(
                    mapScreen.viewMapZoomFocus,
                    mapScreen.viewMapCenterFocus,
                    mapScreen.viewMapPanUpFocus,
                    mapScreen.viewMapPanDownFocus,
                    mapScreen.viewMapPanLeftFocus,
                    mapScreen.viewMapPanRightFocus,
                    mapScreen.viewMapControlBackFocus,
                    mapScreen.viewMapMarkerDetailEditFocus,
                    mapScreen.viewMapMarkerDetailRouteFocus,
                    mapScreen.viewMapMarkerDetailDeleteFocus,
                    mapScreen.viewMapMarkerDetailBackFocus,
                    mapScreen.viewMapTapChoiceRouteFocus,
                    mapScreen.viewMapTapChoiceMarkerFocus,
                    mapScreen.viewMapTapChoiceCancelFocus,
                    mapScreen.viewMapRouteStartFocus,
                    mapScreen.viewMapRouteCancelFocus,
                    mapScreen.viewMapRouteStopFocus,
                    mapScreen.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupCancelFocus,
                    mapScreen.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupSaveFocus,
                ).forEach { it.backgroundTintList = mapFocusAccent }
                // ImageButton без своего tint наследует android:tint активной темы
                // (Theme.PipDroid.*UI) — то же самое, что уже задокументировано для
                // ImageView/Vault-Boy в CLAUDE.md. Без явного сброса стрелка перекрашивалась
                // в акцент темы поверх фона, тоже акцентного, и сливалась с ним — вектор
                // должен рисоваться своим собственным fillColor (pip_button_text_dark, тот
                // же фиксированный тёмный, что у текста "+"/"−").
                mapScreen.btnMapCenter.imageTintList = null
                hideMapHint()
                // Раньше здесь был безусловный mapRootAdapter.setSelectedPositionSilently(0)
                // ("жёсткий сброс курсора" на свежий вход с вкладки ITEMS) — убран (roadmap,
                // этап 27, доработка энкодер-эргономики): этот блок выполняется асинхронно
                // (Dispatchers.Main после декода битмапы на IO), т.е. ПОЗЖЕ синхронного
                // mapRootAdapter.clearSelection()/menuNavigator.activateSelected() в
                // setOnClickListener btnItemsMap (см. ниже) — безусловный сброс здесь заново
                // подсвечивал пункт 0, даже когда клик пришёл от ENC-перебора строки 2 (не от
                // реального тапа), и рамка оставалась видна до явного ENCBTN (найденный баг).
                // Кому и когда показывать рамку — решает целиком тот listener, эта функция
                // саму подсветку больше не трогает.
                showMapMenuState(MapMenuState.ROOT)
                refreshMarkerPins()
                // Оверлей рисует в пространстве экрана, но хранит точки в пространстве
                // битмапа (см. MapOverlayView) — при любом пане/зуме PhotoView пересчитываем
                // её текущую displayMatrix и заново просим перерисоваться.
                mapScreen.photoViewMap.setOnMatrixChangeListener {
                    val matrix = Matrix()
                    mapScreen.photoViewMap.getDisplayMatrix(matrix)
                    mapScreen.viewMapOverlay.displayMatrix = matrix
                    mapScreen.viewMapOverlay.invalidate()
                    updateMapMarkerFocus()
                }
                mapScreen.photoViewMap.setOnPhotoTapListener { _, xPercent, yPercent ->
                    val geoReference = mapGeoReference ?: return@setOnPhotoTapListener
                    val (lat, lon) = geoReference.fractionToLatLon(xPercent, yPercent)
                    // Тап прямо по сырой карте (не по кнопке) — та же синхронизация курсора
                    // энкодера, что и у остальных тач-обработчиков карты (roadmap, доработка
                    // после фидбека): "любой тап должен переключать курсор энкодера".
                    when (mapTapMode) {
                        MapTapMode.PLACE_MARKER -> {
                            armTapMode(MapTapMode.NONE)
                            syncMapEncoderPath(mapMarkerPopupParentPath() + 0)
                            showMarkerNamePopupForNewMarker(lat, lon)
                        }
                        MapTapMode.ROUTE_TO_POINT -> {
                            armTapMode(MapTapMode.NONE)
                            syncMapEncoderPath(mapControlModeRootPath() + 0)
                            routeTo(lat, lon, listOf(mapRootIndex("ROUTE")))
                        }
                        MapTapMode.NONE -> {
                            val tappedPx = geoReference.latLonToPixel(lat, lon)
                            val marker = findMarkerNearTap(tappedPx)
                            if (marker != null) {
                                // Тап прямо по значку на карте — та же цель, что и выбор из
                                // "Список меток" (не "До отметки" — маршрут тут никто не
                                // просил), поэтому и боковое меню, и путь энкодера — туда же
                                // (roadmap, доработка после фидбека: иначе Back из карточки
                                // вёл бы в список, которого не видно на экране — тач и
                                // боковое меню должны совпадать с тем, куда встал энкодер).
                                mapMenuListReturnState = MapMenuState.ROOT
                                showMapMenuState(MapMenuState.MARKER_LIST)
                                val markerIndex = markers.indexOfFirst { it.id == marker.id }
                                if (markerIndex != -1) {
                                    mapMarkerListAdapter.setSelectedPositionSilently(markerIndex)
                                    syncMapEncoderPath(listOf(mapRootIndex("MARKER_LIST"), markerIndex, 0))
                                }
                                showMarkerDetail(marker)
                            } else {
                                syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0))
                                showMapTapChoice(lat, lon)
                            }
                        }
                    }
                }
                startMapLocationUpdates()
                pendingMapReadyAction?.invoke()
                pendingMapReadyAction = null
            }
        }
    }
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
    /** Останавливать при уходе с экрана карты
     */
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
        if (mapRouteState == MapRouteState.ACTIVE) {
            updateActiveNavigation(location)
        }
    }
    /** Строит матрицу вручную
     */
    private fun recenterMapOnUser() {
        val userPx = bindingMain.incLayoutTabItemsMap.viewMapOverlay.userLocationPx ?: return
        centerMapOnBitmapPoint(userPx)
    }
    /** Нижний слот карты
     */
    private fun mapBottomOverlayHeightPx(): Float {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        return listOf(
            mapScreen.layoutMapMarkerDetail,
            mapScreen.layoutMapTapChoice,
            mapScreen.layoutMapRouteControls,
            mapScreen.tvMapHint,
        ).firstOrNull { it.visibility == View.VISIBLE }?.height?.toFloat() ?: 0f
    }
    /** Сдвигает PhotoView
     */
    private fun centerMapOnBitmapPoint(targetPx: PointF) {
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        // getDisplayMatrix() отдаёт ПОЛНУЮ матрицу (базовая "вписать в экран" + supp,
        // накопленный из жестов пользователя) — по ней корректно находим текущую позицию
        // целевой точки на экране.
        val fullMatrix = Matrix()
        photoView.getDisplayMatrix(fullMatrix)
        val screenPoint = floatArrayOf(targetPx.x, targetPx.y)
        fullMatrix.mapPoints(screenPoint)
        val dx = photoView.width / 2f - screenPoint[0]
        val dy = (photoView.height - mapBottomOverlayHeightPx()) / 2f - screenPoint[1]
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
    /** Пересчитывает пиксельные позиции маркеров из [markers] (лат/лон) через
     * [mapGeoReference] и передаёт в оверлей вместе с именами (подпись рисуется прямо на
     * карте, см. MapOverlayView.markerPins). Вызывать после любого изменения списка
     * (добавление/удаление/переименование) или открытия экрана карты. */
    private fun refreshMarkerPins() {
        val geoReference = mapGeoReference ?: return
        bindingMain.incLayoutTabItemsMap.viewMapOverlay.markerPins =
            markers.map { it.name to geoReference.latLonToPixel(it.lat, it.lon) }
    }
    /** Прицел энкодера (focus_corner_brackets, переиспользован — см. view_map_marker_focus)
     * над отметкой, выбранной в "Список меток". Красный, а не белый по умолчанию: белый на
     * пёстрой карте не виден (фидбек этапа 27). MapOverlayView рисует отметки вручную на
     * Canvas, у них нет своего @id — привычный constraint-биндинг прицела к цели (см.
     * CLAUDE.md) тут неприменим, поэтому позиция считается вручную из той же displayMatrix,
     * что и у самого оверлея, и пересчитывается при каждом пане/зуме (см. вызов в
     * setOnMatrixChangeListener). */
    private fun updateMapMarkerFocus() {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        val focusView = mapScreen.viewMapMarkerFocus
        val marker = selectedMarkerForDetail
        val geoReference = mapGeoReference
        if (marker == null || geoReference == null) {
            focusView.visibility = View.GONE
            return
        }
        val matrix = Matrix()
        mapScreen.photoViewMap.getDisplayMatrix(matrix)
        val screenPoint = floatArrayOf(0f, 0f)
        geoReference.latLonToPixel(marker.lat, marker.lon).let { screenPoint[0] = it.x; screenPoint[1] = it.y }
        matrix.mapPoints(screenPoint)
        focusView.translationX = screenPoint[0] - focusView.width / 2f
        focusView.translationY = screenPoint[1] - focusView.height / 2f
        focusView.visibility = View.VISIBLE
    }
    /** Три состояния левого меню (по образцу ITEMS/Clock — переключение видимости, не три
     * отдельных экрана): корень, подменю "Проложить маршрут", список отметок (общий для
     * корневого "Список меток" и "До отметки" — см. mapMenuListReturnState). */
    private enum class MapMenuState { ROOT, ROUTE_SUBMENU, MARKER_LIST }
    /** Метаданные корня и подменю "Маршрут" (roadmap, "Единый компонент бокового меню
     * 3 уровня") — тот же приём, что у statusMeta (список отметок — отдельная задача,
     * шаг 7 плана, там уже есть готовый RecyclerView+MarkerListAdapter). */
    private data class MapMenuItemMeta(val key: String, val labelRes: Int, val action: () -> Unit)
    private val mapRootMeta: List<MapMenuItemMeta> by lazy {
        listOf(
            // "Управление картой" — первым пунктом (roadmap, этап 27, энкодер-эргономика
            // карты, п.1) — на тач это то же самое, что провал энкодером в MAP_CONTROLS
            // (mapRootChildrenNodes()): открывает панель Zoom/Center/Pan/Crosshair/Back
            // поверх карты, сама панель управляется тачем как обычно (кнопки активны для
            // обоих способов ввода). Гейт "только режимы с физическим энкодером" — НЕ
            // здесь: mapRootMeta кешируется `by lazy` один раз, до того как pipBoyMode
            // вообще мог стать известен (та же находка, что у dataFilesMeta/clockMeta) —
            // видимость пересчитывается свежо в mapRootSidebarItems()/mapRootChildrenNodes().
            MapMenuItemMeta("MAP_CONTROLS", R.string.map_menu_control_button) {
                mapControlMode = MapControlMode.ROOT
                setMapControlOverlayVisible(true)
            },
            // "Center" убран из этого списка (roadmap, этап 27, энкодер-карта) — теперь
            // отдельная кнопка-стрелка рядом с +/- (см. btn_map_center), тот же
            // recenterMapOnUser() переиспользован её обработчиком. "Поставить отметку" —
            // та же панель Crosshair/Pan/Zoom/Center/Back, что "Управление картой" (roadmap,
            // доработка после фидбека, п.5), тот же приём, что и у "До точки на карте" ниже.
            MapMenuItemMeta("PLACE_MARKER", R.string.map_menu_place_marker_button) {
                mapControlMode = MapControlMode.PLACE_MARKER
                setMapControlOverlayVisible(true)
                armTapMode(MapTapMode.PLACE_MARKER)
            },
            MapMenuItemMeta("ROUTE", R.string.map_menu_route_button) {
                // Провал вглубь — курсор подменю с индекса 0 (см. showMapMenuState()).
                mapRouteSubmenuAdapter.setSelectedPositionSilently(0)
                showMapMenuState(MapMenuState.ROUTE_SUBMENU)
            },
            MapMenuItemMeta("MARKER_LIST", R.string.map_menu_marker_list_button) {
                mapMenuListReturnState = MapMenuState.ROOT
                showMapMenuState(MapMenuState.MARKER_LIST)
            },
        )
    }
    private val mapRouteSubmenuMeta: List<MapMenuItemMeta> by lazy {
        listOf(
            // Не прыгает обратно в ROOT сразу по выбору — пользователь остаётся на этом
            // пункте подменю, пока не тапнет по карте (фидбек: "меню не должно сбрасываться
            // на первый уровень при выборе способа прокладки маршрута"). Сайдбар уходит в
            // ROOT только когда маршрут реально построен, см. routeTo().
            MapMenuItemMeta("TO_POINT", R.string.map_route_to_point_button) {
                mapControlMode = MapControlMode.ROUTE_TO_POINT
                setMapControlOverlayVisible(true)
                armTapMode(MapTapMode.ROUTE_TO_POINT)
            },
            MapMenuItemMeta("TO_MARKER", R.string.map_route_to_marker_button) {
                mapMenuListReturnState = MapMenuState.ROUTE_SUBMENU
                showMapMenuState(MapMenuState.MARKER_LIST)
            },
            MapMenuItemMeta("BACK", R.string.wizard_back) { showMapMenuState(MapMenuState.ROOT) },
        )
    }
    /** Пункты бокового меню Map для тача — та же схема, что у dataFilesSidebarItems()/
     * clockSidebarItems(): гейт "Управление картой" пересчитывается свежо на каждый вызов
     * (не внутри `mapRootMeta` — та кешируется `by lazy`, см. комментарий там). Общая точка
     * и для начальной постройки mapRootAdapter в onCreate(), и для refreshSidebarBackItems()
     * — тот же приём, что у остальных списков. mapRootChildrenNodes() ищет позиции узлов
     * ИМЕННО в этом списке (не в сыром mapRootMeta), чтобы индексы совпадали с адаптером
     * независимо от режима. */
    private fun mapRootSidebarItems(): List<SidebarMenuItem<String>> {
        val items = mapRootMeta.filter { it.key != "MAP_CONTROLS" || pipBoyMode != PipBoyMode.PHONE }
            .map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes)) }
        // "В меню" — последним пунктом (roadmap, доработка после фидбека — забытый пункт:
        // без него нет способа вернуть курсор энкодера на уровень выше, ITEMS row2), тот же
        // приём, что у dataFilesSidebarItems()/clockSidebarItems().
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    private lateinit var mapRootAdapter: SidebarMenuAdapter<String>
    private lateinit var mapRouteSubmenuAdapter: SidebarMenuAdapter<String>
    /** Список отметок — раньше локальный `val` внутри bindMarkerListAdapter() (не был нужен
     * снаружи, пока список не читал только тач). roadmap, этап 27 — энкодер-эргономика
     * карты: нужен полем, чтобы mapMarkerListChildrenNodes() могла звать
     * flashPressAnimation()/setSelectedPositionSilently() на нём же, тот же приём, что у
     * mapRootAdapter/mapRouteSubmenuAdapter/journalListAdapter. */
    private lateinit var mapMarkerListAdapter: SidebarMenuAdapter<MapMarker?>
    /** Какая из двух панелей делит с "Управление картой" один и тот же набор Zoom/Center/
     * Pan/Crosshair/Back-стрелка (roadmap, этап 27, п.6 — mapControlChildrenNodes()) сейчас
     * открыта — нужно тачу по крестику (view_map_crosshair.setOnClickListener), чтобы понять,
     * показать ли выбор [Route]/[Marker]/[Cancel] (ROOT) или сразу построить маршрут
     * (ROUTE_TO_POINT), тот же выбор, что уже делает CROSSHAIR.onActivate у энкодера. */
    private var mapControlMode: MapControlMode = MapControlMode.ROOT
    private fun showMapMenuState(state: MapMenuState) {
        // Навигация по сайдбар-меню (Build Route/Marker List/Back) прерывает незавершённый
        // цикл взвода тапа (Place a marker/До точки на карте) — игрок ушёл в другую логику,
        // подсказка "Tap the map to..." не должна продолжать висеть, а следующий тап по
        // карте не должен неожиданно поставить отметку/точку маршрута (баг из фидбека).
        if (mapTapMode != MapTapMode.NONE) {
            armTapMode(MapTapMode.NONE)
        }
        // Переход в любое из трёх состояний бокового меню закрывает панель "Управление
        // картой"/"До точки на карте" (roadmap, этап 27, п.1/6) — тот же принцип, что и
        // сброс mapTapMode/hideMapTapChoice() выше, экран не должен показывать сразу два
        // взаимоисключающих набора элементов управления. Попап ввода имени отметки — та же
        // логика (roadmap, доработка после фидбека — найденный баг: тап по другому пункту
        // бокового меню, пока попап открыт, оставлял его висеть поверх новой панели).
        setMapControlOverlayVisible(false)
        hideMarkerNamePopup()
        mapMenuState = state
        val menu = bindingMain.incLayoutTabItemsMap
        menu.recyclerMapMenuRoot.visibility = if (state == MapMenuState.ROOT) View.VISIBLE else View.GONE
        menu.recyclerMapMenuRouteSubmenu.visibility = if (state == MapMenuState.ROUTE_SUBMENU) View.VISIBLE else View.GONE
        menu.layoutMapMenuMarkerList.visibility = if (state == MapMenuState.MARKER_LIST) View.VISIBLE else View.GONE
        // Курсор НЕ сбрасывается тут на первый пункт — тот же принцип, что уже
        // задокументирован для энкодера ("Модель навигации энкодером"): провалиться вглубь
        // (Root->RouteSubmenu/MarkerList, RouteSubmenu->MarkerList) — курсор с индекса 0,
        // это делает конкретное действие-триггер ниже (см. mapRootMeta/mapRouteSubmenuMeta).
        // Подняться обратно (Back, "До точки на карте", завершение маршрута до отметки) —
        // курсор родительского уровня остаётся там, где был, а не сбрасывается — фидбек по
        // итогам тестирования: "возвращаемся не в начало, а туда, откуда пришли". Реальный
        // свежий вход в раздел (по вкладке ITEMS) — отдельный явный сброс в openMapScreen().
        if (state == MapMenuState.MARKER_LIST) {
            bindMarkerListAdapter()
        } else {
            hideMarkerDetail()
        }
        // Навигация по сайдбар-меню отменяет незавершённый выбор [Route]/[Marker] по тапу на
        // пустую точку (бэклог этапа 18) — тот же принцип, что и у карточки деталей отметки.
        hideMapTapChoice()
    }
    /** [Назад] — обычный последний пункт списка (payload=null), roadmap "Единый компонент
     * бокового меню 3 уровня" — чинит известный баг из Roadmap (кривой отступ кнопки, нет
     * звука). Пересобирается заново при каждом входе в MARKER_LIST (как и было у
     * MarkerListAdapter) — курсор поэтому всегда стартует с индекса 0, это "провал вглубь"
     * что от корня, что от подменю маршрута. */
    private fun bindMarkerListAdapter() {
        val menu = bindingMain.incLayoutTabItemsMap
        menu.tvMapMarkerListEmpty.visibility = if (markers.isEmpty()) View.VISIBLE else View.GONE
        val items: List<SidebarMenuItem<MapMarker?>> = markers.map { marker -> SidebarMenuItem<MapMarker?>(payload = marker, label = marker.name) } +
            SidebarMenuItem(payload = null, label = getString(R.string.wizard_back))
        // "До отметки" (mapMenuListReturnState == ROUTE_SUBMENU) — выбор сразу строит
        // маршрут, это просто выбор цели (подтверждено пользователем). "Список меток"
        // (вход через корень) — выбор открывает карточку деталей с Редактировать/Маршрут/
        // Удалить, это отдельный сценарий просмотра/управления, не выбор цели.
        val adapter = SidebarMenuAdapter(
            items = items,
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера (roadmap, доработка после
                // фидбека) — см. syncMapEncoderPath()/mapMarkerListParentPath(). Отметка в
                // "Список меток" (не "До отметки") — с детьми (карточка), курсор на первого
                // ребёнка (Edit); отметка в "До отметки" — лист, остаётся на месте. Back —
                // особый случай: его реальный эффект — popLevel() (см.
                // MAP_MARKER_LIST_BACK.onActivate), поэтому путь останавливается НА
                // РОДИТЕЛЕ списка (mapMarkerListParentPath() без "+ position") — ровно там,
                // где курсор окажется ПОСЛЕ этого popLevel(), а не на самом листе Back
                // (найденный баг: тап на Back оставлял курсор "в списке").
                val marker = item.payload
                val path = when {
                    marker == null -> mapMarkerListParentPath()
                    mapMenuListReturnState == MapMenuState.ROUTE_SUBMENU -> mapMarkerListParentPath() + position
                    else -> mapMarkerListParentPath() + position + 0
                }
                syncMapEncoderPath(path)
                when {
                    marker == null -> showMapMenuState(mapMenuListReturnState)
                    // Сайдбар в ROOT переводит сама routeTo() по факту построения маршрута
                    // (не сразу по выбору цели) — тот же принцип, что и у "До точки на карте".
                    mapMenuListReturnState == MapMenuState.ROUTE_SUBMENU -> routeTo(marker.lat, marker.lon, listOf(mapRootIndex("ROUTE")))
                    else -> {
                        showMarkerDetail(marker)
                        centerMapOnMarkerDeferred(marker)
                    }
                }
            },
        )
        mapMarkerListAdapter = adapter
        menu.rvMapMarkerList.layoutManager = LinearLayoutManager(this)
        menu.rvMapMarkerList.adapter = adapter
    }
    /** Карточка деталей выбранной отметки (имя/координаты + Редактировать/Маршрут/Удалить) —
     * не полноэкранная, только над картой справа от меню (см. layout_map_marker_detail).
     * Открывается и из списка отметок, и прямым тапом по маркеру на карте (бэклог этапа 18,
     * см. findMarkerNearTap()) — делит нижний слот карты с попапом выбора [Route]/[Marker] и
     * панелью управления маршрутом, поэтому прячет обе при показе. */
    private fun showMarkerDetail(marker: MapMarker) {
        selectedMarkerForDetail = marker
        val mapScreen = bindingMain.incLayoutTabItemsMap
        mapScreen.tvMapMarkerDetailName.text = marker.name
        mapScreen.tvMapMarkerDetailCoords.text = String.format(Locale.getDefault(), "%.5f, %.5f", marker.lat, marker.lon)
        pendingTapChoiceLatLon = null
        mapScreen.layoutMapTapChoice.visibility = View.GONE
        mapScreen.layoutMapRouteControls.visibility = View.GONE
        mapScreen.layoutMapMarkerDetail.visibility = View.VISIBLE
    }
    /** Отметка может быть вне текущего пана/зума — центрируем карту на ней (тот же
     * centerMapOnBitmapPoint(), что и у кнопки "Центр"), иначе карточка/прицел окажутся за
     * кадром (фидбек по итогам тестирования этапа 27, и позже — доработка: то же самое
     * нужно и при выборе ENCBTN в mapMarkerListChildrenNodes(), не только по тачу). Вызывать
     * СРАЗУ ПОСЛЕ showMarkerDetail(marker) (не до) — центрирование отложено до реального
     * layout-прохода карточки (post{}), mapBottomOverlayHeightPx() внутри
     * centerMapOnBitmapPoint() должна читать уже актуальную высоту панели, иначе
     * (GONE → VISIBLE ещё не отмерена) высота была бы 0 и центр съезжал под панель. */
    private fun centerMapOnMarkerDeferred(marker: MapMarker) {
        val geoReference = mapGeoReference ?: return
        val targetPx = geoReference.latLonToPixel(marker.lat, marker.lon)
        bindingMain.incLayoutTabItemsMap.layoutMapMarkerDetail.post { centerMapOnBitmapPoint(targetPx) }
    }
    private fun hideMarkerDetail() {
        selectedMarkerForDetail = null
        bindingMain.incLayoutTabItemsMap.layoutMapMarkerDetail.visibility = View.GONE
        // Панель управления маршрутом была спрятана визуально (не сброшена по состоянию),
        // пока была видна карточка деталей — восстановить, если маршрут всё ещё есть.
        updateRouteControlsVisibility()
    }
    /** Тап по пустой точке карты вне режима расстановки/маршрута (бэклог этапа 18) — вместо
     * жёстко предопределённого действия предлагает выбор [Route]/[Marker], см.
     * layout_map_tap_choice. Делит нижний слот с карточкой деталей отметки и панелью
     * маршрута. */
    private fun showMapTapChoice(lat: Double, lon: Double) {
        pendingTapChoiceLatLon = lat to lon
        val mapScreen = bindingMain.incLayoutTabItemsMap
        mapScreen.tvMapTapChoiceCoords.text = String.format(Locale.getDefault(), "%.5f, %.5f", lat, lon)
        selectedMarkerForDetail = null
        mapScreen.layoutMapMarkerDetail.visibility = View.GONE
        mapScreen.layoutMapRouteControls.visibility = View.GONE
        mapScreen.layoutMapTapChoice.visibility = View.VISIBLE
        // Кнопка "←" должна прятаться под этой панелью, не оставаться поверх (roadmap,
        // доработка после фидбека, п.4).
        refreshMapControlBackButtonVisibility()
    }
    private fun hideMapTapChoice() {
        pendingTapChoiceLatLon = null
        bindingMain.incLayoutTabItemsMap.layoutMapTapChoice.visibility = View.GONE
        updateRouteControlsVisibility()
        refreshMapControlBackButtonVisibility()
    }
    /** Ближайший к тапу маркер в ЭКРАННЫХ координатах (не в пикселях битмапа — иначе радиус
     * захвата "плавал" бы с зумом), см. MAP_MARKER_TAP_RADIUS_DP. null — тап дальше порога от
     * любого маркера, вызывающий код (onPhotoTapListener) трактует это как тап по пустой
     * точке. */
    private fun findMarkerNearTap(tapBitmapPx: PointF): MapMarker? {
        if (markers.isEmpty()) return null
        val geoReference = mapGeoReference ?: return null
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        val matrix = Matrix()
        photoView.getDisplayMatrix(matrix)
        val tapScreen = floatArrayOf(tapBitmapPx.x, tapBitmapPx.y)
        matrix.mapPoints(tapScreen)
        val thresholdPx = resources.displayMetrics.density * MAP_MARKER_TAP_RADIUS_DP
        var nearestMarker: MapMarker? = null
        var nearestDist = Double.MAX_VALUE
        for (marker in markers) {
            val markerPx = geoReference.latLonToPixel(marker.lat, marker.lon)
            val screen = floatArrayOf(markerPx.x, markerPx.y)
            matrix.mapPoints(screen)
            val dx = (screen[0] - tapScreen[0]).toDouble()
            val dy = (screen[1] - tapScreen[1]).toDouble()
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < nearestDist) {
                nearestDist = dist
                nearestMarker = marker
            }
        }
        return nearestMarker?.takeIf { nearestDist <= thresholdPx }
    }
    /** +/- зум карты (бэклог этапа 18) — раньше только pinch. PhotoView.setScale() кидает
     * IllegalArgumentException за пределами [minimumScale, maximumScale], поэтому клэмпим
     * вручную перед вызовом, а не полагаемся на исключение. */
    private fun zoomMapBy(factor: Float) {
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        val target = (photoView.scale * factor).coerceIn(photoView.minimumScale, photoView.maximumScale)
        photoView.setScale(target, true)
    }
    private fun showMapHint(text: String) {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        // Делит нижний слот карты с карточкой деталей отметки/попапом [Route]/[Marker]/
        // панелью маршрута (бэклог этапа 18) — те же координаты в layout_tab_items_map.xml,
        // поэтому взаимоисключающе прячет их (панель маршрута — только визуально, её
        // состояние переживёт следующий hideMapHint(), см. updateRouteControlsVisibility()).
        selectedMarkerForDetail = null
        pendingTapChoiceLatLon = null
        mapScreen.layoutMapMarkerDetail.visibility = View.GONE
        mapScreen.layoutMapTapChoice.visibility = View.GONE
        mapScreen.layoutMapRouteControls.visibility = View.GONE
        val hintView = mapScreen.tvMapHint
        hintView.text = text
        // Фон/цвет текста явно кодом (см. showMapMenuState) — ставим тут же, а не один раз в
        // openMapScreen(), чтобы полоса точно перекрашивалась при каждом показе.
        // backgroundTintList = null обязателен: AppCompat сам подмешивает акцент темы поверх
        // ЛЮБОГО выставленного фона (тот же баг, что уже был с кнопками, см. CLAUDE.md) — без
        // сброса фон реально рендерился в themeGreen поверх моего pip_background_darker,
        // и текст того же акцентного цвета становился на нём невидим.
        hintView.backgroundTintList = null
        hintView.setBackgroundColor(ContextCompat.getColor(this, R.color.pip_background_darker))
        hintView.setTextColor(currentWizardAccentColor())
        hintView.visibility = View.VISIBLE
    }
    private fun hideMapHint() {
        bindingMain.incLayoutTabItemsMap.tvMapHint.visibility = View.GONE
        updateRouteControlsVisibility()
    }
    /** Взвод режима тапа по карте — либо расстановка отметки, либо выбор точки маршрута
     * (кнопки "Поставить отметку"/"До точки на карте"). Подсказка появляется в полосе внизу
     * карты, не в тексте самой кнопки. */
    private fun armTapMode(mode: MapTapMode) {
        mapTapMode = mode
        // Подсказка "Tap the map to..." — только Phone (roadmap, доработка после фидбека,
        // п.3): в PipBoy 2000/3000 её место занимает крестообразный прицел/панель
        // Zoom/Pan/Center (setMapControlOverlayVisible()), сам режим тапа по сырому касанию
        // экрана при этом остаётся взведён и рабочим (тач по-прежнему доступен), просто без
        // текстовой подсказки, которая не про энкодер.
        when (mode) {
            MapTapMode.PLACE_MARKER -> if (pipBoyMode == PipBoyMode.PHONE) showMapHint(getString(R.string.map_hint_place_marker))
            MapTapMode.ROUTE_TO_POINT -> if (pipBoyMode == PipBoyMode.PHONE) showMapHint(getString(R.string.map_hint_route_to_point))
            MapTapMode.NONE -> hideMapHint()
        }
    }
    // Ни здесь, ни в Settings/Filter (единственных других экранах с EditText в проекте) нет ни
    // строчки кода про InputMethodManager/requestFocus — клавиатура открывается обычным тапом
    // по полю, системным поведением. Более ранняя версия пыталась звать showSoftInput()
    // программно сама, без тапа — Android такие вызовы вне прямого ответа на касание часто
    // просто игнорирует, отсюда и был баг.
    private fun showMarkerNamePopupForNewMarker(lat: Double, lon: Double) {
        editingMarkerId = null
        pendingMarkerLatLon = lat to lon
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        popup.etMarkerNameValue.setText("")
        popup.root.visibility = View.VISIBLE
    }
    private fun showMarkerNamePopupForEdit(marker: MapMarker) {
        editingMarkerId = marker.id
        pendingMarkerLatLon = marker.lat to marker.lon
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        popup.etMarkerNameValue.setText(marker.name)
        popup.root.visibility = View.VISIBLE
    }
    private fun hideMarkerNamePopup() {
        pendingMarkerLatLon = null
        editingMarkerId = null
        bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.root.visibility = View.GONE
    }
    /***********************************************************************************************************
     * ITEMS - JOURNAL (roadmap, этап 20) — личные записи игрока, текстовый ввод. Тот же
     * паттерн "список слева / контент справа", что у Map/Clock, тот же repository-подход,
     * что у отметок карты выше. Голосовой ввод (Vosk) — отдельный, более поздний шаг.
     **********************************************************************************************************/
    private fun openJournalScreen() {
        journalEntries = journalRepository.loadAll().toMutableList()
        bindJournalListAdapter()
        hideJournalEntryDetail()
    }
    private lateinit var journalListAdapter: SidebarMenuAdapter<JournalSidebarEntry>
    /** Первый пункт списка — всегда "Новая запись", дальше все существующие записи (новые
     * сверху), "В меню" — последним, только в режиме PipBoy 2000/3000 (roadmap, этап 27,
     * п.2 — тот же приём, что у dataFilesSidebarItems()/statusSidebarItems(): порядок здесь
     * обязан совпадать с journalChildrenNodes() дерева энкодера). */
    private fun journalSidebarItems(): List<SidebarMenuItem<JournalSidebarEntry>> {
        val items: List<SidebarMenuItem<JournalSidebarEntry>> =
            listOf(SidebarMenuItem<JournalSidebarEntry>(payload = JournalSidebarEntry.NewEntry, label = getString(R.string.journal_new_entry_button))) +
                journalEntries.sortedByDescending { it.createdAtEpochMillis }
                    .map { entry -> SidebarMenuItem<JournalSidebarEntry>(payload = JournalSidebarEntry.Existing(entry), label = formatJournalDate(entry.createdAtEpochMillis)) }
        return if (pipBoyMode != PipBoyMode.PHONE) {
            items + SidebarMenuItem<JournalSidebarEntry>(payload = JournalSidebarEntry.Menu, label = getString(R.string.sidebar_menu_back))
        } else {
            items
        }
    }
    /** [initialSelectedPosition] — курсор энкодера после Save/Delete должен встать на
     * затронутую запись, не всегда на 0 (roadmap, этап 27, п.3/4). */
    private fun bindJournalListAdapter(initialSelectedPosition: Int = 0) {
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        val adapter = SidebarMenuAdapter(
            items = journalSidebarItems(),
            selectedBackgroundRes = selected_button,
            initialSelectedPosition = initialSelectedPosition,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера с тачем (roadmap, доработка после
                // фидбека) — не menuNavigator.syncCursor(), тот чинит курсор только ВНУТРИ уже
                // активного уровня списка записей (см. doc у syncJournalEncoderPath()).
                when (item.payload) {
                    // "+ 0" — тап равносилен ENCBTN на этом пункте: и у "Новой записи", и у
                    // любой существующей есть дети (Mic/Cancel/Save или Edit/Delete/Back),
                    // курсор садится на первого ребёнка, не остаётся на самом пункте (тот же
                    // приём, что у Map/Clock, см. doc у MenuNavigator.setPath()). Loud-путь сам
                    // вызывает onHighlight первого ребёнка, который и открывает нужный экран
                    // (showJournalEntryEditorForNew()/showJournalEntryDetail()) — отдельно
                    // звать их здесь больше не нужно.
                    is JournalSidebarEntry.NewEntry -> syncJournalEncoderPath(listOf(position, 0))
                    is JournalSidebarEntry.Existing -> syncJournalEncoderPath(listOf(position, 0))
                    is JournalSidebarEntry.Menu -> {
                        playConfirmAudio()
                        syncJournalEncoderPathSilently(emptyList())
                        syncRow2ActiveFromNavigator()
                    }
                }
            },
        )
        journalListAdapter = adapter
        journalScreen.rvJournalEntryList.layoutManager = LinearLayoutManager(this)
        journalScreen.rvJournalEntryList.adapter = adapter
    }
    // Игровой год (тот же приём, что в tickThread для шапки/часов) — реальные месяц/день/
    // время записи остаются как есть, подменяется только YEAR перед форматированием.
    private fun formatJournalDate(epochMillis: Long): String {
        val gameCalendar = Calendar.getInstance()
        gameCalendar.timeInMillis = epochMillis
        gameCalendar.set(Calendar.YEAR, sharedPreferences.getInt(gameYear_SPKey, 2276))
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(gameCalendar.time)
    }
    /** Карточка записи — взаимоисключающе с подсказкой и с редактором (roadmap, этап 27).
     * hideJournalEntryEditor()/setAllJournalEntryDetailFocusesHidden() в начале — идемпотентная
     * подстраховка, а не просто для входа из списка: эта же функция дёргается из onHighlight
     * узла EDIT (journalEntryDetailChildrenNodes()) при возврате из редактора по Cancel/Save,
     * когда его действительно нужно закрыть и сбросить прицелы Edit/Delete/Back. */
    private fun showJournalEntryDetail(entry: JournalEntry) {
        selectedJournalEntryForDetail = entry
        hideJournalEntryEditor()
        setAllJournalEntryDetailFocusesHidden()
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        journalScreen.tvJournalEntryDetailDate.text = formatJournalDate(entry.createdAtEpochMillis)
        journalScreen.tvJournalEntryDetailText.text = entry.text
        journalScreen.tvJournalHint.visibility = View.GONE
        journalScreen.layoutJournalEntryDetail.visibility = View.VISIBLE
    }
    /** Подсказка справа зависит от того, есть ли вообще записи — "нет записей" (список
     * никогда не пуст сам по себе, там всегда есть "Новая запись", поэтому это отдельная
     * проверка не по списку, а по journalEntries) или обычное "выбери запись". */
    private fun hideJournalEntryDetail() {
        selectedJournalEntryForDetail = null
        hideJournalEntryEditor()
        setAllJournalEntryDetailFocusesHidden()
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        journalScreen.layoutJournalEntryDetail.visibility = View.GONE
        journalScreen.tvJournalHint.text = getString(
            if (journalEntries.isEmpty()) R.string.journal_entry_list_empty else R.string.journal_hint
        )
        journalScreen.tvJournalHint.visibility = View.VISIBLE
    }
    // Клавиатура открывается обычным тапом по EditText, никакого showSoftInput()/
    // requestFocus() в коде (см. CLAUDE.md — уже наступали на эти грабли на Map/Filter).
    /** Редактор занимает всю контентную область (roadmap, этап 27, п.1 — раньше всплывающая
     * панель поверх контента) — при создании подменяет собой подсказку. Ранний выход по
     * journalEditorOpenFor (см. объявление поля выше) — редактор уже открыт на новую запись,
     * нельзя затирать уже введённый текст: onHighlight узла MIC (journalEntryEditorChildrenNodes())
     * зовёт эту же функцию на каждое возвращение курсора на Mic внутри уже открытого
     * редактора, не только один раз при входе. */
    private fun showJournalEntryEditorForNew() {
        if (journalEditorOpenFor == JOURNAL_NEW_ENTRY_SENTINEL) return
        journalEditorOpenFor = JOURNAL_NEW_ENTRY_SENTINEL
        editingJournalEntryId = null
        selectedJournalEntryForDetail = null
        setAllJournalEntryDetailFocusesHidden()
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        journalScreen.tvJournalHint.visibility = View.GONE
        journalScreen.layoutJournalEntryDetail.visibility = View.GONE
        val popup = journalScreen.incLayoutTabItemsJournalEntryPopup
        popup.etJournalEntryValue.setText("")
        popup.root.visibility = View.VISIBLE
        refreshJournalMicAvailability()
    }
    /** Та же схема, что у [showJournalEntryEditorForNew] выше, только подменяет собой не
     * подсказку, а карточку конкретной записи (roadmap, этап 27, п.1 — "заменять собой
     * редактируемую запись"). */
    private fun showJournalEntryEditorForEdit(entry: JournalEntry) {
        if (journalEditorOpenFor == entry.id) return
        journalEditorOpenFor = entry.id
        editingJournalEntryId = entry.id
        setAllJournalEntryDetailFocusesHidden()
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        journalScreen.tvJournalHint.visibility = View.GONE
        journalScreen.layoutJournalEntryDetail.visibility = View.GONE
        val popup = journalScreen.incLayoutTabItemsJournalEntryPopup
        popup.etJournalEntryValue.setText(entry.text)
        popup.root.visibility = View.VISIBLE
        refreshJournalMicAvailability()
    }
    /** Идемпотентен (безопасно звать многократно, в т.ч. когда редактор и так уже закрыт) —
     * вызывается защитно из showJournalEntryDetail()/hideJournalEntryDetail() тоже, не
     * только явным Cancel/Save (roadmap, этап 27). */
    private fun hideJournalEntryEditor() {
        journalEditorOpenFor = null
        editingJournalEntryId = null
        stopJournalDictation()
        setAllJournalEntryEditorFocusesHidden()
        bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.root.visibility = View.GONE
    }
    /** Сбрасывает кнопку-микрофон и статус-строку к состоянию покоя при каждом открытии
     * попапа (в т.ч. на случай, если предыдущая сессия попапа была закрыта не через
     * hideJournalEntryPopup) — alpha сразу отражает, импортирована ли модель. */
    private fun refreshJournalMicAvailability() {
        journalDictationState = JournalDictationState.IDLE
        setJournalMicStatus("")
        val popup = bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup
        popup.btnJournalEntryMic.alpha = if (voiceModelRepository.hasModel()) 1f else 0.4f
        updateJournalMicVisual(recording = false)
    }
    private fun setJournalMicStatus(text: String) {
        bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.tvJournalEntryMicStatus.text = text
    }
    /** Слушающее состояние красится чуть светлее акцента темы (blend с белым) — не жёстко
     * зашитым цветом (CLAUDE.md, "Архитектурный принцип: тематизация интерфейса"), просто
     * производным от того же currentWizardAccentColor(), что и обычный фон кнопки. Иконка
     * меняется на квадрат "стоп", пока идёт запись — иначе тап 1/тап 2 неотличимы на вид. */
    private fun updateJournalMicVisual(recording: Boolean) {
        val accent = currentWizardAccentColor()
        val tint = if (recording) ColorUtils.blendARGB(accent, Color.WHITE, 0.4f) else accent
        val micButton = bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.btnJournalEntryMic
        micButton.backgroundTintList = ColorStateList.valueOf(tint)
        micButton.setImageResource(if (recording) R.drawable.ic_stop else R.drawable.ic_mic)
        ImageViewCompat.setImageTintList(micButton, null)
    }
    private fun appendJournalDictatedText(text: String) {
        if (text.isBlank()) return
        val editText = bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.etJournalEntryValue
        val current = editText.text?.toString().orEmpty()
        val separator = if (current.isNotEmpty() && !current.endsWith(" ") && !current.endsWith("\n")) " " else ""
        editText.append(separator + text)
    }
    /** Тап 1 по микрофону. Модель Vosk грузится лениво при первом использовании за сессию
     * приложения (voiceDictationService переживает открытие/закрытие попапа) — если уже
     * загружена, слушать начинает сразу, иначе показывает "Загрузка модели…" и стартует
     * после. journalDictationState — не только UI-индикатор, но и защита от гонки: если
     * попап закрыли посреди загрузки (hideJournalEntryPopup -> stopJournalDictation -> IDLE),
     * повторный тап на LOADING просто игнорируется, а колбэк загрузки, увидев, что состояние
     * уже не LOADING, не запускает прослушивание вдогонку. */
    private fun startJournalDictation() {
        if (voiceDictationService.isModelLoaded()) {
            beginJournalListening()
            return
        }
        journalDictationState = JournalDictationState.LOADING
        setJournalMicStatus(getString(R.string.journal_mic_status_loading))
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { voiceDictationService.loadModel(voiceModelRepository.modelDir().absolutePath) }
            withContext(Dispatchers.Main) {
                if (journalDictationState != JournalDictationState.LOADING) return@withContext
                if (result.isFailure) {
                    journalDictationState = JournalDictationState.IDLE
                    setJournalMicStatus(getString(R.string.journal_mic_status_error))
                    return@withContext
                }
                beginJournalListening()
            }
        }
    }
    private fun beginJournalListening() {
        journalDictationState = JournalDictationState.LISTENING
        setJournalMicStatus(getString(R.string.journal_mic_status_listening))
        updateJournalMicVisual(recording = true)
        voiceDictationService.startListening(object : com.malto4.pipdroid.voice.DictationListener {
            override fun onPartialText(text: String) {
                Log.d("VoiceJournal", "partial: \"$text\"")
                runOnUiThread {
                    setJournalMicStatus(text.ifBlank { getString(R.string.journal_mic_status_listening) })
                }
            }
            override fun onFinalText(text: String) {
                Log.d("VoiceJournal", "final: \"$text\"")
                runOnUiThread { appendJournalDictatedText(text) }
            }
            override fun onError(message: String) {
                Log.d("VoiceJournal", "error: $message")
                runOnUiThread {
                    setJournalMicStatus(getString(R.string.journal_mic_status_error))
                    stopJournalDictation()
                }
            }
        })
    }
    /** Тап 2 по микрофону (и штатное закрытие попапа) — SpeechService.stop() сам отдаёт
     * "хвост" фразы через onFinalResult до того, как метод здесь вернёт управление. */
    private fun stopJournalDictation() {
        if (journalDictationState == JournalDictationState.LISTENING) {
            voiceDictationService.stopListening()
        }
        journalDictationState = JournalDictationState.IDLE
        setJournalMicStatus("")
        updateJournalMicVisual(recording = false)
    }
    /** Общее тело тапа по микрофону (roadmap, этап 27) — раньше жило только в
     * setOnClickListener самой кнопки, теперь общее и для тача, и для ENCBTN (MenuNode.
     * onActivate узла MIC, journalEntryEditorChildrenNodes()) — "кнопки реагируют на
     * ENCBTN как на тач". */
    private fun handleJournalMicTap() {
        when (journalDictationState) {
            JournalDictationState.IDLE -> {
                if (awaitingVoiceCommand) {
                    // VoiceDictationService уже занят распознаванием голосовой команды
                    // после будческого слова (onWakeWordTriggered) — не отбирать его.
                    playErrorAudio()
                    return
                }
                if (!voiceModelRepository.hasModel()) {
                    playErrorAudio()
                    setJournalMicStatus(getString(R.string.journal_mic_status_no_model))
                    return
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION_JOURNAL_DICTATION
                    )
                    return
                }
                startJournalDictation()
            }
            JournalDictationState.LOADING -> { /* повторный тап/ENCBTN во время загрузки модели игнорируется */ }
            JournalDictationState.LISTENING -> stopJournalDictation()
        }
    }
    /** Индекс записи (со сдвигом на "Новую запись" в начале списка) в journalSidebarItems()/
     * journalChildrenNodes() — общая точка между Save и Delete, чтобы курсор энкодера и
     * подсветка списка попадали на актуальную позицию (roadmap, этап 27, п.3/4). Fallback 0
     * — теоретический, entryId всегда должен находиться в списке сразу после сохранения в
     * repository. */
    private fun journalEntrySidebarIndex(entryId: String): Int {
        val sorted = journalEntries.sortedByDescending { it.createdAtEpochMillis }
        val index = sorted.indexOfFirst { it.id == entryId }
        return if (index >= 0) index + 1 else 0
    }
    /** Путь от узла JOURNAL до Mic/Cancel/Save текущего редактора (roadmap, доработка после
     * фидбека — тач-обработчикам кнопок Mic/Cancel/Save нужен полный путь для
     * syncJournalEncoderPath()/syncJournalEncoderPathSilently(), не menuNavigator.syncCursor()
     * — тот же класс бага, что и у списка записей: работал, только если энкодер уже стоял
     * ровно на этом же редакторе, см. doc у syncJournalEncoderPath()). "Новая запись" — сразу
     * дети Mic/Cancel/Save на первом уровне (см. journalChildrenNodes()); правка существующей
     * — на уровень глубже, под общим для всех записей узлом EDIT (см.
     * journalEntryDetailChildrenNodes()). Читать editingJournalEntryId нужно ДО того, как
     * Cancel/Save его сбросят. */
    private fun journalEditorPathPrefix(): List<Int> {
        val editingId = editingJournalEntryId
        return if (editingId != null) listOf(journalEntrySidebarIndex(editingId), 0) else listOf(0)
    }
    /** Cancel — общее тело для тача и ENCBTN (roadmap, этап 27, п.3/4). Данные не меняет,
     * только закрывает редактор и поднимает курсор энкодера: для новой записи — на список
     * (боковое меню, один popLevel() — у "Новая запись" дети сразу Mic/Cancel/Save, без
     * промежуточного уровня, см. journalChildrenNodes()), для правки существующей — на
     * карточку записи (второй popLevel() — там между списком и Mic/Cancel/Save есть ещё
     * уровень Edit/Delete/Back, см. journalEntryDetailChildrenNodes()). */
    private fun performJournalEntryCancel() {
        val wasEditing = editingJournalEntryId != null
        hideJournalEntryEditor()
        menuNavigator.popLevel()
        if (wasEditing) menuNavigator.popLevel()
    }
    /** Save — общее тело для тача и ENCBTN. Курсор энкодера всегда приземляется на уровень
     * списка, на карточку сохранённой записи (roadmap, этап 27 — "на карточку созданной
     * записи"; то же самое и для правки существующей — её текст в уже построенных MenuNode-
     * замыканиях иначе остался бы устаревшим, дерево пересобирается в любом случае). */
    private fun performJournalEntrySave() {
        val popup = bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup
        val text = popup.etJournalEntryValue.text.toString()
        if (text.isBlank()) {
            playErrorAudio()
            return
        }
        val editingId = editingJournalEntryId
        val savedEntryId: String
        if (editingId != null) {
            val existing = journalEntries.find { it.id == editingId }
            if (existing != null) {
                val updated = existing.copy(text = text, updatedAtEpochMillis = System.currentTimeMillis())
                journalEntries[journalEntries.indexOf(existing)] = updated
                journalRepository.update(updated)
            }
            savedEntryId = editingId
        } else {
            val entry = JournalEntry(UUID.randomUUID().toString(), text, System.currentTimeMillis())
            journalRepository.add(entry)
            journalEntries.add(entry)
            savedEntryId = entry.id
        }
        hideJournalEntryEditor()
        menuNavigator.popLevel()
        if (editingId != null) menuNavigator.popLevel()
        val index = journalEntrySidebarIndex(savedEntryId)
        bindJournalListAdapter(initialSelectedPosition = index)
        menuNavigator.replaceChildrenOf("JOURNAL", journalChildrenNodes(), cursor = index)
    }
    /** Delete — общее тело для тача и ENCBTN, без подтверждения (roadmap, этап 27, п.4 — то
     * же поведение, что уже было у тача до энкодера). Курсор энкодера возвращается в боковое
     * меню (список записей) — узел записи, внутри которого он был, больше не существует. */
    private fun performJournalEntryDelete(entry: JournalEntry) {
        journalRepository.delete(entry.id)
        journalEntries.removeAll { it.id == entry.id }
        hideJournalEntryDetail()
        bindJournalListAdapter()
        menuNavigator.popLevel()
        menuNavigator.replaceChildrenOf("JOURNAL", journalChildrenNodes())
    }
    /** Пеший маршрут до точки/отметки (PedestrianRouter, A* по графу дорог из бандла) — с
     * текущей GPS-позиции. Расчёт на Dispatchers.Default — граф может быть на пару тысяч
     * узлов, не блокировать UI-поток. Успешный расчёт переводит панель управления маршрутом
     * (бэклог этапа 18) в состояние BUILT — ждёт [Start]/[Cancel]. */
    /** [returnPath] — куда вернуть курсор энкодера после Cancel/Stop на построенном
     * маршруте (roadmap, доработка после фидбека): узел, с которого реально начиналось
     * построение — "Управление картой" (тап по карте/крестик в ROOT-режиме), "Проложить
     * маршрут" (через "До точки на карте"/"До отметки") или "Список меток" (через карточку
     * отметки из корня). Каждый вызывающий код передаёт его явно — сам routeTo() не может
     * надёжно восстановить контекст ПОСТФАКТУМ (вызов асинхронный, к моменту завершения
     * mapControlMode/mapMenuListReturnState могли уже относиться к чему-то другому).
     * По умолчанию — "Управление картой" (запасной вариант для мест, которым конкретный
     * узел взять неоткуда, напр. голосовая команда "маршрут до..."). */
    private fun routeTo(destLat: Double, destLon: Double, returnPath: List<Int> = listOf(mapRootIndex("MAP_CONTROLS"))) {
        val router = pedestrianRouter
        val geoReference = mapGeoReference
        if (router == null || geoReference == null) {
            Log.w("MainActivity", "routeTo() без графа дорог/geoReference — бандл без map_roads.json?")
            return
        }
        val start = currentLocationOrNull()
        if (start == null) {
            Log.d("MainActivity", "routeTo() — GPS ещё не дал фикс")
            showMapHint(getString(R.string.map_hint_waiting_gps))
            return
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val path = router.route(start.latitude, start.longitude, destLat, destLon)
            withContext(Dispatchers.Main) {
                if (path == null) {
                    showMapHint(getString(R.string.map_hint_no_route))
                    return@withContext
                }
                hideMapHint()
                mapRouteDestination = destLat to destLon
                applyRoutePath(geoReference, path)
                mapRouteState = MapRouteState.BUILT
                // Сайдбар уходит в ROOT по факту построения маршрута (не сразу по выбору
                // способа/цели, см. mapRouteSubmenuMeta/bindMarkerListAdapter) — единая точка,
                // откуда бы ни был вызван routeTo().
                showMapMenuState(MapMenuState.ROOT)
                // Курсор энкодера мог быть на любой глубине внутри MAP или вообще не там —
                // безусловно ставим его на [returnPath] (узел, с которого реально начиналось
                // построение — см. doc у routeTo()), раз сайдбар сам уже прыгнул в ROOT
                // визуально, и сразу проваливаем на панель Start/Cancel
                // (mapRouteControlsChildrenNodes(), roadmap — доработка после фидбека, п.2:
                // "курсор перемещается на кнопку Start", п.1 после этого — "Cancel/Stop
                // должны возвращать туда, откуда начали"). Молча
                // (syncMapEncoderPathSilently, не syncMapEncoderPath) — если [returnPath]
                // когда-нибудь совпадёт с самим узлом MAP, его onHighlight ("=" повторный
                // клик по вкладке карты) заново открыл бы экран и стёр маршрут (см.
                // MenuNavigator.setPathSilently()).
                syncMapEncoderPathSilently(returnPath)
                menuNavigator.pushLevel(mapRouteControlsChildrenNodes(), tag = "MAP_ROUTE_CONTROLS")
                updateRouteControlsVisibility()
                // Отложено до реального layout-прохода панели управления маршрутом (post{}) —
                // тот же приём, что и у центрирования на отметке из "Список меток":
                // mapBottomOverlayHeightPx() внутри fitMapToRoute() должна читать уже
                // актуальную высоту панели, а не 0 от ещё не отмеренного GONE->VISIBLE.
                bindingMain.incLayoutTabItemsMap.layoutMapRouteControls.post {
                    fitMapToRoute(path, destLat, destLon)
                }
            }
        }
    }
    /** Записывает построенный/перестроенный путь и в лат/лон (для haversine-расчётов), и в
     * пиксели битмапа (для отрисовки, см. MapOverlayView.routePx). */
    private fun applyRoutePath(geoReference: GeoReference, path: List<Pair<Double, Double>>) {
        mapRouteLatLonPath = path
        bindingMain.incLayoutTabItemsMap.viewMapOverlay.routePx =
            path.map { (lat, lon) -> geoReference.latLonToPixel(lat, lon) }
    }
    /** Центрирует и масштабирует карту так, чтобы весь построенный маршрут (старт + все
     * промежуточные точки + пункт назначения) попал в видимую область — раньше маршрут молча
     * строился за пределами экрана (фидбек по итогам тестирования этапа 27), игрок видел
     * только тот кусок карты, где был до этого. В отличие от centerMapOnBitmapPoint() (только
     * пан на постоянном зуме) тут меняется и зум — суппматрица PhotoView пересчитывается с
     * нуля, а не двигается дельтой от текущей.
     *
     * suppMatrix библиотеки работает в ЭКРАННОМ пространстве поверх базовой "впис. в экран"
     * матрицы (drawMatrix = suppMatrix * baseMatrix, см. PhotoViewAttacher.getDrawMatrix()/
     * комментарий в centerMapOnBitmapPoint()) — готового геттера базовой матрицы в паблик API
     * нет, поэтому она выводится трюком: подставить suppMatrix=identity, снять
     * получившийся drawMatrix (это и есть база), тут же поставить обратно. Дальше обычная
     * алгебра матриц: зная желаемую АБСОЛЮТНУЮ drawMatrix (bitmap-px -> screen-px, ровно то
     * же пространство, что и everywhere else in this file, е.g. GeoReference.latLonToPixel +
     * matrix.mapPoints), новая suppMatrix = targetDrawMatrix * baseMatrix^-1. */
    private fun fitMapToRoute(path: List<Pair<Double, Double>>, destLat: Double, destLon: Double) {
        val geoReference = mapGeoReference ?: return
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        if (photoView.width == 0 || photoView.height == 0) return
        val points = path.map { (lat, lon) -> geoReference.latLonToPixel(lat, lon) } +
            geoReference.latLonToPixel(destLat, destLon)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
        }
        val bboxWidth = (maxX - minX).coerceAtLeast(1f)
        val bboxHeight = (maxY - minY).coerceAtLeast(1f)
        val bboxCenterX = (minX + maxX) / 2f
        val bboxCenterY = (minY + maxY) / 2f
        val paddingPx = resources.displayMetrics.density * MAP_ROUTE_FIT_PADDING_DP
        val availableWidth = (photoView.width - paddingPx * 2f).coerceAtLeast(1f)
        val availableHeight = (photoView.height - mapBottomOverlayHeightPx() - paddingPx * 2f).coerceAtLeast(1f)
        // Абсолютный масштаб (bitmap-px -> screen-px) нужно перевести в единицы photoView.scale
        // (множитель НАД базовой "впис. в экран" матрицей), чтобы клэмпить в поддерживаемый
        // библиотекой диапазон [minimumScale, maximumScale] — иначе setDisplayMatrix() продавит
        // масштаб, который checkMatrixBounds() тут же попытается скорректировать по-своему.
        val baseMatrix = Matrix()
        run {
            val savedSupp = Matrix()
            photoView.getSuppMatrix(savedSupp)
            photoView.setDisplayMatrix(Matrix())
            photoView.getDisplayMatrix(baseMatrix)
            photoView.setDisplayMatrix(savedSupp)
        }
        val baseMatrixValues = FloatArray(9)
        baseMatrix.getValues(baseMatrixValues)
        val baseScale = baseMatrixValues[Matrix.MSCALE_X]
        if (baseScale <= 0f) return
        val requiredAbsoluteScale = minOf(availableWidth / bboxWidth, availableHeight / bboxHeight)
        val relativeScale = (requiredAbsoluteScale / baseScale).coerceIn(photoView.minimumScale, photoView.maximumScale)
        val finalAbsoluteScale = relativeScale * baseScale
        val targetMatrix = Matrix()
        targetMatrix.setScale(finalAbsoluteScale, finalAbsoluteScale)
        val desiredCenterX = photoView.width / 2f
        val desiredCenterY = (photoView.height - mapBottomOverlayHeightPx()) / 2f
        targetMatrix.postTranslate(desiredCenterX - bboxCenterX * finalAbsoluteScale, desiredCenterY - bboxCenterY * finalAbsoluteScale)
        val baseInverse = Matrix()
        if (!baseMatrix.invert(baseInverse)) return
        val newSuppMatrix = Matrix(targetMatrix)
        newSuppMatrix.preConcat(baseInverse)
        photoView.setDisplayMatrix(newSuppMatrix)
    }
    /** [Cancel] на построенном маршруте и [Stop] на активном следовании — оба полностью
     * сбрасывают маршрут (бэклог этапа 18: "тоже сбрасывает"), а не просто ставят на паузу. */
    private fun cancelActiveRoute() {
        mapRouteState = MapRouteState.NONE
        mapRouteDestination = null
        mapRouteLatLonPath = emptyList()
        bindingMain.incLayoutTabItemsMap.viewMapOverlay.routePx = emptyList()
        updateRouteControlsVisibility()
    }
    /** Единая точка правды для панели управления маршрутом (layout_map_route_controls) —
     * вызывается и после построения/сброса маршрута, и при закрытии карточки деталей
     * отметки/попапа выбора [Route]/[Marker], которые временно перекрывают тот же нижний
     * слот карты. Если один из них всё ещё открыт — ничего не делает, тот сам восстановит
     * панель маршрута при закрытии. */
    private fun updateRouteControlsVisibility() {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        if (selectedMarkerForDetail != null || pendingTapChoiceLatLon != null) return
        if (mapRouteState == MapRouteState.NONE) {
            mapScreen.layoutMapRouteControls.visibility = View.GONE
            return
        }
        val isActive = mapRouteState == MapRouteState.ACTIVE
        mapScreen.btnMapRouteStart.visibility = if (isActive) View.GONE else View.VISIBLE
        mapScreen.btnMapRouteCancel.visibility = if (isActive) View.GONE else View.VISIBLE
        mapScreen.btnMapRouteStop.visibility = if (isActive) View.VISIBLE else View.GONE
        mapScreen.tvMapRouteStatus.visibility = if (isActive) View.VISIBLE else View.GONE
        mapScreen.layoutMapRouteControls.visibility = View.VISIBLE
    }
    /** Режим следования по маршруту (бэклог этапа 18, [Start]) — вызывается из
     * onMapLocationUpdate() на каждый GPS-фикс, пока mapRouteState == ACTIVE: обновляет
     * остаток дистанции текстом в панели маршрута и перестраивает маршрут при отклонении от
     * него дальше MAP_ROUTE_REROUTE_THRESHOLD_M. Голосовые подсказки по поворотам не нужны
     * (roadmap, бэклог этапа 18). */
    private fun updateActiveNavigation(location: Location) {
        val destination = mapRouteDestination ?: return
        val path = mapRouteLatLonPath
        if (path.isEmpty()) return
        // Ближайшая вершина графа дорог к игроку — не полноценная проекция на отрезок, но
        // достаточное приближение для масштаба полигона (roadmap, "Реальная карта —
        // находки"), используется и для остатка дистанции, и для порога перестроения.
        var nearestIndex = 0
        var nearestDist = Double.MAX_VALUE
        path.forEachIndexed { index, (lat, lon) ->
            val dist = GeoReference.haversineMeters(location.latitude, location.longitude, lat, lon)
            if (dist < nearestDist) {
                nearestDist = dist
                nearestIndex = index
            }
        }
        if (nearestDist > MAP_ROUTE_REROUTE_THRESHOLD_M) {
            rerouteActiveNavigation(location, destination)
            return
        }
        var remainingMeters = nearestDist
        for (i in nearestIndex until path.size - 1) {
            val (lat1, lon1) = path[i]
            val (lat2, lon2) = path[i + 1]
            remainingMeters += GeoReference.haversineMeters(lat1, lon1, lat2, lon2)
        }
        bindingMain.incLayoutTabItemsMap.tvMapRouteStatus.text = formatRouteDistance(remainingMeters)
    }
    private fun rerouteActiveNavigation(location: Location, destination: Pair<Double, Double>) {
        val router = pedestrianRouter ?: return
        val geoReference = mapGeoReference ?: return
        lifecycleScope.launch(Dispatchers.Default) {
            val path = router.route(location.latitude, location.longitude, destination.first, destination.second)
            withContext(Dispatchers.Main) {
                // Следование могло быть остановлено, пока считался маршрут — не оживлять его.
                if (path == null || mapRouteState != MapRouteState.ACTIVE) return@withContext
                applyRoutePath(geoReference, path)
            }
        }
    }
    private fun formatRouteDistance(meters: Double): String {
        val unit = if (meters >= 1000) getString(R.string.map_route_unit_km, meters / 1000.0)
        else getString(R.string.map_route_unit_meters, meters.roundToInt())
        return getString(R.string.map_route_status_remaining, unit)
    }
    /** Обновляет статус-строку в Settings > Map — импортирован ли бандл, когда и из какой
     * папки. Вызывается один раз при инициализации Settings и сразу после (не)успешного
     * импорта (раздел теперь всегда на месте, не отдельная подпанель по кнопке). */
    private fun refreshMapBundleStatus() {
        val statusView = bindingMain.incLayoutSettingsGlobal.tvMapBundleStatus
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

    /** Обновляет статус-строку в Settings > Voice Commands — импортирована ли модель Vosk,
     * когда и из какого файла. Вызывается один раз при инициализации Settings и сразу после
     * (не)успешного импорта (раздел теперь всегда на месте, не отдельная подпанель по
     * кнопке). */
    private fun refreshVoiceModelStatus() {
        val statusView = bindingMain.incLayoutSettingsGlobal.tvVoiceModelStatus
        if (!voiceModelRepository.hasModel()) {
            statusView.text = getString(R.string.voice_model_status_none)
            return
        }
        val dateText = voiceModelRepository.importedAtEpochMillis()?.let {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))
        } ?: "?"
        val fileName = voiceModelRepository.importedSourceFileName() ?: "?"
        statusView.text = getString(R.string.voice_model_status_imported, fileName, dateText)
    }

    /***********************************************************************************************************
     * INTERFACE CHANGES
     **********************************************************************************************************/
    /**
     * Блик (img_screenglare) должен быть скрыт на "выключенных" состояниях — обычном
     * чёрном OFF (view_power_off) и на шаге мастера POWER_HINT (тоже концептуально
     * выключенный экран, просто с подсказкой нажать POWER) — иначе бледный оверлей
     * просвечивает поверх сплошного чёрного. Вызывается в каждом месте, которое переключает
     * эти состояния, а не через один общий слушатель — так же, как остальная логика этих
     * состояний в этом файле устроена явными вызовами, а не наблюдателями.
     */
    private fun updateScreenGlareVisibility() {
        val isOff = bindingMain.viewPowerOff.visibility == View.VISIBLE ||
            bindingMain.incLayoutPipboy2000Wizard.layoutWizardPowerHint.visibility == View.VISIBLE
        bindingMain.imgScreenglare.visibility = if (isOff) View.GONE else View.VISIBLE
    }
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
        updateScreenGlareVisibility()
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
            val wizardWasOpen = bindingMain.incLayoutPipboy2000Wizard.root.visibility == View.VISIBLE
            bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
            // Мастер реально закрывается впервые — вернуть кнопки шапки/футера и свайп, см.
            // finishPhoneModeSetup()/комментарий в selectPipBoyMode() (roadmap, этап 28).
            // Гейт по wizardWasOpen — на обычных POWER:1/POWER:0 переключениях в игре (мастер
            // уже закрыт) вызывать нечего, кнопки и так уже включены.
            if (wizardWasOpen) {
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
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
        updateScreenGlareVisibility()
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
        // Первый POWER:1 за сессию в режиме PipBoy 2000/3000 (roadmap, этап 27 — находка
        // "нет строки 2 после POWER"): в отличие от finishPhoneModeSetup(), мастер этого
        // режима никогда не проходит через menuChangeBLE()/resetToRoot() до этого момента —
        // row2Items/row2Views и стек MenuNavigator остаются пустыми, пока не
        // проинициализировать явно здесь. row2Views.isEmpty() — сигнал "ещё ни разу за эту
        // сессию", повторные POWER:1 (row2Views уже не пуст) это пропускают, иначе каждое
        // выключение/включение сбрасывало бы курсор энкодера туда, куда игрок успел
        // добраться до выключения.
        if (row2Views.isEmpty()) {
            menuChangeBLE(curMenu)
            menuNavigator.resetToRoot(menuRootNodesFor(curMenu))
            // Стартовое положение курсора энкодера (roadmap, этап 27 — доработка энкодер-
            // эргономики) — первый дочерний узел бокового меню, не сам узел строки 2, тот
            // же приём, что у finishPhoneModeSetup().
            menuNavigator.activateSelected()
        }
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
        updateScreenGlareVisibility()
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
     * Деревья меню для энкодера (roadmap, "Модель навигации энкодером"). [MenuNode.onHighlight]
     * у каждого узла — либо `performClick()` на уже существующей touch-кнопке этого экрана,
     * либо (после roadmap "Единый компонент бокового меню 3 уровня") прямой вызов
     * `SidebarMenuAdapter.selectPosition(index)` для экранов, у которых пункты меню — это
     * теперь общий компонент, не отдельные кнопки — в обоих случаях гарантирует, что
     * энкодер ведёт себя ровно так же, как палец по экрану в режиме телефона. Должен быть
     * безопасен на каждое перемещение курсора — реальные одноразовые действия (запуск
     * таймера, редактирование значения) идут через `onActivate`/`valueEditor`, не отсюда
     * (roadmap, этап 27 — энкодер-эргономика, находки "Status"/"SPECIAL/Skills").
     *
     * STATS — секция с реальной вложенностью: Status -> LIGHT/HEAVY/STUNNED (лист с
     * `onActivate` — запуск таймера ранения только по `ENCBTN`, движение курсора молча
     * переставляет рамку через `setSelectedPositionSilently`), SPECIAL -> 7 характеристик,
     * Skills -> 13 навыков (листья с `valueEditor` — `ENCBTN` переключает `ENC` на дельту
     * значения через кнопки `+`/`-`, повторный `ENCBTN` возвращает к списку).
     *
     * PERKS — исключение: там RecyclerView с динамическим адаптером (SidebarMenuAdapter,
     * но список пересобирается заново при каждом открытии экрана — см. STATSPerksSetup()),
     * не фиксированный набор пунктов, тот же приём "провалиться -> активировать пункт" не
     * подходит напрямую. Пока лист, без вложенности — отдельная задача.
     *
     * DATA/MISC (Files) — та же схема, что у SPECIAL/Skills/Perks: фиксированный список
     * (dataFilesMeta), дети узла — dataFilesChildrenNodes(), см. dataMenuRoot() ниже.
     *
     * Остальные вкладки ITEMS/DATA (Map/Journal/Holotapes) пока плоский список без своего
     * третьего уровня — их структура целиком поменяется на этапе 6 (перестройка IA, см.
     * видение приложения в roadmap), глубже разбирать их сейчас смысла нет.
     */
    private fun statsMenuRoot(): List<MenuNode> {
        // Status (roadmap, этап 27 — находка "Status"): перемещение курсора (onHighlight)
        // только двигает рамку выделения молча, никакого действия. Запуск таймера ранения
        // (statusAdapter.selectPosition -> звук + statusMeta[].action(), см. onSelect
        // колбэк адаптера ниже) требует отдельного подтверждения onActivate = ENCBTN — тач
        // не меняется, у него это по-прежнему один и тот же тап. Дети — statusChildrenNodes()
        // (не инлайн) — тот же список нужен и живьём, при смене woundPhase на лету, не
        // только при свежем входе в STATS, см. refreshStatusEncoderChildren().
        val statusNode = MenuNode(
            id = "STATUS",
            children = statusChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bindingMain.incLayoutTabStatsBottom.btnStatsStatus) }
        )
        // SPECIAL (roadmap, этап 27 — находка "SPECIAL/Skills"): движение курсора по-прежнему
        // просто обновляет превью картинки/описания — onHighlight зовёт setSelectedPositionSilently()
        // + showSpecialPreview() напрямую, не громкий specialAdapter.selectPosition() (roadmap,
        // доработка после фидбека — найденный баг: громкий selectPosition() сам вызывает
        // onSelect адаптера, то есть простое НАВЕДЕНИЕ курсора энкодером срабатывало как
        // ENCBTN, сразу проваливаясь в ValueEditor; тот же приём, что у showClockContentPanel()
        // в onHighlight Clock). ENCBTN на пункте по-прежнему входит в ValueEditor — крутить
        // `ENC` значит слать дельту в те же adjustSelectedSpecial(), что дёргают кнопки +/- по
        // тапу, повторный ENCBTN выходит обратно к списку характеристик (см. MenuNavigator.ValueEditor).
        val specialNode = MenuNode(
            id = "SPECIAL",
            children = specialMeta.mapIndexed { index, meta ->
                MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTickAudio()
                        specialAdapter.setSelectedPositionSilently(index)
                        showSpecialPreview(meta)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta ->
                            val special = bindingMain.incLayoutTabStatsSpecial
                            flashButtonPressImmediate(if (delta > 0) special.btnSpecialIncrease else special.btnSpecialDecrease)
                            adjustSelectedSpecial(delta)
                        },
                        onEnter = {
                            playConfirmAudio()
                            setSpecialValueEditorFocused(true)
                        },
                        onExit = {
                            // playTickAudio(), не playConfirmAudio() — звук выхода
                            // из редактирования должен отличаться от звука входа/нажатия
                            // +/- (roadmap, этап 27).
                            playTickAudio()
                            setSpecialValueEditorFocused(false)
                        },
                    ),
                )
            } + menuBackNode(
                pipBoyMode,
                onHighlight = { specialAdapter.setSelectedPositionSilently(specialMeta.size) },
                onBeforePop = { specialAdapter.flashPressAnimation(specialMeta.size) },
            ),
            onHighlight = { simulateEncoderTabHighlight(bindingMain.incLayoutTabStatsBottom.btnStatsSpecial) }
        )
        // Skills — тот же приём (onHighlight silently + showSkillPreview()), что у specialNode выше.
        val skillsNode = MenuNode(
            id = "SKILLS",
            children = skillsMeta.mapIndexed { index, meta ->
                MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTickAudio()
                        skillsAdapter.setSelectedPositionSilently(index)
                        showSkillPreview(meta)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta ->
                            val skills = bindingMain.incLayoutTabStatsSkills
                            flashButtonPressImmediate(if (delta > 0) skills.btnSkillIncrease else skills.btnSkillDecrease)
                            adjustSelectedSkill(delta)
                        },
                        onEnter = {
                            playConfirmAudio()
                            setSkillValueEditorFocused(true)
                        },
                        onExit = {
                            playTickAudio()
                            setSkillValueEditorFocused(false)
                        },
                    ),
                )
            } + menuBackNode(
                pipBoyMode,
                onHighlight = { skillsAdapter.setSelectedPositionSilently(skillsMeta.size) },
                onBeforePop = { skillsAdapter.flashPressAnimation(skillsMeta.size) },
            ),
            onHighlight = { simulateEncoderTabHighlight(bindingMain.incLayoutTabStatsBottom.btnStatsSkills) }
        )
        val bottom = bindingMain.incLayoutTabStatsBottom
        return listOf(
            statusNode,
            specialNode,
            skillsNode,
            MenuNode(
                id = "PERKS",
                children = perksChildrenNodes(),
                onHighlight = { simulateEncoderTabHighlight(bottom.btnStatsPerks) },
            ),
        )
    }
    /**
     * Безусловная синхронизация курсора энкодера с тачем (roadmap, этап 27 — доработка
     * энкодер-эргономики STATUS/SPECIAL/Skills/Perks/Files, тот же класс бага и то же
     * решение, что у syncMapEncoderPath()/syncClockEncoderPath()): обычный
     * menuNavigator.syncCursor() чинит курсор только ВНУТРИ уже активного уровня — если тач
     * пришёл, пока энкодер стоял на строке нижних кнопок (ещё не провалился в список) или в
     * совсем другой ветке, синхронизировать было нечего, курсор оставался "залипшим". [path]
     * — индексы от детей самого узла [nodeId] (STATUS/SPECIAL/SKILLS/PERKS/MISC), не всего
     * дерева. [loud] — вызывать ли [MenuNode.onHighlight] конечного узла: `true`, если тач
     * сам не применил соответствующий визуальный эффект/звук (курсору просто нужно физически
     * доехать до места, прицел должен появиться там же — см. doc у syncClockEncoderPath());
     * `false` (silently), если вызывающий код это уже сделал сам и повторный вызов дал бы
     * задвоенный звук либо (для узлов с адаптером в onHighlight, см. specialNode/skillsNode/
     * perksChildrenNodes()/dataFilesChildrenNodes()) рекурсию через их же onSelect.
     */
    private fun syncEncoderPath(rootNodes: List<MenuNode>, nodeId: String, path: List<Int>, loud: Boolean = true) {
        val rootIndex = rootNodes.indexOfFirst { it.id == nodeId }
        if (rootIndex == -1) return
        val fullPath = listOf(rootIndex) + path
        if (loud) menuNavigator.setPath(rootNodes, fullPath) else menuNavigator.setPathSilently(rootNodes, fullPath)
    }
    private fun syncStatsEncoderPath(nodeId: String, path: List<Int>) = syncEncoderPath(statsMenuRoot(), nodeId, path, loud = true)
    private fun syncStatsEncoderPathSilently(nodeId: String, path: List<Int>) = syncEncoderPath(statsMenuRoot(), nodeId, path, loud = false)
    /**
     * ITEMS (roadmap, этап 6) — Map (п.2, переехал из DATA/Local Map), Clock (п.3, переехал
     * из списка радиостанций RADIO — был попапом, теперь обычный раздел), Journal (п.4,
     * энкодер-эргономика — этап 27, дети journalChildrenNodes()).
     */
    private fun itemsMenuRoot(): List<MenuNode> {
        val bottom = bindingMain.incLayoutTabItemsBottom
        // Clock — SidebarMenuAdapter, тот же приём, что у SPECIAL/Skills/Status выше.
        // Recipe B (roadmap, этап 27, п.1) — дети clockChildrenNodes().
        val clockNode = MenuNode(
            id = "CLOCK",
            children = clockChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsClock) }
        )
        // GEIGER требует физического корпуса (Wi-Fi-скан на ESP32) — недоступен в режиме
        // Телефон, см. applyModeGating(). Порядок должен совпадать с itemsRow2Items() ниже.
        // Дети — geigerChildrenNodes() (roadmap, этап 27 — энкодер-эргономика ITEMS): Reset
        // (+ Menu только в PipBoy 2000), тот же приём прицела-уголков, что у STOP в STATUS.
        val geigerNode = MenuNode(
            id = "GEIGER",
            children = geigerChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsGeiger) },
        )
        // Journal — дети journalChildrenNodes() (roadmap, этап 27 — энкодер-эргономика
        // ITEMS: п.2-4), тот же приём "список + Menu в конце", что у MISC/Status.
        // childrenProvider, не статичный children (roadmap — находка "энкодер видит только
        // 2 старых пункта, пока не сохранишь запись") — itemsMenuRoot() строится один раз
        // на вход в ITEMS, до того, как openJournalScreen() успевает подгрузить journalEntries
        // с диска; childrenProvider пересчитывает список записей заново на каждый провал в
        // узел, когда данные уже точно свежие (см. MenuNode.childrenProvider в MenuNavigator.kt).
        val journalNode = MenuNode(
            id = "JOURNAL",
            childrenProvider = { journalChildrenNodes() },
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsJournal) },
        )
        // MAP — дети mapRootChildrenNodes() (roadmap, этап 27 — энкодер-эргономика карты,
        // п.5), childrenProvider не статичный children — тот же приём, что у JOURNAL:
        // markers грузятся асинхронно в openMapScreen(), которую как раз запускает
        // performClick() ниже, до того как курсор реально провалится внутрь.
        val mapNode = MenuNode(
            id = "MAP",
            childrenProvider = { mapRootChildrenNodes() },
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsMap) },
        )
        return listOfNotNull(
            if (pipBoyMode != PipBoyMode.PHONE) geigerNode else null,
            mapNode,
            journalNode,
            clockNode,
        )
    }
    /** Позиция узла с данным id в [itemsMenuRoot] (roadmap, этап 27 — доработка энкодер-
     * эргономики) — GEIGER/MAP/JOURNAL/CLOCK сдвигаются относительно друг друга в зависимости
     * от pipBoyMode (GEIGER скрыт в Phone, см. itemsMenuRoot()), поэтому индекс для
     * [MenuNavigator.setRootCursor] нельзя зашить константой, как у statsMenuRoot() (там
     * состав/порядок фиксирован). */
    private fun itemsRootIndexFor(id: String): Int = itemsMenuRoot().indexOfFirst { it.id == id }
    /** Дети узла JOURNAL (roadmap, этап 27, п.2) — "Новая запись" всегда первым пунктом,
     * дальше все существующие записи (новые сверху), "В меню" — последним пунктом только в
     * PipBoy 2000/3000 (menuBackNode()). Порядок и состав обязаны совпадать с
     * journalSidebarItems() построчно — [MenuNode.onHighlight] каждого пункта дублирует
     * действие тапа по тому же пункту списка (тот же приём, что у dataFilesChildrenNodes()):
     * "Новая запись"/запись сразу показывают соответствующий контент (редактор/карточку) —
     * не только по ENCBTN, а на каждое перемещение курсора, как и везде в списках этого
     * приложения (Files/Perks/Status). */
    private fun journalChildrenNodes(): List<MenuNode> {
        val sortedEntries = journalEntries.sortedByDescending { it.createdAtEpochMillis }
        val newEntryNode = MenuNode(
            id = "JOURNAL_NEW",
            onHighlight = {
                playTickAudio()
                journalListAdapter.setSelectedPositionSilently(0)
                showJournalEntryEditorForNew()
            },
            children = journalEntryEditorChildrenNodes(null),
        )
        val entryNodes = sortedEntries.mapIndexed { index, entry ->
            MenuNode(
                id = "JOURNAL_ENTRY_${entry.id}",
                onHighlight = {
                    playTickAudio()
                    journalListAdapter.setSelectedPositionSilently(index + 1)
                    showJournalEntryDetail(entry)
                },
                children = journalEntryDetailChildrenNodes(entry),
            )
        }
        return listOf(newEntryNode) + entryNodes + menuBackNode(
            pipBoyMode,
            onHighlight = { journalListAdapter.setSelectedPositionSilently(sortedEntries.size + 1) },
            onBeforePop = { journalListAdapter.flashPressAnimation(sortedEntries.size + 1) },
        )
    }
    /** Дети узла конкретной записи Journal (Edit/Delete/Back, roadmap, этап 27, п.4) — тот
     * же приём прицела-уголков, что у Reset/Menu на Гейгере (geigerChildrenNodes()). Edit
     * проваливается ещё на уровень глубже — journalEntryEditorChildrenNodes(), тот же
     * редактор, что и у "Новой записи". Delete/Back — листья без children, ENCBTN на них
     * (activateSelected()) зовёт onActivate напрямую, никуда дальше не проваливаясь. */
    private fun journalEntryDetailChildrenNodes(entry: JournalEntry): List<MenuNode> {
        val journal = bindingMain.incLayoutTabItemsJournal
        return listOfNotNull(
            MenuNode(
                id = "JOURNAL_ENTRY_EDIT",
                onHighlight = {
                    playTickAudio()
                    // Пересобирает и подсказку/детали (её и так уже показывает onHighlight
                    // родительского узла записи выше), и все три прицела — на случай
                    // возврата сюда из редактора по Cancel/Save (см. showJournalEntryDetail()).
                    showJournalEntryDetail(entry)
                    setJournalEntryDetailEditFocused(true)
                },
                children = journalEntryEditorChildrenNodes(entry),
            ),
            MenuNode(
                id = "JOURNAL_ENTRY_DELETE",
                onHighlight = {
                    playTickAudio()
                    setAllJournalEntryDetailFocusesHidden()
                    setJournalEntryDetailDeleteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(journal.btnJournalEntryDetailDelete) {
                        playButtonAudio()
                        performJournalEntryDelete(entry)
                    }
                },
            ),
            // Только режимы с физическим энкодером — не Phone (найденный баг: кнопка была
            // видна и в Phone, где ей вообще нечем пользоваться, см.
            // refreshJournalBackButtonVisibility()), тот же гейт, что у Menu на Гейгере.
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                id = "JOURNAL_ENTRY_BACK",
                onHighlight = {
                    playTickAudio()
                    setAllJournalEntryDetailFocusesHidden()
                    setJournalEntryDetailBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(journal.btnJournalEntryDetailBack) {
                        playConfirmAudio()
                        menuNavigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Дети редактора записи Journal (Mic/Cancel/Save, roadmap, этап 27, п.3-4) — общие и
     * для создания новой записи ([editingEntry] == null), и для правки существующей.
     * onHighlight узла MIC переключает контентную область на редактор (idempotent-безопасно,
     * см. showJournalEntryEditorForNew()/ForEdit()) — при создании это уже сделал onHighlight
     * "Новой записи" на уровне списка, здесь по сути повтор; при правке это первое и
     * единственное место, где редактор реально открывается (уровень выше, EDIT, только
     * показывает карточку и ждёт ENCBTN). Cancel/Delete/Save реагируют на ENCBTN как на тач
     * (roadmap, п.3) — через ту же общую логику, что и сами тач-обработчики кнопок. */
    private fun journalEntryEditorChildrenNodes(editingEntry: JournalEntry?): List<MenuNode> {
        val popup = bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup
        return listOf(
            MenuNode(
                id = "JOURNAL_EDITOR_MIC",
                onHighlight = {
                    playTickAudio()
                    if (editingEntry != null) showJournalEntryEditorForEdit(editingEntry) else showJournalEntryEditorForNew()
                    setAllJournalEntryEditorFocusesHidden()
                    setJournalEntryEditorMicFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnJournalEntryMic) {
                        handleJournalMicTap()
                    }
                },
            ),
            MenuNode(
                id = "JOURNAL_EDITOR_CANCEL",
                onHighlight = {
                    playTickAudio()
                    setAllJournalEntryEditorFocusesHidden()
                    setJournalEntryEditorCancelFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnJournalEntryPopupCancel) {
                        playConfirmAudio()
                        performJournalEntryCancel()
                    }
                },
            ),
            MenuNode(
                id = "JOURNAL_EDITOR_SAVE",
                onHighlight = {
                    playTickAudio()
                    setAllJournalEntryEditorFocusesHidden()
                    setJournalEntryEditorSaveFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnJournalEntryPopupSave) {
                        playButtonAudio()
                        performJournalEntrySave()
                    }
                },
            ),
        )
    }
    /** Дети узла GEIGER (roadmap, этап 27 — энкодер-эргономика ITEMS, п.1-2): Reset всегда
     * первый пункт, Menu ("В меню") — второй, в любом режиме с физическим энкодером (не
     * Phone — тач-кнопка тоже скрыта только в Phone, см. её visibility рядом с
     * setOnClickListener ниже; было сознательно только PipBoy 2000, пересмотрено по фидбеку
     * — см. roadmap). onActivate у Menu поднимает курсор энкодера обратно на уровень ITEMS
     * через тот же menuNavigator.popLevel(), что и "В меню" в SPECIAL/Skills/PERKS/Status/MISC
     * (menuBackNode()) — здесь не через menuBackNode(), т.к. Reset/Menu не элементы
     * SidebarMenuAdapter, а обычные кнопки экрана. */
    private fun geigerChildrenNodes(): List<MenuNode> {
        val geiger = bindingMain.incLayoutTabItemsGeiger
        return listOfNotNull(
            MenuNode(
                id = "RESET",
                onHighlight = {
                    playTickAudio()
                    setGeigerMenuFocused(false)
                    setGeigerResetFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(geiger.btnGeigerReset) {
                        playButtonAudio()
                        resetGeigerDose()
                    }
                },
            ),
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                id = "MENU",
                onHighlight = {
                    playTickAudio()
                    setGeigerResetFocused(false)
                    setGeigerMenuFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(geiger.btnGeigerMenu) {
                        playButtonAudio()
                        setGeigerMenuFocused(false)
                        menuNavigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /***********************************************************************************************************
     * ITEMS - MAP, энкодер (roadmap, этап 27 — энкодер-эргономика карты). Переиспользует уже
     * готовую тач-логику карты (zoomMapBy/recenterMapOnUser/showMapTapChoice/routeTo/
     * bindMarkerListAdapter и т.п., см. блок выше) — здесь только дерево MenuNode и то немногое
     * новое, чего у тача не было вовсе (панорамирование уголками, крестообразный прицел).
     **********************************************************************************************************/
    /** Общая панель Zoom/Center/Pan/Crosshair/Back-стрелка (mapControlChildrenNodes()) живёт в
     * двух разных точках дерева: сам пункт бокового меню "Управление картой" (ROOT — крестик
     * открывает выбор [Route]/[Marker]/[Cancel], тот же тач-путь, что у обычного тапа по
     * пустой точке) и "Проложить маршрут" → "До точки на карте" (ROUTE_TO_POINT — крестик
     * сразу строит маршрут, roadmap п.6). */
    private enum class MapControlMode { ROOT, ROUTE_TO_POINT, PLACE_MARKER }
    /** Геокоордината в центре видимой карты (roadmap, этап 27, п.3) — фиксированная ТОЧКА
     * ЭКРАНА (центр photo_view_map), не мировая координата: карта двигается под ней при
     * пане/зуме, не наоборот, поэтому не latLonToPixel/фиксированный translationX/Y, как у
     * прицела над отметкой (updateMapMarkerFocus()), а инверсия ТЕКУЩЕЙ displayMatrix —
     * пересчитывается заново на каждый вызов. null — карта ещё не готова. */
    private fun mapCrosshairLatLon(): Pair<Double, Double>? {
        val geoReference = mapGeoReference ?: return null
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        val matrix = Matrix()
        photoView.getDisplayMatrix(matrix)
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return null
        val screenCenter = floatArrayOf(photoView.width / 2f, photoView.height / 2f)
        inverse.mapPoints(screenCenter)
        return geoReference.pixelToLatLon(screenCenter[0], screenCenter[1])
    }
    /** Сдвигает видимую область карты на шаг в экранных пикселях (roadmap, этап 27, п.2) —
     * тот же приём (postTranslate по suppMatrix), что и centerMapOnBitmapPoint(), только
     * относительным сдвигом, не абсолютным позиционированием на точку. */
    private fun panMapBy(dxPx: Float, dyPx: Float) {
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        val suppMatrix = Matrix()
        photoView.getSuppMatrix(suppMatrix)
        suppMatrix.postTranslate(dxPx, dyPx)
        photoView.setDisplayMatrix(suppMatrix)
    }
    /** Безусловная синхронизация курсора энкодера с тачем на экране Карты (roadmap,
     * доработка после фидбека — найденный баг "энкодер не следует за тапами между узлами
     * дерева": обычный menuNavigator.syncCursor() чинит только позицию ВНУТРИ уже активного
     * уровня — если тач переключился в совсем другую ветку, с которой энкодер прежде не
     * соприкасался (например, тапнул "Build Route", пока энкодер был внутри "Управление
     * картой"), синхронизировать было нечего, курсор оставался "залипшим"). [path] — индексы
     * от корня детей самого узла MAP (не всего дерева) — единая точка добавляет к нему
     * позицию MAP в itemsMenuRoot() и жёстко ставит курсор через MenuNavigator.setPath(),
     * какой бы веткой энкодер ни занимался раньше. [path] обязан указывать до ПЕРВОГО
     * РЕБЁНКА тапнутого узла, если тот не лист (см. doc у MenuNavigator.setPath()), не на
     * сам тапнутый узел — вызывающий код (тач-обработчик) сам отвечает за этот выбор. */
    private fun syncMapEncoderPath(path: List<Int>) {
        val itemsRoot = itemsMenuRoot()
        val mapIndex = itemsRoot.indexOfFirst { it.id == "MAP" }
        if (mapIndex == -1) return
        menuNavigator.setPath(itemsRoot, listOf(mapIndex) + path)
    }
    /** [syncMapEncoderPath] без вызова onHighlight — для случаев, когда сам узел "MAP" может
     * оказаться конечным в пути: его onHighlight — `btnItemsMap.performClick()`, заново
     * открывающий экран карты (roadmap, доработка после фидбека — см.
     * MenuNavigator.setPathSilently()). Использовать только когда следующим шагом идёт
     * безопасное действие со своим эффектом (напр. menuNavigator.pushLevel() в routeTo()). */
    private fun syncMapEncoderPathSilently(path: List<Int>) {
        val itemsRoot = itemsMenuRoot()
        val mapIndex = itemsRoot.indexOfFirst { it.id == "MAP" }
        if (mapIndex == -1) return
        menuNavigator.setPathSilently(itemsRoot, listOf(mapIndex) + path)
    }
    /** [syncMapEncoderPath] на экране Journal (roadmap, доработка после фидбека — тот же класс
     * бага "энкодер не следует за тапами между узлами дерева", здесь — тач по списку записей
     * Journal, пока энкодер был на строке ITEMS или на дочернем узле ЛЮБОЙ записи, включая ту
     * же самую: обычный menuNavigator.syncCursor("JOURNAL", position) чинит курсор, только
     * если энкодер уже стоит ровно на списке записей — во всех остальных случаях no-op, и
     * курсор "залипает" на прежней записи, прицел не отрисовывается вовсе, пока энкодер не
     * дёрнут вручную). [path] — индексы от детей самого узла JOURNAL, как и везде у
     * syncEncoderPath()/syncMapEncoderPath(). */
    private fun syncJournalEncoderPath(path: List<Int>) {
        val itemsRoot = itemsMenuRoot()
        val journalIndex = itemsRoot.indexOfFirst { it.id == "JOURNAL" }
        if (journalIndex == -1) return
        menuNavigator.setPath(itemsRoot, listOf(journalIndex) + path)
    }
    /** [syncJournalEncoderPath] без onHighlight — для пути, останавливающегося на самом узле
     * JOURNAL ("В меню"): его onHighlight — `btnItemsJournal.performClick()`, заново
     * открывающий экран и перезагружающий записи с диска (см. doc у
     * syncMapEncoderPathSilently()). */
    private fun syncJournalEncoderPathSilently(path: List<Int>) {
        val itemsRoot = itemsMenuRoot()
        val journalIndex = itemsRoot.indexOfFirst { it.id == "JOURNAL" }
        if (journalIndex == -1) return
        menuNavigator.setPathSilently(itemsRoot, listOf(journalIndex) + path)
    }
    /** Позиция пункта бокового меню Map по его ключу — та же логика, что уже строит
     * mapRootChildrenNodes() локально, вынесена наружу для переиспользования в
     * syncMapEncoderPath() из тач-обработчиков. */
    private fun mapRootIndex(key: String): Int = mapRootSidebarItems().indexOfFirst { it.payload == key }
    /** Путь до самого узла "Управление картой"/"До точки на карте"/"Поставить отметку" (не
     * включая его дочерние Zoom/Pan/Center/Crosshair/Back) — зависит от [mapControlMode], та
     * же трактовка, что и в mapControlChildrenNodes()/openOverlayForMode(). ROUTE_TO_POINT на
     * один уровень глубже остальных двух — он сам вложен в MAP_ROUTE (см.
     * mapRouteChildrenNodes(), "До точки на карте" — первый ребёнок). */
    private fun mapControlModeRootPath(): List<Int> = when (mapControlMode) {
        MapControlMode.ROOT -> listOf(mapRootIndex("MAP_CONTROLS"))
        MapControlMode.PLACE_MARKER -> listOf(mapRootIndex("PLACE_MARKER"))
        MapControlMode.ROUTE_TO_POINT -> listOf(mapRootIndex("ROUTE"), 0)
    }
    /** Путь до бокового меню Map (roadmap, доработка после фидбека) — то же самое, куда
     * изначально возвращает "←" по спецификации ("возвращает курсор энкодера в боковое меню
     * Карты"): для ROOT/PLACE_MARKER это ровно mapControlModeRootPath() (они и так прямые
     * дети MAP), а для ROUTE_TO_POINT — на один уровень МЕНЬШЕ (сам узел "Build Route" в
     * боковом меню Map, а не "До точки на карте" внутри него — найденный баг: "←" оттуда
     * останавливался на "До точки на карте", хотя должен был выйти на уровень ВЫШЕ, в
     * боковое меню). */
    private fun mapSidebarRootPathForMode(): List<Int> = when (mapControlMode) {
        MapControlMode.ROUTE_TO_POINT -> listOf(mapRootIndex("ROUTE"))
        else -> mapControlModeRootPath()
    }
    /** Путь до уровня "Список меток"/"До отметки" — общий вход для двух контекстов (roadmap,
     * доработка после фидбека), зеркалит mapMarkerListChildrenNodes()/mapMenuListReturnState. */
    private fun mapMarkerListParentPath(): List<Int> =
        if (mapMenuListReturnState == MapMenuState.ROUTE_SUBMENU) listOf(mapRootIndex("ROUTE"), 1) else listOf(mapRootIndex("MARKER_LIST"))
    /** Путь до попапа ввода имени отметки (Cancel/Save) — два возможных родителя, тот же
     * выбор, что уже делает mapMarkerPopupChildrenNodes()/CROSSHAIR.children в зависимости
     * от [mapControlMode] (roadmap, доработка после фидбека). */
    private fun mapMarkerPopupParentPath(): List<Int> = when (mapControlMode) {
        MapControlMode.PLACE_MARKER -> mapControlModeRootPath() + 0
        else -> listOf(mapRootIndex("MAP_CONTROLS"), 0, 1) // ROOT — через "Place Marker" в панели [Route]/[Marker]/[Cancel]
    }
    /** Показывает/прячет разом всю группу "Управление картой"/"До точки на карте" (roadmap,
     * этап 27, п.1-4): 4 уголка панорамирования с подложками, крестообразный прицел, кнопка
     * "←" с подложкой. Прицелы энкодера (focus_corner_brackets) сюда не входят — они
     * переключаются отдельно, per-узел (см. setAllMapControlFocusesHidden(), вызывается тут
     * же при скрытии — идемпотентная подстраховка). false дополнительно прячет панель выбора
     * [Route]/[Marker]/[Cancel] — крестик мог оставить её открытой (hideMapTapChoice()) и
     * снимает armTapMode(ROUTE_TO_POINT), если панель пряталась в этом режиме. */
    private fun setMapControlOverlayVisible(visible: Boolean) {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        val visibility = if (visible) View.VISIBLE else View.GONE
        listOf(
            mapScreen.btnMapPanUp, mapScreen.viewMapPanUpBg,
            mapScreen.btnMapPanDown, mapScreen.viewMapPanDownBg,
            mapScreen.btnMapPanLeft, mapScreen.viewMapPanLeftBg,
            mapScreen.btnMapPanRight, mapScreen.viewMapPanRightBg,
            mapScreen.viewMapCrosshair,
        ).forEach { it.visibility = visibility }
        if (!visible) {
            setAllMapControlFocusesHidden()
            hideMapTapChoice()
            hideMarkerNamePopup()
            if (mapTapMode == MapTapMode.ROUTE_TO_POINT || mapTapMode == MapTapMode.PLACE_MARKER) armTapMode(MapTapMode.NONE)
        }
        // Кнопка "←" — отдельная видимость (см. refreshMapControlBackButtonVisibility()),
        // не входит в visibility выше: должна прятаться под панель [Route]/[Marker]/[Cancel],
        // а не просто исчезать/появляться синхронно с остальной группой (доработка).
        refreshMapControlBackButtonVisibility()
    }
    /** Кнопка "←" (нижний правый угол) видна, только пока сама панель "Управление картой"/
     * "До точки на карте"/"Поставить отметку" открыта И поверх неё сейчас не висит панель
     * выбора [Route]/[Marker]/[Cancel] (layout_map_tap_choice) — та тоже сидит внизу и
     * перекрывала бы "←" (доработка после фидбека по итогам тестирования: раньше кнопка
     * оставалась поверх панели независимо от z-порядка объявления в XML). Дублируется из
     * showMapTapChoice()/hideMapTapChoice() тоже — не только отсюда. */
    private fun refreshMapControlBackButtonVisibility() {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        val overlayActive = mapScreen.viewMapCrosshair.visibility == View.VISIBLE
        val tapChoiceOpen = mapScreen.layoutMapTapChoice.visibility == View.VISIBLE
        val visible = overlayActive && !tapChoiceOpen
        mapScreen.btnMapControlBack.visibility = if (visible) View.VISIBLE else View.GONE
        mapScreen.viewMapControlBackBg.visibility = if (visible) View.VISIBLE else View.GONE
    }
    /** Дети "Управление картой" (ROOT), "До точки на карте" (ROUTE_TO_POINT) и "Поставить
     * отметку" (PLACE_MARKER) — общая функция (roadmap, этап 27, п.1/5/6, доработка):
     * порядок — Crosshair, Pan-верх/низ, Pan-право/лево, Zoom (пара +/-, один общий прицел —
     * как SPECIAL/Skills), Center ("Моё положение"), Back-стрелка (лист, popLevel()).
     * Crosshair — первый ребёнок, поэтому именно его onHighlight (не родительский пункт в
     * списке-предке) открывает/армит панель — тот же приём "коммит в onHighlight первого
     * элемента", что и у MELODY (clockChildrenNodes()): иначе Back-стрелка (popLevel()
     * поднимает курсор обратно на родительский пункт списка) немедленно открывала бы панель
     * заново. Поведение самого Crosshair зависит от [mode]:
     * - ROOT — провал в mapCrosshairTapChoiceChildrenNodes() (Route/Marker/Cancel)
     * - ROUTE_TO_POINT — лист, onActivate сразу строит маршрут
     * - PLACE_MARKER — провал в mapMarkerPopupChildrenNodes() (Cancel/Save попапа) */
    private fun mapControlChildrenNodes(mode: MapControlMode): List<MenuNode> {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        fun openOverlayForMode() {
            mapControlMode = mode
            setMapControlOverlayVisible(true)
            when (mode) {
                MapControlMode.ROUTE_TO_POINT -> armTapMode(MapTapMode.ROUTE_TO_POINT)
                MapControlMode.PLACE_MARKER -> armTapMode(MapTapMode.PLACE_MARKER)
                MapControlMode.ROOT -> {}
            }
        }
        val crosshairNode = when (mode) {
            MapControlMode.ROOT -> MenuNode(
                id = "MAP_CTRL_CROSSHAIR",
                onHighlight = {
                    playTickAudio()
                    openOverlayForMode()
                    setAllMapControlFocusesHidden()
                    setMapCrosshairFocused(true)
                },
                // Звук подтверждения на любой ENCBTN по прицелу, не только в режиме "До
                // точки на карте" (roadmap, этап 28, доработка после фидбека) — узел всё
                // равно проваливается в children следом, см. MenuNavigator.activateSelected().
                onActivate = { playConfirmAudio() },
                children = mapCrosshairTapChoiceChildrenNodes(),
            )
            MapControlMode.PLACE_MARKER -> MenuNode(
                id = "MAP_CTRL_CROSSHAIR",
                onHighlight = {
                    playTickAudio()
                    openOverlayForMode()
                    setAllMapControlFocusesHidden()
                    setMapCrosshairFocused(true)
                },
                onActivate = { playConfirmAudio() },
                children = mapMarkerPopupChildrenNodes { mapCrosshairLatLon() },
            )
            MapControlMode.ROUTE_TO_POINT -> MenuNode(
                id = "MAP_CTRL_CROSSHAIR",
                onHighlight = {
                    playTickAudio()
                    openOverlayForMode()
                    setAllMapControlFocusesHidden()
                    setMapCrosshairFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.viewMapCrosshair) {
                        val (lat, lon) = mapCrosshairLatLon() ?: return@flashButtonPressThenRun
                        playConfirmAudio()
                        routeTo(lat, lon, listOf(mapRootIndex("ROUTE")))
                    }
                },
            )
        }
        return listOf(
            crosshairNode,
            MenuNode(
                id = "MAP_CTRL_PAN_V",
                onHighlight = {
                    playTickAudio()
                    setAllMapControlFocusesHidden()
                    setMapPanVerticalFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta ->
                        val stepPx = resources.displayMetrics.density * MAP_PAN_STEP_DP
                        playConfirmAudio()
                        flashButtonPressImmediate(if (delta > 0) mapScreen.btnMapPanUp else mapScreen.btnMapPanDown)
                        panMapBy(0f, if (delta > 0) stepPx else -stepPx)
                    },
                    onEnter = { playConfirmAudio() },
                    onExit = { playTickAudio() },
                ),
            ),
            MenuNode(
                id = "MAP_CTRL_PAN_H",
                onHighlight = {
                    playTickAudio()
                    setAllMapControlFocusesHidden()
                    setMapPanHorizontalFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta ->
                        val stepPx = resources.displayMetrics.density * MAP_PAN_STEP_DP
                        playConfirmAudio()
                        flashButtonPressImmediate(if (delta > 0) mapScreen.btnMapPanRight else mapScreen.btnMapPanLeft)
                        // Право = отрицательный dx (тот же знак, что и у "бегунка"
                        // recenterMapOnUser()/centerMapOnBitmapPoint(): чтобы показать
                        // содержимое ПРАВЕЕ, картинка сдвигается ВЛЕВО), см. touch-обработчик
                        // btnMapPanRight/btnMapPanLeft в onCreate() — знак обязан совпадать.
                        panMapBy(if (delta > 0) -stepPx else stepPx, 0f)
                    },
                    onEnter = { playConfirmAudio() },
                    onExit = { playTickAudio() },
                ),
            ),
            MenuNode(
                id = "MAP_CTRL_ZOOM",
                onHighlight = {
                    playTickAudio()
                    setAllMapControlFocusesHidden()
                    setMapZoomFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta ->
                        playConfirmAudio()
                        flashButtonPressImmediate(if (delta > 0) mapScreen.btnMapZoomIn else mapScreen.btnMapZoomOut)
                        zoomMapBy(if (delta > 0) MAP_ZOOM_STEP_FACTOR else 1f / MAP_ZOOM_STEP_FACTOR)
                    },
                    onEnter = { playConfirmAudio() },
                    onExit = { playTickAudio() },
                ),
            ),
            MenuNode(
                id = "MAP_CTRL_CENTER",
                onHighlight = {
                    playTickAudio()
                    setAllMapControlFocusesHidden()
                    setMapCenterFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapCenter) {
                        playConfirmAudio()
                        recenterMapOnUser()
                    }
                },
            ),
            MenuNode(
                id = "MAP_CTRL_BACK",
                onHighlight = {
                    playTickAudio()
                    setAllMapControlFocusesHidden()
                    setMapControlBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapControlBack) {
                        playButtonAudio()
                        setMapControlBackFocused(false)
                        setMapControlOverlayVisible(false)
                        // ROUTE_TO_POINT вложен на уровень глубже ROOT/PLACE_MARKER (сам
                        // узел "До точки на карте" — ребёнок MAP_ROUTE, см.
                        // mapControlModeRootPath()) — один popLevel() поднял бы курсор
                        // только до него самого, а не до бокового меню Map, куда "←" обязан
                        // возвращать по спецификации (roadmap, доработка после фидбека,
                        // найденный баг). Второй popLevel() — до MAP_ROUTE в боковом меню;
                        // showMapMenuState(ROOT) — тот же явный вызов, что и в тач-
                        // обработчике, "До точки на карте" переключил сайдбар на
                        // ROUTE_SUBMENU при входе, само возвращение курсора это не отменяет.
                        menuNavigator.popLevel()
                        if (mode == MapControlMode.ROUTE_TO_POINT) {
                            menuNavigator.popLevel()
                            showMapMenuState(MapMenuState.ROOT)
                        }
                    }
                },
            ),
        )
    }
    /** Дети CROSSHAIR в режиме ROOT (roadmap, этап 27, п.3, доработка) — Route/Marker/
     * Cancel, те же три кнопки и обработчики, что и обычная панель тач-тапа по пустой точке
     * (layout_map_tap_choice/btnMapTapChoiceRoute/_marker/_cancel), каждый со своим прицелом
     * (view_map_tap_choice_*_focus). Панель открывается не в onActivate самого CROSSHAIR (он
     * проваливается в children, onActivate для узла с детьми не вызывается никогда — см.
     * MenuNavigator.activateSelected()), а в onHighlight ПЕРВОГО ребёнка здесь (тот же
     * приём, что у CROSSHAIR/MELODY выше) — ENCBTN на крестике всегда проваливается сюда и
     * сразу подсвечивает Route, что и открывает панель. Route строит маршрут и передаёт
     * курсор на mapRouteControlsChildrenNodes() (Start/Cancel) — см. routeTo(). Marker
     * проваливается в mapMarkerPopupChildrenNodes(). Cancel возвращает курсор на сам
     * крестик (popLevel()) — гасит свой прицел ПЕРЕД этим, не после (см. CLAUDE.md). */
    private fun mapCrosshairTapChoiceChildrenNodes(): List<MenuNode> {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        return listOf(
            MenuNode(
                id = "MAP_CTRL_CROSSHAIR_ROUTE",
                onHighlight = {
                    playTickAudio()
                    // Гасим прицел самого крестика — курсор только что провалился с него
                    // сюда (roadmap, доработка после фидбека, п.2 — найденный баг: прицел
                    // оставался на крестике одновременно с новым на панели).
                    setMapCrosshairFocused(false)
                    mapCrosshairLatLon()?.let { (lat, lon) -> showMapTapChoice(lat, lon) }
                    setAllMapTapChoiceFocusesHidden()
                    setMapTapChoiceRouteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapTapChoiceRoute) {
                        val (lat, lon) = pendingTapChoiceLatLon ?: return@flashButtonPressThenRun
                        playButtonAudio()
                        setMapTapChoiceRouteFocused(false)
                        hideMapTapChoice()
                        // mapCrosshairTapChoiceChildrenNodes() — только режим ROOT
                        // ("Управление картой"), см. mapControlChildrenNodes().
                        routeTo(lat, lon, listOf(mapRootIndex("MAP_CONTROLS")))
                    }
                },
            ),
            MenuNode(
                id = "MAP_CTRL_CROSSHAIR_MARKER",
                onHighlight = {
                    playTickAudio()
                    setAllMapTapChoiceFocusesHidden()
                    setMapTapChoiceMarkerFocused(true)
                },
                children = mapMarkerPopupChildrenNodes { pendingTapChoiceLatLon },
            ),
            MenuNode(
                id = "MAP_CTRL_CROSSHAIR_CANCEL",
                onHighlight = {
                    playTickAudio()
                    setAllMapTapChoiceFocusesHidden()
                    setMapTapChoiceCancelFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapTapChoiceCancel) {
                        playButtonAudio()
                        setMapTapChoiceCancelFocused(false)
                        hideMapTapChoice()
                        menuNavigator.popLevel()
                    }
                },
            ),
        )
    }
    /** Дети popup-а ввода имени отметки (Cancel/Save) — общая функция для двух точек входа
     * (roadmap, доработка после фидбека): выбор "Place Marker" в панели Route/Marker/Cancel
     * (ROOT-режим крестика, координата — pendingTapChoiceLatLon, уже взведена к этому
     * моменту первым/Route-узлом панели выбора) и прямой ENCBTN на крестике в режиме
     * PLACE_MARKER (координата — mapCrosshairLatLon(), свежий геоцентр экрана). Открытие
     * попапа — в onHighlight ПЕРВОГО ребёнка (тот же приём, что у CROSSHAIR/MELODY выше),
     * не у родителя — иначе повторный заход сюда (после Cancel/Save, popLevel()) открывал бы
     * попап заново, пока курсор ещё раз не сдвинулся с него. */
    private fun mapMarkerPopupChildrenNodes(latLonProvider: () -> Pair<Double, Double>?): List<MenuNode> {
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        return listOf(
            MenuNode(
                id = "MAP_MARKER_POPUP_CANCEL",
                onHighlight = {
                    playTickAudio()
                    // Координату читаем ДО hideMapTapChoice() — та сама обнуляет
                    // pendingTapChoiceLatLon (один из двух источников latLonProvider, см.
                    // MAP_CTRL_CROSSHAIR_MARKER выше). hideMapTapChoice() тут нужен, чтобы
                    // панель [Route]/[Marker]/[Cancel] не оставалась висеть под попапом —
                    // тот же баг, что был в исходном тач-обработчике до факторинга (roadmap,
                    // доработка после фидбека): для входа через режим PLACE_MARKER это просто
                    // безопасный no-op, панель там и не была открыта.
                    val latLon = latLonProvider()
                    hideMapTapChoice()
                    latLon?.let { (lat, lon) -> showMarkerNamePopupForNewMarker(lat, lon) }
                    // Гасим прицелы уровней ВЫШЕ — курсор только что провалился сюда либо
                    // прямо с крестика (режим PLACE_MARKER), либо с пункта "Place Marker" на
                    // панели [Route]/[Marker]/[Cancel] (режим ROOT) — какой из двух актуален,
                    // сама эта функция не знает, гасим оба безопасно (roadmap, доработка
                    // после фидбека, п.2 — тот же баг, что и у Route выше).
                    setMapCrosshairFocused(false)
                    setAllMapTapChoiceFocusesHidden()
                    setAllMapMarkerPopupFocusesHidden()
                    setMapMarkerPopupCancelFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnMarkerNamePopupCancel) {
                        playButtonAudio()
                        setMapMarkerPopupCancelFocused(false)
                        performMarkerNamePopupCancel()
                        menuNavigator.popLevel()
                    }
                },
            ),
            MenuNode(
                id = "MAP_MARKER_POPUP_SAVE",
                onHighlight = {
                    playTickAudio()
                    setAllMapMarkerPopupFocusesHidden()
                    setMapMarkerPopupSaveFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnMarkerNamePopupSave) {
                        setMapMarkerPopupSaveFocused(false)
                        playButtonAudio()
                        performMarkerNamePopupSave()
                        menuNavigator.popLevel()
                    }
                },
            ),
        )
    }
    /** Общее тело Cancel/Save попапа переименования/новой отметки — и для тача
     * (btnMarkerNamePopupCancel/Save в onCreate()), и для ENCBTN
     * (mapMarkerPopupChildrenNodes()), тот же приём, что performJournalEntryCancel()/
     * performJournalEntrySave(). */
    private fun performMarkerNamePopupCancel() {
        hideMarkerNamePopup()
    }
    private fun performMarkerNamePopupSave() {
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        val name = popup.etMarkerNameValue.text.toString().ifBlank { getString(R.string.marker_name_popup_heading) }
        val editingId = editingMarkerId
        if (editingId != null) {
            val existing = markers.find { it.id == editingId }
            if (existing != null) {
                val updated = existing.copy(name = name)
                markers[markers.indexOf(existing)] = updated
                markerRepository.update(updated)
                showMarkerDetail(updated)
                bindMarkerListAdapter()
            }
        } else {
            val (lat, lon) = pendingMarkerLatLon ?: return
            val marker = MapMarker(UUID.randomUUID().toString(), name, lat, lon, System.currentTimeMillis())
            markerRepository.add(marker)
            markers.add(marker)
        }
        refreshMarkerPins()
        hideMarkerNamePopup()
    }
    /** Панель управления построенным/активным маршрутом (layout_map_route_controls) —
     * Start/Cancel (маршрут построен, ждёт запуска) либо один Stop (следование активно),
     * тот же выбор состояния, что и у updateRouteControlsVisibility(). Курсор энкодера
     * попадает сюда программным "проваливанием" — menuNavigator.pushLevel() из routeTo(),
     * не через обычный провал по дереву (эта панель не пункт какого-то списка, а плавающая
     * панель, появляющаяся как побочный эффект действия, см. MenuNavigator.pushLevel()).
     * Start пересобирает этот же уровень на месте (replaceTopLevel()) — тот же приём, что
     * refreshClockTimerEncoderChildren()/replaceChildrenOf(), но без родителя. */
    private fun mapRouteControlsChildrenNodes(): List<MenuNode> {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        return if (mapRouteState == MapRouteState.ACTIVE) {
            listOf(
                MenuNode(
                    id = "MAP_ROUTE_CTRL_STOP",
                    onHighlight = {
                        playTickAudio()
                        setAllMapRouteControlsFocusesHidden()
                        setMapRouteStopFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(mapScreen.btnMapRouteStop) {
                            playButtonAudio()
                            setMapRouteStopFocused(false)
                            cancelActiveRoute()
                            menuNavigator.popLevel()
                        }
                    },
                ),
            )
        } else {
            listOf(
                MenuNode(
                    id = "MAP_ROUTE_CTRL_START",
                    onHighlight = {
                        playTickAudio()
                        setAllMapRouteControlsFocusesHidden()
                        setMapRouteStartFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(mapScreen.btnMapRouteStart) {
                            playButtonAudio()
                            mapRouteState = MapRouteState.ACTIVE
                            updateRouteControlsVisibility()
                            menuNavigator.replaceTopLevel(mapRouteControlsChildrenNodes())
                        }
                    },
                ),
                MenuNode(
                    id = "MAP_ROUTE_CTRL_CANCEL",
                    onHighlight = {
                        playTickAudio()
                        setAllMapRouteControlsFocusesHidden()
                        setMapRouteCancelFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(mapScreen.btnMapRouteCancel) {
                            playButtonAudio()
                            setMapRouteCancelFocused(false)
                            cancelActiveRoute()
                            menuNavigator.popLevel()
                        }
                    },
                ),
            )
        }
    }
    /** Дети узла MAP (roadmap, этап 27, п.5) — боковое меню Карты на энкодер: "Управление
     * картой" первым пунктом (только не-Phone, п.1), дальше три существующих пункта
     * mapRootMeta — порядок и гейт обязаны совпадать с ним (тот же приём, что
     * journalChildrenNodes()/journalSidebarItems()). "Проложить маршрут"/"Список меток"
     * НЕ открывают свою панель тут же в onHighlight — тот же приём "коммит на первом
     * ребёнке", что у MAP_CTRL_ZOOM/MELODY выше: иначе Back из их подменю (popLevel() до
     * этого самого уровня) немедленно открывал бы подменю заново. */
    private fun mapRootChildrenNodes(): List<MenuNode> {
        // Позиции ищем в mapRootSidebarItems() (уже отфильтрован по pipBoyMode), не в сыром
        // mapRootMeta — иначе индекс "Управление картой" (если она сейчас скрыта) сдвинул бы
        // все остальные на единицу относительно того, что реально показывает mapRootAdapter.
        val items = mapRootSidebarItems()
        fun indexOf(key: String) = items.indexOfFirst { it.payload == key }
        return listOfNotNull(
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                id = "MAP_CONTROLS",
                onHighlight = {
                    playTickAudio()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("MAP_CONTROLS"))
                },
                children = mapControlChildrenNodes(MapControlMode.ROOT),
            ) else null,
            // "Поставить отметку" — та же панель Crosshair/Pan/Zoom/Center/Back, что
            // "Управление картой" (roadmap, доработка после фидбека, п.5), крестик
            // проваливается прямо в попап ввода имени (mapControlChildrenNodes(PLACE_MARKER)).
            MenuNode(
                id = "MAP_PLACE_MARKER",
                onHighlight = {
                    playTickAudio()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("PLACE_MARKER"))
                },
                children = mapControlChildrenNodes(MapControlMode.PLACE_MARKER),
            ),
            MenuNode(
                id = "MAP_ROUTE",
                onHighlight = {
                    playTickAudio()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("ROUTE"))
                },
                children = mapRouteChildrenNodes(),
            ),
            MenuNode(
                id = "MAP_MARKER_LIST",
                onHighlight = {
                    playTickAudio()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("MARKER_LIST"))
                },
                childrenProvider = { mapMarkerListChildrenNodes(MapMenuState.ROOT) },
            ),
        ) + menuBackNode(
            pipBoyMode,
            onHighlight = { mapRootAdapter.setSelectedPositionSilently(indexOf("BACK")) },
            onBeforePop = { mapRootAdapter.flashPressAnimation(indexOf("BACK")) },
        )
    }
    /** Дети узла MAP_ROUTE (roadmap, этап 27, п.6-7) — "До точки на карте" (та же панель
     * Zoom/Center/Pan/Crosshair/Back, что "Управление картой", крестик сразу строит
     * маршрут), "До отметки" (список меток, выбор сразу строит маршрут), Back (popLevel() +
     * showMapMenuState(ROOT), тот же явный вызов, что у существующего тач-действия "BACK" в
     * mapRouteSubmenuMeta — не полагаемся на побочный эффект onHighlight). Порядок обязан
     * совпадать с mapRouteSubmenuMeta. "До точки на карте" — первый ребёнок, поэтому именно
     * его onHighlight (не MAP_ROUTE выше) показывает подменю (см. комментарий
     * mapRootChildrenNodes()). */
    private fun mapRouteChildrenNodes(): List<MenuNode> {
        return listOf(
            MenuNode(
                id = "MAP_ROUTE_TO_POINT",
                onHighlight = {
                    playTickAudio()
                    showMapMenuState(MapMenuState.ROUTE_SUBMENU)
                    mapRouteSubmenuAdapter.setSelectedPositionSilently(0)
                },
                children = mapControlChildrenNodes(MapControlMode.ROUTE_TO_POINT),
            ),
            MenuNode(
                id = "MAP_ROUTE_TO_MARKER",
                onHighlight = {
                    playTickAudio()
                    mapRouteSubmenuAdapter.setSelectedPositionSilently(1)
                },
                childrenProvider = { mapMarkerListChildrenNodes(MapMenuState.ROUTE_SUBMENU) },
            ),
            MenuNode(
                id = "MAP_ROUTE_BACK",
                onHighlight = {
                    playTickAudio()
                    mapRouteSubmenuAdapter.setSelectedPositionSilently(2)
                },
                onActivate = {
                    mapRouteSubmenuAdapter.flashPressAnimation(2)
                    playConfirmAudio()
                    showMapMenuState(MapMenuState.ROOT)
                    menuNavigator.popLevel()
                },
            ),
        )
    }
    /** Дети MAP_MARKER_LIST (вход из бокового меню Map) и MAP_ROUTE_TO_MARKER (вход из "До
     * отметки") — общая функция (roadmap, этап 27, п.8-9), зеркалит уже существующее
     * ветвление bindMarkerListAdapter(): [returnState] == ROUTE_SUBMENU — выбор отметки
     * сразу строит маршрут (лист, onActivate); иначе — провал в карточку деталей (Edit/
     * Route/Delete/Back, mapMarkerDetailChildrenNodes()). Открытие самой панели списка — в
     * onHighlight ПЕРВОГО узла (реальная отметка либо, если список пуст, сам Back — тот же
     * приём "коммит на первом ребёнке", что у ZOOM/MELODY выше), не в родителе. */
    private fun mapMarkerListChildrenNodes(returnState: MapMenuState): List<MenuNode> {
        fun openListIfFirst(index: Int) {
            if (index != 0) return
            mapMenuListReturnState = returnState
            showMapMenuState(MapMenuState.MARKER_LIST)
        }
        val markerNodes = markers.mapIndexed { index, marker ->
            MenuNode(
                id = "MAP_MARKER_${marker.id}",
                onHighlight = {
                    playTickAudio()
                    openListIfFirst(index)
                    mapMarkerListAdapter.setSelectedPositionSilently(index)
                    if (returnState != MapMenuState.ROUTE_SUBMENU) {
                        showMarkerDetail(marker)
                        // Доработка после фидбека, п.6 — центрирование раньше срабатывало
                        // только по тачу (bindMarkerListAdapter().onSelect), не по ENCBTN/
                        // курсору энкодера.
                        centerMapOnMarkerDeferred(marker)
                    }
                },
                children = if (returnState == MapMenuState.ROUTE_SUBMENU) emptyList() else mapMarkerDetailChildrenNodes(marker),
                onActivate = if (returnState == MapMenuState.ROUTE_SUBMENU) {
                    {
                        mapMarkerListAdapter.flashPressAnimation(index)
                        routeTo(marker.lat, marker.lon, listOf(mapRootIndex("ROUTE")))
                    }
                } else null,
            )
        }
        val backIndex = markers.size
        val backNode = MenuNode(
            id = "MAP_MARKER_LIST_BACK",
            onHighlight = {
                playTickAudio()
                openListIfFirst(backIndex)
                mapMarkerListAdapter.setSelectedPositionSilently(backIndex)
            },
            onActivate = {
                mapMarkerListAdapter.flashPressAnimation(backIndex)
                playConfirmAudio()
                showMapMenuState(returnState)
                menuNavigator.popLevel()
            },
        )
        return markerNodes + backNode
    }
    /** Карточка деталей отметки (roadmap, этап 27, п.9) — Edit/Route/Delete/Back, по образцу
     * journalEntryDetailChildrenNodes(). Edit сознательно БЕЗ children — попап переименования
     * (Cancel/Save/EditText) остаётся touch-only, тот же принцип, что и у клавиатуры вообще
     * в этом проекте (см. CLAUDE.md). Back — только режимы с физическим энкодером. */
    private fun mapMarkerDetailChildrenNodes(marker: MapMarker): List<MenuNode> {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        return listOfNotNull(
            MenuNode(
                id = "MAP_MARKER_EDIT",
                onHighlight = {
                    playTickAudio()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailEditFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapMarkerDetailEdit) {
                        playButtonAudio()
                        showMarkerNamePopupForEdit(marker)
                    }
                },
            ),
            MenuNode(
                id = "MAP_MARKER_ROUTE",
                onHighlight = {
                    playTickAudio()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailRouteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapMarkerDetailRoute) {
                        playButtonAudio()
                        // Гасить свой прицел ПЕРЕД hideMarkerDetail(), не после — иначе он
                        // остаётся "включённым" внутри спрятанной карточки и всплывает
                        // заново, стоит карточке в следующий раз показаться (roadmap,
                        // доработка после фидбека, п.7, тот же приём, что в CLAUDE.md).
                        setMapMarkerDetailRouteFocused(false)
                        // Карточка отметки (в отличие от прямого выбора через "До отметки")
                        // всегда достигается через "Список меток" (см.
                        // mapMarkerListChildrenNodes() — при returnState==ROUTE_SUBMENU
                        // карточка вообще не строится, там прямой лист с routeTo()).
                        routeTo(marker.lat, marker.lon, listOf(mapRootIndex("MARKER_LIST")))
                        hideMarkerDetail()
                    }
                },
            ),
            MenuNode(
                id = "MAP_MARKER_DELETE",
                onHighlight = {
                    playTickAudio()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailDeleteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapMarkerDetailDelete) {
                        playButtonAudio()
                        performMapMarkerDelete(marker)
                    }
                },
            ),
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                id = "MAP_MARKER_BACK",
                onHighlight = {
                    playTickAudio()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapMarkerDetailBack) {
                        playButtonAudio()
                        // Гасить свой прицел ПЕРЕД popLevel(), не после (roadmap, доработка
                        // после фидбека, п.6 — найденный баг, прицел оставался висеть на
                        // кнопке после возврата в список; см. общий приём в CLAUDE.md).
                        setMapMarkerDetailBackFocused(false)
                        menuNavigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Удаление отметки — общая точка и для тача (btnMapMarkerDetailDelete), и для энкодера
     * (MAP_MARKER_DELETE.onActivate), по образцу performJournalEntryDelete(): курсор
     * энкодера, если он сейчас на карточке этой отметки, поднимается на уровень списка
     * (menuNavigator.popLevel() всегда безопасен для вызова из onActivate — по определению
     * вызывается, только когда энкодер уже там, см. MenuNavigator.activateSelected()), затем
     * список пересобирается без удалённой записи — replaceChildrenOf() сам no-op на том
     * родителе, что не совпадает с текущим (безопасно звать оба варианта родителя). */
    private fun performMapMarkerDelete(marker: MapMarker) {
        // Тот же приём, что у Route выше — гасить прицелы карточки ДО того, как она
        // скрывается/исчезает вместе с удалённой отметкой (roadmap, доработка после
        // фидбека, п.7).
        setAllMapMarkerDetailFocusesHidden()
        markerRepository.delete(marker.id)
        markers.removeAll { it.id == marker.id }
        refreshMarkerPins()
        hideMarkerDetail()
        bindMarkerListAdapter()
        menuNavigator.popLevel()
        menuNavigator.replaceChildrenOf("MAP_MARKER_LIST", mapMarkerListChildrenNodes(MapMenuState.ROOT))
        menuNavigator.replaceChildrenOf("MAP_ROUTE_TO_MARKER", mapMarkerListChildrenNodes(MapMenuState.ROUTE_SUBMENU))
    }
    private fun dataMenuRoot(): List<MenuNode> {
        val bottom = bindingMain.incLayoutTabDataBottom
        // HOLOTAPES требует физического корпуса (USB Host на ESP32-S3) — недоступен в режиме
        // Телефон, см. applyModeGating(). Порядок должен совпадать с dataRow2Items() ниже.
        // MISC (Files) — единственный из двух с реальным третьим уровнем (dataFilesChildrenNodes()),
        // тот же приём, что у STATUS/SPECIAL/SKILLS/PERKS в statsMenuRoot().
        return listOfNotNull(
            MenuNode(
                id = "MISC",
                children = dataFilesChildrenNodes(),
                onHighlight = { simulateEncoderTabHighlight(bottom.btnDataMisc) },
            ),
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode("HOLOTAPES") { bottom.btnDataHolotapes.performClick() } else null,
        )
    }
    /** syncStatsEncoderPath()/syncStatsEncoderPathSilently(), только для дерева DATA (см.
     * doc у syncEncoderPath()) — используется у "Files" (MISC), тот же приём, что у Perks. */
    private fun syncDataEncoderPath(nodeId: String, path: List<Int>) = syncEncoderPath(dataMenuRoot(), nodeId, path, loud = true)
    private fun syncDataEncoderPathSilently(nodeId: String, path: List<Int>) = syncEncoderPath(dataMenuRoot(), nodeId, path, loud = false)
    /**
     * RADIO — top-level раздел без второго уровня (roadmap, "Новая шапка + единый
     * Settings", п.4/таблица второго уровня) — корень дерева состоит из одного листа,
     * чтобы `ENCBTN`/`ENC` на этом разделе хотя бы не падали, а не потому что там
     * реально есть навигация вглубь.
     */
    private fun radioMenuRoot(): List<MenuNode> {
        return listOf(MenuNode("RADIO") { })
    }
    /** "STATS"/"ITEMS"/"DATA"/"RADIO" -> корень дерева энкодера этого раздела — общая точка
     * между restoreAppState() и finishBootSequence() (roadmap, этап 27 — находка "нет
     * строки 2 после POWER"), чтобы не держать один и тот же when в двух местах. */
    private fun menuRootNodesFor(menu: String): List<MenuNode> = when (menu) {
        "ITEMS" -> itemsMenuRoot()
        "DATA" -> dataMenuRoot()
        "RADIO" -> radioMenuRoot()
        else -> statsMenuRoot()
    }
    /**
     * RADIOPWR (roadmap, этап 23; протокол, раздел 3.2) — источник истины физический тумблер
     * на ESP32, не приложение. `on=true` дополнительно переключает экран (протокол требует
     * увести игрока на RADIO при физическом включении радио) и командует ESP32 настроиться
     * на волну (энкодер тюнинга даёт только дельты, у RDA5807M нет своей памяти "последней
     * волны" — телефон явно шлёт `RADIOFREQ:<...>`, см. RADIO_FREQUENCY_DEFAULT в companion
     * object). `on=false` — **только** статус-строка, экран не меняется ("неизвестно, куда
     * игрок хочет перейти дальше").
     */
    private fun applyRadioPowerState(on: Boolean) {
        if (on) {
            menuChangeBLE("RADIO")
            menuNavigator.resetToRoot(radioMenuRoot())
            val freq = sharedPreferences.getInt(radioLastFrequency_SPKey, RADIO_FREQUENCY_DEFAULT)
            updateRadioFrequencyDisplay(freq)
            sendBLEText("RADIOFREQ:$freq")
        }
        bindingMain.incLayoutTabDataRadio.tvRadioStatus.setText(
            if (on) R.string.radio_status_on else R.string.radio_status_off
        )
    }
    /**
     * RADIOFREQ (протокол, раздел 3.2/3.3) — абсолютное значение, МГц×10. Приходит либо как
     * подтверждение от ESP32 (тюнинг физически происходит на самом ESP32 — второй энкодер
     * крутит RDA5807M напрямую по I2C, `RADIOTUNE:±N` из протокола чисто информационная и
     * телефоном не обрабатывается), либо выставляется самим applyRadioPowerState() при
     * включении радио. Persist в SharedPreferences в любом случае — это и есть "последняя
     * волна, на которой слушали" для следующего RADIOPWR:1.
     */
    private fun updateRadioFrequencyDisplay(freqTenthsOfMHz: Int) {
        sharedPreferences.edit().putInt(radioLastFrequency_SPKey, freqTenthsOfMHz).apply()
        bindingMain.incLayoutTabDataRadio.tvRadioFrequency.text =
            String.format(Locale.US, "%.1f MHz", freqTenthsOfMHz / 10f)
    }
    /**
     * VOLUME:±N (протокол, раздел 3.3) — только дельты со второго энкодера ESP32, без
     * абсолютного подтверждения (см. RADIO_VOLUME_* в companion object) — radioVolume существует
     * только для шкалы на этом экране, не переживает перезапуск приложения и не является
     * авторитетным значением громкости.
     */
    private fun applyRadioVolumeDelta(delta: Int) {
        playConfirmAudio()
        radioVolume = (radioVolume + delta).coerceIn(RADIO_VOLUME_MIN, RADIO_VOLUME_MAX)
        updateRadioVolumeDisplay()
    }
    private fun updateRadioVolumeDisplay() {
        val radio = bindingMain.incLayoutTabDataRadio
        radio.radioVolumeBar.progress = radioVolume
        radio.tvRadioVolumeValue.text = String.format(Locale.US, "%d%%", radioVolume)
    }
    /**
     * Счётчик радиации (roadmap, этап 22; протокол, раздел 3.4) — `GEIGER:<рад/сек>`
     * приходит от ESP32 безусловно раз в секунду, приложение само суммирует дозу и
     * клампит на [0, GEIGER_LETHAL_DOSE_RAD]. Сохраняется в SharedPreferences, чтобы
     * пережить перезапуск приложения за игру, сбрасывается только явной кнопкой на
     * экране ITEMS/Гейгер — не авторитетный источник дозы, отдельная фича-удобство,
     * не связана с официальным датчиком дозы игрока (см. CLAUDE.md, "Периферия").
     */
    private fun accumulateGeigerDose(radThisSecond: Int) {
        val prevDose = sharedPreferences.getInt(geigerDose_SPKey, 0)
        val curDose = (prevDose + radThisSecond).coerceIn(0, GEIGER_LETHAL_DOSE_RAD)
        sharedPreferences.edit().putInt(geigerDose_SPKey, curDose).apply()
        updateGeigerDoseDisplay(curDose)
    }
    /** Общая логика кнопки Reset (roadmap, этап 27) — используется и тач-обработчиком, и
     * `onActivate` узла RESET дерева энкодера (см. geigerChildrenNodes()); звук
     * (playButtonAudio(), тот же что у тача) каждый вызывающий проигрывает сам. */
    private fun resetGeigerDose() {
        sharedPreferences.edit().putInt(geigerDose_SPKey, 0).apply()
        updateGeigerDoseDisplay(0)
    }
    /**
     * Стрелка (`img_rad_arrow`) отражает долю накопленной дозы от смертельной — bias
     * считается относительно самой шкалы (`img_rad_scale`), не всего экрана, и внутри
     * диапазона [GEIGER_SCALE_START_BIAS, GEIGER_SCALE_END_BIAS], а не [0, 1] — сама
     * картинка шире размеченного на ней диапазона 0-1000 рад с обеих сторон (см. комментарий
     * в layout_tab_items_geiger.xml). Число на игле и в общей нижней панели — одно и то же
     * значение, просто два места отображения одной величины.
     */
    private fun updateGeigerDoseDisplay(dose: Int) {
        val doseText = dose.toString()
        val geiger = bindingMain.incLayoutTabItemsGeiger
        geiger.tvRadArrowValue.text = doseText
        val doseFraction = dose.toFloat() / GEIGER_LETHAL_DOSE_RAD
        val arrowBias = (GEIGER_SCALE_START_BIAS + doseFraction * (GEIGER_SCALE_END_BIAS - GEIGER_SCALE_START_BIAS))
            .coerceIn(GEIGER_SCALE_START_BIAS, GEIGER_SCALE_END_BIAS)
        // Прямая мутация LayoutParams.horizontalBias + requestLayout() ненадёжна именно в
        // сочетании с dimensionRatio на MATCH_CONSTRAINT-измерении (img_rad_arrow держит
        // ширину через ratio от высоты) — на практике стрелка оставалась на месте несмотря
        // на смену bias. ConstraintSet.setHorizontalBias()/applyTo() — официальный путь
        // менять bias в рантайме, гарантированно прогоняет полный пересчёт констрейнтов.
        ConstraintSet().apply {
            clone(geiger.root)
            setHorizontalBias(geiger.imgRadArrow.id, arrowBias)
        }.applyTo(geiger.root)
        geiger.tvGeigerStatus.text = getString(geigerStatusStringRes(dose))
        bindingMain.incLayoutHeaderBottomCommon.tvBottomRadiationValue.text = doseText
    }
    /**
     * Пороги самочувствия по накопленной дозе — фиксированы игроком, не завязаны на
     * GEIGER_LETHAL_DOSE_RAD напрямую (тот отвечает только за кламп шкалы/суммы, не за
     * текст статуса).
     */
    private fun geigerStatusStringRes(dose: Int): Int = when {
        dose < 200 -> R.string.geiger_status_ok
        dose < 400 -> R.string.geiger_status_mild
        dose < 600 -> R.string.geiger_status_moderate
        dose < 800 -> R.string.geiger_status_severe
        else -> R.string.geiger_status_critical
    }
    /**
     * Разбирает входящую BLE-строку по конвенции протокола (PipBoy_BLE_Protocol_v0.2.md,
     * раздел 2: `КЛЮЧ:ЗНАЧЕНИЕ` для параметризованных команд, голое ключевое слово для
     * остальных) и раздаёт по обработчикам. STATS/ITEMS/DATA уходят в уже существующий
     * menuChangeBLE() без изменений.
     */
    private fun handleBleCommand(raw: String) {
        val parts = raw.split(":", limit = 2)
        val key = parts[0]
        val value = parts.getOrNull(1)

        when (key) {
            "STATS" -> {
                menuChangeBLE(key)
                menuNavigator.resetToRoot(statsMenuRoot())
                // Возврат в STATS с других разделов (roadmap, этап 27) — пока таймер ранения
                // актуален, курсор энкодера должен сразу попасть на Stop, а не на вкладку
                // Status/её обычный список: Status всегда индекс 0 в statsMenuRoot(), поэтому
                // activateSelected() здесь безусловно проваливается именно в неё.
                if (woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD) {
                    menuNavigator.activateSelected()
                }
            }
            "ITEMS" -> { menuChangeBLE(key); menuNavigator.resetToRoot(itemsMenuRoot()) }
            "DATA" -> { menuChangeBLE(key); menuNavigator.resetToRoot(dataMenuRoot()) }
            "POWER" -> applyPowerState(value == "1")
            // Оверлей срабатывания таймера/будильника (roadmap, этап 27 — "курсор энкодера
            // попадает на Stop") — глобальный, поверх любого раздела (activity_main.xml,
            // последний ребёнок корня), не часть дерева MenuNavigator ни одного раздела.
            // Пока он виден, ENCBTN закрывает именно его, ENC — no-op (крутить нечего,
            // кнопка одна): раздельно от menuNavigator, а не как ещё один узел дерева.
            "ENCBTN" -> {
                if (bindingMain.incLayoutClockFiredOverlay.root.visibility == View.VISIBLE) {
                    flashButtonPressThenRun(bindingMain.incLayoutClockFiredOverlay.btnClockFiredStop) {
                        playButtonAudio()
                        dismissClockFiredOverlay()
                    }
                } else {
                    menuNavigator.activateSelected()
                    syncRow2ActiveFromNavigator()
                }
            }
            "ENC" -> {
                if (bindingMain.incLayoutClockFiredOverlay.root.visibility != View.VISIBLE) {
                    // RADIO — без второго уровня навигации (radioMenuRoot()), поэтому здесь
                    // ENC напрямую крутит громкость вместо курсора по дереву, без входа в
                    // режим редактирования через ENCBTN — на этом экране больше нечего делать.
                    if (curMenu == "RADIO") {
                        applyRadioVolumeDelta(value?.toIntOrNull() ?: 0)
                    } else {
                        menuNavigator.moveCursor(value?.toIntOrNull() ?: 0)
                        syncRow2ActiveFromNavigator()
                    }
                }
            }
            "GEIGER" -> accumulateGeigerDose(value?.toIntOrNull() ?: 0)
            "RADIOPWR" -> applyRadioPowerState(value == "1")
            "RADIOFREQ" -> value?.toIntOrNull()?.let { updateRadioFrequencyDisplay(it) }
            "VOLUME" -> applyRadioVolumeDelta(value?.toIntOrNull() ?: 0)
            // RADIOTUNE — чисто информационная (см. updateRadioFrequencyDisplay()), экран
            // обновится следующим RADIOFREQ от ESP32. RADIOTUNEBTN — протокол делает его
            // опциональным ("если есть место на экране"), индикатор режима не строим.
            "RADIOTUNE" -> Log.i("BLE", "RADIOTUNE:$value")
            "RADIOTUNEBTN" -> Log.i("BLE", "RADIOTUNEBTN")
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
                bottomButtonsModify(bindingMain.incLayoutTabItemsBottom.btnItemsGeiger, bindingMain.incLayoutTabItemsBottom.btnItemsMap, bindingMain.incLayoutTabItemsBottom.btnItemsJournal, bindingMain.incLayoutTabItemsBottom.btnItemsClock)
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
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.layoutTabStatsCndPopup
            // Часы (ITEMS/Clock, roadmap этап 6 п.3) больше не в этом списке — раньше это
            // был попап со своим фоном-плашкой (settings_menu_background_green), теперь
            // обычный полноэкранный раздел без такого фона, перекрашивать нечего.
            // Settings, экран фильтра и Bluetooth (roadmap, "Редизайн экрана фильтра —
            // UX-спецификация" / "Редизайн Settings" — правки по подразделам) тоже убраны —
            // их корни больше не используют этот бокс-drawable, перекрашивать фон
            // программно не нужно.
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
        // ProgressBar не подхватывает тему через android:tint (в отличие от ImageView, см.
        // CLAUDE.md "Архитектурный принцип: тематизация интерфейса") — тонируется явно, как
        // и фон кнопок (backgroundTintList).
        val accentColor = resources.getColor(primaryColor)
        bindingMain.incLayoutTabDataRadio.radioVolumeBar.progressTintList = ColorStateList.valueOf(accentColor)
    }
    private fun applyScrollBar(scrollbarDrawable: Drawable?){
        scrollbarDrawable?.let {
            // Apply scrollbar drawable to relevant scroll views
            val scrollViews = listOf(
                bindingMain.incLayoutTabStatsSpecial.scrollTabSpecial,
                bindingMain.incLayoutTabStatsSkills.scrollTabSkills,
                bindingMain.incLayoutTabStatsPerks.recyclerTabPerks,
                bindingMain.incLayoutTabDataMisc.recyclerTabDataMisc,
                bindingMain.incLayoutTabDataMisc.scrollTabDataMiscText,
                bindingMain.incLayoutSettingsGlobal.recyclerSettingsSidebar,
                bindingMain.incLayoutSettingsGlobal.scrollSettingsMain,
                bindingMain.incLayoutSettingsGlobal.scrollSettingsGameInfo,
                bindingMain.incLayoutSettingsGlobal.scrollSettingsPreferences,
                bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.scrollBluetoothPairingDevices,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.scrollTutorialWelcomeMain,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage.scrollTutorialPageMain,
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
        playButtonAudio()
        val it: Iterator<Button> = listArrayListButtons!!.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (!Intrinsics.areEqual(next as Any, button as Any)) {
                next.setBackgroundResource(R.drawable.button_unselected)
            }
        }
    }
    /** Превью картинки/описания SPECIAL при движении курсора (ENC/тач) — вынесено из
     * onSelect адаптера (roadmap, этап 27 — доработка энкодер-эргономики, найденный баг:
     * onHighlight листа [statsMenuRoot] раньше звал ГРОМКИЙ specialAdapter.selectPosition(),
     * тот сам вызывает onSelect — значит просто НАВЕДЕНИЕ курсора энкодером срабатывало как
     * ENCBTN, сразу проваливаясь в ValueEditor. onHighlight теперь зовёт эту функцию
     * напрямую + setSelectedPositionSilently(), не selectPosition() — тот же приём, что у
     * showClockContentPanel()/showPerkDescription(). */
    private fun showSpecialPreview(meta: SpecialMeta) {
        selectedSPECIAL = meta.key
        bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(meta.imageRes)
        bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(meta.descriptionRes)
    }
    /** Тот же приём, что у showSpecialPreview() выше, для Skills. */
    private fun showSkillPreview(meta: SkillMeta) {
        selectedSKILL = meta.key
        bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(meta.imageRes)
        bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(meta.descriptionRes)
    }
    /**
     * Кнопки +/- SPECIAL/Skills (roadmap, "Финализация STATS") — [prefKey]/[SharedPreferences]
     * и [TextView] для текущего selectedSPECIAL/selectedSKILL. Диапазоны и дефолты те же,
     * что были у старой схемы долгого тапа по строке (SPECIAL 1-10/5, Skills 10-100/10),
     * но теперь клампятся на границе, а не зацикливаются — с отдельными кнопками +/-
     * зацикливание было осмысленно только при "можно было исключительно прибавлять".
     */
    private fun adjustSelectedSpecial(delta: Int) {
        val position = specialMeta.indexOfFirst { it.key == selectedSPECIAL }
        if (position == -1) return
        val meta = specialMeta[position]
        val prevValue = sharedPreferences.getInt(meta.prefKey, 5)
        val curValue = (prevValue + delta).coerceIn(1, 10)
        sharedPreferences.edit().putInt(meta.prefKey, curValue).apply()
        specialAdapter.updateItemValue(position, curValue.toString())
        if (curValue == prevValue) playErrorAudio() else playConfirmAudio()
        // Тап по +/- переставляет курсор энкодера на редактируемую характеристику и
        // проваливается в её ValueEditor (roadmap, этап 27 — доработка энкодер-эргономики):
        // следующий ENC:+/-1 продолжает листать то же значение. Guard по editingNodeId() —
        // не переигрывать звук/визуал onEnter() на каждое срабатывание при удержании кнопки
        // (longPressRunnable), только на первое (см. doc у MenuNavigator.editingNodeId()).
        if (menuNavigator.editingNodeId() != meta.key) {
            syncStatsEncoderPathSilently("SPECIAL", listOf(position))
            menuNavigator.activateSelected()
        }
    }
    private fun adjustSelectedSkill(delta: Int) {
        val position = skillsMeta.indexOfFirst { it.key == selectedSKILL }
        if (position == -1) return
        val meta = skillsMeta[position]
        val prevValue = sharedPreferences.getInt(meta.prefKey, 10)
        val curValue = (prevValue + delta).coerceIn(10, 100)
        sharedPreferences.edit().putInt(meta.prefKey, curValue).apply()
        skillsAdapter.updateItemValue(position, curValue.toString())
        if (curValue == prevValue) playErrorAudio() else playConfirmAudio()
        // Тот же приём, что у adjustSelectedSpecial() выше.
        if (menuNavigator.editingNodeId() != meta.key) {
            syncStatsEncoderPathSilently("SKILLS", listOf(position))
            menuNavigator.activateSelected()
        }
    }
    /**
     * Общий визуальный признак "энкодер сфокусирован здесь" (roadmap, этап 27) — "прицел-
     * уголки" (`focus_corner_brackets.xml`, 4 независимых L-уголка, не сплошная рамка) на
     * отдельном View-оверлее рядом с целью, не на самой кнопке — увеличение самой кнопки на
     * 1px пробовали раньше, визуально было незаметно. Тач это состояние не видит и не меняет.
     */
    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) {
        bracketsView.visibility = if (visible) View.VISIBLE else View.GONE
    }
    private fun setSpecialValueEditorFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsSpecial.viewSpecialValueFocus, focused)
    }
    private fun setSkillValueEditorFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsSkills.viewSkillValueFocus, focused)
    }
    private fun setWoundStopButtonFocused(focused: Boolean) {
        setFocusBracketsVisible(
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewWoundStopFocus,
            focused,
        )
    }
    private fun setDeadReviveFocused(focused: Boolean) {
        setFocusBracketsVisible(
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewDeadReviveFocus,
            focused,
        )
    }
    /** Тот же приём прицела-уголков на ITEMS/Гейгер (roadmap, этап 27 — энкодер-эргономика
     * ITEMS) — Reset и Menu ("В меню"), см. geigerChildrenNodes(). */
    private fun setGeigerResetFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsGeiger.viewGeigerResetFocus, focused)
    }
    private fun setGeigerMenuFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsGeiger.viewGeigerMenuFocus, focused)
    }
    /** Тот же приём на ITEMS/Clock/Alarm (roadmap, этап 27, п.3) — часы/минуты/Set/Back. */
    private fun setClockAlarmHourFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmHourFocus, focused)
    }
    private fun setClockAlarmMinuteFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmMinuteFocus, focused)
    }
    private fun setClockAlarmSetFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmSetFocus, focused)
    }
    private fun setClockAlarmBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.viewClockAlarmBackFocus, focused)
    }
    private fun setAllClockAlarmFocusesHidden() {
        setClockAlarmHourFocused(false)
        setClockAlarmMinuteFocused(false)
        setClockAlarmSetFocused(false)
        setClockAlarmBackFocused(false)
    }
    /** Тот же приём на ITEMS/Clock/Timer, панель настройки (roadmap, этап 27, п.4). */
    private fun setClockTimerHourFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerHourFocus, focused)
    }
    private fun setClockTimerMinuteFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerMinuteFocus, focused)
    }
    private fun setClockTimerSecondFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerSecondFocus, focused)
    }
    private fun setClockTimerPreset5Focused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerPreset5Focus, focused)
    }
    private fun setClockTimerPreset10Focused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerPreset10Focus, focused)
    }
    private fun setClockTimerStartFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerStartFocus, focused)
    }
    private fun setClockTimerSetupBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerSetupBackFocus, focused)
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
    /** Тот же приём на ITEMS/Clock/Timer, панель обратного отсчёта (roadmap, этап 27, п.4). */
    private fun setClockTimerPauseResumeFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerPauseResumeFocus, focused)
    }
    private fun setClockTimerResetFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerResetFocus, focused)
    }
    private fun setClockTimerRunningBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.viewClockTimerRunningBackFocus, focused)
    }
    private fun setAllClockTimerRunningFocusesHidden() {
        setClockTimerPauseResumeFocused(false)
        setClockTimerResetFocused(false)
        setClockTimerRunningBackFocused(false)
    }
    /** Тот же приём на ITEMS/Clock/Stopwatch (roadmap, этап 27, п.4). */
    private fun setClockStopwatchStartPauseFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.viewClockStopwatchStartPauseFocus, focused)
    }
    private fun setClockStopwatchResetFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.viewClockStopwatchResetFocus, focused)
    }
    private fun setClockStopwatchBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.viewClockStopwatchBackFocus, focused)
    }
    private fun setAllClockStopwatchFocusesHidden() {
        setClockStopwatchStartPauseFocused(false)
        setClockStopwatchResetFocused(false)
        setClockStopwatchBackFocused(false)
    }
    /** Тот же приём на ITEMS/Clock/Ringtones — Select/Back под конкретным треком
     * (roadmap, этап 27, п.2). */
    private fun setClockMelodySelectFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.viewClockMelodySelectFocus, focused)
    }
    private fun setClockMelodyBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.viewClockMelodyBackFocus, focused)
    }
    /** Тот же приём на ITEMS/Journal (roadmap, этап 27, п.4) — Edit/Delete/Back карточки
     * конкретной записи, см. journalEntryDetailChildrenNodes(). */
    private fun setJournalEntryDetailEditFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsJournal.viewJournalEntryDetailEditFocus, focused)
    }
    private fun setJournalEntryDetailDeleteFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsJournal.viewJournalEntryDetailDeleteFocus, focused)
    }
    private fun setJournalEntryDetailBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsJournal.viewJournalEntryDetailBackFocus, focused)
    }
    private fun setAllJournalEntryDetailFocusesHidden() {
        setJournalEntryDetailEditFocused(false)
        setJournalEntryDetailDeleteFocused(false)
        setJournalEntryDetailBackFocused(false)
    }
    /** Тот же приём на редакторе записи Journal (roadmap, этап 27, п.3) — Mic/Cancel/Save,
     * общие и для создания, и для правки, см. journalEntryEditorChildrenNodes(). */
    private fun setJournalEntryEditorMicFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.viewJournalEntryMicFocus, focused)
    }
    private fun setJournalEntryEditorCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.viewJournalEntryPopupCancelFocus, focused)
    }
    private fun setJournalEntryEditorSaveFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.viewJournalEntryPopupSaveFocus, focused)
    }
    private fun setAllJournalEntryEditorFocusesHidden() {
        setJournalEntryEditorMicFocused(false)
        setJournalEntryEditorCancelFocused(false)
        setJournalEntryEditorSaveFocused(false)
    }
    /** Тот же приём на ITEMS/Map (roadmap, этап 27, энкодер-эргономика карты) — "Управление
     * картой"/"До точки на карте" (mapControlChildrenNodes()): Zoom/Center — один прицел на
     * блок, как SPECIAL/Skills; Pan-верх/низ и Pan-право/лево — явно ДВА отдельных прицела
     * одновременно на паре (отличие от SPECIAL, см. layout_tab_items_map.xml). */
    private fun setMapZoomFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapZoomFocus, focused)
    }
    private fun setMapCenterFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapCenterFocus, focused)
    }
    private fun setMapPanVerticalFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapPanUpFocus, focused)
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapPanDownFocus, focused)
    }
    private fun setMapPanHorizontalFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapPanLeftFocus, focused)
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapPanRightFocus, focused)
    }
    private fun setMapCrosshairFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapCrosshairFocus, focused)
    }
    private fun setMapControlBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapControlBackFocus, focused)
    }
    private fun setAllMapControlFocusesHidden() {
        setMapZoomFocused(false)
        setMapCenterFocused(false)
        setMapPanVerticalFocused(false)
        setMapPanHorizontalFocused(false)
        setMapCrosshairFocused(false)
        setMapControlBackFocused(false)
    }
    /** Тот же приём на карточке деталей отметки (roadmap, этап 27, п.9) — Edit/Route/Delete/
     * Back, по образцу setXxxJournalEntryDetailFocused() выше. */
    private fun setMapMarkerDetailEditFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapMarkerDetailEditFocus, focused)
    }
    private fun setMapMarkerDetailRouteFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapMarkerDetailRouteFocus, focused)
    }
    private fun setMapMarkerDetailDeleteFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapMarkerDetailDeleteFocus, focused)
    }
    private fun setMapMarkerDetailBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapMarkerDetailBackFocus, focused)
    }
    private fun setAllMapMarkerDetailFocusesHidden() {
        setMapMarkerDetailEditFocused(false)
        setMapMarkerDetailRouteFocused(false)
        setMapMarkerDetailDeleteFocused(false)
        setMapMarkerDetailBackFocused(false)
    }
    /** Тот же приём на панели выбора [Route]/[Marker]/[Cancel] под крестообразным прицелом
     * (roadmap, доработка после фидбека, п.2 — mapCrosshairTapChoiceChildrenNodes()). */
    private fun setMapTapChoiceRouteFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapTapChoiceRouteFocus, focused)
    }
    private fun setMapTapChoiceMarkerFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapTapChoiceMarkerFocus, focused)
    }
    private fun setMapTapChoiceCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapTapChoiceCancelFocus, focused)
    }
    private fun setAllMapTapChoiceFocusesHidden() {
        setMapTapChoiceRouteFocused(false)
        setMapTapChoiceMarkerFocused(false)
        setMapTapChoiceCancelFocused(false)
    }
    /** Тот же приём на попапе ввода имени отметки — Cancel/Save (roadmap, доработка после
     * фидбека, п.5 — mapMarkerPopupChildrenNodes()). */
    private fun setMapMarkerPopupCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupCancelFocus, focused)
    }
    private fun setMapMarkerPopupSaveFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupSaveFocus, focused)
    }
    private fun setAllMapMarkerPopupFocusesHidden() {
        setMapMarkerPopupCancelFocused(false)
        setMapMarkerPopupSaveFocused(false)
    }
    /** Тот же приём на панели управления построенным/активным маршрутом — Start/Cancel/Stop
     * (roadmap, доработка после фидбека, п.2 — mapRouteControlsChildrenNodes()). */
    private fun setMapRouteStartFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapRouteStartFocus, focused)
    }
    private fun setMapRouteCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapRouteCancelFocus, focused)
    }
    private fun setMapRouteStopFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.viewMapRouteStopFocus, focused)
    }
    private fun setAllMapRouteControlsFocusesHidden() {
        setMapRouteStartFocused(false)
        setMapRouteCancelFocused(false)
        setMapRouteStopFocused(false)
    }
    /** Прицелы на отдельных частях тела (roadmap, этап 27 — курсор энкодера со Stop должен
     * уметь переходить на конкретную часть тела и отмечать её CRIPPLED), тот же приём, что
     * у [setWoundStopButtonFocused]/[setDeadReviveFocused]. [setAllCrippledFocusesHidden] —
     * подстраховка идемпотентности при выходе из этой ветки дерева (DEAD/здоров), тот же
     * смысл, что у существующих `setWoundStopButtonFocused(false)` в других ветках. */
    private fun setCrippledHeadFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewCrippledHeadFocus, focused)
    }
    private fun setCrippledTorsoFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewCrippledTorsoFocus, focused)
    }
    private fun setCrippledLeftArmFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewCrippledLeftArmFocus, focused)
    }
    private fun setCrippledRightArmFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewCrippledRightArmFocus, focused)
    }
    private fun setCrippledLeftLegFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewCrippledLeftLegFocus, focused)
    }
    private fun setCrippledRightLegFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewCrippledRightLegFocus, focused)
    }
    private fun setAllCrippledFocusesHidden() {
        setCrippledHeadFocused(false)
        setCrippledTorsoFocused(false)
        setCrippledLeftArmFocused(false)
        setCrippledRightArmFocused(false)
        setCrippledLeftLegFocused(false)
        setCrippledRightLegFocused(false)
    }
    /** Мгновенный флэш "нажатия" (roadmap, этап 27 — "должна срабатывать анимация нажатия,
     * такая же, как при таче", раньше играл только звук) — для непрерывных ENC-действий
     * (`+`/`-` в ValueEditor), где реальное действие не откладывается: пауза перед ним была
     * бы заметна как лаг при быстром вращении энкодера. Кнопка при этом никуда не девается,
     * ждать нечего. */
    private fun flashButtonPressImmediate(button: View) {
        button.isPressed = true
        button.postDelayed({ button.isPressed = false }, ENCODER_PRESS_FLASH_DURATION_MS)
    }
    /** Флэш "нажатия", ЗАТЕМ (после той же паузы) настоящее действие — для одноразовых
     * ENCBTN-команд, которые сами же сразу прячут/меняют эту кнопку (Stop на STATUS —
     * прячет layout_tab_status_wound_buttons; Stop на оверлее — прячет весь оверлей): без
     * паузы анимация не успела бы стать видна раньше, чем экран уже поменялся. */
    private fun flashButtonPressThenRun(button: View, action: () -> Unit) {
        button.isPressed = true
        button.postDelayed({
            button.isPressed = false
            action()
        }, ENCODER_PRESS_FLASH_DURATION_MS)
    }
    /**
     * Дети узла STATUS дерева энкодера (roadmap, этап 27 — "энкодер должен переключаться на
     * Stop"). Пока таймер ранения актуален (BLEED/BANDAGE/STUNNED — те же фазы, при которых
     * видна сама кнопка Stop, см. updateWoundStatusLine()), список ранений и "В меню"
     * недостижимы энкодером совсем — не просто задизейблены: единственные узлы здесь Stop и
     * 6 частей тела (roadmap, этап 27 — "курсор должен уметь переходить со Stop на часть тела
     * и отмечать её CRIPPLED", повторный ENCBTN снимает отметку — то же поведение, что у
     * тапа, см. toggleCrippled*()). Порядок листания — Stop, Голова, Левая рука, Туловище,
     * Правая рука, Левая нога, Правая нога, снова Stop (через заворот moveCursor()). Вне
     * таймера/DEAD — обычный список. setWoundStopButtonFocused(false)/
     * setAllCrippledFocusesHidden() в обычной ветке — не столько для актуального перехода
     * (тот отдельно триггерит refreshStatusEncoderChildren() при смене woundPhase, см.
     * startWoundTimer() и др.), сколько подстраховка идемпотентности: ни один прицел не
     * должен остаться "выросшим" при любой пересборке этого списка, а не только сразу после
     * выхода из фокуса.
     */
    private fun statusChildrenNodes(): List<MenuNode> {
        return if (woundPhase == WoundPhase.DEAD) {
            // roadmap, этап 27 — "когда персонаж переходит в DEAD, курсор энкодера должен
            // устанавливаться на персонажа, ENCBTN = тот же жест, что тап, воскрешает".
            // reviveCharacter() — то же самое, что зовёт тач-жест (setupFigureTouchTarget),
            // без отдельного звука: у тача его тоже нет, ENCBTN не должен придумывать новый.
            // setWoundStopButtonFocused(false)/setAllCrippledFocusesHidden() — на случай
            // прихода в DEAD прямо из активного таймера (killCharacter() из fireWoundTimer()),
            // где один из этих прицелов только что был в фокусе.
            setWoundStopButtonFocused(false)
            setAllCrippledFocusesHidden()
            listOf(
                MenuNode(
                    id = "REVIVE",
                    onHighlight = { setDeadReviveFocused(true) },
                    onActivate = { reviveCharacter() },
                )
            )
        } else if (woundPhase != WoundPhase.NONE) {
            listOf(
                MenuNode(
                    id = "STOP",
                    onHighlight = {
                        playTickAudio()
                        setAllCrippledFocusesHidden()
                        setWoundStopButtonFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(
                            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.btnTabStatusWoundStop,
                        ) {
                            playButtonAudio()
                            stopWoundTimerEarly()
                        }
                    },
                ),
                MenuNode(
                    id = "BODYPART_HEAD",
                    onHighlight = {
                        playTickAudio()
                        setWoundStopButtonFocused(false)
                        setAllCrippledFocusesHidden()
                        setCrippledHeadFocused(true)
                    },
                    onActivate = {
                        playConfirmAudio()
                        toggleCrippledHead()
                    },
                ),
                MenuNode(
                    id = "BODYPART_LEFT_ARM",
                    onHighlight = {
                        playTickAudio()
                        setWoundStopButtonFocused(false)
                        setAllCrippledFocusesHidden()
                        setCrippledLeftArmFocused(true)
                    },
                    onActivate = {
                        playConfirmAudio()
                        toggleCrippledLeftArm()
                    },
                ),
                MenuNode(
                    id = "BODYPART_TORSO",
                    onHighlight = {
                        playTickAudio()
                        setWoundStopButtonFocused(false)
                        setAllCrippledFocusesHidden()
                        setCrippledTorsoFocused(true)
                    },
                    onActivate = {
                        playConfirmAudio()
                        toggleCrippledTorso()
                    },
                ),
                MenuNode(
                    id = "BODYPART_RIGHT_ARM",
                    onHighlight = {
                        playTickAudio()
                        setWoundStopButtonFocused(false)
                        setAllCrippledFocusesHidden()
                        setCrippledRightArmFocused(true)
                    },
                    onActivate = {
                        playConfirmAudio()
                        toggleCrippledRightArm()
                    },
                ),
                MenuNode(
                    id = "BODYPART_LEFT_LEG",
                    onHighlight = {
                        playTickAudio()
                        setWoundStopButtonFocused(false)
                        setAllCrippledFocusesHidden()
                        setCrippledLeftLegFocused(true)
                    },
                    onActivate = {
                        playConfirmAudio()
                        toggleCrippledLeftLeg()
                    },
                ),
                MenuNode(
                    id = "BODYPART_RIGHT_LEG",
                    onHighlight = {
                        playTickAudio()
                        setWoundStopButtonFocused(false)
                        setAllCrippledFocusesHidden()
                        setCrippledRightLegFocused(true)
                    },
                    onActivate = {
                        playConfirmAudio()
                        toggleCrippledRightLeg()
                    },
                ),
            )
        } else {
            setWoundStopButtonFocused(false)
            setDeadReviveFocused(false)
            setAllCrippledFocusesHidden()
            statusMeta.mapIndexed { index, meta ->
                MenuNode(
                    id = meta.key,
                    // Звук на перемещение курсора — тот же playTickAudio(), что и у
                    // SPECIAL/Skills при листании (roadmap, этап 27), просто не через
                    // playSelectSound() адаптера (тот для Status специально no-op, звук
                    // решает сам onSelect — см. комментарий выше о статusAdapter ниже).
                    onHighlight = {
                        playTickAudio()
                        statusAdapter.setSelectedPositionSilently(index)
                    },
                    onActivate = {
                        statusAdapter.selectPosition(index)
                        statusAdapter.flashPressAnimation(index)
                    },
                )
            } + menuBackNode(
                pipBoyMode,
                onHighlight = { statusAdapter.setSelectedPositionSilently(statusMeta.size) },
                onBeforePop = { statusAdapter.flashPressAnimation(statusMeta.size) },
            )
        }
    }
    /** Живая пересборка узла STATUS в дереве энкодера (roadmap, этап 27) — вызывается из
     * каждого места, где меняется woundPhase (startWoundTimer()/healWoundsToHealthy()/
     * killCharacter()/reviveCharacter()), не только при свежем входе в STATS. No-op, если
     * игрок сейчас не внутри списка Status (MenuNavigator.replaceChildrenOf сам проверяет). */
    private fun refreshStatusEncoderChildren() {
        menuNavigator.replaceChildrenOf("STATUS", statusChildrenNodes())
    }
    /**
     * Пункт "В меню" в боковых списках Status/SPECIAL/Skills (roadmap, этап 27 — находка "нет
     * способа подняться из третьего уровня") — только в режиме PipBoy 2000/3000: в режиме
     * Телефон тач переключает вкладки строки 2 напрямую, "подъём по дереву" энкодера там ни
     * при чём. [specialSidebarItems]/[skillsSidebarItems]/[statusSidebarItems] — источник
     * истины и для начальной постройки адаптеров в onCreate(), и для [refreshSidebarBackItems]
     * (режим может стать известен уже после того, как адаптеры собраны — мастер выбора
     * режима идёт позже в том же onCreate()).
     */
    private fun backSidebarItem(enabled: Boolean = true): SidebarMenuItem<String> =
        SidebarMenuItem(payload = SIDEBAR_BACK_PAYLOAD, label = getString(R.string.sidebar_menu_back), enabled = enabled)
    /** Пункт "В меню" как ребёнок дерева энкодера (`statsMenuRoot()`) — тот же индекс (конец
     * списка), что и [backSidebarItem] в адаптере: пусто в режиме Телефон, один узел иначе.
     * [onHighlight]/[onBeforePop] передаются отдельно, потому что молчаливая подсветка и
     * флэш нажатия (roadmap, этап 27) у каждого экрана — свой adapter/индекс. */
    private fun menuBackNode(mode: PipBoyMode, onHighlight: () -> Unit, onBeforePop: () -> Unit): List<MenuNode> =
        if (mode != PipBoyMode.PHONE) {
            listOf(
                MenuNode(
                    id = "MENU",
                    // Звук на листание/нажатие (roadmap, этап 27 — раньше не было вообще)
                    // — тот же язык, что у остальных пунктов этих же списков:
                    // playTickAudio() на перемещение курсора, playConfirmAudio()
                    // (как у +/-) на реальное нажатие ENCBTN.
                    onHighlight = {
                        playTickAudio()
                        onHighlight()
                    },
                    onActivate = {
                        playConfirmAudio()
                        onBeforePop()
                        menuNavigator.popLevel()
                    },
                )
            )
        } else {
            emptyList()
        }
    /**
     * `ValueEditor` для длинной записи бокового меню (Files/Perks, roadmap этап 27) —
     * `ENCBTN` на записи переключает `ENC` на прокрутку её панели описания вместо движения
     * курсора по списку, повторный `ENCBTN` возвращает к списку (тот же приём переключения
     * режима `ENC`, что и `+`/`-` у SPECIAL/Skills, только `onAdjust` крутит `ScrollView`,
     * а не число). `smoothScrollBy()` — не `scrollBy()`: `ScrollView` сам клэмпит цель в
     * границы контента ([0, childHeight - contentHeight]), простой `scrollBy()` этого не
     * делает и может увести прокрутку в пустоту за пределами текста.
     */
    private fun recordScrollValueEditor(scrollView: ScrollView): ValueEditor {
        val stepPx = (SIDEBAR_RECORD_SCROLL_STEP_DP * resources.displayMetrics.density).toInt()
        return ValueEditor(
            onAdjust = { delta -> scrollView.smoothScrollBy(0, delta * stepPx) },
            onEnter = { playConfirmAudio() },
            onExit = { playTickAudio() },
        )
    }
    private fun specialSidebarItems(): List<SidebarMenuItem<String>> {
        val items = specialMeta.map { meta ->
            SidebarMenuItem(
                payload = meta.key,
                label = getString(meta.labelRes),
                rightValue = sharedPreferences.getInt(meta.prefKey, 5).toString(),
            )
        }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    private fun skillsSidebarItems(): List<SidebarMenuItem<String>> {
        val items = skillsMeta.map { meta ->
            SidebarMenuItem(
                payload = meta.key,
                label = getString(meta.labelRes),
                rightValue = sharedPreferences.getInt(meta.prefKey, 10).toString(),
            )
        }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    private fun statusSidebarItems(): List<SidebarMenuItem<String>> {
        // "В меню" дизейблится вместе с LIGHT/HEAVY/STUNNED, пока актуален таймер ранения
        // (roadmap, этап 27) — тач по нему в это время всё равно доедет до onSelect и даст
        // звук ошибки (SidebarMenuItem.enabled — только визуальное затенение, не блокировка
        // тапа, см. SidebarMenuAdapter.kt), энкодер же в это время туда вообще не попадёт
        // (statusChildrenNodes() убирает "В меню" из дерева совсем, единственный узел — STOP).
        val enabled = woundPhase == WoundPhase.NONE
        val items = statusMeta.map { meta ->
            SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes), enabled = enabled)
        }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem(enabled) else items
    }
    /** DATA/Files — тот же приём, что у specialSidebarItems()/skillsSidebarItems() выше:
     * фиксированный список (не фильтруется, в отличие от Perks), "В меню" — последний пункт. */
    private fun dataFilesSidebarItems(): List<SidebarMenuItem<String>> {
        val items = dataFilesMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.nameRes)) }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    /** Превью описания записи Files при движении курсора (ENC/тач) — тот же приём, что у
     * showSpecialPreview()/showPerkDescription() выше (roadmap, этап 27 — доработка
     * энкодер-эргономики). */
    private fun showDataFilePreview(meta: DataFileMeta) {
        val files = bindingMain.incLayoutTabDataMisc
        files.tvDataMiscHolotapeText.setText(meta.descriptionRes)
        // Сброс прокрутки на новую запись — см. тот же приём в showPerkDescription() выше.
        files.scrollTabDataMiscText.scrollTo(0, 0)
    }
    /** Дети узла MISC дерева энкодера DATA (dataMenuRoot()) — та же схема, что у perksChildrenNodes():
     * onHighlight обновляет описание через setSelectedPositionSilently() + showDataFilePreview(),
     * не громкий dataFilesAdapter.selectPosition() (roadmap, доработка после фидбека — тот сам
     * зовёт onSelect адаптера, то есть простое наведение курсора энкодером срабатывало как
     * ENCBTN, сразу проваливаясь в прокрутку описания). onActivate = {} (не null) на каждой
     * записи, чтобы ENCBTN на ней просто подтверждал подсветку и не проваливался/поднимался
     * никуда (roadmap — "нажатие ENCBTN на пункт меню не делает ничего"), "В меню" поднимает
     * курсор обратно на строку 2 DATA (MISC/HOLOTAPES). */
    private fun dataFilesChildrenNodes(): List<MenuNode> {
        return dataFilesMeta.mapIndexed { index, meta ->
            MenuNode(
                id = "FILE_$index",
                onHighlight = {
                    playTickAudio()
                    dataFilesAdapter.setSelectedPositionSilently(index)
                    showDataFilePreview(meta)
                },
                // ENCBTN на записи — не подъём наверх и не no-op, а вход в прокрутку её
                // описания (roadmap, этап 27 — находка "листание длинных файлов").
                valueEditor = recordScrollValueEditor(bindingMain.incLayoutTabDataMisc.scrollTabDataMiscText),
            )
        } + menuBackNode(
            pipBoyMode,
            onHighlight = { dataFilesAdapter.setSelectedPositionSilently(dataFilesMeta.size) },
            onBeforePop = { dataFilesAdapter.flashPressAnimation(dataFilesMeta.size) },
        )
    }
    /** Позиция пункта TIME/ALARM/TIMER/STOPWATCH/MELODY по его ключу в clockMeta — тот же
     * приём, что mapRootIndex() у Карты, для использования в syncClockEncoderPath(). */
    private fun clockRootIndex(key: String): Int = clockMeta.indexOfFirst { it.key == key }
    /** Безусловная синхронизация курсора энкодера с тачем на экране Часов (roadmap, этап 27,
     * доработка после фидбека по Карте — тот же класс бага "энкодер не следует за тапами
     * между узлами дерева", см. doc у syncMapEncoderPath()/MenuNavigator.setPath()):
     * menuNavigator.syncCursor() чинит курсор только ВНУТРИ уже активного уровня — если тач
     * переключился в совсем другую ветку (например, тапнул сайдбар "Stopwatch", пока энкодер
     * был внутри "Alarm" → HOUR), синхронизировать было нечего, курсор оставался "залипшим"
     * в прежней ветке, а следующий поворот ENC двигал бы не то, что показано на экране.
     * [path] — индексы от детей самого узла CLOCK (не всего дерева).
     *
     * Громкий `setPath()`, не `setPathSilently()` (roadmap, доработка после фидбека —
     * найденный баг: прицел энкодера обязан рисоваться там, где реально стоит курсор, а не
     * только внутренняя бухгалтерия стека — иначе после тача, уводящего энкодер в другую
     * ветку, прицел либо не появлялся вовсе на новом месте, либо оставался нарисованным на
     * старом, хотя курсор там уже не стоит). Раньше здесь был `setPathSilently()` — тогда
     * `onHighlight` TIME/ALARM/TIMER/STOPWATCH/MELODY.TRACK_0 сами вызывали громкий
     * `selectPosition()`, который заново вызывал этот же `onSelect` и зациклился бы; после
     * того как эти `onHighlight` переведены на `setSelectedPositionSilently()` (см.
     * `clockChildrenNodes()`/`melodyChildrenNodes()`), рекурсии больше нет ни у одного узла,
     * до которого может указывать [path] — держаться этого инварианта у любого нового узла
     * Clock: onHighlight не должен звать громкий `selectPosition()` своего же адаптера. */
    private fun syncClockEncoderPath(path: List<Int>) {
        val itemsRoot = itemsMenuRoot()
        val clockIndex = itemsRoot.indexOfFirst { it.id == "CLOCK" }
        if (clockIndex == -1) return
        menuNavigator.setPath(itemsRoot, listOf(clockIndex) + path)
    }
    /** ITEMS/Clock — тот же приём, что у dataFilesSidebarItems() выше: фиксированный список,
     * "В меню" — последний пункт (roadmap, этап 27). */
    private fun clockSidebarItems(): List<SidebarMenuItem<String>> {
        val items = clockMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes)) }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    /** Дети узла CLOCK дерева энкодера (itemsMenuRoot()) — контент следует за курсором
     * (roadmap, этап 27, п.1, фидбек по итогам тестирования — тот же приём, что у записей
     * Journal): TIME/ALARM/TIMER/STOPWATCH показывают свою панель на КАЖДЫЙ шаг листания, у
     * ALARM/TIMER/STOPWATCH при этом есть свои `children` — ENCBTN проваливается в них,
     * `onActivate` никогда не понадобится. TIME — лист без children (экран часов
     * декоративный), `onActivate = {}` — ENCBTN на нём не делает ничего (та же схема, что у
     * записей DATA/Files: подсветка уже стоит, проваливаться/подниматься некуда).
     *
     * onHighlight вызывает `clockAdapter.setSelectedPositionSilently(index)` +
     * `showClockContentPanel(meta.key)` НАПРЯМУЮ, не `clockAdapter.selectPosition(index)` (та
     * же схема, что у mapRootAdapter/mapRouteSubmenuAdapter в Карте) — найденный баг
     * (roadmap, доработка после фидбека): `selectPosition()` (не Silently) заново вызывает
     * `onSelect` сайдбара, который после доработки touch-синхронизации (см.
     * `syncClockEncoderPath()`) выше НЕ просто переключает панель, а безусловно ставит путь
     * энкодера на первого ребёнка ALARM/TIMER/STOPWATCH — обычное ENC-листание, ПРОСТО
     * проходящее через эти пункты (не проваливаясь в них), рекурсивно и молча продавливало
     * курсор на уровень глубже, чем реально показано на экране (прицел ребёнка при этом не
     * рисовался — путь ставился silently), что и давало весь букет находок на устройстве:
     * первый `ENCBTN` на ALARM на самом деле попадал на УЖЕ выбранный HOUR (входя сразу в
     * `ValueEditor` без показа прицела), второй `ENCBTN` (Back) на самом деле дублировался
     * относительно этого чужого состояния, а простое ENC-пролистывание порождало на экране
     * состояние на уровень глубже, чем должно быть видно курсору. */
    private fun clockChildrenNodes(): List<MenuNode> {
        return clockMeta.mapIndexed { index, meta ->
            when (meta.key) {
                "TIME" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTickAudio()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                    },
                    onActivate = {},
                )
                "ALARM" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTickAudio()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                        // Курсор стоит НА самом ALARM (не провалился в children) — прицел
                        // любого внутреннего узла с прошлого визита должен погаснуть,
                        // найденный баг (roadmap, доработка после фидбека).
                        setAllClockAlarmFocusesHidden()
                    },
                    children = alarmChildrenNodes(),
                )
                "TIMER" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTickAudio()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                        // Оба набора — какой из них сейчас видим, знает только timerState,
                        // прятать оба безусловно дешевле и безопаснее, чем разветвлять.
                        setAllClockTimerSetupFocusesHidden()
                        setAllClockTimerRunningFocusesHidden()
                    },
                    // childrenProvider, не статичный children — состав детей зависит от
                    // timerState, пересчитывается заново на каждый провал (см. Journal).
                    childrenProvider = { timerChildrenNodes() },
                )
                "STOPWATCH" -> MenuNode(
                    id = meta.key,
                    onHighlight = {
                        playTickAudio()
                        clockAdapter.setSelectedPositionSilently(index)
                        showClockContentPanel(meta.key)
                        setAllClockStopwatchFocusesHidden()
                    },
                    children = stopwatchChildrenNodes(),
                )
                else -> MenuNode( // "MELODY"
                    id = meta.key,
                    onHighlight = { playTickAudio(); clockAdapter.setSelectedPositionSilently(index) },
                    children = melodyChildrenNodes(),
                )
            }
        } + menuBackNode(
            pipBoyMode,
            onHighlight = { clockAdapter.setSelectedPositionSilently(clockMeta.size) },
            onBeforePop = { clockAdapter.flashPressAnimation(clockMeta.size) },
        )
    }
    /** Дети узла ALARM (roadmap, этап 27, п.3) — настройка часов/минут (ValueEditor поверх
     * ClockWheelPicker), Set (существующий toggle alarmArmed), Back. HOUR коммитит панель
     * (clockAdapter.selectPosition) — первый ребёнок при провале в ALARM. */
    private fun alarmChildrenNodes(): List<MenuNode> {
        val alarm = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm
        return listOfNotNull(
            MenuNode(
                id = "HOUR",
                onHighlight = {
                    playTickAudio()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmHourFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta -> playTickAudio(); alarmHourWheel.scrollToValue(alarmHourWheel.currentValue() + delta) },
                    onEnter = { playConfirmAudio() },
                    onExit = { playTickAudio() },
                ),
            ),
            MenuNode(
                id = "MINUTE",
                onHighlight = {
                    playTickAudio()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmMinuteFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta -> playTickAudio(); alarmMinuteWheel.scrollToValue(alarmMinuteWheel.currentValue() + delta) },
                    onEnter = { playConfirmAudio() },
                    onExit = { playTickAudio() },
                ),
            ),
            MenuNode(
                id = "SET",
                onHighlight = {
                    playTickAudio()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmSetFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(alarm.btnClockAlarmToggle) {
                        playButtonAudio()
                        toggleAlarmArmed()
                    }
                },
            ),
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                id = "BACK",
                onHighlight = {
                    playTickAudio()
                    setAllClockAlarmFocusesHidden()
                    setClockAlarmBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(alarm.btnClockAlarmBack) {
                        playConfirmAudio()
                        setClockAlarmBackFocused(false)
                        menuNavigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Дети узла TIMER (roadmap, этап 27, п.4) — ветвится по timerState: IDLE — колёса
     * Ч/М/С + пресеты + Start, иначе — Pause/Resume + Reset. HOUR/PAUSE_RESUME коммитят
     * панель (первый ребёнок в каждой из двух веток). Пересобирается на лету через
     * refreshClockTimerEncoderChildren() (см. startPlainTimer()/syncClockTimerScreenVisibility()). */
    private fun timerChildrenNodes(): List<MenuNode> {
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        return if (timerState == TimerState.IDLE) {
            listOfNotNull(
                MenuNode(
                    id = "HOUR",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerHourFocused(true)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta -> playTickAudio(); timerHourWheel.scrollToValue(timerHourWheel.currentValue() + delta) },
                        onEnter = { playConfirmAudio() },
                        onExit = { playTickAudio() },
                    ),
                ),
                MenuNode(
                    id = "MINUTE",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerMinuteFocused(true)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta -> playTickAudio(); timerMinuteWheel.scrollToValue(timerMinuteWheel.currentValue() + delta) },
                        onEnter = { playConfirmAudio() },
                        onExit = { playTickAudio() },
                    ),
                ),
                MenuNode(
                    id = "SECOND",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerSecondFocused(true)
                    },
                    valueEditor = ValueEditor(
                        onAdjust = { delta -> playTickAudio(); timerSecondWheel.scrollToValue(timerSecondWheel.currentValue() + delta) },
                        onEnter = { playConfirmAudio() },
                        onExit = { playTickAudio() },
                    ),
                ),
                MenuNode(
                    id = "PRESET5",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerPreset5Focused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerPreset5) {
                            playButtonAudio()
                            addTimerPresetMinutes(5)
                        }
                    },
                ),
                MenuNode(
                    id = "PRESET10",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerPreset10Focused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerPreset10) {
                            playButtonAudio()
                            addTimerPresetMinutes(10)
                        }
                    },
                ),
                MenuNode(
                    id = "START",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerStartFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerStart) {
                            playButtonAudio()
                            startPlainTimer(timerHours * 3600 + timerMinutes * 60 + timerSeconds)
                        }
                    },
                ),
                if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                    id = "BACK",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerSetupFocusesHidden()
                        setClockTimerSetupBackFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerSetupBack) {
                            playConfirmAudio()
                            setClockTimerSetupBackFocused(false)
                            menuNavigator.popLevel()
                        }
                    },
                ) else null,
            )
        } else {
            listOfNotNull(
                MenuNode(
                    id = "PAUSE_RESUME",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerRunningFocusesHidden()
                        setClockTimerPauseResumeFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerPauseResume) {
                            playButtonAudio()
                            pauseResumeTimer()
                        }
                    },
                ),
                MenuNode(
                    id = "RESET",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerRunningFocusesHidden()
                        setClockTimerResetFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerReset) {
                            playButtonAudio()
                            resetTimer()
                        }
                    },
                ),
                if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                    id = "BACK",
                    onHighlight = {
                        playTickAudio()
                        setAllClockTimerRunningFocusesHidden()
                        setClockTimerRunningBackFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(timer.btnClockTimerRunningBack) {
                            playConfirmAudio()
                            setClockTimerRunningBackFocused(false)
                            menuNavigator.popLevel()
                        }
                    },
                ) else null,
            )
        }
    }
    /** Живая пересборка узла TIMER в дереве энкодера (roadmap, этап 27, п.4) — тот же приём,
     * что у refreshStatusEncoderChildren(): вызывается при смене timerState, no-op если
     * курсор энкодера сейчас не внутри TIMER (MenuNavigator.replaceChildrenOf сам проверяет). */
    private fun refreshClockTimerEncoderChildren() {
        menuNavigator.replaceChildrenOf("TIMER", timerChildrenNodes())
    }
    /** Дети узла STOPWATCH (roadmap, этап 27, п.4) — статичный список, набор кнопок не
     * зависит от stopwatchState (только текст START_PAUSE меняется). */
    private fun stopwatchChildrenNodes(): List<MenuNode> {
        val stopwatch = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch
        return listOfNotNull(
            MenuNode(
                id = "START_PAUSE",
                onHighlight = {
                    playTickAudio()
                    setAllClockStopwatchFocusesHidden()
                    setClockStopwatchStartPauseFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(stopwatch.btnClockStopwatchStartPause) {
                        playButtonAudio()
                        toggleStopwatchStartPause()
                    }
                },
            ),
            MenuNode(
                id = "RESET",
                onHighlight = {
                    playTickAudio()
                    setAllClockStopwatchFocusesHidden()
                    setClockStopwatchResetFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(stopwatch.btnClockStopwatchReset) {
                        playButtonAudio()
                        resetStopwatch()
                    }
                },
            ),
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                id = "BACK",
                onHighlight = {
                    playTickAudio()
                    setAllClockStopwatchFocusesHidden()
                    setClockStopwatchBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(stopwatch.btnClockStopwatchBack) {
                        playConfirmAudio()
                        setClockStopwatchBackFocused(false)
                        menuNavigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Дети узла MELODY (roadmap, этап 27, п.2) — трек за треком, автопрослушивание на
     * каждый шаг листания (startMelodyPreview()). ENCBTN на треке проваливается в
     * [SELECT, BACK] (melodySelectBackChildrenNodes()) — первый ребёнок, который получит
     * onHighlight, это SELECT ("курсор переходит на кнопку [Select]"). Существующий пункт
     * списка "Назад" (payload=null в melodyAdapter) — уже готовый общий выход с экрана,
     * отдельный "Menu" не нужен (в ТЗ явно сказано не добавлять). */
    private fun melodyChildrenNodes(): List<MenuNode> {
        val trackNodes = ringtoneTracks.indices.map { i ->
            MenuNode(
                id = "TRACK_$i",
                onHighlight = {
                    // Только первый трек коммитит панель (открывает экран Мелодии) — тот же
                    // приём, что у HOUR в alarmChildrenNodes(): провал в MELODY сразу
                    // приземляет курсор на первый трек, дальнейшее листание уже открытого
                    // экрана коммита не требует. clockAdapter.setSelectedPositionSilently(),
                    // не громкий selectPosition() (roadmap, доработка после фидбека) — тот
                    // заново вызывает onSelect сайдбара, который для MELODY делает cross-
                    // branch syncClockEncoderPath()/setPath() — зациклилось бы, раз этот путь
                    // сам заканчивается здесь же (TRACK_0).
                    playTickAudio()
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
            onHighlight = { playTickAudio(); melodyAdapter.setSelectedPositionSilently(ringtoneTracks.size) },
            // Не дублировать menuNavigator.popLevel() здесь — melodyAdapter.selectPosition()
            // уже вызывает его сам через onSelect (payload=null), см. сетап-блок onCreate().
            // Найденный баг: двойной popLevel() уводил курсор энкодера на уровень выше, чем
            // нужно (в строку ITEMS вместо бокового меню Clock).
            onActivate = {
                melodyAdapter.selectPosition(ringtoneTracks.size)
                melodyAdapter.flashPressAnimation(ringtoneTracks.size)
            },
        )
        return trackNodes + backNode
    }
    /** Select/Back под конкретным треком (roadmap, этап 27, п.2) — Select коммитит текущий
     * melodyFocusedIndex (уже выставлен onHighlight трека), Back — обычный подъём на один
     * уровень (то же тело, что и тач на новой кнопке btnClockMelodyBack). */
    private fun melodySelectBackChildrenNodes(): List<MenuNode> {
        val melody = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody
        return listOfNotNull(
            MenuNode(
                id = "SELECT",
                onHighlight = {
                    playTickAudio()
                    setClockMelodyBackFocused(false)
                    setClockMelodySelectFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(melody.btnClockMelodySelect) {
                        playButtonAudio()
                        commitMelodySelection()
                    }
                },
            ),
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode(
                id = "BACK",
                onHighlight = {
                    playTickAudio()
                    setClockMelodySelectFocused(false)
                    setClockMelodyBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(melody.btnClockMelodyBack) {
                        playConfirmAudio()
                        setClockMelodyBackFocused(false)
                        menuNavigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Пересобирает три списка выше, когда режим становится известен/меняется уже после
     * того, как onCreate() построил адаптеры (selectPipBoyMode()/restoreAppState()) — сам
     * список нужно поменять целиком, а не просто добавить/убрать один View, поэтому
     * [SidebarMenuAdapter.setItems], не точечная правка. resetSelection=false — режим
     * меняется не во время игры на этом самом экране, но незачем и рисковать курсором. */
    private fun refreshSidebarBackItems() {
        specialAdapter.setItems(specialSidebarItems(), resetSelection = false)
        skillsAdapter.setItems(skillsSidebarItems(), resetSelection = false)
        statusAdapter.setItems(statusSidebarItems(), resetSelection = false)
        dataFilesAdapter.setItems(dataFilesSidebarItems(), resetSelection = false)
        clockAdapter.setItems(clockSidebarItems(), resetSelection = false)
        // "Управление картой" — тоже гейт по pipBoyMode (roadmap, этап 27, энкодер-
        // эргономика карты, п.1), тот же приём, что у остальных списков выше.
        mapRootAdapter.setItems(
            mapRootSidebarItems(),
            resetSelection = false,
        )
        // journalListAdapter, в отличие от адаптеров выше, не строится безусловно в
        // onCreate() — только при первом заходе на вкладку Journal (bindJournalListAdapter(),
        // openJournalScreen()), поэтому единственный из всех тут нуждается в проверке
        // инициализации (roadmap, этап 27, п.2).
        if (::journalListAdapter.isInitialized) {
            journalListAdapter.setItems(journalSidebarItems(), resetSelection = false)
        }
        refreshGeigerMenuButtonVisibility()
        refreshJournalBackButtonVisibility()
        refreshMapMarkerDetailBackButtonVisibility()
        refreshClockAlarmBackButtonVisibility()
        refreshClockTimerBackButtonsVisibility()
        refreshClockStopwatchBackButtonVisibility()
        refreshClockMelodyBackButtonVisibility()
    }
    /** Menu на ITEMS/Гейгер — не SidebarMenuAdapter (обычная кнопка, см.
     * geigerChildrenNodes()), поэтому видимость по режиму обновляется отдельным вызовом
     * рядом с остальными "В меню" выше, не через [SidebarMenuAdapter.setItems]. Любой режим
     * с физическим энкодером (не Phone) — было сознательно только PipBoy 2000, пересмотрено
     * по фидбоку (roadmap, этап 27). */
    private fun refreshGeigerMenuButtonVisibility() {
        bindingMain.incLayoutTabItemsGeiger.btnGeigerMenu.visibility =
            if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
    }
    /** Back на карточке записи Journal — та же схема, что у Menu на Гейгере выше: обычная
     * кнопка экрана, не элемент SidebarMenuAdapter, видимость по режиму обновляется отдельно
     * (roadmap, этап 27 — найденный баг: кнопка была видна и в Phone, где физического
     * энкодера нет вообще, а сама кнопка нужна только чтобы отдать курсор энкодера обратно
     * в боковое меню). */
    private fun refreshJournalBackButtonVisibility() {
        bindingMain.incLayoutTabItemsJournal.btnJournalEntryDetailBack.visibility =
            if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
    }
    /** Back на карточке деталей отметки Карты (roadmap, этап 27, п.9) — тот же гейт/приём,
     * что у Back на карточке записи Journal выше. */
    private fun refreshMapMarkerDetailBackButtonVisibility() {
        bindingMain.incLayoutTabItemsMap.btnMapMarkerDetailBack.visibility =
            if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
    }
    /** Back-кнопки ITEMS/Clock (roadmap, этап 27, п.3-4) — та же схема, что у Menu на
     * Гейгере/Back на Journal: обычные кнопки экрана, не элементы SidebarMenuAdapter. */
    private fun refreshClockAlarmBackButtonVisibility() {
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.btnClockAlarmBack.visibility =
            if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
    }
    private fun refreshClockTimerBackButtonsVisibility() {
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        val visibility = if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
        timer.btnClockTimerSetupBack.visibility = visibility
        timer.btnClockTimerRunningBack.visibility = visibility
    }
    private fun refreshClockStopwatchBackButtonVisibility() {
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.btnClockStopwatchBack.visibility =
            if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
    }
    private fun refreshClockMelodyBackButtonVisibility() {
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.btnClockMelodyBack.visibility =
            if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
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
        // SPECIAL/Skills — первый пункт подсвечивается сам по себе (SidebarMenuAdapter,
        // initialSelectedPosition по умолчанию 0), отдельная строка тут больше не нужна.
    }
    private fun setupDATA(){
        // Files — первый пункт подсвечивается сам по себе (SidebarMenuAdapter,
        // initialSelectedPosition по умолчанию 0), отдельная строка тут больше не нужна.
    }
    private fun setupITEMSClock(){
        // Clock — первый пункт подсвечивается сам по себе (SidebarMenuAdapter,
        // initialSelectedPosition по умолчанию 0), отдельная строка тут больше не нужна.
    }
    /**
     * Проверка срабатывания будильника (roadmap, "Часы — UX-спецификация") — вызывается
     * из того же 300мс-цикла, что и обновление часов (onCreate), сверяет часы/минуты
     * [gameCalendar] (реальное время, только YEAR игровой) с выставленным будильником.
     * Совпадение сразу разоружает будильник — иначе сработает повторно на следующей
     * итерации цикла в той же самой минуте.
     */
    /** Функции уровня класса (не локальные closure в onCreate) — нужны и из
     * alarmChildrenNodes() (roadmap, этап 27, п.3), отдельной функции вне сетап-блока. */
    private fun updateAlarmStatusViews() {
        val alarm = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm
        if (alarmArmed) {
            alarm.tvClockAlarmStatus.text = getString(R.string.clock_alarm_status_on, String.format("%02d:%02d", alarmHour, alarmMinute))
            alarm.btnClockAlarmToggle.text = getString(R.string.clock_alarm_cancel)
        } else {
            alarm.tvClockAlarmStatus.text = getString(R.string.clock_alarm_status_off)
            alarm.btnClockAlarmToggle.text = getString(R.string.clock_alarm_set)
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
        val alarm = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm
        alarm.tvClockAlarmStatus.text = getString(R.string.clock_alarm_status_off)
        alarm.btnClockAlarmToggle.text = getString(R.string.clock_alarm_set)
        bindingMain.incLayoutClockFiredOverlay.tvClockFiredTitle.text = getString(R.string.clock_alarm_fired_title)
        bindingMain.incLayoutClockFiredOverlay.root.visibility = View.VISIBLE
        setClockFiredStopFocused(true)
        playClockFiredSound()
    }
    /** Фокус энкодера на Stop оверлея срабатывания (roadmap, этап 27 — "когда срабатывает
     * таймер или будильник... курсор попадает на Stop") — тот же приём "залитая область
     * +1px с каждой стороны", что и у Stop на STATUS/кнопок +/- SPECIAL/Skills, см.
     * setValueEditorButtonGrown(). */
    private fun setClockFiredStopFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutClockFiredOverlay.viewClockFiredStopFocus, focused)
    }
    /** Закрытие оверлея срабатывания — общее для тапа по Stop и ENCBTN, пока оверлей открыт
     * (roadmap, этап 27, см. handleBleCommand()). */
    private fun dismissClockFiredOverlay() {
        stopClockFiredSound()
        setClockFiredStopFocused(false)
        bindingMain.incLayoutClockFiredOverlay.root.visibility = View.GONE
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
        setClockFiredStopFocused(true)
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
        // Живая пересборка дерева энкодера (roadmap, этап 27, п.4) — общая точка для
        // resetTimer()/fireTimer()/таймера ранения/restore, см. refreshClockTimerEncoderChildren().
        refreshClockTimerEncoderChildren()
    }
    /** Общий старт — кнопка [Старт] (значения колёс ЧЧ:ММ:СС) и голосовая команда "таймер
     * N минут" (roadmap, этап 21 ч.2) переиспользуют один и тот же путь, а не дублируют
     * присвоение timerState/timerTargetEpochMillis по отдельности. No-op на 0 секунд —
     * ровно как раньше вело себя условие в самом обработчике кнопки. */
    private fun startPlainTimer(totalSeconds: Int) {
        if (totalSeconds <= 0) return
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        timerTargetEpochMillis = System.currentTimeMillis() + totalSeconds * 1000L
        timerState = TimerState.RUNNING
        timer.btnClockTimerPauseResume.text = getString(R.string.clock_timer_pause)
        timer.layoutClockTimerSetup.visibility = View.GONE
        timer.layoutClockTimerRunning.visibility = View.VISIBLE
        updateClockTimerLabel() // woundPhase == NONE здесь всегда — очищает подпись от предыдущего таймера ранения
        // startPlainTimer() — единственный переход IDLE->RUNNING, что не проходит через
        // syncClockTimerScreenVisibility() (roadmap, этап 27, п.4).
        refreshClockTimerEncoderChildren()
    }
    /** Пресеты +5/+10 мин (roadmap, "Часы — UX-спецификация") — функция уровня класса, не
     * локальная closure в onCreate: нужна и из timerChildrenNodes() (roadmap, этап 27, п.4). */
    private fun addTimerPresetMinutes(minutesToAdd: Int) {
        val totalMinutes = (timerHours * 60 + timerMinutes + minutesToAdd) % (24 * 60)
        timerHours = totalMinutes / 60
        timerMinutes = totalMinutes % 60
        timerHourWheel.scrollToValue(timerHours)
        timerMinuteWheel.scrollToValue(timerMinutes)
    }
    /** Общая пауза/возобновление — кнопка [Пауза] и голосовая команда "пауза"/"продолжи". */
    private fun pauseResumeTimer() {
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
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
    /** Общий сброс — кнопка [Сброс] и голосовая команда "стоп таймер" (roadmap, этап 21
     * ч.2 — таймер ранения ничем не отличается от обычного, отдельной voice-команды на
     * его остановку не нужно). Если сейчас идёт таймер ранения — равнозначен [Стоп] на
     * STATUS: не тихий обрыв без итога, а те же последствия (перевязка/лечение). Обычный
     * сброс — только когда woundPhase == NONE. */
    private fun resetTimer() {
        if (woundPhase != WoundPhase.NONE) {
            stopWoundTimerEarly()
        } else {
            timerState = TimerState.IDLE
            syncClockTimerScreenVisibility()
        }
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
    /** Тело кнопки [Выбрать] (roadmap, этап 27, п.2) — общее для тача и ENCBTN на узле
     * SELECT (melodySelectBackChildrenNodes()). */
    private fun commitMelodySelection() {
        sharedPreferences.edit().putInt(selectedRingtone_SPKey, melodyFocusedIndex).apply()
        updateMelodySelectedLabel()
        playMelodySelectedMarqueeOnce()
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
    /** TIME/ALARM/TIMER/STOPWATCH — переключение видимой панели справа (roadmap, "Единый
     * компонент бокового меню 3 уровня"). MELODY сюда не входит — отдельный полноэкранный
     * переход, см. openClockMelodyScreen(). Баг, найденный на энкодере: openClockMelodyScreen()
     * прячет ВЕСЬ сайдбар (layoutTabItemsClockButtonsContainer) и панель контента
     * (layoutTabItemsClockContent), не только саму Мелодию — раньше выйти можно было только
     * её собственной кнопкой [Назад] (closeClockMelodyScreen(), которая их и возвращает).
     * View.performClick(), на котором работает энкодер, не проверяет видимость — с
     * энкодера можно уйти с Мелодии на любой другой пункт напрямую, минуя [Назад], и без
     * восстановления этих двух контейнеров здесь весь экран Clock визуально пустеет. */
    private fun showClockContentPanel(key: String) {
        stopMelodyPreview()
        val clock = bindingMain.incLayoutTabItemsClock
        clock.layoutTabItemsClockButtonsContainer.visibility = View.VISIBLE
        clock.layoutTabItemsClockContent.visibility = View.VISIBLE
        clock.incLayoutTabItemsClockTime.root.visibility = if (key == "TIME") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockAlarm.root.visibility = if (key == "ALARM") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockTimer.root.visibility = if (key == "TIMER") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockStopwatch.root.visibility = if (key == "STOPWATCH") View.VISIBLE else View.GONE
        clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
    }
    private fun openClockMelodyScreen() {
        val clock = bindingMain.incLayoutTabItemsClock
        clock.layoutTabItemsClockButtonsContainer.visibility = View.GONE
        clock.layoutTabItemsClockContent.visibility = View.GONE
        clock.incLayoutTabItemsClockMelody.root.visibility = View.VISIBLE
        // Рамка обязана совпадать с тем, куда реально садится курсор энкодера при входе в
        // MELODY — а это всегда первый трек (см. melodyChildrenNodes(), "провал в MELODY
        // сразу приземляет курсор на первый трек"), не ранее ПОДТВЕРЖДЁННЫЙ Select-ом трек.
        // Найденный баг (roadmap, доработка после фидбека): раньше здесь читался
        // sharedPreferences.getInt(selectedRingtone_SPKey) — совпадало с курсором только
        // случайно, пока экран открывали исключительно через ENCBTN на TRACK_0 (тот сам
        // перезаписывал melodyFocusedIndex=0 СРАЗУ ПОСЛЕ этого вызова, см. его onHighlight),
        // но не через тач по сайдбару "Мелодия" — тот вызывает эту функцию напрямую, без
        // такой перезаписи следом, и рамка оставалась на прежнем/подтверждённом треке, а
        // курсор энкодера — на первом.
        melodyFocusedIndex = 0
        // Молча (без звука) — восстановление состояния экрана при входе, не выбор игрока.
        melodyAdapter.setSelectedPositionSilently(melodyFocusedIndex)
        updateMelodySelectedLabel()
    }
    private fun closeClockMelodyScreen() {
        stopMelodyPreview()
        val clock = bindingMain.incLayoutTabItemsClock
        clock.incLayoutTabItemsClockMelody.root.visibility = View.GONE
        clock.layoutTabItemsClockButtonsContainer.visibility = View.VISIBLE
        clock.layoutTabItemsClockContent.visibility = View.VISIBLE
    }
    /** Функции уровня класса (не локальные closure в onCreate) — нужны и из
     * stopwatchChildrenNodes() (roadmap, этап 27, п.4). */
    private fun toggleStopwatchStartPause() {
        val stopwatch = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch
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
    private fun resetStopwatch() {
        val stopwatch = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch
        stopwatchState = StopwatchState.IDLE
        stopwatchElapsedMillisAtPause = 0L
        stopwatch.tvClockStopwatchElapsed.text = "00:00:00"
        stopwatch.btnClockStopwatchStartPause.text = getString(R.string.clock_timer_start)
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
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_page).visibility = View.GONE

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
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_tutorial_page).visibility = View.GONE

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
        // Порядок должен совпадать с itemsMenuRoot() и bottomButtonsModify() выше. GEIGER —
        // см. applyModeGating()/itemsMenuRoot().
        val bottom = bindingMain.incLayoutTabItemsBottom
        return listOfNotNull(
            if (pipBoyMode != PipBoyMode.PHONE) Row2Item(bottom.btnItemsGeiger.text) { bottom.btnItemsGeiger.performClick() } else null,
            Row2Item(bottom.btnItemsMap.text) { bottom.btnItemsMap.performClick() },
            Row2Item(bottom.btnItemsJournal.text) { bottom.btnItemsJournal.performClick() },
            Row2Item(bottom.btnItemsClock.text) { bottom.btnItemsClock.performClick() },
        )
    }
    private fun dataRow2Items(): List<Row2Item> {
        // Порядок должен совпадать с dataMenuRoot() и bottomButtonsModify() выше. HOLOTAPES —
        // см. applyModeGating()/dataMenuRoot().
        val bottom = bindingMain.incLayoutTabDataBottom
        return listOfNotNull(
            Row2Item(bottom.btnDataMisc.text) { bottom.btnDataMisc.performClick() },
            if (pipBoyMode != PipBoyMode.PHONE) Row2Item(bottom.btnDataHolotapes.text) { bottom.btnDataHolotapes.performClick() } else null,
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
                    // item.onSelect() == performClick() на самой кнопке узла меню 2 уровня
                    // (statsRow2Items()/itemsRow2Items()/dataRow2Items()) — та сама
                    // синхронизирует menuNavigator (setRootCursor()) и сама же сразу
                    // проваливается на первый дочерний узел (activateSelected(), roadmap,
                    // этап 27 — доработка энкодер-эргономики, тап равносилен ENCBTN). Больше
                    // НЕ дублировать menuNavigator.setRootCursor(index) здесь следом — это
                    // заново схлопывало стек до одного уровня и отменяло тот самый провал,
                    // который onSelect() уже сделал (найденный баг: рамка на первом пункте
                    // бокового меню показывалась, а курсор энкодера при этом оставался в
                    // строке 2 — ENC после тапа листал соседние разделы, а не боковое меню).
                    item.onSelect()
                    renderRow2()
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
     * `MenuNavigator` и переключали контент через `MenuNode.onHighlight()`, но полоса строки 2
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
        // Тот же звук (cnd_rad_eff.wav), что и playConfirmAudio() ниже — раньше здесь был
        // отдельный вечно висящий в памяти MediaPlayer (mediaPlayerCRF) под тот же файл,
        // теперь оба места используют один ленивый create-play-release путь.
        playConfirmAudio()
        topLevelButtonsModify(menu)
        setupMainContent(menu)
        setupRow2(menu)
        enableDisableBottomButtons(false, listBottomButtons)
        enableDisableTopSwipe(false)
        sendBLEText(menu)
    }
    private fun menuOptionClickedBLE(menu: String){
        playConfirmAudio()
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
     * Единый компонент бокового меню 3 уровня (roadmap) — курсор/подсветка/звук теперь
     * SidebarMenuAdapter, не отдельная рамка-View. Курсор двигается тапом/энкодером
     * независимо от disabled/alpha (см. updateWoundButtonsUI()) — фидбек по итогам
     * тестирования: раньше подсвеченная кнопка при этом ещё и не гасла вместе с
     * остальными, что читалось как "эта кнопка работает", хотя клик по ней тоже давал
     * ошибку. [SidebarMenuItem.enabled] в адаптере теперь только затенение, не блокировка
     * тапа — см. SidebarMenuAdapter.kt.
     */
    private data class StatusWoundMeta(val key: String, val labelRes: Int, val action: () -> Unit)
    private val statusMeta = listOf(
        StatusWoundMeta("LIGHT", R.string.title_stats_wound_light) {
            startWoundTimer(WoundPhase.BLEED, WoundSeverity.LIGHT, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
        },
        StatusWoundMeta("HEAVY", R.string.title_stats_wound_heavy) {
            startWoundTimer(WoundPhase.BLEED, WoundSeverity.HEAVY, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
        },
        StatusWoundMeta("STUNNED", R.string.title_stats_stunned) {
            startWoundTimer(WoundPhase.STUNNED, null, STUN_DURATION_SECONDS)
        },
    )
    private lateinit var statusAdapter: SidebarMenuAdapter<String>
    private fun updateWoundButtonsUI() {
        // Затенение — одинаковое у всех трёх пунктов, следует за woundPhase (не блокирует
        // тап через SidebarMenuItem.enabled — см. SidebarMenuAdapter.kt: клик по недоступной
        // сейчас кнопке всё равно должен доехать до onSelect и дать звук ошибки, не молча
        // игнорироваться). Курсор (resetSelection=false) не трогаем — сюда попадают и после
        // тапа/энкодера (курсор уже там, где нужно), и после смены woundPhase без участия
        // игрока (эскалация — та явно двигает курсор сама, см. startWoundTimer()).
        if (::statusAdapter.isInitialized) {
            // statusSidebarItems() — не инлайн-реконструкция статуса из statusMeta напрямую:
            // та версия домалывала список без пункта "В меню" (roadmap, этап 27 — баг "Menu
            // пропадает при старте таймера"), т.к. он дописывается только в
            // statusSidebarItems()/pipBoyMode, единственном источнике истины для этого списка.
            statusAdapter.setItems(statusSidebarItems(), resetSelection = false)
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
        val cursorIndex = when {
            phase == WoundPhase.STUNNED -> 2
            woundSeverity == WoundSeverity.LIGHT -> 0
            else -> 1
        }
        statusAdapter.setSelectedPositionSilently(cursorIndex)
        applyWoundFace()
        updateWoundButtonsUI()
        refreshStatusEncoderChildren()
        timerState = TimerState.RUNNING
        timerTargetEpochMillis = System.currentTimeMillis() + durationSeconds * 1000L
        syncClockTimerScreenVisibility()
        updateWoundStatusLine()
        updateWoundCountdownText(durationSeconds)
        updateClockTimerLabel()
    }
    /** Вылечен — общий финал и для BANDAGE (успели), и для STUNNED (прошло/остановлено):
     * возврат к man_face, таймер снят. CRIPPLED по всем шести частям тела снимается тоже
     * (недосмотр, найден по фидбеку — "здоров" должно означать действительно здоров, не
     * здоров-но-с-переломом; `reviveCharacter()`/`applyReviveVisuals()` уже вели себя так
     * же, только для случая смерти). */
    private fun healWoundsToHealthy() {
        woundPhase = WoundPhase.NONE
        applyWoundFace()
        updateWoundButtonsUI()
        refreshStatusEncoderChildren()
        updateWoundStatusLine()
        timerState = TimerState.IDLE
        syncClockTimerScreenVisibility()
        setCrippledHead(false)
        setCrippledTorso(false)
        setCrippledLeftArm(false)
        setCrippledRightArm(false)
        setCrippledLeftLeg(false)
        setCrippledRightLeg(false)
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
        refreshStatusEncoderChildren()
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
        refreshStatusEncoderChildren()
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
    // set*() — явная установка (не инверсия), нужна голосовым командам "ранение в
    // <часть тела>" (roadmap, этап 21 ч.2): голосовая команда должна быть идемпотентной,
    // повторный вызов с тем же значением не должен ничего переключать обратно. toggle*()
    // (тач по фигуре) остаются тонкими обёртками поверх тех же set*().
    private fun setCrippledHead(crippled: Boolean) {
        crippledHead = crippled
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyHead, cnd.tvTabStatusCndPipboyHeadHpCrippled, crippledHead, R.drawable.man_head, R.drawable.head_broken)
    }
    private fun toggleCrippledHead() = setCrippledHead(!crippledHead)
    private fun setCrippledTorso(crippled: Boolean) {
        crippledTorso = crippled
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyTorso, cnd.tvTabStatusCndPipboyTorsoHpCrippled, crippledTorso, R.drawable.torso, R.drawable.torso_broken)
    }
    private fun toggleCrippledTorso() = setCrippledTorso(!crippledTorso)
    private fun setCrippledLeftArm(crippled: Boolean) {
        crippledLeftArm = crippled
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftArm, cnd.tvTabStatusCndPipboyLeftArmHpCrippled, crippledLeftArm, R.drawable.man_arm_left, R.drawable.left_arm_broken)
    }
    private fun toggleCrippledLeftArm() = setCrippledLeftArm(!crippledLeftArm)
    private fun setCrippledRightArm(crippled: Boolean) {
        crippledRightArm = crippled
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyRightArm, cnd.tvTabStatusCndPipboyRightArmHpCrippled, crippledRightArm, R.drawable.man_arm_right, R.drawable.right_arm_broken)
    }
    private fun toggleCrippledRightArm() = setCrippledRightArm(!crippledRightArm)
    private fun setCrippledLeftLeg(crippled: Boolean) {
        crippledLeftLeg = crippled
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftLeg, cnd.tvTabStatusCndPipboyLeftLegHpCrippled, crippledLeftLeg, R.drawable.man_leg_left, R.drawable.left_leg_broken)
    }
    private fun toggleCrippledLeftLeg() = setCrippledLeftLeg(!crippledLeftLeg)
    private fun setCrippledRightLeg(crippled: Boolean) {
        crippledRightLeg = crippled
        val cnd = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        applyCrippledVisual(cnd.imgTabStatusCndPipboyRightLeg, cnd.tvTabStatusCndPipboyRightLegHpCrippled, crippledRightLeg, R.drawable.man_leg_right, R.drawable.right_leg_broken)
    }
    private fun toggleCrippledRightLeg() = setCrippledRightLeg(!crippledRightLeg)
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
    private fun playTickAudio(){
        val mediaPlayerItemSelect = MediaPlayer.create(this, R.raw.item_select)
        mediaPlayerItemSelectList.add(mediaPlayerItemSelect)
        mediaPlayerItemSelect.start()
        mediaPlayerItemSelect.setOnCompletionListener {
            it.release()
            mediaPlayerItemSelectList.remove(it)
        }
    }
    private fun playButtonAudio(){
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
    private fun playConfirmAudio(){
        val mediaPlayerCndRadEff = MediaPlayer.create(applicationContext, R.raw.cnd_rad_eff)
        mediaPlayerCndRadEffList.add(mediaPlayerCndRadEff)
        mediaPlayerCndRadEff.start()
        mediaPlayerCndRadEff.setOnCompletionListener {
            it.release()
            mediaPlayerCndRadEffList.remove(it)
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
                        playTickAudio()
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
        playButtonAudio()
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
    private fun localizePerk(perk: Map<String, String>): Map<String, String> {
        val id = perk["id"]
        val nameResId = resources.getIdentifier("perk_${id}_name", "string", packageName)
        val descResId = resources.getIdentifier("perk_${id}_desc", "string", packageName)
        return perk + mapOf(
            "name" to if (nameResId != 0) getString(nameResId) else perk["name"].orEmpty(),
            "desc" to if (descResId != 0) getString(descResId) else perk["desc"].orEmpty(),
        )
    }
    private val localizedPerks: List<Map<String, String>> by lazy {
        perks.map { perk -> localizePerk(perk) }
    }
    /** Единый компонент бокового меню 3 уровня (roadmap) — SidebarMenuAdapter вместо
     * PerkAdapter.kt. Список уже отфильтрован (filteredPerksList) до разблокированных
     * игроком перков — в старом PerkAdapter была ещё гейтинг-проверка "perk id in
     * selectedPerkArray" внутри onBindViewHolder, но раз в список и так попадают только
     * такие перки, проверка была тавтологией (мёртвый код), не переносится. */
    /** Превью описания/иконки Perks при движении курсора (ENC/тач) — вынесено из
     * STATSPerksSetup() в отдельный метод (roadmap, этап 27 — доработка энкодер-эргономики,
     * тот же баг и то же решение, что у showSpecialPreview()/showSkillPreview()): нужен и
     * onSelect адаптера (тап), и onHighlight узла в perksChildrenNodes() (просто наведение
     * курсора энкодером) — тот больше не зовёт громкий perksAdapter.selectPosition(). */
    private fun showPerkDescription(perk: Map<String, String>) {
        bindingMain.incLayoutTabStatsPerks.tvPerksDescriptionsText.text = perk["desc"] ?: "No description available"
        bindingMain.incLayoutTabStatsPerks.imgPerksSelected.setImageResource(resources.getIdentifier(perk["icon"], "drawable", packageName))
        // Сброс прокрутки на новую запись (roadmap, этап 27 — "листание длинных файлов")
        // — иначе переключение на другой перк после того, как энкодер проскроллил
        // предыдущее описание вниз, показало бы новый текст с той же смещённой позиции.
        bindingMain.incLayoutTabStatsPerks.scrollviewPerksDescriptionsText.scrollTo(0, 0)
    }
    private fun STATSPerksSetup(recyclerView: RecyclerView){
        val selectedSTATSPerksString = sharedPreferences.getString("selectedSTATSPerksArray", "1")
        val selectedSTATSPerksArray: Array<String> = selectedSTATSPerksString!!.split(",").toTypedArray()
        // Фильтруем СНАЧАЛА (по сырому perks, без локализации), локализуем ТОЛЬКО отобранное
        // (roadmap, этап 27) — не через localizedPerks (весь список, ~140 перков, каждый —
        // 2 вызова resources.getIdentifier(), заметно дороже одного отфильтрованного
        // десятка). localizedPerks остаётся as is (полный список, by lazy) — нужен целиком
        // только экрану фильтра (чекбоксы/поиск по всем перкам), который открывается не
        // сразу, а по отдельному клику — там расчёт по-прежнему честно ленивый.
        val filteredPerksList = perks.filter { perk -> perk["id"] in selectedSTATSPerksArray }.map { localizePerk(it) }
        perksRealItemCount = filteredPerksList.size

        val realItems = filteredPerksList.map { perk -> SidebarMenuItem(payload = perk, label = perk["name"] ?: "") }
        perksAdapter = SidebarMenuAdapter(
            items = if (pipBoyMode != PipBoyMode.PHONE) realItems + perksBackSidebarItem() else realItems,
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера с тачем (roadmap, этап 27 —
                // доработка энкодер-эргономики), не menuNavigator.syncCursor() — тот чинит
                // курсор только ВНУТРИ уже активного уровня (см. doc у syncEncoderPath()).
                if (item.payload["id"] == SIDEBAR_BACK_PAYLOAD) {
                    playConfirmAudio()
                    syncStatsEncoderPath("PERKS", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    showPerkDescription(item.payload)
                    // Тап равносилен ENCBTN на этом пункте (roadmap, этап 27 — доработка
                    // энкодер-эргономики Perks/Files): курсор проваливается сразу в прокрутку
                    // описания — silently, превью уже применено строкой выше,
                    // activateSelected() входит в ValueEditor узла.
                    syncStatsEncoderPathSilently("PERKS", listOf(position))
                    menuNavigator.activateSelected()
                }
            },
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = perksAdapter
        filteredPerksList.firstOrNull()?.let { showPerkDescription(it) }
        // Список фильтруется (не фиксированной длины, в отличие от statusMeta/specialMeta/
        // skillsMeta) — дерево энкодера нужно пересобрать каждый раз, когда список меняется
        // (не только при первом входе в STATS), roadmap этап 27. No-op, если игрок сейчас не
        // внутри списка Perks (см. MenuNavigator.replaceChildrenOf).
        menuNavigator.replaceChildrenOf("PERKS", perksChildrenNodes())
    }
    /** Пункт "В меню" со спец-payload для Perks — SidebarMenuItem<Map<String, String>>, не
     * SidebarMenuItem<String> (см. [backSidebarItem]): payload у Perks — карта полей
     * перка (id/name/desc/icon), нужен эквивалентный дозорный маркер того же типа. */
    private fun perksBackSidebarItem(): SidebarMenuItem<Map<String, String>> =
        SidebarMenuItem(payload = mapOf("id" to SIDEBAR_BACK_PAYLOAD), label = getString(R.string.sidebar_menu_back))
    /** Дети узла PERKS дерева энкодера (roadmap, этап 27) — как и сам список, пересчитывается
     * заново при каждом вызове (не кэшируется), т.к. perksRealItemCount/perksAdapter уже
     * отражают текущий фильтр к моменту вызова (STATSPerksSetup() всегда обновляет их первым).
     * Каждый перк — чистое превью (onHighlight обновляет описание/иконку через
     * setSelectedPositionSilently() + showPerkDescription(), не громкий perksAdapter.selectPosition()
     * — roadmap, доработка после фидбека: тот сам зовёт onSelect адаптера, то есть простое
     * наведение курсора энкодером срабатывало как ENCBTN, сразу проваливаясь в прокрутку
     * описания), без onActivate/valueEditor: пунктам нечего "активировать", ENCBTN на них
     * просто поднимается наверх, как и было в дереве по умолчанию до появления onActivate/
     * valueEditor у других экранов. */
    private fun perksChildrenNodes(): List<MenuNode> {
        return (0 until perksRealItemCount).map { index ->
            // ENCBTN на перке — не подъём наверх (лист без children/valueEditor/onActivate
            // раньше проваливался в запасной "подняться к родителю", roadmap, этап 27 —
            // находка "ENCBTN на любом перке поднимает наверх") и не no-op, а вход в
            // прокрутку описания перка (roadmap, этап 27 — "листание длинных файлов"),
            // повторный ENCBTN — назад к списку перков.
            MenuNode(
                id = "PERK_$index",
                onHighlight = {
                    playTickAudio()
                    perksAdapter.setSelectedPositionSilently(index)
                    perksAdapter.currentItems().getOrNull(index)?.let { showPerkDescription(it.payload) }
                },
                valueEditor = recordScrollValueEditor(bindingMain.incLayoutTabStatsPerks.scrollviewPerksDescriptionsText),
            )
        } + menuBackNode(
            pipBoyMode,
            onHighlight = { perksAdapter.setSelectedPositionSilently(perksRealItemCount) },
            onBeforePop = { perksAdapter.flashPressAnimation(perksRealItemCount) },
        )
    }
    /***********************************************************************************************************
     * SHARED PREFERENCES
     **********************************************************************************************************/
    private fun saveValues(etSettings1: String, uiColourID: Int, dateFormat: Int, showTutorial: Boolean, trueFullscreen: Boolean, gameYear: Int, playerRegion: String, languageID: Int, ambientSoundEnabled: Boolean) {
        sharedPreferences.edit()
            .putString(playerName_SPKey, etSettings1)
            .putString(playerRegion_SPKey, playerRegion)
            .putInt(playerUIColour_SPKey, uiColourID)
            .putInt(dateFormat_SPKey, dateFormat)
            .putBoolean("ShowTutorial", showTutorial)
            .putBoolean("TrueFullscreen", trueFullscreen)
            .putInt(gameYear_SPKey, gameYear)
            .putInt(appLanguage_SPKey, languageID)
            .putBoolean("AmbientSoundEnabled", ambientSoundEnabled)
            .apply()
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

    /**
     * Текст + акцент темы + подпись кнопки [Далее]/[Готово] для текущей страницы тьюториала
     * (roadmap, этап 25). Последняя страница списка — [Готово] вместо [Далее], и [Пропустить]
     * прячется рядом (спека: на последнем экране только одна кнопка).
     */
    private fun showTutorialPage(index: Int) {
        tutorialPageIndex = index
        val page = bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage
        page.tvTutorialPage.text = getString(tutorialPageStringRes[index])
        page.tvTutorialPage.setTextColor(currentWizardAccentColor())
        val isLastPage = index == tutorialPageStringRes.lastIndex
        val nextButton = bindingMain.incLayoutTabTutorialBase.btnNextpage
        val closeButton = bindingMain.incLayoutTabTutorialBase.btnTutorialClose
        nextButton.text = getString(if (isLastPage) R.string.wizard_done else R.string.wizard_next)
        // GONE — на последней странице [Пропустить] убирается из разметки совсем, чтобы
        // [Готово] встало вплотную к чекбоксу "Don't show again" под ним (пользовательская
        // правка), а не оставляло зазор под невидимую кнопку.
        closeButton.visibility = if (isLastPage) View.GONE else View.VISIBLE
        if (!isLastPage) {
            equalizeButtonWidths(nextButton, closeButton)
        }
    }
    /**
     * Открывает страницы тьюториала с указанной, минуя Welcome/дисклеймер — используется и
     * после [Далее] на Welcome (startIndex=0), и повторным входом из Settings ("Обучение" →
     * "Открыть", тоже startIndex=0, но без дисклеймера — тот только про юридическое
     * уведомление при первом запуске).
     */
    private fun openTutorialContent(startIndex: Int) {
        bindingMain.constraintlayoutTutorial.visibility = View.VISIBLE
        // setupMainContent()/setupMainContentBLE() (переключение STATS/ITEMS/DATA) защитно
        // прячут inc_layout_tab_tutorial_base целиком при каждом обычном переключении вкладок
        // — обычный побочный эффект их общего "спрятать все оверлеи" сброса. После хотя бы
        // одного переключения за сессию (т.е. всегда, если тьюториал открыт не с самого
        // холодного старта) он остаётся GONE, и одной видимости constraintlayoutTutorial
        // недостаточно — сам include нужно возвращать явно.
        bindingMain.incLayoutTabTutorialBase.root.visibility = View.VISIBLE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage.root.visibility = View.VISIBLE
        // Чекбокс остаётся видимым и на страницах контента, не только на Welcome — он общий
        // элемент колонки кнопок (layout_tutorial_buttons), а не часть include с текстом.
        // Прятать его нельзя: LinearLayout колонки центрируется по вертикали (gravity=center),
        // и с пропавшим третьим элементом [Далее]/[Пропустить] съезжали относительно того,
        // где они стоят на Welcome.
        showTutorialPage(startIndex)
    }
    /**
     * Закрывает тьюториал ([Пропустить] на любой странице, либо [Готово] на последней) и
     * возвращает разметку в исходное состояние — следующий показ (обычный запуск или
     * повторный вход из Settings) снова начинается с Welcome.
     */
    private fun closeTutorial() {
        bindingMain.constraintlayoutTutorial.visibility = View.GONE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage.root.visibility = View.GONE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.VISIBLE
        bindingMain.incLayoutTabTutorialBase.btnTutorialClose.visibility = View.VISIBLE
        bindingMain.incLayoutTabTutorialBase.btnNextpage.text = getString(R.string.wizard_next)
        tutorialPageIndex = -1
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

        // Полный экран на старте (roadmap, этап 28, найденный баг) — раньше здесь стоял
        // loadViewState(), подхватывавший уменьшенный размер из прошлой сессии ДО того, как
        // решено, какой режим вообще активен (pipBoyMode тут всегда PHONE по умолчанию — ни
        // один путь не восстанавливает аппаратный режим из SharedPreferences при обычном
        // холодном старте, только restoreAppState() при убийстве процесса, через Bundle). В
        // итоге Welcome/выбор режима на каждом новом запуске могли показываться в уменьшенной
        // рамке от прошлой настройки DISPLAY AREA, хотя сама настройка имеет смысл только
        // внутри мастера аппаратного режима, который в этом прогоне ещё даже не открывался.
        // resetToFullScreen() — не только визуально сбрасывает, но и сохраняет сброс в
        // SharedPreferences, так что любой другой loadViewState() в этой же сессии
        // (skipWizardToMainScreenDebug() и т.п.) больше не подхватит устаревшее значение.
        resetToFullScreen()

        // Тема (selected_button/selectedRowButton и т.п., applyAppTheme()) должна быть
        // применена ДО setupModeSelectScreen()/setupPipBoy2000Wizard() — экран выбора
        // режима строит SidebarMenuAdapter с текущим selected_button сразу при вызове, а не
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

        //Keep phone screen active
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // MEDIA SETUP — намеренно пусто. Все звуки/фоновый эмбиент теперь создаются лениво, в
        // момент реального использования (roadmap, "Рефакторинг кода" — память фонового
        // процесса), а не все разом здесь при каждом старте. См.
        // playConfirmAudio()/playButtonAudio()/playLightOnAudio()/playLightOffAudio()
        // (одноразовые UI-звуки, create-play-release), startAmbientBackgroundSound()
        // (живёт дольше одного проигрывания, до явного стопа).

        //BOTTOM BUTTON SETUP (DEFAULT STATUS)
        bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks)


        // SPECIAL — единый компонент бокового меню 3 уровня (см. Skills выше, тот же приём).
        // Пункт "В меню" (только PipBoy 2000/3000, см. specialSidebarItems()) требует
        // отдельной ветки в onSelect ДО поиска по specialMeta — иначе first{} на payload,
        // которого нет ни в одной реальной характеристике, упал бы с исключением.
        specialAdapter = SidebarMenuAdapter(
            items = specialSidebarItems(),
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера с тачем (roadmap, этап 27 —
                // доработка энкодер-эргономики), не menuNavigator.syncCursor() — тот чинит
                // курсор только ВНУТРИ уже активного уровня (см. doc у syncEncoderPath()).
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    syncStatsEncoderPath("SPECIAL", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    showSpecialPreview(specialMeta.first { it.key == item.payload })
                    // Тап равносилен ENCBTN на этом пункте (roadmap, этап 27 — доработка
                    // энкодер-эргономики): курсор проваливается сразу в редактирование
                    // значения, следующий ENC:+/-1 сразу листает его — silently, превью выше
                    // тач уже применил сам, activateSelected() входит в ValueEditor узла.
                    syncStatsEncoderPathSilently("SPECIAL", listOf(position))
                    menuNavigator.activateSelected()
                }
            },
        )
        bindingMain.incLayoutTabStatsSpecial.scrollTabSpecial.layoutManager = LinearLayoutManager(this)
        bindingMain.incLayoutTabStatsSpecial.scrollTabSpecial.adapter = specialAdapter

        // Skills — единый компонент бокового меню 3 уровня (roadmap, "Единый компонент
        // бокового меню 3 уровня") вместо 13 hand-copied XML-блоков + 13 setOnClickListener.
        // onSelect ниже — то же самое, что раньше делал каждый из 13 setOnClickListener
        // (картинка + описание + selectedSKILL), кроме самой подсветки/звука — это теперь
        // общая забота SidebarMenuAdapter. "В меню" — та же ветка, что у SPECIAL выше.
        skillsAdapter = SidebarMenuAdapter(
            items = skillsSidebarItems(),
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера с тачем — тот же приём, что у
                // SPECIAL выше.
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    syncStatsEncoderPath("SKILLS", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    showSkillPreview(skillsMeta.first { it.key == item.payload })
                    // Тап равносилен ENCBTN — тот же приём, что у SPECIAL выше.
                    syncStatsEncoderPathSilently("SKILLS", listOf(position))
                    menuNavigator.activateSelected()
                }
            },
        )
        bindingMain.incLayoutTabStatsSkills.scrollTabSkills.layoutManager = LinearLayoutManager(this)
        bindingMain.incLayoutTabStatsSkills.scrollTabSkills.adapter = skillsAdapter

        // Status — единый компонент бокового меню 3 уровня. playSelectSound молчит
        // (no-op) — звук решает сам onSelect (item_select на успешном тапе, звук ошибки на
        // недоступном сейчас действии, см. StatusWoundMeta/WoundPhase выше). enabled у всех
        // трёх пунктов одинаковый и следует за woundPhase — обновляется в
        // updateWoundButtonsUI(), не тут: тут только начальное состояние при первом показе.
        // "В меню" дизейблится вместе с LIGHT/HEAVY/STUNNED, пока актуален таймер ранения
        // (roadmap, этап 27) — тач по нему тогда просто даёт звук ошибки, тем же способом,
        // что и по трём кнопкам статуса ниже (энкодер до него в это время не доедет вообще,
        // statusChildrenNodes() убирает "В меню" из дерева совсем).
        statusAdapter = SidebarMenuAdapter(
            items = statusSidebarItems(),
            selectedBackgroundRes = selected_button,
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера с тачем (roadmap, этап 27 —
                // доработка энкодер-эргономики), не menuNavigator.syncCursor() — тот чинит
                // курсор только ВНУТРИ уже активного уровня, здесь курсор должен доехать
                // сюда, даже если энкодер был в меню 2 уровня (не провалился в STATUS вовсе)
                // или в совсем другой ветке (см. doc у syncEncoderPath()).
                if (woundPhase != WoundPhase.NONE) {
                    // LIGHT/HEAVY/STUNNED и "В меню" недоступны, пока активен таймер ранения/
                    // оглушения — курсор энкодера в это время должен быть на STOP, единственном
                    // реальном действии в дереве (statusChildrenNodes()), а не оставаться там,
                    // где был до тапа.
                    playErrorAudio()
                    syncStatsEncoderPath("STATUS", listOf(0))
                } else if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    playTickAudio()
                    syncStatsEncoderPath("STATUS", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    val meta = statusMeta.first { it.key == item.payload }
                    // playConfirmAudio(), не playTickAudio() — звук нажатия
                    // (roadmap, этап 27), тот же, что у +/- в SPECIAL/Skills. Листание
                    // (просто перемещение курсора) — playTickAudio(), см. onHighlight
                    // в statusChildrenNodes() выше.
                    playConfirmAudio()
                    // Silently — meta.action() (startWoundTimer()) сама тут же перестраивает
                    // детей STATUS и громко переставляет курсор на новый STOP через
                    // refreshStatusEncoderChildren(); здесь достаточно гарантировать, что
                    // энкодер уже внутри ветки STATUS к этому моменту (иначе replaceChildrenOf()
                    // там — no-op).
                    syncStatsEncoderPathSilently("STATUS", listOf(position))
                    meta.action()
                }
            },
        )
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.layoutManager = LinearLayoutManager(this)
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.adapter = statusAdapter

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
        // Шкала громкости радио — тач (roadmap, этап 28): раньше был нередактируемый
        // ProgressBar, теперь SeekBar с перетаскиванием. radioVolume — то же самое чисто
        // экранное представление, что и у VOLUME:±N с энкодера ESP32 (applyRadioVolumeDelta()),
        // не авторитетное значение — тач просто выставляет его абсолютно, без похода на
        // ESP32 (протокол не поддерживает set-громкости с телефона, см.
        // PipBoy_BLE_Protocol_v0.2.md, раздел 5). Звук — один раз по отпусканию пальца
        // (onStopTrackingTouch), не на каждое изменение progress во время протаскивания.
        bindingMain.incLayoutTabDataRadio.radioVolumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                radioVolume = progress
                bindingMain.incLayoutTabDataRadio.tvRadioVolumeValue.text = String.format(Locale.US, "%d%%", radioVolume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                playConfirmAudio()
            }
        })
        bindingMain.incLayoutHeaderToplevel.btnHeaderSettings.setOnClickListener{
            playButtonAudio()
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
         * DISCLAIMER / TUTORIAL (Welcome — переиспользует старый Welcome-экран Tutorial, см.
         * roadmap "Дисклеймер при запуске — UX-спецификация". [Далее] с Welcome ведёт в 7
         * страниц тьюториала — roadmap, этап 25, "Туториалы по функциям").
         **********************************************************************************************************/
        if (sharedPreferences.getBoolean("ShowTutorial", true)) {
            bindingMain.constraintlayoutTutorial.visibility = View.VISIBLE
        } else {
            bindingMain.constraintlayoutTutorial.visibility = View.GONE
        }

        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.tvTutorialWelcome.setTextColor(currentWizardAccentColor())
        setWizardButtonState(bindingMain.incLayoutTabTutorialBase.btnNextpage, selected = false)
        setWizardButtonState(bindingMain.incLayoutTabTutorialBase.btnTutorialClose, selected = false)
        equalizeButtonWidths(
            bindingMain.incLayoutTabTutorialBase.btnNextpage,
            bindingMain.incLayoutTabTutorialBase.btnTutorialClose
        )

        // [Далее] — на Welcome открывает страницы тьюториала с первой; на любой странице
        // контента, кроме последней, просто листает дальше; на последней странице кнопка уже
        // переименована в [Готово] (см. showTutorialPage()) и закрывает тьюториал целиком.
        bindingMain.incLayoutTabTutorialBase.btnNextpage.setOnClickListener {
            playButtonAudio()
            when {
                tutorialPageIndex == -1 -> openTutorialContent(0)
                tutorialPageIndex < tutorialPageStringRes.lastIndex -> showTutorialPage(tutorialPageIndex + 1)
                else -> closeTutorial()
            }
        }

        // [Пропустить] — на любой странице (Welcome или контент) закрывает тьюториал целиком,
        // не долистывая до конца (roadmap-спека).
        bindingMain.incLayoutTabTutorialBase.btnTutorialClose.setOnClickListener {
            playButtonAudio()
            // Чекбокс живёт только на Welcome и инвертирован относительно чекбокса в Settings
            // ("Больше не показывать" вместо "Показывать обучение при запуске") — оба
            // читают/пишут один и тот же ключ ShowTutorial, поэтому mirror isChecked()
            // напрямую нельзя, см. roadmap "Дисклеймер при запуске — UX-спецификация". Если
            // Skip нажат уже на странице контента, чекбокс скрыт и не менялся пользователем
            // с момента ухода с Welcome — читаем его текущее (неизменное) состояние, поведение
            // то же самое, что и на самом Welcome.
            showTutorialBool = !bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.isChecked()
            sharedPreferences.edit().putBoolean("ShowTutorial", showTutorialBool).apply()
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings.setChecked(showTutorialBool)
            closeTutorial()
        }

        // Повторный вход в тьюториал из Settings ("Обучение" → "Открыть", roadmap этап 25) —
        // сразу с первой страницы контента, минуя Welcome/дисклеймер (тот отвечает только за
        // юридическое уведомление при первом запуске, не за сам тьюториал).
        bindingMain.incLayoutSettingsGlobal.btnSettingsOpenTutorial.setOnClickListener {
            playButtonAudio()
            openTutorialContent(0)
            if (bindingMain.incLayoutSettingsGlobal.root.visibility == View.VISIBLE) {
                bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
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
            playButtonAudio()
            // Откатываем несохранённые правки чекбоксов (см. filterSelectionSnapshot) —
            // saveSelectedItems() не вызывается, персистентные настройки и видимый список
            // Perks не трогаются.
            when(filteringMenu){
                "PERKS" -> selectedFilterSTATSPerks = filterSelectionSnapshot.toMutableSet()
            }
            closeFilterScreen()
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationSelect.setOnClickListener{
            playButtonAudio()
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, localizedPerks, true)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationClear.setOnClickListener{
            playButtonAudio()
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, localizedPerks, false)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationFilter.setOnClickListener{
            playButtonAudio()
            val filterText = bindingMain.incLayoutFilterModification.etFilterModificationValue.text.toString()

            when(filteringMenu){
                "PERKS" -> filterList(localizedPerks, filterText)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationSave.setOnClickListener{
            playButtonAudio()
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
            // Синхронизация энкодера с тачем по нижним кнопкам (roadmap, этап 27 — та же
            // находка, что и у строки 2 шапки выше: без этого MenuNavigator продолжал бы
            // считать курсор там, где он был до тапа). Индекс — позиция в statsMenuRoot().
            menuNavigator.setRootCursor(0)
            // Рамка/прицел бокового меню гаснет (roadmap, этап 27 — доработка энкодер-
            // эргономики): курсор энкодера сейчас на самом узле STATUS (строка 2), ещё не
            // внутри списка — рамка не должна показывать пункт 0 как уже выбранный, пока
            // мы туда явно не провалились.
            statusAdapter.clearSelection()
            setWoundStopButtonFocused(false)
            setDeadReviveFocused(false)
            setAllCrippledFocusesHidden()
            // Реальный тап равносилен ENCBTN (roadmap, этап 27, п.3) — сразу проваливается
            // на первый дочерний узел бокового меню. ENC-перебор строки 2 (см.
            // simulateEncoderTabHighlight()) видит encoderTabHighlight=true и молча
            // останавливается здесь, рамка остаётся погашенной (см. выше).
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        // Клики по LIGHT/HEAVY/STUNNED — теперь внутри SidebarMenuAdapter (statusAdapter,
        // см. выше), 3 setOnClickListener на кнопку тут больше не нужны.
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.btnTabStatusWoundStop.setOnClickListener {
            playButtonAudio()
            stopWoundTimerEarly()
            // Курсор энкодера следует за тачем (roadmap, этап 27 — доработка энкодер-
            // эргономики): BLEED -> BANDAGE сохраняет STOP (индекс 0 нового дерева STATUS),
            // BANDAGE/STUNNED -> здоров возвращает в боковое меню (тоже индекс 0, LIGHT) —
            // woundPhase к этому моменту уже обновлён внутри stopWoundTimerEarly(), безусловный
            // переход работает независимо от того, где энкодер был до тапа (см. syncEncoderPath()).
            syncStatsEncoderPath("STATUS", listOf(0))
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
        // Индексы ниже — порядок узлов statusChildrenNodes() при активном ранении: STOP(0),
        // BODYPART_HEAD(1), BODYPART_LEFT_ARM(2), BODYPART_TORSO(3), BODYPART_RIGHT_ARM(4),
        // BODYPART_LEFT_LEG(5), BODYPART_RIGHT_LEG(6) — roadmap, этап 27, доработка энкодер-
        // эргономики: "тап по конечности переключает курсор энкодера на тапнутую конечность".
        // No-op вне активного ранения — этих узлов тогда в дереве STATUS вообще нет (см. doc
        // у syncEncoderPath()), CRIPPLED всё равно переключается тапом независимо от таймера.
        setupFigureTouchTarget(cndContentSetup.layoutTabStatusCndPipboy) {}
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyHead) {
            toggleCrippledHead()
            syncStatsEncoderPath("STATUS", listOf(1))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyTorso) {
            toggleCrippledTorso()
            syncStatsEncoderPath("STATUS", listOf(3))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyLeftArm) {
            toggleCrippledLeftArm()
            syncStatsEncoderPath("STATUS", listOf(2))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyRightArm) {
            toggleCrippledRightArm()
            syncStatsEncoderPath("STATUS", listOf(4))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyLeftLeg) {
            toggleCrippledLeftLeg()
            syncStatsEncoderPath("STATUS", listOf(5))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyRightLeg) {
            toggleCrippledRightLeg()
            syncStatsEncoderPath("STATUS", listOf(6))
        }
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
        cndContentSetup.viewWoundStopFocus.backgroundTintList = woundAccentTint
        cndContentSetup.viewDeadReviveFocus.backgroundTintList = woundAccentTint
        cndContentSetup.viewCrippledHeadFocus.backgroundTintList = woundAccentTint
        cndContentSetup.viewCrippledTorsoFocus.backgroundTintList = woundAccentTint
        cndContentSetup.viewCrippledLeftArmFocus.backgroundTintList = woundAccentTint
        cndContentSetup.viewCrippledRightArmFocus.backgroundTintList = woundAccentTint
        cndContentSetup.viewCrippledLeftLegFocus.backgroundTintList = woundAccentTint
        cndContentSetup.viewCrippledRightLegFocus.backgroundTintList = woundAccentTint

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
            menuNavigator.setRootCursor(1)
            specialAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        // Клики по пунктам SPECIAL — теперь внутри SidebarMenuAdapter (specialAdapter, см.
        // выше), 7 setOnClickListener на кнопку тут больше не нужны.

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
        bindingMain.incLayoutTabStatsSpecial.viewSpecialValueFocus.backgroundTintList = specialValueButtonsAccentTint



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
            menuNavigator.setRootCursor(2)
            skillsAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        // Клики по пунктам Skills — теперь внутри SidebarMenuAdapter (skillsAdapter, см.
        // выше), 13 setOnClickListener на кнопку тут больше не нужны.

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
        bindingMain.incLayoutTabStatsSkills.viewSkillValueFocus.backgroundTintList = skillValueButtonsAccentTint


        /*
        ////////////////////////////////////////////////////////
        STATS - PERKS MENU
        */
        // Построить сразу здесь, не только лениво по клику на вкладку (roadmap, этап 27) —
        // иначе к моменту первой сборки statsMenuRoot() (finishPhoneModeSetup()/
        // finishBootSequence(), задолго до первого клика по Perks) perksRealItemCount ещё
        // 0, и узел PERKS замораживает единственный пункт "В меню" навсегда: MenuNode.children
        // — обычный val, а не ленивый геттер, повторный вызов STATSPerksSetup() из клика
        // (см. ниже) уже не перестраивает однажды построенный узел ROOT-уровня. Не тяжело —
        // STATSPerksSetup() локализует только отфильтрованные перки (обычно единицы), не
        // весь список ~140 (тот остаётся ленивым, localizedPerks, нужен только экрану
        // фильтра).
        STATSPerksSetup(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
        bindingMain.incLayoutTabStatsBottom.btnStatsPerks.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsPerks, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.VISIBLE
            menuNavigator.setRootCursor(3)
            // STATSPerksSetup() пересобирает perksAdapter заново (roadmap, этап 27 —
            // фильтруемый список, не фиксированной длины, как statusMeta/specialMeta/
            // skillsMeta) — свежий адаптер всегда стартует с подсвеченным пунктом 0,
            // clearSelection() ниже гасит эту рамку молча, пока курсор энкодера реально не
            // провалится в список (см. ту же находку у Status/SPECIAL/Skills выше).
            STATSPerksSetup(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
            perksAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        bindingMain.incLayoutTabStatsPerks.btnPerksFilter.setOnClickListener {
            openPerksFilter()
        }


        // Тематизация Save/Cancel/Change/импорт (roadmap, "Редизайн экрана фильтра —
        // UX-спецификация") — те же PipWizardButtonStyle-кнопки, что у мастера: нейтральная
        // заливка в разметке, акцент темы — backgroundTintList кодом. [X] убран (roadmap,
        // "Редизайн Settings", этап 26) — Cancel теперь и закрывает, и явно сигнализирует
        // "без сохранения", вместо неявного сигнала через нейтральный крестик. Map/Voice
        // Commands — свои кнопки-переходы и [X] тоже убраны (roadmap, "Редизайн Settings" —
        // правки по подразделам), содержимое импорта встроено в раздел напрямую.
        val settingsAccent = currentWizardAccentColor()
        listOf(
            bindingMain.incLayoutSettingsGlobal.btnSettingsCancel,
            bindingMain.incLayoutSettingsGlobal.btnSettingsSave,
            bindingMain.incLayoutSettingsGlobal.btnSettingsChangeMode,
            bindingMain.incLayoutSettingsGlobal.btnSettingsOpenTutorial,
            bindingMain.incLayoutSettingsGlobal.btnMapBundleImport,
            bindingMain.incLayoutSettingsGlobal.btnVoiceModelImport,
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnBluetoothRescan
        ).forEach { it.backgroundTintList = ColorStateList.valueOf(settingsAccent) }
        // Чекбоксы Settings — раньше тонировался только текст-лейбл (applyTextColor()),
        // сама рамка/галочка оставалась нетематизированным Material-дефолтом, на тёмном
        // фоне на грани видимости.
        listOf(
            bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings,
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings,
            bindingMain.incLayoutSettingsGlobal.cboxAmbientSoundSettings
        ).forEach { CompoundButtonCompat.setButtonTintList(it, ColorStateList.valueOf(settingsAccent)) }

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
            // Синхронизация энкодера с тачем по нижним кнопкам (roadmap, этап 27 —
            // доработка энкодер-эргономики) — тот же приём, что у STATS выше. Индекс не
            // константа — MAP сдвигается относительно GEIGER, см. itemsRootIndexFor().
            menuNavigator.setRootCursor(itemsRootIndexFor("MAP"))
            openMapScreen()
            mapRootAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        val mapMenu = bindingMain.incLayoutTabItemsMap
        mapRootAdapter = SidebarMenuAdapter(
            items = mapRootSidebarItems(),
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { _, item ->
                // Безусловная синхронизация курсора энкодера (roadmap, доработка после
                // фидбека) — не menuNavigator.syncCursor(), тот чинит только позицию ВНУТРИ
                // уже активного уровня; тут курсор должен перепрыгнуть сюда, даже если
                // энкодер был в совсем другой ветке (см. syncMapEncoderPath()).
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // Молча — MAP.onHighlight = btnItemsMap.performClick(), заново открыл
                    // бы экран карты и стёр её текущее состояние (маршрут и т.п.), см.
                    // MenuNavigator.setPathSilently(). Подсветка row2 — отдельным вызовом
                    // ниже, не зависит от onHighlight самого узла MAP.
                    syncMapEncoderPathSilently(emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    // "+ 0" — тап равносилен ENCBTN на этом пункте: у всех четырёх (MAP_
                    // CONTROLS/PLACE_MARKER/ROUTE/MARKER_LIST) есть дети, курсор садится на
                    // первого ребёнка, не остаётся на самом пункте (roadmap, доработка после
                    // фидбека — общий принцип, см. doc у MenuNavigator.setPath()).
                    syncMapEncoderPath(listOf(mapRootIndex(item.payload), 0))
                    mapRootMeta.first { it.key == item.payload }.action()
                }
            },
        )
        mapMenu.recyclerMapMenuRoot.layoutManager = LinearLayoutManager(this)
        mapMenu.recyclerMapMenuRoot.adapter = mapRootAdapter
        mapRouteSubmenuAdapter = SidebarMenuAdapter(
            items = mapRouteSubmenuMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes)) },
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // TO_POINT/TO_MARKER (0/1) — есть дети, курсор на первого ребёнка. BACK (2) —
                // особый случай, как и в списке отметок: его реальный эффект — popLevel()
                // (см. "BACK" MapMenuItemMeta/MAP_ROUTE_BACK.onActivate), поэтому путь
                // останавливается НА РОДИТЕЛЕ ("Build Route" в боковом меню Map), а не на
                // самом пункте Back (найденный баг: тап на Back оставлял курсор в подменю).
                val path = if (item.payload == "BACK") {
                    listOf(mapRootIndex("ROUTE"))
                } else {
                    listOf(mapRootIndex("ROUTE"), position, 0)
                }
                syncMapEncoderPath(path)
                mapRouteSubmenuMeta.first { it.key == item.payload }.action()
            },
        )
        mapMenu.recyclerMapMenuRouteSubmenu.layoutManager = LinearLayoutManager(this)
        mapMenu.recyclerMapMenuRouteSubmenu.adapter = mapRouteSubmenuAdapter
        mapMenu.btnMapMarkerDetailEdit.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 0)
            playButtonAudio()
            showMarkerNamePopupForEdit(marker)
        }
        mapMenu.btnMapMarkerDetailRoute.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 1)
            playButtonAudio()
            // Карточка отметки всегда достигается через "Список меток" (см.
            // mapMarkerListChildrenNodes()).
            routeTo(marker.lat, marker.lon, listOf(mapRootIndex("MARKER_LIST")))
            hideMarkerDetail()
        }
        mapMenu.btnMapMarkerDetailDelete.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 2)
            playButtonAudio()
            performMapMarkerDelete(marker)
        }
        // Back — новый пункт (roadmap, этап 27, п.9), только поднимает курсор энкодера в
        // список отметок, самой отметки не касается. Видимость — refreshSidebarBackItems()/
        // refreshMapMarkerDetailBackButtonVisibility(), тот же гейт, что у Journal.
        mapMenu.btnMapMarkerDetailBack.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 3)
            playButtonAudio()
            setMapMarkerDetailBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshMapMarkerDetailBackButtonVisibility()
        // Бэклог этапа 18: зум +/-, выбор [Route]/[Marker] по тапу на пустую точку карты,
        // управление построенным/активным маршрутом ([Start]/[Cancel]/[Stop]).
        // syncMapEncoderPath() на каждой (roadmap, доработка после фидбека — найденный баг:
        // тап по кнопке не переключал курсор энкодера между ветками дерева, только внутри
        // уже активной).
        // Zoom/Center, в отличие от Pan/Crosshair/"←", видны ВСЕГДА (не входят в
        // setMapControlOverlayVisible() — исторически самостоятельный столбик +/-/Center,
        // роадмап, этап 18) — курсор энкодера на тап уходит в Zoom/Center независимо от
        // того, был ли до этого открыт весь остальной оверлей, поэтому здесь его явно
        // показываем сами (roadmap, доработка после фидбека — найденный баг: курсор
        // переключался, но уголки/крестик/"←" оставались невидимы).
        mapMenu.btnMapZoomIn.setOnClickListener {
            playConfirmAudio()
            setMapControlOverlayVisible(true)
            syncMapEncoderPath(mapControlModeRootPath() + 3)
            zoomMapBy(MAP_ZOOM_STEP_FACTOR)
        }
        mapMenu.btnMapZoomOut.setOnClickListener {
            playConfirmAudio()
            setMapControlOverlayVisible(true)
            syncMapEncoderPath(mapControlModeRootPath() + 3)
            zoomMapBy(1f / MAP_ZOOM_STEP_FACTOR)
        }
        mapMenu.btnMapCenter.setOnClickListener {
            playConfirmAudio()
            setMapControlOverlayVisible(true)
            syncMapEncoderPath(mapControlModeRootPath() + 4)
            recenterMapOnUser()
        }
        // Уголки панорамирования/крестообразный прицел/кнопка "←" (roadmap, этап 27,
        // энкодер-эргономика карты, п.2-4) — та же логика, что и у энкодера
        // (mapControlChildrenNodes()/panMapBy()), доступна тачу тоже (кнопки реально видны
        // на экране, не только энкодеру).
        val mapPanStepPx = resources.displayMetrics.density * MAP_PAN_STEP_DP
        mapMenu.btnMapPanUp.setOnClickListener { playConfirmAudio(); syncMapEncoderPath(mapControlModeRootPath() + 1); panMapBy(0f, mapPanStepPx) }
        mapMenu.btnMapPanDown.setOnClickListener { playConfirmAudio(); syncMapEncoderPath(mapControlModeRootPath() + 1); panMapBy(0f, -mapPanStepPx) }
        mapMenu.btnMapPanLeft.setOnClickListener { playConfirmAudio(); syncMapEncoderPath(mapControlModeRootPath() + 2); panMapBy(mapPanStepPx, 0f) }
        mapMenu.btnMapPanRight.setOnClickListener { playConfirmAudio(); syncMapEncoderPath(mapControlModeRootPath() + 2); panMapBy(-mapPanStepPx, 0f) }
        mapMenu.viewMapCrosshair.setOnClickListener {
            // Полный путь до того, что реально окажется на экране, не только до самого
            // крестика (roadmap, доработка после фидбека) — тач по крестику равносилен
            // ENCBTN на нём, а тот у ROOT/PLACE_MARKER сразу проваливается в детей
            // (Route/Marker/Cancel или Cancel/Save попапа), не остаётся на самом крестике.
            val (lat, lon) = mapCrosshairLatLon() ?: return@setOnClickListener
            playConfirmAudio()
            when (mapControlMode) {
                MapControlMode.ROUTE_TO_POINT -> {
                    syncMapEncoderPath(mapControlModeRootPath() + 0)
                    routeTo(lat, lon, listOf(mapRootIndex("ROUTE")))
                }
                MapControlMode.PLACE_MARKER -> {
                    syncMapEncoderPath(mapMarkerPopupParentPath() + 0)
                    showMarkerNamePopupForNewMarker(lat, lon)
                }
                MapControlMode.ROOT -> {
                    syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0))
                    showMapTapChoice(lat, lon)
                }
            }
        }
        mapMenu.btnMapControlBack.setOnClickListener {
            // Возвращает в боковое меню Map (см. mapSidebarRootPathForMode()) — для
            // ROUTE_TO_POINT это на уровень выше самого узла "До точки на карте" (roadmap,
            // доработка после фидбека — найденный баг), поэтому боковое меню тоже нужно
            // явно вернуть в ROOT: "До точки на карте" переключил его на ROUTE_SUBMENU при
            // входе, а его собственный onHighlight (в отличие от первого ребёнка "До точки
            // на карте") этого не отменяет сам ("приём одного открытия", см. mapRootChildrenNodes()).
            val wasRouteToPoint = mapControlMode == MapControlMode.ROUTE_TO_POINT
            playButtonAudio()
            syncMapEncoderPath(mapSidebarRootPathForMode())
            setMapControlOverlayVisible(false)
            if (wasRouteToPoint) showMapMenuState(MapMenuState.ROOT)
        }
        mapMenu.btnMapTapChoiceRoute.setOnClickListener {
            syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0))
            val (lat, lon) = pendingTapChoiceLatLon ?: return@setOnClickListener
            playButtonAudio()
            hideMapTapChoice()
            // Панель [Route]/[Marker]/[Cancel] — только режим ROOT ("Управление картой",
            // тот же крестик и для прямого тапа по пустой точке карты).
            routeTo(lat, lon, listOf(mapRootIndex("MAP_CONTROLS")))
        }
        mapMenu.btnMapTapChoiceMarker.setOnClickListener {
            // "+ 0" — Marker проваливается в попап (Cancel/Save), не остаётся на себе самой.
            syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 1, 0))
            val (lat, lon) = pendingTapChoiceLatLon ?: return@setOnClickListener
            playButtonAudio()
            hideMapTapChoice()
            showMarkerNamePopupForNewMarker(lat, lon)
        }
        mapMenu.btnMapTapChoiceCancel.setOnClickListener {
            playButtonAudio()
            syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 2))
            hideMapTapChoice()
        }
        mapMenu.btnMapRouteStart.setOnClickListener {
            // syncPushedCursor() возвращает false, если энкодер сейчас не на этой самой
            // (запушенной, без родителя в дереве) панели — тогда replaceTopLevel() было бы
            // применять не к тому уровню (roadmap, доработка после фидбека).
            playButtonAudio()
            val onThisPanel = menuNavigator.syncPushedCursor("MAP_ROUTE_CONTROLS", 0)
            mapRouteState = MapRouteState.ACTIVE
            updateRouteControlsVisibility()
            if (onThisPanel) menuNavigator.replaceTopLevel(mapRouteControlsChildrenNodes())
        }
        mapMenu.btnMapRouteCancel.setOnClickListener {
            playButtonAudio()
            val onThisPanel = menuNavigator.syncPushedCursor("MAP_ROUTE_CONTROLS", 1)
            cancelActiveRoute()
            if (onThisPanel) menuNavigator.popLevel()
        }
        mapMenu.btnMapRouteStop.setOnClickListener {
            playButtonAudio()
            val onThisPanel = menuNavigator.syncPushedCursor("MAP_ROUTE_CONTROLS", 0)
            cancelActiveRoute()
            if (onThisPanel) menuNavigator.popLevel()
        }
        val markerNamePopup = mapMenu.incLayoutTabItemsMapNamePopup
        markerNamePopup.btnMarkerNamePopupCancel.setOnClickListener {
            playButtonAudio()
            syncMapEncoderPath(mapMarkerPopupParentPath() + 0)
            performMarkerNamePopupCancel()
        }
        markerNamePopup.btnMarkerNamePopupSave.setOnClickListener {
            playButtonAudio()
            syncMapEncoderPath(mapMarkerPopupParentPath() + 1)
            performMarkerNamePopupSave()
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
            menuNavigator.setRootCursor(itemsRootIndexFor("CLOCK"))
            clockAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - список фичей слева (roadmap, "Часы — UX-спецификация"):
        Часы/Будильник/Таймер/Секундомер/Мелодия звонка, справа содержимое выбранной.
        */
        val clock = bindingMain.incLayoutTabItemsClock
        clockAdapter = SidebarMenuAdapter(
            items = clockSidebarItems(),
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера (roadmap, доработка после
                // фидбека по Карте) — не menuNavigator.syncCursor(), тот чинит только позицию
                // ВНУТРИ уже активного уровня; тут курсор должен перепрыгнуть сюда, даже если
                // энкодер был в совсем другой ветке (см. syncClockEncoderPath()).
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // Путь до самого узла CLOCK — тот же смысл, что и обычный popLevel() из
                    // сайдбара CLOCK, но безусловный: не зависит от того, где раньше был курсор.
                    playConfirmAudio()
                    syncClockEncoderPath(emptyList())
                    syncRow2ActiveFromNavigator()
                } else if (item.payload == "MELODY") {
                    // "+ 0" — тап равносилен ENCBTN на MELODY: у неё есть дети (треки), курсор
                    // садится на первый трек, не остаётся на самом пункте (тот же приём, что у
                    // Карты, см. doc у MenuNavigator.setPath()). Громкий путь сам вызывает
                    // onHighlight TRACK_0, который и открывает экран Мелодии — отдельно звать
                    // openClockMelodyScreen() здесь больше не нужно (roadmap, доработка).
                    syncClockEncoderPath(listOf(position, 0))
                } else if (item.payload == "TIME") {
                    // Единственный лист без children — проваливаться некуда, курсор остаётся
                    // на самом пункте (та же схема, что у DATA/Files).
                    syncClockEncoderPath(listOf(position))
                    showClockContentPanel(item.payload)
                } else {
                    // ALARM/TIMER/STOPWATCH — тоже "+ 0", тап равносилен ENCBTN (roadmap,
                    // доработка после фидбека по итогам теста — найденный баг: раньше курсор
                    // оставался на самом пункте ALARM/TIMER/STOPWATCH, как у TIME, хотя
                    // ожидание игрока — сразу оказаться на первом реальном органе управления
                    // экрана, как и при обычном ENCBTN).
                    syncClockEncoderPath(listOf(position, 0))
                    showClockContentPanel(item.payload)
                }
            },
        )
        clock.incLayoutTabItemsClockButtons.recyclerTabItemsClockButtons.layoutManager = LinearLayoutManager(this)
        clock.incLayoutTabItemsClockButtons.recyclerTabItemsClockButtons.adapter = clockAdapter

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - БУДИЛЬНИК (roadmap, "Часы — UX-спецификация") — свайповое колесо
        на часы/минуты (ClockWheelPicker.kt, по образцу системных часов Android, инерция —
        родная физика RecyclerView), заворот на границах, один однократный будильник.
        Энкодер-эргономика (этап 27, п.3) — Hour/Minute/Set/Back, см. alarmChildrenNodes().
        */
        val clockAccentTint = ColorStateList.valueOf(currentWizardAccentColor())
        val alarm = clock.incLayoutTabItemsClockAlarm
        alarm.btnClockAlarmToggle.backgroundTintList = clockAccentTint
        alarm.btnClockAlarmBack.backgroundTintList = clockAccentTint
        alarm.viewClockAlarmHourFocus.backgroundTintList = clockAccentTint
        alarm.viewClockAlarmMinuteFocus.backgroundTintList = clockAccentTint
        alarm.viewClockAlarmSetFocus.backgroundTintList = clockAccentTint
        alarm.viewClockAlarmBackFocus.backgroundTintList = clockAccentTint
        updateAlarmStatusViews()

        alarmHourWheel = ClockWheelPicker(
            alarm.rvClockAlarmHour, 0..23, alarmHour,
            onValueSettled = { value -> alarmHour = value; updateAlarmStatusViews() },
            // Свайп по колесу должен подтягивать курсор энкодера на HOUR, даже если тот был
            // на MINUTE/SET/BACK или в другой ветке дерева (roadmap, доработка после фидбека
            // по Карте — тот же класс бага, что и с тачем по сайдбару/кнопкам выше).
            onUserAdjusted = { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 0)) },
        )
        alarmMinuteWheel = ClockWheelPicker(
            alarm.rvClockAlarmMinute, 0..59, alarmMinute,
            onValueSettled = { value -> alarmMinute = value; updateAlarmStatusViews() },
            onUserAdjusted = { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 1)) },
        )
        alarm.btnClockAlarmToggle.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 2))
            playButtonAudio()
            toggleAlarmArmed()
        }
        alarm.btnClockAlarmBack.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 3))
            playConfirmAudio()
            setClockAlarmBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshClockAlarmBackButtonVisibility()

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - ТАЙМЕР (roadmap, "Часы — UX-спецификация") — три колеса ЧЧ:ММ:СС
        (тот же ClockWheelPicker, что у Будильника) + пресеты, один таймер. Энкодер-
        эргономика (этап 27, п.4) — timerChildrenNodes(), два набора кнопок по timerState.
        */
        // layout_clock_timer_setup/layout_clock_timer_running — обычные вложенные
        // ConstraintLayout внутри layout_tab_items_clock_timer.xml, не <include>, поэтому
        // ViewBinding кладёт все id этого файла плоско на один timer-объект (без .root у
        // вложенных блоков — то же самое, что и остальные плоские экраны приложения).
        val timer = clock.incLayoutTabItemsClockTimer
        for (btn in listOf(timer.btnClockTimerPreset5, timer.btnClockTimerPreset10, timer.btnClockTimerStart,
            timer.btnClockTimerPauseResume, timer.btnClockTimerReset, timer.btnClockTimerSetupBack, timer.btnClockTimerRunningBack)) {
            btn.backgroundTintList = clockAccentTint
        }
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
            syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 3))
            playButtonAudio()
            addTimerPresetMinutes(5)
        }
        timer.btnClockTimerPreset10.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 4))
            playButtonAudio()
            addTimerPresetMinutes(10)
        }
        timer.btnClockTimerStart.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 5))
            playButtonAudio()
            startPlainTimer(timerHours * 3600 + timerMinutes * 60 + timerSeconds)
        }
        timer.btnClockTimerSetupBack.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 6))
            playConfirmAudio()
            setClockTimerSetupBackFocused(false)
            menuNavigator.popLevel()
        }
        timer.btnClockTimerPauseResume.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 0))
            playButtonAudio()
            pauseResumeTimer()
        }
        timer.btnClockTimerReset.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 1))
            playButtonAudio()
            resetTimer()
        }
        timer.btnClockTimerRunningBack.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 2))
            playConfirmAudio()
            setClockTimerRunningBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshClockTimerBackButtonsVisibility()

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - СЕКУНДОМЕР (roadmap, "Часы — UX-спецификация") — старт/пауза/сброс,
        без кругов. Энкодер-эргономика (этап 27, п.4) — StartPause/Reset/Back, статичное
        дерево, см. stopwatchChildrenNodes().
        */
        val stopwatch = clock.incLayoutTabItemsClockStopwatch
        stopwatch.btnClockStopwatchStartPause.backgroundTintList = clockAccentTint
        stopwatch.btnClockStopwatchReset.backgroundTintList = clockAccentTint
        stopwatch.btnClockStopwatchBack.backgroundTintList = clockAccentTint
        stopwatch.viewClockStopwatchStartPauseFocus.backgroundTintList = clockAccentTint
        stopwatch.viewClockStopwatchResetFocus.backgroundTintList = clockAccentTint
        stopwatch.viewClockStopwatchBackFocus.backgroundTintList = clockAccentTint

        stopwatch.btnClockStopwatchStartPause.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 0))
            playButtonAudio()
            toggleStopwatchStartPause()
        }
        stopwatch.btnClockStopwatchReset.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 1))
            playButtonAudio()
            resetStopwatch()
        }
        stopwatch.btnClockStopwatchBack.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 2))
            playConfirmAudio()
            setClockStopwatchBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshClockStopwatchBackButtonVisibility()

        /*
        ////////////////////////////////////////////////////////
        ITEMS - CLOCK - МЕЛОДИЯ ЗВОНКА (roadmap, "Часы — UX-спецификация") — список строится
        кодом из ringtoneTracks (Data.kt), последний пункт — [Назад]. Листание энкодером —
        автопрослушивание (roadmap, этап 27, п.2, melodyChildrenNodes()), тач по треку —
        превью play/stop как раньше (визуализатор — LineVisualizer, единственный оставшийся
        в приложении с этапа 23 — у Radio своего визуализатора больше нет, реальный
        радиоприём идёт на ESP32).
        */
        val melody = clock.incLayoutTabItemsClockMelody
        melody.btnClockMelodySelect.backgroundTintList = clockAccentTint
        melody.btnClockMelodyBack.backgroundTintList = clockAccentTint
        melody.viewClockMelodySelectFocus.backgroundTintList = clockAccentTint
        melody.viewClockMelodyBackFocus.backgroundTintList = clockAccentTint
        // applyTextColor() эту LineVisualizer не красит (не входит в её список View) — без
        // явного setColor() линия рисуется дефолтным цветом библиотеки, неотличимым от
        // тёмного фона (баг, найденный на устройстве).
        melody.melodyWave.setColor(currentWizardAccentColor())
        melodyFocusedIndex = sharedPreferences.getInt(selectedRingtone_SPKey, 0)

        // Список треков — единый компонент бокового меню 3 уровня (roadmap), [Назад] —
        // обычный последний пункт того же списка (payload=null), не отдельная кнопка.
        val melodyItems: List<SidebarMenuItem<Int?>> = ringtoneTracks.indices.map { index ->
            SidebarMenuItem<Int?>(payload = index, label = ringtoneTracks[index].displayName)
        } + SidebarMenuItem(payload = null, label = getString(R.string.wizard_back))
        melodyAdapter = SidebarMenuAdapter(
            items = melodyItems,
            selectedBackgroundRes = selected_button,
            initialSelectedPosition = melodyFocusedIndex,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                val index = item.payload
                if (index == null) {
                    // Назад — безусловно на сам узел MELODY_LIST_BACK, тот же приём, что и
                    // остальные Back (roadmap, доработка после фидбека по Карте): тач мог
                    // случиться из любой ветки, включая SELECT/BACK текущего трека.
                    syncClockEncoderPath(listOf(clockRootIndex("MELODY"), position))
                    menuNavigator.popLevel()
                    closeClockMelodyScreen()
                } else {
                    // Найденный баг (доработка после фидбека) — если энкодер до тапа стоял на
                    // SELECT/BACK ПРЕЖНЕГО трека (провалился по ENCBTN), тап по ДРУГОМУ треку
                    // сбрасывал курсор на сам новый трек, а прицел энкодера молча оставался на
                    // кнопке прежнего (та же панель Select/Back на экране относится теперь к
                    // новому треку, но подсвечена кнопка, до которой курсор физически не
                    // добрался). Читать глубину нужно ДО того, как melodyFocusedIndex ниже
                    // укажет уже на новый трек — иначе искали бы "TRACK_<новый>" вместо
                    // "TRACK_<прежний>".
                    val childDepth = menuNavigator.cursorIfParent("TRACK_$melodyFocusedIndex")
                    val path = if (childDepth != null) {
                        listOf(clockRootIndex("MELODY"), position, childDepth)
                    } else {
                        listOf(clockRootIndex("MELODY"), position)
                    }
                    syncClockEncoderPath(path)
                    melodyFocusedIndex = index
                    if (melodyPreviewPlayingIndex == index) stopMelodyPreview() else startMelodyPreview(index)
                }
            },
        )
        melody.recyclerClockMelodyTracks.layoutManager = LinearLayoutManager(this)
        melody.recyclerClockMelodyTracks.adapter = melodyAdapter

        melody.btnClockMelodySelect.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("MELODY"), melodyFocusedIndex, 0))
            playButtonAudio()
            commitMelodySelection()
        }
        melody.btnClockMelodyBack.setOnClickListener {
            syncClockEncoderPath(listOf(clockRootIndex("MELODY"), melodyFocusedIndex, 1))
            playConfirmAudio()
            setClockMelodyBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshClockMelodyBackButtonVisibility()

        bindingMain.incLayoutClockFiredOverlay.btnClockFiredStop.backgroundTintList = clockAccentTint
        bindingMain.incLayoutClockFiredOverlay.viewClockFiredStopFocus.backgroundTintList = clockAccentTint
        bindingMain.incLayoutClockFiredOverlay.btnClockFiredStop.setOnClickListener {
            playButtonAudio()
            dismissClockFiredOverlay()
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - JOURNAL MENU (roadmap, этап 6, п.4; энкодер-эргономика — этап 27) — личные
        записи игрока, редактор занимает контентную область (не всплывающая панель), листание
        энкодером через journalChildrenNodes()/journalEntryDetailChildrenNodes()/
        journalEntryEditorChildrenNodes() (itemsMenuRoot()).
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsJournal.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsJournal, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            stopMapLocationUpdates()
            menuNavigator.setRootCursor(itemsRootIndexFor("JOURNAL"))
            // openJournalScreen() пересобирает journalListAdapter заново (roadmap, этап 27 —
            // список записей не фиксированной длины, тот же приём, что у perksAdapter выше)
            // — свежий адаптер стартует с подсвеченным пунктом 0, clearSelection() ниже
            // гасит рамку молча, пока курсор энкодера не провалится в список по-настоящему.
            openJournalScreen()
            journalListAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        val journalAccentColor = ColorStateList.valueOf(currentWizardAccentColor())
        journalScreen.tvJournalHint.setTextColor(currentWizardAccentColor())
        journalScreen.tvJournalEntryDetailDate.setTextColor(currentWizardAccentColor())
        journalScreen.btnJournalEntryDetailEdit.backgroundTintList = journalAccentColor
        journalScreen.btnJournalEntryDetailDelete.backgroundTintList = journalAccentColor
        journalScreen.btnJournalEntryDetailBack.backgroundTintList = journalAccentColor
        // Прицелы-уголки (roadmap, этап 27) — та же схема тонирования, что у Reset/Menu на
        // Гейгере (viewGeigerResetFocus/viewGeigerMenuFocus чуть выше).
        journalScreen.viewJournalEntryDetailEditFocus.backgroundTintList = journalAccentColor
        journalScreen.viewJournalEntryDetailDeleteFocus.backgroundTintList = journalAccentColor
        journalScreen.viewJournalEntryDetailBackFocus.backgroundTintList = journalAccentColor
        journalScreen.btnJournalEntryDetailEdit.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            // Безусловная синхронизация (roadmap, доработка после фидбека) — не
            // menuNavigator.syncCursor(), тот чинит курсор только если энкодер уже стоит
            // ровно на дочернем узле ЭТОЙ ЖЕ записи (см. doc у syncJournalEncoderPath()).
            // "+ 0, 0" — тап равносилен ENCBTN на EDIT: он сам не лист, у него есть дети
            // (Mic/Cancel/Save), курсор садится на первого — MIC, чей onHighlight сам
            // открывает редактор (showJournalEntryEditorForEdit()), отдельно звать не нужно.
            playButtonAudio()
            syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 0, 0))
        }
        journalScreen.btnJournalEntryDetailDelete.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 1))
            playButtonAudio()
            performJournalEntryDelete(entry)
        }
        // Back — новый пункт (roadmap, этап 27, п.4), только поднимает курсор энкодера в
        // боковое меню, самой записи не касается. Любой режим с физическим энкодером, не
        // Phone (найденный баг — была видна и в Phone, см. refreshJournalBackButtonVisibility()),
        // видимость также обновляется в refreshSidebarBackItems() при смене режима в рантайме.
        journalScreen.btnJournalEntryDetailBack.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 2))
            playConfirmAudio()
            menuNavigator.popLevel()
        }
        refreshJournalBackButtonVisibility()
        val journalEntryPopup = journalScreen.incLayoutTabItemsJournalEntryPopup
        journalEntryPopup.btnJournalEntryMic.backgroundTintList = journalAccentColor
        // Без этого сам глиф иконки красится темой в тот же акцентный цвет, что и фон кнопки
        // (тот же класс бага, что и backgroundTint на обычных кнопках, см. CLAUDE.md) —
        // иконка и фон сливаются в сплошной цветной квадрат, глиф не виден.
        ImageViewCompat.setImageTintList(journalEntryPopup.btnJournalEntryMic, null)
        journalEntryPopup.viewJournalEntryMicFocus.backgroundTintList = journalAccentColor
        journalEntryPopup.viewJournalEntryPopupCancelFocus.backgroundTintList = journalAccentColor
        journalEntryPopup.viewJournalEntryPopupSaveFocus.backgroundTintList = journalAccentColor
        // Голосовой ввод (Vosk, этап 21 п.2) — тап 1 старт/тап 2 стоп, тело вынесено в
        // handleJournalMicTap() (roadmap, этап 27) — общее и для тача, и для ENCBTN (см.
        // journalEntryEditorChildrenNodes()).
        journalEntryPopup.btnJournalEntryMic.setOnClickListener {
            // Синхронизация ТОЛЬКО курсора и прицела (roadmap, доработка после фидбека), не
            // syncJournalEncoderPath() — тот вызвал бы onHighlight узла MIC, а его тело
            // (showJournalEntryEditorForNew()/ForEdit()) сбрасывает текст поля ввода на
            // пустую строку/сохранённый текст записи, стирая то, что игрок уже надиктовал
            // или напечатал. Тач по Mic не переключает экран — редактор уже открыт и виден,
            // нужно только физически переставить курсор энкодера и нарисовать прицел.
            syncJournalEncoderPathSilently(journalEditorPathPrefix() + 0)
            setAllJournalEntryEditorFocusesHidden()
            setJournalEntryEditorMicFocused(true)
            handleJournalMicTap()
        }
        journalEntryPopup.btnJournalEntryPopupCancel.backgroundTintList = journalAccentColor
        journalEntryPopup.btnJournalEntryPopupSave.backgroundTintList = journalAccentColor
        journalEntryPopup.btnJournalEntryPopupCancel.setOnClickListener {
            syncJournalEncoderPath(journalEditorPathPrefix() + 1)
            playConfirmAudio()
            performJournalEntryCancel()
        }
        journalEntryPopup.btnJournalEntryPopupSave.setOnClickListener {
            syncJournalEncoderPath(journalEditorPathPrefix() + 2)
            playButtonAudio()
            performJournalEntrySave()
        }
        bindingMain.incLayoutTabItemsBottom.btnItemsGeiger.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsGeiger, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.VISIBLE
            stopMapLocationUpdates()
            menuNavigator.setRootCursor(itemsRootIndexFor("GEIGER"))
            setGeigerResetFocused(false)
            setGeigerMenuFocused(false)
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - GEIGER (roadmap, этап 22; протокол, раздел 3.4) — восстановить дозу,
        накопленную до перезапуска приложения, и завести кнопку сброса. Сама шкала уже
        построена в XML (layout_tab_items_geiger.xml) и обновляется дальше из
        accumulateGeigerDose()/updateGeigerDoseDisplay() по каждому GEIGER:<рад/сек>.
        */
        updateGeigerDoseDisplay(sharedPreferences.getInt(geigerDose_SPKey, 0))
        val geigerButtonAccent = ColorStateList.valueOf(currentWizardAccentColor())
        bindingMain.incLayoutTabItemsGeiger.btnGeigerReset.backgroundTintList = geigerButtonAccent
        // Прицелы-уголки (focus_corner_brackets) — белая заглушка в самом drawable, реальный
        // акцент темы всегда только кодом (тот же приём, что у остальных прицелов —
        // viewWoundStopFocus/viewSpecialValueFocus/viewClockFiredStopFocus и т.п.).
        bindingMain.incLayoutTabItemsGeiger.viewGeigerResetFocus.backgroundTintList = geigerButtonAccent
        bindingMain.incLayoutTabItemsGeiger.viewGeigerMenuFocus.backgroundTintList = geigerButtonAccent
        bindingMain.incLayoutTabItemsGeiger.btnGeigerReset.setOnClickListener {
            // Синхронизация курсора энкодера с тачем (roadmap, этап 27) — тот же приём, что
            // у SidebarMenuAdapter.onSelect (SPECIAL/Skills/Status/PERKS/MISC), только без
            // самого адаптера: Reset/Menu — обычные кнопки экрана, не элементы списка.
            menuNavigator.syncCursor("GEIGER", 0)
            playButtonAudio()
            resetGeigerDose()
        }
        // Menu ("В меню") — любой режим с физическим энкодером, не Phone (roadmap, этап 27),
        // см. geigerChildrenNodes(). Видимость также обновляется в refreshSidebarBackItems() —
        // режим может смениться в рантайме через Settings ("Изменить").
        bindingMain.incLayoutTabItemsGeiger.btnGeigerMenu.backgroundTintList = geigerButtonAccent
        bindingMain.incLayoutTabItemsGeiger.btnGeigerMenu.setOnClickListener {
            menuNavigator.syncCursor("GEIGER", 1)
            playButtonAudio()
            setGeigerMenuFocused(false)
            menuNavigator.popLevel()
            syncRow2ActiveFromNavigator()
        }
        refreshGeigerMenuButtonVisibility()

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
            // MISC — всегда индекс 0 в dataMenuRoot() (roadmap, этап 27 — доработка
            // энкодер-эргономики), в отличие от ITEMS состав/порядок здесь не зависит от
            // pipBoyMode.
            menuNavigator.setRootCursor(0)
            dataFilesAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
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

        // DATA - FILES — единый компонент бокового меню 3 уровня (см. SPECIAL/Skills/Perks/
        // Clock выше) вместо двух hand-copied ConstraintLayout-строк + 2 setOnClickListener,
        // без какой-либо энкодер-логики.
        val files = bindingMain.incLayoutTabDataMisc
        dataFilesAdapter = SidebarMenuAdapter(
            items = dataFilesSidebarItems(),
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { position, item ->
                // Безусловная синхронизация курсора энкодера с тачем (roadmap, этап 27 —
                // доработка энкодер-эргономики), не menuNavigator.syncCursor() — тот чинит
                // курсор только ВНУТРИ уже активного уровня (см. doc у syncEncoderPath()).
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    playConfirmAudio()
                    syncDataEncoderPath("MISC", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    showDataFilePreview(dataFilesMeta.first { it.key == item.payload })
                    // Тап равносилен ENCBTN — тот же приём, что у Perks выше.
                    syncDataEncoderPathSilently("MISC", listOf(position))
                    menuNavigator.activateSelected()
                }
            },
        )
        files.recyclerTabDataMisc.layoutManager = LinearLayoutManager(this)
        files.recyclerTabDataMisc.adapter = dataFilesAdapter
        files.tvDataMiscHolotapeText.setText(dataFilesMeta.first().descriptionRes)

        // Боковое меню разделов Settings (roadmap, "Редизайн Settings", этап 26) — тот же
        // SidebarMenuAdapter, что у SPECIAL/Skills/Clock/Perks/Map/выбора режима. Пункт —
        // сама панель-раздел (payload = View, который нужно показать): единственное, что
        // нужно onSelect — какую панель сделать VISIBLE, остальные GONE, отдельный enum
        // разделов не нужен.
        val settingsSectionPanels: List<View> = listOf(
            bindingMain.incLayoutSettingsGlobal.scrollSettingsMain,
            bindingMain.incLayoutSettingsGlobal.scrollSettingsGameInfo,
            bindingMain.incLayoutSettingsGlobal.layoutSettingsSectionVoice,
            bindingMain.incLayoutSettingsGlobal.layoutSettingsSectionMap,
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.root,
            bindingMain.incLayoutSettingsGlobal.scrollSettingsPreferences,
        )
        val settingsSectionLabels = listOf(
            getString(R.string.settings_section_main),
            getString(R.string.settings_section_game_info),
            getString(R.string.settings_section_voice_commands),
            getString(R.string.items_map),
            getString(R.string.settings_section_bluetooth),
            getString(R.string.settings_section_preferences),
        )
        bindingMain.incLayoutSettingsGlobal.recyclerSettingsSidebar.layoutManager = LinearLayoutManager(this)
        val settingsSidebarAdapter = SidebarMenuAdapter(
            items = settingsSectionPanels.mapIndexed { index, panel ->
                SidebarMenuItem(payload = panel, label = settingsSectionLabels[index])
            },
            selectedBackgroundRes = selected_button,
            playSelectSound = { playTickAudio() },
            onSelect = { _, item ->
                settingsSectionPanels.forEach { it.visibility = if (it === item.payload) View.VISIBLE else View.GONE }
                // Скан по эфиру идёт только пока реально виден раздел Bluetooth — та же
                // дисциплина, что у шага PAIRING мастера (см. showWizardStep()). Уход на
                // любой другой раздел останавливает его безусловно (stopPairingScan()
                // безопасно вызывать и без активного скана).
                if (item.payload === bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.root) {
                    startBluetoothPairingScan()
                } else {
                    stopPairingScan()
                }
            },
        )
        bindingMain.incLayoutSettingsGlobal.recyclerSettingsSidebar.adapter = settingsSidebarAdapter
        settingsSectionPanels.forEachIndexed { index, panel -> panel.visibility = if (index == 0) View.VISIBLE else View.GONE }

        // DataStore for saving Settings
        val saveButtonSettings = bindingMain.incLayoutSettingsGlobal.btnSettingsSave
        val cancelButtonSettings = bindingMain.incLayoutSettingsGlobal.btnSettingsCancel
        val editSettings1 = bindingMain.incLayoutSettingsGlobal.etSettings1Value //PlayerName
        val editSettingsRegion = bindingMain.incLayoutSettingsGlobal.etSettingsRegionValue //PlayerRegion
        var editSettings6 = bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings //ShowTutorial
        var editSettings7 = bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings //Fullscreen
        var editSettings8 = bindingMain.incLayoutSettingsGlobal.cboxAmbientSoundSettings //AmbientSoundEnabled
        val editSettingsYear = bindingMain.incLayoutSettingsGlobal.etSettingsYearValue //GameYear

        // Save пишет все поля разом и делает recreate() (roadmap, "Редизайн Settings", этап
        // 26) — раньше был полный finishAffinity()+Intent-рестарт всего таска. recreate()
        // не убирает пересоздание Activity целиком (тема — theme.applyStyle(), язык —
        // attachBaseContext(), оба применяются только в onCreate, живой ре-тайминг/
        // релокализация на лету не реализовывались), но не убивает и не создаёт новый таск.
        // saveValues() зовётся синхронно (не в отдельной корутине, как раньше) — edit().apply()
        // обновляет in-memory SharedPreferences сразу, на диск пишет асинхронно сам, так что
        // recreate() ниже гарантированно видит новые значения без гонки с корутиной.
        saveButtonSettings.setOnClickListener {
            playButtonAudio()
            stopPairingScan()
            saveValues(editSettings1.text.toString(), UIColour_Selector, dateFormat_Selector, editSettings6.isChecked(), editSettings7.isChecked(), editSettingsYear.text.toString().toInt(), editSettingsRegion.text.toString(), languageSelector, editSettings8.isChecked())
            sendBLEText("STATS")
            recreate()
        }
        // Cancel — выход без сохранения (было [X] в углу, убран, roadmap "Редизайн Settings":
        // явная кнопка рядом с Save читается однозначнее нейтрального крестика). Несохранённые
        // правки полей теряются молча — при следующем открытии populate ниже перечитает
        // актуальные SharedPreferences. stopPairingScan() — на случай, если раздел
        // Bluetooth сканировал в момент закрытия.
        cancelButtonSettings.setOnClickListener {
            playButtonAudio()
            stopPairingScan()
            if (!isResizing) {
                bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
        }

            // Общая нижняя панель (roadmap, "Новая шапка + единый Settings") — имя и регион
            // выставляются один раз при старте, как и остальные Settings-поля ниже:
            // сохранение настроек всегда идёт через recreate() (см.
            // saveButtonSettings.setOnClickListener выше), живого обновления не требуется.
            bindingMain.incLayoutHeaderBottomCommon.tvBottomNameValue.text = sharedPreferences.getString(playerName_SPKey, "Player")
            bindingMain.incLayoutHeaderBottomCommon.tvBottomRegionValue.text = sharedPreferences.getString(playerRegion_SPKey, "Richmond")
            editSettings1.setText(sharedPreferences.getString(playerName_SPKey, "Player"))
            editSettingsRegion.setText(sharedPreferences.getString(playerRegion_SPKey, "Richmond"))
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
         * MAP (roadmap, ветка app-map) — импорт бандла карты, см. MapBundleRepository/
         * openMapBundleTreeLauncher/refreshMapBundleStatus(). Раздел встроен напрямую
         * (roadmap, "Редизайн Settings" — правки по подразделам), своей кнопки-перехода и
         * [X] больше нет — статус читаем один раз при инициализации.
         *
         **********************************************************************************************************/

        refreshMapBundleStatus()
        bindingMain.incLayoutSettingsGlobal.btnMapBundleImport.setOnClickListener {
            openMapBundleTreeLauncher.launch(null)
        }

        /***********************************************************************************************************
         *
         * VOICE COMMANDS (roadmap, ветка app-voice-commands, этап 21) — импорт .zip с
         * офлайн-моделью Vosk, см. VoiceModelRepository/openVoiceModelZipLauncher/
         * refreshVoiceModelStatus(). Раздел встроен напрямую (roadmap, "Редизайн Settings" —
         * правки по подразделам), своей кнопки-перехода и [X] больше нет; переключателей
         * тоже нет — гейтинг по hasModel() теперь под капотом (см.
         * startWakeWordIfPermitted()/btnJournalEntryMic.setOnClickListener).
         *
         **********************************************************************************************************/

        refreshVoiceModelStatus()
        bindingMain.incLayoutSettingsGlobal.btnVoiceModelImport.setOnClickListener {
            openVoiceModelZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        }

        /***********************************************************************************************************
         *
         * BLUETOOTH
         *
         **********************************************************************************************************/

        // BLUETOOTH — видимость раздела ведёт settingsSidebarAdapter выше (было — свои
        // btn_settings_bluetooth/btn_settings_bluetooth_close, убраны вместе с редизайном).
        // Интерфейс мастера (скан + список + Rescan) заменяет собой старый ручной ввод
        // MAC/UUID целиком, не сосуществует с ним (roadmap, "Редизайн Settings" — правки по
        // подразделам) — Save/Connect/Disconnect тоже убраны: тап по найденному устройству
        // уже сохраняет MAC и переподключается сам (applyPairedDevice(), стартует сервис
        // сам, если он ещё не поднят — тот же путь, что раньше делала кнопка Connect).
        // Скан идёт, только пока раздел Bluetooth реально виден (см. onSelect
        // settingsSidebarAdapter выше и btnSettingsCancel/btnSettingsSave ниже — та же
        // дисциплина "не слушать эфир вхолостую", что и у мастера/wake-word).
        refreshBluetoothCurrentDevice()
        bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnBluetoothRescan.setOnClickListener {
            playButtonAudio()
            startBluetoothPairingScan()
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
            playButtonAudio()
            openModeSelectScreen()
        }

        // isResizing/ScaleListener/handleTouch — общий с мастером механизм (шаг DISPLAY AREA
        // сам включает isResizing на время своего показа, см. showWizardStep()), поэтому
        // остаётся и после удаления легаси-попапа. Слушатель — на корне САМОГО мастера, не
        // на bindingMain.root (roadmap, этап 28, доработка после фидбека): мастер теперь
        // обязан поглощать любой тач в своих границах сам (см. комментарий в
        // selectPipBoyMode() про раздизейбленные кнопки под ним), а isResizing включён
        // только пока виден мастер (единственное место, где он ставится в true) — если бы
        // слушатель остался на bindingMain.root, он срабатывал бы только на тех тапах,
        // которые не поглотил уже сам мастер, то есть жест ресайза на DISPLAY AREA перестал
        // бы доезжать.
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        bindingMain.incLayoutPipboy2000Wizard.root.setOnTouchListener { _, event ->
            if (isResizing) {
                handleTouch(event)
            }
            true
        }


        initWakeWordDetector()

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
        outState.putInt(KEY_STATUS_CURSOR_ROW, statusAdapter.selectedPosition())
    }

    /** Сворачивание приложения или блокировка экрана — Activity перестаёт быть видимой
     * (в отличие от onPause(), который срабатывает и на кратких перекрытиях вроде системных
     * диалогов разрешений, onStop() — именно "игрок больше не смотрит на экран"). Эмбиент
     * освобождается по-настоящему (stop+release, не просто мьютится), но намерение
     * [ambientShouldBePlaying] не трогаем — тикThread/BLE-сервис по-прежнему работают в фоне
     * независимо от этого. */
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
        stopAmbientBackgroundSound()
        cancelBootSequence()
        wakeWordDetector?.release()
        voiceDictationService.release()
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