package com.malto4.pipdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Фиксируют текущее поведение хелперов, вынесенных из MainActivity при рефакторинге. */
class TextHelpersTest {

    @Test
    fun `цифры в тексте выигрывают у словесной формы`() {
        assertEquals(15, parseRussianNumber("таймер 15 минут"))
        assertEquals(7, parseRussianNumber("поставь 7"))
        assertEquals(3, parseRussianNumber("три часа 3 минуты"))
    }

    @Test
    fun `словесные числа до девятнадцати`() {
        assertEquals(1, parseRussianNumber("таймер одна минута"))
        assertEquals(2, parseRussianNumber("две минуты"))
        assertEquals(9, parseRussianNumber("девять"))
        assertEquals(10, parseRussianNumber("десять минут"))
        assertEquals(17, parseRussianNumber("семнадцать минут"))
    }

    @Test
    fun `десятки складываются со следующей единицей`() {
        assertEquals(20, parseRussianNumber("двадцать минут"))
        assertEquals(25, parseRussianNumber("двадцать пять минут"))
        assertEquals(45, parseRussianNumber("сорок пять"))
        assertEquals(50, parseRussianNumber("пятьдесят"))
    }

    @Test
    fun `без числа возвращает null`() {
        assertNull(parseRussianNumber("таймер"))
        assertNull(parseRussianNumber(""))
        assertNull(parseRussianNumber("сто минут"))
    }

    @Test
    fun `стеммер режет окончание только при основе от трёх символов`() {
        assertEquals("склад", russianStem("склада"))
        assertEquals("вышк", russianStem("вышки"))
        assertEquals("дом", russianStem("дом"))
        assertEquals("ров", russianStem("ров"))
    }

    @Test
    fun `стеммер предпочитает самое длинное подходящее окончание`() {
        assertEquals("деревн", russianStem("деревнями"))
        assertEquals("больш", russianStem("большого"))
    }

    @Test
    fun `имя отметки нормализуется, запрос приходит нормализованным`() {
        assertTrue(matchesMarkerQuery(listOf("склад"), "Старый Склад"))
        assertTrue(matchesMarkerQuery(listOf("склада"), "склад"))
        assertTrue(matchesMarkerQuery(listOf("елка"), "Ёлка у ворот"))
        // Ненормализованный запрос не совпадёт — ё в токенах не заменяется, это забота вызывающего.
        assertFalse(matchesMarkerQuery(listOf("ёлка"), "Ёлка у ворот"))
    }

    @Test
    fun `совпасть должны все слова запроса`() {
        assertTrue(matchesMarkerQuery(listOf("старый", "склад"), "Старый Склад"))
        assertFalse(matchesMarkerQuery(listOf("старый", "мост"), "Старый Склад"))
    }

    @Test
    fun `пустой запрос и пустое имя не совпадают`() {
        assertFalse(matchesMarkerQuery(emptyList(), "Склад"))
        assertFalse(matchesMarkerQuery(listOf("склад"), "   "))
    }

    @Test
    fun `короткое имя файла не обрезается`() {
        assertEquals("model.zip", truncateFileName("model.zip"))
        assertEquals("a".repeat(16), truncateFileName("a".repeat(16)))
    }

    @Test
    fun `длинное имя обрезается посередине с сохранением хвоста`() {
        assertEquals("vosk-...0.22.zip", truncateFileName("vosk-model-small-ru-0.22.zip"))
        assertEquals("ab...yz", truncateFileName("abcdefghijklmnopqrstuvwxyz", keepStart = 2, keepEnd = 2))
    }
}
