/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import org.signal.core.models.media.Media
import org.thoughtcrime.securesms.avatar.Avatar

/**
 * One-off actions that require an Activity or the nav graph, and so must be carried out by whoever hosts the picker.
 */
sealed interface AvatarPickerActions {
  /** Leave the picker without applying any changes. */
  data object Close : AvatarPickerActions

  /** Leave the picker, handing [media] back to whoever launched it. */
  data class FinishWithAvatar(val media: Media) : AvatarPickerActions

  /** Leave the picker, telling whoever launched it that the avatar was cleared. */
  data object FinishWithClearedAvatar : AvatarPickerActions

  /** Open the camera to take a new avatar photo. */
  data object LaunchCameraCapture : AvatarPickerActions

  /** Open the gallery to choose an avatar photo. */
  data object LaunchPhotoSelection : AvatarPickerActions

  /** Open the text avatar creator without a starting avatar. */
  data object LaunchTextAvatarCreation : AvatarPickerActions

  /** Open the editor that corresponds to [avatar]. */
  data class LaunchAvatarEditor(val avatar: Avatar) : AvatarPickerActions

  /** Tell the user their avatar could not be saved. */
  data object ShowSaveFailed : AvatarPickerActions
}
