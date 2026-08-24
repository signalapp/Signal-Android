/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera

import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** How long a new rotation must hold before we commit it, so a sweep past a diagonal doesn't flicker. */
private const val SETTLE_MS = 200L

/** True for the [Surface] rotations that correspond to a landscape device posture. */
fun isLandscapeRotation(rotation: Int): Boolean {
  return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
}

/**
 * Committed physical device rotation via [OrientationEventListener] — used because a foldable inner display's
 * window rotation can't be relied on. First reading commits immediately; later ones debounce past [SETTLE_MS].
 */
@Composable
fun rememberDeviceRotation(): Int {
  val context = LocalContext.current
  var rawRotation by remember { mutableStateOf<Int?>(null) }
  var committedRotation by remember { mutableStateOf(Surface.ROTATION_0) }
  var hasCommitted by remember { mutableStateOf(false) }

  DisposableEffect(context) {
    val listener = object : OrientationEventListener(context) {
      override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return
        rawRotation = when (orientation) {
          in 330..360, in 0..30 -> Surface.ROTATION_0
          in 60..120 -> Surface.ROTATION_270
          in 150..210 -> Surface.ROTATION_180
          in 240..300 -> Surface.ROTATION_90
          else -> return
        }
      }
    }
    listener.enable()
    onDispose { listener.disable() }
  }

  LaunchedEffect(rawRotation) {
    val raw = rawRotation ?: return@LaunchedEffect
    if (hasCommitted) {
      delay(SETTLE_MS)
    }
    committedRotation = raw
    hasCommitted = true
  }

  return committedRotation
}
