/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import org.signal.core.models.media.Media
import org.thoughtcrime.securesms.avatar.Avatar

sealed interface AvatarPickerEvents {
  /** User has selected an avatar */
  data class AvatarSelected(val avatar: Avatar) : AvatarPickerEvents

  /** User has selected a photo to use as an avatar */
  data class PhotoSelected(val media: Media) : AvatarPickerEvents

  /** User has edited avatar */
  data class AvatarEdited(val avatar: Avatar) : AvatarPickerEvents

  /** User has cleared their current avatar */
  data object ClearAvatar : AvatarPickerEvents

  /** User has deleted an avatar */
  data class DeleteAvatar(val avatar: Avatar) : AvatarPickerEvents

  /** User wants to take a photo */
  data object CapturePhoto : AvatarPickerEvents

  /** User wants to select a photo */
  data object SelectPhoto : AvatarPickerEvents

  /** User wants to select text */
  data object SelectText : AvatarPickerEvents

  /** User wants to edit the avatar */
  data class EditAvatar(val avatar: Avatar) : AvatarPickerEvents

  /** User has saved */
  data object Save : AvatarPickerEvents

  /** User has chosen to close the picker */
  data object Close : AvatarPickerEvents
}
