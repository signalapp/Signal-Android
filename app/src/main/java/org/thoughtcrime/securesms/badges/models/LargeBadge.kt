package org.thoughtcrime.securesms.badges.models

import android.view.View
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

data class LargeBadge(
  val badge: Badge
) {

  class Model(val largeBadge: LargeBadge, val shortName: String, val maxLines: Int) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.largeBadge.badge.id == largeBadge.badge.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return newItem.largeBadge == largeBadge && newItem.shortName == shortName && newItem.maxLines == maxLines
    }
  }

  class EmptyModel : MappingModel<EmptyModel> {
    override fun areItemsTheSame(newItem: EmptyModel): Boolean = true
    override fun areContentsTheSame(newItem: EmptyModel): Boolean = true
  }

  class EmptyViewHolder(itemView: View) : MappingViewHolder<EmptyModel>(itemView) {
    override fun bind(model: EmptyModel) {
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val badge: BadgeImageView = itemView.findViewById(R.id.badge)
    private val name: TextView = itemView.findViewById(R.id.name)
    private val description: TextView = itemView.findViewById(R.id.description)

    override fun bind(model: Model) {
      badge.setBadge(model.largeBadge.badge)

      name.text = context.getString(R.string.ViewBadgeBottomSheetDialogFragment__s_supports_signal, model.shortName)
      description.text = if (model.largeBadge.badge.isSubscription()) {
        context.getString(R.string.ViewBadgeBottomSheetDialogFragment__s_supports_signal_with_a_monthly, model.shortName)
      } else {
        context.getString(R.string.ViewBadgeBottomSheetDialogFragment__s_supports_signal_with_a_donation, model.shortName)
      }

      description.setLines(model.maxLines)
      description.maxLines = model.maxLines
      description.minLines = model.maxLines
    }
  }

  companion object {
    fun register(mappingAdapter: MappingAdapter) {
      mappingAdapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.view_badge_bottom_sheet_dialog_fragment_page))
      mappingAdapter.registerFactory(EmptyModel::class.java, LayoutFactory({ EmptyViewHolder(it) }, R.layout.view_badge_bottom_sheet_dialog_fragment_page))
    }
  }
}
