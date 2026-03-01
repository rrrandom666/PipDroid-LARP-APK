package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class ITEMSMiscViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val button: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
}

class ITEMSMiscAdapter(
    private var imiscList: List<Map<String, String>>,
    private val selectedITEMSMiscArray: Array<String>,
    private var selected_button: Int,
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<ITEMSMiscViewHolder>() {

    // Track the selected position, default is the first item
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ITEMSMiscViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false) // Custom layout
        return ITEMSMiscViewHolder(view)
    }

    override fun onBindViewHolder(holder: ITEMSMiscViewHolder, position: Int) {
        val imisc = imiscList[position]
        if(imisc["misccount"]!!.toInt()>1){
            holder.button.text = "${imisc["name"]} (${imisc["misccount"]})"
        } else {
            holder.button.text = "${imisc["name"]}"
        }

        // Apply background based on selection
        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(selected_button)  // Selected background
        } else {
            holder.itemView.setBackgroundResource(R.drawable.button_unselected)   // Default background
        }

        // Apply selection criteria
        if (imisc["id"] in selectedITEMSMiscArray) {
            holder.itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = holder.adapterPosition

                // Notify changes for previous and current item to refresh their backgrounds
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onItemClick(imisc)
            }
        }
    }

    override fun getItemCount(): Int = imiscList.size

    // Function to update the data and refresh RecyclerView
    fun clearData() {
        imiscList = emptyList()  // Clear the list
        notifyDataSetChanged()   // Notify RecyclerView to refresh
    }
    // Function to update data with new entries
    fun updateData(newITEMSMiscList: List<Map<String, String>>) {
        imiscList = newITEMSMiscList
        notifyDataSetChanged()  // Refresh the RecyclerView
    }
}