/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.discoverability

import org.signal.registration.util.DebugLoggableModel

data class PhoneNumberDiscoverabilityState(
  val discoverable: Boolean = true,
  val showNobodyConfirmation: Boolean = false
) : DebugLoggableModel()
