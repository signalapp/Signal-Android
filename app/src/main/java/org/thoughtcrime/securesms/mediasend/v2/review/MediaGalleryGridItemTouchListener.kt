/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.review

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.OverScroller
import androidx.recyclerview.widget.RecyclerView

class MediaGalleryGridItemTouchListener : RecyclerView.OnItemTouchListener {
  private var isActive = false
  private var start = RecyclerView.NO_POSITION
  private var end = RecyclerView.NO_POSITION
  private var inTopSpot = false
  private var inBottomSpot = false
  private var scrollDistance = 0
  private var lastX = Float.MIN_VALUE
  private var lastY = Float.MIN_VALUE
  private var lastStart = RecyclerView.NO_POSITION
  private var lastEnd = RecyclerView.NO_POSITION

  private var selectListener: OnDragSelectListener? = null
  private var recyclerView: RecyclerView? = null
  private var scroller: OverScroller? = null

  private var topBoundFrom = 0
  private var topBoundTo = 0
  private var bottomBoundFrom = 0
  private var bottomBoundTo = 0
  private var maxScrollDistance = 16
  private var autoScrollDistance = (Resources.getSystem().displayMetrics.density * 56).toInt()
  private var touchRegionTopOffset = 0
  private var touchRegionBottomOffset = 0
  private var scrollAboveTopRegion = true
  private var scrollBelowTopRegion = true

  init {
    reset()
  }

  private fun reset() {
    isActive = false
    start = RecyclerView.NO_POSITION
    end = RecyclerView.NO_POSITION
    lastStart = RecyclerView.NO_POSITION
    lastEnd = RecyclerView.NO_POSITION
    inTopSpot = false
    inBottomSpot = false
    lastX = Float.MIN_VALUE
    lastY = Float.MIN_VALUE
    stopAutoScroll()
  }

  fun stopAutoScroll() {
    if (scroller != null && !scroller!!.isFinished) {
      recyclerView?.removeCallbacks(scrollRunnable)
      scroller?.abortAnimation()
    }
  }

  fun withSelectListener(selectListener: OnDragSelectListener): MediaGalleryGridItemTouchListener {
    this.selectListener = selectListener
    return this
  }

  private val scrollRunnable = object : Runnable {
    override fun run() {
      if (scroller != null && scroller!!.computeScrollOffset()) {
        scrollBy(scrollDistance)
        recyclerView?.postOnAnimation(this)
      }
    }
  }

  fun startDragSelection(position: Int) {
    isActive = true
    start = position
    end = position
    lastStart = position
    lastEnd = position
    selectListener?.onSelectionStarted(position)
  }

  override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
    if (!isActive || rv.adapter?.itemCount == 0) return false

    when (e.action) {
      MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> reset()
    }

    recyclerView = rv
    val height = rv.height
    topBoundFrom = 0 + touchRegionTopOffset
    topBoundTo = topBoundFrom + autoScrollDistance
    bottomBoundFrom = height + touchRegionBottomOffset - autoScrollDistance
    bottomBoundTo = height + touchRegionBottomOffset
    return true
  }

  fun setIsActive(isActive: Boolean) {
    this.isActive = isActive
  }

  override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
    if (!isActive) return

    when (e.action) {
      MotionEvent.ACTION_DOWN -> updateSelectedRange(rv, e)
      MotionEvent.ACTION_MOVE -> {
        if (!inTopSpot && !inBottomSpot) updateSelectedRange(rv, e)
        processAutoScroll(e)
      }
      MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> reset()
    }
  }

  private fun updateSelectedRange(rv: RecyclerView, e: MotionEvent) {
    updateSelectedRange(rv, e.x, e.y)
  }

  private fun processAutoScroll(event: MotionEvent) {
    val y = event.y.toInt()
    val scrollSpeedFactor: Float
    when {
      y in topBoundFrom..topBoundTo -> {
        lastX = event.x
        lastY = event.y
        scrollSpeedFactor = (topBoundTo - topBoundFrom - (y - topBoundFrom)).toFloat() / (topBoundTo - topBoundFrom)
        scrollDistance = (maxScrollDistance * scrollSpeedFactor * -1f).toInt()
        if (!inTopSpot) {
          inTopSpot = true
          startAutoScroll()
        }
      }
      scrollAboveTopRegion && y < topBoundFrom -> {
        lastX = event.x
        lastY = event.y
        scrollDistance = -maxScrollDistance
        if (!inTopSpot) {
          inTopSpot = true
          startAutoScroll()
        }
      }
      y in bottomBoundFrom..bottomBoundTo -> {
        lastX = event.x
        lastY = event.y
        scrollSpeedFactor = (y - bottomBoundFrom).toFloat() / (bottomBoundTo - bottomBoundFrom)
        scrollDistance = (maxScrollDistance * scrollSpeedFactor).toInt()
        if (!inBottomSpot) {
          inBottomSpot = true
          startAutoScroll()
        }
      }
      scrollBelowTopRegion && y > bottomBoundTo -> {
        lastX = event.x
        lastY = event.y
        scrollDistance = maxScrollDistance
        if (!inTopSpot) {
          inTopSpot = true
          startAutoScroll()
        }
      }
      else -> {
        inBottomSpot = false
        inTopSpot = false
        lastX = Float.MIN_VALUE
        lastY = Float.MIN_VALUE
        stopAutoScroll()
      }
    }
  }

  private fun updateSelectedRange(rv: RecyclerView, x: Float, y: Float) {
    val child = rv.findChildViewUnder(x, y)
    if (child != null) {
      val position = rv.getChildAdapterPosition(child)
      if (position != RecyclerView.NO_POSITION && end != position) {
        end = position
        notifySelectRangeChange()
      }
    }
  }

  fun startAutoScroll() {
    val context = recyclerView?.context ?: return
    initScroller(context)
    if (scroller?.isFinished == true) {
      recyclerView?.removeCallbacks(scrollRunnable)
      scroller?.startScroll(0, scroller!!.currY, 0, 5000, 100000)
      recyclerView!!.postOnAnimation(scrollRunnable)
    }
  }

  private fun notifySelectRangeChange() {
    if (selectListener == null || start == RecyclerView.NO_POSITION || end == RecyclerView.NO_POSITION) return

    val newStart = minOf(start, end)
    val newEnd = maxOf(start, end)
    when {
      lastStart == RecyclerView.NO_POSITION || lastEnd == RecyclerView.NO_POSITION -> {
        if (newEnd - newStart == 1) selectListener?.onSelectChange(newStart, newStart, true)
        else selectListener?.onSelectChange(newStart, newEnd, true)
      }
      newStart > lastStart -> selectListener?.onSelectChange(lastStart, newStart - 1, false)
      newStart < lastStart -> selectListener?.onSelectChange(newStart, lastStart - 1, true)
    }

    when {
      newEnd > lastEnd -> selectListener?.onSelectChange(lastEnd + 1, newEnd, true)
      newEnd < lastEnd -> selectListener?.onSelectChange(newEnd + 1, lastEnd, false)
    }

    lastStart = newStart
    lastEnd = newEnd
  }

  private fun initScroller(context: Context) {
    if (scroller == null) scroller = OverScroller(context, LinearInterpolator())
  }

  override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) { }

  private fun scrollBy(distance: Int) {
    val scrollDist = if (distance > 0) minOf(distance, maxScrollDistance) else maxOf(distance, -maxScrollDistance)
    recyclerView?.scrollBy(0, scrollDist)
    if (lastX != Float.MIN_VALUE && lastY != Float.MIN_VALUE) {
      updateSelectedRange(recyclerView!!, lastX, lastY)
    }
  }

  interface OnDragSelectListener {
    fun onSelectionStarted(start: Int)
    fun onSelectChange(start: Int, end: Int, shouldSelect: Boolean)
  }
}
