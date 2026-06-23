/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.discoverability

import org.signal.registration.util.DebugLoggableModel

sealed class PhoneNumberDiscoverabilityScreenEvents : DebugLoggableModel() {
  data object EveryoneSelected : PhoneNumberDiscoverabilityScreenEvents()
  data object NobodySelected : PhoneNumberDiscoverabilityScreenEvents()
  data object NobodyConfirmed : PhoneNumberDiscoverabilityScreenEvents()
  data object NobodyDismissed : PhoneNumberDiscoverabilityScreenEvents()
  data object SaveClicked : PhoneNumberDiscoverabilityScreenEvents()
  data object BackClicked : PhoneNumberDiscoverabilityScreenEvents()
}
