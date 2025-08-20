/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.fragment.app.DialogFragment
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController
import org.thoughtcrime.securesms.megaphone.MegaphoneComponent
import org.thoughtcrime.securesms.megaphone.Megaphones

data class MainMegaphoneState(
  val megaphone: Megaphone = Megaphone.NONE,
  val mainToolbarMode: MainToolbarMode = MainToolbarMode.FULL
)

object EmptyMegaphoneActionController : MegaphoneActionController {
  override fun onMegaphoneNavigationRequested(intent: Intent) = Unit
  override fun onMegaphoneNavigationRequested(intent: Intent, requestCode: Int) = Unit
  override fun onMegaphoneToastRequested(string: String) = Unit
  override fun getMegaphoneActivity(): Activity = error("Empty controller")
  override fun onMegaphoneSnooze(event: Megaphones.Event) = Unit
  override fun onMegaphoneCompleted(event: Megaphones.Event) = Unit
  override fun onMegaphoneDialogFragmentRequested(dialogFragment: DialogFragment) = Unit
}

/**
 * Composable wrapper for Megaphones
 */
@Composable
fun MainMegaphoneContainer(
  state: MainMegaphoneState,
  controller: MegaphoneActionController,
  onMegaphoneVisible: (Megaphone) -> Unit
) {
  val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val visible = remember(isLandscape, state) {
    !(state.megaphone == Megaphone.NONE || state.mainToolbarMode != MainToolbarMode.FULL || isLandscape)
  }

  AnimatedVisibility(visible = visible) {
    MegaphoneComponent(
      megaphone = state.megaphone,
      megaphoneActionController = controller
    )
  }

  LaunchedEffect(state, isLandscape) {
    if (state.megaphone == Megaphone.NONE || state.mainToolbarMode == MainToolbarMode.BASIC || isLandscape) {
      return@LaunchedEffect
    }

    onMegaphoneVisible(state.megaphone)
  }
}

@SignalPreview
@Composable
private fun MainMegaphoneContainerPreview() {
  Previews.Preview {
    MainMegaphoneContainer(
      state = MainMegaphoneState(
        megaphone = Megaphone.Builder(Megaphones.Event.ONBOARDING, Megaphone.Style.ONBOARDING).build()
      ),
      controller = EmptyMegaphoneActionController,
      onMegaphoneVisible = {}
    )
  }
}
