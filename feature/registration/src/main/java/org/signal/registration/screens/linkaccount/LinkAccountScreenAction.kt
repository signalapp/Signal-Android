/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

sealed interface LinkAccountScreenAction {
  /** Open the article explaining how to link a device. */
  data object OpenGetHelpArticle : LinkAccountScreenAction
}
