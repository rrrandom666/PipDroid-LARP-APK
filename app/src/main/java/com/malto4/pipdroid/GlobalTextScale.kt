package com.malto4.pipdroid

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.max

/** Глобальный масштаб текста при сжатии рабочей области; синглтон, а не поле активности —
 * часть текста создаётся программно, и одноразовый обход дерева её не видит. */
/** Квадратные кнопки-иконки сюда намеренно не входят: высота текстовой кнопки нелинейна от
 * масштаба из-за фиксированного паддинга, иконки привязываются констрейнтами к соседней кнопке. */
object GlobalTextScale {

    private val originalTextSizesPx = mutableMapOf<TextView, Float>()
    private var scale = 1f
    private var minTextSizePx = 0f

    /** Зовётся в начале onCreate: синглтон живёт на уровне процесса и переживает recreate(). */
    fun reset() {
        originalTextSizesPx.clear()
        scale = 1f
        minTextSizePx = 0f
    }

    /** Пересчитывает и сразу применяет масштаб ко всем известным TextView. */
    fun setScale(newScale: Float, newMinTextSizePx: Float) {
        scale = newScale
        minTextSizePx = newMinTextSizePx
        originalTextSizesPx.forEach { (view, originalPx) -> applyTo(view, originalPx) }
    }

    /** Регистрирует один View и сразу применяет масштаб; идемпотентно — чертёжный размер не перезаписывается. */
    fun register(view: TextView) {
        val originalPx = originalTextSizesPx.getOrPut(view) { view.textSize }
        applyTo(view, originalPx)
    }

    /** Обходит поддерево и регистрирует все TextView — для статичного XML-дерева при первом layout. */
    fun registerTree(root: View) {
        if (root is TextView) register(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) registerTree(root.getChildAt(i))
        }
    }

    private fun applyTo(view: TextView, originalPx: Float) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, max(originalPx * scale, minTextSizePx))
    }
}
