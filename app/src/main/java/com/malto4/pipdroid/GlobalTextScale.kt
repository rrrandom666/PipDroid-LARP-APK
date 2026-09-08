package com.malto4.pipdroid

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.max

/**
 * Глобальный масштаб текста при сжатии рабочей области (roadmap, этап 29). Синглтон, а не
 * метод/поле MainActivity — часть текста в приложении создаётся программно вне активности
 * (SidebarMenuAdapter.onCreateViewHolder — боковое меню 3 уровня SPECIAL/Skills/Perks/Clock/
 * Map/Journal/ModeSelect и т.п., setupRow2()/addPairingDevice()/listEntries() в самой
 * MainActivity.kt), и одноразовый обход дерева View при старте (`viewMain.post {}` в
 * onCreate) эти View физически не видит — их ещё не существует в момент обхода. Раньше
 * из-за этого меню 2 уровня и боковое меню не уменьшались вместе с остальным текстом
 * (найдено на тесте этапа 29). [register] — точка входа для любого места, создающего
 * TextView/Button в рантайме: вызывается сразу после создания, независимо от того, успел
 * ли уже произойти пинч/ресайз к этому моменту.
 *
 * Квадратные кнопки-иконки (Back/Edit/Route/Delete/Marker, roadmap, этап 29) сюда
 * намеренно НЕ входят — пробовали масштабировать их тем же способом (originalPx * scale),
 * но формула разошлась с тем, как реально меняется высота соседней ТЕКСТОВОЙ кнопки того же
 * стиля (PipWizardButtonStyle): паддинг кнопки (4dp+4dp) фиксирован и не масштабируется,
 * меняется только высота строки текста — значит высота текстовой кнопки НЕ линейна от
 * scale, а иконка при простом умножении растёт/сжимается быстрее и не совпадает (сначала
 * была слишком крупной, после этой находки — слишком мелкой). Правильное решение —
 * не приближение через синглтон, а точная ConstraintLayout-привязка Top/Bottom иконки к
 * реальной соседней кнопке (см. layout_tab_items_clock_alarm.xml и остальные — тот же приём,
 * что уже был у btn_journal_entry_mic/btn_marker_name_popup_mic).
 */
object GlobalTextScale {

    private val originalTextSizesPx = mutableMapOf<TextView, Float>()
    private var scale = 1f
    private var minTextSizePx = 0f

    /** Вызывается в самом начале MainActivity.onCreate — синглтон живёт на уровне процесса,
     * а не активности, и без сброса переживший recreate()/повторный запуск [scale] остаётся
     * от предыдущего инстанса (найдено на тесте: полноэкранный мод-селект после Settings ->
     * Save (которая делает recreate()) показывался с текстом, уменьшенным ещё с прошлой
     * сессии, хотя сам экран уже полноразмерный). Старые View из предыдущей активности всё
     * равно больше никому не нужны — весь их родительский root уже отсоединён от окна. */
    fun reset() {
        originalTextSizesPx.clear()
        scale = 1f
        minTextSizePx = 0f
    }

    /** Вызывается из MainActivity при любом изменении реальных габаритов bindingMain.root
     * (пинч, loadViewState(), resetToFullScreen() и т.п.) — пересчитывает и сразу
     * применяет масштаб ко всем уже известным TextView. */
    fun setScale(newScale: Float, newMinTextSizePx: Float) {
        scale = newScale
        minTextSizePx = newMinTextSizePx
        originalTextSizesPx.forEach { (view, originalPx) -> applyTo(view, originalPx) }
    }

    /** Регистрирует один TextView/Button и сразу применяет текущий масштаб. Идемпотентно —
     * повторный вызов на уже известном View не перезаписывает "чертёжный" размер уже
     * уменьшенным значением (getOrPut), это не даёт ошибке накапливаться при переиспользовании
     * (RecyclerView не пересоздаёт ViewHolder на каждый bind, но на всякий случай). */
    fun register(view: TextView) {
        val originalPx = originalTextSizesPx.getOrPut(view) { view.textSize }
        applyTo(view, originalPx)
    }

    /** Обходит поддерево и регистрирует все найденные TextView — точка входа для
     * одноразового снятия "чертёжных" размеров статичного XML-дерева при первом layout. */
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
