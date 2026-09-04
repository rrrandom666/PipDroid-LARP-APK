package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Вертикальный степпер-колесо свайпом (roadmap, "Часы — UX-спецификация", правка после
 * проверки на устройстве — кнопки +/- заменены на свайп по образцу системных часов
 * Android). Инерция прокрутки — родная физика RecyclerView/LinearSnapHelper, не своя
 * реализация. Бесконечный заворот на границах — Int.MAX_VALUE позиций адаптера, реальное
 * значение = position % [range].size, с большим стартовым индексом посередине, чтобы было
 * куда крутить в обе стороны.
 *
 * Затенение соседних значений — те же коэффициенты альфы, что уже в строке 2 шапки
 * (MainActivity.renderRow2(): 1.0 центр, 0.55 сосед через один шаг, 0.25 через два,
 * дальше 0), просто симметрично сверху и снизу вместо горизонтальной полосы.
 */
class ClockWheelPicker(
    private val recyclerView: RecyclerView,
    private val range: IntRange,
    initialValue: Int,
    private val onValueSettled: (Int) -> Unit,
    // Отдельно от onValueSettled (roadmap, этап 27, доработка после фидбека по Карте) — не
    // вызывается на программные scrollToValue() (те же самые onScrollStateChanged/settleValue,
    // которыми колесо и докручивает значение, когда крутит ENC через ValueEditor.onAdjust —
    // без разделения тач и синхронизация курсора энкодера с тачем сбрасывала бы ValueEditor
    // прямо во время его же собственной работы, см. syncClockEncoderPath() в MainActivity.kt).
    // Вызывается, только когда settle стал реальным результатом свайпа пальцем.
    private val onUserAdjusted: (() -> Unit)? = null,
) {
    private val rangeSize = range.last - range.first + 1
    private val itemHeightPx = (44 * recyclerView.resources.displayMetrics.density).toInt()
    private var currentValue = initialValue
    private var pendingInitialValue: Int? = initialValue
    // Найденный баг (не programmaticScroll-флаг вокруг scrollToValue() — тот ошибочно считал
    // "пальцем" второй settle одного и того же программного вызова: LinearSnapHelper после
    // ЛЮБОГО smoothScrollToPosition(), включая вызванный ENC, часто досылает свою
    // корректирующую доводку до идеального центра — это отдельный IDLE, флаг к тому моменту
    // уже погашен первым settle, второй settle ошибочно принимался за реальный тач и звал
    // onUserAdjusted, который через syncClockEncoderPath() сбрасывал ValueEditor энкодера
    // прямо посреди его же работы — воспроизводилось как "второе подряд ENC:-1 перескакивает
    // на соседний узел", см. roadmap). SCROLL_STATE_DRAGGING возникает ТОЛЬКО от реального
    // касания пальцем — ни smoothScrollToPosition(), ни доводка SnapHelper его не порождают
    // — поэтому это единственный надёжный признак, не зависящий от того, сколько промежуточных
    // settle-проходов случится до полной остановки.
    private var sawUserDrag = false

    private val layoutManager = LinearLayoutManager(recyclerView.context, LinearLayoutManager.VERTICAL, false)
    private val snapHelper = LinearSnapHelper()

    private class ValueViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private val adapter = object : RecyclerView.Adapter<ValueViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValueViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_clock_wheel_value, parent, false) as TextView
            return ValueViewHolder(view)
        }
        override fun getItemCount() = Int.MAX_VALUE
        override fun onBindViewHolder(holder: ValueViewHolder, position: Int) {
            val value = range.first + (position % rangeSize)
            holder.text.text = String.format("%02d", value)
        }
    }

    init {
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.clipToPadding = false
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                applyDimming()
            }
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    sawUserDrag = true
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    settleValue()
                }
            }
        })

        // RecyclerView скрыт (visibility=GONE) до первого открытия экрана Будильник —
        // высота 0, пока не случится первый реальный layout-проход. Ждём его явно, а не
        // recyclerView.post{}, иначе центрирующая математика ниже посчитает по нулевой
        // высоте и промахнётся.
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (recyclerView.height <= 0) return
                recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val visiblePadding = (recyclerView.height - itemHeightPx) / 2
                recyclerView.setPadding(0, visiblePadding, 0, visiblePadding)
                pendingInitialValue?.let { scrollToValue(it, smooth = false) }
                pendingInitialValue = null
                applyDimming()
            }
        })
    }

    private fun centerPosition(): Int {
        val centerView = snapHelper.findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
        return layoutManager.getPosition(centerView)
    }

    private fun applyDimming() {
        val centerY = recyclerView.height / 2f
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val childCenter = (child.top + child.bottom) / 2f
            val steps = abs(childCenter - centerY) / itemHeightPx
            child.alpha = when {
                steps < 0.5f -> 1.0f
                steps < 1.5f -> 0.55f
                steps < 2.5f -> 0.25f
                else -> 0.0f
            }
        }
    }

    private fun settleValue() {
        val pos = centerPosition()
        if (pos == RecyclerView.NO_POSITION) return
        currentValue = range.first + (pos % rangeSize)
        onValueSettled(currentValue)
        // sawUserDrag гасится тут же, а не в момент касания — settle того же самого жеста
        // может прийти не с первого IDLE (доводка SnapHelper), но какой бы по счёту он ни
        // был, он всё ещё относится к тому же реальному свайпу, пока флаг не погашен.
        if (sawUserDrag) onUserAdjusted?.invoke()
        sawUserDrag = false
    }

    /** Прокручивает колесо на конкретное значение — используется и для начальной
     * позиции, и когда значение меняется программно (например, сброс таймера). */
    fun scrollToValue(value: Int, smooth: Boolean = true) {
        val normalized = ((value - range.first) % rangeSize + rangeSize) % rangeSize
        val basePosition = (Int.MAX_VALUE / 2 / rangeSize) * rangeSize + normalized
        if (smooth) {
            recyclerView.smoothScrollToPosition(basePosition)
        } else {
            layoutManager.scrollToPositionWithOffset(basePosition, 0)
        }
        currentValue = value
    }

    fun currentValue(): Int = currentValue
}
