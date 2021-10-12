package org.thoughtcrime.securesms.badges.models

import android.view.View
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder

object BadgePreview {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.featured_badge_preview_preference))
    mappingAdapter.registerFactory(SubscriptionModel::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.subscription_flow_badge_preview_preference))
  }

  abstract class BadgeModel<T : BadgeModel<T>> : PreferenceModel<T>() {
    abstract val badge: Badge?
  }

  data class Model(override val badge: Badge?) : BadgeModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.badge?.id == badge?.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && badge == newItem.badge
    }
  }

  data class SubscriptionModel(override val badge: Badge?) : BadgeModel<SubscriptionModel>() {
    override fun areItemsTheSame(newItem: SubscriptionModel): Boolean {
      return newItem.badge?.id == badge?.id
    }

    override fun areContentsTheSame(newItem: SubscriptionModel): Boolean {
      return super.areContentsTheSame(newItem) && badge == newItem.badge
    }
  }

  class ViewHolder<T : BadgeModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {

    private val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)
    private val badge: BadgeImageView = itemView.findViewById(R.id.badge)

    override fun bind(model: T) {
      avatar.setRecipient(Recipient.self())
      avatar.disableQuickContact()
      badge.setBadge(model.badge)
    }
  }
}
