package com.malto4.pipdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Длительность имитации тач-нажатия для ENCBTN-действий (roadmap, этап 27) — общая и для
 * пунктов бокового меню ([SidebarMenuAdapter.flashPressAnimation]), и для отдельных кнопок
 * вроде Stop/`+`/`-` (MainActivity.kt, playButtonPressAnimation()/flashButtonPressThenRun()) —
 * один и тот же визуальный язык "нажатие энкодером" по всему приложению. */
const val ENCODER_PRESS_FLASH_DURATION_MS = 100L

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
 * [playSelectSound] — прокинутый снаружи вызов уже существующего `playTickAudio()`
 * (MainActivity.kt) вместо копии MediaPlayer-обвязки здесь: логика "как создать и не
 * утечь MediaPlayer" остаётся в одном месте, а звук при этом гарантированно играет каждый
 * раз, когда меняется выбор — вызывающий код не может забыть его вставить, т.к. адаптер
 * зовёт колбэк сам, без явного вызова на каждом месте использования.
 *
 * [selectPosition] — единая точка входа и для тапа (setOnClickListener ниже), и для
 * энкодера, когда движение курсора и подтверждение (`ENCBTN`) для конкретного экрана — одно
 * и то же действие (`MenuNode.onHighlight`/`onActivate`, `MainActivity.kt`) — раньше энкодер
 * работал через `View.performClick()` на реальной кнопке, что гарантировало одинаковое
 * поведение тача и энкодера "бесплатно"; теперь оба идут через один и тот же метод с тем же
 * эффектом. Экраны, где эти два момента должны отличаться (Status — roadmap, этап 27),
 * используют [setSelectedPositionSilently] отдельно от полного [selectPosition].
 */
class SidebarMenuAdapter<T>(
    private var items: List<SidebarMenuItem<T>>,
    private val selectedBackgroundRes: Int,
    initialSelectedPosition: Int = 0,
    private val playSelectSound: () -> Unit,
    private val onSelect: (position: Int, item: SidebarMenuItem<T>) -> Unit,
) : RecyclerView.Adapter<SidebarMenuViewHolder>() {

    private var selectedPosition = initialSelectedPosition.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    // Ссылка на свой RecyclerView (roadmap, этап 27 — "видимая область должна следовать за
    // курсором энкодера") — не передаётся снаружи отдельным параметром, а берётся из
    // штатного лайфстайл-метода адаптера: RecyclerView сам вызывает его в момент
    // `recyclerView.adapter = someAdapter`, раньше, чем вызывающий код успел бы что-то
    // сохранить сам.
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

    /** Визуальная имитация тач-нажатия для ENCBTN-действий (roadmap, этап 27 — "должна
     * срабатывать анимация нажатия, такая же, как при таче") — раньше срабатывал только
     * звук. `post()` — строка только что могла проехать в видимую область через
     * [scrollToReveal] этим же кадром, ViewHolder для неё появляется не раньше следующего
     * layout-прохода. Кратковременный `isPressed` true->false — стандартный приём вызвать
     * анимацию состояния кнопки (ripple/фон по `state_pressed`) без реального касания. */
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
        scrollToReveal(position)
        playSelectSound()
        onSelect(position, item)
    }

    /** Молча переставить рамку курсора — без звука, без [onSelect]. Нужно там, где позицию
     * меняет не тап игрока, а что-то другое: восстановление состояния после убийства
     * процесса, автоматическая эскалация таймера ранения на Status (Light -> Heavy сама
     * двигает рамку, это не выбор игрока), и `ENC` (движение курсора энкодером) на Status —
     * roadmap, этап 27: там подтверждение (запуск таймера) требует отдельного `ENCBTN`,
     * см. `MenuNode.onActivate` в `statsMenuRoot()`, `MainActivity.kt`. */
    fun setSelectedPositionSilently(position: Int) {
        if (position !in items.indices) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous != position) notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
        scrollToReveal(position)
    }

    /** Гасит рамку целиком, ни один пункт не выбран (roadmap, этап 27 — доработка
     * энкодер-эргономики: курсор энкодера стоит на узле меню 2 уровня, ещё не провалился
     * в боковое меню через `ENCBTN` — рамка не должна показывать пункт 0 как уже
     * выбранный). Симметрично [setSelectedPositionSilently] — без звука, без [onSelect]. */
    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = -1
        if (previous in items.indices) notifyItemChanged(previous)
    }

    fun selectedPosition(): Int = selectedPosition

    fun currentItems(): List<SidebarMenuItem<T>> = items
}
