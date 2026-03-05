/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import org.thoughtcrime.securesms.mediasend.CameraFragment

/**
 * Displays the proper camera capture ui
 */
@Composable
fun MediaSendV3CameraSlot() {
  val fragmentClass = remember {
    CameraFragment.getFragmentClass()
  }

  AndroidFragment(
    clazz = fragmentClass,
    modifier = Modifier.fillMaxSize()
  )
}
