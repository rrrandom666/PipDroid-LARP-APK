package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/** Вертикальное колесо свайпом; инерция — родная физика RecyclerView, заворот — через Int.MAX_VALUE позиций. */
/** Затенение соседних значений — те же коэффициенты альфы, что в строке 2 шапки. */
class ClockWheelPicker(
    private val recyclerView: RecyclerView,
    private val range: IntRange,
    initialValue: Int,
    private val onValueSettled: (Int) -> Unit,
    // Отдельно от onValueSettled: программный scrollToValue() сюда не попадает, иначе синхронизация
    // сбрасывала бы ValueEditor во время его же работы.
    private val onUserAdjusted: (() -> Unit)? = null,
) {
    private val rangeSize = range.last - range.first + 1
    // Высота строки — треть реальной высоты колеса, а не фиксированные dp: иначе цифра уменьшается
    // вместе с текстом, а слот строки нет.
    private var itemHeightPx = (44 * recyclerView.resources.displayMetrics.density).toInt()
    private var currentValue = initialValue
    private var pendingInitialValue: Int? = initialValue
    // SCROLL_STATE_DRAGGING возникает только от касания пальцем — ни smoothScrollToPosition(), ни
    // доводка SnapHelper его не порождают, поэтому это единственный надёжный признак свайпа.
    private var sawUserDrag = false

    private val layoutManager = LinearLayoutManager(recyclerView.context, LinearLayoutManager.VERTICAL, false)
    private val snapHelper = LinearSnapHelper()

    private class ValueViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private val adapter = object : RecyclerView.Adapter<ValueViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValueViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_clock_wheel_value, parent, false) as TextView
            // Своя реализация адаптера — глобальный масштаб текста иначе эти View не увидит.
            GlobalTextScale.register(view)
            return ValueViewHolder(view)
        }
        override fun getItemCount() = Int.MAX_VALUE
        override fun onBindViewHolder(holder: ValueViewHolder, position: Int) {
            val value = range.first + (position % rangeSize)
            holder.text.text = String.format("%02d", value)
            // 44dp в разметке — только исходное значение до первого измерения, дальше высота следует за itemHeightPx.
            val lp = holder.text.layoutParams
            if (lp.height != itemHeightPx) {
                lp.height = itemHeightPx
                holder.text.layoutParams = lp
            }
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

        // RecyclerView скрыт до первого открытия экрана: ждём реальный layout, иначе центрирование посчитает по нулевой высоте.
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (recyclerView.height <= 0) return
                recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                applyHeight(recyclerView.height, resettle = false)
                pendingInitialValue?.let { scrollToValue(it, smooth = false) }
                pendingInitialValue = null
                applyDimming()
            }
        })

        // Сжатие рабочей области меняет высоту уже после первого показа, когда одноразовый слушатель себя снял.
        // resettle=true: центрирование валидно только для той itemHeightPx, на которой посчитано.
        recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val newHeight = bottom - top
            val oldHeight = oldBottom - oldTop
            if (newHeight > 0 && newHeight != oldHeight) {
                applyHeight(newHeight, resettle = true)
            }
        }
    }

    private fun applyHeight(recyclerViewHeightPx: Int, resettle: Boolean) {
        itemHeightPx = (recyclerViewHeightPx / VISIBLE_ROWS).coerceAtLeast(1)
        val visiblePadding = (recyclerViewHeightPx - itemHeightPx) / 2
        recyclerView.setPadding(0, visiblePadding, 0, visiblePadding)
        adapter.notifyDataSetChanged()
        if (resettle) {
            scrollToValue(currentValue, smooth = false)
        }
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
        // Флаг гасим здесь, а не при касании: settle того же свайпа может прийти не с первого IDLE.
        if (sawUserDrag) onUserAdjusted?.invoke()
        sawUserDrag = false
    }

    /** Прокручивает колесо на значение — и для начальной позиции, и для программной смены. */
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

    private companion object {
        /** Видимых строк колеса: предыдущая, текущая, следующая. */
        const val VISIBLE_ROWS = 3
    }
}
