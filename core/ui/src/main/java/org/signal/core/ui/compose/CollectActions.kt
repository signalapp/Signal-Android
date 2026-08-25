/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Helper for collecting one-off actions emitted from a ViewModel. Ensures lifecycle safety.
 */
@Composable
fun <T> CollectActions(actions: Flow<T>, onAction: (T) -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(actions, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      actions.collect(onAction)
    }
  }
}
