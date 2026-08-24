package com.malto4.pipdroid

/**
 * Узел дерева меню (roadmap, "Модель навигации энкодером"). Контейнер — если есть дети,
 * лист — если нет. [onSelect] — что показать на экране, когда курсор встал на этот узел
 * (обычно `View.performClick()` на существующей touch-кнопке соответствующего экрана —
 * так поведение энкодера гарантированно совпадает с уже рабочим поведением тача, без
 * дублирования логики показа/скрытия).
 */
class MenuNode(
    val id: String,
    val children: List<MenuNode> = emptyList(),
    val onSelect: () -> Unit = {},
)

/**
 * Курсор по дереву меню: стек уровней, на каждом — свой список узлов и своя позиция
 * курсора. Правила (roadmap, "Модель навигации энкодером"):
 * - [resetToRoot] — жёсткий сброс в корень (кнопки STATS/ITEMS/DATA), курсор на первый пункт
 * - [moveCursor] — двигает курсор по текущему уровню, без заворота на границах
 * - [activateSelected] — поведение зависит от конкретного выделенного узла, не от уровня
 *   целиком: есть дети -> провалиться внутрь (курсор новый список с индекса 0), детей нет
 *   -> подняться к родителю, восстановив его прежнюю позицию курсора
 */
class MenuNavigator {
    private class Level(val nodes: List<MenuNode>, var cursor: Int)

    private var stack: MutableList<Level> = mutableListOf()

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
     * (MenuNode.onSelect кнопки строки 2 показывает именно дефолтный экран раздела).
     * Не вызывает `onSelect` сама — вызывающий код (тап по строке 2) уже переключил контент.
     */
    fun setRootCursor(index: Int) {
        val root = stack.firstOrNull() ?: return
        if (index !in root.nodes.indices) return
        stack = mutableListOf(Level(root.nodes, index))
    }

    fun resetToRoot(rootNodes: List<MenuNode>) {
        if (rootNodes.isEmpty()) return
        stack = mutableListOf(Level(rootNodes, 0))
        activateCurrent()
    }

    /**
     * Restore-путь (roadmap, "Восстановление состояния после убийства процесса —
     * спецификация") — прыжок сразу на сохранённую позицию верхнего уровня, без
     * проигрывания onSelect() промежуточных узлов, через которые пришлось бы пройти
     * повторными вызовами [moveCursor].
     */
    fun resetToRootAtIndex(rootNodes: List<MenuNode>, index: Int) {
        if (rootNodes.isEmpty()) return
        val clamped = index.coerceIn(0, rootNodes.size - 1)
        stack = mutableListOf(Level(rootNodes, clamped))
        activateCurrent()
    }

    fun moveCursor(delta: Int) {
        val level = stack.lastOrNull() ?: return
        val newCursor = (level.cursor + delta).coerceIn(0, level.nodes.size - 1)
        if (newCursor == level.cursor) return
        level.cursor = newCursor
        activateCurrent()
    }

    fun activateSelected() {
        val level = stack.lastOrNull() ?: return
        if (level.nodes.isEmpty()) return
        val node = level.nodes[level.cursor]
        if (node.children.isNotEmpty()) {
            stack.add(Level(node.children, 0))
            activateCurrent()
        } else if (stack.size > 1) {
            // Подняться к родителю — его cursor не менялся, пока мы были внутри ребёнка.
            stack.removeAt(stack.size - 1)
            activateCurrent()
        }
        // На корневом уровне (нет родителя, стек из одного элемента) — no-op.
    }

    private fun activateCurrent() {
        val level = stack.lastOrNull() ?: return
        if (level.nodes.isEmpty()) return
        level.nodes[level.cursor].onSelect()
    }
}
