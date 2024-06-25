/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.reregisterwithpin

data class ReRegisterWithPinState(
  val isLocalVerification: Boolean = false,
  val hasIncorrectGuess: Boolean = false,
  val localPinMatches: Boolean = false
)
