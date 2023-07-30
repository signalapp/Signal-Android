package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import androidx.annotation.ColorInt
import androidx.annotation.Px
import org.thoughtcrime.securesms.util.LayoutUtil

/**
 * Handles drawing the spoiler sparkles for a TextView.
 */
class SpoilerRenderer(
  private val spoilerDrawable: SpoilerDrawable,
  private val renderForComposing: Boolean,
  @Px private val padding: Int,
  @ColorInt composeBackgroundColor: Int
) {

  private val lineTopCache = HashMap<Int, Int>()
  private val lineBottomCache = HashMap<Int, Int>()
  private val paint = Paint().apply { color = composeBackgroundColor }

  fun draw(
    canvas: Canvas,
    layout: Layout,
    startLine: Int,
    endLine: Int,
    startOffset: Int,
    endOffset: Int
  ) {
    if (startLine == endLine) {
      val lineTop = lineTopCache.get(startLine, layout) { getLineTop(layout, startLine) }
      val lineBottom = lineBottomCache.get(startLine, layout) { getLineBottom(layout, startLine) }
      val left = startOffset.coerceAtMost(endOffset)
      val right = startOffset.coerceAtLeast(endOffset)

      if (renderForComposing) {
        canvas.drawComposeBackground(left, lineTop, right, lineBottom)
      } else {
        spoilerDrawable.setBounds(left, lineTop, right, lineBottom)
        spoilerDrawable.draw(canvas)
      }

      return
    }

    val paragraphDirection = layout.getParagraphDirection(startLine)

    val lineEndOffset: Float = if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) layout.getLineLeft(startLine) else layout.getLineRight(startLine)
    var lineBottom = lineBottomCache.get(startLine, layout) { getLineBottom(layout, startLine) }
    var lineTop = lineTopCache.get(startLine, layout) { getLineTop(layout, startLine) }
    drawPartialLine(canvas, startOffset, lineTop, lineEndOffset.toInt(), lineBottom)

    for (line in startLine + 1 until endLine) {
      val left: Int = layout.getLineLeft(line).toInt()
      val right: Int = layout.getLineRight(line).toInt()

      lineTop = getLineTop(layout, line)
      lineBottom = getLineBottom(layout, line)

      if (renderForComposing) {
        canvas.drawComposeBackground(left, lineTop, right, lineBottom)
      } else {
        spoilerDrawable.setBounds(left, lineTop, right, lineBottom)
        spoilerDrawable.draw(canvas)
      }
    }

    val lineStartOffset: Float = if (paragraphDirection == Layout.DIR_RIGHT_TO_LEFT) layout.getLineRight(startLine) else layout.getLineLeft(startLine)
    lineBottom = lineBottomCache.get(endLine, layout) { getLineBottom(layout, endLine) }
    lineTop = lineTopCache.get(endLine, layout) { getLineTop(layout, endLine) }
    drawPartialLine(canvas, lineStartOffset.toInt(), lineTop, endOffset, lineBottom)
  }

  private fun drawPartialLine(canvas: Canvas, start: Int, top: Int, end: Int, bottom: Int) {
    if (renderForComposing) {
      canvas.drawComposeBackground(start, top, end, bottom)
      return
    }

    if (start > end) {
      spoilerDrawable.setBounds(end, top, start, bottom)
    } else {
      spoilerDrawable.setBounds(start, top, end, bottom)
    }
    spoilerDrawable.draw(canvas)
  }

  private fun getLineTop(layout: Layout, line: Int): Int {
    return LayoutUtil.getLineTopWithoutPadding(layout, line)
  }

  private fun getLineBottom(layout: Layout, line: Int): Int {
    return LayoutUtil.getLineBottomWithoutPadding(layout, line)
  }

  private inline fun MutableMap<Int, Int>.get(line: Int, layout: Layout, default: () -> Int): Int {
    return getOrPut(line * 31 + layout.hashCode() * 31, default)
  }

  private fun Canvas.drawComposeBackground(start: Int, top: Int, end: Int, bottom: Int) {
    drawRoundRect(
      start.toFloat() - padding,
      top.toFloat() - padding,
      end.toFloat() + padding,
      bottom.toFloat(),
      padding.toFloat(),
      padding.toFloat(),
      paint
    )
  }
}
