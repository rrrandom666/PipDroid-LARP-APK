package com.malto4.pipdroid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import android.util.DisplayMetrics
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
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
    private var UIColour_Selector = 0
    private var dateFormat_Selector = 0
    private var selected_button = R.drawable.button_selected_green
    private var selectedDateFormat = "MM.dd.yy, HH:mm"
    private var trueFullscreen = false



    /***********************************************************************************************************
     * LIST DEFINITIONS
     **********************************************************************************************************/
    private var listBottomButtons = ArrayList<Button>()
    private var listStatsStatusCndRadEff = ArrayList<Button>()
    private var listStatsSpecials = ArrayList<ConstraintLayout>()
    private var listStatsSkills = ArrayList<ConstraintLayout>()
    private var listStatsGeneralFactions = ArrayList<ConstraintLayout>()
    private var listItemsWeapons = ArrayList<ConstraintLayout>()
    private var listItemsApparel = ArrayList<ConstraintLayout>()
    private var listDataQuests= ArrayList<ConstraintLayout>()
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
    }

    /***********************************************************************************************************
     * WORLD MAP
     **********************************************************************************************************/
    private lateinit var worldMapPhotoView: PhotoView
    private lateinit var worldMapPOIContainer: FrameLayout
    private lateinit var worldMapPOIs:  MutableList<worldMapPointOfInterest>
    private var currentVisibleTextView: TextView? = null
    private var lastClickedWorldMapPoi: worldMapPointOfInterest? = null
    private val minScaleForClickableIcons = 2.2f  // Adjust this value as needed

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
     * TUTORIAL
     **********************************************************************************************************/
    private var tutorialPage = 0
    private var showTutorialBool = true

    /***********************************************************************************************************
     * FILTER MODIFICATION
     **********************************************************************************************************/
    private lateinit var filterFrame: FrameLayout
    private lateinit var filteringMenu: String
    private var selectedFilterSTATSPerks = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterITEMSWeapons = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterITEMSApparel = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterITEMSAid = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterITEMSMisc = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterITEMSAmmo = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterDATAQuests = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterDATAMisc = mutableSetOf<String>()  // Set to keep track of selected item IDs

    /***********************************************************************************************************
     * LongButtonPresses - EasterEgg + FLASHLIGHT + PlayerDamage
     **********************************************************************************************************/
    private var statsCndPopupIsHolding = false
    private var menuSwipeEnabled = true
    private var perkModification = false
    private var weaponModification = false
    private var apparelModification = false
    private var aidModification = false
    private var imiscModification = false
    private var ammoModification = false
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
            if (weaponModification){
                filteringMenu = "WEAPONS"
                listEntries(filterFrame, weapons)
                bindingMain.incLayoutFilterModification.root.visibility = View.VISIBLE
                bindingMain.layoutStats.visibility = View.GONE
                bindingMain.layoutItems.visibility = View.GONE
                bindingMain.layoutData.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
                weaponModification = false
            }
            if (apparelModification){
                filteringMenu = "APPAREL"
                listEntries(filterFrame, apparels)
                bindingMain.incLayoutFilterModification.root.visibility = View.VISIBLE
                bindingMain.layoutStats.visibility = View.GONE
                bindingMain.layoutItems.visibility = View.GONE
                bindingMain.layoutData.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
                apparelModification = false
            }
            if (aidModification){
                filteringMenu = "AID"
                listEntries(filterFrame, aids)
                bindingMain.incLayoutFilterModification.root.visibility = View.VISIBLE
                bindingMain.layoutStats.visibility = View.GONE
                bindingMain.layoutItems.visibility = View.GONE
                bindingMain.layoutData.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
                aidModification = false
            }
            if (imiscModification){
                filteringMenu = "IMISC"
                listEntries(filterFrame, imiscs)
                bindingMain.incLayoutFilterModification.root.visibility = View.VISIBLE
                bindingMain.layoutStats.visibility = View.GONE
                bindingMain.layoutItems.visibility = View.GONE
                bindingMain.layoutData.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
                imiscModification = false
            }
            if (ammoModification){
                filteringMenu = "AMMO"
                listEntries(filterFrame, ammos)
                bindingMain.incLayoutFilterModification.root.visibility = View.VISIBLE
                bindingMain.layoutStats.visibility = View.GONE
                bindingMain.layoutItems.visibility = View.GONE
                bindingMain.layoutData.visibility = View.GONE
                enableDisableBottomButtons(false, listBottomButtons)
                enableDisableTopSwipe(false)
                ammoModification = false
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
    private fun onMenuSwipeLeft() {
        when(curMenu){
            "STATS" -> {
                menuChangeBLE("ITEMS")
            }
            "ITEMS" -> {
                menuChangeBLE("DATA")
            }
            "DATA" -> {
                menuChangeBLE("STATS")
            }
        }
    }
    private fun onMenuSwipeRight() {
        when(curMenu){
            "STATS" -> {
                menuChangeBLE("DATA")
            }
            "ITEMS" -> {
                menuChangeBLE("STATS")
            }
            "DATA" -> {
                menuChangeBLE("ITEMS")
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
                bindingMain.incLayoutTabDataLocalMap.tvPermissionsCheckResult.visibility = View.VISIBLE
                bindingMain.incLayoutTabDataLocalMap.localMapView.visibility = View.GONE
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
        paramMediaPlayer?.setVolume(0.0f, 0.0f)
        if (checkAudioPermission()) {
            lineVisualizer.visibility = View.GONE
        } else {
            requestAudioPermission()
        }
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
        if (checkAudioPermission()) {
            lineVisualizer.visibility = View.GONE
        } else {
            requestAudioPermission()
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
        bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.textViewBLUETOOTHConnection.text = status
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
        // Текущий выбранный режим — контур без заливки, остальные два — обычная
        // сплошная заливка активной кнопки.
        setWizardButtonState(ms.btnModeSelectPhone, selected = mode == PipBoyMode.PHONE)
        setWizardButtonState(ms.btnModeSelectPipboy2000, selected = mode == PipBoyMode.PIPBOY_2000)
        setWizardButtonState(ms.btnModeSelectPipboy3000, selected = mode == PipBoyMode.PIPBOY_3000)
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
        val modeButtons = listOf(ms.btnModeSelectPhone, ms.btnModeSelectPipboy2000, ms.btnModeSelectPipboy3000)

        // Текст описания — тоже акцентом текущей темы, не жёстко зелёным (смысл темы —
        // красить весь экран, не только кнопки, см. currentWizardAccentColor()).
        ms.tvModeSelectDescription.setTextColor(currentWizardAccentColor())

        showModeDescription(PipBoyMode.PHONE)
        modeButtons.forEach { button ->
            button.setOnClickListener {
                playNewTabSelectAudio()
                showModeDescription(
                    when (button) {
                        ms.btnModeSelectPhone -> PipBoyMode.PHONE
                        ms.btnModeSelectPipboy2000 -> PipBoyMode.PIPBOY_2000
                        else -> PipBoyMode.PIPBOY_3000
                    }
                )
            }
        }
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
            }
            PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000 -> {
                // PIPBOY_3000 пока ведёт себя как PIPBOY_2000 — заглушка на будущее, своя
                // конфигурация внешнего железа появится отдельно (roadmap, видение).
                applyPowerState(false) // безопасный дефолт OFF, пока не пришёл первый POWER
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
        bindingMain.incLayoutTabDataLocalMap.tvPermissionsCheckResult.visibility = View.VISIBLE
        bindingMain.incLayoutTabDataLocalMap.localMapView.visibility = View.GONE
    }
    private fun loadLocalMap() {

        localMapOSMDroid = bindingMain.incLayoutTabDataLocalMap.localMapView
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

        bindingMain.incLayoutTabDataLocalMap.tvPermissionsCheckResult.visibility = View.GONE
    }

    /***********************************************************************************************************
     * WORLD MAP
     **********************************************************************************************************/
    private fun updateWorldMapPOIsVisibilityAndPosition() {
        val matrix = worldMapPhotoView.imageMatrix
        val values = FloatArray(9)
        matrix.getValues(values)

        val scale = values[Matrix.MSCALE_X]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        val viewWidth = worldMapPhotoView.width
        val viewHeight = worldMapPhotoView.height

        val visibleRect = RectF(
            -transX / scale,
            -transY / scale,
            (viewWidth - transX) / scale,
            (viewHeight - transY) / scale
        )

        for (poi in worldMapPOIs) {
            // Calculate the exact position in the current view matrix
            val poiX = (poi.x * scale) + transX
            val poiY = (poi.y * scale) + transY

            poi.iconView?.apply {
                // Position the POI in the photoView's coordinate system
                x = poiX
                y = poiY
                // Scale the POI according to the photoView's current scale
                scaleX = scale
                scaleY = scale
                pivotX = 0f
                pivotY = 0f
            }

            poi.textView?.apply {
                x = poiX - ((width * 0.5f) * scale)
                y = poiY - ((poi.iconView?.height) ?: 0) * scale
                scaleX = scale
                scaleY = scale
                pivotX = 0f
                pivotY = 0f
            }

            if (visibleRect.contains(poi.x, poi.y)) {
                poi.iconView?.visibility = View.VISIBLE
            } else {
                poi.iconView?.visibility = View.INVISIBLE
                poi.textView?.visibility = View.GONE
            }
        }
    }
    private fun fallout3WorldMapLocations(){
        // Use these websites to help calculate locations - https://fallout.fandom.com/wiki/Fallout_3_world_map + http://www.gamemapscout.com/fallout3_interactive.html
        // Each sub-square is around 60x60, and starts at 0x0.
        worldMapPhotoView = bindingMain.incLayoutTabDataWorldMap.photoViewWorldmap
        worldMapPOIContainer = bindingMain.incLayoutTabDataWorldMap.poiContainerWorldmap
        worldMapPhotoView.setImageResource(R.drawable.worldmap_f3)
        worldMapPhotoView.maximumScale = 6.0f

        worldMapPOIContainer.removeAllViews()
        worldMapPOIs.clear()
        val customScaling = sharedPreferences.getFloat(customMapScaling_SPKey, 1f)
        worldMapPOIs.add(worldMapPointOfInterest(360f * customScaling, 960f * customScaling, "Abandoned Car Fort", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1860f * customScaling, 1320f * customScaling, "Agatha's House", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(2190f * customScaling, 2860f * customScaling, "Alexandria Arms", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2910f * customScaling, 2790f * customScaling, "Anacostia Crossing Station", R.drawable.icon_map_droplet))
        worldMapPOIs.add(worldMapPointOfInterest(2400f * customScaling, 2240f * customScaling, "Anchorage Memorial", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(1545f * customScaling, 2820f * customScaling, "Andale", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(2810f * customScaling, 1090f * customScaling, "AntAgonizer's Lair", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1140f * customScaling, 1410f * customScaling, "Arefu", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(2220f * customScaling, 2295f * customScaling, "Arlington Cemetery North", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2210f * customScaling, 2445f * customScaling, "Arlington Cemetery South", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2275f * customScaling, 2940f * customScaling, "Arlington Library", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(2115f * customScaling, 1625f * customScaling, "Bethesda Ruins", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1555f * customScaling, 1590f * customScaling, "Big Town", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(420f * customScaling, 600f * customScaling, "Broadcast Tower KB5", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(240f * customScaling, 1260f * customScaling, "Broadcast Tower KT8", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1540f * customScaling, 360f * customScaling, "Broadcast Tower LP8", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2850f * customScaling, 1150f * customScaling, "Canterbury Commons", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(2810f * customScaling, 2415f * customScaling, "Capitol Building", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(520f * customScaling, 2035f * customScaling, "Charnel House", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2700f * customScaling, 360f * customScaling, "Chaste Acres Dairy Farm", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2650f * customScaling, 2070f * customScaling, "Chevy Chase East", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2580f * customScaling, 2040f * customScaling, "Chevy Chase North", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2280f * customScaling, 1860f * customScaling, "Chryslus Building", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2310f * customScaling, 2610f * customScaling, "Citadel", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(1200f * customScaling, 2825f * customScaling, "Cliffside Cavern", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1800f * customScaling, 250f * customScaling, "Clifftop Shacks", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2755f * customScaling, 1500f * customScaling, "Corveg Factory", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(510f * customScaling, 610f * customScaling, "Deathclaw Sanctuary", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(660f * customScaling, 660f * customScaling, "Dickerson Tabernacle Chapel", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(960f * customScaling, 715f * customScaling, "Drowned Devil's Crossing", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2475f * customScaling, 2280f * customScaling, "Dukov's Place", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(240f * customScaling, 2850f * customScaling, "Dunwich Building", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2520f * customScaling, 2160f * customScaling, "Dupont East", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(2500f * customScaling, 2110f * customScaling, "Dupont North-East", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2460f * customScaling, 2180f * customScaling, "Dupont Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2420f * customScaling, 2140f * customScaling, "Dupont West", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(480f * customScaling, 1500f * customScaling, "Everglow National Campground", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(700f * customScaling, 2230f * customScaling, "Evergreen Mills", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(180f * customScaling, 2760f * customScaling, "F. Scott Key Trail & Campground", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(790f * customScaling, 880f * customScaling, "Faded Pomp Estates", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1555f * customScaling, 2535f * customScaling, "Fairfax Ruins", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2240f * customScaling, 2590f * customScaling, "Falls Church East", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2100f * customScaling, 2590f * customScaling, "Falls Church Metro", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2170f * customScaling, 2570f * customScaling, "Falls Church North", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2220f * customScaling, 2035f * customScaling, "Farragut West Metro Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(550f * customScaling, 1180f * customScaling, "Five Axles Rest Stop", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2040f * customScaling, 2890f * customScaling, "Flooded Metro", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(780f * customScaling, 240f * customScaling, "Ford Constantine", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1340f * customScaling, 1560f * customScaling, "Fordham Flash Memorial Field", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(720f * customScaling, 1905f * customScaling, "Fort Bannister", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1410f * customScaling, 2560f * customScaling, "Fort Independence", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2640f * customScaling, 1980f * customScaling, "Friendship Heights", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2580f * customScaling, 2100f * customScaling, "Galaxy News Radio", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(2650f * customScaling, 2305f * customScaling, "Georgetown East", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2580f * customScaling, 2210f * customScaling, "Georgetown North", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2575f * customScaling, 2340f * customScaling, "Georgetown South", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2475f * customScaling, 2175f * customScaling, "Georgetown West", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(1700f * customScaling, 1030f * customScaling, "Germantown Police Headquarters", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(240f * customScaling, 2460f * customScaling, "Girdershade", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(2105f * customScaling, 2350f * customScaling, "Grayditch", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(2180f * customScaling, 540f * customScaling, "Greener Pastures Disposal Site", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2620f * customScaling, 660f * customScaling, "Grisly Diner", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1560f * customScaling, 1275f * customScaling, "Hallowed Moors Cemetery", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1380f * customScaling, 1390f * customScaling, "Hamilton's Hideaway", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2235f * customScaling, 2670f * customScaling, "Hubris Comics", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2610f * customScaling, 2580f * customScaling, "Irradiated Metro", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(690f * customScaling, 1590f * customScaling, "Jalbert Brothers Waste Disposal", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2610f * customScaling, 2850f * customScaling, "Jefferson Memorial", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(480f * customScaling, 2520f * customScaling, "Jocko's Pop & Gas Stop", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1190f * customScaling, 1980f * customScaling, "Jury Street Metro Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(1260f * customScaling, 1670f * customScaling, "Kaelyn's Bed & Breakfast", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(2820f * customScaling, 2600f * customScaling, "L'enfant Plaza", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2915f * customScaling, 2700f * customScaling, "L'enfant South", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2540f * customScaling, 2410f * customScaling, "Lincoln Memorial", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(250f * customScaling, 1680f * customScaling, "Little Lamplight", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(2245f * customScaling, 2440f * customScaling, "Mama Dolce's", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2105f * customScaling, 2460f * customScaling, "Marigold Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2270f * customScaling, 2745f * customScaling, "Mason District South", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(1010f * customScaling, 580f * customScaling, "Mason Dixon Salvage", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(800f * customScaling, 1160f * customScaling, "MDPL Mass Relay Station", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(180f * customScaling, 300f * customScaling, "MDPL-05 Power Station", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(1690f * customScaling, 790f * customScaling, "MDPL-13 Power Station", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(2880f * customScaling, 360f * customScaling, "MDPL-16 Power Station", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(1200f * customScaling, 250f * customScaling, "MDPL-21 Power Station", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1720f * customScaling, 2165f * customScaling, "Megaton", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1740f * customScaling, 1380f * customScaling, "Meresti Trainyard", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2925f * customScaling, 2310f * customScaling, "Metro Central", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2045f * customScaling, 1000f * customScaling, "Minefield", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1440f * customScaling, 480f * customScaling, "Montgomery County Reservoir", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(1410f * customScaling, 1475f * customScaling, "Moonbeam Outdoor Cinema", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(540f * customScaling, 740f * customScaling, "Mount Mabel Campground", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2680f * customScaling, 2350f * customScaling, "Museum Of History", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2740f * customScaling, 2460f * customScaling, "Museum Of Technology", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2890f * customScaling, 1980f * customScaling, "National Guard Depot", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1200f * customScaling, 1260f * customScaling, "Northwest Seneca Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(1710f * customScaling, 2895f * customScaling, "Nuka-Cola Plant", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(1620f * customScaling, 120f * customScaling, "Oasis", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2400f * customScaling, 240f * customScaling, "Old Olney", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2735f * customScaling, 2210f * customScaling, "Our Lady Of Hope Hospital", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1260f * customScaling, 850f * customScaling, "Paradise Falls", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2940f * customScaling, 2370f * customScaling, "Pennsylvania Avenue East", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2850f * customScaling, 2265f * customScaling, "Pennsylvania Avenue North", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2810f * customScaling, 2310f * customScaling, "Pennsylvania Avenue North-West", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2880f * customScaling, 2340f * customScaling, "Pennsylvania Avenue South", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2280f * customScaling, 2280f * customScaling, "Platz Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(3000f * customScaling, 2460f * customScaling, "Ranger Compound", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(110f * customScaling, 110f * customScaling, "Raven Rock", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1665f * customScaling, 620f * customScaling, "Reclining Groves Resort Homes", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1910f * customScaling, 2700f * customScaling, "Red Racer Factory", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2700f * customScaling, 600f * customScaling, "Relay Tower KX-B8-11", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2910f * customScaling, 2850f * customScaling, "Rivet City", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(970f * customScaling, 2705f * customScaling, "RobCo Facility", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2815f * customScaling, 1260f * customScaling, "Robot Repair Center", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2760f * customScaling, 1915f * customScaling, "Rock Creek Caverns", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(520f * customScaling, 1350f * customScaling, "Rockbreaker's Last Gas", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(820f * customScaling, 1000f * customScaling, "Roosevelt Academy", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1020f * customScaling, 300f * customScaling, "SatCom Array NN-03d", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(480f * customScaling, 300f * customScaling, "SatCom Array NW-05a", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(120f * customScaling, 600f * customScaling, "SatCom Array NW-07c", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2110f * customScaling, 1260f * customScaling, "Scrapyard", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2895f * customScaling, 2465f * customScaling, "Seward Square North", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2815f * customScaling, 2370f * customScaling, "Seward Square North-West", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2975f * customScaling, 2520f * customScaling, "Seward Square South-East", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2015f * customScaling, 2270f * customScaling, "Sewer Waystation", R.drawable.icon_map_droplet))
        worldMapPOIs.add(worldMapPointOfInterest(280f * customScaling, 1065f * customScaling, "Shalebridge", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(460f * customScaling, 2240f * customScaling, "Smith Casey's Garage", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1680f * customScaling, 2035f * customScaling, "Springvale", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1740f * customScaling, 1905f * customScaling, "Springvale School", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2775f * customScaling, 2140f * customScaling, "Statesman Hotel", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(2085f * customScaling, 2100f * customScaling, "Super-Duper Mart", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(3000f * customScaling, 2045f * customScaling, "Takoma Industrial Factory", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2970f * customScaling, 2110f * customScaling, "Takoma Park", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2580f * customScaling, 880f * customScaling, "Temple Of The Union", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(840f * customScaling, 2860f * customScaling, "Tenpenny Tower", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2445f * customScaling, 2280f * customScaling, "Tepid Sweres", R.drawable.icon_map_droplet))
        worldMapPOIs.add(worldMapPointOfInterest(2760f * customScaling, 2345f * customScaling, "The Mall North-East", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2600f * customScaling, 2400f * customScaling, "The Mall North-West", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2575f * customScaling, 2460f * customScaling, "The Mall South-West", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2730f * customScaling, 2400f * customScaling, "The National Archives", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1320f * customScaling, 2880f * customScaling, "The Overlook Drive-In", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2930f * customScaling, 240f * customScaling, "The Republic of Dave", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(880f * customScaling, 800f * customScaling, "The Silver Lining Drive-In", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2670f * customScaling, 2405f * customScaling, "The Washington Monument", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(970f * customScaling, 1630f * customScaling, "VAPL-58 Power Station", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(465f * customScaling, 2620f * customScaling, "VAPL-66 Power Station", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(900f * customScaling, 2515f * customScaling, "VAPL-84 Power Station", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1585f * customScaling, 2040f * customScaling, "Vault 101", R.drawable.icon_map_vault_101))
        worldMapPOIs.add(worldMapPointOfInterest(1275f * customScaling, 1755f * customScaling, "Vault 106", R.drawable.icon_map_vault_106))
        worldMapPOIs.add(worldMapPointOfInterest(2880f * customScaling, 1440f * customScaling, "Vault 108", R.drawable.icon_map_vault_108))
        worldMapPOIs.add(worldMapPointOfInterest(130f * customScaling, 1440f * customScaling, "Vault 87", R.drawable.icon_map_vault_87))
        worldMapPOIs.add(worldMapPointOfInterest(2280f * customScaling, 180f * customScaling, "Vault 92", R.drawable.icon_map_vault_92))
        worldMapPOIs.add(worldMapPointOfInterest(2845f * customScaling, 2040f * customScaling, "Vault-Tec Headquarters", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(2790f * customScaling, 2080f * customScaling, "Vernon Square East", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2790f * customScaling, 2020f * customScaling, "Vernon Square North", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(2705f * customScaling, 2130f * customScaling, "Vernon Square Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(740f * customScaling, 2940f * customScaling, "Warrington Station", R.drawable.icon_map_metro))
        worldMapPOIs.add(worldMapPointOfInterest(650f * customScaling, 2850f * customScaling, "Warrington Trainyard", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2390f * customScaling, 1360f * customScaling, "Wheaton Armory", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2735f * customScaling, 2275f * customScaling, "White House", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(2225f * customScaling, 2220f * customScaling, "Wilhelm's Wharf", R.drawable.icon_map_droplet))
        worldMapPOIs.add(worldMapPointOfInterest(780f * customScaling, 710f * customScaling, "WKML Broadcast Station", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(140f * customScaling, 2040f * customScaling, "Yao Guai Tunnels", R.drawable.icon_map_cave))

        for (poi in worldMapPOIs) {
            val iconView = ImageView(this).apply {
                setImageResource(poi.iconRes)
                @Suppress("DEPRECATION")
                setBackgroundColor(resources.getColor(R.color.white))
                @Suppress("DEPRECATION")
                setColorFilter(resources.getColor(R.color.black))
                layoutParams = FrameLayout.LayoutParams(64, 64) // Set the icon size
            }
            worldMapPOIContainer.addView(iconView)
            poi.iconView = iconView

            val textView = TextView(this).apply {
                @Suppress("DEPRECATION")
                setBackgroundColor(resources.getColor(R.color.black))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                text = poi.name
                visibility = View.GONE // Initially hidden
            }
            worldMapPOIContainer.addView(textView)
            poi.textView = textView

            iconView.setOnClickListener {
                handleIconClick(poi)
            }
        }

        updateWorldMapPOIsVisibilityAndPosition()

        worldMapPhotoView.setOnMatrixChangeListener {
            updateWorldMapPOIsVisibilityAndPosition()
        }

    }
    private fun falloutNVWorldMapLocations(){
        // https://fallout.fandom.com/wiki/Fallout:_New_Vegas_world_map
        // Each sub-square is around 54x54, and starts at 0x0.
        worldMapPhotoView = bindingMain.incLayoutTabDataWorldMap.photoViewWorldmap
        worldMapPOIContainer = bindingMain.incLayoutTabDataWorldMap.poiContainerWorldmap
        worldMapPhotoView.setImageResource(R.drawable.worldmap_fnv)
        worldMapPhotoView.maximumScale = 6.0f

        worldMapPOIContainer.removeAllViews()
        worldMapPOIs.clear()
        val customScaling = sharedPreferences.getFloat(customMapScaling_SPKey, 1f)
        worldMapPOIs.add(worldMapPointOfInterest(1990f * customScaling, 1404f * customScaling, "188 Trading Post", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2330f * customScaling, 1944f * customScaling, "Abandoned BoS Bunker", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1674f * customScaling, 976f * customScaling, "Aerotech Office Park", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1516f * customScaling, 1196f * customScaling, "Allied Technologies Offices", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1528f * customScaling, 1256f * customScaling, "Ant Mound", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2457f * customScaling, 830f * customScaling, "Bitter Springs", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(2380f * customScaling, 972f * customScaling, "Bitter Springs Recreation Area", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1728f * customScaling, 1594f * customScaling, "Black Mountain", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1782f * customScaling, 1700f * customScaling, "Black Rock Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2268f * customScaling, 736f * customScaling, "Bloodborne Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2434f * customScaling, 2770f * customScaling, "Blue Paradise Vacation Rentals", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1080f * customScaling, 1324f * customScaling, "Bonnie Springs", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2214f * customScaling, 1334f * customScaling, "Boulder Beach Campground", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2206f * customScaling, 1490f * customScaling, "Boulder City", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1432f * customScaling, 2835f * customScaling, "Bradley's Shack", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1160f * customScaling, 838f * customScaling, "Brewer's Beer Bootlegging", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1885f * customScaling, 2430f * customScaling, "Broc Flower Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1080f * customScaling, 324f * customScaling, "Brooks Tumbleweed Ranch", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(2156f * customScaling, 756f * customScaling, "Brotherhood of Steel Safehouse", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1560f * customScaling, 2806f * customScaling, "Caesar's Legion Safehouse", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1026f * customScaling, 2306f * customScaling, "California Sunset Drive-in", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(2350f * customScaling, 1042f * customScaling, "Callville Bay", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2268f * customScaling, 1830f * customScaling, "Camp Forlorn Hope", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1998f * customScaling, 1080f * customScaling, "Camp Golf", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2590f * customScaling, 1020f * customScaling, "Camp Guardian", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1510f * customScaling, 970f * customScaling, "Camp McCarran", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1944f * customScaling, 2662f * customScaling, "Camp Searchlight", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1836f * customScaling, 972f * customScaling, "Cannibal Johnson's Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(920f * customScaling, 2165f * customScaling, "Canyon Wreckage", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2214f * customScaling, 970f * customScaling, "Cap Counterfeiting Shack", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1625f * customScaling, 1184f * customScaling, "Cassidy Caravan Wreckage", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2192f * customScaling, 1647f * customScaling, "Cazador Nest", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1080f * customScaling, 1080f * customScaling, "Chance's Map", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(568f * customScaling, 620f * customScaling, "Charleston Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1998f * customScaling, 2187f * customScaling, "Clark Field", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2376f * customScaling, 2376f * customScaling, "Cliffside Prospector Camp", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2320f * customScaling, 2592f * customScaling, "Cottonwood Cove", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2214f * customScaling, 2866f * customScaling, "Cottonwood Crater", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2306f * customScaling, 2673f * customScaling, "Cottonwood Overlook", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1400f * customScaling, 2430f * customScaling, "Coyote Den", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2054f * customScaling, 2510f * customScaling, "Coyote Mines", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2324f * customScaling, 914f * customScaling, "Coyote Tail Ridge", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2430f * customScaling, 1134f * customScaling, "Crashed B-29", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1728f * customScaling, 2916f * customScaling, "Crashed Vertibird", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1242f * customScaling, 2916f * customScaling, "Crescent Canyon East", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1026f * customScaling, 2792f * customScaling, "Crescent Canyon West", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1620f * customScaling, 650f * customScaling, "Crimson Caravan Company", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1540f * customScaling, 2296f * customScaling, "Dead Wind Cavern", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1944f * customScaling, 1296f * customScaling, "Deserted Shack", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1755f * customScaling, 782f * customScaling, "Durable Dunn's Sacked Caravan", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1728f * customScaling, 864f * customScaling, "East Pump Station", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2000f * customScaling, 1616f * customScaling, "El Dorado Dry Lake", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2106f * customScaling, 1722f * customScaling, "El Dorado Gas & Service", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1836f * customScaling, 1512f * customScaling, "El Dorado Substation", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1456f * customScaling, 976f * customScaling, "El Rey Motel", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1378f * customScaling, 2215f * customScaling, "Emergency Service Railyard", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1836f * customScaling, 524f * customScaling, "Fields' Shack", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2160f * customScaling, 2810f * customScaling, "Fire Root Cavern", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2188f * customScaling, 1080f * customScaling, "Fisherman's Pride Shack", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1836f * customScaling, 1190f * customScaling, "Follower's Outpost", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(976f * customScaling, 514f * customScaling, "Follower's Safehouse", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1568f * customScaling, 704f * customScaling, "Freeside's East Gate", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1564f * customScaling, 598f * customScaling, "Freeside's North Gate", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1894f * customScaling, 1890f * customScaling, "Gibson Scrap Yard", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(976f * customScaling, 1678f * customScaling, "Goodsprings", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1080f * customScaling, 1782f * customScaling, "Goodsprings Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1054f * customScaling, 1594f * customScaling, "Goodsprings Cemetery", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1028f * customScaling, 1890f * customScaling, "Goodsprings Source", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1190f * customScaling, 1540f * customScaling, "Great Khan Encampment", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1134f * customScaling, 486f * customScaling, "Griffin Wares Sacked Caravan", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1730f * customScaling, 1130f * customScaling, "Grub n' Gulp Rest Stop", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2646f * customScaling, 1024f * customScaling, "Guardian Peak", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1560f * customScaling, 754f * customScaling, "Gun Runners", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2052f * customScaling, 866f * customScaling, "Gypsum Train Yard", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1460f * customScaling, 590f * customScaling, "H&H Tools Factory", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(1565f * customScaling, 2418f * customScaling, "Harper's Shack", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1890f * customScaling, 1780f * customScaling, "HELIOS One", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1460f * customScaling, 2485f * customScaling, "Hidden Supply Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1512f * customScaling, 1755f * customScaling, "Hidden Valley", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1886f * customScaling, 2210f * customScaling, "Highway 95 Viper's Encampment", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2436f * customScaling, 1460f * customScaling, "Hoover Dam", R.drawable.icon_map_monument))
        worldMapPOIs.add(worldMapPointOfInterest(1216f * customScaling, 502f * customScaling, "Horowitz Farmstead", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1460f * customScaling, 1310f * customScaling, "Hunter's Farm", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1113f * customScaling, 2458f * customScaling, "Ivanpah Dry Lake", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1150f * customScaling, 2540f * customScaling, "Ivanpah Race Track", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1276f * customScaling, 2430f * customScaling, "Jack Rabbit Springs", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(594f * customScaling, 756f * customScaling, "Jacobstown", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1138f * customScaling, 1890f * customScaling, "Jean Sky Diving", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1512f * customScaling, 1404f * customScaling, "Junction 15 Railway Station", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1955f * customScaling, 1161f * customScaling, "Lake Las Vegas", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2480f * customScaling, 1172f * customScaling, "Lake Mead Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2727f * customScaling, 1523f * customScaling, "Legate's Camp", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1730f * customScaling, 2430f * customScaling, "Legion Raid Camp", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(972f * customScaling, 2008f * customScaling, "Lone Wolf Radio", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2214f * customScaling, 2296f * customScaling, "Lucky Jim Mine", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1080f * customScaling, 1460f * customScaling, "Makeshift Great Khan Camp", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1756f * customScaling, 2754f * customScaling, "Matthews Animal Husbandry Farm", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(853f * customScaling, 2527f * customScaling, "Mesquite Mountains Camp Site", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(810f * customScaling, 2324f * customScaling, "Mesquite Mountains Crater", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1274f * customScaling, 596f * customScaling, "Miguel's Pawn Shop", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1312f * customScaling, 2758f * customScaling, "Mojave Drive-in", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(853f * customScaling, 2727f * customScaling, "Mojave Outpost", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1728f * customScaling, 594f * customScaling, "Mole Rat Ranch", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1300f * customScaling, 756f * customScaling, "Monte Carlo Suites", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1022f * customScaling, 2700f * customScaling, "Morning Star Cavern", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2050f * customScaling, 1240f * customScaling, "Mountain Shadows Campground", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1346f * customScaling, 1976f * customScaling, "NCR Correctional Facility", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1647f * customScaling, 1431f * customScaling, "NCR Ranger Safehouse", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1566f * customScaling, 875f * customScaling, "NCR Sharecropper Farms", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1566f * customScaling, 1593f * customScaling, "Neil's Shack", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2014f * customScaling, 460f * customScaling, "Nellis Air Force Base", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(2160f * customScaling, 221f * customScaling, "Nellis Array", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2052f * customScaling, 270f * customScaling, "Nellis Hangars", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2210f * customScaling, 2050f * customScaling, "Nelson", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1026f * customScaling, 2434f * customScaling, "Nevada Highway Patrol Station", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1674f * customScaling, 648f * customScaling, "New Vegas Medical Clinic", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1377f * customScaling, 1036f * customScaling, "New Vegas Steel", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1352f * customScaling, 2642f * customScaling, "Nipton", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1161f * customScaling, 2646f * customScaling, "Nipton Road Pit Stop", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(972f * customScaling, 2565f * customScaling, "Nipton Road Reststop", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1161f * customScaling, 1161f * customScaling, "Nopah Cave", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1523f * customScaling, 546f * customScaling, "North Vegas Square", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1458f * customScaling, 384f * customScaling, "Northern Passage", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1890f * customScaling, 2030f * customScaling, "Novac", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1620f * customScaling, 2862f * customScaling, "Old Nuclear Test Site", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1188f * customScaling, 1026f * customScaling, "Poseidon Gas Station", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1458f * customScaling, 2079f * customScaling, "Powder Ganger Camp East", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1339f * customScaling, 1810f * customScaling, "Powder Ganger Camp North", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1296f * customScaling, 2079f * customScaling, "Powder Ganger Camp South", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1136f * customScaling, 1948f * customScaling, "Powder Ganger Camp West", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1118f * customScaling, 2241f * customScaling, "Primm", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1495f * customScaling, 2214f * customScaling, "Primm Pass", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1307f * customScaling, 1544f * customScaling, "Quarry Junction", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1728f * customScaling, 2646f * customScaling, "Raided Farmstead", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1755f * customScaling, 1292f * customScaling, "Ranger Morales' corpse", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2122f * customScaling, 1307f * customScaling, "Ranger Station Alpha", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2532f * customScaling, 810f * customScaling, "Ranger Station Bravo", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1728f * customScaling, 2210f * customScaling, "Ranger Station Charlie", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2320f * customScaling, 1674f * customScaling, "Ranger Station Delta", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(2214f * customScaling, 2430f * customScaling, "Ranger Station Echo", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(988f * customScaling, 814f * customScaling, "Ranger Station Foxtrot", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1955f * customScaling, 691f * customScaling, "Raul's Shack", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(914f * customScaling, 1026f * customScaling, "Red Rock Canyon", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(892f * customScaling, 945f * customScaling, "Red Rock Drug Lab", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(907f * customScaling, 783f * customScaling, "Remnants Bunker", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1712f * customScaling, 1296f * customScaling, "REPCONN Headquarters", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1593f * customScaling, 1998f * customScaling, "REPCONN Test Site", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(886f * customScaling, 486f * customScaling, "Ruby Hill Mine", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1350f * customScaling, 1188f * customScaling, "Samson Rock Crushing Plant", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2268f * customScaling, 1160f * customScaling, "Scavenger Platform", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1647f * customScaling, 1836f * customScaling, "Scorpion Gulch", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2008f * customScaling, 2862f * customScaling, "Searchlight Airport", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2074f * customScaling, 2738f * customScaling, "Searchlight East Gold Mine", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(2046f * customScaling, 2565f * customScaling, "Searchlight North Gold Mine", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(853f * customScaling, 648f * customScaling, "Silver Peak Mine", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1372f * customScaling, 1620f * customScaling, "Sloan", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2270f * customScaling, 2754f * customScaling, "Smith Mesa Prospector Camp", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2176f * customScaling, 2592f * customScaling, "Sniper's Nest", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2009f * customScaling, 2376f * customScaling, "Snyder Prospector Camp", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(972f * customScaling, 1242f * customScaling, "Spring Mt. Ranch State Park", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1430f * customScaling, 670f * customScaling, "South Cistern", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(1350f * customScaling, 1026f * customScaling, "South Vegas Ruins East Entrance", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1242f * customScaling, 1004f * customScaling, "South Vegas Ruins West Entrance", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(2106f * customScaling, 1890f * customScaling, "Southern Nevada Wind Farm", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1296f * customScaling, 864f * customScaling, "Sunset Sarsaparilla Headquarters", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(2295f * customScaling, 2171f * customScaling, "Techatticup Mine", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1454f * customScaling, 1150f * customScaling, "The Basincreek Building", R.drawable.icon_map_office))
        worldMapPOIs.add(worldMapPointOfInterest(1134f * customScaling, 1744f * customScaling, "The Devil's Gullet", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2700f * customScaling, 756f * customScaling, "The Devil's Throat", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(2592f * customScaling, 1283f * customScaling, "The Fort", R.drawable.icon_map_star))
        worldMapPOIs.add(worldMapPointOfInterest(1296f * customScaling, 2338f * customScaling, "The Prospector's Den", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1460f * customScaling, 740f * customScaling, "The Strip North Gate", R.drawable.icon_map_city))
        worldMapPOIs.add(worldMapPointOfInterest(1307f * customScaling, 650f * customScaling, "The Thorn", R.drawable.icon_map_droplet))
        worldMapPOIs.add(worldMapPointOfInterest(945f * customScaling, 1404f * customScaling, "Tribal Village", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(2035f * customScaling, 2052f * customScaling, "Toxic Dump Site", R.drawable.icon_map_ruins_town))
        worldMapPOIs.add(worldMapPointOfInterest(1307f * customScaling, 1080f * customScaling, "Vault 3", R.drawable.icon_map_vault_3))
        worldMapPOIs.add(worldMapPointOfInterest(1944f * customScaling, 1485f * customScaling, "Vault 11", R.drawable.icon_map_vault_11))
        worldMapPOIs.add(worldMapPointOfInterest(1242f * customScaling, 1339f * customScaling, "Vault 19", R.drawable.icon_map_vault_19))
        worldMapPOIs.add(worldMapPointOfInterest(1042f * customScaling, 648f * customScaling, "Vault 22", R.drawable.icon_map_vault_22))
        worldMapPOIs.add(worldMapPointOfInterest(1890f * customScaling, 914f * customScaling, "Vault 34", R.drawable.icon_map_vault_34))
        worldMapPOIs.add(worldMapPointOfInterest(1540f * customScaling, 2402f * customScaling, "Walking Box Cavern", R.drawable.icon_map_cave))
        worldMapPOIs.add(worldMapPointOfInterest(1430f * customScaling, 1080f * customScaling, "West Pump Station", R.drawable.icon_map_factory))
        worldMapPOIs.add(worldMapPointOfInterest(1310f * customScaling, 1050f * customScaling, "Westside South Entrance", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1236f * customScaling, 598f * customScaling, "Westside West Entrance", R.drawable.icon_map_ruins_urban))
        worldMapPOIs.add(worldMapPointOfInterest(1312f * customScaling, 1306f * customScaling, "Whittaker Farmstead", R.drawable.icon_map_settlement))
        worldMapPOIs.add(worldMapPointOfInterest(1620f * customScaling, 2565f * customScaling, "Wolfhorn Ranch", R.drawable.icon_map_encampment))
        worldMapPOIs.add(worldMapPointOfInterest(1954f * customScaling, 2284f * customScaling, "Wrecked Highwayman", R.drawable.icon_map_natural_landmark))
        worldMapPOIs.add(worldMapPointOfInterest(1188f * customScaling, 1674f * customScaling, "Yangtze Memorial", R.drawable.icon_map_monument))

        for (poi in worldMapPOIs) {
            val iconView = ImageView(this).apply {
                setImageResource(poi.iconRes)
                @Suppress("DEPRECATION")
                setBackgroundColor(resources.getColor(R.color.white))
                @Suppress("DEPRECATION")
                setColorFilter(resources.getColor(R.color.black))
                layoutParams = FrameLayout.LayoutParams(64, 64) // Set the icon size
            }
            worldMapPOIContainer.addView(iconView)
            poi.iconView = iconView

            val textView = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                @Suppress("DEPRECATION")
                setBackgroundColor(resources.getColor(R.color.black))
                text = poi.name
                visibility = View.GONE // Initially hidden
            }
            worldMapPOIContainer.addView(textView)
            poi.textView = textView

            iconView.setOnClickListener {
                handleIconClick(poi)
            }
        }
        updateWorldMapPOIsVisibilityAndPosition()

        worldMapPhotoView.setOnMatrixChangeListener {
            updateWorldMapPOIsVisibilityAndPosition()
        }

    }
    private fun handleIconClick(clickedPoi: worldMapPointOfInterest) {
        val scale = worldMapPhotoView.scale
        if (scale >= minScaleForClickableIcons) {
            if (currentVisibleTextView == clickedPoi.textView) {
                // Toggle visibility if the same POI is clicked again
                currentVisibleTextView?.visibility = View.GONE
                currentVisibleTextView = null
                lastClickedWorldMapPoi = null
            } else {
                // Hide the currently visible text view, if any
                currentVisibleTextView?.visibility = View.GONE

                // Show the text view of the clicked POI
                clickedPoi.textView?.visibility = View.VISIBLE
                clickedPoi.textView?.bringToFront()
                currentVisibleTextView = clickedPoi.textView
                lastClickedWorldMapPoi = clickedPoi
            }
        }
    }
    private data class worldMapPointOfInterest(
        val x: Float,
        val y: Float,
        val name: String,
        val iconRes: Int,
        var iconView: ImageView? = null,
        var textView: TextView? = null
    )


    /***********************************************************************************************************
     * INTERFACE CHANGES
     **********************************************************************************************************/
    fun menuChange(menu: String){
        when(menu){
            "STATS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks, bindingMain.incLayoutTabStatsBottom.btnStatsGeneral)
                menuOptionClicked("STATS")
                curMenu = "STATS"
            }
            "ITEMS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabItemsBottom.btnItemsWeapons, bindingMain.incLayoutTabItemsBottom.btnItemsApparel, bindingMain.incLayoutTabItemsBottom.btnItemsAid, bindingMain.incLayoutTabItemsBottom.btnItemsMisc, bindingMain.incLayoutTabItemsBottom.btnItemsAmmo)
                menuOptionClicked("ITEMS")
                ITEMSWeaponsSetup(bindingMain.incLayoutTabItemsWeapons.recyclerTabWeapons)
                curMenu = "ITEMS"
            }
            "DATA" -> {
                bottomButtonsModify(bindingMain.incLayoutTabDataBottom.btnDataWorldmap, bindingMain.incLayoutTabDataBottom.btnDataLocalmap, bindingMain.incLayoutTabDataBottom.btnDataQuests, bindingMain.incLayoutTabDataBottom.btnDataMisc)
                menuOptionClicked("DATA")
                curMenu = "DATA"
            }
        }
    }
    /**
     * État-машина экрана PipBoy (протокол, раздел 3.1): OFF (чёрный экран) <-> ON.
     * ESP32 — хозяин состояния, применяем как есть, не тумблерим локально. Стартовое
     * состояние экрана — OFF (view_power_off видим по умолчанию в разметке), пока не
     * пришёл первый POWER от ESP32.
     *
     * Анимация здесь — намеренно минимальная заглушка (fade), не хореография загрузки
     * настоящего Pip-Boy — это отдельная задача для дизайна позже.
     */
    private fun applyPowerState(on: Boolean) {
        val overlay = bindingMain.viewPowerOff
        if (on) {
            overlay.animate().cancel()
            overlay.alpha = 1f
            overlay.visibility = View.VISIBLE
            overlay.animate().alpha(0f).setDuration(400).withEndAction {
                overlay.visibility = View.GONE
            }.start()
            // Мастер настройки PipBoy 2000/3000 больше не нужен — POWER реально пришёл.
            bindingMain.incLayoutPipboy2000Wizard.root.visibility = View.GONE
            // Пока шли Permissions/подсказка про POWER, окно было временно fullscreen (не
            // персистентно, см. showWizardStep/applyTemporaryFullScreenLayout) — теперь
            // применяем реально настроенную на шаге DISPLAY AREA область для игры.
            loadViewState()
        } else {
            overlay.animate().cancel()
            overlay.alpha = 1f
            overlay.visibility = View.VISIBLE
        }
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
    private fun itemsMenuRoot(): List<MenuNode> {
        val bottom = bindingMain.incLayoutTabItemsBottom
        return listOf(
            MenuNode("WEAPONS") { bottom.btnItemsWeapons.performClick() },
            MenuNode("APPAREL") { bottom.btnItemsApparel.performClick() },
            MenuNode("AID") { bottom.btnItemsAid.performClick() },
            MenuNode("MISC") { bottom.btnItemsMisc.performClick() },
            MenuNode("AMMO") { bottom.btnItemsAmmo.performClick() },
        )
    }
    private fun dataMenuRoot(): List<MenuNode> {
        val bottom = bindingMain.incLayoutTabDataBottom
        return listOf(
            MenuNode("WORLDMAP") { bottom.btnDataWorldmap.performClick() },
            MenuNode("LOCALMAP") { bottom.btnDataLocalmap.performClick() },
            MenuNode("QUESTS") { bottom.btnDataQuests.performClick() },
            MenuNode("MISC") { bottom.btnDataMisc.performClick() },
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
            "ENCBTN" -> menuNavigator.activateSelected()
            "ENC" -> menuNavigator.moveCursor(value?.toIntOrNull() ?: 0)
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
        when(menu){
            "STATS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks, bindingMain.incLayoutTabStatsBottom.btnStatsGeneral)
                menuOptionClickedBLE("STATS")
                curMenu = "STATS"
            }
            "ITEMS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabItemsBottom.btnItemsWeapons, bindingMain.incLayoutTabItemsBottom.btnItemsApparel, bindingMain.incLayoutTabItemsBottom.btnItemsAid, bindingMain.incLayoutTabItemsBottom.btnItemsMisc, bindingMain.incLayoutTabItemsBottom.btnItemsAmmo)
                menuOptionClickedBLE("ITEMS")
                ITEMSWeaponsSetup(bindingMain.incLayoutTabItemsWeapons.recyclerTabWeapons)
                curMenu = "ITEMS"
            }
            "DATA" -> {
                bottomButtonsModify(bindingMain.incLayoutTabDataBottom.btnDataWorldmap, bindingMain.incLayoutTabDataBottom.btnDataLocalmap, bindingMain.incLayoutTabDataBottom.btnDataQuests, bindingMain.incLayoutTabDataBottom.btnDataMisc)
                menuOptionClickedBLE("DATA")
                curMenu = "DATA"
            }
            "RADIO" -> {
                // У RADIO нет второго уровня (roadmap, "Новая шапка + единый Settings",
                // п.4) — listBottomButtons пуст, enableDisableBottomButtons() отработает
                // на пустом списке без ошибок.
                bottomButtonsModify()
                menuOptionClickedBLE("RADIO")
                curMenu = "RADIO"
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
        applyProgressDrawable(Colour)
        applyScrollBar(scrollbarDrawable)
    }
    private fun applyBackgroundResource(Colour: Int) {
        // Apply background to relevant views
        val backgrounds = listOf(
            bindingMain.incLayoutSettingsGlobal.layoutTabSettings,
            bindingMain.incLayoutFilterModification.layoutFilterModification,
            bindingMain.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.layoutTabSettingsBluetooth,
            bindingMain.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.layoutTabStatsCndPopup,
            bindingMain.incLayoutTabDataRadio.incLayoutTabClock.layoutTabClock
            // Add other views as necessary
        )
        val backgroundsmaps = listOf(
            bindingMain.incLayoutTabDataWorldMap.btnWorldmapF3,
            bindingMain.incLayoutTabDataWorldMap.btnWorldmapFNV
        )
        var backgroundRes = R.drawable.settings_menu_background_green
        var backgroundMap = R.drawable.world_map_background_green
        when(Colour){
            0 -> {backgroundRes = R.drawable.settings_menu_background_green
                backgroundMap = R.drawable.world_map_background_green
                selected_button = R.drawable.button_selected_green}
            1 -> {backgroundRes = R.drawable.settings_menu_background_amber
                backgroundMap = R.drawable.world_map_background_amber
                selected_button = R.drawable.button_selected_amber}
            2 -> {backgroundRes = R.drawable.settings_menu_background_white
                backgroundMap = R.drawable.world_map_background_white
                selected_button = R.drawable.button_selected_white}
            3 -> {backgroundRes = R.drawable.settings_menu_background_blue
                backgroundMap = R.drawable.world_map_background_blue
                selected_button = R.drawable.button_selected_blue}
        }
        backgrounds.forEach { it.setBackgroundResource(backgroundRes) }
        backgroundsmaps.forEach { it.setBackgroundResource(backgroundMap)}
    }
    private fun applyTextColor(Colour: Int){
        // Apply text colors to relevant radio buttons and checkboxes
        val primaryTextViews = listOf(
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat1,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat2,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat3,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat4,
            bindingMain.incLayoutSettingsGlobal.rbSettingsDateformat5,
            bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome,
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings,
            bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings
            // Add other radio buttons and text views as needed
        )
        val secondaryTextViews = listOf(
            bindingMain.incLayoutTabDataQuests.btnDataQuestsEntry1,
            bindingMain.incLayoutTabDataQuests.btnDataQuestsEntry2,
            bindingMain.incLayoutTabDataQuests.btnDataQuestsEntry3,
            bindingMain.incLayoutTabDataQuests.btnDataQuestsEntry4
            // Add other secondary buttons or text views
        )

        var primaryColor = R.color.themeGreen
        var secondaryColor = R.color.themeGreenCND
        when(Colour){
            0 -> {primaryColor = R.color.themeGreen
                secondaryColor = R.color.themeGreenCND}
            1 -> {primaryColor = R.color.themeAmber
                secondaryColor = R.color.themeAmberCND}
            2 -> {primaryColor = R.color.themeWhite
                secondaryColor = R.color.themeWhiteCND}
            3 -> {primaryColor = R.color.themeBlue
                secondaryColor = R.color.themeBlueCND}
        }

        @Suppress("ResourceAsColor")
        primaryTextViews.forEach { it.setTextColor(resources.getColor(primaryColor)) }
        lineVisualizer.setColor(getResources().getColor(primaryColor))
        @Suppress("ResourceAsColor")
        secondaryTextViews.forEach { it.setTextColor(resources.getColor(secondaryColor)) }
        bindingMain.incLayoutTabDataWorldMap.photoViewWorldmap.setColorFilter(getResources().getColor(secondaryColor))
    }
    private fun applyProgressDrawable(Colour: Int){
        // Apply progress drawable to relevant progress bars
        val progressBars = listOf(
            bindingMain.incLayoutTabItemsWeapons.pbItemsWeaponsCndValue,
            bindingMain.incLayoutTabItemsApparel.pbItemsApparelCndValue
            // Add other progress bars as necessary
        )

        var progressBarDrawable = getDrawableCompat(this, R.drawable.progressbar_tab_items_weapons_cnd_green)
        when(Colour){
            0 -> {progressBarDrawable = getDrawableCompat(this, R.drawable.progressbar_tab_items_weapons_cnd_green)}
            1 -> {progressBarDrawable = getDrawableCompat(this, R.drawable.progressbar_tab_items_weapons_cnd_amber)}
            2 -> {progressBarDrawable = getDrawableCompat(this, R.drawable.progressbar_tab_items_weapons_cnd_white)}
            3 -> {progressBarDrawable = getDrawableCompat(this, R.drawable.progressbar_tab_items_weapons_cnd_blue)}
        }
        progressBars.forEach { it.progressDrawable = progressBarDrawable }
    }
    private fun applyScrollBar(scrollbarDrawable: Drawable?){
        scrollbarDrawable?.let {
            // Apply scrollbar drawable to relevant scroll views
            val scrollViews = listOf(
                bindingMain.incLayoutTabStatsSpecial.scrollTabSpecial,
                bindingMain.incLayoutTabStatsSkills.scrollTabSkills,
                bindingMain.incLayoutTabStatsPerks.recyclerTabPerks,
                bindingMain.incLayoutTabStatsGeneral.scrollTabGeneral,
                bindingMain.incLayoutTabItemsWeapons.recyclerTabWeapons,
                bindingMain.incLayoutTabItemsApparel.recyclerTabApparel,
                bindingMain.incLayoutTabItemsAid.recyclerTabAid,
                bindingMain.incLayoutTabItemsMisc.recyclerTabItemsMisc,
                bindingMain.incLayoutTabItemsAmmo.recyclerTabAmmo,
                bindingMain.incLayoutTabDataQuests.scrollTabDataQuests,
                bindingMain.incLayoutTabDataQuests.scrollTabDataQuestsText,
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
        findViewById<ConstraintLayout>(R.id.layout_tab_data_quests_entry6).setBackgroundResource(selected_button)
        findViewById<ConstraintLayout>(R.id.layout_tab_data_misc_entry1).setBackgroundResource(selected_button)
    }
    /**
     * Нижняя контекстная панель (roadmap, "Новая шапка + единый Settings", п.3) — раньше
     * эта функция переключала три копии старой шапки (имя раздела + данные вперемешку),
     * теперь переключает панель живых данных: у STATS свой инстанс (LVL/HP/AP/XP), у
     * ITEMS/DATA — общий (дата/время, идентичное содержимое).
     */
    private fun setupTitleBar(menu: String){
        findViewById<ConstraintLayout>(R.id.inc_layout_header_bottom_stats).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_header_bottom_datetime).visibility = View.GONE
        if (menu == "STATS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_header_bottom_stats).visibility = View.VISIBLE
        } else if (menu == "ITEMS" || menu == "DATA" || menu == "RADIO"){
            findViewById<ConstraintLayout>(R.id.inc_layout_header_bottom_datetime).visibility = View.VISIBLE
        }
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

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_weapons).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_apparel).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_aid).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_misc).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_items_misc_main).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_ammo).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_local_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_world_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_quests).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.GONE
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
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_misc).visibility = View.VISIBLE
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

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_weapons).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_apparel).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_aid).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_misc).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_items_misc_main).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_ammo).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_local_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_world_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_quests).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.GONE
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
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_weapons).visibility = View.VISIBLE
        } else if (menu == "DATA"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_local_map).visibility = View.VISIBLE
        } else if (menu == "RADIO"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_radio).visibility = View.VISIBLE
        }
    }
    private fun setupBottomBar(menu: String){
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_bottom).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_bottom).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_bottom).visibility = View.GONE
        if (menu == "STATS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_bottom).visibility = View.VISIBLE
        } else if (menu == "ITEMS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_bottom).visibility = View.VISIBLE
        } else if (menu == "DATA"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_bottom).visibility = View.VISIBLE
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
        if (menu == "STATS"){
            findViewById<Button>(R.id.btn_stats_status).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_stats_special).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_stats_skills).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_stats_perks).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_stats_general).setBackgroundResource(selected_button)
        } else if (menu == "ITEMS"){
            findViewById<Button>(R.id.btn_items_weapons).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_items_apparel).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_items_aid).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_items_misc).setBackgroundResource(selected_button)
            findViewById<Button>(R.id.btn_items_ammo).setBackgroundResource(R.drawable.button_unselected)
        } else if (menu == "DATA"){
            findViewById<Button>(R.id.btn_data_localmap).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_data_worldmap).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_data_quests).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_data_misc).setBackgroundResource(selected_button)
        }
        topLevelButtonsModify(menu)
        setupTitleBar(menu)
        setupMainContent(menu)
        setupBottomBar(menu)
        enableDisableBottomButtons(false, listBottomButtons)
        enableDisableTopSwipe(false)
        sendBLEText(menu)
    }
    private fun menuOptionClickedBLE(menu: String){
        mediaPlayerCRF?.start()
        if (menu == "STATS"){
            findViewById<Button>(R.id.btn_stats_status).setBackgroundResource(selected_button)
            findViewById<Button>(R.id.btn_stats_special).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_stats_skills).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_stats_perks).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_stats_general).setBackgroundResource(R.drawable.button_unselected)
        } else if (menu == "ITEMS"){
            findViewById<Button>(R.id.btn_items_weapons).setBackgroundResource(selected_button)
            findViewById<Button>(R.id.btn_items_apparel).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_items_aid).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_items_misc).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_items_ammo).setBackgroundResource(R.drawable.button_unselected)
        } else if (menu == "DATA"){
            findViewById<Button>(R.id.btn_data_localmap).setBackgroundResource(selected_button)
            findViewById<Button>(R.id.btn_data_worldmap).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_data_quests).setBackgroundResource(R.drawable.button_unselected)
            findViewById<Button>(R.id.btn_data_misc).setBackgroundResource(R.drawable.button_unselected)
        }
        topLevelButtonsModify(menu)
        setupTitleBar(menu)
        setupMainContentBLE(menu)
        setupBottomBar(menu)
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

        bindingMain.incLayoutHeaderBottomStats.tvTitleHpValue.text = "${hpLevel}/720"
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
                typeface = TypefaceCache.getMonofontoTypeface(context) // Set the loaded typeface
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
                "WEAPONS" -> {
                    checkBox.isChecked = selectedFilterITEMSWeapons.contains(itemId)
                    // Listen for CheckBox state changes to update selectedItems
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedFilterITEMSWeapons.add(itemId)  // Add item ID to selected set
                        } else {
                            selectedFilterITEMSWeapons.remove(itemId)  // Remove item ID from selected set
                        }
                    }
                }
                "APPAREL" -> {
                    checkBox.isChecked = selectedFilterITEMSApparel.contains(itemId)
                    // Listen for CheckBox state changes to update selectedItems
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedFilterITEMSApparel.add(itemId)  // Add item ID to selected set
                        } else {
                            selectedFilterITEMSApparel.remove(itemId)  // Remove item ID from selected set
                        }
                    }
                }
                "AID" -> {
                    checkBox.isChecked = selectedFilterITEMSAid.contains(itemId)
                    // Listen for CheckBox state changes to update selectedItems
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedFilterITEMSAid.add(itemId)  // Add item ID to selected set
                        } else {
                            selectedFilterITEMSAid.remove(itemId)  // Remove item ID from selected set
                        }
                    }
                }
                "IMISC" -> {
                    checkBox.isChecked = selectedFilterITEMSMisc.contains(itemId)
                    // Listen for CheckBox state changes to update selectedItems
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedFilterITEMSMisc.add(itemId)  // Add item ID to selected set
                        } else {
                            selectedFilterITEMSMisc.remove(itemId)  // Remove item ID from selected set
                        }
                    }
                }
                "AMMO" -> {
                    checkBox.isChecked = selectedFilterITEMSAmmo.contains(itemId)
                    // Listen for CheckBox state changes to update selectedItems
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedFilterITEMSAmmo.add(itemId)  // Add item ID to selected set
                        } else {
                            selectedFilterITEMSAmmo.remove(itemId)  // Remove item ID from selected set
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
                                "WEAPONS" -> {
                                    selectedFilterITEMSWeapons.add(itemId)
                                }
                                "APPAREL" -> {
                                    selectedFilterITEMSApparel.add(itemId)
                                }
                                "AID" -> {
                                    selectedFilterITEMSAid.add(itemId)
                                }
                                "IMISC" -> {
                                    selectedFilterITEMSMisc.add(itemId)
                                }
                                "AMMO" -> {
                                    selectedFilterITEMSAmmo.add(itemId)
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
                                "WEAPONS" -> {
                                    selectedFilterITEMSWeapons.remove(itemId)
                                }
                                "APPAREL" -> {
                                    selectedFilterITEMSApparel.remove(itemId)
                                }
                                "AID" -> {
                                    selectedFilterITEMSAid.remove(itemId)
                                }
                                "IMISC" -> {
                                    selectedFilterITEMSMisc.remove(itemId)
                                }
                                "AMMO" -> {
                                    selectedFilterITEMSAmmo.remove(itemId)
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
            "selectedITEMSWeaponsArray" -> {
                selectedFilterITEMSWeapons = selectedFilterITEMSWeapons.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterITEMSWeapons.joinToString(",")
            }
            "selectedITEMSApparelArray" -> {
                selectedFilterITEMSApparel = selectedFilterITEMSApparel.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterITEMSApparel.joinToString(",")
            }
            "selectedITEMSAidArray" -> {
                selectedFilterITEMSAid = selectedFilterITEMSAid.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterITEMSAid.joinToString(",")
            }
            "selectedITEMSMiscArray" -> {
                selectedFilterITEMSMisc = selectedFilterITEMSMisc.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterITEMSMisc.joinToString(",")
            }
            "selectedITEMSAmmoArray" -> {
                selectedFilterITEMSAmmo = selectedFilterITEMSAmmo.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterITEMSAmmo.joinToString(",")
            }
            "selectedDATAQuestsArray" -> {
                selectedFilterDATAQuests = selectedFilterDATAQuests.map { it.toInt() }.sorted().map { it.toString() }.toMutableSet()
                selectedItemsString = selectedFilterDATAQuests.joinToString(",")
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
            "selectedITEMSWeaponsArray" -> {
                ITEMSWeaponsSetup(bindingMain.incLayoutTabItemsWeapons.recyclerTabWeapons)
            }
            "selectedITEMSApparelArray" -> {
                ITEMSApparelSetup(bindingMain.incLayoutTabItemsApparel.recyclerTabApparel)
            }
            "selectedITEMSAidArray" -> {
                ITEMSAidSetup(bindingMain.incLayoutTabItemsAid.recyclerTabAid)
            }
            "selectedITEMSMiscArray" -> {
                ITEMSMiscSetup(bindingMain.incLayoutTabItemsMisc.recyclerTabItemsMisc)
            }
            "selectedITEMSAmmoArray" -> {
                ITEMSAmmoSetup(bindingMain.incLayoutTabItemsAmmo.recyclerTabAmmo)
            }
        }
    }
    // Make the function suspendable
    suspend fun loadSelectedItems(){
        // Switch to a background thread to read and split data
        withContext(Dispatchers.IO) {
            val selectedSTATSPerksArray = sharedPreferences.getString("selectedSTATSPerksArray", "1")
            val selectedITEMSWeaponsArray = sharedPreferences.getString("selectedITEMSWeaponsArray", "1")
            val selectedITEMSApparelArray = sharedPreferences.getString("selectedITEMSApparelArray", "1")
            val selectedITEMSAidArray = sharedPreferences.getString("selectedITEMSAidArray", "1")
            val selectedITEMSMiscArray = sharedPreferences.getString("selectedITEMSMiscArray", "1")
            val selectedITEMSAmmoArray = sharedPreferences.getString("selectedITEMSAmmoArray", "1")
            val selectedDATAQuestsArray = sharedPreferences.getString("selectedDATAQuestsArray", "1")
            val selectedDATAMiscArray = sharedPreferences.getString("selectedDATAMiscArray", "1")

            if (!selectedSTATSPerksArray.isNullOrEmpty()) {selectedSTATSPerksArray?.let { selectedFilterSTATSPerks.addAll(it.split(",")) }}
            if (!selectedITEMSWeaponsArray.isNullOrEmpty()) {selectedITEMSWeaponsArray?.let { selectedFilterITEMSWeapons.addAll(it.split(",")) }}
            if (!selectedITEMSApparelArray.isNullOrEmpty()) {selectedITEMSApparelArray?.let { selectedFilterITEMSApparel.addAll(it.split(",")) }}
            if (!selectedITEMSAidArray.isNullOrEmpty()) {selectedITEMSAidArray?.let { selectedFilterITEMSAid.addAll(it.split(",")) }}
            if (!selectedITEMSMiscArray.isNullOrEmpty()) {selectedITEMSMiscArray?.let { selectedFilterITEMSMisc.addAll(it.split(",")) }}
            if (!selectedITEMSAmmoArray.isNullOrEmpty()) {selectedITEMSAmmoArray?.let { selectedFilterITEMSAmmo.addAll(it.split(",")) }}
            if (!selectedDATAQuestsArray.isNullOrEmpty()) {selectedDATAQuestsArray?.let { selectedFilterDATAQuests.addAll(it.split(",")) }}
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
    private fun ITEMSWeaponsSetup(recyclerView: RecyclerView){
        val selectedITEMSWeaponsString = sharedPreferences.getString("selectedITEMSWeaponsArray", "1")
        val selectedITEMSWeaponsArray: Array<String> = selectedITEMSWeaponsString!!.split(",").toTypedArray()

        // Filter the weapon list based on the selected items
        val filteredWeaponsList = weapons.filter { weapon ->
            weapon["id"] in selectedITEMSWeaponsArray
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = WeaponsAdapter(weapons, selectedITEMSWeaponsArray, selected_button, this) { weapon: Map<String, String> ->
            bindingMain.incLayoutTabItemsWeapons.imgItemsWeaponSelected.setImageResource(resources.getIdentifier(weapon["icon"], "drawable", packageName))
            bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsStrValue.text = (weapon["str"] ?: "No description available")
            bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsDpsValue.text = (weapon["dps"] ?: "No description available")
            bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsWgValue.text = (weapon["wg"] ?: "No description available")
            bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsValValue.text = (weapon["val"] ?: "No description available")
            bindingMain.incLayoutTabItemsWeapons.pbItemsWeaponsCndValue.progress = (weapon["cnd"]!!.toInt() ?: 0)
            bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsAmmo.text = (weapon["ammo"] ?: "No description available")
            // Additional selection handling if necessary
        }

        adapter.updateData(filteredWeaponsList)

        recyclerView.adapter = adapter

        // Optional: Scroll to a pre-selected item or update UI as needed
        if (weapons.isNotEmpty()) {
            val firstWeapon = weapons.find { it["id"] == selectedITEMSWeaponsArray[0] }
            firstWeapon?.let {
                bindingMain.incLayoutTabItemsWeapons.imgItemsWeaponSelected.setImageResource(resources.getIdentifier(it["icon"], "drawable", packageName))
                bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsStrValue.text = (it["str"] ?: "No description available")
                bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsDpsValue.text = (it["dps"] ?: "No description available")
                bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsWgValue.text = (it["wg"] ?: "No description available")
                bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsValValue.text = (it["val"] ?: "No description available")
                bindingMain.incLayoutTabItemsWeapons.pbItemsWeaponsCndValue.progress = (it["cnd"]!!.toInt() ?: 0)
                bindingMain.incLayoutTabItemsWeapons.tvItemsWeaponsAmmo.text = (it["ammo"] ?: "No description available")
            }
        }
    }
    private fun ITEMSApparelSetup(recyclerView: RecyclerView){
        val selectedITEMSApparelString = sharedPreferences.getString("selectedITEMSApparelArray", "1")
        val selectedITEMSApparelArray: Array<String> = selectedITEMSApparelString!!.split(",").toTypedArray()

        // Filter the apparel list based on the selected items
        val filteredApparelsList = apparels.filter { apparel ->
            apparel["id"] in selectedITEMSApparelArray
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ApparelsAdapter(apparels, selectedITEMSApparelArray, selected_button, this) { apparel: Map<String, String> ->
            bindingMain.incLayoutTabItemsApparel.imgItemsApparelSelected.setImageResource(resources.getIdentifier(apparel["icon"], "drawable", packageName))
            bindingMain.incLayoutTabItemsApparel.tvItemsApparelDrValue.text = (apparel["dr"] ?: "No description available")
            bindingMain.incLayoutTabItemsApparel.tvItemsApparelWgValue.text = (apparel["wg"] ?: "No description available")
            bindingMain.incLayoutTabItemsApparel.tvItemsApparelValValue.text = (apparel["val"] ?: "No description available")
            bindingMain.incLayoutTabItemsApparel.pbItemsApparelCndValue.progress = (apparel["cnd"]!!.toInt() ?: 0)
            bindingMain.incLayoutTabItemsApparel.tvItemsApparelType.text = (apparel["armortype"] ?: "No description available")
            // Additional selection handling if necessary
        }

        adapter.updateData(filteredApparelsList)

        recyclerView.adapter = adapter

        // Optional: Scroll to a pre-selected item or update UI as needed
        if (apparels.isNotEmpty()) {
            val firstApparel = apparels.find { it["id"] == selectedITEMSApparelArray[0] }
            firstApparel?.let {
                bindingMain.incLayoutTabItemsApparel.imgItemsApparelSelected.setImageResource(resources.getIdentifier(it["icon"], "drawable", packageName))
                bindingMain.incLayoutTabItemsApparel.tvItemsApparelDrValue.text = (it["dr"] ?: "No description available")
                bindingMain.incLayoutTabItemsApparel.tvItemsApparelWgValue.text = (it["wg"] ?: "No description available")
                bindingMain.incLayoutTabItemsApparel.tvItemsApparelValValue.text = (it["val"] ?: "No description available")
                bindingMain.incLayoutTabItemsApparel.pbItemsApparelCndValue.progress = (it["cnd"]!!.toInt() ?: 0)
                bindingMain.incLayoutTabItemsApparel.tvItemsApparelType.text = (it["armortype"] ?: "No description available")
            }
        }
    }
    private fun ITEMSAidSetup(recyclerView: RecyclerView){
        val selectedITEMSAidString = sharedPreferences.getString("selectedITEMSAidArray", "1")
        val selectedITEMSAidArray: Array<String> = selectedITEMSAidString!!.split(",").toTypedArray()

        // Filter the aid list based on the selected items
        val filteredPerksList = aids.filter { aid ->
            aid["id"] in selectedITEMSAidArray
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = AidAdapter(aids, selectedITEMSAidArray, selected_button) { aid ->
            bindingMain.incLayoutTabItemsAid.tvItemsAidWgValue.text = (aid["wg"] ?: "No description available")
            bindingMain.incLayoutTabItemsAid.tvItemsAidValValue.text = (aid["val"] ?: "No description available")
            bindingMain.incLayoutTabItemsAid.tvItemsAidEffValue.text = (aid["eff"] ?: "No description available")
            bindingMain.incLayoutTabItemsAid.imgItemsAidSelected.setImageResource(resources.getIdentifier(aid["icon"], "drawable", packageName))
            // Additional selection handling if necessary
        }

        adapter.updateData(filteredPerksList)

        recyclerView.adapter = adapter

        // Optional: Scroll to a pre-selected item or update UI as needed
        if (aids.isNotEmpty()) {
            val firstAid = aids.find { it["id"] == selectedITEMSAidArray[0] }
            firstAid?.let {
                bindingMain.incLayoutTabItemsAid.tvItemsAidWgValue.text = (it["wg"] ?: "No description available")
                bindingMain.incLayoutTabItemsAid.tvItemsAidValValue.text = (it["val"] ?: "No description available")
                bindingMain.incLayoutTabItemsAid.tvItemsAidEffValue.text = (it["eff"] ?: "No description available")
                bindingMain.incLayoutTabItemsAid.imgItemsAidSelected.setImageResource(resources.getIdentifier(it["icon"], "drawable", packageName))
            }
        }
    }
    private fun ITEMSMiscSetup(recyclerView: RecyclerView){
        val selectedITEMSMiscString = sharedPreferences.getString("selectedITEMSMiscArray", "1")
        val selectedITEMSMiscArray: Array<String> = selectedITEMSMiscString!!.split(",").toTypedArray()

        // Filter the imisc list based on the selected items
        val filteredItemsMiscList = imiscs.filter { imisc ->
            imisc["id"] in selectedITEMSMiscArray
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ITEMSMiscAdapter(imiscs, selectedITEMSMiscArray, selected_button) { imisc ->
            bindingMain.incLayoutTabItemsMisc.tvItemsMiscWgValue.text = (imisc["wg"] ?: "No description available")
            bindingMain.incLayoutTabItemsMisc.tvItemsMiscValValue.text = ((imisc["val"]!!.toInt() * imisc["misccount"]!!.toInt()).toString())
            bindingMain.incLayoutTabItemsMisc.imgItemsMiscSelected.setImageResource(resources.getIdentifier(imisc["icon"], "drawable", packageName))
            // Additional selection handling if necessary
        }

        adapter.updateData(filteredItemsMiscList)

        recyclerView.adapter = adapter

        // Optional: Scroll to a pre-selected item or update UI as needed
        if (imiscs.isNotEmpty()) {
            val firstITEMSMisc = imiscs.find { it["id"] == selectedITEMSMiscArray[0] }
            firstITEMSMisc?.let {
                bindingMain.incLayoutTabItemsMisc.tvItemsMiscWgValue.text = (it["wg"] ?: "No description available")
                bindingMain.incLayoutTabItemsMisc.tvItemsMiscValValue.text = ((it["val"]!!.toInt() * it["misccount"]!!.toInt()).toString())
                bindingMain.incLayoutTabItemsMisc.imgItemsMiscSelected.setImageResource(resources.getIdentifier(it["icon"], "drawable", packageName))
            }
        }
    }
    private fun ITEMSAmmoSetup(recyclerView: RecyclerView) {
        val selectedAmmoString = sharedPreferences.getString("selectedITEMSAmmoArray", "1")
        val selectedAmmoArray: Array<String> = selectedAmmoString!!.split(",").toTypedArray()

        // Filter the ammo list based on the selected items
        val filteredAmmoList = ammos.filter { ammo ->
            ammo["id"] in selectedAmmoArray
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = AmmoAdapter(ammos, selectedAmmoArray, selected_button) { ammo ->
            bindingMain.incLayoutTabItemsAmmo.tvItemsAmmoWgValue.text = ammo["wg"] ?: "No description available"
            bindingMain.incLayoutTabItemsAmmo.tvItemsAmmoValValue.text = ((ammo["val"]!!.toInt() * ammo["ammocount"]!!.toInt()).toString())
            bindingMain.incLayoutTabItemsAmmo.imgItemsAmmoSelected.setImageResource(resources.getIdentifier(ammo["icon"], "drawable", packageName))
            // Additional selection handling if necessary
        }

        adapter.updateData(filteredAmmoList)

        recyclerView.adapter = adapter

        // Optional: Scroll to a pre-selected item or update UI as needed
        if (ammos.isNotEmpty()) {
            val firstAmmo = ammos.find { it["id"] == selectedAmmoArray[0] }
            firstAmmo?.let {
                bindingMain.incLayoutTabItemsAmmo.tvItemsAmmoWgValue.text = it["wg"] ?: "No description available"
                bindingMain.incLayoutTabItemsAmmo.tvItemsAmmoValValue.text = ((it["val"]!!.toInt() * it["ammocount"]!!.toInt()).toString())
                bindingMain.incLayoutTabItemsAmmo.imgItemsAmmoSelected.setImageResource(resources.getIdentifier(it["icon"], "drawable", packageName))
            }
        }
    }

    /***********************************************************************************************************
     * SHARED PREFERENCES
     **********************************************************************************************************/
    private fun saveValues(etSettings1: String, etSettings2: Int, etSettings3: String, uiColourID: Int, etSettings5: Float, dateFormat: Int, showTutorial: Boolean, trueFullscreen: Boolean, gameYear: Int) {
        sharedPreferences.edit().putString(playerName_SPKey, etSettings1).apply()
        sharedPreferences.edit().putInt(playerLevel_SPKey, etSettings2).apply()
        sharedPreferences.edit().putString(customMusicFolder_SPKey, etSettings3).apply()
        sharedPreferences.edit().putInt(playerUIColour_SPKey, uiColourID).apply()
        sharedPreferences.edit().putFloat(customMapScaling_SPKey, etSettings5).apply()
        sharedPreferences.edit().putInt(dateFormat_SPKey, dateFormat).apply()
        sharedPreferences.edit().putBoolean("ShowTutorial", showTutorial).apply()
        sharedPreferences.edit().putBoolean("TrueFullscreen", trueFullscreen).apply()
        sharedPreferences.edit().putInt(gameYear_SPKey, gameYear).apply()
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

        listDataQuests.add(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry1)
        listDataQuests.add(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry2)
        listDataQuests.add(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry3)
        listDataQuests.add(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry4)
        listDataQuests.add(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry5)
        listDataQuests.add(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry6)

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
        bindingMain.incLayoutHeaderToplevel.btnHeaderStats.setOnClickListener{ menuChangeBLE("STATS") }
        bindingMain.incLayoutHeaderToplevel.btnHeaderItems.setOnClickListener{ menuChangeBLE("ITEMS") }
        bindingMain.incLayoutHeaderToplevel.btnHeaderData.setOnClickListener{ menuChangeBLE("DATA") }
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

        // Clock time refresh
        when(sharedPreferences.getInt(dateFormat_SPKey, 0)){
            0 -> { selectedDateFormat = "MM.dd.yy, HH:mm"}
            1 -> { selectedDateFormat = "MM.dd.yyyy, HH:mm"}
            2 -> { selectedDateFormat = "dd.MM.yy, HH:mm"}
            3 -> { selectedDateFormat = "dd.MM.yyyy, HH:mm"}
            4 -> { selectedDateFormat = "yyyy.MM.dd, HH:mm"}
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
                            val datetime: String = SimpleDateFormat(selectedDateFormat).format(gameCalendar.time)
                            val timeHHmm: String = SimpleDateFormat("HH:mm").format(gameCalendar.time)
                            val timess: String = SimpleDateFormat(":ss").format(gameCalendar.time)
                            bindingMain.incLayoutHeaderBottomDatetime.tvTitleDataDatetimeValue.text = datetime
                            bindingMain.incLayoutTabDataRadio.incLayoutTabClock.tvTabRadioClockPopupHm.text = timeHHmm
                            bindingMain.incLayoutTabDataRadio.incLayoutTabClock.tvTabRadioClockPopupS.text = timess
                            bindingMain.incLayoutTabDataRadio.incLayoutTabClock.tvTabRadioClockPopupBattery.text = getBatteryPercent().toString()
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
         * TUTORIAL
         **********************************************************************************************************/
        if(sharedPreferences.getBoolean("ShowTutorial", true)){
            bindingMain.constraintlayoutMain.visibility = View.GONE
            bindingMain.constraintlayoutTutorial.visibility = View.VISIBLE
        } else {
            bindingMain.constraintlayoutMain.visibility = View.VISIBLE
            bindingMain.constraintlayoutTutorial.visibility = View.GONE
        }

        bindingMain.incLayoutTabTutorialBase.btnTutorialClose.setOnClickListener{
            bindingMain.constraintlayoutMain.visibility = View.VISIBLE
            bindingMain.constraintlayoutTutorial.visibility = View.GONE
        }
        bindingMain.incLayoutTabTutorialBase.btnTutorialWelcomeSave.setOnClickListener{
            showTutorialBool = bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.isChecked()
            sharedPreferences.edit().putBoolean("ShowTutorial", showTutorialBool).apply()
            bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            playCNDSelectAudio()
        }

        bindingMain.incLayoutTabTutorialBase.btnNextpage.setOnClickListener{
            if(tutorialPage < 6) {
                tutorialPage++
                playCNDSelectAudio()
            }
            when(tutorialPage){
                0 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(false)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                1 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                2 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                3 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                4 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                5 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(false)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.VISIBLE
                }
            }
        }
        bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setOnClickListener{
            if(tutorialPage > 0) {
                tutorialPage--
                playCNDSelectAudio()
            }
            when(tutorialPage){
                0 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(false)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                1 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                2 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                3 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                4 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.VISIBLE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.GONE
                }
                5 -> {
                    bindingMain.incLayoutTabTutorialBase.btnPreviouspage.setEnabled(true)
                    bindingMain.incLayoutTabTutorialBase.btnNextpage.setEnabled(false)
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWelcome.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorialWhatsnew.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial1Stats.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial2Items.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial3Data.root.visibility = View.GONE
                    bindingMain.incLayoutTabTutorialBase.incLayoutTabTutorial4Settings.root.visibility = View.VISIBLE

                }
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
                "WEAPONS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, weapons, true)
                "APPAREL" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, apparels, true)
                "AID" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, aids, true)
                "IMISC" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, imiscs, true)
                "AMMO" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, ammos, true)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationClear.setOnClickListener{
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, perks, false)
                "WEAPONS" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, weapons, false)
                "APPAREL" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, apparels, false)
                "AID" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, aids, false)
                "IMISC" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, imiscs, false)
                "AMMO" -> selectClearAllCheckBoxes(bindingMain.incLayoutFilterModification.filterModificationFrame, ammos, false)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationFilter.setOnClickListener{
            val filterText = bindingMain.incLayoutFilterModification.etFilterModificationValue.text.toString()

            when(filteringMenu){
                "PERKS" -> filterList(perks, filterText)
                "WEAPONS" -> filterList(weapons, filterText)
                "APPAREL" -> filterList(apparels, filterText)
                "AID" -> filterList(aids, filterText)
                "IMISC" -> filterList(imiscs, filterText)
                "AMMO" -> filterList(ammos, filterText)
            }
        }

        bindingMain.incLayoutFilterModification.btnFilterModificationSave.setOnClickListener{
            when(filteringMenu){
                "PERKS" -> saveSelectedItems("selectedSTATSPerksArray")
                "WEAPONS" -> saveSelectedItems("selectedITEMSWeaponsArray")
                "APPAREL" -> saveSelectedItems("selectedITEMSApparelArray")
                "AID" -> saveSelectedItems("selectedITEMSAidArray")
                "IMISC" -> saveSelectedItems("selectedITEMSMiscArray")
                "AMMO" -> saveSelectedItems("selectedITEMSAmmoArray")
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
        // поверх текущей вкладки, не нужно больше прятать её содержимое под собой. Три
        // кнопки открытия (STATS/ITEMS/DATA general) ведут в один и тот же инстанс, закрытие
        // и слушатели RadioGroup — тоже по одному разу, не по копии на вкладку.
        bindingMain.incLayoutTabStatsGeneral.btnGeneralSettings.setOnClickListener{
            bindingMain.incLayoutSettingsGlobal.root.visibility = View.VISIBLE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
        }
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


        /***********************************************************************************************************
         * ITEMS
         **********************************************************************************************************/

        /*
        ////////////////////////////////////////////////////////
        ITEMS - WEAPONS MENU
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsWeapons.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsWeapons, listBottomButtons)
            bindingMain.incLayoutTabItemsWeapons.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsApparel.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAid.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAmmo.root.visibility = View.GONE
            ITEMSWeaponsSetup(bindingMain.incLayoutTabItemsWeapons.recyclerTabWeapons)
        }

        bindingMain.incLayoutTabItemsBottom.btnItemsWeapons.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(bindingMain.incLayoutTabItemsBottom.btnItemsWeapons == selectedSubMenu){
                        weaponModification = true
                        handler.postDelayed(longPressRunnable, 2000) // 2seconds
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    weaponModification = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - APPAREL MENU
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsApparel.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsApparel, listBottomButtons)
            bindingMain.incLayoutTabItemsWeapons.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsApparel.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsAid.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAmmo.root.visibility = View.GONE
            ITEMSApparelSetup(bindingMain.incLayoutTabItemsApparel.recyclerTabApparel)
        }

        bindingMain.incLayoutTabItemsBottom.btnItemsApparel.setOnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if(bindingMain.incLayoutTabItemsBottom.btnItemsApparel == selectedSubMenu){
                    apparelModification = true
                    handler.postDelayed(longPressRunnable, 2000) // 2seconds
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                apparelModification = false
                handler.removeCallbacks(longPressRunnable)
            }
        }
        false
    }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - AID MENU
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsAid.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsAid, listBottomButtons)
            bindingMain.incLayoutTabItemsWeapons.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsApparel.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAid.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAmmo.root.visibility = View.GONE
            ITEMSAidSetup(bindingMain.incLayoutTabItemsAid.recyclerTabAid)
        }
        bindingMain.incLayoutTabItemsBottom.btnItemsAid.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(bindingMain.incLayoutTabItemsBottom.btnItemsAid == selectedSubMenu){
                        aidModification = true
                        handler.postDelayed(longPressRunnable, 2000) // 2seconds
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    aidModification = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - MISC MENU
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsMisc.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsMisc, listBottomButtons)
            bindingMain.incLayoutTabItemsWeapons.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsApparel.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAid.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsMisc.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsAmmo.root.visibility = View.GONE
            ITEMSMiscSetup(bindingMain.incLayoutTabItemsMisc.recyclerTabItemsMisc)
        }

        // OPEN ITEMS - SETTINGS MENU (тот же единый экран, что и с STATS — закрытие и
        // RadioGroup-листенеры уже подключены там, дублировать не нужно)
        bindingMain.incLayoutTabItemsMisc.btnItemsMiscSettings.setOnClickListener{
            bindingMain.incLayoutSettingsGlobal.root.visibility = View.VISIBLE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
        }

        bindingMain.incLayoutTabItemsBottom.btnItemsMisc.setOnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if(bindingMain.incLayoutTabItemsBottom.btnItemsMisc == selectedSubMenu){
                    imiscModification = true
                    handler.postDelayed(longPressRunnable, 2000) // 2seconds
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                imiscModification = false
                handler.removeCallbacks(longPressRunnable)
            }
        }
        false
    }

        /*
        ////////////////////////////////////////////////////////
        ITEMS - AMMO MENU
        */
        bindingMain.incLayoutTabItemsBottom.btnItemsAmmo.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabItemsBottom.btnItemsAmmo, listBottomButtons)
            bindingMain.incLayoutTabItemsWeapons.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsApparel.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAid.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsAmmo.root.visibility = View.VISIBLE
            ITEMSAmmoSetup(bindingMain.incLayoutTabItemsAmmo.recyclerTabAmmo)
        }
        bindingMain.incLayoutTabItemsBottom.btnItemsAmmo.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(bindingMain.incLayoutTabItemsBottom.btnItemsAmmo == selectedSubMenu){
                        ammoModification = true
                        handler.postDelayed(longPressRunnable, 2000) // 2seconds
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ammoModification = false
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }

        /***********************************************************************************************************
         * DATA
         **********************************************************************************************************/

        /*
        ////////////////////////////////////////////////////////
        DATA - LOCAL MAP MENU
        */
        bindingMain.incLayoutTabDataBottom.btnDataLocalmap.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataLocalmap, listBottomButtons)
            bindingMain.incLayoutTabDataLocalMap.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataWorldMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataQuests.root.visibility = View.GONE
            bindingMain.incLayoutTabDataMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
        }

        networkChangeReceiver = NetworkChangeReceiver(this)
        checkINETPermissions()

        /*
        ////////////////////////////////////////////////////////
        DATA - WORLD MAP MENU
        */
        bindingMain.incLayoutTabDataBottom.btnDataWorldmap.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataWorldmap, listBottomButtons)
            bindingMain.incLayoutTabDataLocalMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataWorldMap.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataQuests.root.visibility = View.GONE
            bindingMain.incLayoutTabDataMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
        }

        worldMapPOIs = mutableListOf()
        fallout3WorldMapLocations()

        bindingMain.incLayoutTabDataWorldMap.btnWorldmapF3.setOnClickListener{
            worldMapPhotoView.setImageResource(R.drawable.worldmap_f3)
            fallout3WorldMapLocations()
        }
        bindingMain.incLayoutTabDataWorldMap.btnWorldmapFNV.setOnClickListener{
            worldMapPhotoView.setImageResource(R.drawable.worldmap_fnv)
            falloutNVWorldMapLocations()
        }

        /*
        ////////////////////////////////////////////////////////
        DATA - QUESTS MENU
        */
        bindingMain.incLayoutTabDataBottom.btnDataQuests.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataQuests, listBottomButtons)
            bindingMain.incLayoutTabDataLocalMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataWorldMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataQuests.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
        }

        bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry1.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry1, listDataQuests)
            bindingMain.incLayoutTabDataQuests.tvDataQuestsQuestsText.setText(R.string.data_quests_entry1_description)
        }
        bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry2.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry2, listDataQuests)
            bindingMain.incLayoutTabDataQuests.tvDataQuestsQuestsText.setText(R.string.data_quests_entry2_description)
        }
        bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry3.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry3, listDataQuests)
            bindingMain.incLayoutTabDataQuests.tvDataQuestsQuestsText.setText(R.string.data_quests_entry3_description)
        }
        bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry4.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry4, listDataQuests)
            bindingMain.incLayoutTabDataQuests.tvDataQuestsQuestsText.setText(R.string.data_quests_entry4_description)
        }
        bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry5.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry5, listDataQuests)
            bindingMain.incLayoutTabDataQuests.tvDataQuestsQuestsText.setText(R.string.data_quests_entry5_description)
        }
        bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry6.setOnClickListener{
            setSelectedSubMenuButton(bindingMain.incLayoutTabDataQuests.layoutTabDataQuestsEntry6, listDataQuests)
            bindingMain.incLayoutTabDataQuests.tvDataQuestsQuestsText.setText(R.string.data_quests_entry6_description)
        }

        /*
        ////////////////////////////////////////////////////////
        DATA - MISC MENU
        */
        bindingMain.incLayoutTabDataBottom.btnDataMisc.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataMisc, listBottomButtons)
            bindingMain.incLayoutTabDataLocalMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataWorldMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataQuests.root.visibility = View.GONE
            bindingMain.incLayoutTabDataMisc.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.GONE
        }

        // OPEN DATA - SETTINGS MENU (тот же единый экран, что и с STATS — закрытие и
        // RadioGroup-листенеры уже подключены там, дублировать не нужно)
        bindingMain.incLayoutTabDataMisc.btnDataMiscSettings.setOnClickListener{
            bindingMain.incLayoutSettingsGlobal.root.visibility = View.VISIBLE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
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

        bindingMain.incLayoutTabDataRadio.layoutTabRadioClock.setOnClickListener{
            bindingMain.incLayoutTabDataRadio.incLayoutTabClock.root.visibility = View.VISIBLE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
        }
        bindingMain.incLayoutTabDataRadio.incLayoutTabClock.btnTabRadioClockPopupClose.setOnClickListener{
            bindingMain.incLayoutTabDataRadio.incLayoutTabClock. root.visibility = View.GONE
            enableDisableBottomButtons(true, listBottomButtons)
            enableDisableTopSwipe(true)
        }


        // DataStore for saving Settings
        val saveButtonSettings = bindingMain.incLayoutSettingsGlobal.btnSettingsSave
        val editSettings1 = bindingMain.incLayoutSettingsGlobal.etSettings1Value //PlayerName
        val editSettings2 = bindingMain.incLayoutSettingsGlobal.etSettings2Value //PlayerLevel
        val editSettings3 = bindingMain.incLayoutSettingsGlobal.etSettings3Value //MusicFolder
        val editSettings5 = bindingMain.incLayoutSettingsGlobal.etSettings5Value //CustomMapScaling
        var editSettings6 = bindingMain.incLayoutSettingsGlobal.cboxTutorialSettings //ShowTutorial
        var editSettings7 = bindingMain.incLayoutSettingsGlobal.cboxTruefullscreenSettings //Fullscreen
        val editSettingsYear = bindingMain.incLayoutSettingsGlobal.etSettingsYearValue //GameYear

        saveButtonSettings.setOnClickListener{
            lifecycleScope.launch(Dispatchers.IO) {
                saveValues(editSettings1.text.toString(), editSettings2.text.toString().toInt(), editSettings3.text.toString(), UIColour_Selector, editSettings5.text.toString().toFloat(), dateFormat_Selector, editSettings6.isChecked(), editSettings7.isChecked(), editSettingsYear.text.toString().toInt())
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
            bindingMain.incLayoutHeaderBottomStats.tvTitleStatsLvlValue.text = (sharedPreferences.getInt(playerLevel_SPKey, 1)).toString()
            editSettings1.setText(sharedPreferences.getString(playerName_SPKey, "Player"))
            editSettings2.setText((sharedPreferences.getInt(playerLevel_SPKey, 1)).toString())
            editSettings3.setText(sharedPreferences.getString(customMusicFolder_SPKey, "Music"))
            editSettings5.setText((sharedPreferences.getFloat(customMapScaling_SPKey, 1f)).toString())
            editSettingsYear.setText((sharedPreferences.getInt(gameYear_SPKey, 2276)).toString())
            bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings6.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings7.setChecked(sharedPreferences.getBoolean("TrueFullscreen", true))
            refreshModeSettingsLabel()

            bindingMain.incLayoutSettingsGlobal.rgSettingsDateformat.check(bindingMain.incLayoutSettingsGlobal.rgSettingsDateformat.getChildAt(sharedPreferences.getInt(dateFormat_SPKey, 0)).id)
            bindingMain.incLayoutSettingsGlobal.rgSettingsUiColour.check(bindingMain.incLayoutSettingsGlobal.rgSettingsUiColour.getChildAt(sharedPreferences.getInt(playerUIColour_SPKey, 0)).id)


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
        // Сервис НЕ останавливаем — он должен продолжать держать BLE-связь и в фоне,
        // это и есть весь смысл foreground service (протокол, раздел 5). Отвязываемся
        // только от локального биндинга, чтобы не утекала ссылка на Activity.
        if (bleServiceBound) {
            unbindService(bleServiceConnection)
            bleServiceBound = false
        }
    }

}

object TypefaceCache {
    private var monofontoTypeface: Typeface? = null

    fun getMonofontoTypeface(context: Context): Typeface {
        if (monofontoTypeface == null) {
            monofontoTypeface = Typeface.createFromAsset(context.assets, "fonts/monofonto.ttf")
        }
        return monofontoTypeface!!
    }
}