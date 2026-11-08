/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import org.thoughtcrime.securesms.contactshare.SharedContactSource

sealed interface SelectContactAction {
  data object Exit : SelectContactAction

  /** The selection, already resolved to something the share editor can read. */
  data class ContactResolved(val source: SharedContactSource) : SelectContactAction {
    /** A contact uri identifies the person, so only which kind of source it is gets logged. */
    override fun toString(): String = "ContactResolved(${source.javaClass.simpleName})"
  }

  data object CouldNotOpenContact : SelectContactAction

  data object LaunchSystemContactPicker : SelectContactAction
}
