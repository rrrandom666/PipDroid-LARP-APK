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
import android.util.TypedValue
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

    // ===== КОНСТАНТЫ И ПОЛЯ =====

    private lateinit var bindingMain: ActivityMainBinding

    // ===== SharedPreferences =====
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
    /** payload — индекс в ringtoneTracks. */
    private lateinit var melodyAdapter: SidebarMenuAdapter<Int?>

    // ===== СПИСКИ VIEW =====
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

    // ===== ЗВУК =====
    private val REQUEST_CODE_PERMISSION_RECORD_AUDIO = 23
    private val REQUEST_CODE_PERMISSION_WAKE_WORD = 24
    private var wakeWordDetector: com.malto4.pipdroid.voice.WakeWordDetector? = null
    // Держит ссылку на играющий одноразовый звук, пока тот не доиграет, см. playSfx().
    private val activeSfxPlayers = mutableListOf<MediaPlayer>()
    private var mediaPlayerBackGround: MediaPlayer? = null

    // ===== КАРТА =====
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
    // ===== ЖУРНАЛ =====
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
    private val REQUEST_CODE_PERMISSION_JOURNAL_DICTATION = 25
    private val REQUEST_CODE_PERMISSION_BLUETOOTH_SETTINGS_SCAN = 26
    private val REQUEST_CODE_PERMISSION_MAP_MARKER_DICTATION = 27
    /** Диктовка в редактор записи Журнала — дописывает к уже набранному тексту. */
    private val journalDictation by lazy {
        val popup = bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup
        DictationController(
            activity = this,
            micButton = popup.btnJournalEntryMic,
            statusView = popup.tvJournalEntryMicStatus,
            editText = popup.etJournalEntryValue,
            permissionRequestCode = REQUEST_CODE_PERMISSION_JOURNAL_DICTATION,
            logTag = "VoiceJournal",
            dictation = voiceDictationService,
            models = voiceModelRepository,
            accentColor = { themeAccentColor() },
            isVoiceCommandBusy = { awaitingVoiceCommand },
            replaceFirstSegment = { false },
            playButtonSound = { playButtonAudio() },
            playErrorSound = { playErrorAudio() },
        )
    }
    /** Диктовка имени отметки — при правке существующей первый сегмент затирает старое имя. */
    private val mapMarkerDictation by lazy {
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        DictationController(
            activity = this,
            micButton = popup.btnMarkerNamePopupMic,
            statusView = popup.tvMarkerNamePopupMicStatus,
            editText = popup.etMarkerNameValue,
            permissionRequestCode = REQUEST_CODE_PERMISSION_MAP_MARKER_DICTATION,
            logTag = "VoiceMapMarker",
            dictation = voiceDictationService,
            models = voiceModelRepository,
            accentColor = { themeAccentColor() },
            isVoiceCommandBusy = { awaitingVoiceCommand },
            replaceFirstSegment = { editingMarkerId != null },
            playButtonSound = { playButtonAudio() },
            playErrorSound = { playErrorAudio() },
        )
    }
    private enum class MapRouteState { NONE, BUILT, ACTIVE }
    private var mapRouteState = MapRouteState.NONE
    private var mapRouteDestination: Pair<Double, Double>? = null
    private var mapRouteLatLonPath: List<Pair<Double, Double>> = emptyList()
    private var pendingMapReadyAction: (() -> Unit)? = null
    private enum class MapTapMode { NONE, PLACE_MARKER, ROUTE_TO_POINT }
    private var mapTapMode = MapTapMode.NONE
    companion object {
        // Отладочная инъекция BLE-команд без реального ESP32, см. registerDebugCommandReceiver().
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
        // Отступ от краёв при автоцентрировании на построенном маршруте.
        private const val MAP_ROUTE_FIT_PADDING_DP = 28f
        // Шаг панорамирования уголками энкодера, в экранных dp.
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

    // ===== BLUETOOTH =====
    private val menuNavigator = MenuNavigator()
    /** true, пока simulateEncoderTabHighlight() имитирует наведение курсора: реальный тап видит
     * false и проваливается в первый дочерний узел, как ENCBTN. */
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
    /** Пускает строки в тот же handleBleCommand(), что и реальный ESP32 по BLE:
     * adb shell am broadcast -p com.malto4.pipdroid -a com.malto4.pipdroid.DEBUG_BLE_COMMAND --es raw "ENC:+1" */
    private fun registerDebugCommandReceiver() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val raw = intent?.getStringExtra(EXTRA_DEBUG_BLE_RAW) ?: return
                handleBleCommand(raw)
            }
        }
        val filter = IntentFilter(ACTION_DEBUG_BLE_COMMAND)
        // С API 33 контекстно зарегистрированный приёмник обязан явно объявить экспортируемость.
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
    /** Системный диалог "Разрешить приложению включить Bluetooth?". */
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        setupBluetooth()
    }
    /** Импорт бандла карты. */
    private val openMapBundleTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@registerForActivityResult
        // Кнопка импорта есть и в Settings, и в шаге IMPORT мастера — обновляем обе строки статуса.
        val resultViews = listOf(
            bindingMain.incLayoutSettingsGlobal.tvMapBundleImportResult,
            bindingMain.incLayoutPipboy2000Wizard.tvWizardImportMapResult,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            val result = mapBundleRepository.importFromTree(treeUri)
            withContext(Dispatchers.Main) {
                refreshMapBundleStatus()
                val text = result.fold(
                    onSuccess = { getString(R.string.map_bundle_import_success) },
                    onFailure = { it.message ?: getString(R.string.map_bundle_import_error_unknown) }
                )
                resultViews.forEach {
                    it.visibility = View.VISIBLE
                    it.text = text
                }
            }
        }
    }
    /** Импорт офлайн-модели Vosk. */
    private val openVoiceModelZipLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { zipUri ->
        if (zipUri == null) return@registerForActivityResult
        // См. openMapBundleTreeLauncher выше — тот же приём, обе result-строки разом.
        val resultViews = listOf(
            bindingMain.incLayoutSettingsGlobal.tvVoiceModelImportResult,
            bindingMain.incLayoutPipboy2000Wizard.tvWizardImportVoiceResult,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            val result = voiceModelRepository.importFromZip(zipUri)
            withContext(Dispatchers.Main) {
                refreshVoiceModelStatus()
                val text = result.fold(
                    onSuccess = { getString(R.string.voice_model_import_success) },
                    onFailure = { it.message ?: getString(R.string.voice_model_import_error_unknown) }
                )
                resultViews.forEach {
                    it.visibility = View.VISIBLE
                    it.text = text
                }
                if (result.isSuccess) startWakeWordIfPermitted()
            }
        }
    }
    private fun onRequiredPermissionsGranted() {
        val wizard = bindingMain.incLayoutPipboy2000Wizard
        val fromWizardPermissions = wizard.root.visibility == View.VISIBLE && wizard.layoutWizardPermissions.visibility == View.VISIBLE
        // IMPORT — общий следующий шаг обоих режимов, см. btnWizardImportDone.
        if (fromWizardPermissions) {
            showWizardStep(PipBoyWizardStep.IMPORT)
            return
        }
        setupBluetooth()
    }

    // ===== РАЗМЕР ЭКРАНА =====
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var newWidth = 0
    private var newHeight = 0
    private var isResizing = false
    // Доп. минимум размера при пинче на шаге DISPLAY AREA мастера, вне мастера — 0.
    private var wizardMinContentWidthPx = 0
    private var wizardMinContentHeightPx = 0
    private var lastX = 0f
    private var lastY = 0f

    // Сама логика масштаба живёт в GlobalTextScale: часть View создаётся вне onCreate().
    private val minTextSizeSp = 10f

    // Масштаб — от более строгого из двух отношений к экрану, чтобы текст влез по обеим осям.
    private fun applyGlobalTextScale(currentWidthPx: Int, currentHeightPx: Int) {
        if (currentWidthPx <= 0 || currentHeightPx <= 0) return
        val displayMetrics = resources.displayMetrics
        val widthRatio = currentWidthPx.toFloat() / displayMetrics.widthPixels
        val heightRatio = currentHeightPx.toFloat() / displayMetrics.heightPixels
        val scale = min(widthRatio, heightRatio).coerceIn(0f, 1f)
        val minTextSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, minTextSizeSp, displayMetrics)
        GlobalTextScale.setScale(scale, minTextSizePx)
    }

    // ===== ДИСКЛЕЙМЕР / ТЬЮТОРИАЛ =====
    private var showTutorialBool = true
    // Индекс страницы тьюториала; -1 — показан Welcome.
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

    // ===== ФИЛЬТР =====
    private lateinit var filterFrame: FrameLayout
    private lateinit var filteringMenu: String
    private var selectedFilterSTATSPerks = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterDATAMisc = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var filterSelectionSnapshot: MutableSet<String> = mutableSetOf()

    // ===== ДОЛГИЕ НАЖАТИЯ: пасхалка и урон игрока =====
    private var statsCndPopupIsHolding = false
    private var menuSwipeEnabled = true
    private var delayIterationCount = 0
    private var delayModify = 500L

    // ===== STATUS =====
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

    /** Метаданные 7 характеристик SPECIAL — единственный источник для SidebarMenuAdapter. */
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

    /** Метаданные 13 навыков Skills. */
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
    private lateinit var perksAdapter: SidebarMenuAdapter<Perk>
    private var perksRealItemCount = 0

    private lateinit var selectedSubMenu: Button

    private val handler = Handler(Looper.getMainLooper())
    // 300мс-тик часов; ссылка нужна, чтобы onDestroy() его остановил.
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

    /** Строка 2 новой шапки. */
    private data class Row2Item(val label: CharSequence, val onSelect: () -> Unit)
    private var row2Items: List<Row2Item> = emptyList()
    private var row2Active = 0
    private val row2Views = mutableListOf<TextView>()
    // Поколение строки 2: отложенный расчёт сверяет его, чтобы не двигать полосу по покинутому разделу.
    private var row2Generation = 0
    private fun onMenuSwipeLeft() {
        // resetToRoot() обязателен и здесь, иначе энкодер крутит дерево раздела, с которого свайпнули.
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


    // ===== ФУНКЦИИ =====

    // ===== ЗВУК И РАЗРЕШЕНИЕ НА МИКРОФОН =====
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
                if (popupVisible) journalDictation.start()
            } else {
                playErrorAudio()
            }
        }
        if (requestCode == REQUEST_CODE_PERMISSION_MAP_MARKER_DICTATION) {
            val popupVisible = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.root.visibility == View.VISIBLE
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (popupVisible) mapMarkerDictation.start()
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
    // ===== ГОЛОСОВЫЕ КОМАНДЫ / WAKE-WORD =====
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
        // Дожимаем накопленное без паузы в речи — короткая команда может уложиться в таймаут раньше Vosk.
        val flushed = voiceDictationService.flushCommandFinalText()
        Log.d("VoiceCommand", "final (timeout flush): \"$flushed\"")
        handleVoiceCommandText(flushed)
        if (awaitingVoiceCommand) cancelVoiceCommandListening(matched = false)
    }
    private val VOICE_COMMAND_TIMEOUT_MS = 6000L
    /** Уступает микрофон любой уже идущей диктовке: VoiceDictationService один на все сценарии. */
    private fun onWakeWordTriggered() {
        if (awaitingVoiceCommand || !journalDictation.isIdle || !mapMarkerDictation.isIdle) return
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
    /** Полный словарь голосовых команд. */
    private fun handleVoiceCommandText(text: String) {
        if (!awaitingVoiceCommand) return
        val normalized = text.lowercase().replace('ё', 'е')

        // Ранение с опциональной частью тела: общий таймер плюс независимая CRIPPLED-отметка.
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
        // Возвращение в строй — только пока персонаж мёртв, тот же гвард, что у тач-жеста по фигуре.
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
    /** Часть тела для команды "лёгкое/тяжёлое ранение в <часть>". */
    private fun matchBodyPartSetter(normalized: String): ((Boolean) -> Unit)? = when {
        normalized.contains("голов") -> ::setCrippledHead
        normalized.contains("торс") || normalized.contains("груд") || normalized.contains("тулов") -> ::setCrippledTorso
        normalized.contains("рук") && normalized.contains("лев") -> ::setCrippledRightArm
        normalized.contains("рук") && normalized.contains("прав") -> ::setCrippledLeftArm
        normalized.contains("ног") && normalized.contains("лев") -> ::setCrippledRightLeg
        normalized.contains("ног") && normalized.contains("прав") -> ::setCrippledLeftLeg
        else -> null
    }
    /** Переключение на top-level узел ITEMS по символическому id. */
    private fun navigateToItemsSection(nodeId: String) {
        val roots = itemsMenuRoot()
        val index = roots.indexOfFirst { it.id == nodeId }
        if (index < 0) return
        menuChangeBLE("ITEMS")
        menuNavigator.resetToRootAtIndex(roots, index)
    }
    private val ROUTE_FILLER_WORDS = setOf("до", "к", "на", "в")
    /** Общий хвост распознанной команды — тост и остановка прослушивания. */
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

    // ===== BLUETOOTH И РАЗРЕШЕНИЯ =====
    private fun requiredPermissionsForCurrentMode(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        // COARSE запрашивается вместе с FINE — обе объявлены в манифесте безусловно.
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        // RECORD_AUDIO запрашивается всегда: голосовой ввод не завязан на физический режим.
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
        } else {
            // Всё уже выдано — тот же обработчик, что и после реального системного диалога.
            onRequiredPermissionsGranted()
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
    /** Не обязательное разрешение, а рекомендация системы. */
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
        // status — внутренний токен состояния, подпись для показа берётся из строкового ресурса.
        val displayText = if (status == "CONNECTED") getString(R.string.bluetooth_status_connected) else getString(R.string.bluetooth_status_disconnected)
        bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.textViewBLUETOOTHConnection.text = displayText
        // Индикатор BLE в углу row1: состояние передаётся альфой, не сменой drawable.
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

    // ===== ЭКРАН ВЫБОРА РЕЖИМА =====
    private var modeSelectHighlighted = PipBoyMode.PHONE
    private val modeSelectList = listOf(PipBoyMode.PHONE, PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000)
    private lateinit var modeSelectAdapter: SidebarMenuAdapter<PipBoyMode>

    /** Акцентный цвет текущей темы оформления. */
    /** Все ресурсы одной темы оформления в одном месте — раньше это же соответствие было
     * размножено по пяти when-блокам, привязанным к playerUIColour_SPKey. */
    private enum class UiTheme(
        val styleRes: Int,
        val accentColorRes: Int,
        val scrollbarRes: Int,
        val boxBackgroundRes: Int,
        val selectedButtonRes: Int,
        val selectedRowRes: Int,
    ) {
        GREEN(R.style.Theme_PipDroid_GreenUI, R.color.themeGreen, R.drawable.scrollbar_custom_green,
            R.drawable.settings_menu_background_green, R.drawable.button_selected_green, R.drawable.status_row_selected_green),
        AMBER(R.style.Theme_PipDroid_AmberUI, R.color.themeAmber, R.drawable.scrollbar_custom_amber,
            R.drawable.settings_menu_background_amber, R.drawable.button_selected_amber, R.drawable.status_row_selected_amber),
        WHITE(R.style.Theme_PipDroid_WhiteUI, R.color.themeWhite, R.drawable.scrollbar_custom_white,
            R.drawable.settings_menu_background_white, R.drawable.button_selected_white, R.drawable.status_row_selected_white),
        BLUE(R.style.Theme_PipDroid_BlueUI, R.color.themeBlue, R.drawable.scrollbar_custom_blue,
            R.drawable.settings_menu_background_blue, R.drawable.button_selected_blue, R.drawable.status_row_selected_blue),
    }
    /** Тема, выбранная игроком в Settings. */
    private fun currentUiTheme(): UiTheme =
        UiTheme.values().getOrElse(sharedPreferences.getInt(playerUIColour_SPKey, 0)) { UiTheme.GREEN }
    /** Акцентный цвет текущей темы оформления. */
    private fun themeAccentColor(): Int = ContextCompat.getColor(this, currentUiTheme().accentColorRes)
    private fun setWizardButtonState(button: Button, selected: Boolean) {
        val accent = themeAccentColor()
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
        val accent = themeAccentColor()
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
        // PipBoy 3000 выбрать нельзя, но кнопка кликабельна — чтобы поймать тап и дать звук ошибки.
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

        ms.tvModeSelectDescription.setTextColor(themeAccentColor())

        ms.recyclerModeSelect.layoutManager = LinearLayoutManager(this)
        modeSelectAdapter = SidebarMenuAdapter(
            items = modeSelectList.map { mode -> SidebarMenuItem(payload = mode, label = pipBoyModeDisplayName(mode)) },
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
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

        // Кнопки шапки/футера здесь НЕ включаем: мастер не перехватывает тач фоном, и они станут кликабельны сквозь него.
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
        // Мастер реально закрылся — возвращаем кнопки шапки/футера и свайп.
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        resetToFullScreen()
        bindingMain.viewPowerOff.animate().cancel()
        bindingMain.viewPowerOff.visibility = View.GONE
        updateScreenGlareVisibility()
        stopBleService()
        menuChangeBLE("STATS")
        menuNavigator.resetToRoot(statsMenuRoot())
        // Стартовый курсор энкодера — первый дочерний пункт бокового меню, не сам узел строки 2.
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
        // Истёкший за время простоя таймер обработает обычный следующий тик 300мс-цикла.
    }

    // ===== МАСТЕР НАСТРОЙКИ PIPBOY 2000/3000 =====
    private enum class PipBoyWizardStep { HARDWARE_INSTRUCTIONS, DISPLAY_AREA, PERMISSIONS, IMPORT, PAIRING, POWER_HINT }

    private fun showWizardStep(step: PipBoyWizardStep, allowAutoAdvance: Boolean = true) {
        val w = bindingMain.incLayoutPipboy2000Wizard
        w.layoutWizardChromeFrame.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.GONE else View.VISIBLE
        w.layoutWizardHardware.visibility = if (step == PipBoyWizardStep.HARDWARE_INSTRUCTIONS) View.VISIBLE else View.GONE
        w.layoutWizardDisplayArea.visibility = if (step == PipBoyWizardStep.DISPLAY_AREA) View.VISIBLE else View.GONE
        w.layoutWizardPermissions.visibility = if (step == PipBoyWizardStep.PERMISSIONS) View.VISIBLE else View.GONE
        w.layoutWizardImport.visibility = if (step == PipBoyWizardStep.IMPORT) View.VISIBLE else View.GONE
        w.layoutWizardPairing.visibility = if (step == PipBoyWizardStep.PAIRING) View.VISIBLE else View.GONE
        w.layoutWizardPowerHint.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.VISIBLE else View.GONE
        w.tvWizardPowerHint.visibility = View.VISIBLE
        w.btnWizardHideHint.visibility = View.VISIBLE
        updateScreenGlareVisibility()

        // Скан идёт строго по факту показа шага PAIRING, а не по нажатию игрока.
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
            // Персистентный сброс — стартовая точка регулировки, масштаб прошлой сессии не подхватываем.
            resetToFullScreen()
        } else if (step == PipBoyWizardStep.HARDWARE_INSTRUCTIONS) {
            // До этого шага область не настраивалась в этом прогоне — перезаписывать нечего.
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
            // Разрешения уже выданы — не задерживаем игрока; allowAutoAdvance=false только у явного Back.
            showWizardStep(PipBoyWizardStep.IMPORT)
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
            backgroundTintList = ColorStateList.valueOf(themeAccentColor())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            setOnClickListener {
                playButtonAudio()
                pairingOnSelect?.invoke(address)
            }
        }
        GlobalTextScale.register(button)
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

        val wizardAccent = themeAccentColor()
        listOf(
            w.btnWizardHardwareBack,
            w.btnWizardHardwareSkipDebug,
            w.btnWizardHardwareNext,
            w.btnWizardDone,
            w.btnWizardReset,
            w.btnWizardCancel,
            w.btnWizardPermissionsBack,
            w.btnWizardGrantPermissions,
            w.btnWizardImportBack,
            w.btnWizardImportDone,
            w.btnWizardImportVoice,
            w.btnWizardImportMap,
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
            w.tvWizardImportTitle,
            w.tvWizardImportText,
            // Статус-строки намеренно не в списке: CNDEFFRADButtonStyle не подмешивает акцент темы.
            w.tvWizardImportVoiceLabel,
            w.tvWizardImportMapLabel,
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
        // Обход всего мастера и анимации загрузки в debug-сборках.
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

        // Шаг Import: кнопки переиспользуют лончеры и репозитории Settings.
        w.btnWizardImportBack.setOnClickListener {
            playButtonAudio()
            // allowAutoAdvance=false, иначе showWizardStep(PERMISSIONS) отскочит обратно на IMPORT.
            showWizardStep(PipBoyWizardStep.PERMISSIONS, allowAutoAdvance = false)
        }
        w.btnWizardImportDone.setOnClickListener {
            playButtonAudio()
            // Продолжает независимо от того, импортировано что-то или нет — донастроить можно в Settings.
            if (pipBoyMode == PipBoyMode.PHONE) {
                finishPhoneModeSetup()
            } else {
                setupBluetooth()
                showWizardStep(PipBoyWizardStep.PAIRING)
            }
        }
        w.btnWizardImportVoice.setOnClickListener {
            playButtonAudio()
            openVoiceModelZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        w.btnWizardImportMap.setOnClickListener {
            playButtonAudio()
            openMapBundleTreeLauncher.launch(null)
        }

        // Шаг 5: Pairing
        w.btnWizardPairingBack.setOnClickListener {
            playButtonAudio()
            stopPairingScan()
            // allowAutoAdvance=false, иначе PERMISSIONS тут же отскочит обратно на PAIRING.
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
        // Тот же откат дизейбла кнопок и свайпа, что и в finishPhoneModeSetup().
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        bindingMain.viewPowerOff.animate().cancel()
        bindingMain.viewPowerOff.visibility = View.GONE
        updateScreenGlareVisibility()
        loadViewState()
        if (row2Views.isEmpty()) {
            menuChangeBLE(curMenu)
            menuNavigator.resetToRoot(menuRootNodesFor(curMenu))
            // Стартовый курсор энкодера — первый дочерний узел бокового меню.
            menuNavigator.activateSelected()
        }
        startContinuousGlitch()
        startAmbientBackgroundSound()
    }


    // ===== РАЗМЕР ЭКРАНА =====
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

    // ===== КАРТА =====
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
                mapScreen.photoViewMap.colorFilter = PorterDuffColorFilter(themeAccentColor(), PorterDuff.Mode.MULTIPLY)
                mapScreen.photoViewMap.visibility = View.VISIBLE
                mapScreen.tvPermissionsCheckResult.visibility = View.GONE
                mapScreen.viewMapOverlay.visibility = View.VISIBLE
                mapScreen.viewMapOverlay.routePx = emptyList()
                mapScreen.layoutMapMenuContainer.visibility = View.VISIBLE
                mapScreen.incLayoutTabItemsMapNamePopup.root.visibility = View.GONE
                // PipWizardButtonStyle-кнопки тонируются вручную кодом, как в Settings.
                val mapAccentColor = themeAccentColor()
                val mapAccent = ColorStateList.valueOf(mapAccentColor)
                listOf(
                    mapScreen.btnMapMarkerDetailEdit,
                    mapScreen.btnMapMarkerDetailRoute,
                    mapScreen.btnMapMarkerDetailDelete,
                    mapScreen.btnMapMarkerDetailBack,
                    mapScreen.incLayoutTabItemsMapNamePopup.btnMarkerNamePopupCancel,
                    mapScreen.incLayoutTabItemsMapNamePopup.btnMarkerNamePopupSave,
                    mapScreen.incLayoutTabItemsMapNamePopup.btnMarkerNamePopupMic,
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
                // Уголки панорамирования — без фона, только цвет текста.
                listOf(
                    mapScreen.btnMapPanUp,
                    mapScreen.btnMapPanDown,
                    mapScreen.btnMapPanLeft,
                    mapScreen.btnMapPanRight,
                ).forEach { it.setTextColor(mapAccentColor) }
                // Прицелы красятся темой везде, кроме центрального крестовидного и прицела над отметкой — те красные для контраста с картой.
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
                    mapScreen.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupMicFocus,
                ).forEach { it.backgroundTintList = mapFocusAccent }
                // ImageButton без своего tint наследует android:tint темы — сбрасываем, иначе стрелка сливается с фоном.
                mapScreen.btnMapCenter.imageTintList = null
                // android:tint="@null" в XML недостаточно — глиф сливался с акцентным фоном без явного сброса.
                ImageViewCompat.setImageTintList(mapScreen.incLayoutTabItemsMapNamePopup.btnMarkerNamePopupMic, null)
                // Тот же сброс tint для иконок карточки метки и попапа тапа.
                listOf(
                    mapScreen.btnMapMarkerDetailEdit,
                    mapScreen.btnMapMarkerDetailRoute,
                    mapScreen.btnMapMarkerDetailDelete,
                    mapScreen.btnMapMarkerDetailBack,
                    mapScreen.btnMapTapChoiceRoute,
                    mapScreen.btnMapTapChoiceMarker,
                ).forEach { it.imageTintList = null }
                hideMapHint()
                // Подсветку пункта 0 здесь не трогаем: блок асинхронный, рамкой управляет listener кнопки.
                showMapMenuState(MapMenuState.ROOT)
                refreshMarkerPins()
                // Оверлей рисует в пространстве экрана, а точки хранит в пространстве битмапа — пересчитываем матрицу.
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
                    // Тап по сырой карте синхронизирует курсор энкодера и даёт ровно один звук на весь тап.
                    playConfirmAudio()
                    when (mapTapMode) {
                        MapTapMode.PLACE_MARKER -> {
                            armTapMode(MapTapMode.NONE)
                            suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerPopupParentPath() + 0) }
                            showMarkerNamePopupForNewMarker(lat, lon)
                        }
                        MapTapMode.ROUTE_TO_POINT -> {
                            armTapMode(MapTapMode.NONE)
                            suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 0) }
                            routeTo(lat, lon, listOf(mapRootIndex("ROUTE")))
                        }
                        MapTapMode.NONE -> {
                            val tappedPx = geoReference.latLonToPixel(lat, lon)
                            val marker = findMarkerNearTap(tappedPx)
                            if (marker != null) {
                                // Тап по значку ведёт туда же, куда выбор из списка меток — тач и энкодер должны совпадать.
                                mapMenuListReturnState = MapMenuState.ROOT
                                showMapMenuState(MapMenuState.MARKER_LIST)
                                val markerIndex = markers.indexOfFirst { it.id == marker.id }
                                if (markerIndex != -1) {
                                    mapMarkerListAdapter.setSelectedPositionSilently(markerIndex)
                                    suppressTickAroundTouchSync { syncMapEncoderPath(listOf(mapRootIndex("MARKER_LIST"), markerIndex, 0)) }
                                }
                                showMarkerDetail(marker)
                            } else {
                                suppressTickAroundTouchSync { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0)) }
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
    /** Останавливать при уходе с экрана карты. */
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
    /** Строит матрицу вручную. */
    private fun recenterMapOnUser() {
        val userPx = bindingMain.incLayoutTabItemsMap.viewMapOverlay.userLocationPx ?: return
        centerMapOnBitmapPoint(userPx)
    }
    /** Нижний слот карты. */
    private fun mapBottomOverlayHeightPx(): Float {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        return listOf(
            mapScreen.layoutMapMarkerDetail,
            mapScreen.layoutMapTapChoice,
            mapScreen.layoutMapRouteControls,
            mapScreen.tvMapHint,
        ).firstOrNull { it.visibility == View.VISIBLE }?.height?.toFloat() ?: 0f
    }
    /** Сдвигает PhotoView. */
    private fun centerMapOnBitmapPoint(targetPx: PointF) {
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        // getDisplayMatrix() отдаёт полную матрицу — по ней находим текущую позицию точки на экране.
        val fullMatrix = Matrix()
        photoView.getDisplayMatrix(fullMatrix)
        val screenPoint = floatArrayOf(targetPx.x, targetPx.y)
        fullMatrix.mapPoints(screenPoint)
        val dx = photoView.width / 2f - screenPoint[0]
        val dy = (photoView.height - mapBottomOverlayHeightPx()) / 2f - screenPoint[1]
        // setDisplayMatrix() пишет в supp-матрицу, а не в полную: сдвигаем текущую supp на экранную дельту, иначе базовая применяется дважды.
        val suppMatrix = Matrix()
        photoView.getSuppMatrix(suppMatrix)
        suppMatrix.postTranslate(dx, dy)
        photoView.setDisplayMatrix(suppMatrix)
    }
    /** Пересчитывает пиксельные позиции маркеров и отдаёт оверлею; звать после любого изменения списка. */
    private fun refreshMarkerPins() {
        val geoReference = mapGeoReference ?: return
        bindingMain.incLayoutTabItemsMap.viewMapOverlay.markerPins =
            markers.map { it.name to geoReference.latLonToPixel(it.lat, it.lon) }
    }
    /** Прицел над отметкой из списка: позиция считается вручную из displayMatrix — у отметок оверлея нет своего @id. */
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
    /** Три состояния левого меню: корень, подменю маршрута, список отметок. */
    private enum class MapMenuState { ROOT, ROUTE_SUBMENU, MARKER_LIST }
    /** Метаданные корня и подменю "Маршрут". */
    private data class MapMenuItemMeta(val key: String, val labelRes: Int, val action: () -> Unit)
    private val mapRootMeta: List<MapMenuItemMeta> by lazy {
        listOf(
            // Гейт "только режимы с энкодером" не здесь: mapRootMeta кешируется by lazy до того, как pipBoyMode известен.
            MapMenuItemMeta("MAP_CONTROLS", R.string.map_menu_control_button) {
                mapControlMode = MapControlMode.ROOT
                setMapControlOverlayVisible(true)
            },
            // "Поставить отметку" открывает ту же панель Crosshair/Pan/Zoom/Center/Back, что и "Управление картой".
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
            // Не прыгает обратно в ROOT по выбору — сайдбар уходит туда, только когда маршрут построен.
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
    /** Пункты бокового меню Map для тача; mapRootChildrenNodes() ищет позиции именно в этом списке. */
    private fun mapRootSidebarItems(): List<SidebarMenuItem<String>> {
        val items = mapRootMeta.filter { it.key != "MAP_CONTROLS" || pipBoyMode != PipBoyMode.PHONE }
            .map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes)) }
        // "В меню" последним пунктом — иначе курсор энкодера некуда вернуть на уровень выше.
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    private lateinit var mapRootAdapter: SidebarMenuAdapter<String>
    private lateinit var mapRouteSubmenuAdapter: SidebarMenuAdapter<String>
    /** Адаптер списка отметок полем, а не локальным val — нужен mapMarkerListChildrenNodes(). */
    private lateinit var mapMarkerListAdapter: SidebarMenuAdapter<MapMarker?>
    /** Какая из двух панелей делит общий набор Zoom/Center/Pan/Crosshair/Back — нужно тачу по крестику. */
    private var mapControlMode: MapControlMode = MapControlMode.ROOT
    private fun showMapMenuState(state: MapMenuState) {
        // Навигация по меню прерывает незавершённый взвод тапа, иначе следующий тап неожиданно поставит отметку.
        if (mapTapMode != MapTapMode.NONE) {
            armTapMode(MapTapMode.NONE)
        }
        // Переход в любое состояние меню закрывает панель управления и попап имени — они взаимоисключающи.
        setMapControlOverlayVisible(false)
        hideMarkerNamePopup()
        mapMenuState = state
        val menu = bindingMain.incLayoutTabItemsMap
        menu.recyclerMapMenuRoot.visibility = if (state == MapMenuState.ROOT) View.VISIBLE else View.GONE
        menu.recyclerMapMenuRouteSubmenu.visibility = if (state == MapMenuState.ROUTE_SUBMENU) View.VISIBLE else View.GONE
        menu.layoutMapMenuMarkerList.visibility = if (state == MapMenuState.MARKER_LIST) View.VISIBLE else View.GONE
        // Курсор не сбрасывается: вглубь — с индекса 0 через действие-триггер, назад — остаётся где был.
        if (state == MapMenuState.MARKER_LIST) {
            bindMarkerListAdapter()
        } else {
            hideMarkerDetail()
        }
        // Навигация по меню отменяет незавершённый выбор [Route]/[Marker].
        hideMapTapChoice()
    }
    /** Пересобирается при каждом входе в MARKER_LIST, поэтому курсор всегда стартует с индекса 0. */
    private fun bindMarkerListAdapter() {
        val menu = bindingMain.incLayoutTabItemsMap
        menu.tvMapMarkerListEmpty.visibility = if (markers.isEmpty()) View.VISIBLE else View.GONE
        val items: List<SidebarMenuItem<MapMarker?>> = markers.map { marker -> SidebarMenuItem<MapMarker?>(payload = marker, label = marker.name) } +
            SidebarMenuItem(payload = null, label = getString(R.string.wizard_back))
        // "До отметки" — выбор сразу строит маршрут; "Список меток" — открывает карточку.
        val adapter = SidebarMenuAdapter(
            items = items,
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // Звук даёт onSelect ниже — ровно один на тап, тик глушится на время синхронизации.
            playSelectSound = {},
            onSelect = { position, item ->
                // Для Back путь останавливается на родителе списка — там курсор окажется после popLevel().
                val marker = item.payload
                val path = when {
                    marker == null -> mapMarkerListParentPath()
                    mapMenuListReturnState == MapMenuState.ROUTE_SUBMENU -> mapMarkerListParentPath() + position
                    else -> mapMarkerListParentPath() + position + 0
                }
                playConfirmAudio()
                suppressTickAroundTouchSync { syncMapEncoderPath(path) }
                when {
                    marker == null -> showMapMenuState(mapMenuListReturnState)
                    // В ROOT сайдбар переводит сама routeTo() по факту построения, не по выбору цели.
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
    /** Карточка деталей отметки делит нижний слот с попапом выбора и панелью маршрута, поэтому прячет обе. */
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
    /** Звать сразу после showMarkerDetail(): центрирование отложено до layout-прохода карточки, иначе её высота 0. */
    private fun centerMapOnMarkerDeferred(marker: MapMarker) {
        val geoReference = mapGeoReference ?: return
        val targetPx = geoReference.latLonToPixel(marker.lat, marker.lon)
        bindingMain.incLayoutTabItemsMap.layoutMapMarkerDetail.post { centerMapOnBitmapPoint(targetPx) }
    }
    private fun hideMarkerDetail() {
        selectedMarkerForDetail = null
        bindingMain.incLayoutTabItemsMap.layoutMapMarkerDetail.visibility = View.GONE
        // Панель маршрута была спрятана визуально, а не сброшена — восстановить, если маршрут ещё есть.
        updateRouteControlsVisibility()
    }
    /** Тап по пустой точке предлагает выбор [Route]/[Marker] вместо предопределённого действия. */
    private fun showMapTapChoice(lat: Double, lon: Double) {
        pendingTapChoiceLatLon = lat to lon
        val mapScreen = bindingMain.incLayoutTabItemsMap
        mapScreen.tvMapTapChoiceCoords.text = String.format(Locale.getDefault(), "%.5f, %.5f", lat, lon)
        selectedMarkerForDetail = null
        mapScreen.layoutMapMarkerDetail.visibility = View.GONE
        mapScreen.layoutMapRouteControls.visibility = View.GONE
        mapScreen.layoutMapTapChoice.visibility = View.VISIBLE
        // Кнопка "←" должна прятаться под этой панелью, а не оставаться поверх.
        refreshMapControlBackButtonVisibility()
    }
    private fun hideMapTapChoice() {
        pendingTapChoiceLatLon = null
        bindingMain.incLayoutTabItemsMap.layoutMapTapChoice.visibility = View.GONE
        updateRouteControlsVisibility()
        refreshMapControlBackButtonVisibility()
    }
    /** Ближайший к тапу маркер в экранных координатах, иначе радиус захвата плавал бы с зумом. */
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
    /** PhotoView.setScale() кидает исключение вне [minimumScale, maximumScale] — клэмпим сами. */
    private fun zoomMapBy(factor: Float) {
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        val target = (photoView.scale * factor).coerceIn(photoView.minimumScale, photoView.maximumScale)
        photoView.setScale(target, true)
    }
    private fun showMapHint(text: String) {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        // Делит нижний слот с карточкой отметки, попапом и панелью маршрута — прячет их взаимоисключающе.
        selectedMarkerForDetail = null
        pendingTapChoiceLatLon = null
        mapScreen.layoutMapMarkerDetail.visibility = View.GONE
        mapScreen.layoutMapTapChoice.visibility = View.GONE
        mapScreen.layoutMapRouteControls.visibility = View.GONE
        val hintView = mapScreen.tvMapHint
        hintView.text = text
        // backgroundTintList = null обязателен: иначе AppCompat подмешает акцент темы поверх любого фона.
        hintView.backgroundTintList = null
        hintView.setBackgroundColor(ContextCompat.getColor(this, R.color.pip_background_darker))
        hintView.setTextColor(themeAccentColor())
        hintView.visibility = View.VISIBLE
    }
    private fun hideMapHint() {
        bindingMain.incLayoutTabItemsMap.tvMapHint.visibility = View.GONE
        updateRouteControlsVisibility()
    }
    /** Взвод режима тапа по карте — расстановка отметки либо выбор точки маршрута. */
    private fun armTapMode(mode: MapTapMode) {
        mapTapMode = mode
        // Текстовая подсказка только в режиме Телефон: в PipBoy её место занимает прицел и панель управления.
        when (mode) {
            MapTapMode.PLACE_MARKER -> if (pipBoyMode == PipBoyMode.PHONE) showMapHint(getString(R.string.map_hint_place_marker))
            MapTapMode.ROUTE_TO_POINT -> if (pipBoyMode == PipBoyMode.PHONE) showMapHint(getString(R.string.map_hint_route_to_point))
            MapTapMode.NONE -> hideMapHint()
        }
    }
    // Клавиатура открывается обычным тапом по полю — showSoftInput() вне ответа на касание Android игнорирует.
    private fun showMarkerNamePopupForNewMarker(lat: Double, lon: Double) {
        editingMarkerId = null
        pendingMarkerLatLon = lat to lon
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        popup.etMarkerNameValue.setText("")
        popup.root.visibility = View.VISIBLE
        mapMarkerDictation.refreshAvailability()
    }
    private fun showMarkerNamePopupForEdit(marker: MapMarker) {
        editingMarkerId = marker.id
        pendingMarkerLatLon = marker.lat to marker.lon
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        popup.etMarkerNameValue.setText(marker.name)
        popup.root.visibility = View.VISIBLE
        mapMarkerDictation.refreshAvailability()
    }
    private fun hideMarkerNamePopup() {
        pendingMarkerLatLon = null
        editingMarkerId = null
        mapMarkerDictation.stop()
        bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.root.visibility = View.GONE
    }
    /** Сбрасывает микрофон и статус-строку к покою при каждом открытии попапа. */
    // ===== ITEMS: ЖУРНАЛ =====
    private fun openJournalScreen() {
        journalEntries = journalRepository.loadAll().toMutableList()
        bindJournalListAdapter()
        hideJournalEntryDetail()
    }
    private lateinit var journalListAdapter: SidebarMenuAdapter<JournalSidebarEntry>
    /** Порядок пунктов обязан совпадать с journalChildrenNodes() дерева энкодера. */
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
    /** [initialSelectedPosition] — после Save/Delete курсор встаёт на затронутую запись, не на 0. */
    private fun bindJournalListAdapter(initialSelectedPosition: Int = 0) {
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        val adapter = SidebarMenuAdapter(
            items = journalSidebarItems(),
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            initialSelectedPosition = initialSelectedPosition,
            // Звук даёт onSelect ниже — ровно один на тап.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: syncCursor() чинит его только внутри активного уровня.
                when (item.payload) {
                    // "+ 0" — тап равносилен ENCBTN: курсор садится на первого ребёнка, который сам откроет нужный экран.
                    is JournalSidebarEntry.NewEntry -> {
                        playConfirmAudio()
                        suppressTickAroundTouchSync { syncJournalEncoderPath(listOf(position, 0)) }
                    }
                    is JournalSidebarEntry.Existing -> {
                        playConfirmAudio()
                        suppressTickAroundTouchSync { syncJournalEncoderPath(listOf(position, 0)) }
                    }
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
    // Подменяется только YEAR — реальные месяц, день и время записи остаются как есть.
    private fun formatJournalDate(epochMillis: Long): String {
        val gameCalendar = Calendar.getInstance()
        gameCalendar.timeInMillis = epochMillis
        gameCalendar.set(Calendar.YEAR, sharedPreferences.getInt(gameYear_SPKey, 2276))
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(gameCalendar.time)
    }
    /** Карточка записи взаимоисключающа с подсказкой и редактором; сброс в начале — идемпотентная подстраховка. */
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
    /** Подсказка справа зависит от journalEntries, а не от списка — в списке всегда есть "Новая запись". */
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
    // Клавиатура открывается обычным тапом по EditText, без showSoftInput().
    /** Ранний выход по journalEditorOpenFor: onHighlight узла MIC зовёт эту функцию на каждый возврат курсора. */
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
        journalDictation.refreshAvailability()
    }
    /** То же, что showJournalEntryEditorForNew(), но подменяет собой карточку записи. */
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
        journalDictation.refreshAvailability()
    }
    /** Идемпотентен — зовётся защитно и тогда, когда редактор уже закрыт. */
    private fun hideJournalEntryEditor() {
        journalEditorOpenFor = null
        editingJournalEntryId = null
        journalDictation.stop()
        setAllJournalEntryEditorFocusesHidden()
        bindingMain.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.root.visibility = View.GONE
    }
    /** Сбрасывает микрофон и статус-строку к покою при каждом открытии попапа. */
    /** Индекс записи со сдвигом на "Новую запись" — общая точка между Save и Delete. */
    private fun journalEntrySidebarIndex(entryId: String): Int {
        val sorted = journalEntries.sortedByDescending { it.createdAtEpochMillis }
        val index = sorted.indexOfFirst { it.id == entryId }
        return if (index >= 0) index + 1 else 0
    }
    /** Путь от узла JOURNAL до Mic/Cancel/Save; editingJournalEntryId читать до того, как Cancel/Save его сбросят. */
    private fun journalEditorPathPrefix(): List<Int> {
        val editingId = editingJournalEntryId
        return if (editingId != null) listOf(journalEntrySidebarIndex(editingId), 0) else listOf(0)
    }
    /** Cancel данные не меняет: поднимает курсор на список (новая запись) или на карточку (правка). */
    private fun performJournalEntryCancel() {
        val wasEditing = editingJournalEntryId != null
        hideJournalEntryEditor()
        menuNavigator.popLevel()
        if (wasEditing) menuNavigator.popLevel()
    }
    /** Save — курсор всегда приземляется на карточку сохранённой записи. */
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
    /** Delete без подтверждения; курсор возвращается в список — узла записи больше нет. */
    private fun performJournalEntryDelete(entry: JournalEntry) {
        journalRepository.delete(entry.id)
        journalEntries.removeAll { it.id == entry.id }
        hideJournalEntryDetail()
        bindJournalListAdapter()
        menuNavigator.popLevel()
        menuNavigator.replaceChildrenOf("JOURNAL", journalChildrenNodes())
    }
    /** Пеший маршрут с текущей GPS-позиции, расчёт на Dispatchers.Default. */
    /** [returnPath] — куда вернуть курсор после Cancel/Stop: вызывающий передаёт явно, постфактум контекст не восстановить. */
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
                // В ROOT сайдбар уходит по факту построения маршрута — единая точка для всех вызовов routeTo().
                showMapMenuState(MapMenuState.ROOT)
                // Молча: если returnPath совпадёт с самим узлом MAP, его onHighlight заново открыл бы экран и стёр маршрут.
                syncMapEncoderPathSilently(returnPath)
                menuNavigator.pushLevel(mapRouteControlsChildrenNodes(), tag = "MAP_ROUTE_CONTROLS")
                updateRouteControlsVisibility()
                // Отложено до layout-прохода панели — иначе mapBottomOverlayHeightPx() прочитает 0.
                bindingMain.incLayoutTabItemsMap.layoutMapRouteControls.post {
                    fitMapToRoute(path, destLat, destLon)
                }
            }
        }
    }
    /** Пишет путь и в лат/лон для расчётов, и в пиксели битмапа для отрисовки. */
    private fun applyRoutePath(geoReference: GeoReference, path: List<Pair<Double, Double>>) {
        mapRouteLatLonPath = path
        bindingMain.incLayoutTabItemsMap.viewMapOverlay.routePx =
            path.map { (lat, lon) -> geoReference.latLonToPixel(lat, lon) }
    }
    /** Вписывает весь построенный маршрут в видимую область, меняя и пан, и зум. */
    /** Базовой матрицы нет в паблик API PhotoView — выводим трюком с suppMatrix=identity, дальше
     * newSupp = targetDraw * base^-1. */
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
        // Абсолютный масштаб переводим в единицы photoView.scale, чтобы клэмпить в [minimumScale, maximumScale].
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
    /** [Cancel] на построенном маршруте и [Stop] на активном следовании полностью сбрасывают маршрут. */
    private fun cancelActiveRoute() {
        mapRouteState = MapRouteState.NONE
        mapRouteDestination = null
        mapRouteLatLonPath = emptyList()
        bindingMain.incLayoutTabItemsMap.viewMapOverlay.routePx = emptyList()
        updateRouteControlsVisibility()
    }
    /** Единая точка правды для панели маршрута; если карточка или попап открыты — не делает ничего, те восстановят её сами. */
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
    /** Следование по маршруту: на каждый GPS-фикс обновляет остаток и перестраивает при отклонении. */
    private fun updateActiveNavigation(location: Location) {
        val destination = mapRouteDestination ?: return
        val path = mapRouteLatLonPath
        if (path.isEmpty()) return
        // Ближайшая вершина графа, не проекция на отрезок — достаточное приближение для масштаба полигона.
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
    /** Обновляет статус бандла карты сразу и в Settings, и на шаге IMPORT мастера. */
    private fun refreshMapBundleStatus() {
        val text = if (!mapBundleRepository.hasBundle()) {
            getString(R.string.map_bundle_status_none)
        } else {
            val dateText = mapBundleRepository.importedAtEpochMillis()?.let {
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "?"
            val folderName = truncateFileName(mapBundleRepository.importedSourceFolderName() ?: "?")
            getString(R.string.map_bundle_status_imported, folderName, dateText)
        }
        bindingMain.incLayoutSettingsGlobal.tvMapBundleStatus.text = text
        bindingMain.incLayoutPipboy2000Wizard.tvWizardImportMapStatus.text = text
    }

    /** Обновляет статус модели Vosk сразу и в Settings, и на шаге IMPORT мастера. */
    private fun refreshVoiceModelStatus() {
        val text = if (!voiceModelRepository.hasModel()) {
            getString(R.string.voice_model_status_none)
        } else {
            val dateText = voiceModelRepository.importedAtEpochMillis()?.let {
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "?"
            val fileName = truncateFileName(voiceModelRepository.importedSourceFileName() ?: "?")
            getString(R.string.voice_model_status_imported, fileName, dateText)
        }
        bindingMain.incLayoutSettingsGlobal.tvVoiceModelStatus.text = text
        bindingMain.incLayoutPipboy2000Wizard.tvWizardImportVoiceStatus.text = text
    }

    // ===== ИЗМЕНЕНИЯ ИНТЕРФЕЙСА =====
    /** Блик скрывается на выключенных состояниях, иначе просвечивает поверх сплошного чёрного. */
    private fun updateScreenGlareVisibility() {
        val isOff = bindingMain.viewPowerOff.visibility == View.VISIBLE ||
            bindingMain.incLayoutPipboy2000Wizard.layoutWizardPowerHint.visibility == View.VISIBLE
        bindingMain.imgScreenglare.visibility = if (isOff) View.GONE else View.VISIBLE
    }
    /** Мгновенный выключенный вид без звука и анимации — служебный дефолт при входе в мастер, не applyPowerState(false). */
    private fun setPowerOffInstant() {
        cancelBootSequence()
        val overlay = bindingMain.viewPowerOff
        overlay.animate().cancel()
        overlay.alpha = 1f
        overlay.visibility = View.VISIBLE
        updateScreenGlareVisibility()
    }

    /** Состояние экрана OFF <-> ON: ESP32 хозяин состояния, применяем как есть, локально не тумблерим. */
    /** Зовётся только из разбора команды POWER и играет полную анимацию в обе стороны. */
    private fun applyPowerState(on: Boolean) {
        if (on) {
            cancelBootSequence()
            playBootSequence()
            // Мастер настройки PipBoy 2000/3000 больше не нужен — POWER реально пришёл.
            val wizardWasOpen = bindingMain.incLayoutPipboy2000Wizard.root.visibility == View.VISIBLE
            bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
            // Гейт по wizardWasOpen: на обычных POWER-переключениях кнопки и так уже включены.
            if (wizardWasOpen) {
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
            // Пока шли Permissions и подсказка про POWER, окно было временно fullscreen — применяем настроенную область.
            loadViewState()
        } else {
            cancelBootSequence()
            playShutdownSequence()
        }
    }

    // ===== ФОНОВЫЙ ЭМБИЕНТ =====
    // Плеер создаётся лениво, когда интерфейс реально доступен игроку, и глохнет при выключении экрана.
    // ambientShouldBePlaying — намерение, отдельное от факта: onStop()/onStart() его не трогают,
    // звук уходит на время сворачивания и возвращается сам.
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

    // ===== АНИМАЦИЯ ВКЛЮЧЕНИЯ =====
    private var bootSoundPlayer: MediaPlayer? = null
    // Токен позволяет оборвать именно шаги анимации, не трогая прочие задачи на том же handler.
    private val bootSequenceToken = Any()

    private fun bootPostDelayed(delayMs: Long, action: () -> Unit) {
        handler.postAtTime(Runnable { action() }, bootSequenceToken, SystemClock.uptimeMillis() + delayMs)
    }

    /** Кадр 1 -> кадр 2 -> кадр 3 -> основной интерфейс; реальный POWER:1 и debug-инъекция ведут сюда одинаково. */
    private fun playBootSequence() {
        playBootSwitchSound()
        val boot = bindingMain.incLayoutBootSequence
        val accent = themeAccentColor()
        // Лого кадра 1 — PNG с альфа-маской, своей раскраски кодом не требует.
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

    /** Суммарная длительность заставки считается, а не хардкодится — она зависит от ширины экрана. */
    private val bootTotalDurationMs: Long
        get() = BOOT_FRAME_LOGO_DURATION_MS + BOOT_FRAME_CODEWALL_DURATION_MS +
            buildBootTerminalText().length * BOOT_TERMINAL_CHAR_DELAY_MS + BOOT_TERMINAL_END_HOLD_MS

    private fun startBootCodewall() {
        val boot = bindingMain.incLayoutBootSequence
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
        val boot = bindingMain.incLayoutBootSequence
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
        val boot = bindingMain.incLayoutBootSequence
        val paint = boot.tvBootTerminal.paint
        val marginsPx = 2 * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics)
        val availableWidthPx = (
            bindingMain.root.width - bindingMain.root.paddingLeft - bindingMain.root.paddingRight - marginsPx
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

    /** Одноразовый звук POWER:1, сам себя освобождает по завершении. */
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
        // Первый POWER:1 за сессию: мастер PipBoy не проходит через resetToRoot(), строку 2 и стек надо поднять явно.
        if (row2Views.isEmpty()) {
            menuChangeBLE(curMenu)
            menuNavigator.resetToRoot(menuRootNodesFor(curMenu))
            // Стартовый курсор энкодера — первый дочерний узел бокового меню.
            menuNavigator.activateSelected()
        }
        // Глитч не ограничен окном после загрузки — фоновый эффект на всё время, пока PipBoy включён.
        startContinuousGlitch()
        startAmbientBackgroundSound()
    }

    /** Обрывает анимацию на любом шаге; стартовое состояние view_power_off готовит следующий play*. */
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

    // ===== АНИМАЦИЯ ВЫКЛЮЧЕНИЯ =====
    private var shutdownSwitchSoundPlayer: MediaPlayer? = null

    /** Щелчок выключения сразу, затем ~2с на текущем экране с импульсами глитча. */
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

    /** Экран гаснет в чёрное, и только на полностью чёрном фоне появляется терминал выключения. */
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

    /** Шапка PIP-OS выводится сразу, тело печатается посимвольно под тот же звук набора. */
    private fun startShutdownTerminal() {
        val boot = bindingMain.incLayoutBootSequence
        boot.tvBootTerminal.setTextColor(themeAccentColor())
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
    private fun startContinuousGlitch() {
        val delay = glitchRandomInt(AMBIENT_GLITCH_MIN_INTERVAL_MS, AMBIENT_GLITCH_MAX_INTERVAL_MS).toLong()
        bootPostDelayed(delay) {
            triggerGlitchPulse()
            startContinuousGlitch()
        }
    }

    /** Импульс: снимок экрана -> та же картинка со сдвигом полос и аберрацией -> короткий показ -> назад. */
    private fun triggerGlitchPulse() {
        val root = bindingMain.root
        if (root.width <= 0 || root.height <= 0) return
        val snapshot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(snapshot))
        val glitched = buildGlitchBitmap(snapshot)
        snapshot.recycle()

        val overlay = bindingMain.ivGlitchOverlay
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

    /** Деревья меню для энкодера: onHighlight каждого узла повторяет то, что делает палец по экрану,
     * и обязан быть безопасен на каждое перемещение курсора — одноразовые действия идут через onActivate. */
    /** PERKS — исключение: список пересобирается при каждом открытии, поэтому пока лист без вложенности. */
    private fun statsMenuRoot(): List<MenuNode> {
        // Status: onHighlight только двигает рамку молча, запуск таймера ранения требует ENCBTN.
        val statusNode = MenuNode(
            id = "STATUS",
            children = statusChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bindingMain.incLayoutTabStatsBottom.btnStatsStatus) }
        )
        // SPECIAL: onHighlight зовёт setSelectedPositionSilently(), а не громкий selectPosition() — тот сработал бы как ENCBTN.
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
                            // Звук выхода из редактирования должен отличаться от звука входа и нажатий +/-.
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
    /** Безусловно ставит курсор энкодера по [path] от детей узла [nodeId] — syncCursor() чинит его
     * только внутри уже активного уровня и молчит, если тач пришёл из другой ветки. */
    /** [loud] = false, когда вызывающий код уже дал свой эффект и звук: иначе задвоится звук либо
     * пойдёт рекурсия через onSelect адаптера. */
    private fun syncEncoderPath(rootNodes: List<MenuNode>, nodeId: String, path: List<Int>, loud: Boolean = true) {
        val rootIndex = rootNodes.indexOfFirst { it.id == nodeId }
        if (rootIndex == -1) return
        val fullPath = listOf(rootIndex) + path
        if (loud) menuNavigator.setPath(rootNodes, fullPath) else menuNavigator.setPathSilently(rootNodes, fullPath)
    }
    private fun syncStatsEncoderPath(nodeId: String, path: List<Int>) = syncEncoderPath(statsMenuRoot(), nodeId, path, loud = true)
    private fun syncStatsEncoderPathSilently(nodeId: String, path: List<Int>) = syncEncoderPath(statsMenuRoot(), nodeId, path, loud = false)
    /** ITEMS: Map, Clock, Journal, Geiger. */
    private fun itemsMenuRoot(): List<MenuNode> {
        val bottom = bindingMain.incLayoutTabItemsBottom
        // Clock — SidebarMenuAdapter, дети clockChildrenNodes().
        val clockNode = MenuNode(
            id = "CLOCK",
            children = clockChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsClock) }
        )
        // GEIGER требует физического корпуса и скрыт в Телефоне; порядок должен совпадать с itemsRow2Items().
        val geigerNode = MenuNode(
            id = "GEIGER",
            children = geigerChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsGeiger) },
        )
        // childrenProvider, а не статичные children: itemsMenuRoot() строится до того, как записи подгружены с диска.
        val journalNode = MenuNode(
            id = "JOURNAL",
            childrenProvider = { journalChildrenNodes() },
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsJournal) },
        )
        // childrenProvider по той же причине, что у JOURNAL: markers грузятся асинхронно в openMapScreen().
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
    /** Позиция узла в itemsMenuRoot(): GEIGER скрыт в Телефоне, поэтому индекс нельзя зашить константой. */
    private fun itemsRootIndexFor(id: String): Int = itemsMenuRoot().indexOfFirst { it.id == id }
    /** Дети JOURNAL; порядок и состав обязаны совпадать с journalSidebarItems() построчно. */
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
    /** Дети записи Journal: Edit проваливается глубже, Delete и Back — листья с onActivate. */
    private fun journalEntryDetailChildrenNodes(entry: JournalEntry): List<MenuNode> {
        val journal = bindingMain.incLayoutTabItemsJournal
        return listOfNotNull(
            MenuNode(
                id = "JOURNAL_ENTRY_EDIT",
                onHighlight = {
                    playTickAudio()
                    // Пересобирает и карточку, и все три прицела — на случай возврата из редактора по Cancel/Save.
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
            // Только режимы с физическим энкодером: в Телефоне кнопкой нечем пользоваться.
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
    /** Дети редактора записи — общие для создания и правки; onHighlight узла MIC открывает редактор идемпотентно. */
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
                        journalDictation.handleMicTap()
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
    /** Дети GEIGER: Reset первым, Menu вторым в любом режиме с энкодером. */
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
    // ===== ITEMS: КАРТА, ЭНКОДЕР =====
    /** Общая панель Zoom/Center/Pan/Crosshair/Back живёт в двух точках дерева и различается поведением крестика. */
    private enum class MapControlMode { ROOT, ROUTE_TO_POINT, PLACE_MARKER }
    /** Геокоордината центра экрана: карта двигается под фиксированной точкой, поэтому берём инверсию текущей displayMatrix. */
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
    /** Сдвигает видимую область на шаг в экранных пикселях — тот же postTranslate, но относительный. */
    private fun panMapBy(dxPx: Float, dyPx: Float) {
        val photoView = bindingMain.incLayoutTabItemsMap.photoViewMap
        val suppMatrix = Matrix()
        photoView.getSuppMatrix(suppMatrix)
        suppMatrix.postTranslate(dxPx, dyPx)
        photoView.setDisplayMatrix(suppMatrix)
    }
    /** Безусловно ставит курсор энкодера по [path] от детей узла MAP. */
    /** [path] обязан указывать до первого ребёнка тапнутого узла, если тот не лист. */
    private fun syncMapEncoderPath(path: List<Int>) =
        syncEncoderPath(itemsMenuRoot(), "MAP", path, loud = true)
    /** То же без onHighlight — onHighlight узла MAP заново открывает экран карты. */
    private fun syncMapEncoderPathSilently(path: List<Int>) =
        syncEncoderPath(itemsMenuRoot(), "MAP", path, loud = false)
    /** То же для экрана Journal: syncCursor() работает, только если энкодер уже стоит на списке записей. */
    private fun syncJournalEncoderPath(path: List<Int>) =
        syncEncoderPath(itemsMenuRoot(), "JOURNAL", path, loud = true)
    /** То же без onHighlight — onHighlight узла JOURNAL перезагружает записи с диска. */
    private fun syncJournalEncoderPathSilently(path: List<Int>) =
        syncEncoderPath(itemsMenuRoot(), "JOURNAL", path, loud = false)
    /** Позиция пункта бокового меню Map по ключу — вынесено для тач-обработчиков. */
    private fun mapRootIndex(key: String): Int = mapRootSidebarItems().indexOfFirst { it.payload == key }
    /** Путь до самого узла панели без её детей; ROUTE_TO_POINT на уровень глубже — он вложен в MAP_ROUTE. */
    private fun mapControlModeRootPath(): List<Int> = when (mapControlMode) {
        MapControlMode.ROOT -> listOf(mapRootIndex("MAP_CONTROLS"))
        MapControlMode.PLACE_MARKER -> listOf(mapRootIndex("PLACE_MARKER"))
        MapControlMode.ROUTE_TO_POINT -> listOf(mapRootIndex("ROUTE"), 0)
    }
    /** Путь до бокового меню Map, куда возвращает "←": для ROUTE_TO_POINT — на уровень выше остальных. */
    private fun mapSidebarRootPathForMode(): List<Int> = when (mapControlMode) {
        MapControlMode.ROUTE_TO_POINT -> listOf(mapRootIndex("ROUTE"))
        else -> mapControlModeRootPath()
    }
    /** Путь до уровня "Список меток"/"До отметки" — общий вход для двух контекстов. */
    private fun mapMarkerListParentPath(): List<Int> =
        if (mapMenuListReturnState == MapMenuState.ROUTE_SUBMENU) listOf(mapRootIndex("ROUTE"), 1) else listOf(mapRootIndex("MARKER_LIST"))
    /** Путь до попапа имени отметки — два возможных родителя, тот же выбор, что в mapMarkerPopupChildrenNodes(). */
    private fun mapMarkerPopupParentPath(): List<Int> {
        // Правка существующей отметки — третья ветка: editingMarkerId читать до того, как Cancel/Save его сбросят.
        val editingId = editingMarkerId
        if (editingId != null) {
            val markerIndex = markers.indexOfFirst { it.id == editingId }
            if (markerIndex != -1) return mapMarkerListParentPath() + markerIndex + 0
        }
        return when (mapControlMode) {
            MapControlMode.PLACE_MARKER -> mapControlModeRootPath() + 0
            else -> listOf(mapRootIndex("MAP_CONTROLS"), 0, 1) // ROOT — через "Place Marker" в панели [Route]/[Marker]/[Cancel]
        }
    }
    /** Показывает и прячет всю группу управления картой разом; прицелы энкодера переключаются отдельно. */
    /** Крестик и уголки — только для режима с энкодером: в Телефоне те же действия делаются жестами и прямым тапом. */
    private fun setMapControlOverlayVisible(visible: Boolean) {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        val visibility = if (visible && pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
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
        // Кнопка "←" имеет отдельную видимость: она должна прятаться под панель выбора, а не исчезать синхронно.
        refreshMapControlBackButtonVisibility()
    }
    /** "←" видна, только пока панель управления открыта и поверх неё не висит панель [Route]/[Marker]/[Cancel]. */
    private fun refreshMapControlBackButtonVisibility() {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        val overlayActive = mapScreen.viewMapCrosshair.visibility == View.VISIBLE
        val tapChoiceOpen = mapScreen.layoutMapTapChoice.visibility == View.VISIBLE
        val visible = overlayActive && !tapChoiceOpen
        mapScreen.btnMapControlBack.visibility = if (visible) View.VISIBLE else View.GONE
        mapScreen.viewMapControlBackBg.visibility = if (visible) View.VISIBLE else View.GONE
    }
    /** Дети всех трёх режимов панели; Crosshair — первый ребёнок, и именно его onHighlight открывает панель,
     * иначе Back немедленно открывал бы её заново. */
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
                // Звук подтверждения на любой ENCBTN по прицелу — узел всё равно проваливается в children следом.
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
                        // Право = отрицательный dx; знак обязан совпадать с тач-обработчиками btnMapPanRight/Left.
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
                        // ROUTE_TO_POINT вложен глубже: одного popLevel() мало, "←" обязан вернуть в боковое меню Map.
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
    /** Дети CROSSHAIR в режиме ROOT — Route/Marker/Cancel; панель открывает onHighlight первого ребёнка. */
    private fun mapCrosshairTapChoiceChildrenNodes(): List<MenuNode> {
        val mapScreen = bindingMain.incLayoutTabItemsMap
        return listOf(
            MenuNode(
                id = "MAP_CTRL_CROSSHAIR_ROUTE",
                onHighlight = {
                    playTickAudio()
                    // Гасим прицел крестика — курсор только что провалился с него сюда.
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
                        // Эта панель бывает только в режиме ROOT ("Управление картой").
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
                // Звук на ENCBTN; сам провал в детей отрабатывает следом как обычно.
                onActivate = { playButtonAudio() },
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
    /** Дети попапа имени отметки — общая функция для двух точек входа; попап открывает onHighlight первого ребёнка. */
    private fun mapMarkerPopupChildrenNodes(
        editingMarker: MapMarker? = null,
        latLonProvider: () -> Pair<Double, Double>? = { null },
    ): List<MenuNode> {
        val popup = bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        return listOf(
            MenuNode(
                id = "MAP_MARKER_POPUP_MIC",
                onHighlight = {
                    playTickAudio()
                    if (editingMarker != null) {
                        // Правка существующей отметки — та же роль, другая функция.
                        showMarkerNamePopupForEdit(editingMarker)
                    } else {
                        // Координату читаем до hideMapTapChoice() — та обнуляет pendingTapChoiceLatLon.
                        val latLon = latLonProvider()
                        hideMapTapChoice()
                        latLon?.let { (lat, lon) -> showMarkerNamePopupForNewMarker(lat, lon) }
                    }
                    // Гасим прицелы уровней выше: какой из трёх входов актуален, эта функция не знает.
                    setMapCrosshairFocused(false)
                    setAllMapTapChoiceFocusesHidden()
                    setAllMapMarkerDetailFocusesHidden()
                    setAllMapMarkerPopupFocusesHidden()
                    setMapMarkerPopupMicFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnMarkerNamePopupMic) {
                        mapMarkerDictation.handleMicTap()
                    }
                },
            ),
            MenuNode(
                id = "MAP_MARKER_POPUP_CANCEL",
                onHighlight = {
                    playTickAudio()
                    setAllMapMarkerPopupFocusesHidden()
                    setMapMarkerPopupCancelFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnMarkerNamePopupCancel) {
                        playButtonAudio()
                        setMapMarkerPopupCancelFocused(false)
                        // popLevel() внутри performMarkerNamePopupCancel(): число уровней зависит от новая это отметка или правка.
                        performMarkerNamePopupCancel()
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
                        // popLevel() — уже внутри performMarkerNamePopupSave(), см. коммент выше.
                        performMarkerNamePopupSave()
                    }
                },
            ),
        )
    }
    /** Общее тело Cancel/Save попапа — и для тача, и для ENCBTN. */
    /** Сколько popLevel() нужно, чтобы вернуться на стабильный узел, а не на одноразовый промежуточный выбор:
     * правка — 2, PLACE_MARKER — 1, ROOT — 2; [editingId] читать до hideMarkerNamePopup(). */
    private fun mapMarkerPopupPopLevelCount(editingId: String?): Int = when {
        editingId != null -> 2
        mapControlMode == MapControlMode.PLACE_MARKER -> 1
        else -> 2
    }
    /** Cancel — общее тело для тача и ENCBTN. */
    private fun performMarkerNamePopupCancel() {
        val popCount = mapMarkerPopupPopLevelCount(editingMarkerId)
        hideMarkerNamePopup()
        repeat(popCount) { menuNavigator.popLevel() }
    }
    /** Save — курсор идёт на карточку сохранённой отметки, для новой — на крестик. */
    /** Список обновляем ПОСЛЕ popLevel(): replaceChildrenOf() сверяет родителя верхнего уровня стека и
     * до подъёма всегда была no-op. */
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
            }
        } else {
            val (lat, lon) = pendingMarkerLatLon ?: return
            val marker = MapMarker(UUID.randomUUID().toString(), name, lat, lon, System.currentTimeMillis())
            markerRepository.add(marker)
            markers.add(marker)
        }
        refreshMarkerPins()
        bindMarkerListAdapter()
        val popCount = mapMarkerPopupPopLevelCount(editingId)
        hideMarkerNamePopup()
        repeat(popCount) { menuNavigator.popLevel() }
        menuNavigator.replaceChildrenOf("MAP_MARKER_LIST", mapMarkerListChildrenNodes(MapMenuState.ROOT))
        menuNavigator.replaceChildrenOf("MAP_ROUTE_TO_MARKER", mapMarkerListChildrenNodes(MapMenuState.ROUTE_SUBMENU))
    }
    /** Панель построенного или активного маршрута; курсор попадает сюда программным pushLevel() из routeTo(). */
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
    /** Дети узла MAP; порядок и гейт обязаны совпадать с mapRootMeta, панели открывает первый ребёнок. */
    private fun mapRootChildrenNodes(): List<MenuNode> {
        // Позиции ищем в mapRootSidebarItems(), уже отфильтрованном по режиму, а не в сыром mapRootMeta.
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
            // "Поставить отметку" — та же панель, но крестик проваливается прямо в попап ввода имени.
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
    /** Дети MAP_ROUTE; порядок обязан совпадать с mapRouteSubmenuMeta, подменю показывает первый ребёнок. */
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
    /** Дети списка меток для обоих входов: из "До отметки" выбор строит маршрут, иначе — провал в карточку. */
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
                        // Центрирование раньше срабатывало только по тачу, не по курсору энкодера.
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
    /** Карточка отметки: Edit/Route/Delete/Back; Back — только режимы с физическим энкодером. */
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
                // children, а не лист: попапу нужно собственное место в дереве, иначе курсору после Save неоткуда подниматься.
                children = mapMarkerPopupChildrenNodes(editingMarker = marker),
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
                        // Гасить свой прицел ПЕРЕД hideMarkerDetail(): иначе он всплывёт вместе со следующим показом карточки.
                        setMapMarkerDetailRouteFocused(false)
                        // Карточка отметки всегда достигается через "Список меток" — при входе из "До отметки" её нет.
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
                        // Гасить свой прицел ПЕРЕД popLevel(), иначе он остаётся висеть на кнопке после возврата.
                        setMapMarkerDetailBackFocused(false)
                        menuNavigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Удаление отметки — общая точка для тача и энкодера; replaceChildrenOf() сам no-op на чужом родителе. */
    private fun performMapMarkerDelete(marker: MapMarker) {
        // Гасим прицелы карточки до того, как она исчезнет вместе с удалённой отметкой.
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
        // HOLOTAPES требует физического корпуса и скрыт в Телефоне; порядок должен совпадать с dataRow2Items().
        return listOfNotNull(
            MenuNode(
                id = "MISC",
                children = dataFilesChildrenNodes(),
                onHighlight = { simulateEncoderTabHighlight(bottom.btnDataMisc) },
            ),
            if (pipBoyMode != PipBoyMode.PHONE) MenuNode("HOLOTAPES") { bottom.btnDataHolotapes.performClick() } else null,
        )
    }
    /** То же, что syncEncoderPath(), но для дерева DATA. */
    private fun syncDataEncoderPath(nodeId: String, path: List<Int>) = syncEncoderPath(dataMenuRoot(), nodeId, path, loud = true)
    private fun syncDataEncoderPathSilently(nodeId: String, path: List<Int>) = syncEncoderPath(dataMenuRoot(), nodeId, path, loud = false)
    /** RADIO — раздел без второго уровня: корень из одного листа, чтобы ENC и ENCBTN не падали. */
    private fun radioMenuRoot(): List<MenuNode> {
        return listOf(MenuNode("RADIO") { })
    }
    /** Корень дерева энкодера по имени раздела — общая точка restoreAppState() и finishBootSequence(). */
    private fun menuRootNodesFor(menu: String): List<MenuNode> = when (menu) {
        "ITEMS" -> itemsMenuRoot()
        "DATA" -> dataMenuRoot()
        "RADIO" -> radioMenuRoot()
        else -> statsMenuRoot()
    }
    /** RADIOPWR: источник истины — физический тумблер ESP32; on=true ещё и уводит экран на RADIO и шлёт частоту. */
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
    /** RADIOFREQ — абсолютное значение, МГц×10; сохраняется как "последняя волна" для следующего RADIOPWR:1. */
    private fun updateRadioFrequencyDisplay(freqTenthsOfMHz: Int) {
        sharedPreferences.edit().putInt(radioLastFrequency_SPKey, freqTenthsOfMHz).apply()
        bindingMain.incLayoutTabDataRadio.tvRadioFrequency.text =
            String.format(Locale.US, "%.1f MHz", freqTenthsOfMHz / 10f)
    }
    /** VOLUME — только дельты со второго энкодера; значение живёт лишь для шкалы и не переживает перезапуск. */
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
    /** Накопление дозы: ESP32 шлёт мгновенное значение раз в секунду, суммирует и клампит приложение. */
    private fun accumulateGeigerDose(radThisSecond: Int) {
        val prevDose = sharedPreferences.getInt(geigerDose_SPKey, 0)
        val curDose = (prevDose + radThisSecond).coerceIn(0, GEIGER_LETHAL_DOSE_RAD)
        sharedPreferences.edit().putInt(geigerDose_SPKey, curDose).apply()
        updateGeigerDoseDisplay(curDose)
    }
    /** Общая логика Reset — для тача и для onActivate узла; звук проигрывает вызывающий. */
    private fun resetGeigerDose() {
        sharedPreferences.edit().putInt(geigerDose_SPKey, 0).apply()
        updateGeigerDoseDisplay(0)
    }
    /** Стрелка отражает долю дозы от смертельной; bias считается по самой шкале, шире размеченного диапазона. */
    private fun updateGeigerDoseDisplay(dose: Int) {
        val doseText = dose.toString()
        val geiger = bindingMain.incLayoutTabItemsGeiger
        geiger.tvRadArrowValue.text = doseText
        val doseFraction = dose.toFloat() / GEIGER_LETHAL_DOSE_RAD
        val arrowBias = (GEIGER_SCALE_START_BIAS + doseFraction * (GEIGER_SCALE_END_BIAS - GEIGER_SCALE_START_BIAS))
            .coerceIn(GEIGER_SCALE_START_BIAS, GEIGER_SCALE_END_BIAS)
        // Прямая мутация horizontalBias не работает вместе с dimensionRatio — только ConstraintSet.applyTo().
        ConstraintSet().apply {
            clone(geiger.root)
            setHorizontalBias(geiger.imgRadArrow.id, arrowBias)
        }.applyTo(geiger.root)
        geiger.tvGeigerStatus.text = getString(geigerStatusStringRes(dose))
        bindingMain.incLayoutHeaderBottomCommon.tvBottomRadiationValue.text = doseText
    }
    /** Пороги самочувствия фиксированы отдельно и не завязаны на предел шкалы. */
    private fun geigerStatusStringRes(dose: Int): Int = when {
        dose < 200 -> R.string.geiger_status_ok
        dose < 400 -> R.string.geiger_status_mild
        dose < 600 -> R.string.geiger_status_moderate
        dose < 800 -> R.string.geiger_status_severe
        else -> R.string.geiger_status_critical
    }
    /** Разбирает входящую BLE-строку по конвенции КЛЮЧ:ЗНАЧЕНИЕ и раздаёт по обработчикам. */
    private fun handleBleCommand(raw: String) {
        val parts = raw.split(":", limit = 2)
        val key = parts[0]
        val value = parts.getOrNull(1)

        when (key) {
            "STATS" -> {
                menuChangeBLE(key)
                menuNavigator.resetToRoot(statsMenuRoot())
                // Пока таймер ранения актуален, возврат в STATS должен сразу попадать на Stop.
                if (woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD) {
                    menuNavigator.activateSelected()
                }
            }
            "ITEMS" -> { menuChangeBLE(key); menuNavigator.resetToRoot(itemsMenuRoot()) }
            "DATA" -> { menuChangeBLE(key); menuNavigator.resetToRoot(dataMenuRoot()) }
            "POWER" -> applyPowerState(value == "1")
            // Оверлей срабатывания глобальный, вне дерева MenuNavigator: пока он виден, ENCBTN закрывает именно его.
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
                    // У RADIO нет второго уровня, поэтому ENC крутит громкость напрямую, без входа в редактирование.
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
            // RADIOTUNE информационная — экран обновится следующим RADIOFREQ от ESP32.
            "RADIOTUNE" -> Log.i("BLE", "RADIOTUNE:$value")
            "RADIOTUNEBTN" -> Log.i("BLE", "RADIOTUNEBTN")
            "HOLOTAPE" -> Log.i("BLE", "HOLOTAPE:$value — голодиски, блокируется готовностью USB Host")
            else -> Log.w("BLE", "Неизвестная BLE-команда: $raw")
        }
    }

    fun menuChangeBLE(menu: String){
        // curMenu переключается ДО menuOptionClickedBLE(): setupRow2() читает его, чтобы найти кнопку строки 1.
        when(menu){
            "STATS" -> {
                curMenu = "STATS"
                setBottomButtons(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks)
                menuOptionClickedBLE("STATS")
            }
            "ITEMS" -> {
                curMenu = "ITEMS"
                setBottomButtons(bindingMain.incLayoutTabItemsBottom.btnItemsGeiger, bindingMain.incLayoutTabItemsBottom.btnItemsMap, bindingMain.incLayoutTabItemsBottom.btnItemsJournal, bindingMain.incLayoutTabItemsBottom.btnItemsClock)
                menuOptionClickedBLE("ITEMS")
            }
            "DATA" -> {
                curMenu = "DATA"
                setBottomButtons(bindingMain.incLayoutTabDataBottom.btnDataMisc, bindingMain.incLayoutTabDataBottom.btnDataHolotapes)
                menuOptionClickedBLE("DATA")
            }
            "RADIO" -> {
                curMenu = "RADIO"
                // У RADIO нет второго уровня — listBottomButtons пуст, вызов отработает на пустом списке.
                setBottomButtons()
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

    private fun applyAppTheme(uiTheme: UiTheme) {
        applyBackgroundResource(uiTheme)
        applyTextColor(uiTheme)
        applyScrollBar(getDrawableCompat(this, uiTheme.scrollbarRes))
    }
    private fun applyBackgroundResource(uiTheme: UiTheme) {
        // Apply background to relevant views
        val backgrounds = listOf(
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.layoutTabStatsCndPopup
            // Часы, Settings, фильтр и Bluetooth убраны из списка: их корни больше не используют этот бокс-drawable.
        )
        selected_button = uiTheme.selectedButtonRes
        selectedRowButton = uiTheme.selectedRowRes
        backgrounds.forEach { it.setBackgroundResource(uiTheme.boxBackgroundRes) }
    }
    private fun applyTextColor(uiTheme: UiTheme) {
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
        val accentColor = ContextCompat.getColor(this, uiTheme.accentColorRes)
        primaryTextViews.forEach { it.setTextColor(accentColor) }
        // ProgressBar не подхватывает android:tint темы — тонируется явно, как и фон кнопок.
        bindingMain.incLayoutTabDataRadio.radioVolumeBar.progressTintList = ColorStateList.valueOf(accentColor)
    }
    private fun applyScrollBar(scrollbarDrawable: Drawable?){
        scrollbarDrawable?.let {
            // Только обычные ScrollView: у боковых меню ползунок ставит SidebarMenuAdapter.
            val scrollViews = listOf(
                bindingMain.incLayoutTabDataMisc.scrollTabDataMiscText,
                bindingMain.incLayoutSettingsGlobal.scrollSettingsMain,
                bindingMain.incLayoutSettingsGlobal.scrollSettingsGameInfo,
                bindingMain.incLayoutSettingsGlobal.scrollSettingsPreferences,
                bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.scrollBluetoothPairingDevices,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.scrollTutorialWelcomeMain,
                bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage.scrollTutorialPageMain,
                bindingMain.incLayoutFilterModification.scrollFilterModification
                // Add other scroll views as necessary
            )
            scrollViews.forEach { setScrollbarThumb(it, scrollbarDrawable) }
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
    /** Превью SPECIAL при движении курсора; onHighlight зовёт эту функцию, а не громкий selectPosition(). */
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
    /** Кнопки +/- SPECIAL и Skills: значения клампятся на границе диапазона, а не зацикливаются. */
    private fun adjustSelectedSpecial(delta: Int) {
        val position = specialMeta.indexOfFirst { it.key == selectedSPECIAL }
        if (position == -1) return
        val meta = specialMeta[position]
        val prevValue = sharedPreferences.getInt(meta.prefKey, 5)
        val curValue = (prevValue + delta).coerceIn(1, 10)
        sharedPreferences.edit().putInt(meta.prefKey, curValue).apply()
        specialAdapter.updateItemValue(position, curValue.toString())
        if (curValue == prevValue) playErrorAudio() else playConfirmAudio()
        // Тап по +/- переставляет курсор на характеристику и входит в её ValueEditor; guard — чтобы не переигрывать onEnter при удержании.
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
    /** Общий признак "энкодер сфокусирован здесь" — четыре L-уголка на отдельном View рядом с целью. */
    /** В режиме Телефон прицелы не показываются никогда: там нет энкодера, курсор которого они рисуют. */
    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) {
        bracketsView.visibility = if (visible && pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
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
    /** Тот же приём прицела на ITEMS/Гейгер — Reset и "В меню". */
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
    /** Тот же приём на ITEMS/Clock/Мелодии — Select и Back под конкретным треком. */
    private fun setClockMelodySelectFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.viewClockMelodySelectFocus, focused)
    }
    private fun setClockMelodyBackFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.viewClockMelodyBackFocus, focused)
    }
    /** Тот же приём на карточке записи Journal — Edit/Delete/Back. */
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
    /** Тот же приём на редакторе записи — Mic/Cancel/Save. */
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
    /** Тот же приём на панели управления картой: у Zoom и Center один прицел на блок, у пар Pan — два сразу. */
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
    /** Тот же приём на карточке отметки — Edit/Route/Delete/Back. */
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
    /** Тот же приём на панели выбора [Route]/[Marker]/[Cancel]. */
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
    /** Тот же приём на попапе имени отметки — Cancel и Save. */
    private fun setMapMarkerPopupMicFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupMicFocus, focused)
    }
    private fun setMapMarkerPopupCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupCancelFocus, focused)
    }
    private fun setMapMarkerPopupSaveFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupSaveFocus, focused)
    }
    private fun setAllMapMarkerPopupFocusesHidden() {
        setMapMarkerPopupMicFocused(false)
        setMapMarkerPopupCancelFocused(false)
        setMapMarkerPopupSaveFocused(false)
    }
    /** Тот же приём на панели маршрута — Start/Cancel/Stop. */
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
    /** Прицелы на частях тела; setAllCrippledFocusesHidden() — идемпотентная подстраховка при выходе из ветки. */
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
    /** Мгновенный флэш нажатия для непрерывных ENC-действий, где пауза читалась бы как лаг. */
    private fun flashButtonPressImmediate(button: View) {
        button.isPressed = true
        button.postDelayed({ button.isPressed = false }, ENCODER_PRESS_FLASH_DURATION_MS)
    }
    /** Флэш, затем действие — для ENCBTN-команд, которые сами прячут эту же кнопку. */
    private fun flashButtonPressThenRun(button: View, action: () -> Unit) {
        button.isPressed = true
        button.postDelayed({
            button.isPressed = false
            action()
        }, ENCODER_PRESS_FLASH_DURATION_MS)
    }
    /** Дети STATUS: пока таймер ранения актуален, доступны только Stop и шесть частей тела, список ранений
     * недостижим совсем. */
    /** Гашение прицелов в обычной ветке — подстраховка идемпотентности при любой пересборке списка. */
    private fun statusChildrenNodes(): List<MenuNode> {
        return if (woundPhase == WoundPhase.DEAD) {
            // В DEAD курсор встаёт на персонажа, ENCBTN воскрешает тем же путём и с тем же звуком, что тап.
            setWoundStopButtonFocused(false)
            setAllCrippledFocusesHidden()
            listOf(
                MenuNode(
                    id = "REVIVE",
                    onHighlight = { setDeadReviveFocused(true) },
                    onActivate = {
                        playStimpackAudio()
                        reviveCharacter()
                    },
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
                        if (crippledHead) playStimpackAudio() else playDamageAudio()
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
                        if (crippledLeftArm) playStimpackAudio() else playDamageAudio()
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
                        if (crippledTorso) playStimpackAudio() else playDamageAudio()
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
                        if (crippledRightArm) playStimpackAudio() else playDamageAudio()
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
                        if (crippledLeftLeg) playStimpackAudio() else playDamageAudio()
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
                        if (crippledRightLeg) playStimpackAudio() else playDamageAudio()
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
                    // Звук перемещения курсора — тот же тик, что у SPECIAL и Skills, но не через playSelectSound адаптера.
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
    /** Живая пересборка узла STATUS при смене woundPhase; no-op, если игрок сейчас не внутри списка Status. */
    private fun refreshStatusEncoderChildren() {
        menuNavigator.replaceChildrenOf("STATUS", statusChildrenNodes())
    }
    /** Пункт "В меню" — только в режимах с энкодером; общий источник и для onCreate(), и для refreshSidebarBackItems(). */
    private fun backSidebarItem(enabled: Boolean = true): SidebarMenuItem<String> =
        SidebarMenuItem(payload = SIDEBAR_BACK_PAYLOAD, label = getString(R.string.sidebar_menu_back), enabled = enabled)
    /** Тот же пункт как узел дерева; onHighlight и onBeforePop передаются отдельно — адаптер у каждого экрана свой. */
    private fun menuBackNode(mode: PipBoyMode, onHighlight: () -> Unit, onBeforePop: () -> Unit): List<MenuNode> =
        if (mode != PipBoyMode.PHONE) {
            listOf(
                MenuNode(
                    id = "MENU",
                    // Тик на перемещение курсора, звук подтверждения — на реальное нажатие ENCBTN.
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
    /** ValueEditor длинной записи: ENCBTN переключает ENC на прокрутку описания. */
    /** smoothScrollBy(), а не scrollBy(): первый клэмпит цель в границы контента. */
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
        // "В меню" дизейблится вместе со списком ранений; энкодер туда в это время вообще не попадает.
        val enabled = woundPhase == WoundPhase.NONE
        val items = statusMeta.map { meta ->
            SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes), enabled = enabled)
        }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem(enabled) else items
    }
    /** DATA/Files — фиксированный список, "В меню" последним пунктом. */
    private fun dataFilesSidebarItems(): List<SidebarMenuItem<String>> {
        val items = dataFilesMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.nameRes)) }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    /** Превью описания записи Files при движении курсора. */
    private fun showDataFilePreview(meta: DataFileMeta) {
        val files = bindingMain.incLayoutTabDataMisc
        files.tvDataMiscHolotapeText.setText(meta.descriptionRes)
        // Сброс прокрутки на новую запись — см. тот же приём в showPerkDescription() выше.
        files.scrollTabDataMiscText.scrollTo(0, 0)
    }
    /** Дети MISC: onHighlight обновляет описание молча, onActivate = {} — ENCBTN на записи никуда не проваливается. */
    private fun dataFilesChildrenNodes(): List<MenuNode> {
        return dataFilesMeta.mapIndexed { index, meta ->
            MenuNode(
                id = "FILE_$index",
                onHighlight = {
                    playTickAudio()
                    dataFilesAdapter.setSelectedPositionSilently(index)
                    showDataFilePreview(meta)
                },
                // ENCBTN на записи входит в прокрутку её описания, а не поднимает наверх.
                valueEditor = recordScrollValueEditor(bindingMain.incLayoutTabDataMisc.scrollTabDataMiscText),
            )
        } + menuBackNode(
            pipBoyMode,
            onHighlight = { dataFilesAdapter.setSelectedPositionSilently(dataFilesMeta.size) },
            onBeforePop = { dataFilesAdapter.flashPressAnimation(dataFilesMeta.size) },
        )
    }
    /** Позиция пункта часов по ключу — для syncClockEncoderPath(). */
    private fun clockRootIndex(key: String): Int = clockMeta.indexOfFirst { it.key == key }
    /** Безусловно ставит курсор энкодера по [path] от детей узла CLOCK. */
    /** Громкий setPath(), а не Silently: прицел обязан рисоваться там, где реально стоит курсор.
     * Инвариант для любого нового узла Clock — onHighlight не должен звать громкий selectPosition() своего адаптера. */
    private fun syncClockEncoderPath(path: List<Int>) =
        syncEncoderPath(itemsMenuRoot(), "CLOCK", path, loud = true)
    /** Тихий вариант нужен ровно треку в Мелодиях: его onHighlight запускает превью, конфликтующее с тумблером в onSelect. */
    private fun syncClockEncoderPathSilently(path: List<Int>) =
        syncEncoderPath(itemsMenuRoot(), "CLOCK", path, loud = false)
    /** ITEMS/Clock — фиксированный список, "В меню" последним пунктом. */
    private fun clockSidebarItems(): List<SidebarMenuItem<String>> {
        val items = clockMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes)) }
        return if (pipBoyMode != PipBoyMode.PHONE) items + backSidebarItem() else items
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
                        // Курсор стоит на самом ALARM — прицел внутреннего узла с прошлого визита должен погаснуть.
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
    /** Дети ALARM: часы и минуты через ValueEditor, Set, Back; панель коммитит первый ребёнок HOUR. */
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
    /** Дети TIMER ветвятся по timerState: в IDLE колёса и пресеты, иначе Pause/Resume и Reset. */
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
    /** Живая пересборка узла TIMER при смене timerState; no-op, если курсор сейчас не внутри TIMER. */
    private fun refreshClockTimerEncoderChildren() {
        menuNavigator.replaceChildrenOf("TIMER", timerChildrenNodes())
    }
    /** Дети STOPWATCH — статичный список, от stopwatchState зависит только текст кнопки. */
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
    /** Дети MELODY — трек за треком с автопрослушиванием; ENCBTN проваливается в [Select, Back]. */
    private fun melodyChildrenNodes(): List<MenuNode> {
        val trackNodes = ringtoneTracks.indices.map { i ->
            MenuNode(
                id = "TRACK_$i",
                onHighlight = {
                    // Панель коммитит только первый трек, и через setSelectedPositionSilently() — громкий вариант зациклился бы.
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
    /** Пересобирает три списка, когда режим стал известен после onCreate(); нужен setItems целиком, не точечная правка. */
    private fun refreshSidebarBackItems() {
        specialAdapter.setItems(specialSidebarItems(), resetSelection = false)
        skillsAdapter.setItems(skillsSidebarItems(), resetSelection = false)
        statusAdapter.setItems(statusSidebarItems(), resetSelection = false)
        dataFilesAdapter.setItems(dataFilesSidebarItems(), resetSelection = false)
        clockAdapter.setItems(clockSidebarItems(), resetSelection = false)
        // "Управление картой" тоже под гейтом по режиму.
        mapRootAdapter.setItems(
            mapRootSidebarItems(),
            resetSelection = false,
        )
        // journalListAdapter строится не в onCreate(), а при первом заходе на вкладку — отсюда проверка инициализации.
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
    /** Menu на Гейгере — обычная кнопка, не элемент адаптера, поэтому видимость обновляется отдельно. */
    /** Кнопки "назад"/"в меню" на экранах видны только в режимах с физическим энкодером. */
    private fun setEncoderOnlyVisible(vararg views: View) {
        val visibility = if (pipBoyMode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
        views.forEach { it.visibility = visibility }
    }
    private fun refreshGeigerMenuButtonVisibility() = setEncoderOnlyVisible(bindingMain.incLayoutTabItemsGeiger.btnGeigerMenu)
    /** Back на карточке записи Journal — та же схема, что у Menu на Гейгере. */
    private fun refreshJournalBackButtonVisibility() = setEncoderOnlyVisible(bindingMain.incLayoutTabItemsJournal.btnJournalEntryDetailBack)
    /** Back на карточке отметки — тот же гейт и приём. */
    private fun refreshMapMarkerDetailBackButtonVisibility() = setEncoderOnlyVisible(bindingMain.incLayoutTabItemsMap.btnMapMarkerDetailBack)
    /** Back-кнопки ITEMS/Clock — та же схема: обычные кнопки экрана, не элементы адаптера. */
    private fun refreshClockAlarmBackButtonVisibility() = setEncoderOnlyVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockAlarm.btnClockAlarmBack)
    private fun refreshClockTimerBackButtonsVisibility() {
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        setEncoderOnlyVisible(timer.btnClockTimerSetupBack, timer.btnClockTimerRunningBack)
    }
    private fun refreshClockStopwatchBackButtonVisibility() = setEncoderOnlyVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockStopwatch.btnClockStopwatchBack)
    private fun refreshClockMelodyBackButtonVisibility() = setEncoderOnlyVisible(bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody.btnClockMelodyBack)
    private fun setBottomButtons(vararg buttons: Button){
        listBottomButtons.clear()
        listBottomButtons.addAll(buttons)
    }
    /** Проверка будильника из того же 300мс-цикла, что и часы; совпадение сразу разоружает его. */
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
    /** Фокус энкодера на Stop оверлея срабатывания. */
    private fun setClockFiredStopFocused(focused: Boolean) {
        setFocusBracketsVisible(bindingMain.incLayoutClockFiredOverlay.viewClockFiredStopFocus, focused)
    }
    /** Закрытие оверлея — общее для тапа по Stop и для ENCBTN. */
    private fun dismissClockFiredOverlay() {
        stopClockFiredSound()
        setClockFiredStopFocused(false)
        bindingMain.incLayoutClockFiredOverlay.root.visibility = View.GONE
    }
    /** Звук срабатывания — выбранный трек; до первого выбора игроком это индекс 0, а не тишина. */
    private fun playClockFiredSound() {
        stopClockFiredSound()
        val trackIndex = sharedPreferences.getInt(selectedRingtone_SPKey, 0)
        val uri = Uri.parse("android.resource://$packageName/${ringtoneTracks[trackIndex].rawResId}")
        // Без AudioAttributes(USAGE_ALARM): тот канал живёт на отдельном системном регуляторе громкости.
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
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.tvClockTimerCountdown.text =
            String.format("%02d:%02d:%02d", h, m, s)
        if (woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD) {
            updateWoundCountdownText(remainingSeconds)
        }
        updateClockTimerLabel()
    }
    /** Подпись над отсчётом: у таймера может быть стадия ранения, для обычного запуска она пустая. */
    private fun clockTimerLabelText(): String = when (woundPhase) {
        WoundPhase.STUNNED -> getString(R.string.status_wound_stunned_label)
        WoundPhase.BLEED -> getString(R.string.status_wound_bleeding_label)
        WoundPhase.BANDAGE -> getString(R.string.status_wound_bandage_label)
        else -> ""
    }
    private fun updateClockTimerLabel() {
        bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.tvClockTimerLabel.text = clockTimerLabelText()
    }
    /** Общий таймер принадлежит либо запуску с экрана Таймера, либо текущей фазе ранения. */
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
    /** Панели экрана Таймера следуют timerState независимо от того, кто таймер запустил. */
    private fun syncClockTimerScreenVisibility() {
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        val running = timerState != TimerState.IDLE
        timer.layoutClockTimerRunning.visibility = if (running) View.VISIBLE else View.GONE
        timer.layoutClockTimerSetup.visibility = if (running) View.GONE else View.VISIBLE
        // Общая точка пересборки дерева для сброса, срабатывания, таймера ранения и восстановления.
        refreshClockTimerEncoderChildren()
    }
    /** Общий старт для кнопки [Старт] и голосовой команды; на нуле секунд — no-op. */
    private fun startPlainTimer(totalSeconds: Int) {
        if (totalSeconds <= 0) return
        val timer = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer
        timerTargetEpochMillis = System.currentTimeMillis() + totalSeconds * 1000L
        timerState = TimerState.RUNNING
        timer.btnClockTimerPauseResume.text = getString(R.string.clock_timer_pause)
        timer.layoutClockTimerSetup.visibility = View.GONE
        timer.layoutClockTimerRunning.visibility = View.VISIBLE
        updateClockTimerLabel() // woundPhase == NONE здесь всегда — очищает подпись от предыдущего таймера ранения
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
    /** Общий сброс: при активном таймере ранения равнозначен [Стоп] на STATUS, а не тихому обрыву. */
    private fun resetTimer() {
        if (woundPhase != WoundPhase.NONE) {
            stopWoundTimerEarly()
        } else {
            timerState = TimerState.IDLE
            syncClockTimerScreenVisibility()
        }
    }
    /** Мелодия звонка — методы класса, а не closure: кнопка регистрируется в onCreate() раньше секции экрана. */
    private fun updateMelodySelectedLabel() {
        val melody = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockMelody
        val index = sharedPreferences.getInt(selectedRingtone_SPKey, 0)
        melody.tvClockMelodySelectedName.apply {
            text = ringtoneTracks[index].displayName
            isSelected = false // застывшее обрезанное состояние, пока не нажали [Выбрать]
        }
    }
    /** Тело кнопки [Выбрать] — общее для тача и ENCBTN. */
    private fun commitMelodySelection() {
        sharedPreferences.edit().putInt(selectedRingtone_SPKey, melodyFocusedIndex).apply()
        updateMelodySelectedLabel()
        playMelodySelectedMarqueeOnce()
    }
    /** Один проход marquee; сброс isSelected нужен, иначе повторный выбор того же трека не прокрутится. */
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
    /** Переключает видимую панель справа; MELODY сюда не входит — это отдельный полноэкранный переход. */
    /** Контейнеры восстанавливаются здесь же: с энкодера можно уйти с Мелодии мимо её кнопки [Назад]. */
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
        // Рамка обязана совпадать с курсором энкодера, а тот при входе в MELODY всегда на первом треке,
        // а не на ранее подтверждённом.
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
    /** Методы класса, а не closure — нужны из stopwatchChildrenNodes(). */
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
    /** Строка 1 шапки — подсветка активного верхнего раздела закрашенным фоном. */
    private fun highlightTopLevelButton(menu: String){
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
        // Порядок должен совпадать с itemsMenuRoot() и setBottomButtons().
        val bottom = bindingMain.incLayoutTabItemsBottom
        return listOfNotNull(
            if (pipBoyMode != PipBoyMode.PHONE) Row2Item(bottom.btnItemsGeiger.text) { bottom.btnItemsGeiger.performClick() } else null,
            Row2Item(bottom.btnItemsMap.text) { bottom.btnItemsMap.performClick() },
            Row2Item(bottom.btnItemsJournal.text) { bottom.btnItemsJournal.performClick() },
            Row2Item(bottom.btnItemsClock.text) { bottom.btnItemsClock.performClick() },
        )
    }
    private fun dataRow2Items(): List<Row2Item> {
        // Порядок должен совпадать с dataMenuRoot() и setBottomButtons().
        val bottom = bindingMain.incLayoutTabDataBottom
        return listOfNotNull(
            Row2Item(bottom.btnDataMisc.text) { bottom.btnDataMisc.performClick() },
            if (pipBoyMode != PipBoyMode.PHONE) Row2Item(bottom.btnDataHolotapes.text) { bottom.btnDataHolotapes.performClick() } else null,
        )
    }
    /** Кнопка строки 1, под которой должен оказаться активный пункт строки 2. */
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
    /** Строит полосу строки 2 с нуля при смене верхнего раздела; внутри раздела работает renderRow2(). */
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
            // fontFamily из Row2ItemStyle разбирает только AppCompat-инфлейтер по XML — коду шрифт ставим явно.
            val tv = TextView(this, null, 0, R.style.Row2ItemStyle).apply {
                text = item.label
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.pipboy_mono)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { if (index > 0) marginStart = (8 * resources.displayMetrics.density).toInt() }
                setOnClickListener {
                    row2Active = index
                    // setRootCursor() здесь не дублировать: onSelect уже синхронизировал курсор и провалился на первого ребёнка.
                    item.onSelect()
                    renderRow2()
                }
            }
            GlobalTextScale.register(tv)
            strip.addView(tv)
            row2Views.add(tv)
        }
        renderRow2()
    }
    /** Перекрашивает уже построенные View строки 2 и выравнивает активный пункт под кнопкой строки 1. */
    /** Затенение по расстоянию от активного пункта; дальше второго пункт уходит в GONE, чтобы не мешать измерению. */
    private fun renderRow2(){
        // Окно показа асимметричное: слева один пункт, справа два — у первого раздела слева почти нет места.
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
    /** Подтягивает подсветку строки 2 к позиции курсора энкодера; rootCursor() не сбивается на вложенных уровнях. */
    private fun syncRow2ActiveFromNavigator(){
        val cursor = menuNavigator.rootCursor()
        if (cursor != row2Active && cursor in row2Views.indices){
            row2Active = cursor
            renderRow2()
        }
    }
    /** Считает translationX абсолютно, а не прибавлением: getLocationOnScreen() уже учитывает прошлый сдвиг. */
    /** Выравнивание по центру пункта, а не по левому краю — иначе первый раздел вылезал за край экрана. */
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

            // Зажимаем полосу так, чтобы крайний видимый пункт не вышел за границу, безопасную при translationX = 0.
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
    private fun menuOptionClickedBLE(menu: String){
        playConfirmAudio()
        highlightTopLevelButton(menu)
        setupMainContentBLE(menu)
        setupRow2(menu)
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        sendBLEText(menu)
        // Уход с ITEMS гасит GPS карты; возврат на Map перезапустит апдейты сам.
        if (menu != "ITEMS") {
            stopMapLocationUpdates()
        }
    }
    // ===== СИСТЕМА РАНЕНИЙ =====
    /** woundPhase и woundSeverity — единый источник истины для лица, кнопок статуса и исхода таймера. */
    private fun woundFaceDrawable(): Int = when (woundPhase) {
        WoundPhase.NONE -> R.drawable.man_face
        WoundPhase.BLEED, WoundPhase.BANDAGE -> if (woundSeverity == WoundSeverity.LIGHT) R.drawable.face_02 else R.drawable.face_03
        WoundPhase.STUNNED, WoundPhase.DEAD -> R.drawable.face_04
    }
    private fun applyWoundFace() {
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(woundFaceDrawable())
    }
    /** Курсор двигается независимо от затенения: enabled в адаптере — только визуальное, тап всё равно доедет до onSelect. */
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
        // Затенение следует за woundPhase и не блокирует тап; курсор не трогаем — его двигают явно.
        if (::statusAdapter.isInitialized) {
            // Через statusSidebarItems(), а не инлайн из statusMeta: пункт "В меню" дописывается только там.
            statusAdapter.setItems(statusSidebarItems(), resetSelection = false)
        }
        // Таймер ранения нельзя ставить на паузу; здесь клик блокируется по-настоящему, звук ошибки не нужен.
        val pauseResume = bindingMain.incLayoutTabItemsClock.incLayoutTabItemsClockTimer.btnClockTimerPauseResume
        pauseResume.isEnabled = woundPhase == WoundPhase.NONE || woundPhase == WoundPhase.DEAD
        pauseResume.alpha = if (pauseResume.isEnabled) 1.0f else 0.4f
    }
    private fun woundStageLabel(): String = when (woundPhase) {
        WoundPhase.BLEED -> getString(R.string.status_wound_bleeding_label)
        WoundPhase.BANDAGE -> getString(R.string.status_wound_bandage_label)
        else -> ""
    }
    /** Панель статуса справа от фигуры: статику ставит этот метод, текст с таймером — updateWoundCountdownText(). */
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
    /** Длительности здесь всегда ≤10 мин, поэтому часовой части нет. */
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
    /** Общая точка входа для всех переходов, запускающих таймер под новую цель; severity=null оставляет прежнюю. */
    private fun startWoundTimer(phase: WoundPhase, severity: WoundSeverity?, durationSeconds: Int) {
        woundPhase = phase
        if (severity != null) woundSeverity = severity
        // Курсор идёт за фазой и при автоматических переходах, а не только за тапом игрока.
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
    /** Вылечен — общий финал для перевязки и оглушения; CRIPPLED снимается со всех шести частей. */
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
    /** [Стоп] на STATUS и сброс таймера на экране Таймера при активном ранении — те же последствия. */
    private fun stopWoundTimerEarly() {
        when (woundPhase) {
            WoundPhase.BLEED -> startWoundTimer(WoundPhase.BANDAGE, woundSeverity, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
            WoundPhase.BANDAGE, WoundPhase.STUNNED -> healWoundsToHealthy()
            else -> {}
        }
    }
    /** Натуральное истечение — из fireTimer(), когда таймер принадлежит системе ранений. */
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
    /** Revive-жест — тап по фигуре, активен только в DEAD; полный сброс, вся система статусов на самоучёте игрока. */
    private fun reviveCharacter() {
        if (woundPhase != WoundPhase.DEAD) return
        woundPhase = WoundPhase.NONE
        applyWoundFace()
        updateWoundButtonsUI()
        refreshStatusEncoderChildren()
        updateWoundStatusLine()
        applyReviveVisuals()
    }
    /** В смерти все шесть частей рисуются сломанными, но подпись остаётся одна — DEAD на туловище. */
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
    /** Независимый тоггл CRIPPLED по одной конечности, не трогает фазу, лицо и остальные части. */
    // set*() — явная установка для идемпотентных голосовых команд, toggle*() — тонкие обёртки поверх них.
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
    /** Общий тач-обработчик висит и на шести частях тела, и на контейнере: у частей свой клик, он поглощает touch. */
    /** Короткий тап — revive в DEAD, иначе переданное действие; пятисекундное удержание — пасхалка. */
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
                        if (woundPhase == WoundPhase.DEAD) {
                            playStimpackAudio()
                            reviveCharacter()
                        } else {
                            onShortTap()
                        }
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
    /** [Skip] переносит целевой epoch в прошлое, а не дублирует логику срабатывания. */
    private fun skipWoundTimer() {
        if (timerState != TimerState.RUNNING) return
        timerTargetEpochMillis = System.currentTimeMillis()
        checkTimerFiring()
    }
    /** Тач по пункту зовёт громкую синхронизацию ради побочных эффектов onHighlight, но тик от неё лишний —
     * Silently не годится, он убрал бы и сами эффекты, поэтому глушим только звук на время вызова. */
    private var suppressTickAudio = false
    /** Одноразовый UI-звук: создаётся лениво, держится в пуле до конца проигрывания и освобождается сам. */
    private fun playSfx(rawResId: Int) {
        val player = MediaPlayer.create(applicationContext, rawResId) ?: return
        activeSfxPlayers.add(player)
        player.setOnCompletionListener {
            it.release()
            activeSfxPlayers.remove(it)
        }
        player.start()
    }
    private fun playTickAudio() {
        if (suppressTickAudio) return
        playSfx(R.raw.item_select)
    }
    /** Глушит тик от onHighlight на время [block]; свой единственный звук вызывающий играет сам. */
    private fun suppressTickAroundTouchSync(block: () -> Unit) {
        suppressTickAudio = true
        try {
            block()
        } finally {
            suppressTickAudio = false
        }
    }
    private fun playButtonAudio() = playSfx(R.raw.newtab)
    private fun playErrorAudio() = playSfx(R.raw.ui_error)
    private fun playConfirmAudio() = playSfx(R.raw.cnd_rad_eff)
    /** Звук тапа или ENCBTN по здоровой части тела; выбирается по состоянию ДО переключения. */
    private fun playDamageAudio() = playSfx(R.raw.damage_sfx)
    /** Звук лечения части тела или revive; ENCBTN-revive этот звук сознательно не получил. */
    private fun playStimpackAudio() = playSfx(R.raw.stimpack)

    // ===== БАТАРЕЯ =====
    private fun getBatteryPercent(): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = this.registerReceiver(null, ifilter)
        val level = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        return level
    }

    /** Вырез экрана бывает только с одной стороны — дублируем его отступ на противоположную,
     * чтобы декоративная рамка оставалась симметричной на любом телефоне игрока. */
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

    // ===== ЭКРАН ФИЛЬТРА =====
    private fun listEntries(frameLayout: FrameLayout, items: List<Perk>){

        frameLayout.removeAllViews()

        // Create a LinearLayout to hold the entries
        val linearLayout = LinearLayout(this)
        linearLayout.orientation = LinearLayout.VERTICAL

        // Iterate over the items and create CheckBox and TextView for each
        for (item in items) {
            val checkBox = CheckBox(this)
            // Чекбокс тонируется акцентом: Material-дефолт на тёмном фоне почти не виден.
            CompoundButtonCompat.setButtonTintList(checkBox, ColorStateList.valueOf(themeAccentColor()))
            val textView = TextView(this).apply {
                // Set the text for the TextView to the "name" value
                text = item.name
                // Set custom font to button
                typeface = TypefaceCache.getPipboyTypeface(context) // Set the loaded typeface
            }

            // Set the CheckBox checked state based on whether the item ID is in selectedItems
            val itemId = item.id
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

            GlobalTextScale.register(textView)

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

    private fun selectClearAllCheckBoxes(frameLayout: FrameLayout, items: List<Perk>, action: Boolean) {
        val linearLayout = frameLayout.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until linearLayout.childCount) {
            val entryLayout = linearLayout.getChildAt(i) as? LinearLayout
            entryLayout?.let { layout ->
                val checkBox = layout.getChildAt(0) as? CheckBox
                checkBox?.let {
                    if (action){
                        if (!it.isChecked) {
                            it.isChecked = true
                            val itemId = items[i].id
                            when(filteringMenu){
                                "PERKS" -> {
                                    selectedFilterSTATSPerks.add(itemId)
                                }
                            }
                        }
                    } else {
                        if (it.isChecked) {
                            it.isChecked = false
                            val itemId = items[i].id
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

    private fun filterList(items: List<Perk>, searchText: String) {
        val filteredItems = items.filter { item ->
            item.name.split(" ").any { word -> word.contains(searchText, ignoreCase = true) }
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
                setupStatsPerks(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
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
    /** Открывает экран фильтра Perks — точка входа кнопка-воронка на экране Perks. */
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
    /** Закрывает экран фильтра — общая часть для Save и Cancel. */
    private fun closeFilterScreen() {
        bindingMain.incLayoutFilterModification.root.visibility = View.GONE
        bindingMain.layoutStats.visibility = View.VISIBLE
        bindingMain.layoutItems.visibility = View.VISIBLE
        bindingMain.layoutData.visibility = View.VISIBLE
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
    }
    /** Локализация перка: Data.kt хранит только английский, перевод резолвится через perk_<id>_name/_desc. */
    /** Считается один раз: язык меняется только полным рестартом Activity. */
    private fun localizePerk(perk: Perk): Perk {
        val nameResId = resources.getIdentifier("perk_${perk.id}_name", "string", packageName)
        val descResId = resources.getIdentifier("perk_${perk.id}_desc", "string", packageName)
        return perk.copy(
            name = if (nameResId != 0) getString(nameResId) else perk.name,
            desc = if (descResId != 0) getString(descResId) else perk.desc,
        )
    }
    private val localizedPerks: List<Perk> by lazy {
        perks.map { perk -> localizePerk(perk) }
    }
    /** Превью описания и иконки Perks при движении курсора — общее для тапа и для наведения энкодером. */
    private fun showPerkDescription(perk: Perk) {
        bindingMain.incLayoutTabStatsPerks.tvPerksDescriptionsText.text = perk.desc
        bindingMain.incLayoutTabStatsPerks.imgPerksSelected.setImageResource(perk.iconRes)
        // Сброс прокрутки на новую запись, иначе новый текст покажется со смещения предыдущего.
        bindingMain.incLayoutTabStatsPerks.scrollviewPerksDescriptionsText.scrollTo(0, 0)
    }
    private fun setupStatsPerks(recyclerView: RecyclerView){
        val selectedSTATSPerksString = sharedPreferences.getString("selectedSTATSPerksArray", "1")
        val selectedSTATSPerksArray: Array<String> = selectedSTATSPerksString!!.split(",").toTypedArray()
        // Фильтруем по сырому списку, локализуем только отобранное: локализация каждого перка — два getIdentifier().
        val filteredPerksList = perks.filter { perk -> perk.id in selectedSTATSPerksArray }.map { localizePerk(it) }
        perksRealItemCount = filteredPerksList.size

        val realItems = filteredPerksList.map { perk -> SidebarMenuItem(payload = perk, label = perk.name) }
        perksAdapter = SidebarMenuAdapter(
            items = if (pipBoyMode != PipBoyMode.PHONE) realItems + perksBackSidebarItem() else realItems,
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // Звук даёт onSelect ниже — тик отсюда его дублировал.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: syncCursor() чинит его только внутри активного уровня.
                if (item.payload.id == SIDEBAR_BACK_PAYLOAD) {
                    playConfirmAudio()
                    syncStatsEncoderPath("PERKS", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    showPerkDescription(item.payload)
                    // Тап равносилен ENCBTN: курсор проваливается сразу в прокрутку описания, превью уже применено выше.
                    syncStatsEncoderPathSilently("PERKS", listOf(position))
                    menuNavigator.activateSelected()
                }
            },
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = perksAdapter
        filteredPerksList.firstOrNull()?.let { showPerkDescription(it) }
        // Список фильтруется, поэтому дерево пересобирается при каждом изменении, а не только при входе в STATS.
        menuNavigator.replaceChildrenOf("PERKS", perksChildrenNodes())
    }
    /** Пункт "В меню" для Perks — payload того же типа, что у реальных перков, с id-маркером. */
    private fun perksBackSidebarItem(): SidebarMenuItem<Perk> =
        SidebarMenuItem(payload = Perk(SIDEBAR_BACK_PAYLOAD, "", "", 0), label = getString(R.string.sidebar_menu_back))
    /** Дети PERKS пересчитываются заново на каждый вызов; onHighlight обновляет превью молча. */
    private fun perksChildrenNodes(): List<MenuNode> {
        return (0 until perksRealItemCount).map { index ->
            // ENCBTN на перке входит в прокрутку описания, а не поднимает наверх; повторный — обратно к списку.
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
    // ===== SharedPreferences =====
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




    /** Язык интерфейса независим от системного; при незаданном appLanguage контекст не трогаем вовсе. */
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

    /** Страница тьюториала: на последней [Далее] становится [Готово], а [Пропустить] прячется. */
    private fun showTutorialPage(index: Int) {
        tutorialPageIndex = index
        val page = bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage
        page.tvTutorialPage.text = getString(tutorialPageStringRes[index])
        page.tvTutorialPage.setTextColor(themeAccentColor())
        val isLastPage = index == tutorialPageStringRes.lastIndex
        val nextButton = bindingMain.incLayoutTabTutorialBase.btnNextpage
        val closeButton = bindingMain.incLayoutTabTutorialBase.btnTutorialClose
        nextButton.text = getString(if (isLastPage) R.string.wizard_done else R.string.wizard_next)
        // GONE, а не INVISIBLE: иначе [Готово] оставляет зазор под невидимой кнопкой.
        closeButton.visibility = if (isLastPage) View.GONE else View.VISIBLE
        if (!isLastPage) {
            equalizeButtonWidths(nextButton, closeButton)
        }
    }
    /** Открывает страницы тьюториала минуя Welcome — и после [Далее], и при повторном входе из Settings. */
    private fun openTutorialContent(startIndex: Int) {
        bindingMain.constraintlayoutTutorial.visibility = View.VISIBLE
        // Переключение вкладок прячет весь include тьюториала — возвращать нужно и его, не только внутренний layout.
        bindingMain.incLayoutTabTutorialBase.root.visibility = View.VISIBLE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage.root.visibility = View.VISIBLE
        // Чекбокс виден и на страницах контента: колонка кнопок центрируется, без третьего элемента они съедут.
        showTutorialPage(startIndex)
    }
    /** Закрывает тьюториал и возвращает разметку к исходному состоянию — следующий показ снова с Welcome. */
    private fun closeTutorial() {
        bindingMain.constraintlayoutTutorial.visibility = View.GONE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialPage.root.visibility = View.GONE
        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.VISIBLE
        bindingMain.incLayoutTabTutorialBase.btnTutorialClose.visibility = View.VISIBLE
        bindingMain.incLayoutTabTutorialBase.btnNextpage.text = getString(R.string.wizard_next)
        tutorialPageIndex = -1
    }

    // ===== MAIN =====
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlobalTextScale.reset()

        //Choose APP theme
        theme.applyStyle(currentUiTheme().styleRes, true)

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

        // Снимок чертёжных размеров шрифта снимается один раз после первого layout; программно созданный
        // текст регистрирует себя сам. Слушатель габаритов viewMain ловит все пути ресайза разом.
        viewMain.post {
            GlobalTextScale.registerTree(viewMain)
            viewMain.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newW = right - left
                val newH = bottom - top
                val oldW = oldRight - oldLeft
                val oldH = oldBottom - oldTop
                if (newW > 0 && newH > 0 && (newW != oldW || newH != oldH)) {
                    // post(), а не вызов на месте: setTextSize() просит requestLayout() у десятков View, и синхронный
                    // повторный layout внутри незавершённого прохода иногда оставлял Settings пустым.
                    v.post { applyGlobalTextScale(newW, newH) }
                }
            }
        }

        // Полный экран на старте: loadViewState() здесь подхватывал уменьшенную область прошлой сессии
        // ещё до того, как известен режим. resetToFullScreen() ещё и сохраняет сброс.
        resetToFullScreen()

        // Тема должна примениться ДО построения экрана выбора режима: тот строит адаптер с текущим
        // selected_button сразу, а не лениво при показе.
        applyAppTheme(currentUiTheme())

        // Экран выбора режима (roadmap, "Видение приложения") — первое, что видит игрок
        setupModeSelectScreen()
        setupPipBoy2000Wizard()
        registerDebugCommandReceiver()

        //Keep phone screen active
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Звуки создаются лениво в момент использования, а не все разом здесь при каждом старте.

        //BOTTOM BUTTON SETUP (DEFAULT STATUS)
        setBottomButtons(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks)


        // Пункт "В меню" требует отдельной ветки ДО поиска по specialMeta — иначе first{} упал бы с исключением.
        specialAdapter = SidebarMenuAdapter(
            items = specialSidebarItems(),
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // Звук даёт onSelect ниже; тик остаётся только там, где его играет реальное вращение энкодера.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: syncCursor() чинит его только внутри активного уровня.
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    playConfirmAudio()
                    syncStatsEncoderPath("SPECIAL", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    showSpecialPreview(specialMeta.first { it.key == item.payload })
                    // Тап равносилен ENCBTN: курсор проваливается сразу в редактирование значения.
                    syncStatsEncoderPathSilently("SPECIAL", listOf(position))
                    menuNavigator.activateSelected()
                }
            },
        )
        bindingMain.incLayoutTabStatsSpecial.scrollTabSpecial.layoutManager = LinearLayoutManager(this)
        bindingMain.incLayoutTabStatsSpecial.scrollTabSpecial.adapter = specialAdapter

        // Skills — тот же общий компонент вместо 13 скопированных XML-блоков и 13 обработчиков.
        skillsAdapter = SidebarMenuAdapter(
            items = skillsSidebarItems(),
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // {} — см. подробный комментарий у specialAdapter выше, тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора — тот же приём, что у SPECIAL.
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    playConfirmAudio()
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

        // Звук решает сам onSelect; enabled у всех трёх пунктов следует за woundPhase и обновляется
        // в updateWoundButtonsUI(), здесь только начальное состояние.
        statusAdapter = SidebarMenuAdapter(
            items = statusSidebarItems(),
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: он должен доехать сюда, даже если энкодер был в другой ветке.
                if (woundPhase != WoundPhase.NONE) {
                    // Пока активен таймер ранения, курсор должен быть на STOP — единственном реальном действии дерева.
                    playErrorAudio()
                    // Цель сама играет тик в onHighlight и задвоила бы звук ошибки выше.
                    suppressTickAroundTouchSync { syncStatsEncoderPath("STATUS", listOf(0)) }
                } else if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // confirm, а не тик — тач всегда даёт confirm.
                    playConfirmAudio()
                    syncStatsEncoderPath("STATUS", emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    val meta = statusMeta.first { it.key == item.payload }
                    // Звук нажатия, тот же что у +/-; листание даёт тик из onHighlight.
                    playConfirmAudio()
                    // Silently: startWoundTimer() сама перестроит детей и громко переставит курсор на новый STOP.
                    syncStatsEncoderPathSilently("STATUS", listOf(position))
                    meta.action()
                }
            },
        )
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.layoutManager = LinearLayoutManager(this)
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.adapter = statusAdapter
        // itemAnimator = null: DefaultItemAnimator по окончании кросс-фейда сбрасывает alpha в 1.0 и
        // затирает затенение недоступных пунктов, которое адаптер ставит тем же alpha.
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.itemAnimator = null

        // SCREEN SCAN ANIMATION
        val translateAnimation: Animation = TranslateAnimation(0, 0.0f, 0, 0.0f, 1, -4.0f, 1, 8.0f)
        translateAnimation.duration = 9000
        translateAnimation.repeatCount = -1
        bindingMain.imgScanline.animation = translateAnimation
        bindingMain.imgScanline.alpha = 0.2f

        // Здоров по умолчанию — ни одна из трёх кнопок статуса не выделена; первые пункты
        // боковых меню подсвечиваются самими адаптерами (initialSelectedPosition = 0).
        updateWoundButtonsUI()
        selectedSubMenu = bindingMain.incLayoutTabStatsBottom.btnStatsStatus
        findViewById<Button>(R.id.btn_stats_status).setBackgroundResource(selected_button)
        highlightTopLevelButton("STATS")

        // ===== ШАПКА, СТРОКА 1 =====
        // resetToRoot() — обязательная пара к смене верхнего уровня, иначе энкодер продолжит крутить
        // дерево прежнего раздела.
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
        // Шкала громкости чисто экранная: тач выставляет её абсолютно, без похода на ESP32 —
        // протокол не поддерживает set-громкости с телефона. Звук — один раз по отпусканию пальца.
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

        // Дата и время — отдельные поля общей нижней панели, а не один комбинированный формат.
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
                            // Подменяется только YEAR, перед тем как Calendar уйдёт в форматирование.
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


        // ===== ДИСКЛЕЙМЕР И ТЬЮТОРИАЛ =====
        if (sharedPreferences.getBoolean("ShowTutorial", true)) {
            bindingMain.constraintlayoutTutorial.visibility = View.VISIBLE
        } else {
            bindingMain.constraintlayoutTutorial.visibility = View.GONE
        }

        bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.tvTutorialWelcome.setTextColor(themeAccentColor())
        setWizardButtonState(bindingMain.incLayoutTabTutorialBase.btnNextpage, selected = false)
        setWizardButtonState(bindingMain.incLayoutTabTutorialBase.btnTutorialClose, selected = false)
        equalizeButtonWidths(
            bindingMain.incLayoutTabTutorialBase.btnNextpage,
            bindingMain.incLayoutTabTutorialBase.btnTutorialClose
        )

        // [Далее]: с Welcome открывает первую страницу, на контенте листает, на последней закрывает.
        bindingMain.incLayoutTabTutorialBase.btnNextpage.setOnClickListener {
            playButtonAudio()
            when {
                tutorialPageIndex == -1 -> openTutorialContent(0)
                tutorialPageIndex < tutorialPageStringRes.lastIndex -> showTutorialPage(tutorialPageIndex + 1)
                else -> closeTutorial()
            }
        }

        // [Пропустить] закрывает тьюториал с любой страницы, не долистывая до конца.
        bindingMain.incLayoutTabTutorialBase.btnTutorialClose.setOnClickListener {
            playButtonAudio()
            // Чекбокс инвертирован относительно такого же в Settings, хотя ключ ShowTutorial у них общий.
            showTutorialBool = !bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.isChecked()
            sharedPreferences.edit().putBoolean("ShowTutorial", showTutorialBool).apply()
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings.setChecked(showTutorialBool)
            closeTutorial()
        }

        // Повторный вход из Settings начинается сразу с контента, минуя дисклеймер.
        bindingMain.incLayoutSettingsGlobal.btnSettingsOpenTutorial.setOnClickListener {
            playButtonAudio()
            openTutorialContent(0)
            if (bindingMain.incLayoutSettingsGlobal.root.visibility == View.VISIBLE) {
                bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
        }


        // ===== ЭКРАН ФИЛЬТРА =====

        filterFrame = bindingMain.incLayoutFilterModification.filterModificationFrame
        CoroutineScope(Dispatchers.Main).launch {
            loadSelectedItems()
            // Any UI updates can be done here after the function completes
        }

        // Плейсхолдер красится акцентом с тем же затенением, что у соседних пунктов row2 — дефолтный hint слишком блёклый.
        bindingMain.incLayoutFilterModification.etFilterModificationValue.setHintTextColor(
            ColorUtils.setAlphaComponent(themeAccentColor(), (0.55f * 255).toInt())
        )

        // Пять кнопок экрана: нейтральная заливка из стиля, акцент темы — backgroundTintList кодом.
        val filterAccent = themeAccentColor()
        listOf(
            bindingMain.incLayoutFilterModification.btnFilterModificationCancel,
            bindingMain.incLayoutFilterModification.btnFilterModificationFilter,
            bindingMain.incLayoutFilterModification.btnFilterModificationSelect,
            bindingMain.incLayoutFilterModification.btnFilterModificationClear,
            bindingMain.incLayoutFilterModification.btnFilterModificationSave
        ).forEach { it.backgroundTintList = ColorStateList.valueOf(filterAccent) }

        bindingMain.incLayoutFilterModification.btnFilterModificationCancel.setOnClickListener{
            playButtonAudio()
            // Откатываем несохранённые правки чекбоксов: saveSelectedItems() не вызывается.
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

        // ===== STATS =====

        // ===== STATS: STATUS =====
        bindingMain.incLayoutTabStatsBottom.btnStatsStatus.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.GONE
            // Синхронизация энкодера с тачем по нижним кнопкам; индекс — позиция в statsMenuRoot().
            menuNavigator.setRootCursor(0)
            // Курсор сейчас на самом узле STATUS — рамка не должна показывать пункт 0 как уже выбранный.
            statusAdapter.clearSelection()
            setWoundStopButtonFocused(false)
            setDeadReviveFocused(false)
            setAllCrippledFocusesHidden()
            // Реальный тап равносилен ENCBTN; ENC-перебор строки 2 видит encoderTabHighlight и останавливается здесь.
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        // Клики по LIGHT/HEAVY/STUNNED живут внутри statusAdapter, отдельные обработчики не нужны.
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.btnTabStatusWoundStop.setOnClickListener {
            playButtonAudio()
            stopWoundTimerEarly()
            // Курсор следует за тачем независимо от того, где он был; тик глушим — цель играет его сама.
            suppressTickAroundTouchSync { syncStatsEncoderPath("STATUS", listOf(0)) }
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.btnTabStatusWoundSkip.setOnClickListener {
            skipWoundTimer()
        }

        // Тач-цели на всей фигуре: каждая часть тела и сам контейнер для пустых промежутков.
        val cndContentSetup = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        // Индексы 1-6 жёстко привязаны к порядку узлов statusChildrenNodes() при активном ранении.
        // Вне ранения этих узлов в дереве нет, и те же индексы указали бы на случайные пункты списка —
        // поэтому проверка внутри каждого колбэка, а не один раз снаружи: woundPhase меняется позже.
        fun hasBodyPartNodes() = woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD
        // Звук выбирается по состоянию ДО переключения: damage на здоровую часть, stimpack на уже сломанную.
        setupFigureTouchTarget(cndContentSetup.layoutTabStatusCndPipboy) {}
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyHead) {
            if (crippledHead) playStimpackAudio() else playDamageAudio()
            toggleCrippledHead()
            if (hasBodyPartNodes()) syncStatsEncoderPath("STATUS", listOf(1))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyTorso) {
            if (crippledTorso) playStimpackAudio() else playDamageAudio()
            toggleCrippledTorso()
            if (hasBodyPartNodes()) syncStatsEncoderPath("STATUS", listOf(3))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyLeftArm) {
            if (crippledLeftArm) playStimpackAudio() else playDamageAudio()
            toggleCrippledLeftArm()
            if (hasBodyPartNodes()) syncStatsEncoderPath("STATUS", listOf(2))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyRightArm) {
            if (crippledRightArm) playStimpackAudio() else playDamageAudio()
            toggleCrippledRightArm()
            if (hasBodyPartNodes()) syncStatsEncoderPath("STATUS", listOf(4))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyLeftLeg) {
            if (crippledLeftLeg) playStimpackAudio() else playDamageAudio()
            toggleCrippledLeftLeg()
            if (hasBodyPartNodes()) syncStatsEncoderPath("STATUS", listOf(5))
        }
        setupFigureTouchTarget(cndContentSetup.imgTabStatusCndPipboyRightLeg) {
            if (crippledRightLeg) playStimpackAudio() else playDamageAudio()
            toggleCrippledRightLeg()
            if (hasBodyPartNodes()) syncStatsEncoderPath("STATUS", listOf(6))
        }
        cndContentSetup.incLayoutTabStatsCndPopup.btnTabStatsCndPopupClose.setOnClickListener{
            cndContentSetup.incLayoutTabStatsCndPopup.root.visibility = View.GONE
            cndContentSetup.layoutTabStatusCndContent.visibility = View.VISIBLE
            enableDisableBottomButtons(true, listBottomButtons)
            enableDisableTopSwipe(true)
        }

        // Кнопки таймера ранения тонируются текущим акцентом темы.
        val woundAccentTint = ColorStateList.valueOf(themeAccentColor())
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

        // ===== STATS: SPECIAL =====
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

        // Клики по пунктам SPECIAL живут внутри specialAdapter.

        // Тап меняет значение на 1, удержание повторяет; у SPECIAL разгона нет — фиксированные 500мс.
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
        val specialValueButtonsAccentTint = ColorStateList.valueOf(themeAccentColor())
        bindingMain.incLayoutTabStatsSpecial.btnSpecialIncrease.backgroundTintList = specialValueButtonsAccentTint
        bindingMain.incLayoutTabStatsSpecial.btnSpecialDecrease.backgroundTintList = specialValueButtonsAccentTint
        bindingMain.incLayoutTabStatsSpecial.viewSpecialValueFocus.backgroundTintList = specialValueButtonsAccentTint



        // ===== STATS: SKILLS =====
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

        // Клики по пунктам Skills живут внутри skillsAdapter.

        // У Skills удержание с разгоном 500мс -> 50мс: диапазон 10-100 без него листать неудобно.
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
        val skillValueButtonsAccentTint = ColorStateList.valueOf(themeAccentColor())
        bindingMain.incLayoutTabStatsSkills.btnSkillIncrease.backgroundTintList = skillValueButtonsAccentTint
        bindingMain.incLayoutTabStatsSkills.btnSkillDecrease.backgroundTintList = skillValueButtonsAccentTint
        bindingMain.incLayoutTabStatsSkills.viewSkillValueFocus.backgroundTintList = skillValueButtonsAccentTint


        // ===== STATS: PERKS =====
        // Строим сразу, а не лениво по клику: к первой сборке statsMenuRoot() список ещё пуст, и узел
        // PERKS навсегда заморозил бы единственный пункт "В меню" — children узла обычный val.
        setupStatsPerks(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
        bindingMain.incLayoutTabStatsBottom.btnStatsPerks.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsPerks, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.VISIBLE
            menuNavigator.setRootCursor(3)
            // Свежий адаптер стартует с подсвеченным пунктом 0 — гасим рамку молча до реального провала курсора.
            setupStatsPerks(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
            perksAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        bindingMain.incLayoutTabStatsPerks.btnPerksFilter.setOnClickListener {
            openPerksFilter()
        }


        // Кнопки Settings: нейтральная заливка из стиля, акцент темы — backgroundTintList кодом.
        val settingsAccent = themeAccentColor()
        listOf(
            bindingMain.incLayoutSettingsGlobal.btnSettingsCancel,
            bindingMain.incLayoutSettingsGlobal.btnSettingsSave,
            bindingMain.incLayoutSettingsGlobal.btnSettingsChangeMode,
            bindingMain.incLayoutSettingsGlobal.btnSettingsOpenTutorial,
            bindingMain.incLayoutSettingsGlobal.btnMapBundleImport,
            bindingMain.incLayoutSettingsGlobal.btnVoiceModelImport,
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnBluetoothRescan
        ).forEach { it.backgroundTintList = ColorStateList.valueOf(settingsAccent) }
        // Чекбоксы Settings: раньше тонировался только лейбл, рамка оставалась Material-дефолтом.
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


        // ===== ITEMS =====

        // ===== ITEMS: КАРТА =====
        bindingMain.incLayoutTabItemsBottom.btnItemsMap.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsMap, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            // Синхронизация энкодера с тачем по нижним кнопкам; индекс не константа — MAP сдвигается относительно GEIGER.
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
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { _, item ->
                // Безусловная синхронизация: курсор должен перепрыгнуть сюда даже из другой ветки дерева.
                playConfirmAudio()
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // Молча: onHighlight узла MAP заново открыл бы экран и стёр текущее состояние карты.
                    syncMapEncoderPathSilently(emptyList())
                    syncRow2ActiveFromNavigator()
                } else {
                    // "+ 0" — тап равносилен ENCBTN: у всех четырёх пунктов есть дети, курсор садится на первого.
                    suppressTickAroundTouchSync { syncMapEncoderPath(listOf(mapRootIndex(item.payload), 0)) }
                    mapRootMeta.first { it.key == item.payload }.action()
                }
            },
        )
        mapMenu.recyclerMapMenuRoot.layoutManager = LinearLayoutManager(this)
        mapMenu.recyclerMapMenuRoot.adapter = mapRootAdapter
        mapRouteSubmenuAdapter = SidebarMenuAdapter(
            items = mapRouteSubmenuMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = getString(meta.labelRes)) },
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                // BACK — особый случай: путь останавливается на родителе, там курсор окажется после popLevel().
                val path = if (item.payload == "BACK") {
                    listOf(mapRootIndex("ROUTE"))
                } else {
                    listOf(mapRootIndex("ROUTE"), position, 0)
                }
                playConfirmAudio()
                suppressTickAroundTouchSync { syncMapEncoderPath(path) }
                mapRouteSubmenuMeta.first { it.key == item.payload }.action()
            },
        )
        mapMenu.recyclerMapMenuRouteSubmenu.layoutManager = LinearLayoutManager(this)
        mapMenu.recyclerMapMenuRouteSubmenu.adapter = mapRouteSubmenuAdapter
        // Тик глушим везде ниже: цель каждой синхронизации играет его сама в своём onHighlight.
        mapMenu.btnMapMarkerDetailEdit.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            // "+ 0, 0" — EDIT теперь узел с детьми, тап проваливается сразу в первого, MIC.
            if (markerIndex != -1) suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 0 + 0) }
            playButtonAudio()
            showMarkerNamePopupForEdit(marker)
        }
        mapMenu.btnMapMarkerDetailRoute.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 1) }
            playButtonAudio()
            // Карточка отметки всегда достигается через "Список меток".
            routeTo(marker.lat, marker.lon, listOf(mapRootIndex("MARKER_LIST")))
            hideMarkerDetail()
        }
        mapMenu.btnMapMarkerDetailDelete.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 2) }
            playButtonAudio()
            performMapMarkerDelete(marker)
        }
        // Back только поднимает курсор в список отметок, самой отметки не касается.
        mapMenu.btnMapMarkerDetailBack.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 3) }
            playButtonAudio()
            setMapMarkerDetailBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshMapMarkerDetailBackButtonVisibility()
        // Zoom и Center видны всегда и не входят в общую группу — поэтому показываем оверлей здесь явно,
        // иначе курсор переключался, а уголки, крестик и "←" оставались невидимы.
        mapMenu.btnMapZoomIn.setOnClickListener {
            playConfirmAudio()
            setMapControlOverlayVisible(true)
            suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 3) }
            zoomMapBy(MAP_ZOOM_STEP_FACTOR)
        }
        mapMenu.btnMapZoomOut.setOnClickListener {
            playConfirmAudio()
            setMapControlOverlayVisible(true)
            suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 3) }
            zoomMapBy(1f / MAP_ZOOM_STEP_FACTOR)
        }
        mapMenu.btnMapCenter.setOnClickListener {
            playConfirmAudio()
            setMapControlOverlayVisible(true)
            suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 4) }
            recenterMapOnUser()
        }
        // Уголки, прицел и "←" доступны и тачу — кнопки реально видны на экране, не только энкодеру.
        val mapPanStepPx = resources.displayMetrics.density * MAP_PAN_STEP_DP
        mapMenu.btnMapPanUp.setOnClickListener { playConfirmAudio(); suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 1) }; panMapBy(0f, mapPanStepPx) }
        mapMenu.btnMapPanDown.setOnClickListener { playConfirmAudio(); suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 1) }; panMapBy(0f, -mapPanStepPx) }
        mapMenu.btnMapPanLeft.setOnClickListener { playConfirmAudio(); suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 2) }; panMapBy(mapPanStepPx, 0f) }
        mapMenu.btnMapPanRight.setOnClickListener { playConfirmAudio(); suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 2) }; panMapBy(-mapPanStepPx, 0f) }
        mapMenu.viewMapCrosshair.setOnClickListener {
            // Полный путь до того, что окажется на экране: тап по крестику равносилен ENCBTN и проваливается в детей.
            val (lat, lon) = mapCrosshairLatLon() ?: return@setOnClickListener
            playConfirmAudio()
            when (mapControlMode) {
                MapControlMode.ROUTE_TO_POINT -> {
                    suppressTickAroundTouchSync { syncMapEncoderPath(mapControlModeRootPath() + 0) }
                    routeTo(lat, lon, listOf(mapRootIndex("ROUTE")))
                }
                MapControlMode.PLACE_MARKER -> {
                    suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerPopupParentPath() + 0) }
                    showMarkerNamePopupForNewMarker(lat, lon)
                }
                MapControlMode.ROOT -> {
                    suppressTickAroundTouchSync { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0)) }
                    showMapTapChoice(lat, lon)
                }
            }
        }
        mapMenu.btnMapControlBack.setOnClickListener {
            // Для ROUTE_TO_POINT боковое меню нужно явно вернуть в ROOT — его onHighlight этого не делает сам.
            val wasRouteToPoint = mapControlMode == MapControlMode.ROUTE_TO_POINT
            playButtonAudio()
            suppressTickAroundTouchSync { syncMapEncoderPath(mapSidebarRootPathForMode()) }
            setMapControlOverlayVisible(false)
            if (wasRouteToPoint) showMapMenuState(MapMenuState.ROOT)
        }
        mapMenu.btnMapTapChoiceRoute.setOnClickListener {
            suppressTickAroundTouchSync { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0)) }
            val (lat, lon) = pendingTapChoiceLatLon ?: return@setOnClickListener
            playButtonAudio()
            hideMapTapChoice()
            // Панель [Route]/[Marker]/[Cancel] бывает только в режиме ROOT.
            routeTo(lat, lon, listOf(mapRootIndex("MAP_CONTROLS")))
        }
        mapMenu.btnMapTapChoiceMarker.setOnClickListener {
            // Координату читаем и звук играем ДО синхронизации: onHighlight цели обнуляет pendingTapChoiceLatLon.
            val (lat, lon) = pendingTapChoiceLatLon ?: return@setOnClickListener
            playButtonAudio()
            // "+ 0" — Marker проваливается в попап (Cancel/Save), не остаётся на себе самой.
            suppressTickAroundTouchSync { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 1, 0)) }
            hideMapTapChoice()
            showMarkerNamePopupForNewMarker(lat, lon)
        }
        mapMenu.btnMapTapChoiceCancel.setOnClickListener {
            playButtonAudio()
            suppressTickAroundTouchSync { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 2)) }
            hideMapTapChoice()
        }
        mapMenu.btnMapRouteStart.setOnClickListener {
            // syncPushedCursor() вернёт false, если энкодер сейчас не на этой запушенной панели.
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
        // Индексы MIC(0)/CANCEL(1)/SAVE(2) — микрофон стал первым узлом, Cancel и Save сдвинулись.
        markerNamePopup.btnMarkerNamePopupMic.setOnClickListener {
            // Синхронизируем только курсор и прицел: громкий путь вызвал бы onHighlight узла MIC,
            // а тот сбрасывает поле ввода и стёр бы надиктованное.
            syncMapEncoderPathSilently(mapMarkerPopupParentPath() + 0)
            setAllMapMarkerPopupFocusesHidden()
            setMapMarkerPopupMicFocused(true)
            mapMarkerDictation.handleMicTap()
        }
        markerNamePopup.btnMarkerNamePopupCancel.setOnClickListener {
            playButtonAudio()
            suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerPopupParentPath() + 1) }
            performMarkerNamePopupCancel()
        }
        markerNamePopup.btnMarkerNamePopupSave.setOnClickListener {
            playButtonAudio()
            suppressTickAroundTouchSync { syncMapEncoderPath(mapMarkerPopupParentPath() + 2) }
            performMarkerNamePopupSave()
        }

        // ===== ITEMS: ЧАСЫ =====
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

        // ===== ITEMS: ЧАСЫ — список фичей слева =====
        val clock = bindingMain.incLayoutTabItemsClock
        clockAdapter = SidebarMenuAdapter(
            items = clockSidebarItems(),
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация плюс один звук на весь тап — тик из onHighlight цели глушится.
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // Путь до самого узла CLOCK — тот же смысл, что popLevel(), но не зависит от прежней позиции курсора.
                    playConfirmAudio()
                    syncClockEncoderPath(emptyList())
                    syncRow2ActiveFromNavigator()
                } else if (item.payload == "MELODY") {
                    // "+ 0" — тап равносилен ENCBTN на MELODY: курсор садится на первый трек, и его onHighlight сам откроет экран.
                    playConfirmAudio()
                    suppressTickAroundTouchSync { syncClockEncoderPath(listOf(position, 0)) }
                } else if (item.payload == "TIME") {
                    // Единственный лист без детей — проваливаться некуда, курсор остаётся на пункте.
                    playConfirmAudio()
                    suppressTickAroundTouchSync { syncClockEncoderPath(listOf(position)) }
                    showClockContentPanel(item.payload)
                } else {
                    // ALARM/TIMER/STOPWATCH — тоже "+ 0": ожидание игрока попасть сразу на первый орган управления.
                    playConfirmAudio()
                    suppressTickAroundTouchSync { syncClockEncoderPath(listOf(position, 0)) }
                    showClockContentPanel(item.payload)
                }
            },
        )
        clock.incLayoutTabItemsClockButtons.recyclerTabItemsClockButtons.layoutManager = LinearLayoutManager(this)
        clock.incLayoutTabItemsClockButtons.recyclerTabItemsClockButtons.adapter = clockAdapter

        // ===== ITEMS: ЧАСЫ — БУДИЛЬНИК =====
        val clockAccentTint = ColorStateList.valueOf(themeAccentColor())
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
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 2)) }
            playButtonAudio()
            toggleAlarmArmed()
        }
        alarm.btnClockAlarmBack.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("ALARM"), 3)) }
            playConfirmAudio()
            setClockAlarmBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshClockAlarmBackButtonVisibility()

        // ===== ITEMS: ЧАСЫ — ТАЙМЕР =====
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
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 3)) }
            playButtonAudio()
            addTimerPresetMinutes(5)
        }
        timer.btnClockTimerPreset10.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 4)) }
            playButtonAudio()
            addTimerPresetMinutes(10)
        }
        timer.btnClockTimerStart.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 5)) }
            playButtonAudio()
            startPlainTimer(timerHours * 3600 + timerMinutes * 60 + timerSeconds)
        }
        timer.btnClockTimerSetupBack.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 6)) }
            playConfirmAudio()
            setClockTimerSetupBackFocused(false)
            menuNavigator.popLevel()
        }
        timer.btnClockTimerPauseResume.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 0)) }
            playButtonAudio()
            pauseResumeTimer()
        }
        timer.btnClockTimerReset.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 1)) }
            playButtonAudio()
            resetTimer()
        }
        timer.btnClockTimerRunningBack.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("TIMER"), 2)) }
            playConfirmAudio()
            setClockTimerRunningBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshClockTimerBackButtonsVisibility()

        // ===== ITEMS: ЧАСЫ — СЕКУНДОМЕР =====
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
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 0)) }
            playButtonAudio()
            toggleStopwatchStartPause()
        }
        stopwatch.btnClockStopwatchReset.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 1)) }
            playButtonAudio()
            resetStopwatch()
        }
        stopwatch.btnClockStopwatchBack.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("STOPWATCH"), 2)) }
            playConfirmAudio()
            setClockStopwatchBackFocused(false)
            menuNavigator.popLevel()
        }
        refreshClockStopwatchBackButtonVisibility()

        // ===== ITEMS: ЧАСЫ — МЕЛОДИЯ ЗВОНКА =====
        val melody = clock.incLayoutTabItemsClockMelody
        melody.btnClockMelodySelect.backgroundTintList = clockAccentTint
        melody.btnClockMelodyBack.backgroundTintList = clockAccentTint
        // Была текстовая кнопка, заменена на иконку (roadmap, этап 29).
        melody.btnClockMelodyBack.imageTintList = null
        melody.viewClockMelodySelectFocus.backgroundTintList = clockAccentTint
        melody.viewClockMelodyBackFocus.backgroundTintList = clockAccentTint
        // applyTextColor() эту LineVisualizer не красит — без явного setColor() линия сливается с фоном.
        melody.melodyWave.setColor(themeAccentColor())
        melodyFocusedIndex = sharedPreferences.getInt(selectedRingtone_SPKey, 0)

        // [Назад] — обычный последний пункт того же списка, не отдельная кнопка.
        val melodyItems: List<SidebarMenuItem<Int?>> = ringtoneTracks.indices.map { index ->
            SidebarMenuItem<Int?>(payload = index, label = ringtoneTracks[index].displayName)
        } + SidebarMenuItem(payload = null, label = getString(R.string.wizard_back))
        melodyAdapter = SidebarMenuAdapter(
            items = melodyItems,
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            initialSelectedPosition = melodyFocusedIndex,
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                val index = item.payload
                playConfirmAudio()
                if (index == null) {
                    // Назад — безусловно на сам узел: тач мог случиться из любой ветки.
                    suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("MELODY"), position)) }
                    menuNavigator.popLevel()
                    closeClockMelodyScreen()
                } else {
                    // Глубину читаем ДО обновления melodyFocusedIndex, иначе искали бы уже новый трек вместо прежнего.
                    val childDepth = menuNavigator.cursorIfParent("TRACK_$melodyFocusedIndex")
                    if (childDepth != null) {
                        // Цель — Select/Back нового трека: onHighlight красит прицел, глушим только звук.
                        suppressTickAroundTouchSync {
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
        melody.recyclerClockMelodyTracks.layoutManager = LinearLayoutManager(this)
        melody.recyclerClockMelodyTracks.adapter = melodyAdapter

        melody.btnClockMelodySelect.setOnClickListener {
            // Цель сама играет тик в onHighlight и задвоила бы звук кнопки.
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("MELODY"), melodyFocusedIndex, 0)) }
            playButtonAudio()
            commitMelodySelection()
        }
        melody.btnClockMelodyBack.setOnClickListener {
            suppressTickAroundTouchSync { syncClockEncoderPath(listOf(clockRootIndex("MELODY"), melodyFocusedIndex, 1)) }
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

        // ===== ITEMS: ЖУРНАЛ =====
        bindingMain.incLayoutTabItemsBottom.btnItemsJournal.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsJournal, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            stopMapLocationUpdates()
            menuNavigator.setRootCursor(itemsRootIndexFor("JOURNAL"))
            // Свежий адаптер стартует с подсвеченным пунктом 0 — гасим рамку молча до реального провала курсора.
            openJournalScreen()
            journalListAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        val journalScreen = bindingMain.incLayoutTabItemsJournal
        val journalAccentColor = ColorStateList.valueOf(themeAccentColor())
        journalScreen.tvJournalHint.setTextColor(themeAccentColor())
        journalScreen.tvJournalEntryDetailDate.setTextColor(themeAccentColor())
        journalScreen.btnJournalEntryDetailEdit.backgroundTintList = journalAccentColor
        journalScreen.btnJournalEntryDetailDelete.backgroundTintList = journalAccentColor
        journalScreen.btnJournalEntryDetailBack.backgroundTintList = journalAccentColor
        // Были текстовыми кнопками, стали иконками — тот же сброс imageTintList.
        journalScreen.btnJournalEntryDetailEdit.imageTintList = null
        journalScreen.btnJournalEntryDetailDelete.imageTintList = null
        journalScreen.btnJournalEntryDetailBack.imageTintList = null
        // Прицелы-уголки тонируются по той же схеме, что Reset и Menu на Гейгере.
        journalScreen.viewJournalEntryDetailEditFocus.backgroundTintList = journalAccentColor
        journalScreen.viewJournalEntryDetailDeleteFocus.backgroundTintList = journalAccentColor
        journalScreen.viewJournalEntryDetailBackFocus.backgroundTintList = journalAccentColor
        journalScreen.btnJournalEntryDetailEdit.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            // "+ 0, 0" — тап равносилен ENCBTN на EDIT: курсор садится на MIC, чей onHighlight откроет редактор.
            playButtonAudio()
            suppressTickAroundTouchSync { syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 0, 0)) }
        }
        journalScreen.btnJournalEntryDetailDelete.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            suppressTickAroundTouchSync { syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 1)) }
            playButtonAudio()
            performJournalEntryDelete(entry)
        }
        // Back только поднимает курсор в боковое меню; видна лишь в режимах с физическим энкодером.
        journalScreen.btnJournalEntryDetailBack.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            suppressTickAroundTouchSync { syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 2)) }
            playConfirmAudio()
            menuNavigator.popLevel()
        }
        refreshJournalBackButtonVisibility()
        val journalEntryPopup = journalScreen.incLayoutTabItemsJournalEntryPopup
        journalEntryPopup.btnJournalEntryMic.backgroundTintList = journalAccentColor
        // Без сброса глиф иконки красится темой в цвет фона кнопки и сливается с ним.
        ImageViewCompat.setImageTintList(journalEntryPopup.btnJournalEntryMic, null)
        journalEntryPopup.viewJournalEntryMicFocus.backgroundTintList = journalAccentColor
        journalEntryPopup.viewJournalEntryPopupCancelFocus.backgroundTintList = journalAccentColor
        journalEntryPopup.viewJournalEntryPopupSaveFocus.backgroundTintList = journalAccentColor
        // Тап 1 старт, тап 2 стоп; тело общее для тача и ENCBTN.
        journalEntryPopup.btnJournalEntryMic.setOnClickListener {
            // Синхронизируем только курсор и прицел: громкий путь стёр бы уже набранный или надиктованный текст.
            syncJournalEncoderPathSilently(journalEditorPathPrefix() + 0)
            setAllJournalEntryEditorFocusesHidden()
            setJournalEntryEditorMicFocused(true)
            journalDictation.handleMicTap()
        }
        journalEntryPopup.btnJournalEntryPopupCancel.backgroundTintList = journalAccentColor
        journalEntryPopup.btnJournalEntryPopupSave.backgroundTintList = journalAccentColor
        journalEntryPopup.btnJournalEntryPopupCancel.setOnClickListener {
            suppressTickAroundTouchSync { syncJournalEncoderPath(journalEditorPathPrefix() + 1) }
            playConfirmAudio()
            performJournalEntryCancel()
        }
        journalEntryPopup.btnJournalEntryPopupSave.setOnClickListener {
            suppressTickAroundTouchSync { syncJournalEncoderPath(journalEditorPathPrefix() + 2) }
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

        // ===== ITEMS: ГЕЙГЕР =====
        updateGeigerDoseDisplay(sharedPreferences.getInt(geigerDose_SPKey, 0))
        val geigerButtonAccent = ColorStateList.valueOf(themeAccentColor())
        bindingMain.incLayoutTabItemsGeiger.btnGeigerReset.backgroundTintList = geigerButtonAccent
        // В drawable прицела белая заглушка — реальный акцент темы ставится только кодом.
        bindingMain.incLayoutTabItemsGeiger.viewGeigerResetFocus.backgroundTintList = geigerButtonAccent
        bindingMain.incLayoutTabItemsGeiger.viewGeigerMenuFocus.backgroundTintList = geigerButtonAccent
        bindingMain.incLayoutTabItemsGeiger.btnGeigerReset.setOnClickListener {
            // Синхронизация курсора с тачем; Reset и Menu — обычные кнопки, не элементы списка.
            menuNavigator.syncCursor("GEIGER", 0)
            playButtonAudio()
            resetGeigerDose()
        }
        // Menu видна в любом режиме с физическим энкодером; видимость обновляется и при смене режима в рантайме.
        bindingMain.incLayoutTabItemsGeiger.btnGeigerMenu.backgroundTintList = geigerButtonAccent
        bindingMain.incLayoutTabItemsGeiger.btnGeigerMenu.setOnClickListener {
            menuNavigator.syncCursor("GEIGER", 1)
            playButtonAudio()
            setGeigerMenuFocused(false)
            menuNavigator.popLevel()
            syncRow2ActiveFromNavigator()
        }
        refreshGeigerMenuButtonVisibility()

        // ===== DATA =====

        // ===== DATA: MISC =====
        bindingMain.incLayoutTabDataBottom.btnDataMisc.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataMisc, listBottomButtons)
            bindingMain.incLayoutTabDataMisc.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataHolotapes.root.visibility = View.GONE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
            // MISC — всегда индекс 0 в dataMenuRoot(): состав здесь не зависит от режима.
            menuNavigator.setRootCursor(0)
            dataFilesAdapter.clearSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        // ===== DATA: ГОЛОДИСКИ =====
        bindingMain.incLayoutTabDataBottom.btnDataHolotapes.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataHolotapes, listBottomButtons)
            bindingMain.incLayoutTabDataMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabDataHolotapes.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
        }

        // DATA/Files — тот же общий компонент вместо двух скопированных строк разметки.
        val files = bindingMain.incLayoutTabDataMisc
        dataFilesAdapter = SidebarMenuAdapter(
            items = dataFilesSidebarItems(),
            selectedBackgroundRes = selected_button,
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: syncCursor() чинит его только внутри активного уровня.
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

        // Боковое меню Settings: пункт — сама панель-раздел, отдельный enum разделов не нужен.
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
            scrollbarThumbRes = currentUiTheme().scrollbarRes,
            // confirm, а не тик: этот сайдбар вообще не завязан на дерево энкодера.
            playSelectSound = { playConfirmAudio() },
            onSelect = { _, item ->
                settingsSectionPanels.forEach { it.visibility = if (it === item.payload) View.VISIBLE else View.GONE }
                // Скан идёт, только пока виден раздел Bluetooth; уход на другой раздел останавливает его безусловно.
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

        // Save пишет все поля и делает recreate(); saveValues() синхронный — apply() обновляет память сразу.
        saveButtonSettings.setOnClickListener {
            playButtonAudio()
            stopPairingScan()
            saveValues(editSettings1.text.toString(), UIColour_Selector, dateFormat_Selector, editSettings6.isChecked(), editSettings7.isChecked(), editSettingsYear.text.toString().toInt(), editSettingsRegion.text.toString(), languageSelector, editSettings8.isChecked())
            sendBLEText("STATS")
            recreate()
        }
        // Cancel — выход без сохранения; несохранённые правки теряются, при следующем открытии поля перечитаются.
        cancelButtonSettings.setOnClickListener {
            playButtonAudio()
            stopPairingScan()
            if (!isResizing) {
                bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
        }

            // Имя и регион выставляются один раз при старте: сохранение настроек всегда идёт через recreate().
            bindingMain.incLayoutHeaderBottomCommon.tvBottomNameValue.text = sharedPreferences.getString(playerName_SPKey, "Player")
            bindingMain.incLayoutHeaderBottomCommon.tvBottomRegionValue.text = sharedPreferences.getString(playerRegion_SPKey, "Richmond")
            editSettings1.setText(sharedPreferences.getString(playerName_SPKey, "Player"))
            editSettingsRegion.setText(sharedPreferences.getString(playerRegion_SPKey, "Richmond"))
            editSettingsYear.setText((sharedPreferences.getInt(gameYear_SPKey, 2276)).toString())
            // Чекбокс на Welcome инвертирован относительно такого же в Settings при общем ключе.
            bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.setChecked(!sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings6.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings7.setChecked(sharedPreferences.getBoolean("TrueFullscreen", true))
            editSettings8.setChecked(sharedPreferences.getBoolean("AmbientSoundEnabled", true))
            refreshModeSettingsLabel()

            bindingMain.incLayoutSettingsGlobal.rgSettingsDateformat.check(bindingMain.incLayoutSettingsGlobal.rgSettingsDateformat.getChildAt(sharedPreferences.getInt(dateFormat_SPKey, 0)).id)
            bindingMain.incLayoutSettingsGlobal.rgSettingsUiColour.check(bindingMain.incLayoutSettingsGlobal.rgSettingsUiColour.getChildAt(sharedPreferences.getInt(playerUIColour_SPKey, 0)).id)
            // При незаданном языке отмечаем тот пункт, который и так действует через системную локаль.
            val effectiveLanguageIndex = sharedPreferences.getInt(appLanguage_SPKey, -1).let {
                if (it in 0..1) it else if (Locale.getDefault().language == "ru") 0 else 1
            }
            bindingMain.incLayoutSettingsGlobal.rgSettingsLanguage.check(bindingMain.incLayoutSettingsGlobal.rgSettingsLanguage.getChildAt(effectiveLanguageIndex).id)


        // ===== НАСТРОЙКИ: КАРТА =====

        refreshMapBundleStatus()
        bindingMain.incLayoutSettingsGlobal.btnMapBundleImport.setOnClickListener {
            openMapBundleTreeLauncher.launch(null)
        }

        // ===== НАСТРОЙКИ: ГОЛОСОВЫЕ КОМАНДЫ =====

        refreshVoiceModelStatus()
        bindingMain.incLayoutSettingsGlobal.btnVoiceModelImport.setOnClickListener {
            openVoiceModelZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        }

        // ===== НАСТРОЙКИ: BLUETOOTH =====

        // Интерфейс мастера заменяет старый ручной ввод MAC и UUID целиком: тап по найденному
        // устройству сам сохраняет адрес и переподключается.
        refreshBluetoothCurrentDevice()
        bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnBluetoothRescan.setOnClickListener {
            playButtonAudio()
            startBluetoothPairingScan()
        }


        // ===== НАСТРОЙКИ: РЕЖИМ РАБОТЫ =====

        // Легаси-попап Screen Resize убран: рабочая область настраивается только шагом DISPLAY AREA мастера.
        bindingMain.incLayoutSettingsGlobal.btnSettingsChangeMode.setOnClickListener {
            playButtonAudio()
            openModeSelectScreen()
        }

        // Слушатель жеста — на корне самого мастера, а не на root: мастер поглощает тач в своих границах,
        // и на root жест ресайза до него бы не доезжал.
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        bindingMain.incLayoutPipboy2000Wizard.root.setOnTouchListener { _, event ->
            if (isResizing) {
                handleTouch(event)
            }
            true
        }


        initWakeWordDetector()

        // savedInstanceState != null означает именно убийство процесса, а не холодный старт.
        if (savedInstanceState != null) {
            restoreAppState(savedInstanceState)
        }
    }

    /** Сохраняем раздел с вкладкой, режим работы и состояние системы ранений. */
    /** Bundle, а не SharedPreferences: только он отличает холодный старт от восстановления. */
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

    /** Сворачивание или блокировка экрана: эмбиент освобождается по-настоящему, но намерение не трогаем. */
    override fun onStop() {
        super.onStop()
        releaseAmbientPlayer()
        stopMapLocationUpdates()
    }
    /** Возврат в приложение; на первом запуске намерение ещё false, поэтому лишнего старта не происходит. */
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
        // Сервис не останавливаем — он держит BLE-связь в фоне; отвязываемся только от локального биндинга.
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