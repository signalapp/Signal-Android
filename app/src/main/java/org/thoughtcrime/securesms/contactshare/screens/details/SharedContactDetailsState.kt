/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

import org.thoughtcrime.securesms.recipients.RecipientId

/** State for a received contact card. Any section can be absent. */
data class SharedContactDetailsState(
  val displayName: String = "",
  /** Null when there is none, or when it is already the title. */
  val organization: String? = null,
  val photoUri: String? = null,
  /** False when the only name is a company, which has no initials worth drawing. */
  val hasPersonalName: Boolean = true,
  val signalRecipientId: RecipientId? = null,
  val actions: List<ContactAction> = emptyList(),
  val details: List<DetailRow> = emptyList(),
  val contextMenu: ContextMenu? = null,
  val isLoading: Boolean = false
) {
  val isOnSignal: Boolean
    get() = signalRecipientId != null

  val showCallButtons: Boolean
    get() = isOnSignal

  enum class ContactAction {
    INVITE_TO_SIGNAL,
    ADD_TO_PHONE_CONTACTS,
    ADD_TO_GROUP
  }

  data class DetailRow(
    val id: String,
    val lines: List<String>,
    val label: String,
    val kind: DetailKind
  ) {
    val copyText: String
      get() = lines.joinToString("\n")
  }

  enum class DetailKind {
    PHONE,

    /** Only ever from the card, never from our own recipient. */
    NICKNAME,
    NOTE,

    EMAIL,
    ADDRESS
  }

  data class ContextMenu(
    val detailId: String,
    val actions: List<DetailAction>
  )

  enum class DetailAction {
    MESSAGE,
    VIDEO_CALL,
    AUDIO_CALL,
    OPEN_IN_MAPS,
    COPY
  }
}
