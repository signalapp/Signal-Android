/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

/** Why the entered account ID can't be submitted. Shown beneath the text field rather than in a dialog. */
sealed interface AccountIdError {
  /** The entered text contains characters that can't appear in an account ID. */
  data object Invalid : AccountIdError
}
