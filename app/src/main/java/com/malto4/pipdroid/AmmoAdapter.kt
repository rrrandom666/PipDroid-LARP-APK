package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class AmmoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val button: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
}

class AmmoAdapter(
    private var ammoList: List<Map<String, String>>,
    private val selectedAmmoArray: Array<String>,
    private var selected_button: Int,
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<AmmoViewHolder>() {

    // Track the selected position, default is the first item
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmmoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false) // Custom layout
        return AmmoViewHolder(view)
    }

    override fun onBindViewHolder(holder: AmmoViewHolder, position: Int) {
        val ammo = ammoList[position]
        holder.button.text = "${ammo["name"]} (${ammo["ammocount"]})"

        // Apply background based on selection
        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(selected_button)  // Selected background
        } else {
            holder.itemView.setBackgroundResource(R.drawable.button_unselected)   // Default background
        }

        // Apply selection criteria
        if (ammo["id"] in selectedAmmoArray) {
            holder.itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = holder.adapterPosition

                // Notify changes for previous and current item to refresh their backgrounds
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onItemClick(ammo)
            }
        }
    }

    override fun getItemCount(): Int = ammoList.size

    // Function to update the data and refresh RecyclerView
    fun clearData() {
        ammoList = emptyList()  // Clear the list
        notifyDataSetChanged()   // Notify RecyclerView to refresh
    }
    // Function to update data with new entries
    fun updateData(newAmmoList: List<Map<String, String>>) {
        ammoList = newAmmoList
        notifyDataSetChanged()  // Refresh the RecyclerView
    }
}