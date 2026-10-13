/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts

sealed interface ShareContactEvent {
  data object Initialize : ShareContactEvent

  data object AvatarToggled : ShareContactEvent

  data object NameToggled : ShareContactEvent

  data class DetailToggled(val id: String) : ShareContactEvent

  data object EditNameClicked : ShareContactEvent

  data class NameEdited(val parts: ContactNameParts) : ShareContactEvent

  data object EditPhotoClicked : ShareContactEvent

  /** Does not commit until the picker is confirmed. */
  data class PhotoSelected(val id: String) : ShareContactEvent

  data object PhotoPickerConfirmed : ShareContactEvent

  data object PhotoPickerDismissed : ShareContactEvent

  data object SendClicked : ShareContactEvent

  data object BackClicked : ShareContactEvent
}
