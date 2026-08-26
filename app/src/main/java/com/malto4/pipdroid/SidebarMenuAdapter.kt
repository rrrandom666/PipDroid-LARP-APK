package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Один пункт бокового меню 3 уровня (Roadmap, "Единый компонент бокового меню 3 уровня").
 * [rightValue] — только у SPECIAL/Skills (справа от названия). [enabled] — только
 * затенение (Status, alpha), НЕ блокирует тап/`selectPosition()` — курсор обязан
 * переезжать на пункт независимо от исхода (Status: тап по недоступной сейчас кнопке
 * ранения всё равно двигает рамку, просто вместо действия играет звук ошибки — это решает
 * колбэк [SidebarMenuAdapter.onSelect] у конкретного экрана, не сам адаптер).
 */
data class SidebarMenuItem<T>(
    val payload: T,
    val label: String,
    val rightValue: String? = null,
    val enabled: Boolean = true,
)

class SidebarMenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val label: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
    val value: TextView = itemView.findViewById(R.id.tv_recycler_selectable_list_value)
}

/**
 * Общий адаптер бокового меню 3 уровня — единая замена шести копий подсветки
 * (`setSelectedButton`/`setSelectedClockButton`/`setSelectedSPECIALButton`/
 * `setSelectedSKILLSButton`/`setSelectedSubMenuButton`/`setSelectedMapMenuButton`,
 * `MainActivity.kt`) и трёх параллельных RecyclerView-адаптеров (`PerkAdapter`/
 * `ModeSelectAdapter`/`MarkerListAdapter`).
 *
 * [selectedBackgroundRes] — уже тема-зависимый ресурс (`selected_button`), адаптер его не
 * резолвит сам — так уже было устроено во всех трёх старых RecyclerView-адаптерах, и это
 * осознанно: не путать с `currentWizardAccentColor()`/`backgroundTintList`, другим,
 * несовместимым путём тематизации (см. CLAUDE.md).
 *
 * [playSelectSound] — прокинутый снаружи вызов уже существующего `playItemSelectAudio()`
 * (MainActivity.kt) вместо копии MediaPlayer-обвязки здесь: логика "как создать и не
 * утечь MediaPlayer" остаётся в одном месте, а звук при этом гарантированно играет каждый
 * раз, когда меняется выбор — вызывающий код не может забыть его вставить, т.к. адаптер
 * зовёт колбэк сам, без явного вызова на каждом месте использования.
 *
 * [selectPosition] — единая точка входа и для тапа (setOnClickListener ниже), и для
 * энкодера (MenuNavigator.onSelect, MainActivity.kt) — раньше энкодер работал через
 * `View.performClick()` на реальной кнопке, что гарантировало одинаковое поведение тача и
 * энкодера "бесплатно"; теперь оба идут через один и тот же метод с тем же эффектом.
 */
class SidebarMenuAdapter<T>(
    private var items: List<SidebarMenuItem<T>>,
    private val selectedBackgroundRes: Int,
    initialSelectedPosition: Int = 0,
    private val playSelectSound: () -> Unit,
    private val onSelect: (position: Int, item: SidebarMenuItem<T>) -> Unit,
) : RecyclerView.Adapter<SidebarMenuViewHolder>() {

    private var selectedPosition = initialSelectedPosition.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidebarMenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false)
        return SidebarMenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: SidebarMenuViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        // На всей строке (рамку красит именно itemView, см. selectedBackgroundRes ниже),
        // не только на тексте — иначе рамка недоступного пункта остаётся яркой, гаснет
        // только текст (Status, фидбек по итогам тестирования).
        holder.itemView.alpha = if (item.enabled) 1.0f else 0.4f

        if (item.rightValue != null) {
            holder.value.text = item.rightValue
            holder.value.visibility = View.VISIBLE
        } else {
            holder.value.visibility = View.GONE
        }

        holder.itemView.setBackgroundResource(
            if (position == selectedPosition) selectedBackgroundRes else R.drawable.button_unselected
        )
        // Прокрутка длинного текста — только у выбранного пункта (тот же приём, что уже
        // был на Clock/Ringtones: playMelodySelectedMarqueeOnce()/highlightMelodyRow()).
        holder.label.isSelected = position == selectedPosition

        holder.itemView.setOnClickListener {
            selectPosition(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = items.size

    /** Полная замена списка (напр. Perks — фильтр, список меток — обновление с карты). */
    fun setItems(newItems: List<SidebarMenuItem<T>>, resetSelection: Boolean = true) {
        items = newItems
        if (resetSelection || selectedPosition >= items.size) {
            selectedPosition = 0
        }
        notifyDataSetChanged()
    }

    /** Точечное обновление правого значения (SPECIAL/Skills, кнопки +/-) без пересборки
     * всего списка и без сброса текущего выбора. */
    fun updateItemValue(position: Int, newValue: String) {
        if (position !in items.indices) return
        items = items.toMutableList().also { it[position] = it[position].copy(rightValue = newValue) }
        notifyItemChanged(position)
    }

    fun selectPosition(position: Int) {
        if (position !in items.indices) return
        val item = items[position]
        val previous = selectedPosition
        selectedPosition = position
        if (previous != position) notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
        playSelectSound()
        onSelect(position, item)
    }

    /** Молча переставить рамку курсора — без звука, без [onSelect]. Нужно там, где позицию
     * меняет не тап игрока, а что-то другое: восстановление состояния после убийства
     * процесса, автоматическая эскалация таймера ранения на Status (Light -> Heavy сама
     * двигает рамку, это не выбор игрока). */
    fun setSelectedPositionSilently(position: Int) {
        if (position !in items.indices) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous != position) notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
    }

    fun selectedPosition(): Int = selectedPosition

    fun currentItems(): List<SidebarMenuItem<T>> = items
}
