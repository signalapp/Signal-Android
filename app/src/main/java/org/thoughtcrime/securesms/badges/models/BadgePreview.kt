package org.thoughtcrime.securesms.badges.models

import android.view.View
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * "Hero Image" for displaying an Avatar and badge. Allows the user to see what their profile will look like with a particular badge applied.
 */
object BadgePreview {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(BadgeModel.FeaturedModel::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.featured_badge_preview_preference))
    mappingAdapter.registerFactory(BadgeModel.SubscriptionModel::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.subscription_flow_badge_preview_preference))
    mappingAdapter.registerFactory(BadgeModel.GiftedBadgeModel::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.gift_badge_preview_preference))
  }

  sealed class BadgeModel<T : BadgeModel<T>> : MappingModel<T> {

    companion object {
      const val PAYLOAD_BADGE = "badge"
    }

    abstract val badge: Badge?
    abstract val recipient: Recipient

    data class FeaturedModel(override val badge: Badge?) : BadgeModel<FeaturedModel>() {
      override val recipient: Recipient = Recipient.self()
    }

    data class SubscriptionModel(override val badge: Badge?) : BadgeModel<SubscriptionModel>() {
      override val recipient: Recipient = Recipient.self()
    }

    data class GiftedBadgeModel(override val badge: Badge?, override val recipient: Recipient) : BadgeModel<GiftedBadgeModel>()

    override fun areItemsTheSame(newItem: T): Boolean {
      return recipient.id == newItem.recipient.id
    }

    override fun areContentsTheSame(newItem: T): Boolean {
      return badge == newItem.badge && recipient.hasSameContent(newItem.recipient)
    }

    override fun getChangePayload(newItem: T): Any? {
      return if (recipient.hasSameContent(newItem.recipient) && badge != newItem.badge) {
        PAYLOAD_BADGE
      } else {
        null
      }
    }
  }

  class ViewHolder<T : BadgeModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {

    private val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)
    private val badge: BadgeImageView = itemView.findViewById(R.id.badge)

    override fun bind(model: T) {
      if (payload.isEmpty()) {
        avatar.setRecipient(model.recipient)
        avatar.disableQuickContact()
      }

      badge.setBadge(model.badge)
    }
  }
}
