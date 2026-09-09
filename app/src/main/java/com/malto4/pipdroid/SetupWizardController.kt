package com.malto4.pipdroid

import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.malto4.pipdroid.databinding.ActivityMainBinding

/** Стартовый экран выбора режима работы и мастер настройки PipBoy 2000/3000 целиком. */
/** Сам режим контроллеру не принадлежит: его читают все три контроллера, оба дерева меню и обе строки
 * шапки, поэтому pipBoyMode остаётся полем активности, а выбор игрока уезжает к ней через onModeChosen.
 * Сканер пейринга тоже снаружи — его делят шаг PAIRING и экран Settings → Bluetooth. */
internal class SetupWizardController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val currentMode: () -> PipBoyMode,
    private val accentColor: () -> Int,
    private val selectedButtonRes: () -> Int,
    private val scrollbarThumbRes: () -> Int,
    private val equalizeButtonWidths: (List<Button>) -> Unit,
    private val setWizardButtonState: (Button, Boolean) -> Unit,
    private val setWizardButtonDisabled: (Button) -> Unit,
    private val playTick: () -> Unit,
    private val playButton: () -> Unit,
    private val playError: () -> Unit,
    private val onModeChosen: (PipBoyMode) -> Unit,
    private val hasAllRequiredPermissions: () -> Boolean,
    private val checkPermissions: () -> Unit,
    private val setupBluetooth: () -> Unit,
    private val startPairingScan: (LinearLayout, TextView, (String) -> Unit) -> Unit,
    private val stopPairingScan: () -> Unit,
    private val applyPairedDevice: (String) -> Unit,
    private val setPowerOffInstant: () -> Unit,
    private val updateScreenGlare: () -> Unit,
    private val resetToFullScreen: () -> Unit,
    private val loadViewState: () -> Unit,
    private val applyTemporaryFullScreenLayout: () -> Unit,
    private val finishPhoneSetup: () -> Unit,
    private val skipToMainScreenDebug: () -> Unit,
    private val importVoiceModel: () -> Unit,
    private val importMapBundle: () -> Unit,
) {
    private var modeSelectHighlighted = PipBoyMode.PHONE
    private val modeSelectList = listOf(PipBoyMode.PHONE, PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000)
    private lateinit var modeSelectAdapter: SidebarMenuAdapter<PipBoyMode>

    private enum class PipBoyWizardStep { HARDWARE_INSTRUCTIONS, DISPLAY_AREA, PERMISSIONS, IMPORT, PAIRING, POWER_HINT }

    // ===== ПУБЛИЧНАЯ ПОВЕРХНОСТЬ =====

    /** Показан шаг DISPLAY AREA: только на нём жест ресайза рабочей области что-то меняет. */
    var isResizing = false
        private set
    /** Доп. минимум размера при пинче на шаге DISPLAY AREA мастера, вне мастера — 0. */
    var minContentWidthPx = 0
        private set
    var minContentHeightPx = 0
        private set

    /** Подсказка про POWER — такое же выключенное состояние экрана, как и оверлей OFF. */
    val isPowerHintVisible: Boolean
        get() = binding.incLayoutPipboy2000Wizard.layoutWizardPowerHint.visibility == View.VISIBLE

    /** Тема к этому моменту уже применена: экран выбора режима строит адаптер сразу, а не лениво при показе. */
    fun setup() {
        setupModeSelectScreen()
        setupPipBoy2000Wizard()
    }

    /** Settings → "Изменить" режим работы. */
    fun openModeSelect() = openModeSelectScreen()

    /** Убирает мастер; true — он был открыт, и зовущему ещё чинить кнопки шапки и футера. */
    fun hide(): Boolean {
        val wizardWasOpen = binding.incLayoutPipboy2000Wizard.root.visibility == View.VISIBLE
        binding.incLayoutPipboy2000Wizard.root.visibility = View.GONE
        return wizardWasOpen
    }

    /** Отдельно от hide(): восстановление после убийства процесса гасит и экран выбора режима. */
    fun hideModeSelect() {
        binding.incLayoutTabModeSelect.root.visibility = View.GONE
    }

    /** Имя режима — и в списке выбора, и в строке Settings. */
    fun displayName(mode: PipBoyMode): String = pipBoyModeDisplayName(mode)

    /** Разрешения выданы: если игрок стоит на шаге PERMISSIONS, ведём его дальше и сообщаем об этом. */
    fun showImportIfOnPermissionsStep(): Boolean {
        val wizard = binding.incLayoutPipboy2000Wizard
        val onPermissionsStep = wizard.root.visibility == View.VISIBLE && wizard.layoutWizardPermissions.visibility == View.VISIBLE
        // IMPORT — общий следующий шаг обоих режимов, см. btnWizardImportDone.
        if (onPermissionsStep) showWizardStep(PipBoyWizardStep.IMPORT)
        return onPermissionsStep
    }

    // ===== ЭКРАН ВЫБОРА РЕЖИМА =====

    private fun showModeDescription(mode: PipBoyMode) {
        val ms = binding.incLayoutTabModeSelect
        modeSelectHighlighted = mode
        ms.tvModeSelectDescription.text = when (mode) {
            PipBoyMode.PHONE -> activity.getString(R.string.mode_description_phone)
            PipBoyMode.PIPBOY_2000 -> activity.getString(R.string.mode_description_pipboy_2000)
            PipBoyMode.PIPBOY_3000 -> activity.getString(R.string.mode_description_pipboy_3000)
        }
        val modeIndex = modeSelectList.indexOf(mode)
        if (modeIndex >= 0) modeSelectAdapter.setSelectedPositionSilently(modeIndex)
        // PipBoy 3000 выбрать нельзя, но кнопка кликабельна — чтобы поймать тап и дать звук ошибки.
        if (mode != PipBoyMode.PIPBOY_3000) {
            setWizardButtonState(ms.btnModeSelectConfirm, false)
        } else {
            setWizardButtonDisabled(ms.btnModeSelectConfirm)
        }
    }
    private fun pipBoyModeDisplayName(mode: PipBoyMode): String = when (mode) {
        PipBoyMode.PHONE -> activity.getString(R.string.mode_phone)
        PipBoyMode.PIPBOY_2000 -> activity.getString(R.string.mode_pipboy_2000)
        PipBoyMode.PIPBOY_3000 -> activity.getString(R.string.mode_pipboy_3000)
    }
    private fun openModeSelectScreen() {
        showModeDescription(currentMode())
        binding.incLayoutTabModeSelect.root.visibility = View.VISIBLE
    }
    private fun setupModeSelectScreen() {
        val ms = binding.incLayoutTabModeSelect

        ms.tvModeSelectDescription.setTextColor(accentColor())

        ms.recyclerModeSelect.layoutManager = LinearLayoutManager(activity)
        modeSelectAdapter = SidebarMenuAdapter(
            items = modeSelectList.map { mode -> SidebarMenuItem(payload = mode, label = pipBoyModeDisplayName(mode)) },
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            playSelectSound = { playTick() },
            onSelect = { _, item -> showModeDescription(item.payload) },
        )
        ms.recyclerModeSelect.adapter = modeSelectAdapter

        showModeDescription(PipBoyMode.PHONE)
        ms.btnModeSelectConfirm.setOnClickListener {
            if (modeSelectHighlighted == PipBoyMode.PIPBOY_3000) {
                playError()
                return@setOnClickListener
            }
            playButton()
            selectPipBoyMode(modeSelectHighlighted)
        }
    }
    /** Режим сознательно спрашивается каждый запуск и на диск не сохраняется: экран выбора —
     * стартовый безусловно, а между запусками режим переживает только убийство процесса
     * (savedInstanceState в restoreAppState()). */
    private fun selectPipBoyMode(mode: PipBoyMode) {
        onModeChosen(mode)
        binding.incLayoutTabModeSelect.root.visibility = View.GONE

        when (mode) {
            PipBoyMode.PHONE -> {

                binding.incLayoutPipboy2000Wizard.root.visibility = View.VISIBLE
                showWizardStep(PipBoyWizardStep.PERMISSIONS)
            }
            PipBoyMode.PIPBOY_2000, PipBoyMode.PIPBOY_3000 -> {
                setPowerOffInstant()
                binding.incLayoutPipboy2000Wizard.root.visibility = View.VISIBLE
                showWizardStep(PipBoyWizardStep.HARDWARE_INSTRUCTIONS)
            }
        }
    }

    // ===== МАСТЕР НАСТРОЙКИ PIPBOY 2000/3000 =====

    private fun showWizardStep(step: PipBoyWizardStep, allowAutoAdvance: Boolean = true) {
        val w = binding.incLayoutPipboy2000Wizard
        w.layoutWizardChromeFrame.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.GONE else View.VISIBLE
        w.layoutWizardHardware.visibility = if (step == PipBoyWizardStep.HARDWARE_INSTRUCTIONS) View.VISIBLE else View.GONE
        w.layoutWizardDisplayArea.visibility = if (step == PipBoyWizardStep.DISPLAY_AREA) View.VISIBLE else View.GONE
        w.layoutWizardPermissions.visibility = if (step == PipBoyWizardStep.PERMISSIONS) View.VISIBLE else View.GONE
        w.layoutWizardImport.visibility = if (step == PipBoyWizardStep.IMPORT) View.VISIBLE else View.GONE
        w.layoutWizardPairing.visibility = if (step == PipBoyWizardStep.PAIRING) View.VISIBLE else View.GONE
        w.layoutWizardPowerHint.visibility = if (step == PipBoyWizardStep.POWER_HINT) View.VISIBLE else View.GONE
        w.tvWizardPowerHint.visibility = View.VISIBLE
        w.btnWizardHideHint.visibility = View.VISIBLE
        updateScreenGlare()

        // Скан идёт строго по факту показа шага PAIRING, а не по нажатию игрока.
        if (step == PipBoyWizardStep.PAIRING) {
            startPairingScan(w.layoutWizardPairingDevices, w.tvWizardPairingStatus) { address -> selectPairingDevice(address) }
        } else {
            stopPairingScan()
        }

        // Регулировка рабочей области жестом активна только пока реально показан этот шаг.
        isResizing = (step == PipBoyWizardStep.DISPLAY_AREA)
        if (step == PipBoyWizardStep.DISPLAY_AREA) {
            val displayMetrics = activity.resources.displayMetrics
            minContentWidthPx = (displayMetrics.widthPixels * 0.6f).toInt()
            minContentHeightPx = (displayMetrics.heightPixels * 0.7f).toInt()
            // Персистентный сброс — стартовая точка регулировки, масштаб прошлой сессии не подхватываем.
            resetToFullScreen()
        } else if (step == PipBoyWizardStep.HARDWARE_INSTRUCTIONS) {
            // До этого шага область не настраивалась в этом прогоне — перезаписывать нечего.
            minContentWidthPx = 0
            minContentHeightPx = 0
            applyTemporaryFullScreenLayout()
        } else if (currentMode() == PipBoyMode.PHONE) {
            minContentWidthPx = 0
            minContentHeightPx = 0
            resetToFullScreen()
        } else {
            minContentWidthPx = 0
            minContentHeightPx = 0
            loadViewState()
        }

        if (step == PipBoyWizardStep.PERMISSIONS && allowAutoAdvance && hasAllRequiredPermissions()) {
            // Разрешения уже выданы — не задерживаем игрока; allowAutoAdvance=false только у явного Back.
            showWizardStep(PipBoyWizardStep.IMPORT)
        }
    }

    /** Тап по найденному устройству на шаге PAIRING: адрес сохраняет активность, шаг двигаем мы. */
    private fun selectPairingDevice(address: String) {
        applyPairedDevice(address)
        showWizardStep(PipBoyWizardStep.POWER_HINT)
    }

    private fun setupPipBoy2000Wizard() {
        val w = binding.incLayoutPipboy2000Wizard

        val wizardAccent = accentColor()
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
        equalizeButtonWidths(listOf(w.btnWizardDone, w.btnWizardReset, w.btnWizardCancel))

        // Шаг 2: Hardware Instructions
        w.btnWizardHardwareBack.setOnClickListener {
            playButton()
            w.root.visibility = View.GONE
            binding.incLayoutTabModeSelect.root.visibility = View.VISIBLE
        }
        w.btnWizardHardwareNext.setOnClickListener {
            playButton()
            showWizardStep(PipBoyWizardStep.DISPLAY_AREA)
        }
        // Обход всего мастера и анимации загрузки в debug-сборках.
        w.btnWizardHardwareSkipDebug.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        w.btnWizardHardwareSkipDebug.setOnClickListener {
            playButton()
            skipToMainScreenDebug()
        }

        // Шаг 3: Display Area
        w.btnWizardDone.setOnClickListener {
            playButton()
            showWizardStep(PipBoyWizardStep.PERMISSIONS)
        }
        w.btnWizardReset.setOnClickListener {
            playButton()
            resetToFullScreen()
        }
        w.btnWizardCancel.setOnClickListener {
            playButton()
            showWizardStep(PipBoyWizardStep.HARDWARE_INSTRUCTIONS)
        }

        // Шаг 4: Permissions
        w.btnWizardPermissionsBack.setOnClickListener {
            playButton()
            if (currentMode() == PipBoyMode.PHONE) {
                w.root.visibility = View.GONE
                binding.incLayoutTabModeSelect.root.visibility = View.VISIBLE
            } else {
                showWizardStep(PipBoyWizardStep.DISPLAY_AREA)
            }
        }
        w.btnWizardGrantPermissions.setOnClickListener {
            playButton()
            checkPermissions()
        }

        // Шаг Import: кнопки переиспользуют лончеры и репозитории Settings.
        w.btnWizardImportBack.setOnClickListener {
            playButton()
            // allowAutoAdvance=false, иначе showWizardStep(PERMISSIONS) отскочит обратно на IMPORT.
            showWizardStep(PipBoyWizardStep.PERMISSIONS, allowAutoAdvance = false)
        }
        w.btnWizardImportDone.setOnClickListener {
            playButton()
            // Продолжает независимо от того, импортировано что-то или нет — донастроить можно в Settings.
            if (currentMode() == PipBoyMode.PHONE) {
                finishPhoneSetup()
            } else {
                setupBluetooth()
                showWizardStep(PipBoyWizardStep.PAIRING)
            }
        }
        w.btnWizardImportVoice.setOnClickListener {
            playButton()
            importVoiceModel()
        }
        w.btnWizardImportMap.setOnClickListener {
            playButton()
            importMapBundle()
        }

        // Шаг 5: Pairing
        w.btnWizardPairingBack.setOnClickListener {
            playButton()
            stopPairingScan()
            // Предыдущий шаг — IMPORT; автопереход тут не при чём, он бывает только на PERMISSIONS.
            showWizardStep(PipBoyWizardStep.IMPORT)
        }
        w.btnWizardPairingRescan.setOnClickListener {
            playButton()
            startPairingScan(w.layoutWizardPairingDevices, w.tvWizardPairingStatus) { address -> selectPairingDevice(address) }
        }
        // Обход пейринга в debug-сборках
        w.btnWizardPairingSkipDebug.visibility =
            if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        w.btnWizardPairingSkipDebug.setOnClickListener {
            playButton()
            stopPairingScan()
            showWizardStep(PipBoyWizardStep.POWER_HINT)
        }

        // Шаг 6: подсказка про POWER
        w.btnWizardHideHint.setOnClickListener {
            playButton()
            w.tvWizardPowerHint.visibility = View.GONE
            w.btnWizardHideHint.visibility = View.GONE
        }
    }
}
