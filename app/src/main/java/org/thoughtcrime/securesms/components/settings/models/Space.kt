package org.thoughtcrime.securesms.components.settings.models

import android.view.View
import androidx.annotation.Px
import androidx.core.view.updateLayoutParams
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Adds extra space between elements in a DSL fragment
 */
data class Space(
  @Px val pixels: Int
) {

  companion object {
    fun register(mappingAdapter: MappingAdapter) {
      mappingAdapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.dsl_space_preference))
    }
  }

  class Model(val space: Space) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && newItem.space == space
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {
    override fun bind(model: Model) {
      itemView.updateLayoutParams {
        height = model.space.pixels
      }
    }
  }
}
