package org.thoughtcrime.securesms.contacts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * ItemDecoration which paints a letter header at the appropriate location above a LetterHeaderItem.
 */
class LetterHeaderDecoration(private val context: Context, private val hideDecoration: () -> Boolean) : RecyclerView.ItemDecoration() {

  private val textBounds = Rect()
  private val bounds = Rect()
  private val padTop = ViewUtil.dpToPx(16)
  private val padStart = context.resources.getDimensionPixelSize(R.dimen.dsl_settings_gutter)

  private var dividerHeight = -1

  private val textPaint = Paint().apply {
    color = ContextCompat.getColor(context, R.color.signal_text_primary)
    isAntiAlias = true
    style = Paint.Style.FILL
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    textAlign = Paint.Align.LEFT
    textSize = ViewUtil.spToPx(16f).toFloat()
  }

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val viewHolder = parent.getChildViewHolder(view)
    if (hideDecoration() || viewHolder !is LetterHeaderItem || viewHolder.getHeaderLetter() == null) {
      outRect.set(0, 0, 0, 0)
      return
    }

    if (dividerHeight == -1) {
      val v = LayoutInflater.from(context).inflate(R.layout.dsl_section_header, parent, false)
      v.measure(0, 0)
      dividerHeight = v.measuredHeight
    }
    outRect.set(0, dividerHeight, 0, 0)
  }

  override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    if (hideDecoration()) {
      return
    }

    val childCount = parent.childCount
    val isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL

    for (i in 0 until childCount) {
      val child = parent.getChildAt(i)
      val holder = parent.getChildViewHolder(child)
      val headerLetter = if (holder is LetterHeaderItem) holder.getHeaderLetter() else null

      if (headerLetter != null) {
        parent.getDecoratedBoundsWithMargins(child, bounds)

        textPaint.getTextBounds(headerLetter, 0, headerLetter.length, textBounds)

        val x = if (isRtl) getLayoutBoundsRTL() else getLayoutBoundsLTR()
        val y = bounds.top + padTop - textBounds.top

        canvas.save()
        canvas.drawText(headerLetter, x.toFloat(), y.toFloat(), textPaint)
        canvas.restore()
      }
    }
  }

  private fun getLayoutBoundsLTR() = bounds.left + padStart

  private fun getLayoutBoundsRTL() = bounds.right - padStart - textBounds.width()

  interface LetterHeaderItem {
    fun getHeaderLetter(): String?
  }
}
