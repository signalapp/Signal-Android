package org.thoughtcrime.securesms.conversation.quotes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.marginLeft
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Serves as the separator between the original message and the messages that quote it in [MessageQuotesBottomSheet]
 */
class MessageQuoteHeaderDecoration(context: Context) : RecyclerView.ItemDecoration() {

  private val dividerMargin = ViewUtil.dpToPx(context, 32)
  private val dividerHeight = ViewUtil.dpToPx(context, 2)
  private val dividerRect = Rect()
  private val dividerPaint: Paint = Paint().apply {
    style = Paint.Style.FILL
    color = context.resources.getColor(R.color.signal_colorSurfaceVariant)
  }

  private var cachedHeader: View? = null
  private val headerMargin = ViewUtil.dpToPx(24)

  override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    val lastItem: View = parent.children.firstOrNull { child ->
      parent.getChildAdapterPosition(child) == state.itemCount - 1
    } ?: return

    dividerRect.apply {
      left = parent.left
      top = lastItem.bottom + dividerMargin
      right = parent.right
      bottom = lastItem.bottom + dividerMargin + dividerHeight
    }

    canvas.drawRect(dividerRect, dividerPaint)

    val header = getHeader(parent)

    canvas.save()
    canvas.translate((parent.left + header.marginLeft).toFloat(), (dividerRect.bottom + dividerMargin).toFloat())
    header.draw(canvas)
    canvas.restore()
  }

  private fun getHeader(parent: RecyclerView): View {
    cachedHeader?.let {
      return it
    }

    val header: View = LayoutInflater.from(parent.context).inflate(R.layout.message_quote_header_decoration, parent, false)

    val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

    val childWidth = ViewGroup.getChildMeasureSpec(
      widthSpec,
      parent.paddingLeft + parent.paddingRight,
      header.layoutParams.width
    )

    val childHeight = ViewGroup.getChildMeasureSpec(
      heightSpec,
      parent.paddingTop + parent.paddingBottom,
      header.layoutParams.height
    )

    header.measure(childWidth, childHeight)
    header.layout(header.marginLeft, 0, header.measuredWidth, header.measuredHeight)

    cachedHeader = header

    return header
  }

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val currentPosition = parent.getChildAdapterPosition(view)
    val lastPosition = state.itemCount - 1

    if (currentPosition == lastPosition) {
      outRect.bottom = ViewUtil.dpToPx(view.context, 110)
    }
  }
}
