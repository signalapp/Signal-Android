/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

sealed interface AddUsernameScreenActions {
  /** Open the article explaining Signal usernames. */
  data object OpenLearnMoreArticle : AddUsernameScreenActions
}
