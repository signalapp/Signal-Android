/*
MIT License

Copyright (c) 2020 Tiago Ornelas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package org.thoughtcrime.securesms.components.segmentedprogressbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.viewpager.widget.ViewPager
import org.thoughtcrime.securesms.R
import java.util.concurrent.TimeUnit

/**
 * Created by Tiago Ornelas on 18/04/2020.
 * Represents a segmented progress bar on which, the progress is set by segments
 * @see Segment
 * And the progress of each segment is animated based on a set speed
 */
class SegmentedProgressBar : View, ViewPager.OnPageChangeListener, View.OnTouchListener {

  companion object {
    /**
     * It is common now for devices to run at 60FPS
     */
    val MILLIS_PER_FRAME = TimeUnit.MILLISECONDS.toMillis(17)
  }

  private val path = Path()
  private val corners = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

  /**
   * Number of total segments to draw
   */
  var segmentCount: Int = resources.getInteger(R.integer.segmentedprogressbar_default_segments_count)
    set(value) {
      field = value
      this.initSegments()
    }

  /**
   * Mapping of segment index -> duration in millis. Negative durations
   * ARE valid but they'll result in a call to SegmentedProgressBarListener#onRequestSegmentProgressPercentage
   * which should return the current % position for the currently playing item. This helps
   * to avoid synchronizing the seek bar to playback.
   */
  var segmentDurations: Map<Int, Long> = mapOf()
    set(value) {
      field = value
      this.initSegments()
    }

  var margin: Int = resources.getDimensionPixelSize(R.dimen.segmentedprogressbar_default_segment_margin)
    private set
  var radius: Int = resources.getDimensionPixelSize(R.dimen.segmentedprogressbar_default_corner_radius)
    private set
  var segmentStrokeWidth: Int =
    resources.getDimensionPixelSize(R.dimen.segmentedprogressbar_default_segment_stroke_width)
    private set

  var segmentBackgroundColor: Int = Color.WHITE
    private set
  var segmentSelectedBackgroundColor: Int =
    context.getThemeColor(R.attr.colorAccent)
    private set
  var segmentStrokeColor: Int = Color.BLACK
    private set
  var segmentSelectedStrokeColor: Int = Color.BLACK
    private set

  var timePerSegmentMs: Long =
    resources.getInteger(R.integer.segmentedprogressbar_default_time_per_segment_ms).toLong()
    private set

  private var segments = mutableListOf<Segment>()
  private val selectedSegment: Segment?
    get() = segments.firstOrNull { it.animationState == Segment.AnimationState.ANIMATING }
  private val selectedSegmentIndex: Int
    get() = segments.indexOf(this.selectedSegment)

  // Drawing
  val strokeApplicable: Boolean
    get() = segmentStrokeWidth * 4 <= measuredHeight

  val segmentWidth: Float
    get() = (measuredWidth - margin * (segmentCount - 1)).toFloat() / segmentCount

  var viewPager: ViewPager? = null
    @SuppressLint("ClickableViewAccessibility")
    set(value) {
      field = value
      if (value == null) {
        viewPager?.removeOnPageChangeListener(this)
        viewPager?.setOnTouchListener(null)
      } else {
        viewPager?.addOnPageChangeListener(this)
        viewPager?.setOnTouchListener(this)
      }
    }

  /**
   * Sets callbacks for progress bar state changes
   * @see SegmentedProgressBarListener
   */
  var listener: SegmentedProgressBarListener? = null

  private var lastFrameTimeMillis: Long = 0L

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

    val typedArray =
      context.theme.obtainStyledAttributes(attrs, R.styleable.SegmentedProgressBar, 0, 0)

    segmentCount =
      typedArray.getInt(R.styleable.SegmentedProgressBar_totalSegments, segmentCount)

    margin =
      typedArray.getDimensionPixelSize(
        R.styleable.SegmentedProgressBar_segmentMargins,
        margin
      )
    radius =
      typedArray.getDimensionPixelSize(
        R.styleable.SegmentedProgressBar_segmentCornerRadius,
        radius
      )
    segmentStrokeWidth =
      typedArray.getDimensionPixelSize(
        R.styleable.SegmentedProgressBar_segmentStrokeWidth,
        segmentStrokeWidth
      )

    segmentBackgroundColor =
      typedArray.getColor(
        R.styleable.SegmentedProgressBar_segmentBackgroundColor,
        segmentBackgroundColor
      )
    segmentSelectedBackgroundColor =
      typedArray.getColor(
        R.styleable.SegmentedProgressBar_segmentSelectedBackgroundColor,
        segmentSelectedBackgroundColor
      )

    segmentStrokeColor =
      typedArray.getColor(
        R.styleable.SegmentedProgressBar_segmentStrokeColor,
        segmentStrokeColor
      )
    segmentSelectedStrokeColor =
      typedArray.getColor(
        R.styleable.SegmentedProgressBar_segmentSelectedStrokeColor,
        segmentSelectedStrokeColor
      )

    timePerSegmentMs =
      typedArray.getInt(
        R.styleable.SegmentedProgressBar_timePerSegment,
        timePerSegmentMs.toInt()
      ).toLong()

    typedArray.recycle()
  }

  constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  )

  init {
    setLayerType(LAYER_TYPE_SOFTWARE, null)
  }

  override fun onDraw(canvas: Canvas?) {
    super.onDraw(canvas)

    segments.forEachIndexed { index, segment ->
      val drawingComponents = getDrawingComponents(segment, index)

      when (index) {
        0 -> {
          corners.indices.forEach { corners[it] = 0f }
          corners[0] = radius.toFloat()
          corners[1] = radius.toFloat()
          corners[6] = radius.toFloat()
          corners[7] = radius.toFloat()
        }

        segments.lastIndex -> {
          corners.indices.forEach { corners[it] = 0f }
          corners[2] = radius.toFloat()
          corners[3] = radius.toFloat()
          corners[4] = radius.toFloat()
          corners[5] = radius.toFloat()
        }
      }

      drawingComponents.first.forEachIndexed { drawingIndex, rectangle ->
        when (index) {
          0, segments.lastIndex -> {
            path.reset()
            path.addRoundRect(rectangle, corners, Path.Direction.CW)
            canvas?.drawPath(path, drawingComponents.second[drawingIndex])
          }

          else -> canvas?.drawRect(
            rectangle,
            drawingComponents.second[drawingIndex]
          )
        }
      }
    }

    onFrame(System.currentTimeMillis())
  }

  /**
   * Start/Resume progress animation
   */
  fun start() {
    pause()
    val segment = selectedSegment
    if (segment == null) {
      next()
    } else {
      isPaused = false
      invalidate()
    }
  }

  /**
   * Pauses the animation process
   */
  fun pause() {
    isPaused = true
    lastFrameTimeMillis = 0L
  }

  /**
   * Resets the whole animation state and selected segments
   * !Doesn't restart it!
   * To restart, call the start() method
   */
  fun reset() {
    this.segments.map { it.animationState = Segment.AnimationState.IDLE }
    this.invalidate()
  }

  /**
   * Starts animation for the following segment
   */
  fun next() {
    loadSegment(offset = 1, userAction = true)
  }

  /**
   * Starts animation for the previous segment
   */
  fun previous() {
    loadSegment(offset = -1, userAction = true)
  }

  /**
   * Restarts animation for the current segment
   */
  fun restartSegment() {
    loadSegment(offset = 0, userAction = true)
  }

  /**
   * Skips a number of segments
   * @param offset number o segments fo skip
   */
  fun skip(offset: Int) {
    loadSegment(offset = offset, userAction = true)
  }

  /**
   * Sets current segment to the
   * @param position index
   */
  fun setPosition(position: Int) {
    loadSegment(offset = position - this.selectedSegmentIndex, userAction = true)
  }

  // Private methods
  private fun loadSegment(offset: Int, userAction: Boolean) {
    val oldSegmentIndex = this.segments.indexOf(this.selectedSegment)

    val nextSegmentIndex = oldSegmentIndex + offset

    // Index out of bounds, ignore operation
    if (userAction && nextSegmentIndex !in 0 until segmentCount) {
      if (nextSegmentIndex >= segmentCount) {
        this.listener?.onFinished()
      } else {
        loadSegment(offset = 0, userAction = false)
      }
      return
    }

    segments.mapIndexed { index, segment ->
      if (offset > 0) {
        if (index < nextSegmentIndex) {
          segment.animationState = Segment.AnimationState.ANIMATED
        }
      } else if (offset < 0) {
        if (index > nextSegmentIndex - 1) {
          segment.animationState = Segment.AnimationState.IDLE
        }
      } else {
        if (index == nextSegmentIndex) segment.animationState = Segment.AnimationState.IDLE
      }
    }

    val nextSegment = this.segments.getOrNull(nextSegmentIndex)

    // Handle next segment transition/ending
    if (nextSegment != null) {
      pause()
      nextSegment.animationState = Segment.AnimationState.ANIMATING
      isPaused = false
      invalidate()
      this.listener?.onPage(oldSegmentIndex, this.selectedSegmentIndex)
      viewPager?.currentItem = this.selectedSegmentIndex
    } else {
      pause()
      this.listener?.onFinished()
    }
  }

  private fun getSegmentProgressPercentage(segment: Segment, timeSinceLastFrameMillis: Long): Float {
    return if (segment.animationDurationMillis > 0) {
      segment.animationProgressPercentage + timeSinceLastFrameMillis.toFloat() / segment.animationDurationMillis
    } else {
      listener?.onRequestSegmentProgressPercentage() ?: 0f
    }
  }

  private fun initSegments() {
    this.segments.clear()
    segments.addAll(
      List(segmentCount) {
        val duration = segmentDurations[it] ?: timePerSegmentMs
        Segment(duration)
      }
    )
    this.invalidate()
    reset()
  }

  private var isPaused = true

  private fun onFrame(frameTimeMillis: Long) {
    if (isPaused) {
      return
    }

    val lastFrameTimeMillis = this.lastFrameTimeMillis

    this.lastFrameTimeMillis = frameTimeMillis

    val selectedSegment = this.selectedSegment
    if (selectedSegment == null) {
      loadSegment(offset = 1, userAction = false)
    } else if (lastFrameTimeMillis > 0L) {
      val segmentProgressPercentage = getSegmentProgressPercentage(selectedSegment, frameTimeMillis - lastFrameTimeMillis)
      selectedSegment.animationProgressPercentage = segmentProgressPercentage
      if (selectedSegment.animationProgressPercentage >= 1f) {
        loadSegment(offset = 1, userAction = false)
      } else {
        this.invalidate()
      }
    } else {
      this.invalidate()
    }
  }

  override fun onPageScrollStateChanged(state: Int) {}

  override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

  override fun onPageSelected(position: Int) {
    this.setPosition(position)
  }

  override fun onTouch(p0: View?, p1: MotionEvent?): Boolean {
    when (p1?.action) {
      MotionEvent.ACTION_DOWN -> pause()
      MotionEvent.ACTION_UP -> start()
    }
    return false
  }
}
