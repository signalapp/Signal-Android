/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.DialogFragment
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController
import org.thoughtcrime.securesms.megaphone.MegaphoneViewBuilder
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.util.visible

data class MainMegaphoneState(
  val megaphone: Megaphone = Megaphone.NONE,
  val isDisplayingArchivedChats: Boolean = false,
  val isSearchOpen: Boolean = false,
  val isInActionMode: Boolean = false
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
  val visible = remember(isLandscape, state.isDisplayingArchivedChats, state.isSearchOpen, state.isInActionMode, state.megaphone) {
    !(state.megaphone == Megaphone.NONE || state.megaphone.style == Megaphone.Style.FULLSCREEN || state.isDisplayingArchivedChats || isLandscape || state.isSearchOpen || state.isInActionMode)
  }

  AnimatedVisibility(visible = visible) {
    if (LocalInspectionMode.current) {
      Box(
        modifier = Modifier
          .background(color = Color.Red)
          .fillMaxWidth()
          .height(80.dp)
      )
    } else {
      AndroidView(factory = { context ->
        LayoutInflater.from(context).inflate(R.layout.conversation_list_megaphone_container, null, false) as FrameLayout
      }) { megaphoneContainer ->
        val view = requireNotNull(MegaphoneViewBuilder.build(megaphoneContainer.context, state.megaphone, controller))
        megaphoneContainer.removeAllViews()
        megaphoneContainer.addView(view)
        megaphoneContainer.visible = true
      }
    }
  }

  LaunchedEffect(state.megaphone, state.isDisplayingArchivedChats, isLandscape) {
    if (state.megaphone == Megaphone.NONE || state.isDisplayingArchivedChats || isLandscape) {
      return@LaunchedEffect
    }

    if (state.megaphone.style == Megaphone.Style.FULLSCREEN) {
      state.megaphone.onVisibleListener?.onEvent(state.megaphone, controller)
    }

    onMegaphoneVisible(state.megaphone)
  }
}

@SignalPreview
@Composable
private fun MainMegaphoneContainerPreview() {
  Previews.Preview {
    MainMegaphoneContainer(
      state = MainMegaphoneState(),
      controller = EmptyMegaphoneActionController,
      onMegaphoneVisible = {}
    )
  }
}
