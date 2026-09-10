package com.malto4.pipdroid

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.widget.CompoundButtonCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.malto4.pipdroid.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Разделы STATS/SPECIAL, STATS/Skills и STATS/Perks вместе с экраном фильтра перков. */
/** Контроллер владеет состоянием трёх разделов и ветками их дерева энкодера; MainActivity остаётся
 * слоем навигации — узел STATUS и обёртки узлов SPECIAL/SKILLS/PERKS строит её statsMenuRoot().
 * Экран фильтра принадлежит Perks: живая ветка when(filteringMenu) в нём ровно одна. */
internal class StatsController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val prefs: SharedPreferences,
    private val navigator: MenuNavigator,
    private val mode: () -> PipBoyMode,
    private val accentColor: () -> Int,
    private val selectedButtonRes: () -> Int,
    private val scrollbarThumbRes: () -> Int,
    private val statsMenuRoot: () -> List<MenuNode>,
    private val backSidebarItem: () -> SidebarMenuItem<String>,
    private val menuBackNode: (onHighlight: () -> Unit, onBeforePop: () -> Unit) -> List<MenuNode>,
    private val recordScrollValueEditor: (scrollView: ScrollView) -> ValueEditor,
    private val playTick: () -> Unit,
    private val playButton: () -> Unit,
    private val playConfirm: () -> Unit,
    private val playError: () -> Unit,
    private val syncRow2Active: () -> Unit,
    private val enableBottomButtons: (Boolean) -> Unit,
    private val enableTopSwipe: (Boolean) -> Unit,
) {
    // ===== ФИЛЬТР =====
    private lateinit var filterFrame: FrameLayout
    private lateinit var filteringMenu: String
    private var selectedFilterSTATSPerks = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var selectedFilterDATAMisc = mutableSetOf<String>()  // Set to keep track of selected item IDs
    private var filterSelectionSnapshot: MutableSet<String> = mutableSetOf()

    // ===== ДОЛГОЕ НАЖАТИЕ +/- =====
    private var delayIterationCount = 0
    private var delayModify = 500L

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

    private val handler = Handler(Looper.getMainLooper())
    /** Повтор +/- при удержании кнопки; у SPECIAL шаг фиксированный, у Skills — с разгоном. */
    private val valueRepeatRunnable = object : Runnable {
        override fun run() {
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

    // ===== ПУБЛИЧНАЯ ПОВЕРХНОСТЬ =====

    /** Порядок блоков внутри — тот же, что был у них в onCreate(). */
    fun setup() {
        setupSpecialAndSkillsLists()
        setupFilterScreen()
        setupSpecialValueButtons()
        setupSkillsValueButtons()
        setupPerksScreen()
    }

    /** Ветка SPECIAL дерева энкодера — обёртку узла строит statsMenuRoot() активности. */
    /** onHighlight зовёт setSelectedPositionSilently(), а не громкий selectPosition() — тот сработал бы как ENCBTN. */
    fun specialChildrenNodes(): List<MenuNode> = specialMeta.mapIndexed { index, meta ->
        MenuNode(
            id = meta.key,
            onHighlight = {
                playTick()
                specialAdapter.setSelectedPositionSilently(index)
                showSpecialPreview(meta)
            },
            valueEditor = ValueEditor(
                onAdjust = { delta ->
                    val special = binding.incLayoutTabStatsSpecial
                    flashButtonPressImmediate(if (delta > 0) special.btnSpecialIncrease else special.btnSpecialDecrease)
                    adjustSelectedSpecial(delta)
                },
                onEnter = {
                    playConfirm()
                    setSpecialValueEditorFocused(true)
                },
                onExit = {
                    // Звук выхода из редактирования должен отличаться от звука входа и нажатий +/-.
                    playTick()
                    setSpecialValueEditorFocused(false)
                },
            ),
        )
    } + menuBackNode(
        { specialAdapter.setSelectedPositionSilently(specialMeta.size) },
        { specialAdapter.flashPressAnimation(specialMeta.size) },
    )

    /** Ветка SKILLS — тот же приём (onHighlight silently + showSkillPreview()), что у SPECIAL выше. */
    fun skillsChildrenNodes(): List<MenuNode> = skillsMeta.mapIndexed { index, meta ->
        MenuNode(
            id = meta.key,
            onHighlight = {
                playTick()
                skillsAdapter.setSelectedPositionSilently(index)
                showSkillPreview(meta)
            },
            valueEditor = ValueEditor(
                onAdjust = { delta ->
                    val skills = binding.incLayoutTabStatsSkills
                    flashButtonPressImmediate(if (delta > 0) skills.btnSkillIncrease else skills.btnSkillDecrease)
                    adjustSelectedSkill(delta)
                },
                onEnter = {
                    playConfirm()
                    setSkillValueEditorFocused(true)
                },
                onExit = {
                    playTick()
                    setSkillValueEditorFocused(false)
                },
            ),
        )
    } + menuBackNode(
        { skillsAdapter.setSelectedPositionSilently(skillsMeta.size) },
        { skillsAdapter.flashPressAnimation(skillsMeta.size) },
    )

    /** Дети PERKS пересчитываются заново на каждый вызов; onHighlight обновляет превью молча. */
    fun perksChildrenNodes(): List<MenuNode> {
        return (0 until perksRealItemCount).map { index ->
            // ENCBTN на перке входит в прокрутку описания, а не поднимает наверх; повторный — обратно к списку.
            MenuNode(
                id = "PERK_$index",
                onHighlight = {
                    playTick()
                    perksAdapter.setSelectedPositionSilently(index)
                    perksAdapter.currentItems().getOrNull(index)?.let { showPerkDescription(it.payload) }
                },
                valueEditor = recordScrollValueEditor(binding.incLayoutTabStatsPerks.scrollviewPerksDescriptionsText),
            )
        } + menuBackNode(
            { perksAdapter.setSelectedPositionSilently(perksRealItemCount) },
            { perksAdapter.flashPressAnimation(perksRealItemCount) },
        )
    }

    /** Режим стал известен после onCreate(): пересобираем оба списка с пунктом "В меню" целиком. */
    fun refreshModeGating() {
        specialAdapter.setItems(specialSidebarItems(), resetSelection = false)
        skillsAdapter.setItems(skillsSidebarItems(), resetSelection = false)
    }

    /** Курсор ещё на узле раздела и не провалился в боковое меню — рамку гасим целиком. */
    fun clearSpecialSelection() = specialAdapter.clearSelection()
    fun clearSkillsSelection() = skillsAdapter.clearSelection()
    fun clearPerksSelection() = perksAdapter.clearSelection()

    /** Вход на вкладку Perks: список пересобирается, потому что фильтр мог измениться. */
    fun openPerksScreen() = setupStatsPerks(binding.incLayoutTabStatsPerks.recyclerTabPerks)

    // ===== SPECIAL И SKILLS =====

    /** Боковые списки обоих разделов; клики по пунктам живут внутри адаптеров. */
    private fun setupSpecialAndSkillsLists() {
        // Пункт "В меню" требует отдельной ветки ДО поиска по specialMeta — иначе first{} упал бы с исключением.
        specialAdapter = SidebarMenuAdapter(
            items = specialSidebarItems(),
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            // Звук даёт onSelect ниже; тик остаётся только там, где его играет реальное вращение энкодера.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: syncCursor() чинит его только внутри активного уровня.
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    playConfirm()
                    syncStatsEncoderPath("SPECIAL", emptyList())
                    syncRow2Active()
                } else {
                    showSpecialPreview(specialMeta.first { it.key == item.payload })
                    // Тап равносилен ENCBTN: курсор проваливается сразу в редактирование значения.
                    syncStatsEncoderPathSilently("SPECIAL", listOf(position))
                    navigator.activateSelected()
                }
            },
        )
        binding.incLayoutTabStatsSpecial.scrollTabSpecial.layoutManager = LinearLayoutManager(activity)
        binding.incLayoutTabStatsSpecial.scrollTabSpecial.adapter = specialAdapter

        // Skills — тот же общий компонент вместо 13 скопированных XML-блоков и 13 обработчиков.
        skillsAdapter = SidebarMenuAdapter(
            items = skillsSidebarItems(),
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            // {} — см. подробный комментарий у specialAdapter выше, тот же приём.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора — тот же приём, что у SPECIAL.
                if (item.payload == SIDEBAR_BACK_PAYLOAD) {
                    playConfirm()
                    syncStatsEncoderPath("SKILLS", emptyList())
                    syncRow2Active()
                } else {
                    showSkillPreview(skillsMeta.first { it.key == item.payload })
                    // Тап равносилен ENCBTN — тот же приём, что у SPECIAL выше.
                    syncStatsEncoderPathSilently("SKILLS", listOf(position))
                    navigator.activateSelected()
                }
            },
        )
        binding.incLayoutTabStatsSkills.scrollTabSkills.layoutManager = LinearLayoutManager(activity)
        binding.incLayoutTabStatsSkills.scrollTabSkills.adapter = skillsAdapter
    }

    /** Тап меняет значение на 1, удержание повторяет; у SPECIAL разгона нет — фиксированные 500мс. */
    private fun setupSpecialValueButtons() {
        binding.incLayoutTabStatsSpecial.btnSpecialIncrease.setOnClickListener {
            adjustSelectedSpecial(1)
        }
        binding.incLayoutTabStatsSpecial.btnSpecialIncrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSPECIALValueIncreasing = true
                    handler.postDelayed(valueRepeatRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIALValueIncreasing = false
                    handler.removeCallbacks(valueRepeatRunnable)
                }
            }
            false
        }
        binding.incLayoutTabStatsSpecial.btnSpecialDecrease.setOnClickListener {
            adjustSelectedSpecial(-1)
        }
        binding.incLayoutTabStatsSpecial.btnSpecialDecrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSPECIALValueDecreasing = true
                    handler.postDelayed(valueRepeatRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSPECIALValueDecreasing = false
                    handler.removeCallbacks(valueRepeatRunnable)
                }
            }
            false
        }
        val specialValueButtonsAccentTint = ColorStateList.valueOf(accentColor())
        binding.incLayoutTabStatsSpecial.btnSpecialIncrease.backgroundTintList = specialValueButtonsAccentTint
        binding.incLayoutTabStatsSpecial.btnSpecialDecrease.backgroundTintList = specialValueButtonsAccentTint
        binding.incLayoutTabStatsSpecial.viewSpecialValueFocus.backgroundTintList = specialValueButtonsAccentTint
    }

    /** У Skills удержание с разгоном 500мс -> 50мс: диапазон 10-100 без него листать неудобно. */
    private fun setupSkillsValueButtons() {
        binding.incLayoutTabStatsSkills.btnSkillIncrease.setOnClickListener {
            adjustSelectedSkill(1)
        }
        binding.incLayoutTabStatsSkills.btnSkillIncrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSKILLValueIncreasing = true
                    delayModify = 500L
                    delayIterationCount = 0
                    handler.postDelayed(valueRepeatRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILLValueIncreasing = false
                    handler.removeCallbacks(valueRepeatRunnable)
                }
            }
            false
        }
        binding.incLayoutTabStatsSkills.btnSkillDecrease.setOnClickListener {
            adjustSelectedSkill(-1)
        }
        binding.incLayoutTabStatsSkills.btnSkillDecrease.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSKILLValueDecreasing = true
                    delayModify = 500L
                    delayIterationCount = 0
                    handler.postDelayed(valueRepeatRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSKILLValueDecreasing = false
                    handler.removeCallbacks(valueRepeatRunnable)
                }
            }
            false
        }
        val skillValueButtonsAccentTint = ColorStateList.valueOf(accentColor())
        binding.incLayoutTabStatsSkills.btnSkillIncrease.backgroundTintList = skillValueButtonsAccentTint
        binding.incLayoutTabStatsSkills.btnSkillDecrease.backgroundTintList = skillValueButtonsAccentTint
        binding.incLayoutTabStatsSkills.viewSkillValueFocus.backgroundTintList = skillValueButtonsAccentTint
    }

    private fun specialSidebarItems(): List<SidebarMenuItem<String>> {
        val items = specialMeta.map { meta ->
            SidebarMenuItem(
                payload = meta.key,
                label = activity.getString(meta.labelRes),
                rightValue = prefs.getInt(meta.prefKey, 5).toString(),
            )
        }
        return if (mode() != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    private fun skillsSidebarItems(): List<SidebarMenuItem<String>> {
        val items = skillsMeta.map { meta ->
            SidebarMenuItem(
                payload = meta.key,
                label = activity.getString(meta.labelRes),
                rightValue = prefs.getInt(meta.prefKey, 10).toString(),
            )
        }
        return if (mode() != PipBoyMode.PHONE) items + backSidebarItem() else items
    }
    /** Превью SPECIAL при движении курсора; onHighlight зовёт эту функцию, а не громкий selectPosition(). */
    private fun showSpecialPreview(meta: SpecialMeta) {
        selectedSPECIAL = meta.key
        binding.incLayoutTabStatsSpecial.imgSpecialSelected.setImageResource(meta.imageRes)
        binding.incLayoutTabStatsSpecial.tvSpecialDescriptionsText.setText(meta.descriptionRes)
    }
    /** Тот же приём, что у showSpecialPreview() выше, для Skills. */
    private fun showSkillPreview(meta: SkillMeta) {
        selectedSKILL = meta.key
        binding.incLayoutTabStatsSkills.imgSkillSelected.setImageResource(meta.imageRes)
        binding.incLayoutTabStatsSkills.tvSkillDescriptionsText.setText(meta.descriptionRes)
    }
    /** Кнопки +/- SPECIAL и Skills: значения клампятся на границе диапазона, а не зацикливаются. */
    private fun adjustSelectedSpecial(delta: Int) {
        val position = specialMeta.indexOfFirst { it.key == selectedSPECIAL }
        if (position == -1) return
        val meta = specialMeta[position]
        val prevValue = prefs.getInt(meta.prefKey, 5)
        val curValue = (prevValue + delta).coerceIn(1, 10)
        prefs.edit().putInt(meta.prefKey, curValue).apply()
        specialAdapter.updateItemValue(position, curValue.toString())
        if (curValue == prevValue) playError() else playConfirm()
        // Тап по +/- переставляет курсор на характеристику и входит в её ValueEditor; guard — чтобы не переигрывать onEnter при удержании.
        if (navigator.editingNodeId() != meta.key) {
            syncStatsEncoderPathSilently("SPECIAL", listOf(position))
            navigator.activateSelected()
        }
    }
    private fun adjustSelectedSkill(delta: Int) {
        val position = skillsMeta.indexOfFirst { it.key == selectedSKILL }
        if (position == -1) return
        val meta = skillsMeta[position]
        val prevValue = prefs.getInt(meta.prefKey, 10)
        val curValue = (prevValue + delta).coerceIn(10, 100)
        prefs.edit().putInt(meta.prefKey, curValue).apply()
        skillsAdapter.updateItemValue(position, curValue.toString())
        if (curValue == prevValue) playError() else playConfirm()
        // Тот же приём, что у adjustSelectedSpecial() выше.
        if (navigator.editingNodeId() != meta.key) {
            syncStatsEncoderPathSilently("SKILLS", listOf(position))
            navigator.activateSelected()
        }
    }
    /** Общий признак "энкодер сфокусирован здесь" — четыре L-уголка на отдельном View рядом с целью. */
    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) =
        applyFocusBrackets(bracketsView, mode(), visible)
    private fun setSpecialValueEditorFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabStatsSpecial.viewSpecialValueFocus, focused)
    }
    private fun setSkillValueEditorFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabStatsSkills.viewSkillValueFocus, focused)
    }

    // ===== PERKS =====

    /** Список строится сразу, а не лениво по клику: к первой сборке statsMenuRoot() он ещё пуст, и узел
     * PERKS навсегда заморозил бы единственный пункт "В меню" — children узла обычный val. */
    private fun setupPerksScreen() {
        setupStatsPerks(binding.incLayoutTabStatsPerks.recyclerTabPerks)
        binding.incLayoutTabStatsPerks.btnPerksFilter.setOnClickListener {
            openPerksFilter()
        }
    }
    /** Локализация перка: Data.kt хранит только английский, перевод резолвится через perk_<id>_name/_desc. */
    /** Считается один раз: язык меняется только полным рестартом Activity. */
    private fun localizePerk(perk: Perk): Perk {
        val nameResId = activity.resources.getIdentifier("perk_${perk.id}_name", "string", activity.packageName)
        val descResId = activity.resources.getIdentifier("perk_${perk.id}_desc", "string", activity.packageName)
        return perk.copy(
            name = if (nameResId != 0) activity.getString(nameResId) else perk.name,
            desc = if (descResId != 0) activity.getString(descResId) else perk.desc,
        )
    }
    private val localizedPerks: List<Perk> by lazy {
        perks.map { perk -> localizePerk(perk) }
    }
    /** Превью описания и иконки Perks при движении курсора — общее для тапа и для наведения энкодером. */
    private fun showPerkDescription(perk: Perk) {
        binding.incLayoutTabStatsPerks.tvPerksDescriptionsText.text = perk.desc
        binding.incLayoutTabStatsPerks.imgPerksSelected.setImageResource(perk.iconRes)
        // Сброс прокрутки на новую запись, иначе новый текст покажется со смещения предыдущего.
        binding.incLayoutTabStatsPerks.scrollviewPerksDescriptionsText.scrollTo(0, 0)
    }
    private fun setupStatsPerks(recyclerView: RecyclerView){
        val selectedSTATSPerksString = prefs.getString("selectedSTATSPerksArray", "1")
        val selectedSTATSPerksArray: Array<String> = selectedSTATSPerksString!!.split(",").toTypedArray()
        // Фильтруем по сырому списку, локализуем только отобранное: локализация каждого перка — два getIdentifier().
        val filteredPerksList = perks.filter { perk -> perk.id in selectedSTATSPerksArray }.map { localizePerk(it) }
        perksRealItemCount = filteredPerksList.size

        val realItems = filteredPerksList.map { perk -> SidebarMenuItem(payload = perk, label = perk.name) }
        perksAdapter = SidebarMenuAdapter(
            items = if (mode() != PipBoyMode.PHONE) realItems + perksBackSidebarItem() else realItems,
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            // Звук даёт onSelect ниже — тик отсюда его дублировал.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: syncCursor() чинит его только внутри активного уровня.
                if (item.payload.id == SIDEBAR_BACK_PAYLOAD) {
                    playConfirm()
                    syncStatsEncoderPath("PERKS", emptyList())
                    syncRow2Active()
                } else {
                    showPerkDescription(item.payload)
                    // Тап равносилен ENCBTN: курсор проваливается сразу в прокрутку описания, превью уже применено выше.
                    syncStatsEncoderPathSilently("PERKS", listOf(position))
                    navigator.activateSelected()
                }
            },
        )
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = perksAdapter
        filteredPerksList.firstOrNull()?.let { showPerkDescription(it) }
        // Список фильтруется, поэтому дерево пересобирается при каждом изменении, а не только при входе в STATS.
        navigator.replaceChildrenOf("PERKS", perksChildrenNodes())
    }
    /** Пункт "В меню" для Perks — payload того же типа, что у реальных перков, с id-маркером. */
    private fun perksBackSidebarItem(): SidebarMenuItem<Perk> =
        SidebarMenuItem(payload = Perk(SIDEBAR_BACK_PAYLOAD, "", "", 0), label = activity.getString(R.string.sidebar_menu_back))

    // ===== ЭКРАН ФИЛЬТРА =====

    /** Пять кнопок экрана и поле поиска; сам список чекбоксов строит listEntries() при открытии. */
    private fun setupFilterScreen() {
        filterFrame = binding.incLayoutFilterModification.filterModificationFrame
        CoroutineScope(Dispatchers.Main).launch {
            loadSelectedItems()
            // Any UI updates can be done here after the function completes
        }

        // Плейсхолдер красится акцентом с тем же затенением, что у соседних пунктов row2 — дефолтный hint слишком блёклый.
        binding.incLayoutFilterModification.etFilterModificationValue.setHintTextColor(
            ColorUtils.setAlphaComponent(accentColor(), (0.55f * 255).toInt())
        )

        // Пять кнопок экрана: нейтральная заливка из стиля, акцент темы — backgroundTintList кодом.
        val filterAccent = accentColor()
        listOf(
            binding.incLayoutFilterModification.btnFilterModificationCancel,
            binding.incLayoutFilterModification.btnFilterModificationFilter,
            binding.incLayoutFilterModification.btnFilterModificationSelect,
            binding.incLayoutFilterModification.btnFilterModificationClear,
            binding.incLayoutFilterModification.btnFilterModificationSave
        ).forEach { it.backgroundTintList = ColorStateList.valueOf(filterAccent) }

        binding.incLayoutFilterModification.btnFilterModificationCancel.setOnClickListener{
            playButton()
            // Откатываем несохранённые правки чекбоксов: saveSelectedItems() не вызывается.
            when(filteringMenu){
                "PERKS" -> selectedFilterSTATSPerks = filterSelectionSnapshot.toMutableSet()
            }
            closeFilterScreen()
        }

        binding.incLayoutFilterModification.btnFilterModificationSelect.setOnClickListener{
            playButton()
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(binding.incLayoutFilterModification.filterModificationFrame, localizedPerks, true)
            }
        }

        binding.incLayoutFilterModification.btnFilterModificationClear.setOnClickListener{
            playButton()
            when(filteringMenu){
                "PERKS" -> selectClearAllCheckBoxes(binding.incLayoutFilterModification.filterModificationFrame, localizedPerks, false)
            }
        }

        binding.incLayoutFilterModification.btnFilterModificationFilter.setOnClickListener{
            playButton()
            val filterText = binding.incLayoutFilterModification.etFilterModificationValue.text.toString()

            when(filteringMenu){
                "PERKS" -> filterList(localizedPerks, filterText)
            }
        }

        binding.incLayoutFilterModification.btnFilterModificationSave.setOnClickListener{
            playButton()
            when(filteringMenu){
                "PERKS" -> saveSelectedItems("selectedSTATSPerksArray")
            }
            closeFilterScreen()
        }
    }

    private fun listEntries(frameLayout: FrameLayout, items: List<Perk>){

        frameLayout.removeAllViews()

        // Create a LinearLayout to hold the entries
        val linearLayout = LinearLayout(activity)
        linearLayout.orientation = LinearLayout.VERTICAL

        // Iterate over the items and create CheckBox and TextView for each
        for (item in items) {
            val checkBox = CheckBox(activity)
            // Чекбокс тонируется акцентом: Material-дефолт на тёмном фоне почти не виден.
            CompoundButtonCompat.setButtonTintList(checkBox, ColorStateList.valueOf(accentColor()))
            val textView = TextView(activity).apply {
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
                        playTick()
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
            val entryLayout = LinearLayout(activity)
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
        prefs.edit().putString(filterModificationItems, selectedItemsString).apply()
        when(filterModificationItems){
            "selectedSTATSPerksArray" -> {
                setupStatsPerks(binding.incLayoutTabStatsPerks.recyclerTabPerks)
            }
        }
    }
    /** Поднимает сохранённые выборки фильтров из prefs в фоновом потоке. */
    private suspend fun loadSelectedItems(){
        withContext(Dispatchers.IO) {
            val selectedSTATSPerksArray = prefs.getString("selectedSTATSPerksArray", "1")
            val selectedDATAMiscArray = prefs.getString("selectedDATAMiscArray", "1")

            if (!selectedSTATSPerksArray.isNullOrEmpty()) selectedFilterSTATSPerks.addAll(selectedSTATSPerksArray.split(","))
            if (!selectedDATAMiscArray.isNullOrEmpty()) selectedFilterDATAMisc.addAll(selectedDATAMiscArray.split(","))
        }
    }
    /** Открывает экран фильтра Perks — точка входа кнопка-воронка на экране Perks. */
    private fun openPerksFilter() {
        playButton()
        filteringMenu = "PERKS"
        filterSelectionSnapshot = selectedFilterSTATSPerks.toMutableSet()
        listEntries(filterFrame, localizedPerks)
        binding.incLayoutFilterModification.root.visibility = View.VISIBLE
        binding.layoutStats.visibility = View.GONE
        binding.layoutItems.visibility = View.GONE
        binding.layoutData.visibility = View.GONE
        enableBottomButtons(false)
        enableTopSwipe(false)
    }
    /** Закрывает экран фильтра — общая часть для Save и Cancel. */
    private fun closeFilterScreen() {
        binding.incLayoutFilterModification.root.visibility = View.GONE
        binding.layoutStats.visibility = View.VISIBLE
        binding.layoutItems.visibility = View.VISIBLE
        binding.layoutData.visibility = View.VISIBLE
        enableBottomButtons(true)
        enableTopSwipe(true)
    }

    // ===== ДЕРЕВО ЭНКОДЕРА =====

    /** Безусловно ставит курсор энкодера по [path] от детей узла [nodeId] дерева STATS. */
    private fun syncStatsEncoderPath(nodeId: String, path: List<Int>) = syncStatsEncoderPath(nodeId, path, loud = true)
    /** [loud] = false, когда вызывающий код уже дал свой эффект и звук. */
    private fun syncStatsEncoderPathSilently(nodeId: String, path: List<Int>) = syncStatsEncoderPath(nodeId, path, loud = false)
    private fun syncStatsEncoderPath(nodeId: String, path: List<Int>, loud: Boolean) {
        val rootNodes = statsMenuRoot()
        val rootIndex = rootNodes.indexOfFirst { it.id == nodeId }
        if (rootIndex == -1) return
        val fullPath = listOf(rootIndex) + path
        if (loud) navigator.setPath(rootNodes, fullPath) else navigator.setPathSilently(rootNodes, fullPath)
    }
}
