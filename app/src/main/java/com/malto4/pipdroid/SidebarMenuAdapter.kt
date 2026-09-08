package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Длительность имитации тач-нажатия для ENCBTN — общая для пунктов меню и отдельных кнопок. */
const val ENCODER_PRESS_FLASH_DURATION_MS = 100L

/** Один пункт бокового меню; [rightValue] только у SPECIAL и Skills. */
/** [enabled] — только затенение: тап всё равно доезжает до onSelect, исход решает сам экран. */
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

/** Общий адаптер бокового меню 3 уровня — замена шести копий подсветки и трёх адаптеров. */
/** [selectedBackgroundRes] — уже тема-зависимый ресурс, адаптер его не резолвит сам. */
/** [selectPosition] — единая точка входа для тапа и для энкодера; экраны, где наведение и
 * подтверждение должны различаться, зовут [setSelectedPositionSilently] отдельно. */
class SidebarMenuAdapter<T>(
    private var items: List<SidebarMenuItem<T>>,
    private val selectedBackgroundRes: Int,
    initialSelectedPosition: Int = 0,
    private val playSelectSound: () -> Unit,
    private val onSelect: (position: Int, item: SidebarMenuItem<T>) -> Unit,
) : RecyclerView.Adapter<SidebarMenuViewHolder>() {

    private var selectedPosition = initialSelectedPosition.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    // RecyclerView берём из onAttachedToRecyclerView, а не параметром: он зовётся раньше, чем
    // вызывающий код успел бы сохранить ссылку сам.
    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun scrollToReveal(position: Int) {
        recyclerView?.scrollToPosition(position)
    }

    /** Имитация нажатия для ENCBTN; post() — строка могла проехать в видимую область этим же кадром. */
    fun flashPressAnimation(position: Int) {
        val rv = recyclerView ?: return
        rv.post {
            val holder = rv.findViewHolderForAdapterPosition(position) as? SidebarMenuViewHolder ?: return@post
            holder.label.isPressed = true
            holder.label.postDelayed({ holder.label.isPressed = false }, ENCODER_PRESS_FLASH_DURATION_MS)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidebarMenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false)
        val holder = SidebarMenuViewHolder(view)
        // Пункты создаются в рантайме, и одноразовый обход дерева при старте их не видит.
        GlobalTextScale.register(holder.label)
        GlobalTextScale.register(holder.value)
        return holder
    }

    override fun onBindViewHolder(holder: SidebarMenuViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        // Гасим всю строку, а не только текст: рамку красит itemView.
        holder.itemView.alpha = if (item.enabled) 1.0f else 0.4f

        if (item.rightValue != null) {
            holder.value.text = item.rightValue
            holder.value.visibility = View.VISIBLE
        } else {
            holder.value.visibility = View.GONE
        }

        // enabled — тоже условие рамки: подсвеченная поверх затенения кнопка читалась как рабочая.
        holder.itemView.setBackgroundResource(
            if (position == selectedPosition && item.enabled) selectedBackgroundRes else R.drawable.button_unselected
        )
        // isSelected сам красит текст акцентом через дефолтный ColorStateList — гасим и здесь.
        holder.label.isSelected = position == selectedPosition && item.enabled

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

    /** Точечное обновление правого значения без пересборки списка и сброса выбора. */
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
        scrollToReveal(position)
        playSelectSound()
        onSelect(position, item)
    }

    /** Молча переставить рамку — без звука и onSelect, когда позицию меняет не тап игрока. */
    fun setSelectedPositionSilently(position: Int) {
        if (position !in items.indices) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous != position) notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
        scrollToReveal(position)
    }

    /** Гасит рамку целиком: курсор ещё на узле 2 уровня и не провалился в боковое меню. */
    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = -1
        if (previous in items.indices) notifyItemChanged(previous)
    }

    fun selectedPosition(): Int = selectedPosition

    fun currentItems(): List<SidebarMenuItem<T>> = items
}
