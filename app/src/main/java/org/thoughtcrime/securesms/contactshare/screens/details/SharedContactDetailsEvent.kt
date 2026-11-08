/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.ContactAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailAction

sealed interface SharedContactDetailsEvent {
  data object Initialize : SharedContactDetailsEvent

  data object BackClicked : SharedContactDetailsEvent

  data object MessageClicked : SharedContactDetailsEvent
  data object VideoCallClicked : SharedContactDetailsEvent
  data object AudioCallClicked : SharedContactDetailsEvent

  data class ActionClicked(val action: ContactAction) : SharedContactDetailsEvent

  /** Tap or long press, both open the menu. */
  data class DetailPressed(val id: String) : SharedContactDetailsEvent

  data class DetailActionClicked(val action: DetailAction) : SharedContactDetailsEvent

  data object ContextMenuDismissed : SharedContactDetailsEvent
}
