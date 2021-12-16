package org.thoughtcrime.securesms.avatar

import android.view.View
import android.widget.ImageView
import com.airbnb.lottie.SimpleColorFilter
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

typealias OnAvatarColorClickListener = (Avatars.ColorPair) -> Unit

/**
 * Selectable color item for choosing colors when editing a Text or Vector avatar.
 */
data class AvatarColorItem(
  val colors: Avatars.ColorPair,
  val selected: Boolean
) {

  companion object {
    fun registerViewHolder(adapter: MappingAdapter, onAvatarColorClickListener: OnAvatarColorClickListener) {
      adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onAvatarColorClickListener) }, R.layout.avatar_color_item))
    }
  }

  class Model(val colorItem: AvatarColorItem) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = newItem.colorItem.colors == colorItem.colors
    override fun areContentsTheSame(newItem: Model): Boolean = newItem.colorItem == colorItem
  }

  private class ViewHolder(itemView: View, private val onAvatarColorClickListener: OnAvatarColorClickListener) : MappingViewHolder<Model>(itemView) {

    private val imageView: ImageView = findViewById(R.id.avatar_color_item)

    override fun bind(model: Model) {
      itemView.setOnClickListener { onAvatarColorClickListener(model.colorItem.colors) }
      imageView.background.colorFilter = SimpleColorFilter(model.colorItem.colors.backgroundColor)
      imageView.isSelected = model.colorItem.selected
    }
  }
}
