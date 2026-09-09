package com.malto4.pipdroid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.malto4.pipdroid.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Экран ITEMS/Карта целиком: бандл и геолокация, отметки, маршрут, боковое меню и дерево энкодера. */
/** Контроллер владеет состоянием экрана, MainActivity остаётся слоем навигации. Диктовка имени отметки
 * живёт здесь же — микрофон в приложении один, поэтому наружу видна только её занятость. */
internal class MapController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val navigator: MenuNavigator,
    private val bundleRepository: MapBundleRepository,
    private val dictationService: com.malto4.pipdroid.voice.VoiceDictationService,
    private val voiceModels: com.malto4.pipdroid.voice.VoiceModelRepository,
    private val dictationPermissionRequestCode: Int,
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
    private val isVoiceCommandBusy: () -> Boolean,
) {
    private var mapGeoReference: GeoReference? = null
    private var mapLocationListener: LocationListener? = null
    private var mapHasCenteredOnUser = false
    private var pedestrianRouter: PedestrianRouter? = null
    private val markerRepository by lazy { MarkerRepository(activity) }
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
    private enum class MapRouteState { NONE, BUILT, ACTIVE }
    private var mapRouteState = MapRouteState.NONE
    private var mapRouteDestination: Pair<Double, Double>? = null
    private var mapRouteLatLonPath: List<Pair<Double, Double>> = emptyList()
    private var pendingMapReadyAction: (() -> Unit)? = null
    private enum class MapTapMode { NONE, PLACE_MARKER, ROUTE_TO_POINT }
    private var mapTapMode = MapTapMode.NONE
    /** Диктовка имени отметки — при правке существующей первый сегмент затирает старое имя. */
    private val markerDictation by lazy {
        val popup = binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        DictationController(
            activity = activity,
            micButton = popup.btnMarkerNamePopupMic,
            statusView = popup.tvMarkerNamePopupMicStatus,
            editText = popup.etMarkerNameValue,
            permissionRequestCode = dictationPermissionRequestCode,
            logTag = "VoiceMapMarker",
            dictation = dictationService,
            models = voiceModels,
            accentColor = { accentColor() },
            isVoiceCommandBusy = { isVoiceCommandBusy() },
            replaceFirstSegment = { editingMarkerId != null },
            playButtonSound = { playButton() },
            playErrorSound = { playError() },
        )
    }

    companion object {
        private const val MAP_ZOOM_STEP_FACTOR = 1.4f
        private const val MAP_MARKER_TAP_RADIUS_DP = 28f
        private const val MAP_ROUTE_REROUTE_THRESHOLD_M = 30.0
        // Отступ от краёв при автоцентрировании на построенном маршруте.
        private const val MAP_ROUTE_FIT_PADDING_DP = 28f
        // Шаг панорамирования уголками энкодера, в экранных dp.
        private const val MAP_PAN_STEP_DP = 80f
    }

    // ===== ПУБЛИЧНАЯ ПОВЕРХНОСТЬ =====

    /** Вход на вкладку Карта: бандл грузится с диска асинхронно, поэтому экран поднимается не мгновенно. */
    fun openScreen() = openMapScreen()

    /** Ветка MAP дерева энкодера — её строит itemsMenuRoot() активности. */
    fun childrenNodes(): List<MenuNode> = mapRootChildrenNodes()

    /** Режим стал известен после onCreate(): пересобираем боковой список и кнопку "назад" карточки. */
    fun refreshModeGating() {
        mapRootAdapter.setItems(mapRootSidebarItems(), resetSelection = false)
        refreshMapMarkerDetailBackButtonVisibility()
    }

    /** Курсор ещё на узле MAP и не провалился в боковое меню — рамку гасим целиком. */
    fun clearSidebarSelection() = mapRootAdapter.clearSelection()

    /** Экран карты скрыт или приложение ушло в фон — GPS больше не нужен. */
    fun stopLocationUpdates() = stopMapLocationUpdates()

    /** Возврат в приложение на открытой карте. */
    fun startLocationUpdates() = startMapLocationUpdates()

    /** Голосовое "отменить маршрут". */
    fun cancelRoute() = cancelActiveRoute()

    /** Голосовое "маршрут до <отметки>": false, если под запрос подошла не ровно одна отметка. Сам
     * маршрут откладывается до готовности экрана — бандл карты грузится асинхронно. */
    fun prepareVoiceRouteToMarker(queryTokens: List<String>): Boolean {
        val candidates = markerRepository.loadAll().filter { matchesMarkerQuery(queryTokens, it.name) }
        if (candidates.size != 1) return false
        val destination = candidates[0]
        pendingMapReadyAction = { routeTo(destination.lat, destination.lon) }
        return true
    }

    /** Wake-word уступает микрофон уже идущей диктовке: VoiceDictationService один на все сценарии. */
    val isMarkerDictationIdle: Boolean
        get() = markerDictation.isIdle

    /** Разрешение на запись выдано — стартуем, только если попап имени всё ещё открыт. */
    fun startMarkerDictationIfPopupVisible() {
        if (binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.root.visibility == View.VISIBLE) {
            markerDictation.start()
        }
    }

    // ===== ЭКРАН И ГЕОЛОКАЦИЯ =====

    private fun openMapScreen() {
        val mapScreen = binding.incLayoutTabItemsMap
        if (!bundleRepository.hasBundle()) {
            mapScreen.tvPermissionsCheckResult.visibility = View.VISIBLE
            mapScreen.photoViewMap.visibility = View.GONE
            mapScreen.viewMapOverlay.visibility = View.GONE
            mapScreen.layoutMapMenuContainer.visibility = View.GONE
            pendingMapReadyAction = null
            return
        }
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(bundleRepository.bundleImageFile().absolutePath)
            val bounds = bundleRepository.loadBounds()
            val roadGraph = bundleRepository.loadRoadGraph()
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
                    Log.w("MapController", "map_roads.json не распарсился — маршрутизация недоступна")
                } else {
                    Log.d("MapController", "Граф дорог загружен: ${roadGraph.nodes.size} узлов")
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
                mapScreen.photoViewMap.colorFilter = PorterDuffColorFilter(accentColor(), PorterDuff.Mode.MULTIPLY)
                mapScreen.photoViewMap.visibility = View.VISIBLE
                mapScreen.tvPermissionsCheckResult.visibility = View.GONE
                mapScreen.viewMapOverlay.visibility = View.VISIBLE
                mapScreen.viewMapOverlay.routePx = emptyList()
                mapScreen.layoutMapMenuContainer.visibility = View.VISIBLE
                mapScreen.incLayoutTabItemsMapNamePopup.root.visibility = View.GONE
                // PipWizardButtonStyle-кнопки тонируются вручную кодом, как в Settings.
                val mapAccentColor = accentColor()
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
                    playConfirm()
                    when (mapTapMode) {
                        MapTapMode.PLACE_MARKER -> {
                            armTapMode(MapTapMode.NONE)
                            suppressTickAround { syncMapEncoderPath(mapMarkerPopupParentPath() + 0) }
                            showMarkerNamePopupForNewMarker(lat, lon)
                        }
                        MapTapMode.ROUTE_TO_POINT -> {
                            armTapMode(MapTapMode.NONE)
                            suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 0) }
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
                                    suppressTickAround { syncMapEncoderPath(listOf(mapRootIndex("MARKER_LIST"), markerIndex, 0)) }
                                }
                                showMarkerDetail(marker)
                            } else {
                                suppressTickAround { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0)) }
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
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (mapLocationListener != null) return
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location -> onMapLocationUpdate(location) }
        mapLocationListener = listener
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 3f, listener)
        } catch (e: Exception) {
            Log.w("MapController", "Не удалось подписаться на обновления геолокации карты", e)
        }
        (currentLocationOrNull())?.let { onMapLocationUpdate(it) }
    }
    /** Останавливать при уходе с экрана карты. */
    private fun stopMapLocationUpdates() {
        val listener = mapLocationListener ?: return
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(listener)
        mapLocationListener = null
    }
    @SuppressLint("MissingPermission")
    private fun currentLocationOrNull(): Location? {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }
    private fun onMapLocationUpdate(location: Location) {
        val geoReference = mapGeoReference ?: return
        val overlay = binding.incLayoutTabItemsMap.viewMapOverlay
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
        val userPx = binding.incLayoutTabItemsMap.viewMapOverlay.userLocationPx ?: return
        centerMapOnBitmapPoint(userPx)
    }
    /** Нижний слот карты. */
    private fun mapBottomOverlayHeightPx(): Float {
        val mapScreen = binding.incLayoutTabItemsMap
        return listOf(
            mapScreen.layoutMapMarkerDetail,
            mapScreen.layoutMapTapChoice,
            mapScreen.layoutMapRouteControls,
            mapScreen.tvMapHint,
        ).firstOrNull { it.visibility == View.VISIBLE }?.height?.toFloat() ?: 0f
    }
    /** Сдвигает PhotoView. */
    private fun centerMapOnBitmapPoint(targetPx: PointF) {
        val photoView = binding.incLayoutTabItemsMap.photoViewMap
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
        binding.incLayoutTabItemsMap.viewMapOverlay.markerPins =
            markers.map { it.name to geoReference.latLonToPixel(it.lat, it.lon) }
    }
    /** Прицел над отметкой из списка: позиция считается вручную из displayMatrix — у отметок оверлея нет своего @id. */
    private fun updateMapMarkerFocus() {
        val mapScreen = binding.incLayoutTabItemsMap
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

    // ===== БОКОВОЕ МЕНЮ, ОТМЕТКИ, ПОПАПЫ =====

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
        val items = mapRootMeta.filter { it.key != "MAP_CONTROLS" || mode() != PipBoyMode.PHONE }
            .map { meta -> SidebarMenuItem(payload = meta.key, label = activity.getString(meta.labelRes)) }
        // "В меню" последним пунктом — иначе курсор энкодера некуда вернуть на уровень выше.
        return if (mode() != PipBoyMode.PHONE) items + backSidebarItem() else items
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
        val menu = binding.incLayoutTabItemsMap
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
        val menu = binding.incLayoutTabItemsMap
        menu.tvMapMarkerListEmpty.visibility = if (markers.isEmpty()) View.VISIBLE else View.GONE
        val items: List<SidebarMenuItem<MapMarker?>> = markers.map { marker -> SidebarMenuItem<MapMarker?>(payload = marker, label = marker.name) } +
            SidebarMenuItem(payload = null, label = activity.getString(R.string.wizard_back))
        // "До отметки" — выбор сразу строит маршрут; "Список меток" — открывает карточку.
        val adapter = SidebarMenuAdapter(
            items = items,
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
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
                playConfirm()
                suppressTickAround { syncMapEncoderPath(path) }
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
        menu.rvMapMarkerList.layoutManager = LinearLayoutManager(activity)
        menu.rvMapMarkerList.adapter = adapter
    }
    /** Карточка деталей отметки делит нижний слот с попапом выбора и панелью маршрута, поэтому прячет обе. */
    private fun showMarkerDetail(marker: MapMarker) {
        selectedMarkerForDetail = marker
        val mapScreen = binding.incLayoutTabItemsMap
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
        binding.incLayoutTabItemsMap.layoutMapMarkerDetail.post { centerMapOnBitmapPoint(targetPx) }
    }
    private fun hideMarkerDetail() {
        selectedMarkerForDetail = null
        binding.incLayoutTabItemsMap.layoutMapMarkerDetail.visibility = View.GONE
        // Панель маршрута была спрятана визуально, а не сброшена — восстановить, если маршрут ещё есть.
        updateRouteControlsVisibility()
    }
    /** Тап по пустой точке предлагает выбор [Route]/[Marker] вместо предопределённого действия. */
    private fun showMapTapChoice(lat: Double, lon: Double) {
        pendingTapChoiceLatLon = lat to lon
        val mapScreen = binding.incLayoutTabItemsMap
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
        binding.incLayoutTabItemsMap.layoutMapTapChoice.visibility = View.GONE
        updateRouteControlsVisibility()
        refreshMapControlBackButtonVisibility()
    }
    /** Ближайший к тапу маркер в экранных координатах, иначе радиус захвата плавал бы с зумом. */
    private fun findMarkerNearTap(tapBitmapPx: PointF): MapMarker? {
        if (markers.isEmpty()) return null
        val geoReference = mapGeoReference ?: return null
        val photoView = binding.incLayoutTabItemsMap.photoViewMap
        val matrix = Matrix()
        photoView.getDisplayMatrix(matrix)
        val tapScreen = floatArrayOf(tapBitmapPx.x, tapBitmapPx.y)
        matrix.mapPoints(tapScreen)
        val thresholdPx = activity.resources.displayMetrics.density * MAP_MARKER_TAP_RADIUS_DP
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
        val photoView = binding.incLayoutTabItemsMap.photoViewMap
        val target = (photoView.scale * factor).coerceIn(photoView.minimumScale, photoView.maximumScale)
        photoView.setScale(target, true)
    }
    private fun showMapHint(text: String) {
        val mapScreen = binding.incLayoutTabItemsMap
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
        hintView.setBackgroundColor(ContextCompat.getColor(activity, R.color.pip_background_darker))
        hintView.setTextColor(accentColor())
        hintView.visibility = View.VISIBLE
    }
    private fun hideMapHint() {
        binding.incLayoutTabItemsMap.tvMapHint.visibility = View.GONE
        updateRouteControlsVisibility()
    }
    /** Взвод режима тапа по карте — расстановка отметки либо выбор точки маршрута. */
    private fun armTapMode(mode: MapTapMode) {
        mapTapMode = mode
        // Текстовая подсказка только в режиме Телефон: в PipBoy её место занимает прицел и панель управления.
        when (mode) {
            MapTapMode.PLACE_MARKER -> if (this.mode() == PipBoyMode.PHONE) showMapHint(activity.getString(R.string.map_hint_place_marker))
            MapTapMode.ROUTE_TO_POINT -> if (this.mode() == PipBoyMode.PHONE) showMapHint(activity.getString(R.string.map_hint_route_to_point))
            MapTapMode.NONE -> hideMapHint()
        }
    }
    // Клавиатура открывается обычным тапом по полю — showSoftInput() вне ответа на касание Android игнорирует.
    private fun showMarkerNamePopupForNewMarker(lat: Double, lon: Double) {
        editingMarkerId = null
        pendingMarkerLatLon = lat to lon
        val popup = binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        popup.etMarkerNameValue.setText("")
        popup.root.visibility = View.VISIBLE
        markerDictation.refreshAvailability()
    }
    private fun showMarkerNamePopupForEdit(marker: MapMarker) {
        editingMarkerId = marker.id
        pendingMarkerLatLon = marker.lat to marker.lon
        val popup = binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        popup.etMarkerNameValue.setText(marker.name)
        popup.root.visibility = View.VISIBLE
        markerDictation.refreshAvailability()
    }
    private fun hideMarkerNamePopup() {
        pendingMarkerLatLon = null
        editingMarkerId = null
        markerDictation.stop()
        binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.root.visibility = View.GONE
    }

    // ===== МАРШРУТ =====

    private fun routeTo(destLat: Double, destLon: Double, returnPath: List<Int> = listOf(mapRootIndex("MAP_CONTROLS"))) {
        val router = pedestrianRouter
        val geoReference = mapGeoReference
        if (router == null || geoReference == null) {
            Log.w("MapController", "routeTo() без графа дорог/geoReference — бандл без map_roads.json?")
            return
        }
        val start = currentLocationOrNull()
        if (start == null) {
            Log.d("MapController", "routeTo() — GPS ещё не дал фикс")
            showMapHint(activity.getString(R.string.map_hint_waiting_gps))
            return
        }
        activity.lifecycleScope.launch(Dispatchers.Default) {
            val path = router.route(start.latitude, start.longitude, destLat, destLon)
            withContext(Dispatchers.Main) {
                if (path == null) {
                    showMapHint(activity.getString(R.string.map_hint_no_route))
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
                navigator.pushLevel(mapRouteControlsChildrenNodes(), tag = "MAP_ROUTE_CONTROLS")
                updateRouteControlsVisibility()
                // Отложено до layout-прохода панели — иначе mapBottomOverlayHeightPx() прочитает 0.
                binding.incLayoutTabItemsMap.layoutMapRouteControls.post {
                    fitMapToRoute(path, destLat, destLon)
                }
            }
        }
    }
    /** Пишет путь и в лат/лон для расчётов, и в пиксели битмапа для отрисовки. */
    private fun applyRoutePath(geoReference: GeoReference, path: List<Pair<Double, Double>>) {
        mapRouteLatLonPath = path
        binding.incLayoutTabItemsMap.viewMapOverlay.routePx =
            path.map { (lat, lon) -> geoReference.latLonToPixel(lat, lon) }
    }
    /** Вписывает весь построенный маршрут в видимую область, меняя и пан, и зум. */
    /** Базовой матрицы нет в паблик API PhotoView — выводим трюком с suppMatrix=identity, дальше
     * newSupp = targetDraw * base^-1. */
    private fun fitMapToRoute(path: List<Pair<Double, Double>>, destLat: Double, destLon: Double) {
        val geoReference = mapGeoReference ?: return
        val photoView = binding.incLayoutTabItemsMap.photoViewMap
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
        val paddingPx = activity.resources.displayMetrics.density * MAP_ROUTE_FIT_PADDING_DP
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
        binding.incLayoutTabItemsMap.viewMapOverlay.routePx = emptyList()
        updateRouteControlsVisibility()
    }
    /** Единая точка правды для панели маршрута; если карточка или попап открыты — не делает ничего, те восстановят её сами. */
    private fun updateRouteControlsVisibility() {
        val mapScreen = binding.incLayoutTabItemsMap
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
        binding.incLayoutTabItemsMap.tvMapRouteStatus.text = formatRouteDistance(remainingMeters)
    }
    private fun rerouteActiveNavigation(location: Location, destination: Pair<Double, Double>) {
        val router = pedestrianRouter ?: return
        val geoReference = mapGeoReference ?: return
        activity.lifecycleScope.launch(Dispatchers.Default) {
            val path = router.route(location.latitude, location.longitude, destination.first, destination.second)
            withContext(Dispatchers.Main) {
                // Следование могло быть остановлено, пока считался маршрут — не оживлять его.
                if (path == null || mapRouteState != MapRouteState.ACTIVE) return@withContext
                applyRoutePath(geoReference, path)
            }
        }
    }
    private fun formatRouteDistance(meters: Double): String {
        val unit = if (meters >= 1000) activity.getString(R.string.map_route_unit_km, meters / 1000.0)
        else activity.getString(R.string.map_route_unit_meters, meters.roundToInt())
        return activity.getString(R.string.map_route_status_remaining, unit)
    }

    // ===== ДЕРЕВО ЭНКОДЕРА =====

    private enum class MapControlMode { ROOT, ROUTE_TO_POINT, PLACE_MARKER }
    /** Геокоордината центра экрана: карта двигается под фиксированной точкой, поэтому берём инверсию текущей displayMatrix. */
    private fun mapCrosshairLatLon(): Pair<Double, Double>? {
        val geoReference = mapGeoReference ?: return null
        val photoView = binding.incLayoutTabItemsMap.photoViewMap
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
        val photoView = binding.incLayoutTabItemsMap.photoViewMap
        val suppMatrix = Matrix()
        photoView.getSuppMatrix(suppMatrix)
        suppMatrix.postTranslate(dxPx, dyPx)
        photoView.setDisplayMatrix(suppMatrix)
    }
    /** Безусловно ставит курсор энкодера по [path] от детей узла MAP. */
    /** [path] обязан указывать до первого ребёнка тапнутого узла, если тот не лист. */
    private fun syncMapEncoderPath(path: List<Int>) = syncMapEncoderPath(path, loud = true)
    /** То же без onHighlight — onHighlight узла MAP заново открывает экран карты. */
    private fun syncMapEncoderPathSilently(path: List<Int>) = syncMapEncoderPath(path, loud = false)
    private fun syncMapEncoderPath(path: List<Int>, loud: Boolean) {
        val rootNodes = itemsMenuRoot()
        val rootIndex = rootNodes.indexOfFirst { it.id == "MAP" }
        if (rootIndex == -1) return
        val fullPath = listOf(rootIndex) + path
        if (loud) navigator.setPath(rootNodes, fullPath) else navigator.setPathSilently(rootNodes, fullPath)
    }
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
        val mapScreen = binding.incLayoutTabItemsMap
        val visibility = if (visible && mode() != PipBoyMode.PHONE) View.VISIBLE else View.GONE
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
        val mapScreen = binding.incLayoutTabItemsMap
        val overlayActive = mapScreen.viewMapCrosshair.visibility == View.VISIBLE
        val tapChoiceOpen = mapScreen.layoutMapTapChoice.visibility == View.VISIBLE
        val visible = overlayActive && !tapChoiceOpen
        mapScreen.btnMapControlBack.visibility = if (visible) View.VISIBLE else View.GONE
        mapScreen.viewMapControlBackBg.visibility = if (visible) View.VISIBLE else View.GONE
    }
    /** Дети всех трёх режимов панели; Crosshair — первый ребёнок, и именно его onHighlight открывает панель,
     * иначе Back немедленно открывал бы её заново. */
    private fun mapControlChildrenNodes(controlMode: MapControlMode): List<MenuNode> {
        val mapScreen = binding.incLayoutTabItemsMap
        fun openOverlayForMode() {
            mapControlMode = controlMode
            setMapControlOverlayVisible(true)
            when (controlMode) {
                MapControlMode.ROUTE_TO_POINT -> armTapMode(MapTapMode.ROUTE_TO_POINT)
                MapControlMode.PLACE_MARKER -> armTapMode(MapTapMode.PLACE_MARKER)
                MapControlMode.ROOT -> {}
            }
        }
        val crosshairNode = when (controlMode) {
            MapControlMode.ROOT -> MenuNode(
                id = "MAP_CTRL_CROSSHAIR",
                onHighlight = {
                    playTick()
                    openOverlayForMode()
                    setAllMapControlFocusesHidden()
                    setMapCrosshairFocused(true)
                },
                // Звук подтверждения на любой ENCBTN по прицелу — узел всё равно проваливается в children следом.
                onActivate = { playConfirm() },
                children = mapCrosshairTapChoiceChildrenNodes(),
            )
            MapControlMode.PLACE_MARKER -> MenuNode(
                id = "MAP_CTRL_CROSSHAIR",
                onHighlight = {
                    playTick()
                    openOverlayForMode()
                    setAllMapControlFocusesHidden()
                    setMapCrosshairFocused(true)
                },
                onActivate = { playConfirm() },
                children = mapMarkerPopupChildrenNodes { mapCrosshairLatLon() },
            )
            MapControlMode.ROUTE_TO_POINT -> MenuNode(
                id = "MAP_CTRL_CROSSHAIR",
                onHighlight = {
                    playTick()
                    openOverlayForMode()
                    setAllMapControlFocusesHidden()
                    setMapCrosshairFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.viewMapCrosshair) {
                        val (lat, lon) = mapCrosshairLatLon() ?: return@flashButtonPressThenRun
                        playConfirm()
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
                    playTick()
                    setAllMapControlFocusesHidden()
                    setMapPanVerticalFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta ->
                        val stepPx = activity.resources.displayMetrics.density * MAP_PAN_STEP_DP
                        playConfirm()
                        flashButtonPressImmediate(if (delta > 0) mapScreen.btnMapPanUp else mapScreen.btnMapPanDown)
                        panMapBy(0f, if (delta > 0) stepPx else -stepPx)
                    },
                    onEnter = { playConfirm() },
                    onExit = { playTick() },
                ),
            ),
            MenuNode(
                id = "MAP_CTRL_PAN_H",
                onHighlight = {
                    playTick()
                    setAllMapControlFocusesHidden()
                    setMapPanHorizontalFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta ->
                        val stepPx = activity.resources.displayMetrics.density * MAP_PAN_STEP_DP
                        playConfirm()
                        flashButtonPressImmediate(if (delta > 0) mapScreen.btnMapPanRight else mapScreen.btnMapPanLeft)
                        // Право = отрицательный dx; знак обязан совпадать с тач-обработчиками btnMapPanRight/Left.
                        panMapBy(if (delta > 0) -stepPx else stepPx, 0f)
                    },
                    onEnter = { playConfirm() },
                    onExit = { playTick() },
                ),
            ),
            MenuNode(
                id = "MAP_CTRL_ZOOM",
                onHighlight = {
                    playTick()
                    setAllMapControlFocusesHidden()
                    setMapZoomFocused(true)
                },
                valueEditor = ValueEditor(
                    onAdjust = { delta ->
                        playConfirm()
                        flashButtonPressImmediate(if (delta > 0) mapScreen.btnMapZoomIn else mapScreen.btnMapZoomOut)
                        zoomMapBy(if (delta > 0) MAP_ZOOM_STEP_FACTOR else 1f / MAP_ZOOM_STEP_FACTOR)
                    },
                    onEnter = { playConfirm() },
                    onExit = { playTick() },
                ),
            ),
            MenuNode(
                id = "MAP_CTRL_CENTER",
                onHighlight = {
                    playTick()
                    setAllMapControlFocusesHidden()
                    setMapCenterFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapCenter) {
                        playConfirm()
                        recenterMapOnUser()
                    }
                },
            ),
            MenuNode(
                id = "MAP_CTRL_BACK",
                onHighlight = {
                    playTick()
                    setAllMapControlFocusesHidden()
                    setMapControlBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapControlBack) {
                        playButton()
                        setMapControlBackFocused(false)
                        setMapControlOverlayVisible(false)
                        // ROUTE_TO_POINT вложен глубже: одного popLevel() мало, "←" обязан вернуть в боковое меню Map.
                        navigator.popLevel()
                        if (controlMode == MapControlMode.ROUTE_TO_POINT) {
                            navigator.popLevel()
                            showMapMenuState(MapMenuState.ROOT)
                        }
                    }
                },
            ),
        )
    }
    /** Дети CROSSHAIR в режиме ROOT — Route/Marker/Cancel; панель открывает onHighlight первого ребёнка. */
    private fun mapCrosshairTapChoiceChildrenNodes(): List<MenuNode> {
        val mapScreen = binding.incLayoutTabItemsMap
        return listOf(
            MenuNode(
                id = "MAP_CTRL_CROSSHAIR_ROUTE",
                onHighlight = {
                    playTick()
                    // Гасим прицел крестика — курсор только что провалился с него сюда.
                    setMapCrosshairFocused(false)
                    mapCrosshairLatLon()?.let { (lat, lon) -> showMapTapChoice(lat, lon) }
                    setAllMapTapChoiceFocusesHidden()
                    setMapTapChoiceRouteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapTapChoiceRoute) {
                        val (lat, lon) = pendingTapChoiceLatLon ?: return@flashButtonPressThenRun
                        playButton()
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
                    playTick()
                    setAllMapTapChoiceFocusesHidden()
                    setMapTapChoiceMarkerFocused(true)
                },
                // Звук на ENCBTN; сам провал в детей отрабатывает следом как обычно.
                onActivate = { playButton() },
                children = mapMarkerPopupChildrenNodes { pendingTapChoiceLatLon },
            ),
            MenuNode(
                id = "MAP_CTRL_CROSSHAIR_CANCEL",
                onHighlight = {
                    playTick()
                    setAllMapTapChoiceFocusesHidden()
                    setMapTapChoiceCancelFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapTapChoiceCancel) {
                        playButton()
                        setMapTapChoiceCancelFocused(false)
                        hideMapTapChoice()
                        navigator.popLevel()
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
        val popup = binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        return listOf(
            MenuNode(
                id = "MAP_MARKER_POPUP_MIC",
                onHighlight = {
                    playTick()
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
                        markerDictation.handleMicTap()
                    }
                },
            ),
            MenuNode(
                id = "MAP_MARKER_POPUP_CANCEL",
                onHighlight = {
                    playTick()
                    setAllMapMarkerPopupFocusesHidden()
                    setMapMarkerPopupCancelFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnMarkerNamePopupCancel) {
                        playButton()
                        setMapMarkerPopupCancelFocused(false)
                        // popLevel() внутри performMarkerNamePopupCancel(): число уровней зависит от новая это отметка или правка.
                        performMarkerNamePopupCancel()
                    }
                },
            ),
            MenuNode(
                id = "MAP_MARKER_POPUP_SAVE",
                onHighlight = {
                    playTick()
                    setAllMapMarkerPopupFocusesHidden()
                    setMapMarkerPopupSaveFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnMarkerNamePopupSave) {
                        setMapMarkerPopupSaveFocused(false)
                        playButton()
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
        repeat(popCount) { navigator.popLevel() }
    }
    /** Save — курсор идёт на карточку сохранённой отметки, для новой — на крестик. */
    /** Список обновляем ПОСЛЕ popLevel(): replaceChildrenOf() сверяет родителя верхнего уровня стека и
     * до подъёма всегда была no-op. */
    private fun performMarkerNamePopupSave() {
        val popup = binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup
        val name = popup.etMarkerNameValue.text.toString().ifBlank { activity.getString(R.string.marker_name_popup_heading) }
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
        repeat(popCount) { navigator.popLevel() }
        navigator.replaceChildrenOf("MAP_MARKER_LIST", mapMarkerListChildrenNodes(MapMenuState.ROOT))
        navigator.replaceChildrenOf("MAP_ROUTE_TO_MARKER", mapMarkerListChildrenNodes(MapMenuState.ROUTE_SUBMENU))
    }
    /** Панель построенного или активного маршрута; курсор попадает сюда программным pushLevel() из routeTo(). */
    private fun mapRouteControlsChildrenNodes(): List<MenuNode> {
        val mapScreen = binding.incLayoutTabItemsMap
        return if (mapRouteState == MapRouteState.ACTIVE) {
            listOf(
                MenuNode(
                    id = "MAP_ROUTE_CTRL_STOP",
                    onHighlight = {
                        playTick()
                        setAllMapRouteControlsFocusesHidden()
                        setMapRouteStopFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(mapScreen.btnMapRouteStop) {
                            playButton()
                            setMapRouteStopFocused(false)
                            cancelActiveRoute()
                            navigator.popLevel()
                        }
                    },
                ),
            )
        } else {
            listOf(
                MenuNode(
                    id = "MAP_ROUTE_CTRL_START",
                    onHighlight = {
                        playTick()
                        setAllMapRouteControlsFocusesHidden()
                        setMapRouteStartFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(mapScreen.btnMapRouteStart) {
                            playButton()
                            mapRouteState = MapRouteState.ACTIVE
                            updateRouteControlsVisibility()
                            navigator.replaceTopLevel(mapRouteControlsChildrenNodes())
                        }
                    },
                ),
                MenuNode(
                    id = "MAP_ROUTE_CTRL_CANCEL",
                    onHighlight = {
                        playTick()
                        setAllMapRouteControlsFocusesHidden()
                        setMapRouteCancelFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(mapScreen.btnMapRouteCancel) {
                            playButton()
                            setMapRouteCancelFocused(false)
                            cancelActiveRoute()
                            navigator.popLevel()
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
            if (mode() != PipBoyMode.PHONE) MenuNode(
                id = "MAP_CONTROLS",
                onHighlight = {
                    playTick()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("MAP_CONTROLS"))
                },
                children = mapControlChildrenNodes(MapControlMode.ROOT),
            ) else null,
            // "Поставить отметку" — та же панель, но крестик проваливается прямо в попап ввода имени.
            MenuNode(
                id = "MAP_PLACE_MARKER",
                onHighlight = {
                    playTick()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("PLACE_MARKER"))
                },
                children = mapControlChildrenNodes(MapControlMode.PLACE_MARKER),
            ),
            MenuNode(
                id = "MAP_ROUTE",
                onHighlight = {
                    playTick()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("ROUTE"))
                },
                children = mapRouteChildrenNodes(),
            ),
            MenuNode(
                id = "MAP_MARKER_LIST",
                onHighlight = {
                    playTick()
                    mapRootAdapter.setSelectedPositionSilently(indexOf("MARKER_LIST"))
                },
                childrenProvider = { mapMarkerListChildrenNodes(MapMenuState.ROOT) },
            ),
        ) + menuBackNode(
            { mapRootAdapter.setSelectedPositionSilently(indexOf("BACK")) },
            { mapRootAdapter.flashPressAnimation(indexOf("BACK")) },
        )
    }
    /** Дети MAP_ROUTE; порядок обязан совпадать с mapRouteSubmenuMeta, подменю показывает первый ребёнок. */
    private fun mapRouteChildrenNodes(): List<MenuNode> {
        return listOf(
            MenuNode(
                id = "MAP_ROUTE_TO_POINT",
                onHighlight = {
                    playTick()
                    showMapMenuState(MapMenuState.ROUTE_SUBMENU)
                    mapRouteSubmenuAdapter.setSelectedPositionSilently(0)
                },
                children = mapControlChildrenNodes(MapControlMode.ROUTE_TO_POINT),
            ),
            MenuNode(
                id = "MAP_ROUTE_TO_MARKER",
                onHighlight = {
                    playTick()
                    mapRouteSubmenuAdapter.setSelectedPositionSilently(1)
                },
                childrenProvider = { mapMarkerListChildrenNodes(MapMenuState.ROUTE_SUBMENU) },
            ),
            MenuNode(
                id = "MAP_ROUTE_BACK",
                onHighlight = {
                    playTick()
                    mapRouteSubmenuAdapter.setSelectedPositionSilently(2)
                },
                onActivate = {
                    mapRouteSubmenuAdapter.flashPressAnimation(2)
                    playConfirm()
                    showMapMenuState(MapMenuState.ROOT)
                    navigator.popLevel()
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
                    playTick()
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
                playTick()
                openListIfFirst(backIndex)
                mapMarkerListAdapter.setSelectedPositionSilently(backIndex)
            },
            onActivate = {
                mapMarkerListAdapter.flashPressAnimation(backIndex)
                playConfirm()
                showMapMenuState(returnState)
                navigator.popLevel()
            },
        )
        return markerNodes + backNode
    }
    /** Карточка отметки: Edit/Route/Delete/Back; Back — только режимы с физическим энкодером. */
    private fun mapMarkerDetailChildrenNodes(marker: MapMarker): List<MenuNode> {
        val mapScreen = binding.incLayoutTabItemsMap
        return listOfNotNull(
            MenuNode(
                id = "MAP_MARKER_EDIT",
                onHighlight = {
                    playTick()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailEditFocused(true)
                },
                // children, а не лист: попапу нужно собственное место в дереве, иначе курсору после Save неоткуда подниматься.
                children = mapMarkerPopupChildrenNodes(editingMarker = marker),
            ),
            MenuNode(
                id = "MAP_MARKER_ROUTE",
                onHighlight = {
                    playTick()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailRouteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapMarkerDetailRoute) {
                        playButton()
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
                    playTick()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailDeleteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapMarkerDetailDelete) {
                        playButton()
                        performMapMarkerDelete(marker)
                    }
                },
            ),
            if (mode() != PipBoyMode.PHONE) MenuNode(
                id = "MAP_MARKER_BACK",
                onHighlight = {
                    playTick()
                    setAllMapMarkerDetailFocusesHidden()
                    setMapMarkerDetailBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(mapScreen.btnMapMarkerDetailBack) {
                        playButton()
                        // Гасить свой прицел ПЕРЕД popLevel(), иначе он остаётся висеть на кнопке после возврата.
                        setMapMarkerDetailBackFocused(false)
                        navigator.popLevel()
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
        navigator.popLevel()
        navigator.replaceChildrenOf("MAP_MARKER_LIST", mapMarkerListChildrenNodes(MapMenuState.ROOT))
        navigator.replaceChildrenOf("MAP_ROUTE_TO_MARKER", mapMarkerListChildrenNodes(MapMenuState.ROUTE_SUBMENU))
    }

    // ===== ПРИЦЕЛЫ ЭНКОДЕРА =====

    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) =
        applyFocusBrackets(bracketsView, mode(), visible)
    /** Back на карточке отметки — та же схема, что у Menu на Гейгере. */
    private fun refreshMapMarkerDetailBackButtonVisibility() =
        applyEncoderOnlyVisible(mode(), binding.incLayoutTabItemsMap.btnMapMarkerDetailBack)
    private fun setMapZoomFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapZoomFocus, focused)
    }
    private fun setMapCenterFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapCenterFocus, focused)
    }
    private fun setMapPanVerticalFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapPanUpFocus, focused)
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapPanDownFocus, focused)
    }
    private fun setMapPanHorizontalFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapPanLeftFocus, focused)
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapPanRightFocus, focused)
    }
    private fun setMapCrosshairFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapCrosshairFocus, focused)
    }
    private fun setMapControlBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapControlBackFocus, focused)
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
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapMarkerDetailEditFocus, focused)
    }
    private fun setMapMarkerDetailRouteFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapMarkerDetailRouteFocus, focused)
    }
    private fun setMapMarkerDetailDeleteFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapMarkerDetailDeleteFocus, focused)
    }
    private fun setMapMarkerDetailBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapMarkerDetailBackFocus, focused)
    }
    private fun setAllMapMarkerDetailFocusesHidden() {
        setMapMarkerDetailEditFocused(false)
        setMapMarkerDetailRouteFocused(false)
        setMapMarkerDetailDeleteFocused(false)
        setMapMarkerDetailBackFocused(false)
    }
    /** Тот же приём на панели выбора [Route]/[Marker]/[Cancel]. */
    private fun setMapTapChoiceRouteFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapTapChoiceRouteFocus, focused)
    }
    private fun setMapTapChoiceMarkerFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapTapChoiceMarkerFocus, focused)
    }
    private fun setMapTapChoiceCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapTapChoiceCancelFocus, focused)
    }
    private fun setAllMapTapChoiceFocusesHidden() {
        setMapTapChoiceRouteFocused(false)
        setMapTapChoiceMarkerFocused(false)
        setMapTapChoiceCancelFocused(false)
    }
    /** Тот же приём на попапе имени отметки — Cancel и Save. */
    private fun setMapMarkerPopupMicFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupMicFocus, focused)
    }
    private fun setMapMarkerPopupCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupCancelFocus, focused)
    }
    private fun setMapMarkerPopupSaveFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.incLayoutTabItemsMapNamePopup.viewMarkerNamePopupSaveFocus, focused)
    }
    private fun setAllMapMarkerPopupFocusesHidden() {
        setMapMarkerPopupMicFocused(false)
        setMapMarkerPopupCancelFocused(false)
        setMapMarkerPopupSaveFocused(false)
    }
    /** Тот же приём на панели маршрута — Start/Cancel/Stop. */
    private fun setMapRouteStartFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapRouteStartFocus, focused)
    }
    private fun setMapRouteCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapRouteCancelFocus, focused)
    }
    private fun setMapRouteStopFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsMap.viewMapRouteStopFocus, focused)
    }
    private fun setAllMapRouteControlsFocusesHidden() {
        setMapRouteStartFocused(false)
        setMapRouteCancelFocused(false)
        setMapRouteStopFocused(false)
    }

    // ===== ТАЧ: АДАПТЕРЫ И КНОПКИ ЭКРАНА =====

    /** Зовётся из onCreate() активности на том же месте, где раньше стоял блок карты. */
    fun setup() {
        val mapMenu = binding.incLayoutTabItemsMap
        mapRootAdapter = SidebarMenuAdapter(
            items = mapRootSidebarItems(),
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { _, item ->
                // Безусловная синхронизация: курсор должен перепрыгнуть сюда даже из другой ветки дерева.
                playConfirm()
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // Молча: onHighlight узла MAP заново открыл бы экран и стёр текущее состояние карты.
                    syncMapEncoderPathSilently(emptyList())
                    syncRow2Active()
                } else {
                    // "+ 0" — тап равносилен ENCBTN: у всех четырёх пунктов есть дети, курсор садится на первого.
                    suppressTickAround { syncMapEncoderPath(listOf(mapRootIndex(item.payload), 0)) }
                    mapRootMeta.first { it.key == item.payload }.action()
                }
            },
        )
        mapMenu.recyclerMapMenuRoot.layoutManager = LinearLayoutManager(activity)
        mapMenu.recyclerMapMenuRoot.adapter = mapRootAdapter
        mapRouteSubmenuAdapter = SidebarMenuAdapter(
            items = mapRouteSubmenuMeta.map { meta -> SidebarMenuItem(payload = meta.key, label = activity.getString(meta.labelRes)) },
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            // {} — см. подробный комментарий у specialAdapter (roadmap, этап 28), тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                // BACK — особый случай: путь останавливается на родителе, там курсор окажется после popLevel().
                val path = if (item.payload == "BACK") {
                    listOf(mapRootIndex("ROUTE"))
                } else {
                    listOf(mapRootIndex("ROUTE"), position, 0)
                }
                playConfirm()
                suppressTickAround { syncMapEncoderPath(path) }
                mapRouteSubmenuMeta.first { it.key == item.payload }.action()
            },
        )
        mapMenu.recyclerMapMenuRouteSubmenu.layoutManager = LinearLayoutManager(activity)
        mapMenu.recyclerMapMenuRouteSubmenu.adapter = mapRouteSubmenuAdapter
        // Тик глушим везде ниже: цель каждой синхронизации играет его сама в своём onHighlight.
        mapMenu.btnMapMarkerDetailEdit.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            // "+ 0, 0" — EDIT теперь узел с детьми, тап проваливается сразу в первого, MIC.
            if (markerIndex != -1) suppressTickAround { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 0 + 0) }
            playButton()
            showMarkerNamePopupForEdit(marker)
        }
        mapMenu.btnMapMarkerDetailRoute.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) suppressTickAround { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 1) }
            playButton()
            // Карточка отметки всегда достигается через "Список меток".
            routeTo(marker.lat, marker.lon, listOf(mapRootIndex("MARKER_LIST")))
            hideMarkerDetail()
        }
        mapMenu.btnMapMarkerDetailDelete.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) suppressTickAround { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 2) }
            playButton()
            performMapMarkerDelete(marker)
        }
        // Back только поднимает курсор в список отметок, самой отметки не касается.
        mapMenu.btnMapMarkerDetailBack.setOnClickListener {
            val marker = selectedMarkerForDetail ?: return@setOnClickListener
            val markerIndex = markers.indexOfFirst { it.id == marker.id }
            if (markerIndex != -1) suppressTickAround { syncMapEncoderPath(mapMarkerListParentPath() + markerIndex + 3) }
            playButton()
            setMapMarkerDetailBackFocused(false)
            navigator.popLevel()
        }
        refreshMapMarkerDetailBackButtonVisibility()
        // Zoom и Center видны всегда и не входят в общую группу — поэтому показываем оверлей здесь явно,
        // иначе курсор переключался, а уголки, крестик и "←" оставались невидимы.
        mapMenu.btnMapZoomIn.setOnClickListener {
            playConfirm()
            setMapControlOverlayVisible(true)
            suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 3) }
            zoomMapBy(MAP_ZOOM_STEP_FACTOR)
        }
        mapMenu.btnMapZoomOut.setOnClickListener {
            playConfirm()
            setMapControlOverlayVisible(true)
            suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 3) }
            zoomMapBy(1f / MAP_ZOOM_STEP_FACTOR)
        }
        mapMenu.btnMapCenter.setOnClickListener {
            playConfirm()
            setMapControlOverlayVisible(true)
            suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 4) }
            recenterMapOnUser()
        }
        // Уголки, прицел и "←" доступны и тачу — кнопки реально видны на экране, не только энкодеру.
        val mapPanStepPx = activity.resources.displayMetrics.density * MAP_PAN_STEP_DP
        mapMenu.btnMapPanUp.setOnClickListener { playConfirm(); suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 1) }; panMapBy(0f, mapPanStepPx) }
        mapMenu.btnMapPanDown.setOnClickListener { playConfirm(); suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 1) }; panMapBy(0f, -mapPanStepPx) }
        mapMenu.btnMapPanLeft.setOnClickListener { playConfirm(); suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 2) }; panMapBy(mapPanStepPx, 0f) }
        mapMenu.btnMapPanRight.setOnClickListener { playConfirm(); suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 2) }; panMapBy(-mapPanStepPx, 0f) }
        mapMenu.viewMapCrosshair.setOnClickListener {
            // Полный путь до того, что окажется на экране: тап по крестику равносилен ENCBTN и проваливается в детей.
            val (lat, lon) = mapCrosshairLatLon() ?: return@setOnClickListener
            playConfirm()
            when (mapControlMode) {
                MapControlMode.ROUTE_TO_POINT -> {
                    suppressTickAround { syncMapEncoderPath(mapControlModeRootPath() + 0) }
                    routeTo(lat, lon, listOf(mapRootIndex("ROUTE")))
                }
                MapControlMode.PLACE_MARKER -> {
                    suppressTickAround { syncMapEncoderPath(mapMarkerPopupParentPath() + 0) }
                    showMarkerNamePopupForNewMarker(lat, lon)
                }
                MapControlMode.ROOT -> {
                    suppressTickAround { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0)) }
                    showMapTapChoice(lat, lon)
                }
            }
        }
        mapMenu.btnMapControlBack.setOnClickListener {
            // Для ROUTE_TO_POINT боковое меню нужно явно вернуть в ROOT — его onHighlight этого не делает сам.
            val wasRouteToPoint = mapControlMode == MapControlMode.ROUTE_TO_POINT
            playButton()
            suppressTickAround { syncMapEncoderPath(mapSidebarRootPathForMode()) }
            setMapControlOverlayVisible(false)
            if (wasRouteToPoint) showMapMenuState(MapMenuState.ROOT)
        }
        mapMenu.btnMapTapChoiceRoute.setOnClickListener {
            suppressTickAround { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 0)) }
            val (lat, lon) = pendingTapChoiceLatLon ?: return@setOnClickListener
            playButton()
            hideMapTapChoice()
            // Панель [Route]/[Marker]/[Cancel] бывает только в режиме ROOT.
            routeTo(lat, lon, listOf(mapRootIndex("MAP_CONTROLS")))
        }
        mapMenu.btnMapTapChoiceMarker.setOnClickListener {
            // Координату читаем и звук играем ДО синхронизации: onHighlight цели обнуляет pendingTapChoiceLatLon.
            val (lat, lon) = pendingTapChoiceLatLon ?: return@setOnClickListener
            playButton()
            // "+ 0" — Marker проваливается в попап (Cancel/Save), не остаётся на себе самой.
            suppressTickAround { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 1, 0)) }
            hideMapTapChoice()
            showMarkerNamePopupForNewMarker(lat, lon)
        }
        mapMenu.btnMapTapChoiceCancel.setOnClickListener {
            playButton()
            suppressTickAround { syncMapEncoderPath(listOf(mapRootIndex("MAP_CONTROLS"), 0, 2)) }
            hideMapTapChoice()
        }
        mapMenu.btnMapRouteStart.setOnClickListener {
            // syncPushedCursor() вернёт false, если энкодер сейчас не на этой запушенной панели.
            playButton()
            val onThisPanel = navigator.syncPushedCursor("MAP_ROUTE_CONTROLS", 0)
            mapRouteState = MapRouteState.ACTIVE
            updateRouteControlsVisibility()
            if (onThisPanel) navigator.replaceTopLevel(mapRouteControlsChildrenNodes())
        }
        mapMenu.btnMapRouteCancel.setOnClickListener {
            playButton()
            val onThisPanel = navigator.syncPushedCursor("MAP_ROUTE_CONTROLS", 1)
            cancelActiveRoute()
            if (onThisPanel) navigator.popLevel()
        }
        mapMenu.btnMapRouteStop.setOnClickListener {
            playButton()
            val onThisPanel = navigator.syncPushedCursor("MAP_ROUTE_CONTROLS", 0)
            cancelActiveRoute()
            if (onThisPanel) navigator.popLevel()
        }
        val markerNamePopup = mapMenu.incLayoutTabItemsMapNamePopup
        // Индексы MIC(0)/CANCEL(1)/SAVE(2) — микрофон стал первым узлом, Cancel и Save сдвинулись.
        markerNamePopup.btnMarkerNamePopupMic.setOnClickListener {
            // Синхронизируем только курсор и прицел: громкий путь вызвал бы onHighlight узла MIC,
            // а тот сбрасывает поле ввода и стёр бы надиктованное.
            syncMapEncoderPathSilently(mapMarkerPopupParentPath() + 0)
            setAllMapMarkerPopupFocusesHidden()
            setMapMarkerPopupMicFocused(true)
            markerDictation.handleMicTap()
        }
        markerNamePopup.btnMarkerNamePopupCancel.setOnClickListener {
            playButton()
            suppressTickAround { syncMapEncoderPath(mapMarkerPopupParentPath() + 1) }
            performMarkerNamePopupCancel()
        }
        markerNamePopup.btnMarkerNamePopupSave.setOnClickListener {
            playButton()
            suppressTickAround { syncMapEncoderPath(mapMarkerPopupParentPath() + 2) }
            performMarkerNamePopupSave()
        }
    }
}
