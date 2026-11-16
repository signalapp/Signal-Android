/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** A [ViewModelStoreOwner] that lives exactly as long as the calling composition. */
@Composable
fun rememberViewModelStoreOwner(): ViewModelStoreOwner {
  val owner = remember {
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  }

  DisposableEffect(owner) {
    onDispose { owner.viewModelStore.clear() }
  }

  return owner
}
