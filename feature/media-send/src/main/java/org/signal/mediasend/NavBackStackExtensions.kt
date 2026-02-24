/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

internal fun NavBackStack<NavKey>.goToEdit() {
  if (contains(MediaSendNavKey.Edit)) {
    popTo(MediaSendNavKey.Edit)
  } else {
    add(MediaSendNavKey.Edit)
  }
}

internal fun NavBackStack<NavKey>.pop() {
  if (isNotEmpty()) {
    removeAt(size - 1)
  }
}

private fun NavBackStack<NavKey>.popTo(key: NavKey) {
  while (size > 1 && get(size - 1) != key) {
    removeAt(size - 1)
  }
}
