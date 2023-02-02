package org.thoughtcrime.securesms

import android.view.View
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

class StaticMappingViewHolder<T : MappingModel<T>>(itemView: View, onClickListener: () -> Unit) : MappingViewHolder<T>(itemView) {
  init {
    itemView.setOnClickListener { onClickListener() }
  }

  override fun bind(model: T) = Unit
}
