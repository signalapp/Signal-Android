package org.thoughtcrime.securesms.avatar.vector

import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.AvatarColorItem
import org.thoughtcrime.securesms.avatar.Avatars

data class VectorAvatarCreationState(
  val currentAvatar: Avatar.Vector
) {
  fun colors(): List<AvatarColorItem> = Avatars.colors.map { AvatarColorItem(it, currentAvatar.color == it) }
}
