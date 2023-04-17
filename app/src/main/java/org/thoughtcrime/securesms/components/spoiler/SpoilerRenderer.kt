package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Canvas
import android.text.Layout
import org.thoughtcrime.securesms.util.LayoutUtil

/**
 * Handles drawing the spoiler sparkles for a TextView.
 */
abstract class SpoilerRenderer {

  abstract fun draw(
    canvas: Canvas,
    layout: Layout,
    startLine: Int,
    endLine: Int,
    startOffset: Int,
    endOffset: Int
  )

  protected fun getLineTop(layout: Layout, line: Int): Int {
    return LayoutUtil.getLineTopWithoutPadding(layout, line)
  }

  protected fun getLineBottom(layout: Layout, line: Int): Int {
    return LayoutUtil.getLineBottomWithoutPadding(layout, line)
  }

  protected inline fun MutableMap<Int, Int>.get(line: Int, layout: Layout, default: () -> Int): Int {
    return getOrPut(line * 31 + layout.hashCode() * 31, default)
  }

  class SingleLineSpoilerRenderer(private val spoilerDrawable: SpoilerDrawable) : SpoilerRenderer() {
    private val lineTopCache = HashMap<Int, Int>()
    private val lineBottomCache = HashMap<Int, Int>()

    override fun draw(
      canvas: Canvas,
      layout: Layout,
      startLine: Int,
      endLine: Int,
      startOffset: Int,
      endOffset: Int
    ) {
      val lineTop = lineTopCache.get(startLine, layout) { getLineTop(layout, startLine) }
      val lineBottom = lineBottomCache.get(startLine, layout) { getLineBottom(layout, startLine) }
      val left = startOffset.coerceAtMost(endOffset)
      val right = startOffset.coerceAtLeast(endOffset)

      spoilerDrawable.setBounds(left, lineTop, right, lineBottom)
      spoilerDrawable.draw(canvas)
    }
  }

  class MultiLineSpoilerRenderer(private val spoilerDrawable: SpoilerDrawable) : SpoilerRenderer() {
    private val lineTopCache = HashMap<Int, Int>()
    private val lineBottomCache = HashMap<Int, Int>()

    override fun draw(
      canvas: Canvas,
      layout: Layout,
      startLine: Int,
      endLine: Int,
      startOffset: Int,
      endOffset: Int
    ) {
      val paragraphDirection = layout.getParagraphDirection(startLine)

      val lineEndOffset: Float = if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) layout.getLineLeft(startLine) else layout.getLineRight(startLine)
      var lineBottom = lineBottomCache.get(startLine, layout) { getLineBottom(layout, startLine) }
      var lineTop = lineTopCache.get(startLine, layout) { getLineTop(layout, startLine) }
      drawStart(canvas, startOffset, lineTop, lineEndOffset.toInt(), lineBottom)

      for (line in startLine + 1 until endLine) {
        val left: Int = layout.getLineLeft(line).toInt()
        val right: Int = layout.getLineRight(line).toInt()

        lineTop = getLineTop(layout, line)
        lineBottom = getLineBottom(layout, line)

        spoilerDrawable.setBounds(left, lineTop, right, lineBottom)
        spoilerDrawable.draw(canvas)
      }

      val lineStartOffset: Float = if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) layout.getLineRight(startLine) else layout.getLineLeft(startLine)
      lineBottom = lineBottomCache.get(endLine, layout) { getLineBottom(layout, endLine) }
      lineTop = lineTopCache.get(endLine, layout) { getLineTop(layout, endLine) }
      drawEnd(canvas, lineStartOffset.toInt(), lineTop, endOffset, lineBottom)
    }

    private fun drawStart(canvas: Canvas, start: Int, top: Int, end: Int, bottom: Int) {
      if (start > end) {
        spoilerDrawable.setBounds(end, top, start, bottom)
      } else {
        spoilerDrawable.setBounds(start, top, end, bottom)
      }
      spoilerDrawable.draw(canvas)
    }

    private fun drawEnd(canvas: Canvas, start: Int, top: Int, end: Int, bottom: Int) {
      if (start > end) {
        spoilerDrawable.setBounds(end, top, start, bottom)
      } else {
        spoilerDrawable.setBounds(start, top, end, bottom)
      }
      spoilerDrawable.draw(canvas)
    }
  }
}
