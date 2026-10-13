/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.editname

sealed interface EditContactNameEvent {
  /** Seeds the editor with the name as it currently stands. */
  data class Initialize(val parts: ContactNameParts) : EditContactNameEvent

  data class PrefixChanged(val value: String) : EditContactNameEvent {
    override fun toString(): String = "PrefixChanged(hasValue=${value.isNotBlank()})"
  }

  data class GivenNameChanged(val value: String) : EditContactNameEvent {
    override fun toString(): String = "GivenNameChanged(hasValue=${value.isNotBlank()})"
  }

  data class MiddleNameChanged(val value: String) : EditContactNameEvent {
    override fun toString(): String = "MiddleNameChanged(hasValue=${value.isNotBlank()})"
  }

  data class FamilyNameChanged(val value: String) : EditContactNameEvent {
    override fun toString(): String = "FamilyNameChanged(hasValue=${value.isNotBlank()})"
  }

  data class SuffixChanged(val value: String) : EditContactNameEvent {
    override fun toString(): String = "SuffixChanged(hasValue=${value.isNotBlank()})"
  }

  /** The user tapped "Done" and the edited name should be handed back to the share screen. */
  data object SaveClicked : EditContactNameEvent

  /** The user tapped the navigation icon in the app bar, discarding any edits. */
  data object BackClicked : EditContactNameEvent
}
