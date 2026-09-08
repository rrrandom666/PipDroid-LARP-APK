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
    // Раньше — фиксированные 44dp*density, никак не реагировали на сжатие рабочей области
    // (roadmap, этап 29 — "едет вёрстка колёс": цифра внутри уменьшается вместе с текстом
    // через GlobalTextScale, см. onCreateViewHolder ниже, а сам слот строки — нет,
    // рассинхрон). Теперь — треть РЕАЛЬНОЙ высоты recyclerView (сама RecyclerView уже
    // корректно уменьшается через layout_constraintHeight_percent в XML, ничего
    // дополнительно на неё не завязываем) — пересчитывается в addOnLayoutChangeListener
    // ниже при каждом изменении реальных габаритов колеса. 3 — видимых строк (пред./
    // текущая/след.), тот же расчёт, что раньше был неявно за фиксированными 44dp.
    private var itemHeightPx = (44 * recyclerView.resources.displayMetrics.density).toInt()
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
            // Своя, отдельная от SidebarMenuAdapter реализация RecyclerView.Adapter — тот же
            // рантайм-инфлейт после первого layout активности, глобальный масштаб текста
            // (roadmap, этап 29, GlobalTextScale) иначе эти View не увидит (найдено на
            // тесте: колёса Alarm/Timer оставались крупными на сжатом экране).
            GlobalTextScale.register(view)
            return ValueViewHolder(view)
        }
        override fun getItemCount() = Int.MAX_VALUE
        override fun onBindViewHolder(holder: ValueViewHolder, position: Int) {
            val value = range.first + (position % rangeSize)
            holder.text.text = String.format("%02d", value)
            // item_clock_wheel_value.xml задаёт 44dp только как исходное значение до первого
            // реального измерения — дальше высота строки следует за itemHeightPx (см. его
            // объявление выше), не фиксированным dp из XML.
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

        // RecyclerView скрыт (visibility=GONE) до первого открытия экрана Будильник —
        // высота 0, пока не случится первый реальный layout-проход. Ждём его явно, а не
        // recyclerView.post{}, иначе центрирующая математика ниже посчитает по нулевой
        // высоте и промахнётся.
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

        // Сжатие/восстановление рабочей области (roadmap, этап 29) меняет реальную высоту
        // recyclerView (она уже сама корректно ловит это через layout_constraintHeight_percent
        // в XML) уже ПОСЛЕ первого показа экрана — тот однократный addOnGlobalLayoutListener
        // выше к этому моменту давно снял себя. resettle=true — центрирование по padding
        // валидно только для конкретной itemHeightPx, на которой оно было посчитано; после
        // смены высоты нужно доскроллить текущее значение заново на новый центр, иначе
        // видимая позиция съедет от реального currentValue.
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

    private companion object {
        /** Видимых строк колеса (пред./текущая/след.) — тот же расчёт, что раньше был
         * неявно зашит в фиксированные 44dp на строку (roadmap, этап 29). */
        const val VISIBLE_ROWS = 3
    }
}
