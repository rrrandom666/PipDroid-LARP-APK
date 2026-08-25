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

/**
 * Рисуется поверх PhotoView с картой (layout_tab_items_map.xml) — GPS-точка игрока
 * (Фаза D), отметки игрока (Фаза E), линия маршрута (Фаза F). Координаты хранятся в
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
    // Имя + позиция — подпись рисуется прямо на карте (не только в списках), с подложкой,
    // иначе на пёстром фоне карты нечитаемо.
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

    // GPS-точка игрока — фиксированный красный, не акцент темы: на White-теме акцент сам
    // белый и точка сливалась бы с картой. Ни одна из 4 тем (Green/Amber/White/Blue) не
    // красная, так что цвет не сольётся ни с одной из них.
    private val userDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3B30")
        style = Paint.Style.FILL
    }
    private val userDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    // Отметки — тоже фиксированный цвет, не акцент темы: акцентом уже красится сама карта
    // (дороги/объекты на тайле), отметка того же цвета сливалась бы с ней. Жёлтый не
    // конфликтует ни с одной из 4 тем и отличается от красной точки игрока.
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
    // Маршрут — тоже фиксированный цвет, не акцент темы: тем же акцентом уже красится сама
    // карта (дороги на тайле), линия того же цвета сливалась бы с ними. Голубой не
    // конфликтует ни с одной из 4 тем (в т.ч. с приглушённым Blue) и отличается от красной
    // точки игрока и жёлтых отметок. Тёмный halo под линией — читаемость на светлых
    // (Amber/White-тонированных) участках карты, тот же приём, что у точки/отметок.
    private val routeHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val routeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
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
