package com.malto4.pipdroid

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import android.content.Context
import android.content.SharedPreferences

class WeaponsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val icon: ImageView = itemView.findViewById(R.id.img_recycler_selectable_list_selector)
    val button: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
}

class WeaponsAdapter(
    private var weaponList: List<Map<String, String>>,
    private val selectedWeaponsArray: Array<String>,
    private var selected_button: Int,
    context: Context, // Pass context for SharedPreferences
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<WeaponsViewHolder>() {

    // SharedPreferences setup
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("PipDroid_Weapons", Context.MODE_PRIVATE)

    // Track the currently selected position and its type
    private var selectedPosition = 0
    private val selectedTypesMap = mutableMapOf<String, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeaponsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false) // Custom layout
        return WeaponsViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeaponsViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val weapon = weaponList[position]
        val weaponType = weapon["type"] ?: ""

        holder.button.text = "${weapon["name"]}"

        // Show the ImageView only if this item is selected and tracked in the map
        holder.icon.visibility = if (selectedTypesMap[weaponType] == position) View.VISIBLE else View.INVISIBLE

        // Apply background based on selection
        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(selected_button)  // Selected background
        } else {
            holder.itemView.setBackgroundResource(R.drawable.button_unselected)   // Default background
        }

        // Apply selection criteria
        if (weapon["id"] in selectedWeaponsArray) {
            holder.itemView.setOnClickListener {
                // Handle single click: Change background
                val previousPosition = selectedPosition
                selectedPosition = position

                // Refresh the old and new selected positions
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onItemClick(weapon)
            }

            holder.itemView.setOnLongClickListener {
                if (selectedTypesMap[weaponType] == position) {
                    // Toggle off: Hide the ImageView if already selected
                    selectedTypesMap.remove(weaponType)
                    notifyItemChanged(position)
                } else {
                    // Toggle on: Hide the previous selection and show the current one
                    val previousPosition = selectedTypesMap[weaponType]  // Get previous selection for this type
                    if (previousPosition != null) {
                        selectedTypesMap.remove(weaponType)
                        notifyItemChanged(previousPosition)  // Hide previous ImageView
                    }
                    selectedTypesMap[weaponType] = position  // Set the new selection
                    notifyItemChanged(position)  // Show new ImageView
                }

                saveSelectedPositions()  // Save the updated selection state
                true  // Indicates the long-press event is handled
            }
        }
    }

    override fun getItemCount(): Int = weaponList.size

    // Save the selected positions to SharedPreferences
    private fun saveSelectedPositions() {
        with(sharedPrefs.edit()) {
            selectedTypesMap.forEach { (type, pos) ->
                putInt(type, pos) // Save each type and its selected position
            }
            apply()
        }
    }

    // Load the selected positions from SharedPreferences (if needed in future)
    private fun loadSelectedPositions() {
        weaponList.forEach { weapon ->
            val type = weapon["type"] ?: ""
            val pos = sharedPrefs.getInt(type, -1)
            if (pos != -1) selectedTypesMap[type] = pos
        }
    }

    init {
        loadSelectedPositions()  // Load saved selections on adapter creation
    }

    // Function to update the data and refresh RecyclerView
    fun clearData() {
        weaponList = emptyList()  // Clear the list
        notifyDataSetChanged()   // Notify RecyclerView to refresh
    }
    // Function to update data with new entries
    fun updateData(newWeaponsList: List<Map<String, String>>) {
        weaponList = newWeaponsList
        notifyDataSetChanged()  // Refresh the RecyclerView
    }
}