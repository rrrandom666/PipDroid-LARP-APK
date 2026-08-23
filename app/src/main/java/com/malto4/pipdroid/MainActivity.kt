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
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.graphics.Bitmap
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
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkInfo
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import com.malto4.pipdroid.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Random
import java.util.UUID
import kotlin.jvm.internal.Intrinsics
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import com.chibde.visualizer.LineVisualizer

class MainActivity : AppCompatActivity(), NetworkChangeReceiver.ConnectivityListener {

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
    private var selectedDateFormat = "MM.dd.yy"
    private var trueFullscreen = false



    /***********************************************************************************************************
     * LIST DEFINITIONS
     **********************************************************************************************************/
    private var listBottomButtons = ArrayList<Button>()
    private var listStatsStatusCndRadEff = ArrayList<Button>()
    private var listStatsSpecials = ArrayList<ConstraintLayout>()
    private var listStatsSkills = ArrayList<ConstraintLayout>()
    private var listStatsGeneralFactions = ArrayList<ConstraintLayout>()
    private var listDataMisc = ArrayList<ConstraintLayout>()
    private var listDataRadios = ArrayList<ConstraintLayout>()

    /***********************************************************************************************************
     * MEDIA PLAYERS
     **********************************************************************************************************/
    private lateinit var lineVisualizer: LineVisualizer
    private val REQUEST_CODE_PERMISSION_RECORD_AUDIO = 23
    private val REQUEST_CODE_PERMISSION_MEDIA = 123
    private var mediaPlayerCndRadEffList = mutableListOf<MediaPlayer>()
    private var mediaPlayerCRF: MediaPlayer? = null
    private val mediaPlayerStimpakList = mutableListOf<MediaPlayer>()
    private var mediaPlayerDamage: MediaPlayer? = null
    private var mediaPlayerRadaway: MediaPlayer? = null
    private var mediaPlayerRadX: MediaPlayer? = null
    private var mediaPlayerNewTabList = mutableListOf<MediaPlayer>()
    private var mediaPlayerLightOn: MediaPlayer? = null
    private var mediaPlayerLightOff: MediaPlayer? = null
    private var mediaPlayerItemSelectList = mutableListOf<MediaPlayer>()
    private var mediaPlayerErrorList = mutableListOf<MediaPlayer>()
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
     * LOCAL MAP
     **********************************************************************************************************/
    private lateinit var localMapOSMDroid: MapView
    private lateinit var localMapcolorFilter: ColorFilter
    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    companion object {
        private const val REQUEST_CODE_PERMISSION_INTERNET = 1
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
     * debug-сборки — в релизе приёмник не регистрируется и адрес недостижим. Явное
     * FQCN, не unqualified BuildConfig — в файле уже есть import
     * org.osmdroid.library.BuildConfig (см. Configuration чуть выше), который иначе
     * перехватил бы разрешение простого имени.
     *
     * adb shell am broadcast -p com.malto4.pipdroid -a com.malto4.pipdroid.DEBUG_BLE_COMMAND --es raw "ENC:+1"
     */
    private fun registerDebugCommandReceiver() {
        if (!com.malto4.pipdroid.BuildConfig.DEBUG) return
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
    private fun onRequiredPermissionsGranted() {
        setupBluetooth()
        // Если разрешения выдавались с шага PERMISSIONS мастера PipBoy 2000/3000 —
        // сразу ведём дальше, к сопряжению с корпусом, не заставляя жать что-то ещё.
        val wizard = bindingMain.incLayoutPipboy2000Wizard
        if (wizard.root.visibility == View.VISIBLE && wizard.layoutWizardPermissions.visibility == View.VISIBLE) {
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

    /***********************************************************************************************************
     * LongButtonPresses - EasterEgg + FLASHLIGHT + PlayerDamage
     **********************************************************************************************************/
    private var statsCndPopupIsHolding = false
    private var menuSwipeEnabled = true
    private var perkModification = false
    private var isFlashlightOn = false
    private var isFlashlightOff = false
    private var numStimpak = 10
    private var hpLevel = 720
    private var delayIterationCount = 0
    private var delayModify = 500L

    private var lvlDmgTotal = 0
    private var lvlDmgHead = 0
    private var lvlDmgTorso = 0
    private var lvlDmgLftArm = 0
    private var lvlDmgRgtArm = 0
    private var lvlDmgLftLeg = 0
    private var lvlDmgRgtLeg = 0

    private var isDmgHead = false
    private var isDmgTorso = false
    private var isDmgLftArm = false
    private var isDmgRgtArm = false
    private var isDmgLftLeg = false
    private var isDmgRgtLeg = false

    private var selectedSPECIAL = "STRENGTH"
    private var isSPECIAL_S = false
    private var isSPECIAL_P = false
    private var isSPECIAL_E = false
    private var isSPECIAL_C = false
    private var isSPECIAL_I = false
    private var isSPECIAL_A = false
    private var isSPECIAL_L = false

    private var selectedSKILL = "BARTER"
    private var isSKILL_1 = false
    private var isSKILL_2 = false
    private var isSKILL_3 = false
    private var isSKILL_4 = false
    private var isSKILL_5 = false
    private var isSKILL_6 = false
    private var isSKILL_7 = false
    private var isSKILL_8 = false
    private var isSKILL_9 = false
    private var isSKILL_10 = false
    private var isSKILL_11 = false
    private var isSKILL_12 = false
    private var isSKILL_13 = false

    private var selectedFACTION = "BOOMERS"
    private var isFACTION_1 = false
    private var isFACTION_2 = false
    private var isFACTION_3 = false
    private var isFACTION_4 = false
    private var isFACTION_5 = false
    private var isFACTION_6 = false
    private var isFACTION_7 = false
    private var isFACTION_8 = false
    private var isFACTION_9 = false
    private var isFACTION_10 = false
    private var isFACTION_11 = false
    private var isFACTION_12 = false
    private var isFACTION_13 = false

    private lateinit var selectedSubMenu: Button

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = object : Runnable {
        override fun run() {
            if (statsCndPopupIsHolding) {
                bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.root.visibility = View.VISIBLE
                bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.layoutTabStatusCndContent.visibility = View.GONE
                bindingMain.incLayoutFilterModification.root.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
            }
            if (perkModification){
                filteringMenu = "PERKS"
                listEntries(filterFrame, perks)
                bindingMain.incLayoutFilterModification.root.visibility = View.VISIBLE
                bindingMain.layoutStats.visibility = View.GONE
                bindingMain.layoutItems.visibility = View.GONE
                bindingMain.layoutData.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
                perkModification = false
            }
            if (isFlashlightOn){
                mediaPlayerLightOn?.start()
                bindingMain.titleConstraintLayout.visibility = View.GONE
                bindingMain.mainConstraintLayout.visibility = View.GONE
                bindingMain.bottomConstraintLayout.visibility = View.GONE
                bindingMain.flFlashlight.visibility = View.VISIBLE
                isFlashlightOff = false
            }
            if (isFlashlightOff){
                mediaPlayerLightOff?.start()
                bindingMain.titleConstraintLayout.visibility = View.VISIBLE
                bindingMain.mainConstraintLayout.visibility = View.VISIBLE
                bindingMain.bottomConstraintLayout.visibility = View.VISIBLE
                bindingMain.flFlashlight.visibility = View.GONE
                isFlashlightOn = false
            }
            if (isDmgHead){
                playerCharacterUpdate("head", "damage")
                handler.postDelayed(this, 1000) // 1000 second
            }
            if (isDmgTorso){
                playerCharacterUpdate("torso", "damage")
                handler.postDelayed(this, 1000) // 1000 second
            }
            if (isDmgLftArm){
                playerCharacterUpdate("leftArm", "damage")
                handler.postDelayed(this, 1000) // 1000 second
            }
            if (isDmgRgtArm){
                playerCharacterUpdate("rightArm", "damage")
                handler.postDelayed(this, 1000) // 1000 second
            }
            if (isDmgLftLeg){
                playerCharacterUpdate("leftLeg", "damage")
                handler.postDelayed(this, 1000) // 1000 second
            }
            if (isDmgRgtLeg){
                playerCharacterUpdate("rightLeg", "damage")
                handler.postDelayed(this, 1000) // 1 second
            }

            /*
            ---------------- SPECIAL
            */
            if(isSPECIAL_S){
                var curValue = sharedPreferences.getInt("SPECIAL_S", 5)
                curValue++
                if(curValue > 10) {
                    curValue = 1
                }
                bindingMain.incLayoutTabStatsSpecial.tvSpecialStrengthValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SPECIAL_S", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isSPECIAL_P){
                var curValue = sharedPreferences.getInt("SPECIAL_P", 5)
                curValue++
                if(curValue > 10) {
                    curValue = 1
                }
                bindingMain.incLayoutTabStatsSpecial.tvSpecialPerceptionValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SPECIAL_P", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isSPECIAL_E){
                var curValue = sharedPreferences.getInt("SPECIAL_E", 5)
                curValue++
                if(curValue > 10) {
                    curValue = 1
                }
                bindingMain.incLayoutTabStatsSpecial.tvSpecialEnduranceValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SPECIAL_E", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isSPECIAL_C){
                var curValue = sharedPreferences.getInt("SPECIAL_C", 5)
                curValue++
                if(curValue > 10) {
                    curValue = 1
                }
                bindingMain.incLayoutTabStatsSpecial.tvSpecialCharismaValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SPECIAL_C", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isSPECIAL_I){
                var curValue = sharedPreferences.getInt("SPECIAL_I", 5)
                curValue++
                if(curValue > 10) {
                    curValue = 1
                }
                bindingMain.incLayoutTabStatsSpecial.tvSpecialIntelligenceValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SPECIAL_I", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isSPECIAL_A){
                var curValue = sharedPreferences.getInt("SPECIAL_A", 5)
                curValue++
                if(curValue > 10) {
                    curValue = 1
                }
                bindingMain.incLayoutTabStatsSpecial.tvSpecialAgilityValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SPECIAL_A", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isSPECIAL_L){
                var curValue = sharedPreferences.getInt("SPECIAL_L", 5)
                curValue++
                if(curValue > 10) {
                    curValue = 1
                }
                bindingMain.incLayoutTabStatsSpecial.tvSpecialLuckValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SPECIAL_L", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }

            /*
            ---------------- SKILLS
            */
            if(isSKILL_1){
                var curValue = sharedPreferences.getInt("SKILLS_1", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsBarterValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_1", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_2){
                var curValue = sharedPreferences.getInt("SKILLS_2", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsBigGunsValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_2", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_3){
                var curValue = sharedPreferences.getInt("SKILLS_3", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsEnergyWeaponsValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_3", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_4){
                var curValue = sharedPreferences.getInt("SKILLS_4", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsExplosivesValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_4", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_5){
                var curValue = sharedPreferences.getInt("SKILLS_5", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsLockpickValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_5", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_6){
                var curValue = sharedPreferences.getInt("SKILLS_6", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsMedicineValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_6", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_7){
                var curValue = sharedPreferences.getInt("SKILLS_7", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsMeleeWeaponsValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_7", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_8){
                var curValue = sharedPreferences.getInt("SKILLS_8", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsRepairValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_8", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_9){
                var curValue = sharedPreferences.getInt("SKILLS_9", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsScienceValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_9", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_10){
                var curValue = sharedPreferences.getInt("SKILLS_10", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsSmallGunsValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_10", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_11){
                var curValue = sharedPreferences.getInt("SKILLS_11", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsSneakValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_11", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_12){
                var curValue = sharedPreferences.getInt("SKILLS_12", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsSpeechValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_12", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }
            if(isSKILL_13){
                var curValue = sharedPreferences.getInt("SKILLS_13", 10)
                curValue++
                if(curValue > 100) {
                    curValue = 10
                }
                bindingMain.incLayoutTabStatsSkills.tvSkillsUnarmedValue.text = curValue.toString()
                sharedPreferences.edit().putInt("SKILLS_13", curValue).apply()
                playCNDSelectAudio()
                if (delayIterationCount % 10 == 0) {
                    // Decrease the delay every 10 iterations
                    delayModify = (delayModify * 0.9).toLong().coerceAtLeast(50L) // Minimum delay of 100ms
                }
                handler.postDelayed(this, delayModify)
            }

            /*
            ---------------- FACTIONS
            */
            if(isFACTION_1){
                var curValue = sharedPreferences.getInt("FACTION_1", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_1", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_2){
                var curValue = sharedPreferences.getInt("FACTION_2", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_2", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_3){
                var curValue = sharedPreferences.getInt("FACTION_3", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_3", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_4){
                var curValue = sharedPreferences.getInt("FACTION_4", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_4", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_5){
                var curValue = sharedPreferences.getInt("FACTION_5", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_5", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_6){
                var curValue = sharedPreferences.getInt("FACTION_6", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_6", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_7){
                var curValue = sharedPreferences.getInt("FACTION_7", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_7", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_8){
                var curValue = sharedPreferences.getInt("FACTION_8", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_8", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_9){
                var curValue = sharedPreferences.getInt("FACTION_9", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_9", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_10){
                var curValue = sharedPreferences.getInt("FACTION_10", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_10", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_11){
                var curValue = sharedPreferences.getInt("FACTION_11", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_11", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_12){
                var curValue = sharedPreferences.getInt("FACTION_12", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_12", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
            }
            if(isFACTION_13){
                var curValue = sharedPreferences.getInt("FACTION_13", 3)
                curValue++
                if(curValue > 6) {
                    curValue = 0
                }
                bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(curValue))
                sharedPreferences.edit().putInt("FACTION_13", curValue).apply()
                playCNDSelectAudio()
                handler.postDelayed(this, 500) // 500 msecond
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
        if (requestCode == REQUEST_CODE_PERMISSION_INTERNET) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalMap()
            } else {
                bindingMain.incLayoutTabItemsMap.tvPermissionsCheckResult.visibility = View.VISIBLE
                bindingMain.incLayoutTabItemsMap.localMapView.visibility = View.GONE
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
    private fun turnRadioOn(paramMediaPlayer: MediaPlayer) {

        turnAllRadioOff()

        @Suppress("NAME_SHADOWING") var paramMediaPlayer: MediaPlayer? = paramMediaPlayer
        val random = Random()
        if (paramMediaPlayer != null) paramMediaPlayer.isLooping = true
        paramMediaPlayer?.seekTo(random.nextInt(paramMediaPlayer.duration))
        paramMediaPlayer?.start()

        val audioSessionId: Int? = paramMediaPlayer?.audioSessionId

        if (checkAudioPermission()) {
            if (audioSessionId != -1 && audioSessionId != null) {
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

        when(paramMediaPlayer){
            galaxyRadioMediaPlayer -> {
                val mediaPlayer: MediaPlayer? = galaxyRadioMediaPlayer
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            enclaveRadioMediaPlayer -> {
                val mediaPlayer: MediaPlayer? = enclaveRadioMediaPlayer
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            newVegasRadioMediaPlayer -> {
                val mediaPlayer: MediaPlayer? = newVegasRadioMediaPlayer
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            customRadioMediaPlayer -> {
                val mediaPlayer: MediaPlayer? = customRadioMediaPlayer
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }
    private fun turnRadioOff(paramMediaPlayer: MediaPlayer?) {
        // lineVisualizer больше не прячется при остановке — горизонтальная шкала (тот же
        // View, что рисует бегущую волну во время игры) должна оставаться на экране
        // независимо от того, играет музыка или нет.
        paramMediaPlayer?.setVolume(0.0f, 0.0f)
    }
    private fun turnAllRadioOff() {
        val radioMedia1: MediaPlayer? = galaxyRadioMediaPlayer
        radioMedia1?.setVolume(0.0f, 0.0f)
        val radioMedia2: MediaPlayer? = enclaveRadioMediaPlayer
        radioMedia2?.setVolume(0.0f, 0.0f)
        val radioMedia3: MediaPlayer? = newVegasRadioMediaPlayer
        radioMedia3?.setVolume(0.0f, 0.0f)
        if(customMP3FilesFound){
            val radioMedia4: MediaPlayer? = customRadioMediaPlayer
            radioMedia4?.setVolume(0.0f, 0.0f)
        }
    }
    private fun turnAllRadioOffNoVis() {
        val radioMedia1: MediaPlayer? = galaxyRadioMediaPlayer
        radioMedia1?.setVolume(0.0f, 0.0f)
        val radioMedia2: MediaPlayer? = enclaveRadioMediaPlayer
        radioMedia2?.setVolume(0.0f, 0.0f)
        val radioMedia3: MediaPlayer? = newVegasRadioMediaPlayer
        radioMedia3?.setVolume(0.0f, 0.0f)
        if(customMP3FilesFound){
            val radioMedia4: MediaPlayer? = customRadioMediaPlayer
            radioMedia4?.setVolume(0.0f, 0.0f)
        }
    }


    /***********************************************************************************************************
     * BLUETOOTH
     **********************************************************************************************************/
    private fun checkPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Системный диалог разрешений должен физически поместиться на экране — на миг
            // разворачиваемся на весь экран, сворачиваемся обратно в колбэке
            // permissionRequestLauncher выше сразу после закрытия диалога.
            applyTemporaryFullScreenLayout()
            permissionRequestLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
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
            playNewTabSelectAudio()
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
     * Мастер настройки PipBoy 2000/3000 (roadmap, UX-спецификация мастера) — 5 шагов
     * поверх тёмного экрана: Hardware Instructions -> Display Area -> Permissions ->
     * Pairing -> подсказка про POWER. Реальный выход из POWER_HINT — только физическое
     * нажатие POWER на корпусе (applyPowerState(true) прячет весь мастер целиком).
     */
    private enum class PipBoyWizardStep { HARDWARE_INSTRUCTIONS, DISPLAY_AREA, PERMISSIONS, PAIRING, POWER_HINT }

    private fun showWizardStep(step: PipBoyWizardStep) {
        val w = bindingMain.incLayoutPipboy2000Wizard
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
            setupBluetooth()
            showWizardStep(PipBoyWizardStep.PAIRING)
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
        val required = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
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
            w.btnWizardGrantPermissions,
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

        // Шаг 4: Permissions
        w.btnWizardGrantPermissions.setOnClickListener {
            playNewTabSelectAudio()
            checkPermissions()
        }

        // Шаг 5: Pairing
        w.btnWizardPairingRescan.setOnClickListener {
            playNewTabSelectAudio()
            startPairingScan()
        }
        // Обход пейринга в debug-сборках — без реального ESP32 иначе нельзя пройти
        // мастер дальше этого шага вообще (roadmap, этап 7, "быстрая отладка логики
        // экранов"). Не трогает bluetoothMAC_SPKey и не пытается подключиться — просто
        // пропускает шаг, как будто корпус уже выбран.
        w.btnWizardPairingSkipDebug.visibility =
            if (com.malto4.pipdroid.BuildConfig.DEBUG) View.VISIBLE else View.GONE
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
     * LOCAL MAP
     **********************************************************************************************************/
    private fun checkINETPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            checkInternetConnection()
        } else {
            // Request INTERNET permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), REQUEST_CODE_PERMISSION_INTERNET)
        }
    }
    private fun checkInternetConnection() {
        if (isInternetAvailable()) {
            loadLocalMap()
        } else {
            showNoInternetMessage()
        }
    }
    private fun isInternetAvailable(): Boolean {
        var isConnected = false
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            isConnected = networkCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            isConnected = networkInfo?.isConnectedOrConnecting == true
        }

        return isConnected
    }
    override fun onNetworkChanged(isConnected: Boolean) {
        if (isConnected) {
            loadLocalMap()
        } else {
            showNoInternetMessage()
        }
    }
    private fun showNoInternetMessage() {
        bindingMain.incLayoutTabItemsMap.tvPermissionsCheckResult.visibility = View.VISIBLE
        bindingMain.incLayoutTabItemsMap.localMapView.visibility = View.GONE
    }
    private fun loadLocalMap() {

        localMapOSMDroid = bindingMain.incLayoutTabItemsMap.localMapView
        localMapOSMDroid.visibility = MapView.VISIBLE
        localMapOSMDroid.setMultiTouchControls(true)
        localMapOSMDroid.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        localMapOSMDroid.isTilesScaledToDpi = true
        localMapOSMDroid.tileProvider.clearTileCache()

        val mapController = localMapOSMDroid.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(38.8895, -77.0381) // Washington DC
        mapController.setCenter(startPoint)

        // Apply color tint to the map
        val overlay = TilesOverlay(localMapOSMDroid.tileProvider, this)

        when(sharedPreferences.getInt(playerUIColour_SPKey, 0)){
            0 -> {
                val color = ResourcesCompat.getColor(resources, R.color.themeGreenLocalMap, null)
                localMapcolorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
            1 -> {
                val color = ResourcesCompat.getColor(resources, R.color.themeAmberLocalMap, null)
                localMapcolorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
            2 -> {
                val color = ResourcesCompat.getColor(resources, R.color.themeWhiteLocalMap, null)
                localMapcolorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
            3 -> {
                val color = ResourcesCompat.getColor(resources, R.color.themeBlueLocalMap, null)
                localMapcolorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
        }

        overlay.setColorFilter(localMapcolorFilter)
        localMapOSMDroid.overlays.add(overlay)

        // Add MyLocation overlay
        val locationOverlay = MyLocationNewOverlay(localMapOSMDroid)
        locationOverlay.enableMyLocation()
        localMapOSMDroid.overlays.add(locationOverlay)

        bindingMain.incLayoutTabItemsMap.tvPermissionsCheckResult.visibility = View.GONE
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
                MenuNode("CND") { statusButtons.btnCnd.performClick() },
                MenuNode("RAD") { statusButtons.btnRad.performClick() },
                MenuNode("EFF") { statusButtons.btnEff.performClick() },
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
        val general = bindingMain.incLayoutTabStatsGeneral
        val generalNode = MenuNode(
            id = "GENERAL",
            children = listOf(
                MenuNode("BOOMERS") { general.layoutTabGeneralBoomers.performClick() },
                MenuNode("BOS") { general.layoutTabGeneralBos.performClick() },
                MenuNode("CAESARS_LEGION") { general.layoutTabGeneralCaesarsLegion.performClick() },
                MenuNode("FOLLOWERS_APOCALYPSE") { general.layoutTabGeneralFollowersApocalypse.performClick() },
                MenuNode("FREESIDE") { general.layoutTabGeneralFreeside.performClick() },
                MenuNode("GOODSPRINGS") { general.layoutTabGeneralGoodsprings.performClick() },
                MenuNode("GREAT_KHANS") { general.layoutTabGeneralGreatKhans.performClick() },
                MenuNode("NCR") { general.layoutTabGeneralNcr.performClick() },
                MenuNode("NOVAC") { general.layoutTabGeneralNovac.performClick() },
                MenuNode("POWDER_GANGERS") { general.layoutTabGeneralPowderGangers.performClick() },
                MenuNode("PRIMM") { general.layoutTabGeneralPrimm.performClick() },
                MenuNode("THE_STRIP") { general.layoutTabGeneralTheStrip.performClick() },
                MenuNode("WHITE_GLOVE_SOCIETY") { general.layoutTabGeneralWhiteGloveSociety.performClick() },
            ),
            onSelect = { bindingMain.incLayoutTabStatsBottom.btnStatsGeneral.performClick() }
        )
        val bottom = bindingMain.incLayoutTabStatsBottom
        return listOf(
            statusNode,
            specialNode,
            skillsNode,
            MenuNode("PERKS") { bottom.btnStatsPerks.performClick() },
            generalNode,
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
        return listOf(
            MenuNode("MAP") { bottom.btnItemsMap.performClick() },
            MenuNode("CLOCK") { bottom.btnItemsClock.performClick() },
            MenuNode("JOURNAL") { bottom.btnItemsJournal.performClick() },
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
                bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks, bindingMain.incLayoutTabStatsBottom.btnStatsGeneral)
                menuOptionClickedBLE("STATS")
            }
            "ITEMS" -> {
                curMenu = "ITEMS"
                bottomButtonsModify(bindingMain.incLayoutTabItemsBottom.btnItemsMap, bindingMain.incLayoutTabItemsBottom.btnItemsClock, bindingMain.incLayoutTabItemsBottom.btnItemsJournal)
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
            bindingMain.incLayoutSettingsGlobal.layoutTabSettings,
            bindingMain.incLayoutFilterModification.layoutFilterModification,
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.layoutTabSettingsBluetooth,
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.layoutTabStatsCndPopup
            // Часы (ITEMS/Clock, roadmap этап 6 п.3) больше не в этом списке — раньше это
            // был попап со своим фоном-плашкой (settings_menu_background_green), теперь
            // обычный полноэкранный раздел без такого фона, перекрашивать нечего.
            // Add other views as necessary
        )
        var backgroundRes = R.drawable.settings_menu_background_green
        when(Colour){
            0 -> {backgroundRes = R.drawable.settings_menu_background_green
                selected_button = R.drawable.button_selected_green}
            1 -> {backgroundRes = R.drawable.settings_menu_background_amber
                selected_button = R.drawable.button_selected_amber}
            2 -> {backgroundRes = R.drawable.settings_menu_background_white
                selected_button = R.drawable.button_selected_white}
            3 -> {backgroundRes = R.drawable.settings_menu_background_blue
                selected_button = R.drawable.button_selected_blue}
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
            bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings
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
                bindingMain.incLayoutTabStatsGeneral.scrollTabGeneral,
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
    private fun setSelectedCNDEFFRADButton(button: Button?, listArrayListButtons: ArrayList<Button>?) {
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
        findViewById<Button>(R.id.btn_cnd).setBackgroundResource(selected_button)
        findViewById<ConstraintLayout>(R.id.layout_tab_stats_special_strength).setBackgroundResource(selected_button)
        findViewById<ConstraintLayout>(R.id.layout_tab_skills_barter).setBackgroundResource(selected_button)
        findViewById<ConstraintLayout>(R.id.layout_tab_general_boomers).setBackgroundResource(selected_button)
    }
    private fun setupDATA(){
        //Set Selected buttons by default
        findViewById<ConstraintLayout>(R.id.layout_tab_data_misc_entry1).setBackgroundResource(selected_button)
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
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_general).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_stats_general_main).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_clock).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_journal).visibility = View.GONE

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
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_general).visibility = View.VISIBLE
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
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_general).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_stats_general_main).visibility = View.VISIBLE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_clock).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_journal).visibility = View.GONE

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
            Row2Item(bottom.btnStatsGeneral.text) { bottom.btnStatsGeneral.performClick() },
        )
    }
    private fun itemsRow2Items(): List<Row2Item> {
        // Порядок должен совпадать с itemsMenuRoot() и bottomButtonsModify() выше.
        val bottom = bindingMain.incLayoutTabItemsBottom
        return listOf(
            Row2Item(bottom.btnItemsMap.text) { bottom.btnItemsMap.performClick() },
            Row2Item(bottom.btnItemsClock.text) { bottom.btnItemsClock.performClick() },
            Row2Item(bottom.btnItemsJournal.text) { bottom.btnItemsJournal.performClick() },
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
        mediaPlayerCRF?.start()
        topLevelButtonsModify(menu)
        setupMainContent(menu)
        setupRow2(menu)
        enableDisableBottomButtons(false, listBottomButtons)
        enableDisableTopSwipe(false)
        sendBLEText(menu)
    }
    private fun menuOptionClickedBLE(menu: String){
        mediaPlayerCRF?.start()
        topLevelButtonsModify(menu)
        setupMainContentBLE(menu)
        setupRow2(menu)
        enableDisableBottomButtons(true, listBottomButtons)
        enableDisableTopSwipe(true)
        sendBLEText(menu)
    }
    @SuppressLint("SetTextI18n")
    @Suppress("KotlinConstantConditions")
    private fun playerCharacterUpdate(affectedPart: String, bodyAction: String){
        if ((lvlDmgTotal >= 23) && (bodyAction == "damage")){lvlDmgTotal = 23; bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(R.drawable.face_04); return}

        when(affectedPart){
            "head" -> {
                val healthCounter = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyHeadHp
                val healthCounterCrippled = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndPipboyHeadHpCrippled
                val bodyPart = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyHead
                when(bodyAction){
                    "heal" -> {if (lvlDmgHead >= 0){lvlDmgTotal--}; if (lvlDmgHead > 0){lvlDmgHead--; numStimpak--; hpLevel+=30; playStimpakAudio()}}
                    "damage" -> {if (lvlDmgHead >= 4){return}; if (lvlDmgHead < 4){lvlDmgHead++; lvlDmgTotal++; hpLevel-=30; mediaPlayerDamage?.start()};}
                }
                if (lvlDmgHead <= 0){lvlDmgHead = 0}
                if (lvlDmgHead >= 4){lvlDmgHead = 4}
                when(lvlDmgHead){
                    0 -> {healthCounter.setImageResource(R.drawable.hp_center)
                        bodyPart.setImageResource(R.drawable.man_head)}
                    1 -> {healthCounter.setImageResource(R.drawable.hp_center_dmg1)
                        bodyPart.setImageResource(R.drawable.man_head)}
                    2 -> {healthCounter.setImageResource(R.drawable.hp_center_dmg2)
                        bodyPart.setImageResource(R.drawable.man_head)}
                    3 -> {healthCounter.setImageResource(R.drawable.hp_center_dmg3)
                        healthCounter.visibility = View.VISIBLE
                        healthCounterCrippled.visibility = View.GONE
                        bodyPart.setImageResource(R.drawable.man_head)}
                    4 -> {healthCounter.visibility = View.GONE
                        healthCounterCrippled.visibility = View.VISIBLE
                        bodyPart.setImageResource(R.drawable.head_broken)}
                }
            }
            "torso" -> {
                val healthCounter = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyTorsoHp
                val healthCounterCrippled = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndPipboyTorsoHpCrippled
                val bodyPart = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyTorso
                when(bodyAction){
                    "heal" -> {if (lvlDmgTorso >= 0){lvlDmgTotal--}; if (lvlDmgTorso > 0){lvlDmgTorso--; numStimpak--; hpLevel+=30; playStimpakAudio()}}
                    "damage" -> {if (lvlDmgTorso >= 4){return}; if (lvlDmgTorso < 4){lvlDmgTorso++; lvlDmgTotal++; hpLevel-=30; mediaPlayerDamage?.start()};}
                }
                if (lvlDmgTorso <= 0){lvlDmgTorso = 0}
                if (lvlDmgTorso >= 4){lvlDmgTorso = 4}
                when(lvlDmgTorso){
                    0 -> {healthCounter.setImageResource(R.drawable.hp_center)
                        bodyPart.setImageResource(R.drawable.torso)}
                    1 -> {healthCounter.setImageResource(R.drawable.hp_center_dmg1)
                        bodyPart.setImageResource(R.drawable.torso)}
                    2 -> {healthCounter.setImageResource(R.drawable.hp_center_dmg2)
                        bodyPart.setImageResource(R.drawable.torso)}
                    3 -> {healthCounter.setImageResource(R.drawable.hp_center_dmg3)
                        healthCounter.visibility = View.VISIBLE
                        healthCounterCrippled.visibility = View.GONE
                        bodyPart.setImageResource(R.drawable.torso)}
                    4 -> {healthCounter.visibility = View.GONE
                        healthCounterCrippled.visibility = View.VISIBLE
                        bodyPart.setImageResource(R.drawable.torso_broken)}
                }
            }
            "leftArm" -> {
                val healthCounter = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftArmHp
                val healthCounterCrippled = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndPipboyLeftArmHpCrippled
                val bodyPart = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftArm
                when(bodyAction){
                    "heal" -> {if (lvlDmgLftArm >= 0){lvlDmgTotal--}; if (lvlDmgLftArm > 0){lvlDmgLftArm--; numStimpak--; hpLevel+=30; playStimpakAudio()}}
                    "damage" -> {if (lvlDmgLftArm >= 4){return}; if (lvlDmgLftArm < 4){lvlDmgLftArm++; lvlDmgTotal++; hpLevel-=30; mediaPlayerDamage?.start()};}
                }
                if (lvlDmgLftArm <= 0){lvlDmgLftArm = 0}
                if (lvlDmgLftArm >= 4){lvlDmgLftArm = 4}
                when(lvlDmgLftArm){
                    0 -> {healthCounter.setImageResource(R.drawable.hp_left)
                        bodyPart.setImageResource(R.drawable.man_arm_left)}
                    1 -> {healthCounter.setImageResource(R.drawable.hp_left_dmg1)
                        bodyPart.setImageResource(R.drawable.man_arm_left)}
                    2 -> {healthCounter.setImageResource(R.drawable.hp_left_dmg2)
                        bodyPart.setImageResource(R.drawable.man_arm_left)}
                    3 -> {healthCounter.setImageResource(R.drawable.hp_left_dmg3)
                        healthCounter.visibility = View.VISIBLE
                        healthCounterCrippled.visibility = View.GONE
                        bodyPart.setImageResource(R.drawable.man_arm_left)}
                    4 -> {healthCounter.visibility = View.GONE
                        healthCounterCrippled.visibility = View.VISIBLE
                        bodyPart.setImageResource(R.drawable.left_arm_broken)}
                }
            }
            "rightArm" -> {
                val healthCounter = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightArmHp
                val healthCounterCrippled = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndPipboyRightArmHpCrippled
                val bodyPart = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightArm
                when(bodyAction){
                    "heal" -> {if (lvlDmgRgtArm >= 0){lvlDmgTotal--}; if (lvlDmgRgtArm > 0){lvlDmgRgtArm--; numStimpak--; hpLevel+=30; playStimpakAudio()}}
                    "damage" -> {if (lvlDmgRgtArm >= 4){return}; if (lvlDmgRgtArm < 4){lvlDmgRgtArm++; lvlDmgTotal++; hpLevel-=30; mediaPlayerDamage?.start()};}
                }
                if (lvlDmgRgtArm <= 0){lvlDmgRgtArm = 0}
                if (lvlDmgRgtArm >= 4){lvlDmgRgtArm = 4}
                when(lvlDmgRgtArm){
                    0 -> {healthCounter.setImageResource(R.drawable.hp_right)
                        bodyPart.setImageResource(R.drawable.man_arm_right)}
                    1 -> {healthCounter.setImageResource(R.drawable.hp_right_dmg1)
                        bodyPart.setImageResource(R.drawable.man_arm_right)}
                    2 -> {healthCounter.setImageResource(R.drawable.hp_right_dmg2)
                        bodyPart.setImageResource(R.drawable.man_arm_right)}
                    3 -> {healthCounter.setImageResource(R.drawable.hp_right_dmg3)
                        healthCounter.visibility = View.VISIBLE
                        healthCounterCrippled.visibility = View.GONE
                        bodyPart.setImageResource(R.drawable.man_arm_right)}
                    4 -> {healthCounter.visibility = View.GONE
                        healthCounterCrippled.visibility = View.VISIBLE
                        bodyPart.setImageResource(R.drawable.right_arm_broken)}
                }
            }
            "leftLeg" -> {
                val healthCounter = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftLegHp
                val healthCounterCrippled = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndPipboyLeftLegHpCrippled
                val bodyPart = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftLeg
                when(bodyAction){
                    "heal" -> {if (lvlDmgLftLeg >= 0){lvlDmgTotal--}; if (lvlDmgLftLeg > 0){lvlDmgLftLeg--; numStimpak--; hpLevel+=30; playStimpakAudio()}}
                    "damage" -> {if (lvlDmgLftLeg >= 4){return}; if (lvlDmgLftLeg < 4){lvlDmgLftLeg++; lvlDmgTotal++; hpLevel-=30; mediaPlayerDamage?.start()};}
                }
                if (lvlDmgLftLeg <= 0){lvlDmgLftLeg = 0}
                if (lvlDmgLftLeg >= 4){lvlDmgLftLeg = 4}
                when(lvlDmgLftLeg){
                    0 -> {healthCounter.setImageResource(R.drawable.hp_left)
                        bodyPart.setImageResource(R.drawable.man_leg_left)}
                    1 -> {healthCounter.setImageResource(R.drawable.hp_left_dmg1)
                        bodyPart.setImageResource(R.drawable.man_leg_left)}
                    2 -> {healthCounter.setImageResource(R.drawable.hp_left_dmg2)
                        bodyPart.setImageResource(R.drawable.man_leg_left)}
                    3 -> {healthCounter.setImageResource(R.drawable.hp_left_dmg3)
                        healthCounter.visibility = View.VISIBLE
                        healthCounterCrippled.visibility = View.GONE
                        bodyPart.setImageResource(R.drawable.man_leg_left)}
                    4 -> {healthCounter.visibility = View.GONE
                        healthCounterCrippled.visibility = View.VISIBLE
                        bodyPart.setImageResource(R.drawable.left_leg_broken)}
                }
            }
            "rightLeg" -> {
                val healthCounter = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightLegHp
                val healthCounterCrippled = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndPipboyRightLegHpCrippled
                val bodyPart = bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightLeg
                when(bodyAction){
                    "heal" -> {if (lvlDmgRgtLeg >= 0){lvlDmgTotal--}; if (lvlDmgRgtLeg > 0){lvlDmgRgtLeg--; numStimpak--; hpLevel+=30; playStimpakAudio()}}
                    "damage" -> {if (lvlDmgRgtLeg >= 4){return}; if (lvlDmgRgtLeg < 4){lvlDmgRgtLeg++; lvlDmgTotal++; hpLevel-=30; mediaPlayerDamage?.start()};}
                }
                if (lvlDmgRgtLeg <= 0){lvlDmgRgtLeg = 0}
                if (lvlDmgRgtLeg >= 4){lvlDmgRgtLeg = 4}
                when(lvlDmgRgtLeg){
                    0 -> {healthCounter.setImageResource(R.drawable.hp_right)
                        bodyPart.setImageResource(R.drawable.man_leg_right)}
                    1 -> {healthCounter.setImageResource(R.drawable.hp_right_dmg1)
                        bodyPart.setImageResource(R.drawable.man_leg_right)}
                    2 -> {healthCounter.setImageResource(R.drawable.hp_right_dmg2)
                        bodyPart.setImageResource(R.drawable.man_leg_right)}
                    3 -> {healthCounter.setImageResource(R.drawable.hp_right_dmg3)
                        healthCounter.visibility = View.VISIBLE
                        healthCounterCrippled.visibility = View.GONE
                        bodyPart.setImageResource(R.drawable.man_leg_right)}
                    4 -> {healthCounter.visibility = View.GONE
                        healthCounterCrippled.visibility = View.VISIBLE
                        bodyPart.setImageResource(R.drawable.right_leg_broken)}
                }
            }
        }

        if (numStimpak <= 0){numStimpak = 10;}
        if (lvlDmgTotal <= 0){lvlDmgTotal = 0; bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(R.drawable.man_face)}
        if ((lvlDmgTotal >= 0) && (lvlDmgTotal <= 4)){bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(R.drawable.man_face)}
        if ((lvlDmgTotal >= 5) && (lvlDmgTotal <= 11)){bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(R.drawable.face_02)}
        if ((lvlDmgTotal >= 12) && (lvlDmgTotal <= 21)){bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(R.drawable.face_03)}
        if (lvlDmgTotal >= 23){lvlDmgTotal = 23; bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(R.drawable.face_04)}

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndStimpacksValue.text = "(${numStimpak})"

    }
    private fun playStimpakAudio(){
        val mediaPlayerStimpak = MediaPlayer.create(this, R.raw.stimpack)
        mediaPlayerStimpakList.add(mediaPlayerStimpak)
        mediaPlayerStimpak.start()
        mediaPlayerStimpak.setOnCompletionListener {
            it.release()
            mediaPlayerStimpakList.remove(it)
        }
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


    /***********************************************************************************************************
     * BATTERY MONITOR
     **********************************************************************************************************/
    private fun getBatteryPercent(): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = this.registerReceiver(null, ifilter)
        val level = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        return level
    }

    private fun getFactionReputation(curValue: Int): String {
        when(curValue){
            0 -> {return "Idolized"}
            1 -> {return "Liked"}
            2 -> {return "Accepted"}
            3 -> {return "Neutral"}
            4 -> {return "Shunned"}
            5 -> {return "Hated"}
            6 -> {return "Vilified"}
        }
        return "Neutral"
    }
    private fun setSelectedFaction(faction: String){
        selectedFACTION = faction
    }

    // Hide the system UI (notification bar and navigation bar)
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
    private fun STATSPerksSetup(recyclerView: RecyclerView){
        val selectedSTATSPerksString = sharedPreferences.getString("selectedSTATSPerksArray", "1")
        val selectedSTATSPerksArray: Array<String> = selectedSTATSPerksString!!.split(",").toTypedArray()

        // Filter the perk list based on the selected items
        val filteredPerksList = perks.filter { perk ->
            perk["id"] in selectedSTATSPerksArray
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = PerkAdapter(perks, selectedSTATSPerksArray, selected_button) { perk ->
            bindingMain.incLayoutTabStatsPerks.tvPerksDescriptionsText.text = (perk["desc"] ?: "No description available")
            bindingMain.incLayoutTabStatsPerks.imgPerksSelected.setImageResource(resources.getIdentifier(perk["icon"], "drawable", packageName))
            // Additional selection handling if necessary
        }

        adapter.updateData(filteredPerksList)

        recyclerView.adapter = adapter

        // Optional: Scroll to a pre-selected item or update UI as needed
        if (perks.isNotEmpty()) {
            val firstPerk = perks.find { it["id"] == selectedSTATSPerksArray[0] }
            firstPerk?.let {
                bindingMain.incLayoutTabStatsPerks.tvPerksDescriptionsText.text = (it["desc"] ?: "No description available")
                bindingMain.incLayoutTabStatsPerks.imgPerksSelected.setImageResource(resources.getIdentifier(it["icon"], "drawable", packageName))
            }
        }
    }
    /***********************************************************************************************************
     * SHARED PREFERENCES
     **********************************************************************************************************/
    private fun saveValues(etSettings1: String, etSettings2: Int, etSettings3: String, uiColourID: Int, etSettings5: Float, dateFormat: Int, showTutorial: Boolean, trueFullscreen: Boolean, gameYear: Int, playerRegion: String, languageID: Int) {
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
     * вообще, приложение ведёт себя как раньше, языком рулит система. `Configuration`
     * здесь — android.content.res, не org.osmdroid.config.Configuration (тот уже
     * импортирован под тем же именем выше по файлу, поэтому FQCN, а не import).
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
        val config = android.content.res.Configuration(newBase.resources.configuration)
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

        // Configure OSMDroid to not use cache
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = BuildConfig.APPLICATION_ID

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

        //Load saved size and position
        loadViewState()

        // Экран выбора режима (roadmap, "Видение приложения") — первое, что видит игрок
        setupModeSelectScreen()
        setupPipBoy2000Wizard()
        registerDebugCommandReceiver()

        //Disable all radioStations
        turnAllRadioOffNoVis()

        //Keep phone screen active
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        //Initialize RadioWave-View
        lineVisualizer = findViewById(R.id.radioWave)

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

        //MEDIA SETUP
        mediaPlayerCRF = MediaPlayer.create(applicationContext, R.raw.cnd_rad_eff)
        mediaPlayerDamage = MediaPlayer.create(applicationContext, R.raw.damage_sfx)
        mediaPlayerRadaway = MediaPlayer.create(applicationContext, R.raw.radaway)
        mediaPlayerRadX = MediaPlayer.create(applicationContext, R.raw.radx)
        mediaPlayerLightOn = MediaPlayer.create(applicationContext, R.raw.ui_pipboy_light_on)
        mediaPlayerLightOff = MediaPlayer.create(applicationContext, R.raw.ui_pipboy_light_off)
        galaxyRadioMediaPlayer = MediaPlayer.create(applicationContext, R.raw.galaxynewsradio)
        enclaveRadioMediaPlayer = MediaPlayer.create(applicationContext, R.raw.enclaveradio)
        newVegasRadioMediaPlayer = MediaPlayer.create(applicationContext, R.raw.newvegasradio)
        mediaPlayerBackGround = MediaPlayer.create(applicationContext, R.raw.background)
        val mediaPlayer = mediaPlayerBackGround
        if (mediaPlayer != null) {
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0.5f, 0.5f)
        }

        //BOTTOM BUTTON SETUP (DEFAULT STATUS)
        bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks, bindingMain.incLayoutTabStatsBottom.btnStatsGeneral)

        //(DEFAULT STATS SETUP)
        listStatsStatusCndRadEff.add(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnCnd)
        listStatsStatusCndRadEff.add(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnRad)
        listStatsStatusCndRadEff.add(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnEff)

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

        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBoomers)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBos)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralCaesarsLegion)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFollowersApocalypse)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFreeside)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGoodsprings)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGreatKhans)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNcr)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNovac)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPowderGangers)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPrimm)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralTheStrip)
        listStatsGeneralFactions.add(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralWhiteGloveSociety)

        listDataMisc.add(bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry1)
        listDataMisc.add(bindingMain.incLayoutTabDataMisc.layoutTabDataMiscEntry2)

        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioGnr)
        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioNvr)
        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclave)
        listDataRadios.add(bindingMain.incLayoutTabDataRadio.layoutTabRadioCustom)

        // SCREEN SCAN ANIMATION
        val translateAnimation: Animation = TranslateAnimation(0, 0.0f, 0, 0.0f, 1, -4.0f, 1, 8.0f)
        translateAnimation.duration = 9000
        translateAnimation.repeatCount = -1
        bindingMain.imgScanline.animation = translateAnimation
        bindingMain.imgScanline.alpha = 0.2f

        //Set Selected buttons by default
        setupSTATS()
        setupDATA()
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
        val thread: Thread = object : Thread() {
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
                            bindingMain.incLayoutTabItemsClock.tvTabRadioClockPopupHm.text = timeHHmm
                            bindingMain.incLayoutTabItemsClock.tvTabRadioClockPopupS.text = timess
                            bindingMain.incLayoutTabItemsClock.tvTabRadioClockPopupBattery.text = getBatteryPercent().toString()
                        }
                    }
                } catch (_: InterruptedException) {}
            }
        }
        thread.start()

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

        bindingMain.incLayoutFilterModification.btnFilterModificationClose.setOnClickListener{
            bindingMain.incLayoutFilterModification.root.visibility = View.GONE
            bindingMain.layoutStats.visibility = View.VISIBLE
            bindingMain.layoutItems.visibility = View.VISIBLE
            bindingMain.layoutData.visibility = View.VISIBLE
            enableDisableBottomButtons(true, listBottomButtons)
            enableDisableTopSwipe(true)
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationSelect.setOnClickListener{
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, perks, true)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationClear.setOnClickListener{
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, perks, false)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationFilter.setOnClickListener{
            val filterText = bindingMain.incLayoutFilterModification.etFilterModificationValue.text.toString()

            when(filteringMenu){
                "PERKS" -> filterList(perks, filterText)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationSave.setOnClickListener{
            when(filteringMenu){
                "PERKS" -> saveSelectedItems("selectedSTATSPerksArray")
            }
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
            bindingMain.incLayoutTabStatsGeneral.root.visibility = View.GONE
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnCnd.setOnClickListener {
            setSelectedCNDEFFRADButton(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnCnd, listStatsStatusCndRadEff)
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusRadContent.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusEffContent.root.visibility = View.GONE
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnRad.setOnClickListener {
            setSelectedCNDEFFRADButton(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnRad, listStatsStatusCndRadEff)
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusRadContent.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusEffContent.root.visibility = View.GONE
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnEff.setOnClickListener {
            setSelectedCNDEFFRADButton(bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.btnEff, listStatsStatusCndRadEff)
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusRadContent.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusEffContent.root.visibility = View.VISIBLE
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndName.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    statsCndPopupIsHolding = true
                    handler.postDelayed(longPressRunnable, 5000) // 5 seconds
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    statsCndPopupIsHolding = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.btnTabStatsCndPopupClose.setOnClickListener{
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.layoutTabStatusCndContent.visibility = View.VISIBLE
            enableDisableBottomButtons(true, listBottomButtons)
            enableDisableTopSwipe(true)
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyHeadHp.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDmgHead = true
                    handler.postDelayed(longPressRunnable, 500) // 500 msecond
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDmgHead = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyHead.setOnClickListener {
            playerCharacterUpdate("head", "heal")
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyTorsoHp.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDmgTorso = true
                    handler.postDelayed(longPressRunnable, 500) // 500 msecond
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDmgTorso = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyTorso.setOnClickListener {
            playerCharacterUpdate("torso", "heal")
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftArmHp.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDmgLftArm = true
                    handler.postDelayed(longPressRunnable, 500) // 500 msecond
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDmgLftArm = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftArm.setOnClickListener {
            playerCharacterUpdate("leftArm", "heal")
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightArmHp.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDmgRgtArm = true
                    handler.postDelayed(longPressRunnable, 500) // 500 msecond
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDmgRgtArm = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightArm.setOnClickListener {
            playerCharacterUpdate("rightArm", "heal")
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftLegHp.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDmgLftLeg = true
                    handler.postDelayed(longPressRunnable, 500) // 500 msecond
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDmgLftLeg = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyLeftLeg.setOnClickListener {
            playerCharacterUpdate("leftLeg", "heal")
        }

        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightLegHp.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDmgRgtLeg = true
                    handler.postDelayed(longPressRunnable, 500) // 500 msecond
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDmgRgtLeg = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
        bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyRightLeg.setOnClickListener {
            playerCharacterUpdate("rightLeg", "heal")
        }

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
            bindingMain.incLayoutTabStatsGeneral.root.visibility = View.GONE
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabStatsSpecialStrength.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabStatsSpecialStrength, listStatsSpecials, "STRENGTH")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_strength)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_strength_description)
        }
        bindingMain.incLayoutTabStatsSpecial.layoutTabStatsSpecialStrength.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSPECIAL == "STRENGTH"){
                        isSPECIAL_S = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIAL_S = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialPerception.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialPerception, listStatsSpecials, "PERCEPTION")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_perception)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_perception_description)
        }
        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialPerception.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSPECIAL == "PERCEPTION"){
                        isSPECIAL_P = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIAL_P = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialEndurance.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialEndurance, listStatsSpecials, "ENDURANCE")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_endurance)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_endurance_description)
        }
        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialEndurance.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSPECIAL == "ENDURANCE"){
                        isSPECIAL_E = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIAL_E = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialCharisma.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialCharisma, listStatsSpecials, "CHARISMA")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_charisma)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_charisma_description)
        }
        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialCharisma.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSPECIAL == "CHARISMA"){
                        isSPECIAL_C = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIAL_C = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialIntelligence.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialIntelligence, listStatsSpecials, "INTELLIGENCE")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_intelligence)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_intelligence_description)
        }
        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialIntelligence.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSPECIAL == "INTELLIGENCE"){
                        isSPECIAL_I = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIAL_I = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialAgility.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialAgility, listStatsSpecials, "AGILITY")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_agility)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_agility_description)
        }
        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialAgility.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSPECIAL == "AGILITY"){
                        isSPECIAL_A = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIAL_A = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialLuck.setOnClickListener{
            setSelectedSPECIALButton(bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialLuck, listStatsSpecials, "LUCK")
            bindingMain.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(R.drawable.special_luck)
            bindingMain.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(R.string.special_luck_description)
        }
        bindingMain.incLayoutTabStatsSpecial.layoutTabSpecialLuck.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSPECIAL == "LUCK"){
                        isSPECIAL_L = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIAL_L = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }



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
            bindingMain.incLayoutTabStatsGeneral.root.visibility = View.GONE
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBarter.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBarter, listStatsSkills, "BARTER")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_barter)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_barter_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBarter.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "BARTER"){
                        isSKILL_1 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_1 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBigGuns.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBigGuns, listStatsSkills, "BIGGUNS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_big_guns)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_big_guns_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsBigGuns.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "BIGGUNS"){
                        isSKILL_2 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_2 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsEnergyWeapons.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsEnergyWeapons, listStatsSkills, "ENERGYWEAPONS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_energy_weapons)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_energy_weapons_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsEnergyWeapons.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "ENERGYWEAPONS"){
                        isSKILL_3 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_3 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsExplosives.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsExplosives, listStatsSkills, "EXPLOSIVES")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_explosives)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_explosives_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsExplosives.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "EXPLOSIVES"){
                        isSKILL_4 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_4 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsLockpick.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsLockpick, listStatsSkills, "LOCKPICK")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_lockpick)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_lockpick_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsLockpick.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "LOCKPICK"){
                        isSKILL_5 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_5 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMedicine.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMedicine, listStatsSkills, "MEDICINE")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_medicine)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_medicine_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMedicine.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "MEDICINE"){
                        isSKILL_6 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_6 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMeleeWeapons.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMeleeWeapons, listStatsSkills, "MELEEWEAPONS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_melee_weapons)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_melee_weapons_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsMeleeWeapons.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "MELEEWEAPONS"){
                        isSKILL_7 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_7 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsRepair.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsRepair, listStatsSkills, "REPAIR")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_repair)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_repair_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsRepair.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "REPAIR"){
                        isSKILL_8 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_8 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsScience.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsScience, listStatsSkills, "SCIENCE")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_science)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_science_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsScience.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "SCIENCE"){
                        isSKILL_9 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_9 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSmallGuns.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSmallGuns, listStatsSkills, "SMALLGUNS")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_small_guns)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_small_guns_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSmallGuns.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "SMALLGUNS"){
                        isSKILL_10 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_10 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSneak.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSneak, listStatsSkills, "SNEAK")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_sneak)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_sneak_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSneak.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "SNEAK"){
                        isSKILL_11 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_11 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSpeech.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSpeech, listStatsSkills, "SPEECH")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_speech)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_speech_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsSpeech.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "SPEECH"){
                        isSKILL_12 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_12 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }
        
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsUnarmed.setOnClickListener{
            setSelectedSKILLSButton(bindingMain.incLayoutTabStatsSkills.layoutTabSkillsUnarmed, listStatsSkills, "UNARMED")
            bindingMain.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(R.drawable.skills_unarmed)
            bindingMain.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(R.string.skill_unarmed_description)
        }
        bindingMain.incLayoutTabStatsSkills.layoutTabSkillsUnarmed.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedSKILL == "UNARMED"){
                        isSKILL_13 = true
                        delayModify = 500L // Reset delay to 1 second when initially pressed
                        delayIterationCount = 0 // Reset iteration count
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILL_13 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }


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
            bindingMain.incLayoutTabStatsGeneral.root.visibility = View.GONE
            STATSPerksSetup(bindingMain.incLayoutTabStatsPerks.recyclerTabPerks)
        }
        bindingMain.incLayoutTabStatsBottom.btnStatsPerks.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(bindingMain.incLayoutTabStatsBottom.btnStatsPerks == selectedSubMenu){
                        perkModification = true
                        handler.postDelayed(longPressRunnable, 2000) // 2seconds
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    perkModification = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }


        /*
        ////////////////////////////////////////////////////////
        STATS - GENERAL MENU
        */
        bindingMain.incLayoutTabStatsBottom.btnStatsGeneral.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabStatsBottom.btnStatsGeneral, listBottomButtons)
            bindingMain.incLayoutTabStatsStatus.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSpecial.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsSkills.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsPerks.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsGeneral.root.visibility = View.VISIBLE
        }

        bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_1", 3)))

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBoomers.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBoomers, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_boomers)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_boomers)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_1", 3)))
            setSelectedFaction("BOOMERS")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBoomers.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "BOOMERS"){
                        isFACTION_1 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_1 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBos.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBos, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_brotherhood_of_steel)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_bos)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_2", 3)))
            setSelectedFaction("BOS")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralBos.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "BOS"){
                        isFACTION_2 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_2 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralCaesarsLegion.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralCaesarsLegion, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_caesars_legion)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_caesars_legion)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_3", 3)))
            setSelectedFaction("CAESARSLEGION")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralCaesarsLegion.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "CAESARSLEGION"){
                        isFACTION_3 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_3 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFollowersApocalypse.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFollowersApocalypse, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_followers_apocalypse)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_followers_apocalypse)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_4", 3)))
            setSelectedFaction("FOLLOWERSAPOCALYPSE")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFollowersApocalypse.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "FOLLOWERSAPOCALYPSE"){
                        isFACTION_4 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_4 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFreeside.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFreeside, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_freeside)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_freeside)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_5", 3)))
            setSelectedFaction("FREESIDE")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralFreeside.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "FREESIDE"){
                        isFACTION_5 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_5 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGoodsprings.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGoodsprings, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_goodsprings)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_goodsprings)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_6", 3)))
            setSelectedFaction("GOODSPRINGS")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGoodsprings.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "GOODSPRINGS"){
                        isFACTION_6 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_6 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGreatKhans.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGreatKhans, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_great_khans)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_great_khans)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_7", 3)))
            setSelectedFaction("GREATKHANS")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralGreatKhans.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "GREATKHANS"){
                        isFACTION_7 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_7 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNcr.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNcr, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_ncr)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_ncr)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_8", 3)))
            setSelectedFaction("NCR")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNcr.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "NCR"){
                        isFACTION_8 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_8 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNovac.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNovac, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_novac)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_novac)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_9", 3)))
            setSelectedFaction("NOVAC")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralNovac.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "NOVAC"){
                        isFACTION_9 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_9 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPowderGangers.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPowderGangers, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_powder_ganger)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_powder_gangers)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_10", 3)))
            setSelectedFaction("POWDERGANGERS")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPowderGangers.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "POWDERGANGERS"){
                        isFACTION_10 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_10 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPrimm.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPrimm, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_primm)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_primm)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_11", 3)))
            setSelectedFaction("PRIMM")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralPrimm.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "PRIMM"){
                        isFACTION_11 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_11 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralTheStrip.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralTheStrip, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_the_strip)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_the_strip)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_12", 3)))
            setSelectedFaction("STRIP")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralTheStrip.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "STRIP"){
                        isFACTION_12 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_12 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralWhiteGloveSociety.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralWhiteGloveSociety, listStatsGeneralFactions)
            bindingMain.incLayoutTabStatsGeneral.imgStatsGeneralFactionSelected.setImageResource(R.drawable.reputations_white_glove_society)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionName.setText(R.string.stats_general_faction_white_glove_society)
            bindingMain.incLayoutTabStatsGeneral.tvStatsGeneralFactionReputation.setText(getFactionReputation(sharedPreferences.getInt("FACTION_13", 3)))
            setSelectedFaction("WHITEGLOVE")
        }
        bindingMain.incLayoutTabStatsGeneral.layoutTabGeneralWhiteGloveSociety.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(selectedFACTION == "WHITEGLOVE"){
                        isFACTION_13 = true
                        handler.postDelayed(longPressRunnable, 500) // 500 msecond
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFACTION_13 = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }


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
        }

        networkChangeReceiver = NetworkChangeReceiver(this)
        checkINETPermissions()

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
                    turnRadioOn(galaxyRadioMediaPlayer!!)
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.VISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.INVISIBLE
                } else {
                    turnRadioOff(galaxyRadioMediaPlayer!!)
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
                    turnRadioOn(enclaveRadioMediaPlayer!!)
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.VISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.INVISIBLE
                } else {
                    turnRadioOff(enclaveRadioMediaPlayer!!)
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
                    turnRadioOn(newVegasRadioMediaPlayer!!)
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.VISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.INVISIBLE
                } else {
                    turnRadioOff(newVegasRadioMediaPlayer!!)
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
                        loadMp3Files()
                        playRandomTrack()
                        if(customMP3FilesFound){
                            turnRadioOn(customRadioMediaPlayer!!)
                        }
                    } else {
                        requestCustomMediaPermission()
                    }
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioGnrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioEnclaveSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioNvrSelector.visibility = View.INVISIBLE
                    bindingMain.incLayoutTabDataRadio.layoutTabRadioCustomSelector.visibility = View.VISIBLE
                } else {
                    if(customMP3FilesFound){
                        turnRadioOff(customRadioMediaPlayer!!)
                    }
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
        val editSettingsYear = bindingMain.incLayoutSettingsGlobal.etSettingsYearValue //GameYear

        saveButtonSettings.setOnClickListener{
            lifecycleScope.launch(Dispatchers.IO) {
                saveValues(editSettings1.text.toString(), editSettings2.text.toString().toInt(), editSettings3.text.toString(), UIColour_Selector, editSettings5.text.toString().toFloat(), dateFormat_Selector, editSettings6.isChecked(), editSettings7.isChecked(), editSettingsYear.text.toString().toInt(), editSettingsRegion.text.toString(), languageSelector)
            }
            turnAllRadioOff()
            sendBLEText("STATS")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            finishAffinity() // Close all previous activities
            startActivity(intent)
        }

            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndName.text = sharedPreferences.getString(playerName_SPKey, "Player")
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusCndNameLevelValue.text = (sharedPreferences.getInt(playerLevel_SPKey, 1)).toString()
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

    }

    override fun onDestroy() {
        super.onDestroy()
        turnAllRadioOff()
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