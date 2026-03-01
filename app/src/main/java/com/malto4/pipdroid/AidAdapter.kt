package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class AidViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val button: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
}

class AidAdapter(
    private var aidList: List<Map<String, String>>,
    private val selectedAidArray: Array<String>,
    private var selected_button: Int,
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<AidViewHolder>() {

    // Track the selected position, default is the first item
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AidViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false) // Custom layout
        return AidViewHolder(view)
    }

    override fun onBindViewHolder(holder: AidViewHolder, position: Int) {
        val aid = aidList[position]
        if(aid["aidcount"]!!.toInt()>1){
            holder.button.text = "${aid["name"]} (${aid["aidcount"]})"
        } else {
            holder.button.text = "${aid["name"]}"
        }

        // Apply background based on selection
        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(selected_button)  // Selected background
        } else {
            holder.itemView.setBackgroundResource(R.drawable.button_unselected)   // Default background
        }

        // Apply selection criteria
        if (aid["id"] in selectedAidArray) {
            holder.itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = holder.adapterPosition

                // Notify changes for previous and current item to refresh their backgrounds
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onItemClick(aid)
            }
        }
    }

    override fun getItemCount(): Int = aidList.size

    // Function to update the data and refresh RecyclerView
    fun clearData() {
        aidList = emptyList()  // Clear the list
        notifyDataSetChanged()   // Notify RecyclerView to refresh
    }
    // Function to update data with new entries
    fun updateData(newAidList: List<Map<String, String>>) {
        aidList = newAidList
        notifyDataSetChanged()  // Refresh the RecyclerView
    }
}