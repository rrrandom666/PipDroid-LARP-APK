package com.malto4.pipdroid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.malto4.pipdroid.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.jvm.internal.Intrinsics
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

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

    // ===== СПИСКИ VIEW =====
    private var listBottomButtons = ArrayList<Button>()
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

    // ===== БАНДЛЫ КАРТЫ И ГОЛОСОВОЙ МОДЕЛИ =====
    private val mapBundleRepository by lazy { MapBundleRepository(this) }
    private val voiceModelRepository by lazy { com.malto4.pipdroid.voice.VoiceModelRepository(this) }
    private val voiceDictationService by lazy { com.malto4.pipdroid.voice.VoiceDictationService() }
    /** Анимация включения/выключения и глитч. */
    private val bootSequence by lazy {
        BootSequenceController(
            activity = this,
            binding = bindingMain,
            handler = handler,
            accentColor = { themeAccentColor() },
            onGlareShouldUpdate = { updateScreenGlareVisibility() },
            onBootFinished = {
                // Первый POWER:1 за сессию: мастер PipBoy не проходит через resetToRoot(),
                // строку 2 и стек надо поднять явно.
                if (row2Views.isEmpty()) {
                    menuChangeBLE(curMenu)
                    menuNavigator.resetToRoot(menuRootNodesFor(curMenu))
                    menuNavigator.activateSelected()
                }
            },
            ambient = { start -> if (start) startAmbientBackgroundSound() else stopAmbientBackgroundSound() },
        )
    }
    /** Экран ITEMS/Часы целиком; общий таймер делится с системой ранений через пять последних колбэков. */
    private val clockController by lazy {
        ClockController(
            activity = this,
            binding = bindingMain,
            prefs = sharedPreferences,
            navigator = menuNavigator,
            mode = { pipBoyMode },
            accentColor = { themeAccentColor() },
            selectedButtonRes = { selected_button },
            scrollbarThumbRes = { currentUiTheme().scrollbarRes },
            itemsMenuRoot = { itemsMenuRoot() },
            backSidebarItem = { backSidebarItem() },
            menuBackNode = { onHighlight, onBeforePop -> menuBackNode(pipBoyMode, onHighlight, onBeforePop) },
            playTick = { playTickAudio() },
            playButton = { playButtonAudio() },
            playConfirm = { playConfirmAudio() },
            playError = { playErrorAudio() },
            suppressTickAround = { block -> suppressTickAroundTouchSync(block) },
            syncRow2Active = { syncRow2ActiveFromNavigator() },
            hasAudioPermission = { checkAudioPermission() },
            requestAudioPermission = { requestAudioPermission() },
            isWoundActive = { woundPhase != WoundPhase.NONE },
            timerLabelText = { clockTimerLabelText() },
            onWoundTimerFired = { fireWoundTimer() },
            onWoundStopRequested = { stopWoundTimerEarly() },
            onWoundCountdownTick = { remainingSeconds ->
                if (woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD) {
                    updateWoundCountdownText(remainingSeconds)
                }
            },
        )
    }
    private val REQUEST_CODE_PERMISSION_JOURNAL_DICTATION = 25
    private val REQUEST_CODE_PERMISSION_BLUETOOTH_SETTINGS_SCAN = 26
    private val REQUEST_CODE_PERMISSION_MAP_MARKER_DICTATION = 27
    /** Экран ITEMS/Карта целиком; диктовка имени отметки живёт внутри контроллера, наружу видна только её занятость. */
    private val mapController by lazy {
        MapController(
            activity = this,
            binding = bindingMain,
            navigator = menuNavigator,
            bundleRepository = mapBundleRepository,
            dictationService = voiceDictationService,
            voiceModels = voiceModelRepository,
            dictationPermissionRequestCode = REQUEST_CODE_PERMISSION_MAP_MARKER_DICTATION,
            mode = { pipBoyMode },
            accentColor = { themeAccentColor() },
            selectedButtonRes = { selected_button },
            scrollbarThumbRes = { currentUiTheme().scrollbarRes },
            itemsMenuRoot = { itemsMenuRoot() },
            backSidebarItem = { backSidebarItem() },
            menuBackNode = { onHighlight, onBeforePop -> menuBackNode(pipBoyMode, onHighlight, onBeforePop) },
            playTick = { playTickAudio() },
            playButton = { playButtonAudio() },
            playConfirm = { playConfirmAudio() },
            playError = { playErrorAudio() },
            suppressTickAround = { block -> suppressTickAroundTouchSync(block) },
            syncRow2Active = { syncRow2ActiveFromNavigator() },
            isVoiceCommandBusy = { awaitingVoiceCommand },
        )
    }
    /** Экран ITEMS/Журнал целиком; диктовка текста записи живёт внутри контроллера, наружу видна только её занятость. */
    private val journalController by lazy {
        JournalController(
            activity = this,
            binding = bindingMain,
            navigator = menuNavigator,
            dictationService = voiceDictationService,
            voiceModels = voiceModelRepository,
            dictationPermissionRequestCode = REQUEST_CODE_PERMISSION_JOURNAL_DICTATION,
            gameYear = { sharedPreferences.getInt(gameYear_SPKey, 2276) },
            mode = { pipBoyMode },
            accentColor = { themeAccentColor() },
            selectedButtonRes = { selected_button },
            scrollbarThumbRes = { currentUiTheme().scrollbarRes },
            itemsMenuRoot = { itemsMenuRoot() },
            menuBackNode = { onHighlight, onBeforePop -> menuBackNode(pipBoyMode, onHighlight, onBeforePop) },
            playTick = { playTickAudio() },
            playButton = { playButtonAudio() },
            playConfirm = { playConfirmAudio() },
            playError = { playErrorAudio() },
            suppressTickAround = { block -> suppressTickAroundTouchSync(block) },
            syncRow2Active = { syncRow2ActiveFromNavigator() },
            isVoiceCommandBusy = { awaitingVoiceCommand },
        )
    }
    /** Разделы STATS/SPECIAL, Skills и Perks вместе с экраном фильтра перков. */
    private val statsController by lazy {
        StatsController(
            activity = this,
            binding = bindingMain,
            prefs = sharedPreferences,
            navigator = menuNavigator,
            mode = { pipBoyMode },
            accentColor = { themeAccentColor() },
            selectedButtonRes = { selected_button },
            scrollbarThumbRes = { currentUiTheme().scrollbarRes },
            statsMenuRoot = { statsMenuRoot() },
            backSidebarItem = { backSidebarItem() },
            menuBackNode = { onHighlight, onBeforePop -> menuBackNode(pipBoyMode, onHighlight, onBeforePop) },
            recordScrollValueEditor = { scrollView -> recordScrollValueEditor(scrollView) },
            playTick = { playTickAudio() },
            playButton = { playButtonAudio() },
            playConfirm = { playConfirmAudio() },
            playError = { playErrorAudio() },
            syncRow2Active = { syncRow2ActiveFromNavigator() },
            enableBottomButtons = { action -> enableDisableBottomButtons(action, listBottomButtons) },
            enableTopSwipe = { action -> enableDisableTopSwipe(action) },
        )
    }
    /** Связь с корпусом по BLE и сканер пейринга; разбор пришедших команд остаётся в активности. */
    // Тип указан явно: контроллер и enableBluetoothLauncher ссылаются друг на друга, и без него вывод типов зацикливается.
    private val bluetooth: BluetoothController by lazy {
        BluetoothController(
            activity = this,
            binding = bindingMain,
            prefs = sharedPreferences,
            scanPermissionRequestCode = REQUEST_CODE_PERMISSION_BLUETOOTH_SETTINGS_SCAN,
            accentColor = { themeAccentColor() },
            playButton = { playButtonAudio() },
            requestEnableBluetooth = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onCommand = { raw -> handleBleCommand(raw) },
        )
    }
    /** Экран выбора режима и мастер настройки PipBoy 2000/3000; сам режим остаётся полем активности. */
    private val setupWizard by lazy {
        SetupWizardController(
            activity = this,
            binding = bindingMain,
            currentMode = { pipBoyMode },
            accentColor = { themeAccentColor() },
            selectedButtonRes = { selected_button },
            scrollbarThumbRes = { currentUiTheme().scrollbarRes },
            equalizeButtonWidths = { buttons -> equalizeButtonWidths(*buttons.toTypedArray()) },
            setWizardButtonState = { button, selected -> setWizardButtonState(button, selected) },
            setWizardButtonDisabled = { button -> setWizardButtonDisabled(button) },
            playTick = { playTickAudio() },
            playButton = { playButtonAudio() },
            playError = { playErrorAudio() },
            onModeChosen = { mode -> onPipBoyModeChosen(mode) },
            hasAllRequiredPermissions = { hasAllRequiredPermissions() },
            checkPermissions = { checkPermissions() },
            setupBluetooth = { bluetooth.start() },
            startPairingScan = { container, status, onSelect -> bluetooth.startPairingScan(container, status, onSelect) },
            stopPairingScan = { bluetooth.stopPairingScan() },
            applyPairedDevice = { address -> bluetooth.applyPairedDevice(address) },
            setPowerOffInstant = { setPowerOffInstant() },
            updateScreenGlare = { updateScreenGlareVisibility() },
            resetToFullScreen = { resetToFullScreen() },
            loadViewState = { loadViewState() },
            applyTemporaryFullScreenLayout = { applyTemporaryFullScreenLayout() },
            finishPhoneSetup = { finishPhoneModeSetup() },
            skipToMainScreenDebug = { skipWizardToMainScreenDebug() },
            importVoiceModel = { openVoiceModelZipLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            importMapBundle = { openMapBundleTreeLauncher.launch(null) },
        )
    }
    companion object {
        // Отладочная инъекция BLE-команд без реального ESP32, см. registerDebugCommandReceiver().
        private const val ACTION_DEBUG_BLE_COMMAND = "com.malto4.pipdroid.DEBUG_BLE_COMMAND"
        private const val EXTRA_DEBUG_BLE_RAW = "raw"


        // Система ранений/кровотечения
        private const val WOUND_BLEED_BANDAGE_DURATION_SECONDS = 600
        private const val STUN_DURATION_SECONDS = 300

        // Восстановление состояния после убийства процесса в фоне
        private const val KEY_CUR_MENU = "restore_curMenu"
        private const val KEY_ROOT_CURSOR = "restore_rootCursor"
        private const val KEY_PIPBOY_MODE = "restore_pipBoyMode"
        private const val KEY_WOUND_PHASE = "restore_woundPhase"
        private const val KEY_WOUND_SEVERITY = "restore_woundSeverity"
        private const val KEY_CRIPPLED_HEAD = "restore_crippledHead"
        private const val KEY_CRIPPLED_TORSO = "restore_crippledTorso"
        private const val KEY_CRIPPLED_LEFT_ARM = "restore_crippledLeftArm"
        private const val KEY_CRIPPLED_RIGHT_ARM = "restore_crippledRightArm"
        private const val KEY_CRIPPLED_LEFT_LEG = "restore_crippledLeftLeg"
        private const val KEY_CRIPPLED_RIGHT_LEG = "restore_crippledRightLeg"
        private const val KEY_STATUS_CURSOR_ROW = "restore_statusCursorRow"

        // Прокрутка длинной записи энкодером
        private const val SIDEBAR_RECORD_SCROLL_STEP_DP = 60f


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
        bluetooth.start()
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
        if (setupWizard.showImportIfOnPermissionsStep()) return
        bluetooth.start()
    }

    // ===== РАЗМЕР ЭКРАНА =====
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var newWidth = 0
    private var newHeight = 0
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

    // ===== ДОЛГИЕ НАЖАТИЯ: пасхалка и урон игрока =====
    private var statsCndPopupIsHolding = false
    private var menuSwipeEnabled = true

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

    private lateinit var selectedSubMenu: Button

    private val handler = Handler(Looper.getMainLooper())
    // 300мс-тик часов; ссылка нужна, чтобы onDestroy() его остановил.
    private var tickThread: Thread? = null
    private val longPressRunnable = Runnable {
        if (statsCndPopupIsHolding) {
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.layoutTabStatusCndContent.visibility = View.GONE
            bindingMain.incLayoutFilterModification.root.visibility = View.GONE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
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
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                journalController.startDictationIfPopupVisible()
            } else {
                playErrorAudio()
            }
        }
        if (requestCode == REQUEST_CODE_PERMISSION_MAP_MARKER_DICTATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mapController.startMarkerDictationIfPopupVisible()
            } else {
                playErrorAudio()
            }
        }
        if (requestCode == REQUEST_CODE_PERMISSION_BLUETOOTH_SETTINGS_SCAN) {
            val bluetoothPanelVisible = bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.root.visibility == View.VISIBLE
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED } && bluetoothPanelVisible) {
                bluetooth.startSettingsPairingScan()
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
        if (awaitingVoiceCommand || !journalController.isDictationIdle || !mapController.isMarkerDictationIdle) return
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
            clockController.resetTimer()
            finishVoiceCommand(text)
            return
        }
        if (normalized.contains("пауз") || normalized.contains("продолж") || normalized.contains("возобнов")) {
            val allowed = woundPhase == WoundPhase.NONE || woundPhase == WoundPhase.DEAD
            if (!allowed || clockController.isTimerIdle) {
                playErrorAudio()
            } else {
                playButtonAudio()
                clockController.pauseResumeTimer()
            }
            finishVoiceCommand(text)
            return
        }
        if (normalized.contains("таймер") && normalized.contains("минут")) {
            val minutes = parseRussianNumber(normalized)
            if (minutes == null || minutes <= 0 || !clockController.isTimerIdle) {
                playErrorAudio()
            } else {
                playButtonAudio()
                clockController.startPlainTimer(minutes * 60)
            }
            finishVoiceCommand(text)
            return
        }
        if (normalized.contains("маршрут")) {
            if (normalized.contains("отмен")) {
                playButtonAudio()
                mapController.cancelRoute()
            } else {
                val queryTokens = normalized.substringAfter("маршрут").trim()
                    .split(Regex("\\s+"))
                    .filterNot { it.isBlank() || it in ROUTE_FILLER_WORDS }
                if (mapController.prepareVoiceRouteToMarker(queryTokens)) {
                    playTickAudio()
                    navigateToItemsSection("MAP")
                } else {
                    playErrorAudio()
                }
            }
            finishVoiceCommand(text)
            return
        }
        // Журнал — новая запись
        if (normalized.contains("нов") && normalized.contains("запис")) {
            playTickAudio()
            navigateToItemsSection("JOURNAL")
            journalController.openNewEntryEditor()
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
            clockController.selectFeature("STOPWATCH")
            finishVoiceCommand(text); return
        }
        if (normalized.contains("час")) {
            navigateToItemsSection("CLOCK")
            clockController.selectFeature("TIME")
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

    // ===== ТЕМА ОФОРМЛЕНИЯ =====
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
    /** Вид кнопки в стиле мастера — не только у мастера: тем же красятся кнопки тьюториала. */
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
    private fun refreshModeSettingsLabel() {
        val label = "${getString(R.string.settings_4_name)} ${setupWizard.displayName(pipBoyMode)}"
        bindingMain.incLayoutSettingsGlobal.tvSettings4.text = label
    }
    private fun applyModeGating() {
        val header = bindingMain.incLayoutHeaderToplevel
        val visibility = if (pipBoyMode == PipBoyMode.PHONE) View.GONE else View.VISIBLE
        header.btnHeaderRadio.visibility = visibility
        header.spaceHeaderRadioGap.visibility = visibility
    }
    /** Глобальная часть выбора режима: сам экран выбора и мастер дальше ведёт SetupWizardController. */
    private fun onPipBoyModeChosen(mode: PipBoyMode) {
        stopAmbientBackgroundSound()
        pipBoyMode = mode
        refreshModeSettingsLabel()
        applyModeGating()
        refreshSidebarBackItems()

        // Кнопки шапки/футера здесь НЕ включаем: мастер не перехватывает тач фоном, и они станут кликабельны сквозь него.
        if (bindingMain.incLayoutSettingsGlobal.root.visibility == View.VISIBLE) {
            bindingMain.incLayoutSettingsGlobal.root.visibility = View.GONE
        }

        bindingMain.constraintlayoutTutorial.visibility = View.GONE
        bindingMain.constraintlayoutMain.visibility = View.VISIBLE
    }
    private fun finishPhoneModeSetup() {
        setupWizard.hide()
        // Мастер реально закрылся — возвращаем кнопки шапки/футера и свайп.
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        resetToFullScreen()
        bindingMain.viewPowerOff.animate().cancel()
        bindingMain.viewPowerOff.visibility = View.GONE
        updateScreenGlareVisibility()
        bluetooth.stop()
        menuChangeBLE("STATS")
        menuNavigator.resetToRoot(statsMenuRoot())
        // Стартовый курсор энкодера — первый дочерний пункт бокового меню, не сам узел строки 2.
        menuNavigator.activateSelected()
        bootSequence.cancelBootSequence()
        bootSequence.startContinuousGlitch()
        startAmbientBackgroundSound()
    }

    private fun restoreAppState(savedInstanceState: Bundle) {
        bindingMain.constraintlayoutTutorial.visibility = View.GONE
        setupWizard.hideModeSelect()
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
                setupWizard.hide()
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

        applyWoundFace()
        updateWoundButtonsUI()
        updateWoundStatusLine()
        clockController.restoreState(savedInstanceState)
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
    private fun skipWizardToMainScreenDebug() {
        bluetooth.stopPairingScan()
        bootSequence.cancelBootSequence()
        setupWizard.hide()
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
        bootSequence.startContinuousGlitch()
        startAmbientBackgroundSound()
    }


    // ===== РАЗМЕР ЭКРАНА =====
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
                newWidth = max(newWidth, setupWizard.minContentWidthPx)
                newHeight = max(newHeight, setupWizard.minContentHeightPx)

                val displayMetrics = resources.displayMetrics
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

    // ===== СТАТУС БАНДЛОВ КАРТЫ И ГОЛОСОВОЙ МОДЕЛИ =====
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
        val isOff = bindingMain.viewPowerOff.visibility == View.VISIBLE || setupWizard.isPowerHintVisible
        bindingMain.imgScreenglare.visibility = if (isOff) View.GONE else View.VISIBLE
    }
    /** Мгновенный выключенный вид без звука и анимации — служебный дефолт при входе в мастер, не applyPowerState(false). */
    private fun setPowerOffInstant() {
        bootSequence.cancelBootSequence()
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
            bootSequence.cancelBootSequence()
            bootSequence.playBootSequence()
            // Мастер настройки PipBoy 2000/3000 больше не нужен — POWER реально пришёл.
            // Гейт по факту открытости: на обычных POWER-переключениях кнопки и так уже включены.
            if (setupWizard.hide()) {
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
            // Пока шли Permissions и подсказка про POWER, окно было временно fullscreen — применяем настроенную область.
            loadViewState()
        } else {
            bootSequence.cancelBootSequence()
            bootSequence.playShutdownSequence()
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
        // SPECIAL, Skills и Perks: сами ветки строит StatsController, здесь только обёртки узлов.
        val specialNode = MenuNode(
            id = "SPECIAL",
            children = statsController.specialChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bindingMain.incLayoutTabStatsBottom.btnStatsSpecial) }
        )
        val skillsNode = MenuNode(
            id = "SKILLS",
            children = statsController.skillsChildrenNodes(),
            onHighlight = { simulateEncoderTabHighlight(bindingMain.incLayoutTabStatsBottom.btnStatsSkills) }
        )
        val bottom = bindingMain.incLayoutTabStatsBottom
        return listOf(
            statusNode,
            specialNode,
            skillsNode,
            MenuNode(
                id = "PERKS",
                children = statsController.perksChildrenNodes(),
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
        // Clock — SidebarMenuAdapter, дети целиком за ClockController.
        val clockNode = MenuNode(
            id = "CLOCK",
            children = clockController.childrenNodes(),
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
            childrenProvider = { journalController.childrenNodes() },
            onHighlight = { simulateEncoderTabHighlight(bottom.btnItemsJournal) },
        )
        // childrenProvider по той же причине, что у JOURNAL: markers грузятся асинхронно при открытии экрана.
        val mapNode = MenuNode(
            id = "MAP",
            childrenProvider = { mapController.childrenNodes() },
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
    // ===== DATA И RADIO: ЭНКОДЕР =====
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
            bluetooth.send("RADIOFREQ:$freq")
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
                if (clockController.isFiredOverlayVisible) {
                    clockController.activateFiredOverlayStop()
                } else {
                    menuNavigator.activateSelected()
                    syncRow2ActiveFromNavigator()
                }
            }
            "ENC" -> {
                if (!clockController.isFiredOverlayVisible) {
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
    /** Общий признак "энкодер сфокусирован здесь" — четыре L-уголка на отдельном View рядом с целью. */
    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) =
        applyFocusBrackets(bracketsView, pipBoyMode, visible)
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
    /** Пересобирает три списка, когда режим стал известен после onCreate(); нужен setItems целиком, не точечная правка. */
    private fun refreshSidebarBackItems() {
        statsController.refreshModeGating()
        statusAdapter.setItems(statusSidebarItems(), resetSelection = false)
        dataFilesAdapter.setItems(dataFilesSidebarItems(), resetSelection = false)
        refreshGeigerMenuButtonVisibility()
        journalController.refreshModeGating()
        mapController.refreshModeGating()
        clockController.refreshModeGating()
        // Уровни, уже лежащие в стеке навигатора, захвачены прежним режимом: itemsMenuRoot() и
        // menuBackNode() пересчитываются на каждом обращении, но не задним числом для того уровня,
        // на котором курсор стоит прямо сейчас. Обязана идти до строки 2 — та читает rootCursor().
        menuNavigator.rebuildLevels(menuRootNodesFor(curMenu))
        // Строка 2 гейтится режимом так же, как боковые списки, но живёт вне их: без пересборки её
        // пункты остаются от прежнего режима и расходятся с деревом на один индекс.
        // Пустую не трогаем: её первую сборку держит гейт row2Views.isEmpty() на старте.
        if (row2Views.isNotEmpty()) {
            setupRow2(curMenu)
            syncRow2ActiveFromNavigator()
        }
    }
    /** Menu на Гейгере — обычная кнопка, не элемент адаптера, поэтому видимость обновляется отдельно. */
    private fun setEncoderOnlyVisible(vararg views: View) = applyEncoderOnlyVisible(pipBoyMode, *views)
    private fun refreshGeigerMenuButtonVisibility() = setEncoderOnlyVisible(bindingMain.incLayoutTabItemsGeiger.btnGeigerMenu)
    private fun setBottomButtons(vararg buttons: Button){
        listBottomButtons.clear()
        listBottomButtons.addAll(buttons)
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
        bluetooth.send(menu)
        // Уход с ITEMS гасит GPS карты; возврат на Map перезапустит апдейты сам.
        if (menu != "ITEMS") {
            mapController.stopLocationUpdates()
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
        clockController.setTimerPauseAllowed(woundPhase == WoundPhase.NONE || woundPhase == WoundPhase.DEAD)
    }
    /** Подпись над отсчётом на экране Таймера: у таймера может быть стадия ранения, для обычного запуска она пустая. */
    private fun clockTimerLabelText(): String = when (woundPhase) {
        WoundPhase.STUNNED -> getString(R.string.status_wound_stunned_label)
        WoundPhase.BLEED -> getString(R.string.status_wound_bleeding_label)
        WoundPhase.BANDAGE -> getString(R.string.status_wound_bandage_label)
        else -> ""
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
        clockController.startWoundTimer(durationSeconds)
        updateWoundStatusLine()
        updateWoundCountdownText(durationSeconds)
    }
    /** Вылечен — общий финал для перевязки и оглушения; CRIPPLED снимается со всех шести частей. */
    private fun healWoundsToHealthy() {
        woundPhase = WoundPhase.NONE
        applyWoundFace()
        updateWoundButtonsUI()
        refreshStatusEncoderChildren()
        updateWoundStatusLine()
        clockController.stopTimer()
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
        clockController.stopTimer()
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
    private fun skipWoundTimer() = clockController.skipTimerToEnd()
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
        setupWizard.setup()
        registerDebugCommandReceiver()

        //Keep phone screen active
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Звуки создаются лениво в момент использования, а не все разом здесь при каждом старте.

        //BOTTOM BUTTON SETUP (DEFAULT STATUS)
        setBottomButtons(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks)


        // Оба списка STATS, экран фильтра и кнопки +/- — за StatsController; порядок блоков внутри тот же.
        statsController.setup()

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
        bluetooth.refreshConnectionIndicator()

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
                            bindingMain.incLayoutHeaderToplevel.tvHeaderBattery.text = getBatteryPercent().toString()
                            clockController.onTick(gameCalendar, timeHHmm, timess)
                        }
                    }
                } catch (_: InterruptedException) {}
            }
        }
        tickThread?.start()

        // Горизонтальный свайп по шапке переключает вкладку.
        menuGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Начальное событие система может не отдать — без него свайпа нет.
                val start = e1 ?: return false
                val diffX = e2.x - start.x
                val diffY = e2.y - start.y
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onMenuSwipeRight()
                        } else {
                            onMenuSwipeLeft()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bindingMain.titleConstraintLayout.setOnTouchListener{_, event ->
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
            statsController.clearSpecialSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        // ===== STATS: SKILLS =====
        bindingMain.incLayoutTabStatsBottom.btnStatsSkills.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsSkills, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.GONE
            menuNavigator.setRootCursor(2)
            statsController.clearSkillsSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }

        // ===== STATS: PERKS =====
        bindingMain.incLayoutTabStatsBottom.btnStatsPerks.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsPerks, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.VISIBLE
            menuNavigator.setRootCursor(3)
            // Свежий адаптер стартует с подсвеченным пунктом 0 — гасим рамку молча до реального провала курсора.
            statsController.openPerksScreen()
            statsController.clearPerksSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
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
            mapController.openScreen()
            mapController.clearSidebarSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        mapController.setup()

        // ===== ITEMS: ЧАСЫ =====
        bindingMain.incLayoutTabItemsBottom.btnItemsClock.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsClock, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            mapController.stopLocationUpdates()
            menuNavigator.setRootCursor(itemsRootIndexFor("CLOCK"))
            clockController.clearSidebarSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        // Сам экран Часов — целиком за ClockController; порядок вызова тот же, что был у его блока.
        clockController.setup()

        // ===== ITEMS: ЖУРНАЛ =====
        bindingMain.incLayoutTabItemsBottom.btnItemsJournal.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsJournal, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.GONE
            mapController.stopLocationUpdates()
            menuNavigator.setRootCursor(itemsRootIndexFor("JOURNAL"))
            // Свежий адаптер стартует с подсвеченным пунктом 0 — гасим рамку молча до реального провала курсора.
            journalController.openScreen()
            journalController.clearSidebarSelection()
            if (!encoderTabHighlight) menuNavigator.activateSelected()
            syncRow2ActiveFromNavigator()
        }
        journalController.setup()

        bindingMain.incLayoutTabItemsBottom.btnItemsGeiger.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsGeiger, listBottomButtons)
            bindingMain.incLayoutTabItemsMap.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsClock.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsJournal.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsGeiger.root.visibility = View.VISIBLE
            mapController.stopLocationUpdates()
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
                    bluetooth.startSettingsPairingScan()
                } else {
                    bluetooth.stopPairingScan()
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
            bluetooth.stopPairingScan()
            saveValues(editSettings1.text.toString(), UIColour_Selector, dateFormat_Selector, editSettings6.isChecked(), editSettings7.isChecked(), editSettingsYear.text.toString().toInt(), editSettingsRegion.text.toString(), languageSelector, editSettings8.isChecked())
            bluetooth.send("STATS")
            recreate()
        }
        // Cancel — выход без сохранения; несохранённые правки теряются, при следующем открытии поля перечитаются.
        cancelButtonSettings.setOnClickListener {
            playButtonAudio()
            bluetooth.stopPairingScan()
            if (!setupWizard.isResizing) {
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

        bluetooth.setup()


        // ===== НАСТРОЙКИ: РЕЖИМ РАБОТЫ =====

        // Легаси-попап Screen Resize убран: рабочая область настраивается только шагом DISPLAY AREA мастера.
        bindingMain.incLayoutSettingsGlobal.btnSettingsChangeMode.setOnClickListener {
            playButtonAudio()
            setupWizard.openModeSelect()
        }

        // Слушатель жеста — на корне самого мастера, а не на root: мастер поглощает тач в своих границах,
        // и на root жест ресайза до него бы не доезжал.
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        bindingMain.incLayoutPipboy2000Wizard.root.setOnTouchListener { _, event ->
            if (setupWizard.isResizing) {
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
        clockController.saveState(outState)
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
        mapController.stopLocationUpdates()
    }
    /** Возврат в приложение; на первом запуске намерение ещё false, поэтому лишнего старта не происходит. */
    override fun onStart() {
        super.onStart()
        if (ambientShouldBePlaying) {
            startAmbientBackgroundSound()
        }
        if (bindingMain.incLayoutTabItemsMap.root.visibility == View.VISIBLE) {
            mapController.startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickThread?.interrupt()
        stopAmbientBackgroundSound()
        bootSequence.cancelBootSequence()
        wakeWordDetector?.release()
        voiceDictationService.release()
        // Сервис не останавливаем — он держит BLE-связь в фоне; отвязываемся только от локального биндинга.
        bluetooth.unbind()
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