package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class MarkerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val label: Button = itemView.findViewById(R.id.btn_recycler_marker_list)
    val deleteButton: Button = itemView.findViewById(R.id.btn_recycler_marker_list_delete)
}

/**
 * Список маркеров — тот же паттерн выбора с подсветкой, что PerkAdapter, плюс кнопка
 * удаления в строке (единственное, для чего в проекте не было готового образца).
 */
class MarkerAdapter(
    private var markerList: List<MapMarker>,
    private val selectedButtonBackground: Int,
    private val onSelect: (MapMarker) -> Unit,
    private val onDelete: (MapMarker) -> Unit
) : RecyclerView.Adapter<MarkerViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarkerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_marker_list, parent, false)
        return MarkerViewHolder(view)
    }

    override fun onBindViewHolder(holder: MarkerViewHolder, position: Int) {
        val marker = markerList[position]
        holder.label.text = marker.name
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
        holder.deleteButton.setOnClickListener {
            onDelete(marker)
        }
    }

    override fun getItemCount(): Int = markerList.size

    fun updateData(newMarkerList: List<MapMarker>) {
        markerList = newMarkerList
        selectedPosition = -1
        notifyDataSetChanged()
    }
}
