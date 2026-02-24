/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.accountlocked

sealed class AccountLockedScreenEvents {
  data object Next : AccountLockedScreenEvents()
  data object LearnMore : AccountLockedScreenEvents()
}
