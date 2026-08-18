package com.malto4.pipdroid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
    val bluetoothMAC_SPKey = "bluetoothMAC"
    val bluetoothSUUID_SPKey = "bluetoothSUUID"
    val bluetoothRUUID_SPKey = "bluetoothRUUID"
    val bluetoothWUUID_SPKey = "bluetoothWUUID"
    private var UIColour_Selector = 0
    private var dateFormat_Selector = 0
    private var selected_button = R.drawable.button_selected_green
    private var selectedDateFormat = "MM.dd.yy, HH:mm"
    private var trueFullscreen = false



    /***********************************************************************************************************
     * LIST DEFINITIONS
     **********************************************************************************************************/
    private var listMenuButtons = ArrayList<Button>()
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
    private var listSettingsMenus = ArrayList<Button>()

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
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var bleMenuChange: String
    private var deviceAddress : String? = null
    private var serviceUUID : UUID? = null
    private var characteristicReadUUID : UUID? = null
    private var characteristicWriteUUID : UUID? = null
    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val bluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
            val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
            if (granted || (bluetoothScanGranted && bluetoothConnectGranted)) {
                setupBluetooth()
            } else {
                Log.e("MainActivity", "Required permissions are not granted")
            }
        }  else {
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (granted) {
                setupBluetooth()
            } else {
                Log.e("MainActivity", "Required permissions are not granted")
            }
        }
    }

    /***********************************************************************************************************
     * SCREEN SIZE
     **********************************************************************************************************/
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var newWidth = 0
    private var newHeight = 0
    private var isResizing = false
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionRequestLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            setupBluetooth()
        }
    }
    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Log.e("MainActivity", "Bluetooth is not supported")
        } else {
            if (!bluetoothAdapter.isEnabled) {
                Log.e("MainActivity", "Bluetooth is disabled")
                // Optionally, you could prompt the user to enable Bluetooth here
            } else if (deviceAddress != null){
                connectToDevice(deviceAddress!!)
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun connectToDevice(address: String) {
        val device = bluetoothAdapter.getRemoteDevice(address)
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BluetoothGattCallback", "Connected to GATT server.")
                    gatt.discoverServices()
                    updateBLEConnected("CONNECTED")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("BluetoothGattCallback", "Disconnected from GATT server.")
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    updateBLEConnected("DISCONNECTED")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothGattCallback", "Services discovered.")
                val characteristic = gatt.getService(serviceUUID)?.getCharacteristic(characteristicReadUUID)
                characteristic?.let { enableCharacteristicNotification(it) }
            } else {
                Log.w("BluetoothGattCallback", "onServicesDiscovered received: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            runOnUiThread {
                @Suppress("DEPRECATION")
                bleMenuChange = characteristic.value.toString(Charsets.UTF_8)
                Log.i("BluetoothGattCallback", "Characteristic changed: $bleMenuChange")
                handleBleCommand(bleMenuChange)
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableCharacteristicNotification(characteristic: BluetoothGattCharacteristic) {
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)

            // Enable notifications on the characteristic descriptor
            val descriptor = characteristic.getDescriptor(
                UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
            )
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeDescriptor(descriptor)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothGattCallback", "Characteristic written successfully")
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(value: ByteArray) {
        bluetoothGatt?.let { gatt ->
            val service = gatt.getService(serviceUUID)
            val characteristic = service?.getCharacteristic(characteristicWriteUUID)
            characteristic?.let {
                @Suppress("DEPRECATION")
                it.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(it)
            }
        }
    }
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    private fun sendBLEText(bleText: String) {
        if (bluetoothGatt != null) {
            val service = bluetoothGatt?.getService(serviceUUID)
            val characteristic = service?.getCharacteristic(characteristicWriteUUID)
            if (service != null && characteristic != null) {
                writeCharacteristic(bleText.toByteArray())
                Log.i("MainActivity", "Sending text to BLE device" )
            } else {
                Log.e("MainActivity", "Service or Characteristic not found")
            }
        } else {
            Log.e("MainActivity", "BluetoothGatt is not connected")
        }
    }
    fun updateBLEConnected(status: String){
        bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.textViewBLUETOOTHConnection.text = status
        bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.textViewBLUETOOTHConnection.text = status
        bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.textViewBLUETOOTHConnection.text = status
    }
    @SuppressLint("MissingPermission")
    private fun disconnectBLE(){
        updateBLEConnected("DISCONNECTED")
        bluetoothGatt?.close()
        bluetoothGatt = null
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

                val displayMetrics = resources.displayMetrics
                val statusBarHeight = getStatusBarHeight()
                val navigationBarHeight = getNavigationBarHeight()

                layoutParams.width = min(newWidth, displayMetrics.widthPixels)
                layoutParams.height = min(newHeight, displayMetrics.heightPixels)

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
        val btnstatsSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsSTATS
        val btnitemsSTATS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsSTATS
        val btndataSTATS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsSTATS
        val btnstatsITEMS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsITEMS
        val btnitemsITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsITEMS
        val btndataITEMS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsITEMS
        val btnstatsDATA = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsDATA
        val btnitemsDATA = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsDATA
        val btndataDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsDATA

        when(menu){
            "STATS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks, bindingMain.incLayoutTabStatsBottom.btnStatsGeneral)
                menuOptionClicked(btnstatsSTATS, listSettingsMenus, "STATS", btnstatsSTATS, btnitemsSTATS, btndataSTATS)
                curMenu = "STATS"
            }
            "ITEMS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabItemsBottom.btnItemsWeapons, bindingMain.incLayoutTabItemsBottom.btnItemsApparel, bindingMain.incLayoutTabItemsBottom.btnItemsAid, bindingMain.incLayoutTabItemsBottom.btnItemsMisc, bindingMain.incLayoutTabItemsBottom.btnItemsAmmo)
                menuOptionClicked(btnstatsITEMS, listSettingsMenus, "ITEMS", btnstatsITEMS, btnitemsITEMS, btndataITEMS)
                ITEMSWeaponsSetup(bindingMain.incLayoutTabItemsWeapons.recyclerTabWeapons)
                curMenu = "ITEMS"
            }
            "DATA" -> {
                bottomButtonsModify(bindingMain.incLayoutTabDataBottom.btnDataWorldmap, bindingMain.incLayoutTabDataBottom.btnDataLocalmap, bindingMain.incLayoutTabDataBottom.btnDataQuests, bindingMain.incLayoutTabDataBottom.btnDataMisc, bindingMain.incLayoutTabDataBottom.btnDataRadio)
                menuOptionClicked(btndataDATA, listSettingsMenus, "DATA", btnstatsDATA, btnitemsDATA, btndataDATA)
                curMenu = "DATA"
            }
        }
    }
    /**
     * Разбирает входящую BLE-строку по конвенции протокола (PipBoy_BLE_Protocol_v0.2.md,
     * раздел 2: `КЛЮЧ:ЗНАЧЕНИЕ` для параметризованных команд, голое ключевое слово для
     * остальных) и раздаёт по обработчикам. STATS/ITEMS/DATA уходят в уже существующий
     * menuChangeBLE() без изменений — остальные команды пока только логируются, реальная
     * обработка (POWER, навигация энкодером, радио) — следующие этапы roadmap.
     */
    private fun handleBleCommand(raw: String) {
        val parts = raw.split(":", limit = 2)
        val key = parts[0]
        val value = parts.getOrNull(1)

        when (key) {
            "STATS", "ITEMS", "DATA" -> menuChangeBLE(key)
            "POWER" -> Log.i("BLE", "POWER:$value — état-машина экрана, roadmap этап 2")
            "ENCBTN" -> Log.i("BLE", "ENCBTN — навигация энкодером, roadmap этап 3")
            "ENC" -> Log.i("BLE", "ENC:$value — навигация энкодером, roadmap этап 3")
            "GEIGER" -> Log.i("BLE", "GEIGER:$value — отображение, roadmap этап 7")
            "RADIOPWR" -> Log.i("BLE", "RADIOPWR:$value — реальное радио, roadmap этап 7")
            "RADIOFREQ" -> Log.i("BLE", "RADIOFREQ:$value — реальное радио, roadmap этап 7")
            "RADIOTUNE" -> Log.i("BLE", "RADIOTUNE:$value — реальное радио, roadmap этап 7")
            "VOLUME" -> Log.i("BLE", "VOLUME:$value — реальное радио, roadmap этап 7")
            "RADIOTUNEBTN" -> Log.i("BLE", "RADIOTUNEBTN — реальное радио, roadmap этап 7")
            "HOLOTAPE" -> Log.i("BLE", "HOLOTAPE:$value — голодиски, блокируется готовностью USB Host")
            else -> Log.w("BLE", "Неизвестная BLE-команда: $raw")
        }
    }

    fun menuChangeBLE(menu: String){
        val btnstatsSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsSTATS
        val btnitemsSTATS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsSTATS
        val btndataSTATS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsSTATS
        val btnstatsITEMS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsITEMS
        val btnitemsITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsITEMS
        val btndataITEMS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsITEMS
        val btnstatsDATA = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsDATA
        val btnitemsDATA = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsDATA
        val btndataDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsDATA

        when(menu){
            "STATS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabStatsBottom.btnStatsStatus, bindingMain.incLayoutTabStatsBottom.btnStatsSpecial, bindingMain.incLayoutTabStatsBottom.btnStatsSkills, bindingMain.incLayoutTabStatsBottom.btnStatsPerks, bindingMain.incLayoutTabStatsBottom.btnStatsGeneral)
                menuOptionClickedBLE(btnstatsSTATS, listSettingsMenus, "STATS", btnstatsSTATS, btnitemsSTATS, btndataSTATS)
                curMenu = "STATS"
            }
            "ITEMS" -> {
                bottomButtonsModify(bindingMain.incLayoutTabItemsBottom.btnItemsWeapons, bindingMain.incLayoutTabItemsBottom.btnItemsApparel, bindingMain.incLayoutTabItemsBottom.btnItemsAid, bindingMain.incLayoutTabItemsBottom.btnItemsMisc, bindingMain.incLayoutTabItemsBottom.btnItemsAmmo)
                menuOptionClickedBLE(btnstatsITEMS, listSettingsMenus, "ITEMS", btnstatsITEMS, btnitemsITEMS, btndataITEMS)
                ITEMSWeaponsSetup(bindingMain.incLayoutTabItemsWeapons.recyclerTabWeapons)
                curMenu = "ITEMS"
            }
            "DATA" -> {
                bottomButtonsModify(bindingMain.incLayoutTabDataBottom.btnDataWorldmap, bindingMain.incLayoutTabDataBottom.btnDataLocalmap, bindingMain.incLayoutTabDataBottom.btnDataQuests, bindingMain.incLayoutTabDataBottom.btnDataMisc, bindingMain.incLayoutTabDataBottom.btnDataRadio)
                menuOptionClickedBLE(btndataDATA, listSettingsMenus, "DATA", btnstatsDATA, btnitemsDATA, btndataDATA)
                curMenu = "DATA"
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
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.layoutTabSettings,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.layoutTabSettings,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.layoutTabSettings,
            bindingMain.incLayoutFilterModification.layoutFilterModification,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.layoutTabSettingsBluetooth,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.layoutTabSettingsBluetooth,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.layoutTabSettingsBluetooth,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.layoutTabSettingsScreensize,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.layoutTabSettingsScreensize,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.layoutTabSettingsScreensize,
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
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rbSettingsDateformat1,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rbSettingsDateformat2,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rbSettingsDateformat3,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rbSettingsDateformat4,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rbSettingsDateformat5,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rbSettingsDateformat1,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rbSettingsDateformat2,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rbSettingsDateformat3,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rbSettingsDateformat4,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rbSettingsDateformat5,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rbSettingsDateformat1,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rbSettingsDateformat2,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rbSettingsDateformat3,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rbSettingsDateformat4,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rbSettingsDateformat5,
            bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.cboxTutorialSettings,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.cboxTutorialSettings,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.cboxTutorialSettings,
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.cboxTruefullscreenSettings,
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.cboxTruefullscreenSettings,
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.cboxTruefullscreenSettings
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
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.scrollTabSettings,
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.scrollTabSettings,
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.scrollTabSettings,
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.scrollTabSettingsBluetooth,
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.scrollTabSettingsBluetooth,
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.scrollTabSettingsBluetooth,
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

    private fun setSelectedMenu(button: Button?, listArrayListButtons: ArrayList<Button>?, mediaSound: MediaPlayer?) {
        button?.setBackgroundResource(R.drawable.settings_menu_buttons_selected)
        mediaSound?.start()
        val it: Iterator<Button> = listArrayListButtons!!.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (!Intrinsics.areEqual(next as Any, button as Any)) {
                next.setBackgroundResource(R.drawable.settings_menu_buttons)
            }
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
    private fun bottomButtonsModify(button1: Button, button2: Button, button3: Button, button4: Button, button5: Button){
        listBottomButtons.clear()
        listBottomButtons.add(button1)
        listBottomButtons.add(button2)
        listBottomButtons.add(button3)
        listBottomButtons.add(button4)
        listBottomButtons.add(button5)
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
    private fun setupTitleBar(menu: String){
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_title).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_title).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_title).visibility = View.GONE
        if (menu == "STATS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_title).visibility = View.VISIBLE
        } else if (menu == "ITEMS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_title).visibility = View.VISIBLE
        } else if (menu == "DATA"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_title).visibility = View.VISIBLE
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
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_settings).visibility = View.VISIBLE
        } else if (menu == "ITEMS"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_misc).visibility = View.VISIBLE
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_item_settings).visibility = View.VISIBLE
        } else if (menu == "DATA"){
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.VISIBLE
            findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_settings).visibility = View.VISIBLE
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
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_stats_settings).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_weapons).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_apparel).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_aid).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_misc).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_items_misc_main).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_item_settings).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_items_ammo).visibility = View.GONE

        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_local_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_world_map).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_quests).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_misc).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.layout_tab_data_misc_main).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.inc_layout_tab_data_settings).visibility = View.GONE
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
    private fun menuOptionClicked(origin: Button, settingsMenus: ArrayList<Button>, menu: String, menugroup1: Button, menugroup2: Button, menugroup3: Button){
        setSelectedMenu(origin, listMenuButtons, mediaPlayerCRF)
        for (button in settingsMenus){
            button.setBackgroundResource(R.drawable.settings_menu_buttons)
        }
        menugroup1.setBackgroundResource(R.drawable.settings_menu_buttons_selected)
        menugroup2.setBackgroundResource(R.drawable.settings_menu_buttons_selected)
        menugroup3.setBackgroundResource(R.drawable.settings_menu_buttons_selected)
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
            findViewById<Button>(R.id.btn_data_radio).setBackgroundResource(R.drawable.button_unselected)
        }
        setupTitleBar(menu)
        setupMainContent(menu)
        setupBottomBar(menu)
        enableDisableBottomButtons(false, listBottomButtons)
        enableDisableTopSwipe(false)
        sendBLEText(menu)
    }
    private fun menuOptionClickedBLE(origin: Button, settingsMenus: ArrayList<Button>, menu: String, menugroup1: Button, menugroup2: Button, menugroup3: Button){
        setSelectedMenu(origin, listMenuButtons, mediaPlayerCRF)
        for (button in settingsMenus){
            button.setBackgroundResource(R.drawable.settings_menu_buttons)
        }
        menugroup1.setBackgroundResource(R.drawable.settings_menu_buttons_selected)
        menugroup2.setBackgroundResource(R.drawable.settings_menu_buttons_selected)
        menugroup3.setBackgroundResource(R.drawable.settings_menu_buttons_selected)
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
            findViewById<Button>(R.id.btn_data_radio).setBackgroundResource(R.drawable.button_unselected)
        }
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

        bindingMain.incLayoutTabStatsTitle.tvTitleHpValue.text = "${hpLevel}/720"
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
    private fun saveValues(etSettings1: String, etSettings2: Int, etSettings3: String, uiColourID: Int, etSettings5: Float, dateFormat: Int, showTutorial: Boolean, trueFullscreen: Boolean) {
        sharedPreferences.edit().putString(playerName_SPKey, etSettings1).apply()
        sharedPreferences.edit().putInt(playerLevel_SPKey, etSettings2).apply()
        sharedPreferences.edit().putString(customMusicFolder_SPKey, etSettings3).apply()
        sharedPreferences.edit().putInt(playerUIColour_SPKey, uiColourID).apply()
        sharedPreferences.edit().putFloat(customMapScaling_SPKey, etSettings5).apply()
        sharedPreferences.edit().putInt(dateFormat_SPKey, dateFormat).apply()
        sharedPreferences.edit().putBoolean("ShowTutorial", showTutorial).apply()
        sharedPreferences.edit().putBoolean("TrueFullscreen", trueFullscreen).apply()
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

        trueFullscreen = sharedPreferences.getBoolean("TrueFullscreen", false)

        if(trueFullscreen){
            //Remove notification bar from APP
            hideSystemUI()
        }

        bindingMain =  ActivityMainBinding.inflate(layoutInflater)
        val viewMain = bindingMain.root
        setContentView(viewMain)

        //Load saved size and position
        loadViewState()

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

        listMenuButtons.add(findViewById(R.id.bt_settings_STATS))
        listMenuButtons.add(findViewById(R.id.bt_settings_ITEMS))
        listMenuButtons.add(findViewById(R.id.bt_settings_DATA))


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
        findViewById<Button>(R.id.bt_settings_STATS).setBackgroundResource(R.drawable.settings_menu_buttons_selected)

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
                            val datetime: String = SimpleDateFormat(selectedDateFormat).format(Calendar.getInstance().time)
                            val timeHHmm: String = SimpleDateFormat("HH:mm").format(Calendar.getInstance().time)
                            val timess: String = SimpleDateFormat(":ss").format(Calendar.getInstance().time)
                            bindingMain.incLayoutTabDataTitle.tvTitleDataDatetimeValue.text = datetime
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
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.cboxTutorialSettings.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.cboxTutorialSettings.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.cboxTutorialSettings.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
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


        // OPEN STATS - SETTINGS MENU
        bindingMain.incLayoutTabStatsGeneral.btnGeneralSettings.setOnClickListener{
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsGeneral.layoutTabStatsGeneralMain.visibility = View.GONE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
        }
        bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btnSettingsClose.setOnClickListener{
            if(!isResizing){
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.root.visibility = View.GONE
                bindingMain.incLayoutTabStatsGeneral.layoutTabStatsGeneralMain.visibility = View.VISIBLE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
        }
        val rg_DateFormat_STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rgSettingsDateformat
        rg_DateFormat_STATS.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_DateFormat_STATS.getChildAt(0)?.id) -> dateFormat_Selector = 0
                (rg_DateFormat_STATS.getChildAt(1)?.id) -> dateFormat_Selector = 1
                (rg_DateFormat_STATS.getChildAt(2)?.id) -> dateFormat_Selector = 2
                (rg_DateFormat_STATS.getChildAt(3)?.id) -> dateFormat_Selector = 3
                (rg_DateFormat_STATS.getChildAt(4)?.id) -> dateFormat_Selector = 4
            }
        }
        val rg_UIColour_STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rgSettingsUiColour
        rg_UIColour_STATS.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_UIColour_STATS.getChildAt(0)?.id) -> UIColour_Selector = 0
                (rg_UIColour_STATS.getChildAt(1)?.id) -> UIColour_Selector = 1
                (rg_UIColour_STATS.getChildAt(2)?.id) -> UIColour_Selector = 2
                (rg_UIColour_STATS.getChildAt(3)?.id) -> UIColour_Selector = 3
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

        // OPEN ITEMS - SETTINGS MENU
        bindingMain.incLayoutTabItemsMisc.btnItemsMiscSettings.setOnClickListener{
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsMisc.layoutTabItemsMiscMain.visibility = View.GONE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
        }
        bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btnSettingsClose.setOnClickListener{
            if(!isResizing){
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.root.visibility = View.GONE
                bindingMain.incLayoutTabItemsMisc.layoutTabItemsMiscMain.visibility = View.VISIBLE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
        }
        val rg_DateFormat_ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rgSettingsDateformat
        rg_DateFormat_ITEMS.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_DateFormat_ITEMS.getChildAt(0)?.id) -> dateFormat_Selector = 0
                (rg_DateFormat_ITEMS.getChildAt(1)?.id) -> dateFormat_Selector = 1
                (rg_DateFormat_ITEMS.getChildAt(2)?.id) -> dateFormat_Selector = 2
                (rg_DateFormat_ITEMS.getChildAt(3)?.id) -> dateFormat_Selector = 3
                (rg_DateFormat_ITEMS.getChildAt(4)?.id) -> dateFormat_Selector = 4
            }
        }
        val rg_UIColour_ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rgSettingsUiColour
        rg_UIColour_ITEMS.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_UIColour_ITEMS.getChildAt(0)?.id) -> {UIColour_Selector = 0}
                (rg_UIColour_ITEMS.getChildAt(1)?.id) -> {UIColour_Selector = 1}
                (rg_UIColour_ITEMS.getChildAt(2)?.id) -> {UIColour_Selector = 2}
                (rg_UIColour_ITEMS.getChildAt(3)?.id) -> UIColour_Selector = 3
            }
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

        // OPEN DATA - SETTINGS MENU
        bindingMain.incLayoutTabDataMisc.btnDataMiscSettings.setOnClickListener{
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataMisc.layoutTabDataMiscMain.visibility = View.GONE
            enableDisableBottomButtons(false, listBottomButtons)
            enableDisableTopSwipe(false)
        }
        bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btnSettingsClose.setOnClickListener{
            if(!isResizing){
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.root.visibility = View.GONE
                bindingMain.incLayoutTabDataMisc.layoutTabDataMiscMain.visibility = View.VISIBLE
                enableDisableBottomButtons(true, listBottomButtons)
                enableDisableTopSwipe(true)
            }
        }
        val rg_DateFormat_DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rgSettingsDateformat
        rg_DateFormat_DATA.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_DateFormat_DATA.getChildAt(0)?.id) -> dateFormat_Selector = 0
                (rg_DateFormat_DATA.getChildAt(1)?.id) -> dateFormat_Selector = 1
                (rg_DateFormat_DATA.getChildAt(2)?.id) -> dateFormat_Selector = 2
                (rg_DateFormat_DATA.getChildAt(3)?.id) -> dateFormat_Selector = 3
                (rg_DateFormat_DATA.getChildAt(4)?.id) -> dateFormat_Selector = 4
            }
        }
        val rg_UIColour_DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rgSettingsUiColour
        rg_UIColour_DATA.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId){
                (rg_UIColour_DATA.getChildAt(0)?.id) -> UIColour_Selector = 0
                (rg_UIColour_DATA.getChildAt(1)?.id) -> UIColour_Selector = 1
                (rg_UIColour_DATA.getChildAt(2)?.id) -> UIColour_Selector = 2
                (rg_UIColour_DATA.getChildAt(3)?.id) -> UIColour_Selector = 3
            }
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
        */
        bindingMain.incLayoutTabDataBottom.btnDataRadio.setOnClickListener {
            setSelectedButton(bindingMain.incLayoutTabDataBottom.btnDataRadio, listBottomButtons)
            bindingMain.incLayoutTabDataLocalMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataWorldMap.root.visibility = View.GONE
            bindingMain.incLayoutTabDataQuests.root.visibility = View.GONE
            bindingMain.incLayoutTabDataMisc.root.visibility = View.GONE
            bindingMain.incLayoutTabDataRadio.root.visibility = View.VISIBLE
        }

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


        /*
        ////////////////////////////////////////////////////////
        SETTINGS MENU
        */
        val tvstatsSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.tvSettingsSTATS
        val tvitemsSTATS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.tvSettingsSTATS
        val tvdataSTATS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.tvSettingsSTATS
        val tvstatsITEMS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.tvSettingsITEMS
        val tvitemsITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.tvSettingsITEMS
        val tvdataITEMS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.tvSettingsITEMS
        val tvstatsDATA = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.tvSettingsDATA
        val tvitemsDATA = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.tvSettingsDATA
        val tvdataDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.tvSettingsDATA

        tvstatsSTATS.setOnClickListener {menuChange("STATS")}
        tvitemsSTATS.setOnClickListener {menuChange("STATS")}
        tvdataSTATS.setOnClickListener {menuChange("STATS")}

        tvstatsITEMS.setOnClickListener {menuChange("ITEMS")}
        tvitemsITEMS.setOnClickListener {menuChange("ITEMS")}
        tvdataITEMS.setOnClickListener {menuChange("ITEMS")}

        tvstatsDATA.setOnClickListener {menuChange("DATA")}
        tvitemsDATA.setOnClickListener {menuChange("DATA")}
        tvdataDATA.setOnClickListener {menuChange("DATA")}

        val btnstatsSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsSTATS
        val btnitemsSTATS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsSTATS
        val btndataSTATS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsSTATS
        val btnstatsITEMS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsITEMS
        val btnitemsITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsITEMS
        val btndataITEMS = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsITEMS
        val btnstatsDATA = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btSettingsDATA
        val btnitemsDATA = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btSettingsDATA
        val btndataDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btSettingsDATA

        listSettingsMenus.add(btnstatsSTATS)
        listSettingsMenus.add(btnitemsSTATS)
        listSettingsMenus.add(btndataSTATS)
        listSettingsMenus.add(btnstatsITEMS)
        listSettingsMenus.add(btnitemsITEMS)
        listSettingsMenus.add(btndataITEMS)
        listSettingsMenus.add(btnstatsDATA)
        listSettingsMenus.add(btnitemsDATA)
        listSettingsMenus.add(btndataDATA)
        
        btnstatsSTATS.setOnClickListener {menuChange("STATS")}
        btnitemsSTATS.setOnClickListener {menuChange("STATS")}
        btndataSTATS.setOnClickListener {menuChange("STATS")}
        
        btnstatsITEMS.setOnClickListener {menuChange("ITEMS")}
        btnitemsITEMS.setOnClickListener {menuChange("ITEMS")}
        btndataITEMS.setOnClickListener {menuChange("ITEMS")}

        btnstatsDATA.setOnClickListener {menuChange("DATA")}
        btnitemsDATA.setOnClickListener {menuChange("DATA")}
        btndataDATA.setOnClickListener {menuChange("DATA")}

        // DataStore for saving Settings
        val saveButtonSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btnSettingsSave
        val editSettings1STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.etSettings1Value //PlayerName
        val editSettings2STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.etSettings2Value //PlayerLevel
        val editSettings3STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.etSettings3Value //MusicFolder
        val editSettings5STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.etSettings5Value //CustomMapScaling
        var editSettings6STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.cboxTutorialSettings //ShowTutorial
        var editSettings7STATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.cboxTruefullscreenSettings //Fullscreen

        saveButtonSTATS.setOnClickListener{
            lifecycleScope.launch(Dispatchers.IO) {
                saveValues(editSettings1STATS.text.toString(), editSettings2STATS.text.toString().toInt(), editSettings3STATS.text.toString(), UIColour_Selector, editSettings5STATS.text.toString().toFloat(), dateFormat_Selector, editSettings6STATS.isChecked(), editSettings7STATS.isChecked())
            }
            turnAllRadioOff()
            sendBLEText("STATS")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            finishAffinity() // Close all previous activities
            startActivity(intent)
        }

        val saveButtonITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btnSettingsSave
        val editSettings1ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.etSettings1Value //PlayerName
        val editSettings2ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.etSettings2Value //PlayerLevel
        val editSettings3ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.etSettings3Value //MusicFolder
        val editSettings5ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.etSettings5Value //CustomMapScaling
        var editSettings6ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.cboxTutorialSettings //ShowTutorial
        var editSettings7ITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.cboxTruefullscreenSettings //Fullscreen

        saveButtonITEMS.setOnClickListener{
            lifecycleScope.launch(Dispatchers.IO) {
                saveValues(editSettings1ITEMS.text.toString(), editSettings2ITEMS.text.toString().toInt(), editSettings3ITEMS.text.toString(), UIColour_Selector, editSettings5ITEMS.text.toString().toFloat(), dateFormat_Selector, editSettings6ITEMS.isChecked(), editSettings7ITEMS.isChecked())
            }
            turnAllRadioOff()
            sendBLEText("STATS")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            finishAffinity() // Close all previous activities
            startActivity(intent)
        }

        val saveButtonDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btnSettingsSave
        val editSettings1DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.etSettings1Value //PlayerName
        val editSettings2DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.etSettings2Value //PlayerLevel
        val editSettings3DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.etSettings3Value //MusicFolder
        val editSettings5DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.etSettings5Value //CustomMapScaling
        var editSettings6DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.cboxTutorialSettings //ShowTutorial
        var editSettings7DATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.cboxTruefullscreenSettings //Fullscreen

        saveButtonDATA.setOnClickListener{
            lifecycleScope.launch(Dispatchers.IO) {
                saveValues(editSettings1DATA.text.toString(), editSettings2DATA.text.toString().toInt(), editSettings3DATA.text.toString(), UIColour_Selector, editSettings5DATA.text.toString().toFloat(), dateFormat_Selector, editSettings6DATA.isChecked(), editSettings7DATA.isChecked())
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
            bindingMain.incLayoutTabStatsTitle.tvTitleStatsLvlValue.text = (sharedPreferences.getInt(playerLevel_SPKey, 1)).toString()
            editSettings1STATS.setText(sharedPreferences.getString(playerName_SPKey, "Player"))
            editSettings1ITEMS.setText(sharedPreferences.getString(playerName_SPKey, "Player"))
            editSettings1DATA.setText(sharedPreferences.getString(playerName_SPKey, "Player"))
            editSettings2STATS.setText((sharedPreferences.getInt(playerLevel_SPKey, 1)).toString())
            editSettings2ITEMS.setText((sharedPreferences.getInt(playerLevel_SPKey, 1)).toString())
            editSettings2DATA.setText((sharedPreferences.getInt(playerLevel_SPKey, 1)).toString())
            editSettings3STATS.setText(sharedPreferences.getString(customMusicFolder_SPKey, "Music"))
            editSettings3ITEMS.setText(sharedPreferences.getString(customMusicFolder_SPKey, "Music"))
            editSettings3DATA.setText(sharedPreferences.getString(customMusicFolder_SPKey, "Music"))
            editSettings5STATS.setText((sharedPreferences.getFloat(customMapScaling_SPKey, 1f)).toString())
            editSettings5ITEMS.setText((sharedPreferences.getFloat(customMapScaling_SPKey, 1f)).toString())
            editSettings5DATA.setText((sharedPreferences.getFloat(customMapScaling_SPKey, 1f)).toString())
            bindingMain.incLayoutTabTutorialBase.cboxTutorialWelcome.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings6STATS.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings6ITEMS.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings6DATA.setChecked(sharedPreferences.getBoolean("ShowTutorial", true))
            editSettings7STATS.setChecked(sharedPreferences.getBoolean("TrueFullscreen", false))
            editSettings7ITEMS.setChecked(sharedPreferences.getBoolean("TrueFullscreen", false))
            editSettings7DATA.setChecked(sharedPreferences.getBoolean("TrueFullscreen", false))

            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rgSettingsDateformat.check(bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rgSettingsDateformat.getChildAt(sharedPreferences.getInt(dateFormat_SPKey, 0)).id)
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rgSettingsDateformat.check(bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rgSettingsDateformat.getChildAt(sharedPreferences.getInt(dateFormat_SPKey, 0)).id)
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rgSettingsDateformat.check(bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rgSettingsDateformat.getChildAt(sharedPreferences.getInt(dateFormat_SPKey, 0)).id)

            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rgSettingsUiColour.check(bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.rgSettingsUiColour.getChildAt(sharedPreferences.getInt(playerUIColour_SPKey, 0)).id)
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rgSettingsUiColour.check(bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.rgSettingsUiColour.getChildAt(sharedPreferences.getInt(playerUIColour_SPKey, 0)).id)
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rgSettingsUiColour.check(bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.rgSettingsUiColour.getChildAt(sharedPreferences.getInt(playerUIColour_SPKey, 0)).id)


        /***********************************************************************************************************
         *
         * BLUETOOTH
         *
         **********************************************************************************************************/

        //BLUETOOTH
        val bluetoothButtonSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btnSettingsBluetooth
        val bluetoothButtonITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btnSettingsBluetooth
        val bluetoothButtonDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btnSettingsBluetooth

        bluetoothButtonSTATS.setOnClickListener{
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.layoutSettingsLayout.visibility = View.GONE
        }
        bluetoothButtonITEMS.setOnClickListener{
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.layoutSettingsLayout.visibility = View.GONE
        }
        bluetoothButtonDATA.setOnClickListener{
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.layoutSettingsLayout.visibility = View.GONE
        }

        val bluetoothButtonCloseSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.btnSettingsBluetoothClose
        val bluetoothButtonCloseITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.btnSettingsBluetoothClose
        val bluetoothButtonCloseDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.btnSettingsBluetoothClose

        bluetoothButtonCloseSTATS.setOnClickListener{
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.root.visibility = View.GONE
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.layoutSettingsLayout.visibility = View.VISIBLE
        }
        bluetoothButtonCloseITEMS.setOnClickListener{
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.root.visibility = View.GONE
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.layoutSettingsLayout.visibility = View.VISIBLE
        }
        bluetoothButtonCloseDATA.setOnClickListener{
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.root.visibility = View.GONE
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.layoutSettingsLayout.visibility = View.VISIBLE
        }


        val etBluetoothMACSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.etMACAddressValue
        val etBluetoothSUUIDSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.etServiceUUIDValue
        val etBluetoothRUUIDSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.etReadUUIDValue
        val etBluetoothWUUIDSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.etWriteUUIDValue
        val bluetoothButtonSaveSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.btnSettingsBluetoothSave

        etBluetoothMACSTATS.setText(sharedPreferences.getString(bluetoothMAC_SPKey, "AA:BB:CC:DD:EE:FF"))
        etBluetoothSUUIDSTATS.setText(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothRUUIDSTATS.setText(sharedPreferences.getString(bluetoothRUUID_SPKey, "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothWUUIDSTATS.setText(sharedPreferences.getString(bluetoothWUUID_SPKey, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))

        bluetoothButtonSaveSTATS.setOnClickListener{
            saveBluetoothValues(
                etBluetoothMACSTATS.text.toString(),
                etBluetoothSUUIDSTATS.text.toString(),
                etBluetoothRUUIDSTATS.text.toString(),
                etBluetoothWUUIDSTATS.text.toString()
            )
        }

        val etBluetoothMACITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.etMACAddressValue
        val etBluetoothSUUIDITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.etServiceUUIDValue
        val etBluetoothRUUIDITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.etReadUUIDValue
        val etBluetoothWUUIDITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.etWriteUUIDValue
        val bluetoothButtonSaveITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.btnSettingsBluetoothSave

        etBluetoothMACITEMS.setText(sharedPreferences.getString(bluetoothMAC_SPKey, "AA:BB:CC:DD:EE:FF"))
        etBluetoothSUUIDITEMS.setText(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothRUUIDITEMS.setText(sharedPreferences.getString(bluetoothRUUID_SPKey, "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothWUUIDITEMS.setText(sharedPreferences.getString(bluetoothWUUID_SPKey, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))

        bluetoothButtonSaveITEMS.setOnClickListener{
            saveBluetoothValues(
                etBluetoothMACITEMS.text.toString(),
                etBluetoothSUUIDITEMS.text.toString(),
                etBluetoothRUUIDITEMS.text.toString(),
                etBluetoothWUUIDITEMS.text.toString()
            )
        }

        val etBluetoothMACDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.etMACAddressValue
        val etBluetoothSUUIDDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.etServiceUUIDValue
        val etBluetoothRUUIDDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.etReadUUIDValue
        val etBluetoothWUUIDDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.etWriteUUIDValue
        val bluetoothButtonSaveDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.btnSettingsBluetoothSave

        etBluetoothMACDATA.setText(sharedPreferences.getString(bluetoothMAC_SPKey, "AA:BB:CC:DD:EE:FF"))
        etBluetoothSUUIDDATA.setText(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothRUUIDDATA.setText(sharedPreferences.getString(bluetoothRUUID_SPKey, "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
        etBluetoothWUUIDDATA.setText(sharedPreferences.getString(bluetoothWUUID_SPKey, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))

        bluetoothButtonSaveDATA.setOnClickListener{
            saveBluetoothValues(
                etBluetoothMACDATA.text.toString(),
                etBluetoothSUUIDDATA.text.toString(),
                etBluetoothRUUIDDATA.text.toString(),
                etBluetoothWUUIDDATA.text.toString()
            )
        }


        val bluetoothButtonConnectSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.btnSettingsConnect
        val bluetoothButtonConnectITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.btnSettingsConnect
        val bluetoothButtonConnectDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.btnSettingsConnect

        bluetoothButtonConnectSTATS.setOnClickListener{
            deviceAddress = sharedPreferences.getString(bluetoothMAC_SPKey, "AA:BB:CC:DD:EE:FF")
            serviceUUID = UUID.fromString(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
            characteristicReadUUID = UUID.fromString(sharedPreferences.getString(bluetoothRUUID_SPKey, "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
            characteristicWriteUUID = UUID.fromString(sharedPreferences.getString(bluetoothWUUID_SPKey, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkPermissions()
            } else {
                setupBluetooth()
            }
        }

        bluetoothButtonConnectITEMS.setOnClickListener{
            deviceAddress = sharedPreferences.getString(bluetoothMAC_SPKey, "AA:BB:CC:DD:EE:FF")
            serviceUUID = UUID.fromString(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
            characteristicReadUUID = UUID.fromString(sharedPreferences.getString(bluetoothRUUID_SPKey, "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
            characteristicWriteUUID = UUID.fromString(sharedPreferences.getString(bluetoothWUUID_SPKey, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkPermissions()
            } else {
                setupBluetooth()
            }
        }

        bluetoothButtonConnectDATA.setOnClickListener{
            deviceAddress = sharedPreferences.getString(bluetoothMAC_SPKey, "AA:BB:CC:DD:EE:FF")
            serviceUUID = UUID.fromString(sharedPreferences.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
            characteristicReadUUID = UUID.fromString(sharedPreferences.getString(bluetoothRUUID_SPKey, "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
            characteristicWriteUUID = UUID.fromString(sharedPreferences.getString(bluetoothWUUID_SPKey, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkPermissions()
            } else {
                setupBluetooth()
            }
        }

        val bluetoothButtonDisconnectSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsBluetooth.btnSettingsDisconnect
        val bluetoothButtonDisconnectITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsBluetooth.btnSettingsDisconnect
        val bluetoothButtonDisconnectDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsBluetooth.btnSettingsDisconnect

        bluetoothButtonDisconnectSTATS.setOnClickListener{
            disconnectBLE()
        }
        bluetoothButtonDisconnectITEMS.setOnClickListener{
            disconnectBLE()
        }
        bluetoothButtonDisconnectDATA.setOnClickListener{
            disconnectBLE()
        }


        /***********************************************************************************************************
         *
         * SCREENSIZE
         *
         **********************************************************************************************************/

        //SCREENSIZE
        val screensizeButtonSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.btnSettingsScreensize
        val screensizeButtonITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.btnSettingsScreensize
        val screensizeButtonDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.btnSettingsScreensize

        screensizeButtonSTATS.setOnClickListener{
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.layoutSettingsLayout.visibility = View.GONE
        }
        screensizeButtonITEMS.setOnClickListener{
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.layoutSettingsLayout.visibility = View.GONE
        }
        screensizeButtonDATA.setOnClickListener{
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.root.visibility = View.VISIBLE
            bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.layoutSettingsLayout.visibility = View.GONE
        }

        val screensizeButtonCloseSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeClose
        val screensizeButtonCloseITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeClose
        val screensizeButtonCloseDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeClose

        screensizeButtonCloseSTATS.setOnClickListener{
            if(!isResizing){
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.root.visibility = View.GONE
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.layoutSettingsLayout.visibility = View.VISIBLE
            }
        }
        screensizeButtonCloseITEMS.setOnClickListener{
            if(!isResizing){
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.root.visibility = View.GONE
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.layoutSettingsLayout.visibility = View.VISIBLE
            }
        }
        screensizeButtonCloseDATA.setOnClickListener{
            if(!isResizing){
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.root.visibility = View.GONE
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.layoutSettingsLayout.visibility = View.VISIBLE
            }
        }

        val screensizeButtonResizeSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeResize
        val screensizeButtonResizeITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeResize
        val screensizeButtonResizeDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeResize

        screensizeButtonResizeSTATS.setOnClickListener{
            isResizing = !isResizing
            if(isResizing){
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
            } else {
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
            }
        }
        screensizeButtonResizeITEMS.setOnClickListener{
            isResizing = !isResizing
            if(isResizing){
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
            } else {
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
            }
        }
        screensizeButtonResizeDATA.setOnClickListener{
            isResizing = !isResizing
            if(isResizing){
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.VISIBLE
            } else {
                bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
                bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
                bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.tvScreensizeEditting.visibility = View.GONE
            }
        }

        val screensizeButtonFullscreenSTATS = bindingMain.incLayoutTabStatsGeneral.incLayoutTabStatsSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeFullscreen
        val screensizeButtonFullscreenITEMS = bindingMain.incLayoutTabItemsMisc.incLayoutTabItemSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeFullscreen
        val screensizeButtonFullscreenDATA = bindingMain.incLayoutTabDataMisc.incLayoutTabDataSettings.incLayoutTabSettingsScreensize.btnSettingsScreensizeFullscreen

        screensizeButtonFullscreenSTATS.setOnClickListener{
            resetToFullScreen()
        }
        screensizeButtonFullscreenITEMS.setOnClickListener{
            resetToFullScreen()
        }
        screensizeButtonFullscreenDATA.setOnClickListener{
            resetToFullScreen()
        }

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
        bindingMain.incLayoutTabStatsTitle.tvTitleData.setOnTouchListener { view, event ->
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
        bindingMain.incLayoutTabItemsTitle.tvTitleData.setOnTouchListener { view, event ->
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
        bindingMain.incLayoutTabDataTitle.tvTitleData.setOnTouchListener { view, event ->
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