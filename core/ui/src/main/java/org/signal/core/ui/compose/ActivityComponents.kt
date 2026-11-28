/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.fragment.app.FragmentActivity

/**
 * Returns whether the screen is currently in the system picture-in-picture mode.
 *
 * This requires an AppCompatActivity context, so it cannot be utilized in Composables
 * that require a preview.
 */
@Composable
fun rememberIsInPipMode(): Boolean {
  if (Build.VERSION.SDK_INT >= 26) {
    val activity = LocalActivity.current as AppCompatActivity
    var pipMode: Boolean by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
      val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
        pipMode = info.isInPictureInPictureMode
      }
      activity.addOnPictureInPictureModeChangedListener(
        observer
      )
      onDispose { activity.removeOnPictureInPictureModeChangedListener(observer) }
    }
    return pipMode
  } else {
    return false
  }
}

/**
 * The [FragmentActivity] hosting this composition, for callers that need its fragment manager or other fragment APIs.
 * Fails with a message naming the actual host when the composition isn't in one.
 */
@Composable
fun requireFragmentActivity(): FragmentActivity {
  val activity = LocalActivity.current
  return activity as? FragmentActivity ?: error("Expected a FragmentActivity host, but found ${activity?.javaClass?.name}.")
}
