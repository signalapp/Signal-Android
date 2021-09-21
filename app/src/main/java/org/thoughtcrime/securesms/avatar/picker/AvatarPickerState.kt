package org.thoughtcrime.securesms.avatar.picker

import org.thoughtcrime.securesms.avatar.Avatar

data class AvatarPickerState(
  val currentAvatar: Avatar? = null,
  val selectableAvatars: List<Avatar> = listOf(),
  val canSave: Boolean = false,
  val canClear: Boolean = false,
  val isCleared: Boolean = false
)
