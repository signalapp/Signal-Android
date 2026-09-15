/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import org.thoughtcrime.securesms.recipients.RecipientId

/** State for the "Share contact" screen. */
data class ShareContactState(
  val sendingTo: String = "",
  val recipientId: RecipientId? = null,
  val avatar: AvatarSelection? = null,
  val name: NameSelection? = null,
  val details: List<DetailSelection> = emptyList(),
  val photoPicker: PhotoPicker? = null,
  val isLoading: Boolean = false,
  val isSending: Boolean = false
) {
  val canSend: Boolean
    get() = !isLoading && !isSending && name != null && name.isSelected && name.displayName.isNotBlank()

  data class AvatarSelection(
    val isSelected: Boolean,
    /** Null renders the name's initials, and shares no photo. */
    val photo: ContactPhoto?,
    val isEditable: Boolean
  )

  data class NameSelection(
    val displayName: String,
    val isSelected: Boolean,
    val isEditable: Boolean,
    /** When false the user cannot deselect the name. */
    val isToggleable: Boolean = true
  )

  data class DetailSelection(
    val id: String,
    val lines: List<String>,
    val label: DetailLabel,
    val isSelected: Boolean
  )

  data class PhotoPicker(
    val options: List<PhotoOption>,
    val selectedId: String
  )

  data class PhotoOption(
    val id: String,
    /** Null for the "no photo" choice, which the picker draws as the initials fallback. */
    val photo: ContactPhoto?
  )

  /** Profile photos are blobbed first, so sharing one is no different from an address book photo. */
  data class ContactPhoto(
    val uri: String,
    val isProfile: Boolean
  )

  /** Fixed labels are resolved by the view, keeping resources out of the view model. */
  sealed interface DetailLabel {
    data object Nickname : DetailLabel
    data object Note : DetailLabel

    data class Text(val value: String) : DetailLabel
  }
}
