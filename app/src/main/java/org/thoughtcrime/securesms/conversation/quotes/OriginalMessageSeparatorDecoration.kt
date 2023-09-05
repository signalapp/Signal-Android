package org.thoughtcrime.securesms.conversation.quotes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.marginLeft
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Serves as the separator between the original message and other messages. Used in [MessageQuotesBottomSheet] and [EditMessageHistoryDialog]
 */
class OriginalMessageSeparatorDecoration(
  context: Context,
  private val titleRes: Int,
  private val getOriginalMessagePosition: (RecyclerView.State) -> Int = { it.itemCount - 1 }
) : RecyclerView.ItemDecoration() {

  private val dividerMargin = ViewUtil.dpToPx(context, 32)
  private val dividerHeight = ViewUtil.dpToPx(context, 2)
  private val dividerRect = Rect()
  private val dividerPaint: Paint = Paint().apply {
    style = Paint.Style.FILL
    color = ContextCompat.getColor(context, R.color.signal_colorSurfaceVariant)
  }

  private var cachedHeader: View? = null

  override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    val originalItem: View = parent.children.firstOrNull { child ->
      parent.getChildAdapterPosition(child) == getOriginalMessagePosition(state)
    } ?: return

    dividerRect.apply {
      left = parent.left
      top = originalItem.bottom + dividerMargin
      right = parent.right
      bottom = originalItem.bottom + dividerMargin + dividerHeight
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

    val header: View = LayoutInflater.from(parent.context).inflate(R.layout.original_message_separator_decoration, parent, false)
    val titleView: TextView = header.findViewById(R.id.separator_title)
    titleView.setText(titleRes)

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
    val originalMessagePosition = getOriginalMessagePosition(state)

    if (currentPosition == originalMessagePosition) {
      outRect.bottom = ViewUtil.dpToPx(view.context, 110)
    }
  }
}
