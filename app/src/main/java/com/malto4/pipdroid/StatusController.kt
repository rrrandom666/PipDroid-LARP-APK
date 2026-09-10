package com.malto4.pipdroid

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.malto4.pipdroid.databinding.ActivityMainBinding

/** Фаза ранения и её тяжесть — единый источник истины для лица, кнопок статуса и исхода таймера. */
internal enum class WoundPhase { NONE, BLEED, BANDAGE, STUNNED, DEAD }
internal enum class WoundSeverity { LIGHT, HEAVY }

/** Часть тела для отметки CRIPPLED; порядок совпадает с порядком узлов в дереве энкодера. */
internal enum class BodyPart { HEAD, LEFT_ARM, TORSO, RIGHT_ARM, LEFT_LEG, RIGHT_LEG }

/** Раздел STATS/Status вместе со всей системой ранений: фаза, таймер, CRIPPLED по шести частям тела,
 * смерть и воскрешение. */
/** Контроллер владеет фазой ранения целиком — наружу торчат только предикаты (isWoundActive,
 * hasActiveWoundTimer, isDead) и команды. Таймер принадлежит ClockController и приходит сюда
 * колбэками startTimer/stopTimer, а сам ClockController фазы по-прежнему не знает. */
internal class StatusController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val navigator: MenuNavigator,
    private val mode: () -> PipBoyMode,
    private val accentColor: () -> Int,
    private val selectedButtonRes: () -> Int,
    private val scrollbarThumbRes: () -> Int,
    private val backSidebarItem: (enabled: Boolean) -> SidebarMenuItem<String>,
    private val menuBackNode: (onHighlight: () -> Unit, onBeforePop: () -> Unit) -> List<MenuNode>,
    private val playTick: () -> Unit,
    private val playButton: () -> Unit,
    private val playConfirm: () -> Unit,
    private val playError: () -> Unit,
    private val playDamage: () -> Unit,
    private val playStimpack: () -> Unit,
    private val suppressTickAround: (block: () -> Unit) -> Unit,
    private val syncEncoderPath: (path: List<Int>) -> Unit,
    private val syncEncoderPathSilently: (path: List<Int>) -> Unit,
    private val syncRow2Active: () -> Unit,
    private val enableBottomButtons: (Boolean) -> Unit,
    private val enableTopSwipe: (Boolean) -> Unit,
    private val startTimer: (durationSeconds: Int) -> Unit,
    private val stopTimer: () -> Unit,
    private val skipTimerToEnd: () -> Unit,
    private val setTimerPauseAllowed: (Boolean) -> Unit,
) {
    private var woundPhase = WoundPhase.NONE
    private var woundSeverity = WoundSeverity.LIGHT
    private var crippledHead = false
    private var crippledTorso = false
    private var crippledLeftArm = false
    private var crippledRightArm = false
    private var crippledLeftLeg = false
    private var crippledRightLeg = false

    private var statsCndPopupIsHolding = false

    private val handler = Handler(Looper.getMainLooper())
    /** Пятисекундное удержание фигуры — пасхалка поверх обычного тапа. */
    private val longPressRunnable = Runnable {
        if (statsCndPopupIsHolding) {
            binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.root.visibility = View.VISIBLE
            binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.layoutTabStatusCndContent.visibility = View.GONE
            binding.incLayoutFilterModification.root.visibility = View.GONE
            enableBottomButtons(false)
            enableTopSwipe(false)
        }
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

    // ===== ПУБЛИЧНАЯ ПОВЕРХНОСТЬ =====

    /** Активное ранение любой фазы, включая смерть: этим гейтится пауза таймера на экране Таймера. */
    val isWoundActive: Boolean get() = woundPhase != WoundPhase.NONE
    /** Идёт отсчёт ранения — только в этих фазах в дереве есть узлы частей тела. */
    val hasActiveWoundTimer: Boolean get() = woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD
    val isDead: Boolean get() = woundPhase == WoundPhase.DEAD

    /** Порядок блоков внутри — тот же, что был у них в onCreate(). */
    fun setup() {
        setupWoundList()
        updateWoundButtonsUI()
        setupWoundButtons()
        setupFigureTouchTargets()
        applyAccentTint()
    }

    /** Ветка STATUS дерева энкодера — обёртку узла строит statsMenuRoot() активности. */
    /** Дети STATUS: пока таймер ранения актуален, доступны только Stop и шесть частей тела, список ранений
     * недостижим совсем. */
    /** Гашение прицелов в обычной ветке — подстраховка идемпотентности при любой пересборке списка. */
    fun childrenNodes(): List<MenuNode> {
        return if (woundPhase == WoundPhase.DEAD) {
            // В DEAD курсор встаёт на персонажа, ENCBTN воскрешает тем же путём и с тем же звуком, что тап.
            setWoundStopButtonFocused(false)
            setAllCrippledFocusesHidden()
            listOf(
                MenuNode(
                    id = "REVIVE",
                    onHighlight = { setDeadReviveFocused(true) },
                    onActivate = {
                        playStimpack()
                        revive()
                    },
                )
            )
        } else if (woundPhase != WoundPhase.NONE) {
            listOf(
                MenuNode(
                    id = "STOP",
                    onHighlight = {
                        playTick()
                        setAllCrippledFocusesHidden()
                        setWoundStopButtonFocused(true)
                    },
                    onActivate = {
                        flashButtonPressThenRun(
                            binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.btnTabStatusWoundStop,
                        ) {
                            playButton()
                            stopWoundEarly()
                        }
                    },
                ),
            ) + BODY_PART_NODE_ORDER.map { part ->
                MenuNode(
                    id = "BODYPART_${part.name}",
                    onHighlight = {
                        playTick()
                        setWoundStopButtonFocused(false)
                        setAllCrippledFocusesHidden()
                        setCrippledFocused(part, true)
                    },
                    onActivate = {
                        if (isCrippled(part)) playStimpack() else playDamage()
                        setCrippled(part, !isCrippled(part))
                    },
                )
            }
        } else {
            setWoundStopButtonFocused(false)
            setDeadReviveFocused(false)
            setAllCrippledFocusesHidden()
            statusMeta.mapIndexed { index, meta ->
                MenuNode(
                    id = meta.key,
                    // Звук перемещения курсора — тот же тик, что у SPECIAL и Skills, но не через playSelectSound адаптера.
                    onHighlight = {
                        playTick()
                        statusAdapter.setSelectedPositionSilently(index)
                    },
                    onActivate = {
                        statusAdapter.selectPosition(index)
                        statusAdapter.flashPressAnimation(index)
                    },
                )
            } + menuBackNode(
                { statusAdapter.setSelectedPositionSilently(statusMeta.size) },
                { statusAdapter.flashPressAnimation(statusMeta.size) },
            )
        }
    }

    fun refreshModeGating() {
        statusAdapter.setItems(statusSidebarItems(), resetSelection = false)
    }

    /** Открытие вкладки Status тапом: курсор стоит на самом узле, ни один пункт ещё не выбран. */
    fun onStatusTabOpened() {
        statusAdapter.clearSelection()
        setWoundStopButtonFocused(false)
        setDeadReviveFocused(false)
        setAllCrippledFocusesHidden()
    }

    /** Ранение голосовой командой; часть тела отмечается отдельно, поверх общего таймера. */
    fun startWound(severity: WoundSeverity) =
        startWoundTimer(WoundPhase.BLEED, severity, WOUND_BLEED_BANDAGE_DURATION_SECONDS)

    fun startStun() = startWoundTimer(WoundPhase.STUNNED, null, STUN_DURATION_SECONDS)

    /** Revive — полный сброс, вся система статусов на самоучёте игрока. */
    fun revive() {
        if (woundPhase != WoundPhase.DEAD) return
        woundPhase = WoundPhase.NONE
        applyWoundFace()
        updateWoundButtonsUI()
        refreshEncoderChildren()
        updateWoundStatusLine()
        applyReviveVisuals()
    }

    /** [Стоп] на STATUS и сброс таймера на экране Таймера при активном ранении — те же последствия. */
    fun stopWoundEarly() {
        when (woundPhase) {
            WoundPhase.BLEED -> startWoundTimer(WoundPhase.BANDAGE, woundSeverity, WOUND_BLEED_BANDAGE_DURATION_SECONDS)
            WoundPhase.BANDAGE, WoundPhase.STUNNED -> healWoundsToHealthy()
            else -> {}
        }
    }

    /** Натуральное истечение — из fireTimer() часов, когда таймер принадлежит системе ранений. */
    fun onTimerFired() {
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

    fun onCountdownTick(remainingSeconds: Int) {
        if (woundPhase != WoundPhase.NONE && woundPhase != WoundPhase.DEAD) {
            updateWoundCountdownText(remainingSeconds)
        }
    }

    /** Подпись над отсчётом на экране Таймера: у таймера может быть стадия ранения, для обычного запуска она пустая. */
    fun timerLabelText(): String = when (woundPhase) {
        WoundPhase.STUNNED -> activity.getString(R.string.status_wound_stunned_label)
        WoundPhase.BLEED -> activity.getString(R.string.status_wound_bleeding_label)
        WoundPhase.BANDAGE -> activity.getString(R.string.status_wound_bandage_label)
        else -> ""
    }

    /** Явная установка CRIPPLED — для идемпотентных голосовых команд. */
    fun setCrippled(part: BodyPart, crippled: Boolean) {
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        when (part) {
            BodyPart.HEAD -> {
                crippledHead = crippled
                applyCrippledVisual(cnd.imgTabStatusCndPipboyHead, cnd.tvTabStatusCndPipboyHeadHpCrippled, crippledHead, R.drawable.man_head, R.drawable.head_broken)
            }
            BodyPart.TORSO -> {
                crippledTorso = crippled
                applyCrippledVisual(cnd.imgTabStatusCndPipboyTorso, cnd.tvTabStatusCndPipboyTorsoHpCrippled, crippledTorso, R.drawable.torso, R.drawable.torso_broken)
            }
            BodyPart.LEFT_ARM -> {
                crippledLeftArm = crippled
                applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftArm, cnd.tvTabStatusCndPipboyLeftArmHpCrippled, crippledLeftArm, R.drawable.man_arm_left, R.drawable.left_arm_broken)
            }
            BodyPart.RIGHT_ARM -> {
                crippledRightArm = crippled
                applyCrippledVisual(cnd.imgTabStatusCndPipboyRightArm, cnd.tvTabStatusCndPipboyRightArmHpCrippled, crippledRightArm, R.drawable.man_arm_right, R.drawable.right_arm_broken)
            }
            BodyPart.LEFT_LEG -> {
                crippledLeftLeg = crippled
                applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftLeg, cnd.tvTabStatusCndPipboyLeftLegHpCrippled, crippledLeftLeg, R.drawable.man_leg_left, R.drawable.left_leg_broken)
            }
            BodyPart.RIGHT_LEG -> {
                crippledRightLeg = crippled
                applyCrippledVisual(cnd.imgTabStatusCndPipboyRightLeg, cnd.tvTabStatusCndPipboyRightLegHpCrippled, crippledRightLeg, R.drawable.man_leg_right, R.drawable.right_leg_broken)
            }
        }
    }

    fun saveState(outState: Bundle) {
        outState.putString(KEY_WOUND_PHASE, woundPhase.name)
        outState.putString(KEY_WOUND_SEVERITY, woundSeverity.name)
        outState.putBoolean(KEY_CRIPPLED_HEAD, crippledHead)
        outState.putBoolean(KEY_CRIPPLED_TORSO, crippledTorso)
        outState.putBoolean(KEY_CRIPPLED_LEFT_ARM, crippledLeftArm)
        outState.putBoolean(KEY_CRIPPLED_RIGHT_ARM, crippledRightArm)
        outState.putBoolean(KEY_CRIPPLED_LEFT_LEG, crippledLeftLeg)
        outState.putBoolean(KEY_CRIPPLED_RIGHT_LEG, crippledRightLeg)
        outState.putInt(KEY_STATUS_CURSOR_ROW, statusAdapter.selectedPosition())
    }

    /** Восстановление идёт до восстановления таймера в ClockController: тот читает фазу через колбэки. */
    fun restoreState(savedInstanceState: Bundle) {
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
    }

    /** Вторая половина восстановления — после ClockController, потому что смерть гасит таймер. */
    fun restoreVisuals() {
        if (woundPhase == WoundPhase.DEAD) {
            applyDeathVisuals()
        } else {
            val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
            applyCrippledVisual(cnd.imgTabStatusCndPipboyHead, cnd.tvTabStatusCndPipboyHeadHpCrippled, crippledHead, R.drawable.man_head, R.drawable.head_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyTorso, cnd.tvTabStatusCndPipboyTorsoHpCrippled, crippledTorso, R.drawable.torso, R.drawable.torso_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftArm, cnd.tvTabStatusCndPipboyLeftArmHpCrippled, crippledLeftArm, R.drawable.man_arm_left, R.drawable.left_arm_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyRightArm, cnd.tvTabStatusCndPipboyRightArmHpCrippled, crippledRightArm, R.drawable.man_arm_right, R.drawable.right_arm_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyLeftLeg, cnd.tvTabStatusCndPipboyLeftLegHpCrippled, crippledLeftLeg, R.drawable.man_leg_left, R.drawable.left_leg_broken)
            applyCrippledVisual(cnd.imgTabStatusCndPipboyRightLeg, cnd.tvTabStatusCndPipboyRightLegHpCrippled, crippledRightLeg, R.drawable.man_leg_right, R.drawable.right_leg_broken)
        }
    }

    // ===== СПИСОК РАНЕНИЙ =====

    /** Звук решает сам onSelect; enabled у всех трёх пунктов следует за woundPhase и обновляется
     * в updateWoundButtonsUI(), здесь только начальное состояние. */
    private fun setupWoundList() {
        statusAdapter = SidebarMenuAdapter(
            items = statusSidebarItems(),
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: он должен доехать сюда, даже если энкодер был в другой ветке.
                if (woundPhase != WoundPhase.NONE) {
                    // Пока активен таймер ранения, курсор должен быть на STOP — единственном реальном действии дерева.
                    playError()
                    // Цель сама играет тик в onHighlight и задвоила бы звук ошибки выше.
                    suppressTickAround { syncEncoderPath(listOf(0)) }
                } else if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    // confirm, а не тик — тач всегда даёт confirm.
                    playConfirm()
                    syncEncoderPath(emptyList())
                    syncRow2Active()
                } else {
                    val meta = statusMeta.first { it.key == item.payload }
                    // Звук нажатия, тот же что у +/-; листание даёт тик из onHighlight.
                    playConfirm()
                    // Silently: startWoundTimer() сама перестроит детей и громко переставит курсор на новый STOP.
                    syncEncoderPathSilently(listOf(position))
                    meta.action()
                }
            },
        )
        binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.layoutManager = LinearLayoutManager(activity)
        binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.adapter = statusAdapter
        // itemAnimator = null: DefaultItemAnimator по окончании кросс-фейда сбрасывает alpha в 1.0 и
        // затирает затенение недоступных пунктов, которое адаптер ставит тем же alpha.
        binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusButtons.recyclerTabStatusButtons.itemAnimator = null
    }

    private fun statusSidebarItems(): List<SidebarMenuItem<String>> {
        // "В меню" дизейблится вместе со списком ранений; энкодер туда в это время вообще не попадает.
        val enabled = woundPhase == WoundPhase.NONE
        val items = statusMeta.map { meta ->
            SidebarMenuItem(payload = meta.key, label = activity.getString(meta.labelRes), enabled = enabled)
        }
        return if (mode() != PipBoyMode.PHONE) items + backSidebarItem(enabled) else items
    }

    // ===== КНОПКИ И ТАЧ-ЦЕЛИ =====

    /** Клики по LIGHT/HEAVY/STUNNED живут внутри statusAdapter, отдельные обработчики не нужны. */
    private fun setupWoundButtons() {
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        cnd.btnTabStatusWoundStop.setOnClickListener {
            playButton()
            stopWoundEarly()
            // Курсор следует за тачем независимо от того, где он был; тик глушим — цель играет его сама.
            suppressTickAround { syncEncoderPath(listOf(0)) }
        }
        // [Skip] переносит целевой epoch в прошлое, а не дублирует логику срабатывания.
        cnd.btnTabStatusWoundSkip.setOnClickListener { skipTimerToEnd() }
        cnd.incLayoutTabStatsCndPopup.btnTabStatsCndPopupClose.setOnClickListener {
            cnd.incLayoutTabStatsCndPopup.root.visibility = View.GONE
            cnd.layoutTabStatusCndContent.visibility = View.VISIBLE
            enableBottomButtons(true)
            enableTopSwipe(true)
        }
    }

    /** Тач-цели на всей фигуре: каждая часть тела и сам контейнер для пустых промежутков. */
    private fun setupFigureTouchTargets() {
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        setupFigureTouchTarget(cnd.layoutTabStatusCndPipboy) {}
        // Гейт по фазе стоит внутри колбэка, а не снаружи: woundPhase меняется уже после установки целей.
        val targets = listOf(
            BodyPart.HEAD to cnd.imgTabStatusCndPipboyHead,
            BodyPart.TORSO to cnd.imgTabStatusCndPipboyTorso,
            BodyPart.LEFT_ARM to cnd.imgTabStatusCndPipboyLeftArm,
            BodyPart.RIGHT_ARM to cnd.imgTabStatusCndPipboyRightArm,
            BodyPart.LEFT_LEG to cnd.imgTabStatusCndPipboyLeftLeg,
            BodyPart.RIGHT_LEG to cnd.imgTabStatusCndPipboyRightLeg,
        )
        targets.forEach { (part, view) ->
            setupFigureTouchTarget(view) {
                // Звук выбирается по состоянию ДО переключения: damage на здоровую часть, stimpack на уже сломанную.
                if (isCrippled(part)) playStimpack() else playDamage()
                setCrippled(part, !isCrippled(part))
                if (hasActiveWoundTimer) syncEncoderPath(listOf(BODY_PART_NODE_ORDER.indexOf(part) + 1))
            }
        }
    }

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
                    val popupShown = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.incLayoutTabStatsCndPopup.root.visibility == View.VISIBLE
                    if (!popupShown) {
                        if (woundPhase == WoundPhase.DEAD) {
                            playStimpack()
                            revive()
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

    /** Кнопки таймера ранения и прицелы тонируются текущим акцентом темы. */
    private fun applyAccentTint() {
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        val woundAccentTint = ColorStateList.valueOf(accentColor())
        cnd.btnTabStatusWoundStop.backgroundTintList = woundAccentTint
        cnd.btnTabStatusWoundSkip.backgroundTintList = woundAccentTint
        cnd.viewWoundStopFocus.backgroundTintList = woundAccentTint
        cnd.viewDeadReviveFocus.backgroundTintList = woundAccentTint
        cnd.viewCrippledHeadFocus.backgroundTintList = woundAccentTint
        cnd.viewCrippledTorsoFocus.backgroundTintList = woundAccentTint
        cnd.viewCrippledLeftArmFocus.backgroundTintList = woundAccentTint
        cnd.viewCrippledRightArmFocus.backgroundTintList = woundAccentTint
        cnd.viewCrippledLeftLegFocus.backgroundTintList = woundAccentTint
        cnd.viewCrippledRightLegFocus.backgroundTintList = woundAccentTint
    }

    // ===== ПРИЦЕЛЫ ЭНКОДЕРА =====

    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) =
        applyFocusBrackets(bracketsView, mode(), visible)
    private fun setWoundStopButtonFocused(focused: Boolean) {
        setFocusBracketsVisible(
            binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewWoundStopFocus,
            focused,
        )
    }
    private fun setDeadReviveFocused(focused: Boolean) {
        setFocusBracketsVisible(
            binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.viewDeadReviveFocus,
            focused,
        )
    }
    /** Прицелы на частях тела; setAllCrippledFocusesHidden() — идемпотентная подстраховка при выходе из ветки. */
    private fun setCrippledFocused(part: BodyPart, focused: Boolean) {
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        val brackets = when (part) {
            BodyPart.HEAD -> cnd.viewCrippledHeadFocus
            BodyPart.TORSO -> cnd.viewCrippledTorsoFocus
            BodyPart.LEFT_ARM -> cnd.viewCrippledLeftArmFocus
            BodyPart.RIGHT_ARM -> cnd.viewCrippledRightArmFocus
            BodyPart.LEFT_LEG -> cnd.viewCrippledLeftLegFocus
            BodyPart.RIGHT_LEG -> cnd.viewCrippledRightLegFocus
        }
        setFocusBracketsVisible(brackets, focused)
    }
    private fun setAllCrippledFocusesHidden() {
        BodyPart.values().forEach { setCrippledFocused(it, false) }
    }

    /** Живая пересборка узла STATUS при смене woundPhase; no-op, если игрок сейчас не внутри списка Status. */
    private fun refreshEncoderChildren() {
        navigator.replaceChildrenOf("STATUS", childrenNodes())
    }

    // ===== ФАЗА РАНЕНИЯ =====

    private fun woundFaceDrawable(): Int = when (woundPhase) {
        WoundPhase.NONE -> R.drawable.man_face
        WoundPhase.BLEED, WoundPhase.BANDAGE -> if (woundSeverity == WoundSeverity.LIGHT) R.drawable.face_02 else R.drawable.face_03
        WoundPhase.STUNNED, WoundPhase.DEAD -> R.drawable.face_04
    }
    private fun applyWoundFace() {
        binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.imgTabStatusCndPipboyFace.setImageResource(woundFaceDrawable())
    }
    private fun updateWoundButtonsUI() {
        // Затенение следует за woundPhase и не блокирует тап; курсор не трогаем — его двигают явно.
        if (::statusAdapter.isInitialized) {
            // Через statusSidebarItems(), а не инлайн из statusMeta: пункт "В меню" дописывается только там.
            statusAdapter.setItems(statusSidebarItems(), resetSelection = false)
        }
        setTimerPauseAllowed(!hasActiveWoundTimer)
    }
    private fun woundStageLabel(): String = when (woundPhase) {
        WoundPhase.BLEED -> activity.getString(R.string.status_wound_bleeding_label)
        WoundPhase.BANDAGE -> activity.getString(R.string.status_wound_bandage_label)
        else -> ""
    }
    /** Панель статуса справа от фигуры: статику ставит этот метод, текст с таймером — updateWoundCountdownText(). */
    private fun updateWoundStatusLine() {
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        when (woundPhase) {
            WoundPhase.NONE -> {
                cnd.tvTabStatusWoundText.text = activity.getString(R.string.status_text_healthy)
                cnd.layoutTabStatusWoundButtons.visibility = View.GONE
            }
            WoundPhase.DEAD -> {
                cnd.tvTabStatusWoundText.text =
                    activity.getString(R.string.status_text_dead_header) + "\n" + activity.getString(R.string.status_revive_hint)
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
            WoundPhase.STUNNED -> activity.getString(R.string.status_text_stunned) + timerText
            WoundPhase.BLEED, WoundPhase.BANDAGE -> activity.getString(R.string.status_text_wounded) + woundStageLabel() + ": " + timerText
            else -> return
        }
        binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent.tvTabStatusWoundText.text = text
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
        refreshEncoderChildren()
        startTimer(durationSeconds)
        updateWoundStatusLine()
        updateWoundCountdownText(durationSeconds)
    }
    /** Вылечен — общий финал для перевязки и оглушения; CRIPPLED снимается со всех шести частей. */
    private fun healWoundsToHealthy() {
        woundPhase = WoundPhase.NONE
        applyWoundFace()
        updateWoundButtonsUI()
        refreshEncoderChildren()
        updateWoundStatusLine()
        stopTimer()
        BodyPart.values().forEach { setCrippled(it, false) }
    }
    private fun killCharacter() {
        woundPhase = WoundPhase.DEAD
        applyWoundFace()
        updateWoundButtonsUI()
        refreshEncoderChildren()
        updateWoundStatusLine()
        stopTimer()
        applyDeathVisuals()
    }

    // ===== CRIPPLED ПО ЧАСТЯМ ТЕЛА =====

    private fun isCrippled(part: BodyPart): Boolean = when (part) {
        BodyPart.HEAD -> crippledHead
        BodyPart.TORSO -> crippledTorso
        BodyPart.LEFT_ARM -> crippledLeftArm
        BodyPart.RIGHT_ARM -> crippledRightArm
        BodyPart.LEFT_LEG -> crippledLeftLeg
        BodyPart.RIGHT_LEG -> crippledRightLeg
    }
    private fun applyCrippledVisual(bodyPart: ImageView, label: TextView, crippled: Boolean, normalRes: Int, brokenRes: Int) {
        bodyPart.setImageResource(if (crippled) brokenRes else normalRes)
        label.visibility = if (crippled) View.VISIBLE else View.GONE
    }
    /** В смерти все шесть частей рисуются сломанными, но подпись остаётся одна — DEAD на туловище. */
    private fun applyDeathVisuals() {
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        crippledHead = true; crippledTorso = true; crippledLeftArm = true
        crippledRightArm = true; crippledLeftLeg = true; crippledRightLeg = true
        cnd.imgTabStatusCndPipboyHead.setImageResource(R.drawable.head_broken)
        cnd.tvTabStatusCndPipboyHeadHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyTorso.setImageResource(R.drawable.torso_broken)
        cnd.tvTabStatusCndPipboyTorsoHpCrippled.text = activity.getString(R.string.stats_cnd_status_dead)
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
        val cnd = binding.incLayoutTabStatsStatus.incLayoutTabStatsStatusCndContent
        crippledHead = false; crippledTorso = false; crippledLeftArm = false
        crippledRightArm = false; crippledLeftLeg = false; crippledRightLeg = false
        cnd.imgTabStatusCndPipboyHead.setImageResource(R.drawable.man_head)
        cnd.tvTabStatusCndPipboyHeadHpCrippled.visibility = View.GONE
        cnd.imgTabStatusCndPipboyTorso.setImageResource(R.drawable.torso)
        cnd.tvTabStatusCndPipboyTorsoHpCrippled.text = activity.getString(R.string.stats_cnd_status_crippled)
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

    private companion object {
        private const val WOUND_BLEED_BANDAGE_DURATION_SECONDS = 600
        private const val STUN_DURATION_SECONDS = 300

        /** Порядок узлов частей тела в дереве энкодера; тач-цели считают свой индекс отсюда же. */
        private val BODY_PART_NODE_ORDER = listOf(
            BodyPart.HEAD, BodyPart.LEFT_ARM, BodyPart.TORSO,
            BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG,
        )

        private const val KEY_WOUND_PHASE = "restore_woundPhase"
        private const val KEY_WOUND_SEVERITY = "restore_woundSeverity"
        private const val KEY_CRIPPLED_HEAD = "restore_crippledHead"
        private const val KEY_CRIPPLED_TORSO = "restore_crippledTorso"
        private const val KEY_CRIPPLED_LEFT_ARM = "restore_crippledLeftArm"
        private const val KEY_CRIPPLED_RIGHT_ARM = "restore_crippledRightArm"
        private const val KEY_CRIPPLED_LEFT_LEG = "restore_crippledLeftLeg"
        private const val KEY_CRIPPLED_RIGHT_LEG = "restore_crippledRightLeg"
        private const val KEY_STATUS_CURSOR_ROW = "restore_statusCursorRow"
    }
}
