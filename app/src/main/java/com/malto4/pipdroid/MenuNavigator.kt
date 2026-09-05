package com.malto4.pipdroid

/**
 * Редактор значения листа (roadmap, этап 27 — SPECIAL/Skills) — `ENCBTN` на узле с
 * [ValueEditor] не проваливается вглубь и не поднимается наверх, а переключает смысл `ENC`:
 * вместо движения курсора по списку он теперь шлёт дельту в [onAdjust] (кнопки `+`/`-` на
 * экране). Повторный `ENCBTN` выходит обратно к обычной навигации по списку. [onEnter]/
 * [onExit] — точка для визуальной индикации "энкодер сейчас здесь", не обязательны.
 */
class ValueEditor(
    val onAdjust: (delta: Int) -> Unit,
    val onEnter: () -> Unit = {},
    val onExit: () -> Unit = {},
)

/**
 * Узел дерева меню (roadmap, "Модель навигации энкодером"). Контейнер — если есть дети,
 * лист — если нет. [onHighlight] — что показать на экране, когда курсор встал на этот узел,
 * вызывается на **каждое** перемещение `ENC` (обычно `View.performClick()` на существующей
 * touch-кнопке экрана либо безопасный preview типа смены картинки/описания) — должен быть
 * безопасен вызываться многократно без нажатия, без побочных действий вроде запуска
 * таймеров. [onActivate] — реальное одноразовое действие листа, вызывается только явным
 * `ENCBTN` (Status — запуск таймера ранения; roadmap этап 27, находка про Status). Лист без
 * [onActivate] и без [valueEditor] на `ENCBTN` просто поднимается к родителю, как раньше.
 * Если у узла есть и [children], и [onActivate] (Карта — крестовый прицел, roadmap, этап 28:
 * звук подтверждения нужен при любом `ENCBTN` по прицелу, не только в режиме без своих
 * детей) — узел всё равно проваливается в детей, [onActivate] при этом вызывается первым,
 * как побочный эффект самого факта провала, а не альтернатива ему (см.
 * [MenuNavigator.activateSelected]).
 *
 * [childrenProvider] — альтернатива статичному [children] для узлов, чей список детей может
 * меняться по составу, а не только по содержимому отдельного пункта, пока сам родительский
 * узел ещё не сконструирован заново (roadmap, этап 27 — находка на Journal: `itemsMenuRoot()`
 * вызывается один раз за вход в ITEMS и "запекает" список записей в момент, предшествующий
 * их реальной загрузке с диска через `openJournalScreen()` — при обычном статичном `children`
 * список остаётся тем самым, устаревшим снимком до явного `replaceChildrenOf()`, которого не
 * происходит, пока курсор ещё не провалился внутрь). [childrenProvider], если задан,
 * пересчитывается заново на каждый `ENCBTN`-провал в узел ([MenuNavigator.activateSelected]) —
 * то есть строго после того, как уже отработал [onHighlight] самого узла (курсор физически не
 * может провалиться внутрь узла, на который ещё не наведён), а тот для строк верхнего уровня
 * ITEMS/DATA/STATS обычно и есть точка, где актуальные данные подгружаются (см.
 * `btnItemsJournal.performClick()` → `openJournalScreen()`).
 */
class MenuNode(
    val id: String,
    children: List<MenuNode> = emptyList(),
    val onActivate: (() -> Unit)? = null,
    val valueEditor: ValueEditor? = null,
    private val childrenProvider: (() -> List<MenuNode>)? = null,
    // Последний параметр — единственный сохраняет существующий приём "trailing lambda"
    // (`MenuNode("MAP") { ... }`) без явного onHighlight = у полусотни существующих мест по
    // всему MainActivity.kt: Kotlin резолвит висячую лямбду в конце вызова на ПОСЛЕДНИЙ
    // параметр функционального типа, а не на конкретное имя — добавленный childrenProvider
    // должен стоять раньше него, иначе все такие вызовы молча перестанут компилироваться
    // (что и произошло при первой попытке — компилятор ловит расхождение типов сразу).
    val onHighlight: () -> Unit = {},
) {
    private val staticChildren: List<MenuNode> = children
    val children: List<MenuNode> get() = childrenProvider?.invoke() ?: staticChildren
}

/**
 * Курсор по дереву меню: стек уровней, на каждом — свой список узлов и своя позиция
 * курсора. Правила (roadmap, "Модель навигации энкодером"):
 * - [resetToRoot] — жёсткий сброс в корень (кнопки STATS/ITEMS/DATA), курсор на первый пункт
 * - [moveCursor] — двигает курсор по текущему уровню, **с заворотом на границах** (roadmap,
 *   этап 27 — доступность пункта "В меню": с первого пункта списка один шаг назад сразу
 *   попадает на последний, где он лежит, а не упирается в границу); если сейчас активен
 *   [ValueEditor] выбранного узла — вместо этого шлёт дельту в него
 * - [activateSelected] — поведение зависит от конкретного выделенного узла, не от уровня
 *   целиком: есть [ValueEditor] -> войти в редактирование значения; иначе есть дети ->
 *   вызвать [MenuNode.onActivate], если он тоже задан (побочный эффект, не альтернатива), и
 *   провалиться внутрь (курсор новый список с индекса 0); иначе, если детей нет, но есть
 *   [MenuNode.onActivate] -> вызвать его на месте, никуда не переходя; иначе (лист без
 *   действия) -> подняться к родителю, восстановив его прежнюю позицию курсора
 */
class MenuNavigator {
    // [tag] — только для уровней, запущенных через [pushLevel] (без узла-родителя в дереве,
    // поэтому обычному syncCursor()/setPathSilently(), которые сверяются по дереву, не с
    // чем сверяться) — см. [syncPushedCursor].
    private class Level(val nodes: List<MenuNode>, var cursor: Int, val tag: String? = null)

    private var stack: MutableList<Level> = mutableListOf()

    /** Узел, чей [ValueEditor] сейчас перехватывает `ENC` — null в обычном режиме навигации. */
    private var editingNode: MenuNode? = null

    /**
     * Id узла, чей [ValueEditor] сейчас активен — или `null` (roadmap, этап 27 — доработка
     * энкодер-эргономики SPECIAL/Skills). Нужен тач-коду кнопок `+`/`-`: тап должен войти в
     * редактирование, только если энкодер там ЕЩЁ не стоит — иначе повторный тап при
     * удержании кнопки заново проигрывал бы звук/визуал [ValueEditor.onEnter] на каждое
     * срабатывание [longPressRunnable] (`MainActivity.kt`), а не только на первое нажатие.
     */
    fun editingNodeId(): String? = editingNode?.id

    /**
     * Позиция курсора на верхнем уровне дерева (строка 2 шапки — Status/Special/... и т.п.),
     * не текущая глубина стека. Не меняется, пока курсор гуляет внутри вложенных уровней
     * (CND/RAD/EFF и т.п.) — родительский узел остаётся выделенным, пока мы у него внутри.
     * Используется, чтобы визуальная подсветка строки 2 (`renderRow2()` в MainActivity)
     * следовала за энкодером, а не только за тапами по самой строке 2.
     */
    fun rootCursor(): Int = stack.firstOrNull()?.cursor ?: 0

    /**
     * Обратная сторона [rootCursor] — тап по строке 2 руками задаёт позицию курсора
     * напрямую, не через [moveCursor]. Сворачивает стек до одного (верхнего) уровня: тап
     * по строке 2 логически равносилен свежему выбору пункта, а не продолжению drill-down,
     * в котором мы, возможно, были (CND/RAD/EFF и т.п.) — это уже и так поведение контента
     * (MenuNode.onHighlight кнопки строки 2 показывает именно дефолтный экран раздела).
     * Не вызывает `onHighlight` сама — вызывающий код (тап по строке 2) уже переключил контент.
     */
    fun setRootCursor(index: Int) {
        val root = stack.firstOrNull() ?: return
        if (index !in root.nodes.indices) return
        editingNode = null
        stack = mutableListOf(Level(root.nodes, index))
    }

    fun resetToRoot(rootNodes: List<MenuNode>) {
        if (rootNodes.isEmpty()) return
        editingNode = null
        stack = mutableListOf(Level(rootNodes, 0))
        activateCurrent()
    }

    /**
     * Restore-путь (roadmap, "Восстановление состояния после убийства процесса —
     * спецификация") — прыжок сразу на сохранённую позицию верхнего уровня, без
     * проигрывания onHighlight() промежуточных узлов, через которые пришлось бы пройти
     * повторными вызовами [moveCursor].
     */
    fun resetToRootAtIndex(rootNodes: List<MenuNode>, index: Int) {
        if (rootNodes.isEmpty()) return
        editingNode = null
        val clamped = index.coerceIn(0, rootNodes.size - 1)
        stack = mutableListOf(Level(rootNodes, clamped))
        activateCurrent()
    }

    fun moveCursor(delta: Int) {
        val editing = editingNode
        if (editing != null) {
            editing.valueEditor?.onAdjust?.invoke(delta)
            return
        }
        val level = stack.lastOrNull() ?: return
        val size = level.nodes.size
        // Modulo, не coerceIn — заворот на границах (roadmap, этап 27), не остановка.
        // Двойной % (не просто (x % size)) — в Kotlin остаток от отрицательного числа
        // отрицательный, "% size + size) % size" всегда приводит его в [0, size).
        val newCursor = ((level.cursor + delta) % size + size) % size
        if (newCursor == level.cursor) return
        level.cursor = newCursor
        activateCurrent()
    }

    fun activateSelected() {
        val editing = editingNode
        if (editing != null) {
            editingNode = null
            editing.valueEditor?.onExit?.invoke()
            return
        }
        val level = stack.lastOrNull() ?: return
        if (level.nodes.isEmpty()) return
        val node = level.nodes[level.cursor]
        when {
            node.valueEditor != null -> {
                editingNode = node
                node.valueEditor.onEnter()
            }
            node.children.isNotEmpty() -> {
                // onActivate здесь — не альтернатива провалу, а звук/побочный эффект самого
                // ENCBTN по узлу с детьми (Карта, крестовый прицел — roadmap, этап 28): узел
                // всё равно проваливается внутрь сразу следом, см. doc у MenuNode.onActivate.
                node.onActivate?.invoke()
                stack.add(Level(node.children, 0))
                activateCurrent()
            }
            node.onActivate != null -> node.onActivate.invoke()
            stack.size > 1 -> {
                // Подняться к родителю — его cursor не менялся, пока мы были внутри ребёнка.
                stack.removeAt(stack.size - 1)
                activateCurrent()
            }
            // На корневом уровне (нет родителя, стек из одного элемента) — no-op.
        }
    }

    /**
     * Явный "подняться на уровень выше" (roadmap, этап 27 — находка "нет способа подняться
     * из третьего уровня", после того как у его листьев появились `onActivate`/
     * `valueEditor` — раньше это был единственный, а не явный исход `ENCBTN` на таком
     * листе). Вызывается из пункта списка "В меню" (`MainActivity.kt`, `statsMenuRoot()`),
     * не из самого `activateSelected()` — конкретный лист сам решает, нужен ли ему такой
     * выход, а не дерево навязывает его всем поголовно.
     */
    fun popLevel() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            activateCurrent()
        }
    }

    /**
     * Проваливается на новый уровень НАПРЯМУЮ, без узла-родителя в дереве (roadmap, этап 27
     * — доработка) — для плавающих панелей, которые появляются программно как побочный
     * эффект действия (построенный маршрут открывает Start/Cancel, см.
     * mapRouteControlsChildrenNodes() в MainActivity.kt), а не как обычный провал по дереву
     * через существующий узел-родитель. [popLevel] с этого уровня сам вернёт туда, откуда он
     * был запушен — обычная механика стека, никакой специальной логики не требуется.
     */
    fun pushLevel(nodes: List<MenuNode>, tag: String? = null) {
        if (nodes.isEmpty()) return
        stack.add(Level(nodes, 0, tag))
        activateCurrent()
    }

    /**
     * Заменяет ТЕКУЩИЙ (верхний) уровень стека новым списком узлов на месте, безусловно —
     * не привязано к id родителя, в отличие от [replaceChildrenOf] (тот сверяет id и
     * безопасен для вызова "вслепую" из мест, не знающих, там ли сейчас энкодер). Для
     * уровней, запущенных через [pushLevel] (без родителя, id сверять не с чем) — там, где
     * вызывающий код и так точно знает, что энкодер сейчас на этом самом уровне (Start
     * пересобирает свою же панель в Stop, см. mapRouteControlsChildrenNodes()). [tag] по
     * умолчанию наследуется от текущего уровня (Start не обязан помнить его сам).
     */
    fun replaceTopLevel(nodes: List<MenuNode>, cursor: Int = 0, tag: String? = null) {
        if (stack.isEmpty() || nodes.isEmpty()) return
        val effectiveTag = tag ?: stack.last().tag
        stack[stack.size - 1] = Level(nodes, cursor.coerceIn(0, nodes.size - 1), effectiveTag)
        activateCurrent()
    }

    /**
     * Синхронизация тача с курсором энкодера на уровне, запущенном через [pushLevel] —
     * тот же смысл, что [syncCursor], но сверяется по [tag] самого уровня, а не по id
     * родителя (у запушенного уровня родителя в дереве нет). No-op (и `false`), если сейчас
     * на вершине стека не тот тег (энкодер не на этой панели) — тот же защитный принцип, что
     * у [syncCursor]/[replaceChildrenOf]. Возвращает `true`, только если реально применена —
     * у запушенных уровней нет узла-предка в дереве, которым можно было бы "дотянуться" до
     * них позже (в отличие от [setPathSilently]), поэтому вызывающий код (тач по Start/
     * Cancel/Stop в mapRouteControlsChildrenNodes()) обязан сам проверить результат перед
     * [popLevel]/[replaceTopLevel] — иначе рисковал бы применить их не к той панели.
     */
    fun syncPushedCursor(tag: String, position: Int): Boolean {
        val level = stack.lastOrNull() ?: return false
        if (level.tag != tag) return false
        if (position !in level.nodes.indices) return false
        level.cursor = position
        return true
    }

    /**
     * Жёстко ставит курсор энкодера на конкретный путь узлов от корня всего дерева (roadmap,
     * доработка после фидбека — найденный баг "энкодер не следует за тапами между узлами
     * дерева": [syncCursor] чинит только позицию ВНУТРИ уже активного уровня — если тач
     * переключился в СОВСЕМ другую ветку (с которой энкодер прежде не соприкасался вообще,
     * напр. Map Controls -> Build Route тачем, минуя энкодер), синхронизировать нечего,
     * старый уровень остаётся "залипшим"). [path] — индексы курсора на каждом уровне вниз от
     * корня, ровно те же индексы, что вызывающий код (тач-обработчик) обязан посчитать сам —
     * тап по узлу с детьми обязан указывать путь ДО его первого ребёнка (roadmap, доработка
     * после фидбека — "тап по узлу энкодера должен устанавливать курсор энкодера на первый
     * дочерний узел тапнутого узла", тот же исход, что и обычный ENCBTN на этом узле), а не
     * оставаться на самом тапнутом узле.
     *
     * Вызывает [MenuNode.onHighlight] ТОЛЬКО последнего узла пути (не промежуточных — тот же
     * приём, что у [resetToRootAtIndex]) — не "молча": прицел-уголки/подсветка и вся "команда
     * открыть эту панель" (напр. mapControlChildrenNodes() — сама панель Zoom/Pan/Center
     * открывается в onHighlight её первого ребёнка, не родителя) живут именно там, без этого
     * вызова экран показывал бы верный курсор внутри дерева, но не рисовал бы соответствующий
     * прицел (roadmap, доработка после фидбека — найденный баг "курсор на кнопке, а прицела
     * не видно"). Невалидный путь (индекс вне диапазона на любом шаге) — no-op, оставляет
     * текущее состояние как было, не ломает половину пути.
     */
    fun setPath(rootNodes: List<MenuNode>, path: List<Int>) {
        val newStack = buildPathStack(rootNodes, path) ?: return
        stack = newStack
        activateCurrent()
    }

    /**
     * То же самое, что [setPath], но без вызова [MenuNode.onHighlight] конечного узла —
     * для случаев, когда сам этот вызов был бы разрушительным побочным эффектом (roadmap,
     * доработка после фидбека — найденный баг: [setPath] на узел "MAP" внутри routeTo()
     * вызывал его onHighlight — `btnItemsMap.performClick()` — тот заново открывает экран
     * карты через openMapScreen(), которая сбрасывает mapRouteState и стирала только что
     * построенный маршрут). Использовать только там, где следующим шагом и так идёт
     * действие с собственным, безопасным эффектом (напр. [pushLevel] сразу поверх).
     */
    fun setPathSilently(rootNodes: List<MenuNode>, path: List<Int>) {
        val newStack = buildPathStack(rootNodes, path) ?: return
        stack = newStack
    }

    private fun buildPathStack(rootNodes: List<MenuNode>, path: List<Int>): MutableList<Level>? {
        if (rootNodes.isEmpty() || path.isEmpty()) return null
        var currentNodes = rootNodes
        val newStack = mutableListOf<Level>()
        for (index in path) {
            if (index !in currentNodes.indices) return null
            newStack.add(Level(currentNodes, index))
            currentNodes = currentNodes[index].children
        }
        editingNode = null
        return newStack
    }

    /**
     * Заменяет узлы ТЕКУЩЕГО (верхнего) уровня на лету, только если он был порождён узлом с
     * id [parentId] — иначе no-op, энкодер сейчас не там (roadmap, этап 27 — набор пунктов
     * Status меняется на ходу при смене фазы ранения: обычный список ранений/"В меню"
     * заменяется на один пункт Stop и обратно, не только при свежем входе в STATS). [cursor]
     * по умолчанию 0 — новый список почти всегда должен сразу сфокусировать первый (часто
     * единственный) пункт.
     */
    fun replaceChildrenOf(parentId: String, nodes: List<MenuNode>, cursor: Int = 0) {
        if (stack.size < 2 || nodes.isEmpty()) return
        val parentLevel = stack[stack.size - 2]
        val parentNode = parentLevel.nodes.getOrNull(parentLevel.cursor) ?: return
        if (parentNode.id != parentId) return
        editingNode = null
        stack[stack.size - 1] = Level(nodes, cursor.coerceIn(0, nodes.size - 1))
        activateCurrent()
    }

    /**
     * Синхронизирует курсор ТЕКУЩЕГО уровня с тем, что уже выбрал тач (roadmap, этап 27 —
     * находка "тач и энкодер расходятся": `SidebarMenuAdapter.selectPosition()` меняет
     * подсветку в самом адаптере и на тач, и на энкодер-активацию одинаково, но раньше не
     * сообщал об этом `MenuNavigator` — следующий `ENC` после тача продолжал листать с
     * прежней, а не с тронутой тачем позиции). Не вызывает `onHighlight`/`activateCurrent()`
     * — визуальный эффект тач уже применил сам, здесь только внутренняя бухгалтерия курсора.
     * No-op, если текущий уровень не был порождён узлом с id [parentId] (энкодер сейчас не
     * там) — тот же принцип защиты, что и у [replaceChildrenOf].
     */
    fun syncCursor(parentId: String, position: Int) {
        if (stack.size < 2) return
        val parentLevel = stack[stack.size - 2]
        val parentNode = parentLevel.nodes.getOrNull(parentLevel.cursor) ?: return
        if (parentNode.id != parentId) return
        val level = stack[stack.size - 1]
        if (position !in level.nodes.indices) return
        level.cursor = position
    }

    /**
     * Читает текущую позицию курсора ВНУТРИ уровня, порождённого узлом с id [parentId] — или
     * `null`, если энкодер сейчас не там (обратная сторона [syncCursor]/[replaceChildrenOf]:
     * те пишут курсор с той же защитой, эта — читает). Нужно тач-коду, которому важно не
     * просто переставить курсор в другую ветку, а сохранить относительную "глубину" — roadmap,
     * доработка после фидбека, найденный баг на Ringtones: `ENCBTN` на треке проваливает
     * курсор в [SELECT, BACK] под ним; тач по ДРУГОМУ треку, пока энкодер был там, обязан
     * перенести курсор на тот же дочерний узел (SELECT либо BACK) нового трека, а не просто
     * сбросить курсор на сам новый трек, теряя эту глубину.
     */
    fun cursorIfParent(parentId: String): Int? {
        if (stack.size < 2) return null
        val parentLevel = stack[stack.size - 2]
        val parentNode = parentLevel.nodes.getOrNull(parentLevel.cursor) ?: return null
        if (parentNode.id != parentId) return null
        return stack.last().cursor
    }

    private fun activateCurrent() {
        val level = stack.lastOrNull() ?: return
        if (level.nodes.isEmpty()) return
        level.nodes[level.cursor].onHighlight()
    }
}
