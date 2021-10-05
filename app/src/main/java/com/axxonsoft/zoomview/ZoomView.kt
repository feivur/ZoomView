package com.axxonsoft.zoomview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import timber.log.Timber
import kotlin.math.min

/**
 * Zooming view.
 */
class ZoomView : FrameLayout, ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    // zooming
    var zoom = 1.0f
        internal set

    var maxZoom = 3.0f
    var minZoom = 1.0f
        set(value) {
            field = value.coerceAtLeast(1f)
        }

    private var zoomX = 0f
    private var zoomY = 0f

    // minimap variables
    private var miniMapPosX = 10f
    private var miniMapPosY = 10f
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
    private var minimapCornerRadius = resources.getDimension(R.dimen.margin_s)

    // drawing
    private val m = Matrix()
    private val paint = Paint()

    var listener: ZoomViewListener? = null

    fun contentSize() = PointF(getView().width.toFloat(), getView().height.toFloat())

    private val scaleDetector = ScaleGestureDetector(context, this)
    private val gestureDetector = GestureDetector(context, this)

    /**
     * Zooming view listener interface.
     */
    interface ZoomViewListener {
        fun onZoomStarted()
        fun onZooming(zoom: Float, zoomx: Float, zoomy: Float)
        fun onZoomEnded()
    }

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        Timber.i("init")
        gestureDetector.setOnDoubleTapListener(this)
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

    fun zoomTo(zoom: Float, x: Float, y: Float) {
        this.zoom = min(zoom, maxZoom)
        zoomX = x
        zoomY = y
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

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            zoomTo(1f, 0.5f, 0.5f)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        if (!isEnabled)
            return false

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
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

    //endregion VIEW


    //region GESTURES

    override fun onScale(sgt: ScaleGestureDetector): Boolean {
        zoom = (zoom * sgt.scaleFactor).coerceIn(minZoom, maxZoom)
        // correct child offsets by zoom focus
        val sfx = (-zoomX * zoom + sgt.focusX) / getView().width / zoom // scaled focus X
        zoomX -= getView().width * sfx * (sgt.scaleFactor - 1) / zoom
        val sfy = (-zoomY * zoom + sgt.focusY) / getView().height / zoom // scaled focus Y
        zoomY -= getView().height * sfy * (sgt.scaleFactor - 1) / zoom

        fitChildInParent()

        if (sgt.previousSpan != sgt.currentSpan)
            listener?.onZooming(zoom, zoomX, zoomY)

        Timber.i("onScale zoom=$zoom, sfx=${(sfx * 100).toInt()}%, sfy=${(sfy * 100).toInt()}%")
        invalidate()
        return true
    }

    override fun onScaleBegin(sgt: ScaleGestureDetector?): Boolean {
        Timber.i("onScaleBegin")
        listener?.onZoomStarted()
        return true
    }

    override fun onScaleEnd(sgt: ScaleGestureDetector?) {
        Timber.i("onScaleEnd")
        listener?.onZoomEnded()
    }

    override fun onDown(me: MotionEvent): Boolean {
        Timber.i("onDown at ${me.x}x${me.y}")
        return false
    }

    override fun onShowPress(me: MotionEvent) {
        Timber.i("onShowPress at ${me.x}x${me.y}")
    }

    override fun onSingleTapUp(me: MotionEvent): Boolean {
        Timber.i("onSingleTapUp at ${me.x}x${me.y}")
        return false
    }

    override fun onScroll(me1: MotionEvent, me2: MotionEvent, dx: Float, dy: Float): Boolean {
        zoomX -= dx / zoom
        zoomY -= dy / zoom
        fitChildInParent()
        Timber.i("onScroll: zoomXY=${zoomX.toInt()}/${zoomY.toInt()}")
        invalidate()
        return true
    }


    override fun onLongPress(me: MotionEvent) {
        Timber.i("onLongPress at ${me.x}x${me.y}")
    }

    override fun onFling(me1: MotionEvent, me2: MotionEvent, vx: Float, vy: Float): Boolean {
        Timber.i("onLongPress at ${vx}|${vy}")
        return false
    }

    override fun onSingleTapConfirmed(me: MotionEvent): Boolean {
        Timber.i("onSingleTapConfirmed at ${me.x}x${me.y}")
        return false
    }

    override fun onDoubleTap(me: MotionEvent): Boolean {
        Timber.i("onDoubleTap at ${me.x}x${me.y}")
        return false
    }

    override fun onDoubleTapEvent(me: MotionEvent): Boolean {
        Timber.i("onDoubleTapEvent at ${me.x}x${me.y}")
        return false
    }

    //endregion GESTURES

}