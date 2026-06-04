/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import org.signal.registration.util.DebugLoggableModel

sealed class LinkAccountScreenEvent : DebugLoggableModel() {
  data object GetHelpClick : LinkAccountScreenEvent()
  data object CreateAccountClick : LinkAccountScreenEvent()
  data object DisplayOverlayClick : LinkAccountScreenEvent()
  data object HideOverlayClick : LinkAccountScreenEvent()
}
