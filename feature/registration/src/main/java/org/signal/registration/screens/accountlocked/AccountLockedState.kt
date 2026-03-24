/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.accountlocked

import org.signal.registration.util.DebugLoggableModel

data class AccountLockedState(
  val daysRemaining: Int = 10
) : DebugLoggableModel()
