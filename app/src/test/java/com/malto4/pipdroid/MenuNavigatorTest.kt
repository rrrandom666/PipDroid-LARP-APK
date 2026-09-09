package com.malto4.pipdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Модель навигации энкодером — та, что зафиксирована в PipBoy_Roadmap.md. */
class MenuNavigatorTest {

    private val highlighted = mutableListOf<String>()

    /** Наблюдаемое состояние навигатора — след из onHighlight: ровно то, по чему живёт реальный UI. */
    private fun node(
        id: String,
        children: List<MenuNode> = emptyList(),
        onActivate: (() -> Unit)? = null,
        valueEditor: ValueEditor? = null,
        childrenProvider: (() -> List<MenuNode>)? = null,
    ) = MenuNode(
        id = id,
        children = children,
        onActivate = onActivate,
        valueEditor = valueEditor,
        childrenProvider = childrenProvider,
        onHighlight = { highlighted.add(id) },
    )

    @Test
    fun `курсор заворачивается на границах уровня`() {
        val nav = MenuNavigator()
        nav.resetToRoot(listOf(node("A"), node("B"), node("C")))
        nav.moveCursor(-1)
        nav.moveCursor(1)
        assertEquals(listOf("A", "C", "A"), highlighted)
    }

    /** Курсор родителя обязан быть НЕнулевым: с нулевым сохранение позиции неотличимо от сброса на первый пункт. */
    @Test
    fun `ENCBTN на листе поднимает к родителю и сохраняет его позицию`() {
        val nav = MenuNavigator()
        val special = node("SPECIAL", children = listOf(node("Сила"), node("Восприятие")))
        nav.resetToRoot(listOf(node("Status"), special, node("Skills")))
        nav.moveCursor(1)
        nav.activateSelected()
        nav.moveCursor(1)
        nav.activateSelected()
        assertEquals(
            listOf("Status", "SPECIAL", "Сила", "Восприятие", "SPECIAL"),
            highlighted,
        )
    }

    /** Тот же инвариант на втором пути подъёма — "В меню"/Back зовут popLevel() напрямую. */
    @Test
    fun `popLevel возвращает курсор родителя туда, откуда провалились`() {
        val nav = MenuNavigator()
        val special = node("SPECIAL", children = listOf(node("Сила")))
        nav.resetToRoot(listOf(node("Status"), special, node("Skills")))
        nav.moveCursor(1)
        nav.activateSelected()
        highlighted.clear()
        nav.popLevel()
        assertEquals(listOf("SPECIAL"), highlighted)
    }

    @Test
    fun `лист со своим действием не поднимается наверх, а повторяет действие`() {
        val nav = MenuNavigator()
        var fired = 0
        val leaf = node("LEAF", onActivate = { fired++ })
        nav.resetToRoot(listOf(node("P", children = listOf(leaf))))
        nav.activateSelected()
        nav.activateSelected()
        nav.activateSelected()
        assertEquals(2, fired)
        assertEquals(listOf("P", "LEAF"), highlighted)
    }

    @Test
    fun `ValueEditor перехватывает ENC до повторного ENCBTN`() {
        val nav = MenuNavigator()
        var value = 0
        var enters = 0
        var exits = 0
        val editor = ValueEditor(onAdjust = { value += it }, onEnter = { enters++ }, onExit = { exits++ })
        nav.resetToRoot(listOf(node("VOL", valueEditor = editor), node("NEXT")))
        nav.activateSelected()
        assertEquals("VOL", nav.editingNodeId())
        nav.moveCursor(3)
        nav.moveCursor(-1)
        assertEquals(2, value)
        assertEquals(listOf("VOL"), highlighted)
        nav.activateSelected()
        assertNull(nav.editingNodeId())
        assertEquals(1, enters)
        assertEquals(1, exits)
        nav.moveCursor(1)
        assertEquals(listOf("VOL", "NEXT"), highlighted)
    }

    @Test
    fun `setPath зовёт onHighlight только конечного узла, а Silently — ничей`() {
        val nav = MenuNavigator()
        val root = listOf(node("MAP", children = listOf(node("CHILD"), node("OTHER"))))
        nav.setPath(root, listOf(0, 0))
        assertEquals(listOf("CHILD"), highlighted)
        highlighted.clear()
        nav.setPathSilently(root, listOf(0, 1))
        assertTrue(highlighted.isEmpty())
    }

    @Test
    fun `setPath игнорирует путь за границами дерева`() {
        val nav = MenuNavigator()
        val root = listOf(node("MAP", children = listOf(node("CHILD"))))
        nav.setPath(root, listOf(0, 0))
        highlighted.clear()
        nav.setPath(root, listOf(0, 5))
        assertTrue(highlighted.isEmpty())
    }

    @Test
    fun `replaceChildrenOf — no-op на чужом родителе`() {
        val nav = MenuNavigator()
        nav.resetToRoot(listOf(node("A", children = listOf(node("X")))))
        nav.activateSelected()
        highlighted.clear()
        nav.replaceChildrenOf("B", listOf(node("Z")))
        assertTrue(highlighted.isEmpty())
        nav.replaceChildrenOf("A", listOf(node("Z")))
        assertEquals(listOf("Z"), highlighted)
    }

    @Test
    fun `syncPushedCursor признаёт только свой уровень и свой диапазон`() {
        val nav = MenuNavigator()
        nav.resetToRoot(listOf(node("ROOT")))
        nav.pushLevel(listOf(node("START"), node("CANCEL")), tag = "MAP_ROUTE_CONTROLS")
        assertFalse(nav.syncPushedCursor("ДРУГАЯ_ПАНЕЛЬ", 1))
        assertFalse(nav.syncPushedCursor("MAP_ROUTE_CONTROLS", 5))
        assertTrue(nav.syncPushedCursor("MAP_ROUTE_CONTROLS", 1))
        highlighted.clear()
        nav.moveCursor(1)
        assertEquals(listOf("START"), highlighted)
    }

    @Test
    fun `childrenProvider пересчитывает состав детей на каждый провал`() {
        val nav = MenuNavigator()
        var items = listOf("A")
        nav.resetToRoot(listOf(node("P", childrenProvider = { items.map { node(it) } })))
        nav.activateSelected()
        nav.activateSelected()
        items = listOf("B")
        nav.activateSelected()
        assertEquals(listOf("P", "A", "P", "B"), highlighted)
    }

    @Test
    fun `setRootCursor сворачивает стек и молчит`() {
        val nav = MenuNavigator()
        nav.resetToRoot(listOf(node("A", children = listOf(node("X"))), node("B")))
        nav.activateSelected()
        highlighted.clear()
        nav.setRootCursor(1)
        assertTrue(highlighted.isEmpty())
        assertEquals(1, nav.rootCursor())
        nav.activateSelected()
        assertTrue(highlighted.isEmpty())
    }

    @Test
    fun `resetToRootAtIndex клампит индекс в границы уровня`() {
        val nav = MenuNavigator()
        nav.resetToRootAtIndex(listOf(node("A"), node("B")), 7)
        assertEquals(1, nav.rootCursor())
        assertEquals(listOf("B"), highlighted)
    }

    /** Корень ITEMS в режиме Телефон: GEIGER скрыт, у экранов нет пункта "В меню". */
    private fun itemsRootPhone() = listOf(
        node("MAP", children = listOf(node("MARKERS"))),
        node("JOURNAL", children = listOf(node("JOURNAL_NEW"), node("JOURNAL_ENTRY_1"))),
        node("CLOCK", children = listOf(node("ALARM"))),
    )

    /** Тот же корень в режиме с энкодером: GEIGER встал первым и сдвинул всё, у экранов появился MENU. */
    private fun itemsRootEncoder() = listOf(
        node("GEIGER", children = listOf(node("RESET"))),
        node("MAP", children = listOf(node("MARKERS"), node("MENU"))),
        node("JOURNAL", children = listOf(node("JOURNAL_NEW"), node("JOURNAL_ENTRY_1"), node("MENU"))),
        node("CLOCK", children = listOf(node("ALARM"), node("MENU"))),
    )

    /** Смена режима на экране Журнала: боковой список получал пункт "В меню", а замороженный уровень — нет. */
    @Test
    fun `пересборка уровней открывает пункт, которого не было в прежнем режиме`() {
        val nav = MenuNavigator()
        nav.resetToRoot(itemsRootPhone())
        nav.moveCursor(1)
        nav.activateSelected()
        nav.moveCursor(1)
        highlighted.clear()
        nav.rebuildLevels(itemsRootEncoder())
        assertTrue(highlighted.isEmpty())
        nav.moveCursor(1)
        assertEquals(listOf("MENU"), highlighted)
    }

    /** Курсор корня обязан стоять на НЕнулевом узле: с нулевым восстановление по id неотличимо от сохранения индекса. */
    @Test
    fun `курсор корня восстанавливается по id узла, а не по индексу`() {
        val nav = MenuNavigator()
        nav.resetToRoot(itemsRootPhone())
        nav.moveCursor(1)
        assertEquals(1, nav.rootCursor())
        nav.rebuildLevels(itemsRootEncoder())
        // JOURNAL уехал с индекса 1 на 2: перед ним появился GEIGER.
        assertEquals(2, nav.rootCursor())
    }

    /** Обратный переход: MENU исчез, а курсор стоял на нём — уровень схлопывать нельзя, но и промахнуться мимо границы тоже. */
    @Test
    fun `курсор клампится, когда узла с прежним id в новом составе нет`() {
        val nav = MenuNavigator()
        nav.resetToRoot(itemsRootEncoder())
        nav.moveCursor(2)
        nav.activateSelected()
        nav.moveCursor(2)
        nav.rebuildLevels(itemsRootPhone())
        highlighted.clear()
        // В Телефоне у JOURNAL два ребёнка, курсор с третьего (MENU) прижат к последнему.
        nav.moveCursor(-1)
        assertEquals(listOf("JOURNAL_NEW"), highlighted)
    }

    /** Узел стал листом в новом режиме — уровни под ним держать не на чем. */
    @Test
    fun `пересборка сбрасывает уровни под узлом, потерявшим детей`() {
        val nav = MenuNavigator()
        nav.resetToRoot(listOf(node("A"), node("B", children = listOf(node("X"), node("Y")))))
        nav.moveCursor(1)
        nav.activateSelected()
        nav.rebuildLevels(listOf(node("A"), node("B")))
        highlighted.clear()
        // Стек свёрнут до корня: движение идёт по разделам, а не по детям B.
        nav.moveCursor(1)
        assertEquals(listOf("A"), highlighted)
    }

    /** Плавающая панель из pushLevel() родителя в дереве не имеет — восстанавливать её состав неоткуда. */
    @Test
    fun `пересборка не трогает запушенный уровень`() {
        val nav = MenuNavigator()
        nav.resetToRoot(itemsRootPhone())
        nav.moveCursor(1)
        nav.pushLevel(listOf(node("STOP"), node("REROUTE")), tag = "MAP_ROUTE_CONTROLS")
        highlighted.clear()
        nav.rebuildLevels(itemsRootEncoder())
        assertTrue(nav.syncPushedCursor("MAP_ROUTE_CONTROLS", 1))
        nav.moveCursor(1)
        assertEquals(listOf("STOP"), highlighted)
    }
}
