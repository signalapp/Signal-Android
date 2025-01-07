/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Delays setting the state to [key] for the given [delayDuration].
 *
 * Useful for reducing animation flickering when displaying loading indicators
 * when the process may finish immediately or may take a bit of time.
 */
@Composable
fun <T> rememberDelayedState(
  key: T,
  delayDuration: Duration = 200.milliseconds
): State<T> {
  val delayedState = remember { mutableStateOf(key) }

  LaunchedEffect(key, delayDuration) {
    delay(delayDuration)
    delayedState.value = key
  }

  return delayedState
}
