/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.snackbars

/**
 * Marker interface for identifying snackbar host locations.
 *
 * Implement this interface to define distinct snackbar display locations within the app.
 * When a [SnackbarState] is emitted, its [SnackbarState.hostKey] is used to route the
 * snackbar to the appropriate registered consumer.
 */
interface SnackbarHostKey {
  object Global : SnackbarHostKey
}
