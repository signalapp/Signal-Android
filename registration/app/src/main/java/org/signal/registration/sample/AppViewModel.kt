/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample

import androidx.lifecycle.ViewModel
import org.signal.core.ui.navigation.ResultEventBus

class AppViewModel : ViewModel() {
  val resultEventBus = ResultEventBus()
}
