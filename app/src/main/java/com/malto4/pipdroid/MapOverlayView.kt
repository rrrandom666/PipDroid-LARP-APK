package com.malto4.pipdroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

/**
 * Рисуется поверх PhotoView с картой (layout_tab_items_map.xml) — GPS-точка игрока
 * (Фаза D), маркеры игрока (Фаза E), позже линия маршрута (Фаза F). Координаты хранятся в
 * пространстве битмапа (пиксели map.png), а не экрана: displayMatrix (текущий пан/зум
 * PhotoView, см. PhotoView.getDisplayMatrix()) применяется к точкам вручную перед
 * отрисовкой — так толщина не плавает при зуме, в отличие от canvas.concat(matrix), который
 * отмасштабировал бы и сам Paint.strokeWidth.
 */
class MapOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var displayMatrix: Matrix? = null
    var userLocationPx: PointF? = null
        set(value) {
            field = value
            invalidate()
        }
    var markerPositionsPx: List<PointF> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    // Маркеры красятся акцентом текущей темы (тематизация — принцип проекта), в отличие от
    // GPS-точки игрока (та фиксированно красная, см. ниже) — MainActivity выставляет через
    // currentWizardAccentColor() при каждом открытии экрана карты.
    var accentColor: Int = Color.WHITE
        set(value) {
            field = value
            markerFillPaint.color = value
            invalidate()
        }

    // Фиксированный красный, а не акцент текущей темы — на White-теме акцент сам белый и
    // точка сливалась бы с картой. Ни одна из 4 тем (Green/Amber/White/Blue) не красная,
    // так что цвет не сольётся ни с одной из них. Чёрная обводка — контраст на светлых
    // участках (например, поверх Amber/White-тонированных дорог).
    private val userDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3B30")
        style = Paint.Style.FILL
    }
    private val userDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    // Ромб, не круг — форма нарочно отличается от GPS-точки игрока, чтобы на глаз не путать
    // "где я" с "где отметка".
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val markerOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val markerPath = Path()
    private val screenPointBuffer = FloatArray(2)

    private fun toScreen(bitmapPoint: PointF): PointF? {
        val matrix = displayMatrix ?: return null
        screenPointBuffer[0] = bitmapPoint.x
        screenPointBuffer[1] = bitmapPoint.y
        matrix.mapPoints(screenPointBuffer)
        return PointF(screenPointBuffer[0], screenPointBuffer[1])
    }

    private fun drawMarker(canvas: Canvas, screenPoint: PointF) {
        val r = 11f
        markerPath.reset()
        markerPath.moveTo(screenPoint.x, screenPoint.y - r)
        markerPath.lineTo(screenPoint.x + r, screenPoint.y)
        markerPath.lineTo(screenPoint.x, screenPoint.y + r)
        markerPath.lineTo(screenPoint.x - r, screenPoint.y)
        markerPath.close()
        canvas.drawPath(markerPath, markerFillPaint)
        canvas.drawPath(markerPath, markerOutlinePaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (markerPx in markerPositionsPx) {
            val screenPoint = toScreen(markerPx) ?: continue
            drawMarker(canvas, screenPoint)
        }
        val userPoint = userLocationPx?.let { toScreen(it) } ?: return
        canvas.drawCircle(userPoint.x, userPoint.y, 8f, userDotFillPaint)
        canvas.drawCircle(userPoint.x, userPoint.y, 13f, userDotRingPaint)
    }
}
