package com.malto4.pipdroid

/** Редактор значения листа: ENCBTN переключает смысл ENC с движения курсора на дельту в [onAdjust]. */
class ValueEditor(
    val onAdjust: (delta: Int) -> Unit,
    val onEnter: () -> Unit = {},
    val onExit: () -> Unit = {},
)

/** Узел дерева меню: контейнер с детьми либо лист. */
/** onHighlight зовётся на каждое перемещение курсора и обязан быть безопасен без нажатия, onActivate — только по ENCBTN. */
/** childrenProvider пересчитывает состав детей на каждый провал — для списков, подгружаемых позже построения дерева. */
class MenuNode(
    val id: String,
    children: List<MenuNode> = emptyList(),
    val onActivate: (() -> Unit)? = null,
    val valueEditor: ValueEditor? = null,
    private val childrenProvider: (() -> List<MenuNode>)? = null,
    // childrenProvider обязан стоять до onHighlight: висячая лямбда резолвится в последний
    // функциональный параметр, иначе полсотни вызовов MenuNode("MAP") { ... } перестанут компилироваться.
    val onHighlight: () -> Unit = {},
) {
    private val staticChildren: List<MenuNode> = children
    val children: List<MenuNode> get() = childrenProvider?.invoke() ?: staticChildren
}

/** Курсор по дереву: стек уровней, на каждом свой список узлов и своя позиция. */
/** moveCursor заворачивает на границах; activateSelected выбирает исход по самому узлу —
 * редактор значения, провал к детям, действие на месте или подъём к родителю. */
class MenuNavigator {
    // [tag] — только для уровней из pushLevel(), у которых нет узла-родителя в дереве.
    private class Level(val nodes: List<MenuNode>, var cursor: Int, val tag: String? = null)

    private var stack: MutableList<Level> = mutableListOf()

    /** Узел, чей [ValueEditor] сейчас перехватывает `ENC` — null в обычном режиме навигации. */
    private var editingNode: MenuNode? = null

    /** Id узла с активным ValueEditor: тач по +/- должен входить в редактирование, только если энкодер там ещё не стоит. */
    fun editingNodeId(): String? = editingNode?.id

    /** Позиция курсора на верхнем уровне, не глубина стека — подсветка строки 2 следует за энкодером. */
    fun rootCursor(): Int = stack.firstOrNull()?.cursor ?: 0

    /** Тап по строке 2 задаёт позицию напрямую и сворачивает стек до верхнего уровня; onHighlight не зовёт. */
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

    /** Restore-путь: прыжок сразу на сохранённую позицию, без onHighlight промежуточных узлов. */
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
        // Двойной остаток даёт заворот на границах: в Kotlin остаток от отрицательного числа отрицателен.
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
                // onActivate здесь — побочный эффект ENCBTN, узел всё равно проваливается к детям следом.
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

    /** Явный подъём на уровень выше — зовётся из пункта "В меню", а не навязывается всем листьям. */
    fun popLevel() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            activateCurrent()
        }
    }

    /** Проваливается на новый уровень без узла-родителя — для плавающих панелей, возникающих как эффект действия. */
    fun pushLevel(nodes: List<MenuNode>, tag: String? = null) {
        if (nodes.isEmpty()) return
        stack.add(Level(nodes, 0, tag))
        activateCurrent()
    }

    /** Заменяет верхний уровень безусловно, без сверки родителя — для уровней из pushLevel(). */
    fun replaceTopLevel(nodes: List<MenuNode>, cursor: Int = 0, tag: String? = null) {
        if (stack.isEmpty() || nodes.isEmpty()) return
        val effectiveTag = tag ?: stack.last().tag
        stack[stack.size - 1] = Level(nodes, cursor.coerceIn(0, nodes.size - 1), effectiveTag)
        activateCurrent()
    }

    /** Синхронизация тача с курсором на запушенном уровне по [tag]; false — энкодер сейчас не на этой панели. */
    fun syncPushedCursor(tag: String, position: Int): Boolean {
        val level = stack.lastOrNull() ?: return false
        if (level.tag != tag) return false
        if (position !in level.nodes.indices) return false
        level.cursor = position
        return true
    }

    /** Жёстко ставит курсор на путь от корня дерева — для тача, ушедшего в ветку, которой энкодер не касался. */
    /** Тап по узлу с детьми обязан указывать путь до его первого ребёнка. */
    /** onHighlight зовётся только у последнего узла пути: там живут прицел и команда открыть панель. */
    fun setPath(rootNodes: List<MenuNode>, path: List<Int>) {
        val newStack = buildPathStack(rootNodes, path) ?: return
        stack = newStack
        activateCurrent()
    }

    /** То же без onHighlight конечного узла — когда сам этот вызов был бы разрушительным. */
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

    /** Пересобирает все захваченные уровни стека под текущий состав дерева — состав узлов гейтится
     * режимом работы, а уровень заморожен с момента провала в него. */
    /** Курсор восстанавливается по id узла, а не по индексу: в режиме с энкодером у корня ITEMS
     * появляется GEIGER первым пунктом и сдвигает все остальные разделы. */
    /** onHighlight не зовётся: пересборка идёт после смены режима, когда пользователь смотрит на
     * другой экран, и подъём подсветки увёл бы вкладку. */
    fun rebuildLevels(rootNodes: List<MenuNode>) {
        if (stack.isEmpty() || rootNodes.isEmpty()) return
        var currentNodes = rootNodes
        for (index in stack.indices) {
            val level = stack[index]
            // Плавающая панель из pushLevel() узла-родителя в дереве не имеет — её состав восстановить неоткуда.
            if (level.tag != null) return
            val previousId = level.nodes.getOrNull(level.cursor)?.id
            val restored = currentNodes.indexOfFirst { it.id == previousId }
            val cursor = if (restored >= 0) restored else level.cursor.coerceIn(0, currentNodes.size - 1)
            stack[index] = Level(currentNodes, cursor)
            currentNodes = currentNodes[cursor].children
            // Узел стал листом в новом режиме — уровней под ним больше нет.
            if (currentNodes.isEmpty()) {
                while (stack.size > index + 1) stack.removeAt(stack.size - 1)
                return
            }
        }
    }

    /** Заменяет узлы верхнего уровня, только если он порождён узлом [parentId], иначе no-op. */
    fun replaceChildrenOf(parentId: String, nodes: List<MenuNode>, cursor: Int = 0) {
        if (stack.size < 2 || nodes.isEmpty()) return
        val parentLevel = stack[stack.size - 2]
        val parentNode = parentLevel.nodes.getOrNull(parentLevel.cursor) ?: return
        if (parentNode.id != parentId) return
        editingNode = null
        stack[stack.size - 1] = Level(nodes, cursor.coerceIn(0, nodes.size - 1))
        activateCurrent()
    }

    /** Синхронизирует курсор текущего уровня с выбором тача; onHighlight не зовёт — визуал тач уже применил. */
    fun syncCursor(parentId: String, position: Int) {
        if (stack.size < 2) return
        val parentLevel = stack[stack.size - 2]
        val parentNode = parentLevel.nodes.getOrNull(parentLevel.cursor) ?: return
        if (parentNode.id != parentId) return
        val level = stack[stack.size - 1]
        if (position !in level.nodes.indices) return
        level.cursor = position
    }

    /** Читает позицию курсора внутри уровня [parentId] или null — нужно тачу, сохраняющему относительную глубину. */
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
