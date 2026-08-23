package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class ModeSelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val button: Button = itemView.findViewById(R.id.btn_recycler_selectable_list)
}

/**
 * Список экрана выбора режима — тот же переиспользуемый item-layout, что у PerkAdapter
 * (layout_recycler_selectable_list.xml), но без гейтинга по "разблокировано" — все три
 * пункта всегда кликабельны, PipBoy 3000 просто нельзя подтвердить кнопкой [Выбрать]
 * (roadmap "Косметические правки мастера").
 */
class ModeSelectAdapter(
    private val modes: List<PipBoyMode>,
    private val modeLabel: (PipBoyMode) -> String,
    private val selectedButtonBackground: Int,
    private var selectedMode: PipBoyMode,
    private val onItemClick: (PipBoyMode) -> Unit
) : RecyclerView.Adapter<ModeSelectViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModeSelectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_recycler_selectable_list, parent, false)
        return ModeSelectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModeSelectViewHolder, position: Int) {
        val mode = modes[position]
        holder.button.text = modeLabel(mode)
        holder.itemView.setBackgroundResource(
            if (mode == selectedMode) selectedButtonBackground else R.drawable.button_unselected
        )
        holder.itemView.setOnClickListener { onItemClick(mode) }
    }

    override fun getItemCount(): Int = modes.size

    fun setSelectedMode(mode: PipBoyMode) {
        if (selectedMode == mode) return
        selectedMode = mode
        notifyDataSetChanged()
    }
}
