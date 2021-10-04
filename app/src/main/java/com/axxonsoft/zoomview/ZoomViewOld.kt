package com.axxonsoft.zoomview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Zooming view.
 * https://github.com/Polidea/android-zoom-view
 */
class ZoomViewOld : FrameLayout {

    // zooming
    var zoom = 1.0f
        internal set
    var miniMapPosX = 10f
    var miniMapPosY = 10f

    private var maxZoom = 3.0f
    private var minZoom = 1.0f
    private var smoothZoom = minZoom
    private var lastZoom = minZoom
    private var zoomX = 0f
    private var zoomY = 0f
    private var smoothZoomX = 0f
    private var smoothZoomY = 0f
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

    private val DOUBLE_TAP_MAX_TIME_MS: Long = 200

    // listener
    var listener: ZoomViewListener? = null
        internal set

    val zoomFocusX: Float
        get() = zoomX * zoom

    val zoomFocusY: Float
        get() = zoomY * zoom

    val contentSize: PointF
        get() {
            val v = getChildAt(0)
            return PointF(v.width.toFloat(), v.height.toFloat())
        }

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
        smoothZoomTo(this.zoom, x, y)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            smoothZoomTo(1f, 0.5f, 0.5f)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {

        if (!isEnabled)
            return false

        when (ev.pointerCount) {
            1 -> processSingleTouchEvent(ev)
            else -> processDoubleTouchEvent(ev)
        }

        // redraw
        rootView.invalidate()
        invalidate()

        return true
    }

    private fun processSingleTouchEvent(ev: MotionEvent) {
        val w = miniMapSize * width.toFloat() / height
        val h = miniMapSize.toFloat()
        val touchingMiniMap = ev.x in miniMapPosX..(miniMapPosX + w) &&
                ev.y in miniMapPosY..(miniMapPosY + h)

        if (isMiniMapEnabled && smoothZoom > minZoom && touchingMiniMap) {
            processSingleTouchOnMinimap(ev)
        } else {
            processSingleTouchOutsideMinimap(ev)
        }
    }

    private fun processSingleTouchOnMinimap(ev: MotionEvent) {
        val x = ev.x
        val y = ev.y

        val w = miniMapSize * width.toFloat() / height
        val h = miniMapSize.toFloat()
        val zx = (x - 10.0f) / w * width
        val zy = (y - 10.0f) / h * height
        smoothZoomTo(smoothZoom, zx, zy)
    }

    private fun processSingleTouchOutsideMinimap(ev: MotionEvent) {
        val x = ev.x
        val y = ev.y
        val lx = x - touchStartX
        val ly = y - touchStartY
        val l = hypot(lx.toDouble(), ly.toDouble()).toFloat()
        val dx = x - touchLastX
        val dy = y - touchLastY
        touchLastX = x
        touchLastY = y

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                //Timber.i("action DOWN, ${ev.pointerCount}")
                touchStartX = x
                touchStartY = y
                touchLastX = x
                touchLastY = y
                scrolling = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (scrolling || smoothZoom > minZoom && l > 30.0f) {
                    if (!scrolling) {
                        Timber.i("start scrolling")
                        scrolling = true
                        ev.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(ev)
                    }
                    smoothZoomX -= dx / zoom
                    smoothZoomY -= dy / zoom
                    return
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_OUTSIDE -> {
                //Timber.i("action UP, ${ev.pointerCount}")

                if (isWaitingForSecondTap) {
                    // double tap detected
                    Timber.i("double tap detected")
                    clearWaitingForSecondTap()
                    val doubleTapConsumed = listener?.onDoubleTap(x, y) ?: false
                    if (!doubleTapConsumed) {
                        if (smoothZoom == minZoom) {
                            smoothZoomTo(3f.coerceAtMost(maxZoom), x, y)
                        } else {
                            smoothZoomTo(minZoom, width / 2.0f, height / 2.0f)
                        }
                        lastTapTime = 0
                        ev.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(ev)
                        return
                    }
                } else {
                    //Timber.i("start waitingForSecondTap")
                    isWaitingForSecondTap = true
                    waitingForSecondTap =
                        Completable.timer(DOUBLE_TAP_MAX_TIME_MS, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                isWaitingForSecondTap = false
                                if (!scrolling && clickEnabled) {
                                    //Timber.i("expired, performClick")
                                    performClick()
                                } else {
                                    //Timber.i("expired")
                                }
                                clickEnabled = true
                                //Timber.i("clickEnabled = $clickEnabled")
                            }
                }
            }

            else -> {
                //ev.setLocation(zoomX + (x - 0.5f * width) / zoom, zoomY + (y - 0.5f * height) / zoom)
                super.dispatchTouchEvent(ev)
            }
        }
    }

    private var isWaitingForSecondTap = false
    private var waitingForSecondTap = Disposable.disposed()

    private fun clearWaitingForSecondTap() {
        isWaitingForSecondTap = false
        waitingForSecondTap.dispose()
    }

    private fun processDoubleTouchEvent(ev: MotionEvent) {
        val x1 = ev.getX(0)
        val dx1 = x1 - lastdx1
        lastdx1 = x1
        val y1 = ev.getY(0)
        val dy1 = y1 - lastdy1
        lastdy1 = y1
        val x2 = ev.getX(1)
        val dx2 = x2 - lastdx2
        lastdx2 = x2
        val y2 = ev.getY(1)
        val dy2 = y2 - lastdy2
        lastdy2 = y2

        // pointers distance
        val currPinch = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
        val deltaPinch = currPinch - lastPinch
        lastPinch = currPinch
        val pinchDistance = abs(currPinch - startPinch)
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                //Timber.i("actionD DOWN, ${ev.pointerCount}")
                startPinch = currPinch
                pinching = true
                clickEnabled = false
            }

            MotionEvent.ACTION_MOVE -> if (pinching || pinchDistance > 30.0f) {
                pinching = true
                val dxk = (dx1 + dx2) / 2
                val dyk = (dy1 + dy2) / 2
                val targetZoom = zoom * currPinch / (currPinch - deltaPinch * 4)
                smoothZoomTo(targetZoom, zoomX - dxk / zoom, zoomY - dyk / zoom)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                //Timber.i("actionD UP, ${ev.pointerCount}")
                pinching = false
                if (zoom == minZoom)
                    listener?.onZoomEnded()
            }
        }

        ev.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(ev)
    }

    private fun smoothZoomTo(zoom: Float, x: Float, y: Float) {
        smoothZoom = zoom.coerceIn(minZoom, maxZoom)
        smoothZoomX = x
        smoothZoomY = y
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

        // do zoom
        val halfW = width / 2f
        val halfH = height / 2f

        zoom = lerp(bias(zoom, smoothZoom, 0.01f), smoothZoom, 0.2f)
        smoothZoomX = smoothZoomX.coerceIn(halfW / smoothZoom, width - halfW / smoothZoom)
        smoothZoomY = smoothZoomY.coerceIn(halfH / smoothZoom, height - halfH / smoothZoom)

        zoomX = lerp(bias(zoomX, smoothZoomX, 0.1f), smoothZoomX, 0.35f)
        zoomY = lerp(bias(zoomY, smoothZoomY, 0.1f), smoothZoomY, 0.35f)

        if (childCount == 0)
            return // nothing to draw

        // prepare matrix
        m.setTranslate(halfW, halfH)
        m.preScale(zoom, zoom)
        m.preTranslate(
            -zoomX.coerceIn(halfW / zoom, width - halfW / zoom),
            -zoomY.coerceIn(halfH / zoom, height - halfH / zoom)
        )

        // apply zoom
        val childView = getChildAt(0)
        m.preTranslate(childView.left.toFloat(), childView.top.toFloat())
        try {
            canvas.save()
            canvas.concat(m)
            childView.draw(canvas)
            canvas.restore()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isMiniMapEnabled && zoom > minZoom)
            drawMiniMap(canvas)

        if (zoom != lastZoom) {
            listener?.onZooming(zoom, zoomX, zoomY)
            rootView.invalidate()
            invalidate()

            if (lastZoom == minZoom && zoom > lastZoom)
                listener?.onZoomStarted()

            if (zoom == minZoom && lastZoom > zoom && !pinching)
                listener?.onZoomEnded()

            lastZoom = zoom
        }
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