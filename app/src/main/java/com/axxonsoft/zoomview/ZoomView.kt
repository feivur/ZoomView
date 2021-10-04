package com.axxonsoft.zoomview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import timber.log.Timber
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Zooming view.
 */
class ZoomView : FrameLayout, ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    // zooming
    var zoom = 1.0f
        internal set
    var offsetX = 0f
    var offsetY = 0f
    var miniMapPosX = 10f
    var miniMapPosY = 10f

    private var maxZoom = 5.0f
    private var minZoom = 1.0f
    private var lastZoom = minZoom
    private var zoomX = 0f
    private var zoomY = 0f
    private var scrolling = false // NOPMD by karooolek on 29.06.11 11:45

    // minimap variables
    private var isMiniMapEnabled = false
    private var miniMapColorThumb = Color.WHITE
    private var miniMapColorBackground = Color.BLACK
    private var miniMapColorStroke = Color.GRAY
    private var miniMapSize = -1
    private var miniMapCaptionSize = 10
    private var miniMapCaptionColor = Color.WHITE
    private var miniMapAlignRight = false
    private var miniMapAlignBottom = false
    private var minimapCornerRadius = resources.getDimension(R.dimen.margin_s)

    // touching variables
    private var lastTapTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchLastX = 0f
    private var touchLastY = 0f
    private var startPinch = 0f
    private var pinching = false
    private var clickEnabled = true
    private var lastPinch = 0f
    private var lastdx1 = 0f
    private var lastdy1 = 0f
    private var lastdx2 = 0f
    private var lastdy2 = 0f

    // drawing
    private val m = Matrix()
    private val paint = Paint()

    private val DOUBLE_TAP_TIMEOUT_MS: Long = 200

    // listener
    var listener: ZoomViewListener? = null
        internal set

    val contentSize: PointF
        get() {
            val v = getChildAt(0)
            return PointF(v.width.toFloat(), v.height.toFloat())
        }

    private val scaleDetector = ScaleGestureDetector(context, this)
    private val gestureDetector = GestureDetector(context, this)

    /**
     * Zooming view listener interface.
     *
     * @author karooolek
     */
    interface ZoomViewListener {
        fun onZoomStarted()
        fun onZooming(zoom: Float, zoomx: Float, zoomy: Float)
        fun onZoomEnded()

        /**
         * @param x coordinate of touch
         * @param y coordinate of touch
         * @return double tap consumed by listener flag
         */
        fun onDoubleTap(x: Float, y: Float): Boolean
    }

    open class SimpleZoomViewListener : ZoomViewListener {
        override fun onZoomStarted() {}
        override fun onZooming(zoom: Float, zoomx: Float, zoomy: Float) {}
        override fun onZoomEnded() {}
        override fun onDoubleTap(x: Float, y: Float): Boolean = false
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

            miniMapAlignRight = a.getInt(R.styleable.zoomview_minimap_gravity, 0) and 0x01 == 0x01
            miniMapAlignBottom = a.getInt(R.styleable.zoomview_minimap_gravity, 0) and 0x02 == 0x02
            //boolean left = (a.getInt(R.styleable.zoomview_gravity, 0) & 0x04) == 0x04;

            a.recycle()
        }
    }

    fun getMaxZoom(): Float {
        return maxZoom
    }

    fun setMaxZoom(maxZoom: Float) {
        if (maxZoom < 1.0f) {
            return
        }

        this.maxZoom = maxZoom
    }

    fun setMiniMapSize(miniMapSize: Int) {
        if (miniMapSize < 0) {
            return
        }
        this.miniMapSize = miniMapSize
    }

    fun getMiniMapSize(): Int {
        return miniMapSize
    }

    fun zoomTo(zoom: Float, x: Float, y: Float) {
        this.zoom = min(zoom, maxZoom)
        zoomX = x
        zoomY = y
    }

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

    /**
     * Linear interpolation
     * */
    private fun lerp(from: Float, to: Float, k: Float): Float {
        return from + (to - from) * k
    }

    private fun bias(from: Float, to: Float, k: Float): Float {
        val distance = to - from
        return if (distance.absoluteValue >= k)
            from + k * distance.sign
        else
            to
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
    }

    private fun getView() = getChildAt(0)

    override fun onScale(sgt: ScaleGestureDetector): Boolean {
        zoom = (zoom * sgt.scaleFactor).coerceIn(minZoom, maxZoom)
        Timber.i("onScale zoom=$zoom, center=${sgt.focusX.toInt()}x${sgt.focusY.toInt()}")
        invalidate()
        return true
    }

    override fun onScaleBegin(sgt: ScaleGestureDetector?): Boolean {
        Timber.i("onScaleBegin")
        return true
    }

    override fun onScaleEnd(sgt: ScaleGestureDetector?) {
        Timber.i("onScaleEnd")
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

        if (width > getView().width * zoom)
            zoomX = (width / zoom - getView().width) / 2f
        else
            zoomX = zoomX.coerceIn(width / zoom - getView().width, 0f)

        if (height > getView().height * zoom)
            zoomY = (height / zoom - getView().height) / 2f
        else
            zoomY = zoomY.coerceIn(height / zoom - getView().height, 0f)

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


    private fun drawMiniMap(canvas: Canvas) {
        if (miniMapSize < 0)
            miniMapSize = min(width, height) / 4

        val aspectRatio = width.toFloat() / height.toFloat()
        val w: Float
        val h: Float
        if (aspectRatio > 1) { // landscape
            h = miniMapSize.toFloat()
            w = h * aspectRatio
        } else { // portrait
            w = miniMapSize.toFloat()
            h = w / aspectRatio
        }

        val offsetX = if (miniMapAlignRight)
            width - w.roundToInt() - miniMapPosX
        else
            miniMapPosX
        val offsetY = if (miniMapAlignBottom)
            height - h.roundToInt() - miniMapPosY
        else
            miniMapPosY
        canvas.translate(offsetX, offsetY)

        // background stroke
        val rectBg = RectF(0.0f, 0.0f, w, h)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x00FFFFFF and miniMapColorStroke or 0x70000000
        canvas.drawRoundRect(rectBg, minimapCornerRadius, minimapCornerRadius, paint)

        // background body
        paint.style = Paint.Style.FILL
        paint.color = 0x00FFFFFF and miniMapColorBackground or 0x70000000
        canvas.drawRoundRect(rectBg, minimapCornerRadius, minimapCornerRadius, paint)

        // thumb stroke
        val dx = w * zoomX / width
        val dy = h * zoomY / height
        val rectTh = RectF(
            dx - 0.5f * w / zoom, dy - 0.5f * h / zoom,
            dx + 0.5f * w / zoom, dy + 0.5f * h / zoom
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x00FFFFFF and miniMapColorStroke or 0x70000000
        canvas.drawRoundRect(rectTh, minimapCornerRadius, minimapCornerRadius, paint)

        // thumb body
        paint.style = Paint.Style.FILL
        paint.color = 0x00FFFFFF and miniMapColorThumb or 0x40000000
        canvas.drawRoundRect(rectTh, minimapCornerRadius, minimapCornerRadius, paint)

        val miniMapCaption = String.format("x%.1f", zoom)
        paint.textSize = miniMapCaptionSize.toFloat()
        paint.color = miniMapCaptionColor
        paint.isAntiAlias = true
        canvas.drawText(miniMapCaption, 10.0f, 10.0f + miniMapCaptionSize, paint)
        paint.isAntiAlias = false

        canvas.translate((-offsetX), (-offsetY))
    }
}