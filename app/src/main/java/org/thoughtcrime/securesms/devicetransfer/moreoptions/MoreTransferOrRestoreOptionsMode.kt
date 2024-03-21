/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer.moreoptions

/**
 * Allows component opening sheet to specify mode
 */
enum class MoreTransferOrRestoreOptionsMode {
  /**
   * Only display the option to log in without transferring. Selection
   * will be disabled.
   */
  SKIP_ONLY,

  /**
   * Display transfer/restore local/skip as well as a next and cancel button
   */
  SELECTION
}
