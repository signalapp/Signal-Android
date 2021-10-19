package org.thoughtcrime.securesms.components

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingModel
import org.thoughtcrime.securesms.util.MappingViewHolder

/**
 * A custom context menu that will show next to an anchor view and display several options. Basically a PopupMenu with custom UI and positioning rules.
 *
 * This will prefer showing the menu underneath the anchor, but if there's not enough space in the container, it will show it above the anchor and reverse the
 * order of the menu items. If there's not enough room for either, it'll show it centered above the anchor. If there's not enough room then, it'll center it,
 * chop off the part that doesn't fit, and make the menu scrollable.
 */
class SignalContextMenu private constructor(
  val anchor: View,
  val container: View,
  val items: List<Item>,
  val baseOffsetX: Int = 0,
  val baseOffsetY: Int = 0,
  val onDismiss: Runnable? = null
) : PopupWindow(
  LayoutInflater.from(anchor.context).inflate(R.layout.signal_context_menu, null),
  ViewGroup.LayoutParams.WRAP_CONTENT,
  ViewGroup.LayoutParams.WRAP_CONTENT
) {

  val context: Context = anchor.context

  val mappingAdapter = MappingAdapter().apply {
    registerFactory(DisplayItem::class.java, ItemViewHolderFactory())
  }

  init {
    setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.signal_context_menu_background))

    isFocusable = true

    if (onDismiss != null) {
      setOnDismissListener { onDismiss.run() }
    }

    if (Build.VERSION.SDK_INT >= 21) {
      elevation = 20f
    }

    contentView.findViewById<RecyclerView>(R.id.signal_context_menu_list).apply {
      adapter = mappingAdapter
      layoutManager = LinearLayoutManager(context)
      itemAnimator = null
    }

    mappingAdapter.submitList(items.toAdapterItems())
  }

  private fun show() {
    if (anchor.width == 0 || anchor.height == 0) {
      anchor.post(this::show)
      return
    }

    contentView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    val menuBottomBound = anchor.y + anchor.height + contentView.measuredHeight + baseOffsetY
    val menuTopBound = anchor.y - contentView.measuredHeight - baseOffsetY

    val screenBottomBound = container.height
    val screenTopBound = container.y

    val offsetY: Int

    if (menuBottomBound < screenBottomBound) {
      offsetY = baseOffsetY
    } else if (menuTopBound > screenTopBound) {
      offsetY = -(anchor.height + contentView.measuredHeight + baseOffsetY)
      mappingAdapter.submitList(items.reversed().toAdapterItems())
    } else {
      offsetY = -((anchor.height / 2) + (contentView.measuredHeight / 2) + baseOffsetY)
    }

    showAsDropDown(anchor, baseOffsetX, offsetY)
  }

  private fun List<Item>.toAdapterItems(): List<DisplayItem> {
    return this.mapIndexed { index, item ->
      val displayType: DisplayType = when {
        this.size == 1 -> DisplayType.ONLY
        index == 0 -> DisplayType.TOP
        index == this.size - 1 -> DisplayType.BOTTOM
        else -> DisplayType.MIDDLE
      }

      DisplayItem(item, displayType)
    }
  }

  data class Item(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    val action: Runnable
  )

  private data class DisplayItem(
    val item: Item,
    val displayType: DisplayType
  ) : MappingModel<DisplayItem> {
    override fun areItemsTheSame(newItem: DisplayItem): Boolean {
      return this == newItem
    }

    override fun areContentsTheSame(newItem: DisplayItem): Boolean {
      return this == newItem
    }
  }

  enum class DisplayType {
    TOP, BOTTOM, MIDDLE, ONLY
  }

  private inner class ItemViewHolder(itemView: View) : MappingViewHolder<DisplayItem>(itemView) {
    val icon: ImageView = itemView.findViewById(R.id.signal_context_menu_item_icon)
    val title: TextView = itemView.findViewById(R.id.signal_context_menu_item_title)

    override fun bind(model: DisplayItem) {
      icon.setImageResource(model.item.iconRes)
      title.setText(model.item.titleRes)
      itemView.setOnClickListener {
        model.item.action.run()
        dismiss()
      }

      if (Build.VERSION.SDK_INT >= 21) {
        when (model.displayType) {
          DisplayType.TOP -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_top)
          DisplayType.BOTTOM -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_bottom)
          DisplayType.MIDDLE -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_middle)
          DisplayType.ONLY -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_only)
        }
      }
    }
  }

  private inner class ItemViewHolderFactory : MappingAdapter.Factory<DisplayItem> {
    override fun createViewHolder(parent: ViewGroup): MappingViewHolder<DisplayItem> {
      return ItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.signal_context_menu_item, parent, false))
    }
  }

  /**
   * @param anchor The view to put the pop-up on
   * @param container A parent of [anchor] that represents the acceptable boundaries of the popup
   */
  class Builder(
    val anchor: View,
    val container: View
  ) {

    var onDismiss: Runnable? = null
    var offsetX: Int = 0
    var offsetY: Int = 0

    fun onDismiss(onDismiss: Runnable): Builder {
      this.onDismiss = onDismiss
      return this
    }

    fun offsetX(offsetPx: Int): Builder {
      this.offsetX = offsetPx
      return this
    }

    fun offsetY(offsetPx: Int): Builder {
      this.offsetY = offsetPx
      return this
    }

    fun show(items: List<Item>) {
      SignalContextMenu(
        anchor = anchor,
        container = container,
        items = items,
        baseOffsetX = offsetX,
        baseOffsetY = offsetY,
        onDismiss = onDismiss
      ).show()
    }
  }
}
