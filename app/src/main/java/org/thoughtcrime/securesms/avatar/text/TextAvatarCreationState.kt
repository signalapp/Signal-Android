package org.thoughtcrime.securesms.avatar.text

import org.thoughtcrime.securesms.avatar.Avatar

/**
 * @param currentAvatar the avatar as edited so far.
 * @param selectedTab whether the user is typing the avatar's text or choosing its color.
 */
data class TextAvatarCreationState(
  val currentAvatar: Avatar.Text,
  val selectedTab: Tab = Tab.TEXT
) {
  enum class Tab {
    TEXT,
    COLOR
  }
}
