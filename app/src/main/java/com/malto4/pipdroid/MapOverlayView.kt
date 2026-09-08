package com.malto4.pipdroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/** Рисуется поверх PhotoView: точка игрока, отметки, линия маршрута. */
/** Точки хранятся в пространстве битмапа, матрица применяется вручную — canvas.concat()
 * отмасштабировал бы и толщину линий. */
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
    // Подпись рисуется прямо на карте, с подложкой — на пёстром фоне иначе нечитаемо.
    var markerPins: List<Pair<String, PointF>> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var routePx: List<PointF> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    // Точка игрока — фиксированный красный, не акцент: на White-теме акцент сам белый.
    private val userDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3B30")
        style = Paint.Style.FILL
    }
    private val userDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    // Отметки — фиксированный жёлтый: акцентом уже красится сама карта.
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD400")
        style = Paint.Style.FILL
    }
    private val markerOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val markerLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }
    private val markerLabelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 0, 0, 0)
        style = Paint.Style.FILL
    }
    // Маршрут — фиксированный пурпурный: не сливается ни с картой, ни с точкой игрока, ни с отметками.
    // Тёмный halo под линией — читаемость на светлых участках карты.
    private val routeHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val routeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2D95")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val markerPath = Path()
    private val routePath = Path()
    private val labelBoundsRect = Rect()
    private val screenPointBuffer = FloatArray(2)

    private fun toScreen(bitmapPoint: PointF): PointF? {
        val matrix = displayMatrix ?: return null
        screenPointBuffer[0] = bitmapPoint.x
        screenPointBuffer[1] = bitmapPoint.y
        matrix.mapPoints(screenPointBuffer)
        return PointF(screenPointBuffer[0], screenPointBuffer[1])
    }

    private fun drawMarker(canvas: Canvas, screenPoint: PointF, name: String) {
        val r = 11f
        markerPath.reset()
        markerPath.moveTo(screenPoint.x, screenPoint.y - r)
        markerPath.lineTo(screenPoint.x + r, screenPoint.y)
        markerPath.lineTo(screenPoint.x, screenPoint.y + r)
        markerPath.lineTo(screenPoint.x - r, screenPoint.y)
        markerPath.close()
        canvas.drawPath(markerPath, markerFillPaint)
        canvas.drawPath(markerPath, markerOutlinePaint)
        drawMarkerLabel(canvas, screenPoint, name)
    }

    private fun drawMarkerLabel(canvas: Canvas, screenPoint: PointF, name: String) {
        if (name.isBlank()) return
        markerLabelTextPaint.getTextBounds(name, 0, name.length, labelBoundsRect)
        val paddingH = 6f
        val paddingV = 4f
        val left = screenPoint.x - labelBoundsRect.width() / 2f - paddingH
        val top = screenPoint.y + 16f
        val right = screenPoint.x + labelBoundsRect.width() / 2f + paddingH
        val bottom = top + labelBoundsRect.height() + paddingV * 2
        canvas.drawRect(left, top, right, bottom, markerLabelBackgroundPaint)
        canvas.drawText(name, left + paddingH, bottom - paddingV, markerLabelTextPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (routePx.size >= 2) {
            routePath.reset()
            val matrix = displayMatrix
            if (matrix != null) {
                routePx.forEachIndexed { index, bitmapPoint ->
                    val screenPoint = toScreen(bitmapPoint) ?: return@forEachIndexed
                    if (index == 0) routePath.moveTo(screenPoint.x, screenPoint.y)
                    else routePath.lineTo(screenPoint.x, screenPoint.y)
                }
                canvas.drawPath(routePath, routeHaloPaint)
                canvas.drawPath(routePath, routeLinePaint)
            }
        }
        for ((name, markerPx) in markerPins) {
            val screenPoint = toScreen(markerPx) ?: continue
            drawMarker(canvas, screenPoint, name)
        }
        val userPoint = userLocationPx?.let { toScreen(it) } ?: return
        canvas.drawCircle(userPoint.x, userPoint.y, 8f, userDotFillPaint)
        canvas.drawCircle(userPoint.x, userPoint.y, 13f, userDotRingPaint)
    }
}
