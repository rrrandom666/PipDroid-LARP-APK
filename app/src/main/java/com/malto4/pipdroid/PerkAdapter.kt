package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class PerkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val button: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
}

class PerkAdapter(
    private var perkList: List<Map<String, String>>,
    private val selectedPerkArray: Array<String>,
    private var selected_button: Int,
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<PerkViewHolder>() {

    // Track the selected position, default is the first item
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PerkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false) // Custom layout
        return PerkViewHolder(view)
    }

    override fun onBindViewHolder(holder: PerkViewHolder, position: Int) {
        val perk = perkList[position]
        holder.button.text = "${perk["name"]}"

        // Apply background based on selection
        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(selected_button)  // Selected background
        } else {
            holder.itemView.setBackgroundResource(R.drawable.button_unselected)   // Default background
        }

        // Apply selection criteria
        if (perk["id"] in selectedPerkArray) {
            holder.itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = holder.adapterPosition

                // Notify changes for previous and current item to refresh their backgrounds
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onItemClick(perk)
            }
        }
    }

    override fun getItemCount(): Int = perkList.size

    // Function to update the data and refresh RecyclerView
    fun clearData() {
        perkList = emptyList()  // Clear the list
        notifyDataSetChanged()   // Notify RecyclerView to refresh
    }
    // Function to update data with new entries
    fun updateData(newPerkList: List<Map<String, String>>) {
        perkList = newPerkList
        notifyDataSetChanged()  // Refresh the RecyclerView
    }
}