package com.feivur.zoomview

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * Zooming view
 * Created by Feivur on 05.10.2021.
 * inspired by https://github.com/Polidea/android-zoom-view
 */

class ZoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        init(context, attrs)
    }

    var zoom = 1.0f
        internal set(value) {

            if (field == minZoom && value > field)
                listener?.onZoomStarted()

            if (value == minZoom && field > value)
                listener?.onZoomEnded()

            field = value
        }

    var minZoom = 1.0f
        set(value) {
            field = value.coerceAtLeast(1f)
        }
    var maxZoom = 3.0f

    var listener: ZoomViewListener? = null


    var miniMapPosX = 10f
    var miniMapPosY = 10f
    private var isMiniMapEnabled = false
    private var miniMapColorThumb = Color.WHITE
    private var miniMapColorBackground = Color.BLACK
    private var miniMapColorStroke = Color.GRAY
    private var miniMapSize = -1
        set(value) {
            field = value.coerceAtLeast(0)
        }
    var miniMapCaptionSize = 10
    private var miniMapCaptionColor = Color.WHITE
    private var miniMapAlignRight = false
    private var miniMapAlignBottom = false
    private var minimapCornerRadius = 16f

    private var zoomX = 0f
    private var zoomY = 0f
    private val m = Matrix()
    private val paint = Paint()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val TAG = BuildConfig.LIBRARY_PACKAGE_NAME

    /**
     * Zooming view listener interface.
     */
    interface ZoomViewListener {
        fun onZoomStarted()
        fun onZooming(zoom: Float, zoomx: Float, zoomy: Float)
        fun onZoomEnded()
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        Log.i(TAG, "init")
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.zoomview)
            if (a.hasValue(R.styleable.zoomview_minimap_enabled))
                isMiniMapEnabled =
                    a.getBoolean(R.styleable.zoomview_minimap_enabled, isMiniMapEnabled)

            if (a.hasValue(R.styleable.zoomview_minimap_color_thumb))
                miniMapColorThumb =
                    a.getColor(R.styleable.zoomview_minimap_color_thumb, miniMapColorThumb)

            if (a.hasValue(R.styleable.zoomview_minimap_color_background))
                miniMapColorBackground =
                    a.getColor(
                        R.styleable.zoomview_minimap_color_background,
                        miniMapColorBackground
                    )

            if (a.hasValue(R.styleable.zoomview_minimap_color_stroke))
                miniMapColorStroke =
                    a.getColor(R.styleable.zoomview_minimap_color_stroke, miniMapColorStroke)

            if (a.hasValue(R.styleable.zoomview_minimap_size))
                miniMapSize =
                    a.getDimensionPixelOffset(R.styleable.zoomview_minimap_size, miniMapSize)

            //if (a.hasValue(R.styleable.zoomview_minimap_caption))
            //    miniMapCaption = a.getString(R.styleable.zoomview_minimap_caption)

            if (a.hasValue(R.styleable.zoomview_minimap_caption_color))
                miniMapCaptionColor =
                    a.getColor(R.styleable.zoomview_minimap_caption_color, miniMapCaptionColor)

            if (a.hasValue(R.styleable.zoomview_minimap_caption_size))
                miniMapCaptionSize = a.getDimensionPixelOffset(
                    R.styleable.zoomview_minimap_caption_size,
                    miniMapCaptionSize
                )

            if (a.hasValue(R.styleable.zoomview_minimap_pos_x))
                miniMapPosX =
                    a.getDimensionPixelOffset(
                        R.styleable.zoomview_minimap_pos_x,
                        miniMapPosX.toInt()
                    ).toFloat()

            if (a.hasValue(R.styleable.zoomview_minimap_pos_y))
                miniMapPosY =
                    a.getDimensionPixelOffset(
                        R.styleable.zoomview_minimap_pos_y,
                        miniMapPosY.toInt()
                    ).toFloat()


            if (a.hasValue(R.styleable.zoomview_min_zoom))
                minZoom = a.getFloat(R.styleable.zoomview_min_zoom, 1f)

            if (a.hasValue(R.styleable.zoomview_max_zoom))
                maxZoom = a.getFloat(R.styleable.zoomview_max_zoom, 3f)

            miniMapAlignRight = a.getInt(R.styleable.zoomview_minimap_gravity, 0) and 0x01 == 0x01
            miniMapAlignBottom = a.getInt(R.styleable.zoomview_minimap_gravity, 0) and 0x02 == 0x02
            //boolean left = (a.getInt(R.styleable.zoomview_gravity, 0) & 0x04) == 0x04;

            a.recycle()
        }
    }

    /** Animated zoom to target value at focused place */
    fun zoom(targetZoom: Float, focusX: Float, focusY: Float) {
        val target = targetZoom.coerceIn(minZoom, maxZoom)
        val animator = ValueAnimator.ofFloat(zoom, target)
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            val current = it.animatedValue as Float
            zoomAt(current, focusX, focusY)
            fitChildInParent()
            invalidate()
        }
        animator.start()

    }

    private fun getView() = getChildAt(0)

    /** prevent white space around child view sides */
    private fun fitChildInParent() {
        if (width > getView().width * zoom)
            zoomX = (width / zoom - getView().width) / 2f
        else
            zoomX = zoomX.coerceIn(width / zoom - getView().width, 0f)

        if (height > getView().height * zoom)
            zoomY = (height / zoom - getView().height) / 2f
        else
            zoomY = zoomY.coerceIn(height / zoom - getView().height, 0f)
    }

    /** Zoom to target value at focused place */
    private fun zoomAt(target: Float, focusX: Float, focusY: Float) {
        // correct child offsets by zoom focus
        val newZoom = target.coerceIn(minZoom, maxZoom)
        val sfx = (-zoomX * zoom + focusX) / getView().width / zoom // scaled focus X
        zoomX -= getView().width * sfx * (newZoom / zoom - 1) / zoom
        val sfy = (-zoomY * zoom + focusY) / getView().height / zoom // scaled focus Y
        zoomY -= getView().height * sfy * (newZoom / zoom - 1) / zoom
        zoom = newZoom
    }

    private fun drawMiniMap(canvas: Canvas) {
        val aspectRatio = getView().width.toFloat() / getView().height.toFloat()
        val mapW: Float
        val mapH: Float
        if (aspectRatio > 1) { // landscape
            mapH = miniMapSize.toFloat()
            mapW = mapH * aspectRatio
        } else { // portrait
            mapW = miniMapSize.toFloat()
            mapH = mapW / aspectRatio
        }

        val offsetX = if (miniMapAlignRight)
            width - mapW - miniMapPosX
        else
            miniMapPosX
        val offsetY = if (miniMapAlignBottom)
            height - mapH - miniMapPosY
        else
            miniMapPosY
        canvas.translate(offsetX, offsetY)

        // background stroke
        val rectBg = RectF(0.0f, 0.0f, mapW, mapH)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x00FFFFFF and miniMapColorStroke or 0x70000000
        canvas.drawRoundRect(rectBg, minimapCornerRadius, minimapCornerRadius, paint)

        // background body
        paint.style = Paint.Style.FILL
        paint.color = 0x00FFFFFF and miniMapColorBackground or 0x70000000
        canvas.drawRoundRect(rectBg, minimapCornerRadius, minimapCornerRadius, paint)

        // thumb stroke
        val thumbX = (-zoomX / getView().width * mapW).coerceAtLeast(0f)
        val thumbY = (-zoomY / getView().height * mapH).coerceAtLeast(0f)
        val thumbR = ((-zoomX + width / zoom) / getView().width * mapW).coerceAtMost(mapW)
        val thumbB = ((-zoomY + height / zoom) / getView().height * mapH).coerceAtMost(mapH)
        val mapRect = RectF(thumbX, thumbY, thumbR, thumbB)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x00FFFFFF and miniMapColorStroke or 0x70000000
        canvas.drawRoundRect(mapRect, minimapCornerRadius, minimapCornerRadius, paint)

        // thumb body
        paint.style = Paint.Style.FILL
        paint.color = 0x00FFFFFF and miniMapColorThumb or 0x40000000
        canvas.drawRoundRect(mapRect, minimapCornerRadius, minimapCornerRadius, paint)

        val miniMapCaption = String.format("x%.1f", zoom)
        paint.textSize = miniMapCaptionSize.toFloat()
        paint.color = miniMapCaptionColor
        paint.isAntiAlias = true
        canvas.drawText(miniMapCaption, 10.0f, 10.0f + miniMapCaptionSize, paint)
        paint.isAntiAlias = false

        canvas.translate((-offsetX), (-offsetY))
    }


    //region VIEW


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled)
            return false

        gestureDetector.onTouchEvent(event)
        return scaleDetector.onTouchEvent(event)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        fitChildInParent()
    }

    override fun dispatchDraw(canvas: Canvas) {
        m.setTranslate(zoomX, zoomY)
        m.postScale(zoom, zoom)
        try {
            canvas.save()
            canvas.concat(m)
            getView().draw(canvas)
            canvas.restore()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isMiniMapEnabled && zoom > minZoom)
            drawMiniMap(canvas)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled)
            zoom(minZoom, width / 2f, height / 2f)
    }

    //endregion VIEW


    //region GESTURES

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(me: MotionEvent): Boolean {
            // Log.i( TAG,"onSingleTapConfirmed at ${me.x}x${me.y}")
            callOnClick()
            return false
        }

        override fun onDoubleTap(me: MotionEvent): Boolean {
            // Log.i( TAG,"onDoubleTap at ${me.x}x${me.y}")
            val targetZoom = if (zoom == minZoom) 3f else minZoom
            zoom(targetZoom, me.x, me.y)
            return false
        }

        override fun onDoubleTapEvent(me: MotionEvent): Boolean {
            //Log.i( TAG,"onDoubleTapEvent at ${me.x}x${me.y}")
            return false
        }

        override fun onScroll(me1: MotionEvent, me2: MotionEvent, dx: Float, dy: Float): Boolean {
            zoomX -= dx / zoom
            zoomY -= dy / zoom
            fitChildInParent()
            // Log.i( TAG,"onScroll: zoomXY=${zoomX.toInt()}/${zoomY.toInt()}")
            invalidate()
            return true
        }

        override fun onFling(me1: MotionEvent, me2: MotionEvent, vx: Float, vy: Float): Boolean {
            // Log.i( TAG,"onFling at ${vx}|${vy}")
            val dempfer = zoom * 10
            val start = PointF(zoomX, zoomY)
            val end = PointF(zoomX + vx / dempfer, zoomY + vy / dempfer)
            val animator = ValueAnimator.ofObject(EvaluatorOfPointF(), start, end)
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener {
                val current = it.animatedValue as PointF
                zoomX = current.x
                zoomY = current.y
                //Log.i( TAG,"onFling: zoomXY=${zoomX.toInt()}/${zoomY.toInt()}")
                fitChildInParent()
                invalidate()
            }
            animator.start()
            return false
        }

        override fun onDown(me: MotionEvent): Boolean {
            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(sgt: ScaleGestureDetector): Boolean {
            zoomAt(zoom * sgt.scaleFactor, sgt.focusX, sgt.focusY)
            fitChildInParent()

            if (sgt.previousSpan != sgt.currentSpan)
                listener?.onZooming(zoom, zoomX, zoomY)

            //Log.i( TAG,"onScale zoom=$zoom, sfx=${(sfx * 100).toInt()}%, sfy=${(sfy * 100).toInt()}%")
            invalidate()
            return true
        }
    }

    private class EvaluatorOfPointF : TypeEvaluator<PointF> {
        override fun evaluate(fraction: Float, p1: PointF, p2: PointF): PointF {
            //Log.i( TAG,"interpolator fraction = $fraction")
            return PointF(
                p1.x + (p2.x - p1.x) * fraction,
                p1.y + (p2.y - p1.y) * fraction
            )
        }
    }

    //endregion GESTURES
}