/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

data class WelcomeScreenState(
  /** Gates whether the link device option is shown as the primary path on large devices. */
  val isLinkAndSyncAvailable: Boolean = false,
  /**
   * Whether to offer the restore-or-transfer option. Hidden during a re-registration, and kept hidden until the parent
   * flow has finished loading so we never briefly show it before learning that pre-existing data exists.
   */
  val showRestoreOrTransfer: Boolean = true
)
