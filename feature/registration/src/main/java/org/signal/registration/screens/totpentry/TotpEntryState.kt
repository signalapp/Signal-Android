/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

/**
 * Everything [TotpEntryScreen] needs to render.
 */
data class TotpEntryState(
  val codeEntry: CodeEntryFieldState = CodeEntryFieldState()
)
