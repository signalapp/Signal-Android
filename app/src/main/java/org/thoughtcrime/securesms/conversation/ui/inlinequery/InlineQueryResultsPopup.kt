package org.thoughtcrime.securesms.conversation.ui.inlinequery

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.AnyMappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class InlineQueryResultsPopup(
  val anchor: View,
  val container: ViewGroup,
  results: List<AnyMappingModel>,
  val baseOffsetX: Int = 0,
  val baseOffsetY: Int = 0,
  var callback: Callback?
) : PopupWindow(
  LayoutInflater.from(anchor.context).inflate(R.layout.inline_query_results_popup, null),
  ViewGroup.LayoutParams.WRAP_CONTENT,
  ViewGroup.LayoutParams.WRAP_CONTENT,
  false
) {

  private val context: Context = anchor.context

  private val list: RecyclerView = contentView.findViewById(R.id.inline_query_results_list)
  private val adapter: MappingAdapter

  init {
    contentView.outlineProvider = ViewOutlineProvider.BACKGROUND
    contentView.clipToOutline = true

    inputMethodMode = INPUT_METHOD_NOT_NEEDED

    setOnDismissListener {
      callback?.onDismiss()
      callback = null
    }

    elevation = 20f

    adapter = InlineQueryAdapter { m -> callback?.onSelection(m) }
    list.adapter = adapter
    list.itemAnimator = null

    setResults(results)
  }

  fun setResults(results: List<AnyMappingModel>) {
    adapter.submitList(results) { list.scrollToPosition(0) }
  }

  fun show(): InlineQueryResultsPopup {
    if (anchor.width == 0 || anchor.height == 0) {
      anchor.post(this::show)
      return this
    }

    val (offsetX, offsetY) = calculateOffsets()

    showAsDropDown(anchor, offsetX, offsetY)

    return this
  }

  fun updateWithAnchor() {
    val (offsetX, offsetY) = calculateOffsets()
    update(anchor, offsetX, offsetY, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
  }

  private fun calculateOffsets(): Pair<Int, Int> {
    contentView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    val anchorRect = Rect(anchor.left, anchor.top, anchor.right, anchor.bottom).also {
      if (anchor.parent != container) {
        container.offsetDescendantRectToMyCoords(anchor, it)
      }
    }

    val menuBottomBound = anchorRect.bottom + contentView.measuredHeight + baseOffsetY
    val menuTopBound = anchorRect.top - contentView.measuredHeight - baseOffsetY

    val screenBottomBound = container.height
    val screenTopBound = container.y

    val offsetY: Int = when {
      menuTopBound > screenTopBound -> -(anchorRect.height() + contentView.measuredHeight + baseOffsetY)
      menuBottomBound < screenBottomBound -> baseOffsetY
      menuTopBound > screenTopBound -> -(anchorRect.height() + contentView.measuredHeight + baseOffsetY)
      else -> -((anchorRect.height() / 2) + (contentView.measuredHeight / 2) + baseOffsetY)
    }

    val offsetX: Int = if (ViewUtil.isLtr(context)) {
      baseOffsetX
    } else {
      -(baseOffsetX + contentView.measuredWidth)
    }

    return offsetX to offsetY
  }

  interface Callback {
    fun onSelection(model: AnyMappingModel)
    fun onDismiss()
  }
}
