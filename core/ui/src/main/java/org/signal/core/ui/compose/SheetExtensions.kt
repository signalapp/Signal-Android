/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Simple helper to dismiss the sheet and run a callback when the animation is finished.
 * In unit tests, set skipAnimations = true to invoke the callback immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun SheetState.dismissWithAnimation(
  scope: CoroutineScope,
  skipAnimations: Boolean = Build.MODEL.equals("robolectric", ignoreCase = true),
  onComplete: () -> Unit
) {
  if (skipAnimations) {
    onComplete()
    return
  }

  scope.launch {
    this@dismissWithAnimation.hide()
    onComplete()
  }
}
