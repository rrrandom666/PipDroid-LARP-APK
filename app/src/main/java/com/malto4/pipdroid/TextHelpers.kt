package com.malto4.pipdroid

/** Чистые текстовые хелперы без зависимостей от Android — вынесены из MainActivity ради тестов. */

/** Разбор произвольного числа из речи: сначала цифры, затем словесная форма до 59. */
internal fun parseRussianNumber(text: String): Int? {
    Regex("\\d+").find(text)?.value?.toIntOrNull()?.let { return it }
    val units = mapOf(
        "один" to 1, "одна" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4,
        "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
    )
    val teens = mapOf(
        "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13,
        "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17,
        "восемнадцать" to 18, "девятнадцать" to 19,
    )
    val tens = mapOf("двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50)
    val tokens = text.split(Regex("\\s+"))
    for (i in tokens.indices) {
        tens[tokens[i]]?.let { tensValue -> return tensValue + (units[tokens.getOrNull(i + 1)] ?: 0) }
        teens[tokens[i]]?.let { return it }
        units[tokens[i]]?.let { return it }
    }
    return null
}

/** Лёгкий стеммер: отрезает окончание, только если основа остаётся не короче трёх символов. */
internal fun russianStem(word: String): String {
    val suffixes = listOf(
        "иями", "иях", "ями", "ами", "его", "ого", "ему", "ому", "ыми", "ими",
        "ия", "ие", "ых", "их", "ев", "ов", "ей", "ой", "ый", "ая", "яя", "ую", "юю",
        "а", "я", "о", "е", "и", "ы", "у", "ю", "й", "ь",
    )
    for (suffix in suffixes) {
        if (word.length - suffix.length >= 3 && word.endsWith(suffix)) return word.dropLast(suffix.length)
    }
    return word
}

/** Имя отметки подходит запросу, если каждое слово запроса нашлось среди слов имени по основам. */
/** [queryTokens] обязаны приходить уже нормализованными (lowercase, ё→е) — это делает вызывающий. */
internal fun matchesMarkerQuery(queryTokens: List<String>, markerName: String): Boolean {
    if (queryTokens.isEmpty()) return false
    val markerStems = markerName.lowercase().replace('ё', 'е')
        .split(Regex("\\s+")).filter { it.isNotBlank() }.map { russianStem(it) }
    if (markerStems.isEmpty()) return false
    return queryTokens.map { russianStem(it) }.all { queryStem ->
        markerStems.any { markerStem -> markerStem.contains(queryStem) || queryStem.contains(markerStem) }
    }
}

/** Обрезает длинное имя посередине, сохраняя расширение: "vosk-model-small-ru-0.22.zip" -> "vosk-...0.22.zip". */
internal fun truncateFileName(name: String, keepStart: Int = 5, keepEnd: Int = 8): String {
    if (name.length <= keepStart + keepEnd + 3) return name
    return name.take(keepStart) + "..." + name.takeLast(keepEnd)
}
