/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.nicknames

import org.thoughtcrime.securesms.recipients.Recipient

data class NicknameState(
  val recipient: Recipient? = null,
  val firstName: String = "",
  val lastName: String = "",
  val note: String = "",
  val noteCharactersRemaining: Int = 0,
  val formState: FormState = FormState.LOADING,
  val hasBecomeReady: Boolean = false,
  val isEditing: Boolean = false
) {

  private val isFormBlank: Boolean = firstName.isBlank() && lastName.isBlank() && note.isBlank()
  private val hasNameOrNote: Boolean = firstName.isNotBlank() || lastName.isNotBlank() || note.isNotBlank()
  private val isFormReady: Boolean = formState == FormState.READY
  private val isBlankFormDuringEdit: Boolean = isFormBlank && isEditing

  val canSave: Boolean = isFormReady && (hasNameOrNote || isBlankFormDuringEdit)
  enum class FormState {
    LOADING,
    READY,
    SAVING,
    SAVED
  }
}
