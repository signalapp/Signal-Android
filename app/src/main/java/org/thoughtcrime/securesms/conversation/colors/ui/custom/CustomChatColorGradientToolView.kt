package org.thoughtcrime.securesms.conversation.colors.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Renders the gradient customization tool.
 *
 * The Gradient customization tool is two selectable circles on either side
 * of a rectangle with a pipe connecting them, a TOP and a BOTTOM (an edge)
 *
 * The user can then swap between the selected edge via a touch-down and can
 * drag the selected edge such that it traces around the outline of the square.
 * The other edge traces along the opposite side of the rectangle.
 *
 * The way the position along the edge is determined is by dividing the rectangle
 * into 8 right-angled triangles, all joining at the center. Using the specified
 * angle, we can determine which "octant" the top edge should be in, and can
 * determine its distance from the center point of the relevant edge, and use
 * similar logic to determine where the bottom edge lies.
 *
 * All of the math assumes an origin at the dead center of the view, and
 * that 0deg corresponds to a vector pointing directly towards the right hand
 * side of the view. This doesn't quite line up with what the gradient rendering
 * math requires, so we apply a simple function to degrees when it comes into or
 * leaves this tool (see `Float.invert`)
 */
class CustomChatColorGradientToolView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val clipRect = Rect()
  private val rect = RectF()
  private val center = PointF()
  private val top = PointF()
  private val bottom = PointF()

  private var selectedEdge: CustomChatColorEdge = CustomChatColorEdge.TOP
  private var degrees: Float = 18f
  private var listener: Listener? = null

  private val thumbRadius: Float = ViewUtil.dpToPx(THUMB_RADIUS).toFloat()
  private val thumbBorder: Float = ViewUtil.dpToPx(THUMB_BORDER).toFloat()
  private val thumbBorderSelected: Float = ViewUtil.dpToPx(THUMB_BORDER_SELECTED).toFloat()
  private val opaqueThumbRadius: Float = ViewUtil.dpToPx(OPAQUE_THUMB_RADIUS).toFloat()
  private val opaqueThumbPadding: Float = ViewUtil.dpToPx(OPAGUE_THUMB_PADDING).toFloat()
  private val opaqueThumbPaddingSelected: Float = ViewUtil.dpToPx(OPAGUE_THUMB_PADDING_SELECTED).toFloat()
  private val pipeWidth: Float = ViewUtil.dpToPx(PIPE_WIDTH).toFloat()
  private val pipeBorder: Float = ViewUtil.dpToPx(PIPE_BORDER).toFloat()

  private val topColorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.RED
  }

  private val bottomColorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLUE
  }

  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ContextCompat.getColor(context, R.color.signal_background_primary)
  }

  private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ContextCompat.getColor(context, R.color.signal_inverse_transparent_10)
  }

  private val thumbBorderPaintSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ContextCompat.getColor(context, R.color.signal_inverse_transparent_60)
  }

  private val pipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    strokeWidth = pipeWidth - pipeBorder * 2
    style = Paint.Style.STROKE
    color = ContextCompat.getColor(context, R.color.signal_background_primary)
  }

  private val pipeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    strokeWidth = pipeWidth
    style = Paint.Style.STROKE
    color = ContextCompat.getColor(context, R.color.signal_inverse_transparent_10)
  }

  private val gestureListener = GestureListener()
  private val gestureDetectorCompat = GestureDetectorCompat(context, gestureListener)

  fun setTopColor(@ColorInt color: Int) {
    topColorPaint.color = color
    invalidate()
  }

  fun setBottomColor(@ColorInt color: Int) {
    bottomColorPaint.color = color
    invalidate()
  }

  fun setSelectedEdge(selectedEdge: CustomChatColorEdge) {
    if (this.selectedEdge == selectedEdge) {
      return
    }

    this.selectedEdge = selectedEdge
    invalidate()

    listener?.onSelectedEdgeChanged(selectedEdge)
  }

  fun setDegrees(degrees: Float) {
    setDegreesInternal(degrees.invertDegrees())
  }

  private fun setDegreesInternal(degrees: Float) {
    if (this.degrees == degrees) {
      return
    }

    this.degrees = degrees
    invalidate()

    listener?.onDegreesChanged(degrees.invertDegrees())
  }

  private fun Float.invertDegrees(): Float = 360f - rotate(90f)

  fun setListener(listener: Listener) {
    this.listener = listener
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP) {
      listener?.onGestureFinished()
    } else if (event.action == MotionEvent.ACTION_DOWN) {
      listener?.onGestureStarted()
    }

    return gestureDetectorCompat.onTouchEvent(event)
  }

  override fun onDraw(canvas: Canvas) {
    canvas.getClipBounds(clipRect)
    rect.set(clipRect)
    rect.inset(thumbRadius, thumbRadius)

    center.set(rect.width() / 2f, rect.height() / 2f)

    val alpha = atan((rect.height() / rect.width())).toDegrees()
    val beta = (360.0 - alpha * 4) / 4f

    if (degrees < alpha) {
      // Right top
      val a = center.x
      val b = a * tan(degrees.toRadians())
      top.set(rect.width(), center.y - b)
      bottom.set(0f, center.y + b)
    } else if (degrees < 90f) {
      // Top right
      val phi = 90f - degrees
      val a = center.y
      val b = a * tan(phi.toRadians())
      top.set(center.x + b, 0f)
      bottom.set(center.x - b, rect.height())
    } else if (degrees < (90f + beta)) {
      // Top left
      val phi = degrees - 90f
      val a = center.y
      val b = a * tan(phi.toRadians())
      top.set(center.x - b, 0f)
      bottom.set(center.x + b, rect.height())
    } else if (degrees < 180f) {
      // left top
      val phi = 180f - degrees
      val a = center.x
      val b = a * tan(phi.toRadians())
      top.set(0f, center.y - b)
      bottom.set(rect.width(), center.y + b)
    } else if (degrees < (180f + alpha)) {
      // left bottom
      val phi = degrees - 180f
      val a = center.x
      val b = a * tan(phi.toRadians())
      top.set(0f, center.y + b)
      bottom.set(rect.width(), center.y - b)
    } else if (degrees < 270f) {
      // bottom left
      val phi = 270f - degrees
      val a = center.y
      val b = a * tan(phi.toRadians())
      top.set(center.x - b, rect.height())
      bottom.set(center.x + b, 0f)
    } else if (degrees < (270f + beta)) {
      // bottom right
      val phi = degrees - 270f
      val a = center.y
      val b = a * tan(phi.toRadians())
      top.set(center.x + b, rect.height())
      bottom.set(center.x - b, 0f)
    } else {
      // right bottom
      val phi = 360f - degrees
      val a = center.x
      val b = a * tan(phi.toRadians())
      top.set(rect.width(), center.y + b)
      bottom.set(0f, center.y - b)
    }

    val (selected, other) = when (selectedEdge) {
      CustomChatColorEdge.TOP -> top to bottom
      CustomChatColorEdge.BOTTOM -> bottom to top
    }

    val (selectedPaint, otherPaint) = when (selectedEdge) {
      CustomChatColorEdge.TOP -> topColorPaint to bottomColorPaint
      CustomChatColorEdge.BOTTOM -> bottomColorPaint to topColorPaint
    }

    canvas.apply {
      save()
      translate(rect.top, rect.left)

      drawLine(selected.x, selected.y, other.x, other.y, pipeBorderPaint)
      drawLine(selected.x, selected.y, other.x, other.y, pipePaint)

      drawCircle(other.x, other.y, opaqueThumbRadius + thumbBorder, thumbBorderPaint)
      drawCircle(other.x, other.y, opaqueThumbRadius, backgroundPaint)
      drawCircle(other.x, other.y, opaqueThumbRadius - opaqueThumbPadding, otherPaint)

      drawCircle(selected.x, selected.y, opaqueThumbRadius + thumbBorderSelected, thumbBorderPaintSelected)
      drawCircle(selected.x, selected.y, opaqueThumbRadius, backgroundPaint)
      drawCircle(selected.x, selected.y, opaqueThumbRadius - opaqueThumbPaddingSelected, selectedPaint)
      restore()
    }

    top.offset(rect.top, rect.left)
    bottom.offset(rect.top, rect.left)
  }

  private fun Float.toDegrees(): Float = this * (180f / Math.PI.toFloat())
  private fun Float.toRadians(): Float = this * (Math.PI.toFloat() / 180f)
  private fun PointF.distance(other: PointF): Float = abs(sqrt((this.x - other.x).pow(2) + (this.y - other.y).pow(2)))
  private fun PointF.dotProduct(other: PointF): Float = (this.x * other.x) + (this.y * other.y)
  private fun PointF.determinate(other: PointF): Float = (this.x * other.y) - (this.y * other.x)

  private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

    var activePointerId: Int = MotionEvent.INVALID_POINTER_ID

    override fun onDown(e: MotionEvent): Boolean {
      activePointerId = e.getPointerId(0)

      val touchPoint = PointF(e.getX(activePointerId), e.getY(activePointerId))
      val distanceFromTop = touchPoint.distance(top)

      if (distanceFromTop <= thumbRadius) {
        setSelectedEdge(CustomChatColorEdge.TOP)
        return true
      }

      val distanceFromBottom = touchPoint.distance(bottom)
      if (distanceFromBottom <= thumbRadius) {
        setSelectedEdge(CustomChatColorEdge.BOTTOM)
        return true
      }

      return false
    }

    override fun onScroll(
      e1: MotionEvent,
      e2: MotionEvent,
      distanceX: Float,
      distanceY: Float
    ): Boolean {
      val a = PointF(e2.getX(activePointerId) - center.x, e2.getY(activePointerId) - center.y)
      val b = PointF(center.x, 0f)
      val dot = a.dotProduct(b)
      val det = a.determinate(b)

      val offset = if (selectedEdge == CustomChatColorEdge.BOTTOM) 180f else 0f
      val degrees = (atan2(det, dot).toDegrees() + 360f + offset) % 360f

      setDegreesInternal(degrees)

      return true
    }
  }

  private fun Float.rotate(degrees: Float): Float = (this + degrees + 360f) % 360f

  interface Listener {
    fun onGestureStarted()
    fun onGestureFinished()
    fun onDegreesChanged(degrees: Float)
    fun onSelectedEdgeChanged(edge: CustomChatColorEdge)
  }

  companion object {
    @Dimension(unit = Dimension.DP)
    private const val THUMB_RADIUS = 24

    @Dimension(unit = Dimension.DP)
    private const val THUMB_BORDER = 1

    @Dimension(unit = Dimension.DP)
    private const val THUMB_BORDER_SELECTED = 4

    @Dimension(unit = Dimension.DP)
    private const val OPAQUE_THUMB_RADIUS = 20

    @Dimension(unit = Dimension.DP)
    private const val OPAGUE_THUMB_PADDING = 2

    @Dimension(unit = Dimension.DP)
    private const val OPAGUE_THUMB_PADDING_SELECTED = 1

    @Dimension(unit = Dimension.DP)
    private const val PIPE_WIDTH = 6

    @Dimension(unit = Dimension.DP)
    private const val PIPE_BORDER = 1
  }
}
