/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.snackbars

/**
 * A consumer that can display snackbar messages.
 *
 * Implementations are typically UI components that host a snackbar display area.
 */
fun interface SnackbarStateConsumer {
  /**
   * Consumes the given snackbar state.
   *
   * @param snackbarState The snackbar to display.
   */
  fun consume(snackbarState: SnackbarState)
}
