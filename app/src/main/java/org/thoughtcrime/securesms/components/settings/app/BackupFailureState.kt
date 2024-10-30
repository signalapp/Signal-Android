/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

/**
 * Describes the current backup failure state.
 */
enum class BackupFailureState {
  NONE,
  SUBSCRIPTION_STATE_MISMATCH
}
