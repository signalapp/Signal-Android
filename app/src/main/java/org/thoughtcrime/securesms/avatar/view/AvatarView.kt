package org.thoughtcrime.securesms.avatar.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.visible

/**
 * AvatarView encapsulating the AvatarImageView and decorations.
 */
class AvatarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  init {
    inflate(context, R.layout.avatar_view, this)
  }

  private val avatar: AvatarImageView = findViewById(R.id.avatar_image_view)
  private val storyRing: View = findViewById(R.id.avatar_story_ring)

  fun showStoryRing(hasUnreadStory: Boolean) {
    if (!FeatureFlags.stories() || SignalStore.storyValues().isFeatureDisabled) {
      return
    }

    storyRing.visible = true
    storyRing.isActivated = hasUnreadStory

    avatar.scaleX = 0.82f
    avatar.scaleY = 0.82f
  }

  fun hideStoryRing() {
    storyRing.visible = false

    avatar.scaleX = 1f
    avatar.scaleY = 1f
  }

  /**
   * Displays Note-to-Self
   */
  fun displayChatAvatar(recipient: Recipient) {
    avatar.setAvatar(recipient)
  }

  /**
   * Displays Note-to-Self
   */
  fun displayChatAvatar(requestManager: GlideRequests, recipient: Recipient, isQuickContactEnabled: Boolean) {
    avatar.setAvatar(requestManager, recipient, isQuickContactEnabled)
  }

  /**
   * Displays Profile image
   */
  fun displayProfileAvatar(recipient: Recipient) {
    avatar.setRecipient(recipient)
  }

  fun setFallbackPhotoProvider(fallbackPhotoProvider: Recipient.FallbackPhotoProvider) {
    avatar.setFallbackPhotoProvider(fallbackPhotoProvider)
  }

  fun disableQuickContact() {
    avatar.disableQuickContact()
  }
}
