package com.malto4.pipdroid

import android.view.View

/** Помощники экранов, общие для MainActivity и контроллеров фич: прицел энкодера, гейт по режиму, флэш нажатия. */

/** В режиме Телефон прицелы не показываются никогда: там нет энкодера, курсор которого они рисуют. */
internal fun applyFocusBrackets(bracketsView: View, mode: PipBoyMode, visible: Boolean) {
    bracketsView.visibility = if (visible && mode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
}

/** Кнопки "назад"/"в меню" на экранах видны только в режимах с физическим энкодером. */
internal fun applyEncoderOnlyVisible(mode: PipBoyMode, vararg views: View) {
    val visibility = if (mode != PipBoyMode.PHONE) View.VISIBLE else View.GONE
    views.forEach { it.visibility = visibility }
}

/** Мгновенный флэш нажатия для непрерывных ENC-действий, где пауза читалась бы как лаг. */
internal fun flashButtonPressImmediate(button: View) {
    button.isPressed = true
    button.postDelayed({ button.isPressed = false }, ENCODER_PRESS_FLASH_DURATION_MS)
}

/** Флэш, затем действие — для ENCBTN-команд, которые сами прячут эту же кнопку. */
internal fun flashButtonPressThenRun(button: View, action: () -> Unit) {
    button.isPressed = true
    button.postDelayed({
        button.isPressed = false
        action()
    }, ENCODER_PRESS_FLASH_DURATION_MS)
}
