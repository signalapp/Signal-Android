/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

sealed interface EnterAepScreenActions {
  /** Open the article explaining recovery keys. */
  data object OpenRecoveryKeyHelpArticle : EnterAepScreenActions
}
