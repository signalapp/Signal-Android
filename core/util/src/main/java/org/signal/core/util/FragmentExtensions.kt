/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch

/**
 * Helper function for collecting flows in fragments.
 */
fun <T> Fragment.collectFlowOnViewLifecycle(
  flow: Flow<T>,
  lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
  collector: FlowCollector<T>
) {
  viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(lifecycleState) {
      flow.collect(collector)
    }
  }
}
