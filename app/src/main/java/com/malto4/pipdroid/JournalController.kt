package com.malto4.pipdroid

import android.content.res.ColorStateList
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.malto4.pipdroid.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Экран ITEMS/Журнал целиком: список записей, карточка, редактор, боковое меню и дерево энкодера. */
/** Контроллер владеет состоянием экрана, MainActivity остаётся слоем навигации. Диктовка текста записи
 * живёт здесь же — микрофон в приложении один, поэтому наружу видна только её занятость. */
internal class JournalController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val navigator: MenuNavigator,
    private val dictationService: com.malto4.pipdroid.voice.VoiceDictationService,
    private val voiceModels: com.malto4.pipdroid.voice.VoiceModelRepository,
    private val dictationPermissionRequestCode: Int,
    private val gameYear: () -> Int,
    private val mode: () -> PipBoyMode,
    private val accentColor: () -> Int,
    private val selectedButtonRes: () -> Int,
    private val scrollbarThumbRes: () -> Int,
    private val itemsMenuRoot: () -> List<MenuNode>,
    private val menuBackNode: (onHighlight: () -> Unit, onBeforePop: () -> Unit) -> List<MenuNode>,
    private val playTick: () -> Unit,
    private val playButton: () -> Unit,
    private val playConfirm: () -> Unit,
    private val playError: () -> Unit,
    private val suppressTickAround: (block: () -> Unit) -> Unit,
    private val syncRow2Active: () -> Unit,
    private val isVoiceCommandBusy: () -> Boolean,
) {
    private val journalRepository by lazy { JournalRepository(activity) }
    private var journalEntries: MutableList<JournalEntry> = mutableListOf()
    private var selectedJournalEntryForDetail: JournalEntry? = null
    private var editingJournalEntryId: String? = null
    private var journalEditorOpenFor: String? = null
    private sealed class JournalSidebarEntry {
        object NewEntry : JournalSidebarEntry()
        data class Existing(val entry: JournalEntry) : JournalSidebarEntry()
        object Menu : JournalSidebarEntry()
    }
    private lateinit var journalListAdapter: SidebarMenuAdapter<JournalSidebarEntry>
    /** Диктовка в редактор записи Журнала — дописывает к уже набранному тексту. */
    private val journalDictation by lazy {
        val popup = binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup
        DictationController(
            activity = activity,
            micButton = popup.btnJournalEntryMic,
            statusView = popup.tvJournalEntryMicStatus,
            editText = popup.etJournalEntryValue,
            permissionRequestCode = dictationPermissionRequestCode,
            logTag = "VoiceJournal",
            dictation = dictationService,
            models = voiceModels,
            accentColor = { accentColor() },
            isVoiceCommandBusy = { isVoiceCommandBusy() },
            replaceFirstSegment = { false },
            playButtonSound = { playButton() },
            playErrorSound = { playError() },
        )
    }

    companion object {
        // journalEditorOpenFor
        private const val JOURNAL_NEW_ENTRY_SENTINEL = "JOURNAL_NEW_ENTRY"
    }

    // ===== ПУБЛИЧНАЯ ПОВЕРХНОСТЬ =====

    /** Вход на вкладку Журнал: записи перечитываются с диска, карточка и редактор сбрасываются. */
    fun openScreen() = openJournalScreen()

    /** Ветка JOURNAL дерева энкодера — её строит itemsMenuRoot() активности. */
    fun childrenNodes(): List<MenuNode> = journalChildrenNodes()

    /** Голосовое "новая запись": редактор открывается уже после перехода на вкладку Журнал. */
    fun openNewEntryEditor() = showJournalEntryEditorForNew()

    /** Режим стал известен после onCreate(): пересобираем боковой список и кнопку "назад" карточки. */
    fun refreshModeGating() {
        // journalListAdapter строится не в onCreate(), а при первом заходе на вкладку — отсюда проверка инициализации.
        if (::journalListAdapter.isInitialized) {
            journalListAdapter.setItems(journalSidebarItems(), resetSelection = false)
        }
        refreshJournalBackButtonVisibility()
    }

    /** Курсор ещё на узле JOURNAL и не провалился в боковое меню — рамку гасим целиком. */
    fun clearSidebarSelection() = journalListAdapter.clearSelection()

    /** Wake-word уступает микрофон уже идущей диктовке: VoiceDictationService один на все сценарии. */
    val isDictationIdle: Boolean
        get() = journalDictation.isIdle

    /** Разрешение на запись выдано — стартуем, только если редактор записи всё ещё открыт. */
    fun startDictationIfPopupVisible() {
        if (binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.root.visibility == View.VISIBLE) {
            journalDictation.start()
        }
    }

    // ===== ЭКРАН, СПИСОК И КАРТОЧКА ЗАПИСИ =====

    private fun openJournalScreen() {
        journalEntries = journalRepository.loadAll().toMutableList()
        bindJournalListAdapter()
        hideJournalEntryDetail()
    }
    /** Порядок пунктов обязан совпадать с journalChildrenNodes() дерева энкодера. */
    private fun journalSidebarItems(): List<SidebarMenuItem<JournalSidebarEntry>> {
        val items: List<SidebarMenuItem<JournalSidebarEntry>> =
            listOf(SidebarMenuItem<JournalSidebarEntry>(payload = JournalSidebarEntry.NewEntry, label = activity.getString(R.string.journal_new_entry_button))) +
                journalEntries.sortedByDescending { it.createdAtEpochMillis }
                    .map { entry -> SidebarMenuItem<JournalSidebarEntry>(payload = JournalSidebarEntry.Existing(entry), label = formatJournalDate(entry.createdAtEpochMillis)) }
        return if (mode() != PipBoyMode.PHONE) {
            items + SidebarMenuItem<JournalSidebarEntry>(payload = JournalSidebarEntry.Menu, label = activity.getString(R.string.sidebar_menu_back))
        } else {
            items
        }
    }
    /** [initialSelectedPosition] — после Save/Delete курсор встаёт на затронутую запись, не на 0. */
    private fun bindJournalListAdapter(initialSelectedPosition: Int = 0) {
        val journalScreen = binding.incLayoutTabItemsJournal
        val adapter = SidebarMenuAdapter(
            items = journalSidebarItems(),
            selectedBackgroundRes = selectedButtonRes(),
            scrollbarThumbRes = scrollbarThumbRes(),
            initialSelectedPosition = initialSelectedPosition,
            // Звук даёт onSelect ниже — ровно один на тап.
            playSelectSound = {},
            onSelect = { position, item ->
                // Безусловная синхронизация курсора: syncCursor() чинит его только внутри активного уровня.
                when (item.payload) {
                    // "+ 0" — тап равносилен ENCBTN: курсор садится на первого ребёнка, который сам откроет нужный экран.
                    is JournalSidebarEntry.NewEntry -> {
                        playConfirm()
                        suppressTickAround { syncJournalEncoderPath(listOf(position, 0)) }
                    }
                    is JournalSidebarEntry.Existing -> {
                        playConfirm()
                        suppressTickAround { syncJournalEncoderPath(listOf(position, 0)) }
                    }
                    is JournalSidebarEntry.Menu -> {
                        playConfirm()
                        syncJournalEncoderPathSilently(emptyList())
                        syncRow2Active()
                    }
                }
            },
        )
        journalListAdapter = adapter
        journalScreen.rvJournalEntryList.layoutManager = LinearLayoutManager(activity)
        journalScreen.rvJournalEntryList.adapter = adapter
    }
    // Подменяется только YEAR — реальные месяц, день и время записи остаются как есть.
    private fun formatJournalDate(epochMillis: Long): String {
        val gameCalendar = Calendar.getInstance()
        gameCalendar.timeInMillis = epochMillis
        gameCalendar.set(Calendar.YEAR, gameYear())
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(gameCalendar.time)
    }
    /** Карточка записи взаимоисключающа с подсказкой и редактором; сброс в начале — идемпотентная подстраховка. */
    private fun showJournalEntryDetail(entry: JournalEntry) {
        selectedJournalEntryForDetail = entry
        hideJournalEntryEditor()
        setAllJournalEntryDetailFocusesHidden()
        val journalScreen = binding.incLayoutTabItemsJournal
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
        val journalScreen = binding.incLayoutTabItemsJournal
        journalScreen.layoutJournalEntryDetail.visibility = View.GONE
        journalScreen.tvJournalHint.text = activity.getString(
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
        val journalScreen = binding.incLayoutTabItemsJournal
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
        val journalScreen = binding.incLayoutTabItemsJournal
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
        binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.root.visibility = View.GONE
    }
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
        navigator.popLevel()
        if (wasEditing) navigator.popLevel()
    }
    /** Save — курсор всегда приземляется на карточку сохранённой записи. */
    private fun performJournalEntrySave() {
        val popup = binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup
        val text = popup.etJournalEntryValue.text.toString()
        if (text.isBlank()) {
            playError()
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
        navigator.popLevel()
        if (editingId != null) navigator.popLevel()
        val index = journalEntrySidebarIndex(savedEntryId)
        bindJournalListAdapter(initialSelectedPosition = index)
        navigator.replaceChildrenOf("JOURNAL", journalChildrenNodes(), cursor = index)
    }
    /** Delete без подтверждения; курсор возвращается в список — узла записи больше нет. */
    private fun performJournalEntryDelete(entry: JournalEntry) {
        journalRepository.delete(entry.id)
        journalEntries.removeAll { it.id == entry.id }
        hideJournalEntryDetail()
        bindJournalListAdapter()
        navigator.popLevel()
        navigator.replaceChildrenOf("JOURNAL", journalChildrenNodes())
    }

    // ===== ДЕРЕВО ЭНКОДЕРА =====

    /** Дети JOURNAL; порядок и состав обязаны совпадать с journalSidebarItems() построчно. */
    private fun journalChildrenNodes(): List<MenuNode> {
        val sortedEntries = journalEntries.sortedByDescending { it.createdAtEpochMillis }
        val newEntryNode = MenuNode(
            id = "JOURNAL_NEW",
            onHighlight = {
                playTick()
                journalListAdapter.setSelectedPositionSilently(0)
                showJournalEntryEditorForNew()
            },
            children = journalEntryEditorChildrenNodes(null),
        )
        val entryNodes = sortedEntries.mapIndexed { index, entry ->
            MenuNode(
                id = "JOURNAL_ENTRY_${entry.id}",
                onHighlight = {
                    playTick()
                    journalListAdapter.setSelectedPositionSilently(index + 1)
                    showJournalEntryDetail(entry)
                },
                children = journalEntryDetailChildrenNodes(entry),
            )
        }
        return listOf(newEntryNode) + entryNodes + menuBackNode(
            { journalListAdapter.setSelectedPositionSilently(sortedEntries.size + 1) },
            { journalListAdapter.flashPressAnimation(sortedEntries.size + 1) },
        )
    }
    /** Дети записи Journal: Edit проваливается глубже, Delete и Back — листья с onActivate. */
    private fun journalEntryDetailChildrenNodes(entry: JournalEntry): List<MenuNode> {
        val journal = binding.incLayoutTabItemsJournal
        return listOfNotNull(
            MenuNode(
                id = "JOURNAL_ENTRY_EDIT",
                onHighlight = {
                    playTick()
                    // Пересобирает и карточку, и все три прицела — на случай возврата из редактора по Cancel/Save.
                    showJournalEntryDetail(entry)
                    setJournalEntryDetailEditFocused(true)
                },
                children = journalEntryEditorChildrenNodes(entry),
            ),
            MenuNode(
                id = "JOURNAL_ENTRY_DELETE",
                onHighlight = {
                    playTick()
                    setAllJournalEntryDetailFocusesHidden()
                    setJournalEntryDetailDeleteFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(journal.btnJournalEntryDetailDelete) {
                        playButton()
                        performJournalEntryDelete(entry)
                    }
                },
            ),
            // Только режимы с физическим энкодером: в Телефоне кнопкой нечем пользоваться.
            if (mode() != PipBoyMode.PHONE) MenuNode(
                id = "JOURNAL_ENTRY_BACK",
                onHighlight = {
                    playTick()
                    setAllJournalEntryDetailFocusesHidden()
                    setJournalEntryDetailBackFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(journal.btnJournalEntryDetailBack) {
                        playConfirm()
                        navigator.popLevel()
                    }
                },
            ) else null,
        )
    }
    /** Дети редактора записи — общие для создания и правки; onHighlight узла MIC открывает редактор идемпотентно. */
    private fun journalEntryEditorChildrenNodes(editingEntry: JournalEntry?): List<MenuNode> {
        val popup = binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup
        return listOf(
            MenuNode(
                id = "JOURNAL_EDITOR_MIC",
                onHighlight = {
                    playTick()
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
                    playTick()
                    setAllJournalEntryEditorFocusesHidden()
                    setJournalEntryEditorCancelFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnJournalEntryPopupCancel) {
                        playConfirm()
                        performJournalEntryCancel()
                    }
                },
            ),
            MenuNode(
                id = "JOURNAL_EDITOR_SAVE",
                onHighlight = {
                    playTick()
                    setAllJournalEntryEditorFocusesHidden()
                    setJournalEntryEditorSaveFocused(true)
                },
                onActivate = {
                    flashButtonPressThenRun(popup.btnJournalEntryPopupSave) {
                        playButton()
                        performJournalEntrySave()
                    }
                },
            ),
        )
    }
    /** Безусловно ставит курсор энкодера по [path] от детей узла JOURNAL — syncCursor() работает,
     * только если энкодер уже стоит на списке записей. */
    private fun syncJournalEncoderPath(path: List<Int>) = syncJournalEncoderPath(path, loud = true)
    /** То же без onHighlight — onHighlight узла JOURNAL перезагружает записи с диска. */
    private fun syncJournalEncoderPathSilently(path: List<Int>) = syncJournalEncoderPath(path, loud = false)
    private fun syncJournalEncoderPath(path: List<Int>, loud: Boolean) {
        val rootNodes = itemsMenuRoot()
        val rootIndex = rootNodes.indexOfFirst { it.id == "JOURNAL" }
        if (rootIndex == -1) return
        val fullPath = listOf(rootIndex) + path
        if (loud) navigator.setPath(rootNodes, fullPath) else navigator.setPathSilently(rootNodes, fullPath)
    }

    // ===== ПРИЦЕЛЫ ЭНКОДЕРА И ГЕЙТ ПО РЕЖИМУ =====

    private fun setFocusBracketsVisible(bracketsView: View, visible: Boolean) =
        applyFocusBrackets(bracketsView, mode(), visible)
    private fun setEncoderOnlyVisible(vararg views: View) = applyEncoderOnlyVisible(mode(), *views)
    /** Back на карточке записи Journal — та же схема, что у Menu на Гейгере. */
    private fun refreshJournalBackButtonVisibility() = setEncoderOnlyVisible(binding.incLayoutTabItemsJournal.btnJournalEntryDetailBack)
    /** Прицелы на карточке записи — Edit/Delete/Back. */
    private fun setJournalEntryDetailEditFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsJournal.viewJournalEntryDetailEditFocus, focused)
    }
    private fun setJournalEntryDetailDeleteFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsJournal.viewJournalEntryDetailDeleteFocus, focused)
    }
    private fun setJournalEntryDetailBackFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsJournal.viewJournalEntryDetailBackFocus, focused)
    }
    private fun setAllJournalEntryDetailFocusesHidden() {
        setJournalEntryDetailEditFocused(false)
        setJournalEntryDetailDeleteFocused(false)
        setJournalEntryDetailBackFocused(false)
    }
    /** Тот же приём на редакторе записи — Mic/Cancel/Save. */
    private fun setJournalEntryEditorMicFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.viewJournalEntryMicFocus, focused)
    }
    private fun setJournalEntryEditorCancelFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.viewJournalEntryPopupCancelFocus, focused)
    }
    private fun setJournalEntryEditorSaveFocused(focused: Boolean) {
        setFocusBracketsVisible(binding.incLayoutTabItemsJournal.incLayoutTabItemsJournalEntryPopup.viewJournalEntryPopupSaveFocus, focused)
    }
    private fun setAllJournalEntryEditorFocusesHidden() {
        setJournalEntryEditorMicFocused(false)
        setJournalEntryEditorCancelFocused(false)
        setJournalEntryEditorSaveFocused(false)
    }

    // ===== ТАЧ: КНОПКИ ЭКРАНА =====

    /** Зовётся из onCreate() активности на том же месте, где раньше стоял блок Журнала. */
    fun setup() {
        val journalScreen = binding.incLayoutTabItemsJournal
        val journalAccentColor = ColorStateList.valueOf(accentColor())
        journalScreen.tvJournalHint.setTextColor(accentColor())
        journalScreen.tvJournalEntryDetailDate.setTextColor(accentColor())
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
            playButton()
            suppressTickAround { syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 0, 0)) }
        }
        journalScreen.btnJournalEntryDetailDelete.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            suppressTickAround { syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 1)) }
            playButton()
            performJournalEntryDelete(entry)
        }
        // Back только поднимает курсор в боковое меню; видна лишь в режимах с физическим энкодером.
        journalScreen.btnJournalEntryDetailBack.setOnClickListener {
            val entry = selectedJournalEntryForDetail ?: return@setOnClickListener
            suppressTickAround { syncJournalEncoderPath(listOf(journalEntrySidebarIndex(entry.id), 2)) }
            playConfirm()
            navigator.popLevel()
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
            suppressTickAround { syncJournalEncoderPath(journalEditorPathPrefix() + 1) }
            playConfirm()
            performJournalEntryCancel()
        }
        journalEntryPopup.btnJournalEntryPopupSave.setOnClickListener {
            suppressTickAround { syncJournalEncoderPath(journalEditorPathPrefix() + 2) }
            playButton()
            performJournalEntrySave()
        }
    }
}
