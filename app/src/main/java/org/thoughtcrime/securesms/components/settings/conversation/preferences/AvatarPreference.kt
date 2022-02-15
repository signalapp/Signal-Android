package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.view.View
import androidx.core.view.ViewCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto
import org.thoughtcrime.securesms.contacts.avatars.FallbackPhoto
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Renders a large avatar (80dp) for a given Recipient.
 */
object AvatarPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.conversation_settings_avatar_preference_item))
  }

  class Model(
    val recipient: Recipient,
    val onAvatarClick: (View) -> Unit,
    val onBadgeClick: (Badge) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipient == newItem.recipient
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && recipient.hasSameContent(newItem.recipient)
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {
    private val avatar: AvatarImageView = itemView.findViewById<AvatarImageView>(R.id.bio_preference_avatar).apply {
      setFallbackPhotoProvider(AvatarPreferenceFallbackPhotoProvider())
    }

    private val badge: BadgeImageView = itemView.findViewById(R.id.bio_preference_badge)

    init {
      ViewCompat.setTransitionName(avatar.parent as View, "avatar")
    }

    override fun bind(model: Model) {
      if (model.recipient.isSelf) {
        badge.setBadge(null)
        badge.setOnClickListener(null)
      } else {
        badge.setBadgeFromRecipient(model.recipient)
        badge.setOnClickListener {
          val badge = model.recipient.badges.firstOrNull()
          if (badge != null) {
            model.onBadgeClick(badge)
          }
        }
      }

      avatar.setAvatar(model.recipient)
      avatar.disableQuickContact()
      avatar.setOnClickListener { model.onAvatarClick(avatar) }
    }
  }

  private class AvatarPreferenceFallbackPhotoProvider : Recipient.FallbackPhotoProvider() {
    override fun getPhotoForGroup(): FallbackContactPhoto {
      return FallbackPhoto(R.drawable.ic_group_outline_40, ViewUtil.dpToPx(8))
    }
  }
}
