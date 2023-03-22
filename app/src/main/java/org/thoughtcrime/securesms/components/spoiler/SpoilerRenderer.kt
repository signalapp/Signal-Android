package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Canvas
import android.text.Layout
import org.thoughtcrime.securesms.util.LayoutUtil
import kotlin.math.max
import kotlin.math.min

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
    endOffset: Int,
    spoilerDrawables: List<SpoilerDrawable>
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

  class SingleLineSpoilerRenderer : SpoilerRenderer() {
    private val lineTopCache = HashMap<Int, Int>()
    private val lineBottomCache = HashMap<Int, Int>()

    override fun draw(
      canvas: Canvas,
      layout: Layout,
      startLine: Int,
      endLine: Int,
      startOffset: Int,
      endOffset: Int,
      spoilerDrawables: List<SpoilerDrawable>
    ) {
      val lineTop = lineTopCache.get(startLine, layout) { getLineTop(layout, startLine) }
      val lineBottom = lineBottomCache.get(startLine, layout) { getLineBottom(layout, startLine) }
      val left = startOffset.coerceAtMost(endOffset)
      val right = startOffset.coerceAtLeast(endOffset)

      spoilerDrawables[0].setBounds(left, lineTop, right, lineBottom)
      spoilerDrawables[0].draw(canvas)
    }
  }

  class MultiLineSpoilerRenderer : SpoilerRenderer() {
    private val lineTopCache = HashMap<Int, Int>()
    private val lineBottomCache = HashMap<Int, Int>()

    override fun draw(
      canvas: Canvas,
      layout: Layout,
      startLine: Int,
      endLine: Int,
      startOffset: Int,
      endOffset: Int,
      spoilerDrawables: List<SpoilerDrawable>
    ) {
      val paragraphDirection = layout.getParagraphDirection(startLine)

      val lineEndOffset: Float = if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) layout.getLineLeft(startLine) else layout.getLineRight(startLine)
      var lineBottom = lineBottomCache.get(startLine, layout) { getLineBottom(layout, startLine) }
      var lineTop = lineTopCache.get(startLine, layout) { getLineTop(layout, startLine) }
      drawStart(canvas, startOffset, lineTop, lineEndOffset.toInt(), lineBottom, spoilerDrawables)

      if (startLine + 1 < endLine) {
        var left = Int.MAX_VALUE
        var right = -1
        lineTop = Int.MAX_VALUE
        lineBottom = -1
        for (line in startLine + 1 until endLine) {
          left = min(left, layout.getLineLeft(line).toInt())
          right = max(right, layout.getLineRight(line).toInt())

          lineTop = min(lineTop, lineTopCache.get(line, layout) { getLineTop(layout, line) })
          lineBottom = max(lineBottom, lineBottomCache.get(line, layout) { getLineBottom(layout, line) })
        }
        spoilerDrawables[1].setBounds(left, lineTop, right, lineBottom)
        spoilerDrawables[1].draw(canvas)
      }

      val lineStartOffset: Float = if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) layout.getLineRight(startLine) else layout.getLineLeft(startLine)
      lineBottom = lineBottomCache.get(endLine, layout) { getLineBottom(layout, endLine) }
      lineTop = lineTopCache.get(endLine, layout) { getLineTop(layout, endLine) }
      drawEnd(canvas, lineStartOffset.toInt(), lineTop, endOffset, lineBottom, spoilerDrawables)
    }

    private fun drawStart(canvas: Canvas, start: Int, top: Int, end: Int, bottom: Int, spoilerDrawables: List<SpoilerDrawable>) {
      if (start > end) {
        spoilerDrawables[2].setBounds(end, top, start, bottom)
        spoilerDrawables[2].draw(canvas)
      } else {
        spoilerDrawables[0].setBounds(start, top, end, bottom)
        spoilerDrawables[0].draw(canvas)
      }
    }

    private fun drawEnd(canvas: Canvas, start: Int, top: Int, end: Int, bottom: Int, spoilerDrawables: List<SpoilerDrawable>) {
      if (start > end) {
        spoilerDrawables[0].setBounds(end, top, start, bottom)
        spoilerDrawables[0].draw(canvas)
      } else {
        spoilerDrawables[2].setBounds(start, top, end, bottom)
        spoilerDrawables[2].draw(canvas)
      }
    }
  }
}
