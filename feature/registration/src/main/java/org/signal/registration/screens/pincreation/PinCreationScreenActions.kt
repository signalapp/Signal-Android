/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

sealed interface PinCreationScreenActions {
  /** Open the article explaining Signal PINs. */
  data object OpenLearnMoreArticle : PinCreationScreenActions
}
