package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class MarkerListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val button: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
}

/**
 * Список отметок — только выбор (переиспользует layout_recycler_selectable_list.xml, тот же
 * паттерн подсветки, что PerkAdapter), без удаления/действий в самой строке. Удаление и
 * маршрут — кнопки на карточке деталей выбранной отметки (см. layout_tab_items_map.xml).
 * Один и тот же список обслуживает и "Список меток" (корень меню), и "До отметки"
 * (подменю маршрута) — какая именно карточка деталей открывается по выбору, решает
 * MainActivity.kt.
 */
class MarkerListAdapter(
    private var markerList: List<MapMarker>,
    private val selectedButtonBackground: Int,
    private val onSelect: (MapMarker) -> Unit
) : RecyclerView.Adapter<MarkerListViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarkerListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false)
        return MarkerListViewHolder(view)
    }

    override fun onBindViewHolder(holder: MarkerListViewHolder, position: Int) {
        val marker = markerList[position]
        holder.button.text = marker.name
        holder.itemView.setBackgroundResource(
            if (position == selectedPosition) selectedButtonBackground else R.drawable.button_unselected
        )
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onSelect(marker)
        }
    }

    override fun getItemCount(): Int = markerList.size

    fun updateData(newMarkerList: List<MapMarker>) {
        markerList = newMarkerList
        selectedPosition = -1
        notifyDataSetChanged()
    }
}
