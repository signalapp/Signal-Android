package org.thoughtcrime.securesms.components.menu

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Handles the setup and display of actions shown in a context menu.
 */
class ContextMenuList(recyclerView: RecyclerView, onItemClick: () -> Unit) {

  private val mappingAdapter = MappingAdapter().apply {
    registerFactory(DisplayItem::class.java, LayoutFactory({ ItemViewHolder(it, onItemClick) }, R.layout.signal_context_menu_item))
  }

  init {
    recyclerView.apply {
      adapter = mappingAdapter
      layoutManager = LinearLayoutManager(context)
      itemAnimator = null
    }
  }

  fun setItems(items: List<ActionItem>) {
    mappingAdapter.submitList(items.toAdapterItems())
  }

  private fun List<ActionItem>.toAdapterItems(): List<DisplayItem> {
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

  private data class DisplayItem(
    val item: ActionItem,
    val displayType: DisplayType
  ) : MappingModel<DisplayItem> {
    override fun areItemsTheSame(newItem: DisplayItem): Boolean {
      return this == newItem
    }

    override fun areContentsTheSame(newItem: DisplayItem): Boolean {
      return this == newItem
    }
  }

  private enum class DisplayType {
    TOP,
    BOTTOM,
    MIDDLE,
    ONLY
  }

  private class ItemViewHolder(
    itemView: View,
    private val onItemClick: () -> Unit
  ) : MappingViewHolder<DisplayItem>(itemView) {
    val icon: ImageView = itemView.findViewById(R.id.signal_context_menu_item_icon)
    val title: TextView = itemView.findViewById(R.id.signal_context_menu_item_title)

    override fun bind(model: DisplayItem) {
      icon.setImageResource(model.item.iconRes)
      title.text = model.item.title
      itemView.setOnClickListener {
        model.item.action.run()
        onItemClick()
      }

      val tintColor = ContextCompat.getColor(context, model.item.tintRes)
      icon.setColorFilter(tintColor)
      title.setTextColor(tintColor)

      when (model.displayType) {
        DisplayType.TOP -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_top)
        DisplayType.BOTTOM -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_bottom)
        DisplayType.MIDDLE -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_middle)
        DisplayType.ONLY -> itemView.setBackgroundResource(R.drawable.signal_context_menu_item_background_only)
      }
    }
  }
}
