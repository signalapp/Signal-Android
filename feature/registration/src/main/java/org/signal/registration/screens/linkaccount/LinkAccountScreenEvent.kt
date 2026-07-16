/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

sealed interface LinkAccountScreenEvent {
  data object GetHelpClick : LinkAccountScreenEvent
  data object CreateAccountClick : LinkAccountScreenEvent
  data object DisplayOverlayClick : LinkAccountScreenEvent
  data object HideOverlayClick : LinkAccountScreenEvent
  data object RetryQrCode : LinkAccountScreenEvent
  data object DismissError : LinkAccountScreenEvent
  data object ConfirmDeleteAndRelink : LinkAccountScreenEvent
  data object CancelDeleteAndRelink : LinkAccountScreenEvent
}
