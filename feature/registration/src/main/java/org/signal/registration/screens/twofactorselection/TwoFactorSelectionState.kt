/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.twofactorselection

/**
 * Everything [TwoFactorSelectionScreen] needs to render.
 */
data class TwoFactorSelectionState(
  /** The methods to offer, in the order they should be displayed. */
  val methods: List<TwoFactorMethod> = emptyList(),
  /** Whether to tell the user their passkey ceremony didn't work, leaving them here to pick again. */
  val showPasskeyError: Boolean = false
)
