/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.accountlocked

import org.signal.registration.util.DebugLoggableModel

sealed class AccountLockedScreenEvents : DebugLoggableModel() {
  data object Next : AccountLockedScreenEvents()
  data object LearnMore : AccountLockedScreenEvents()
}
