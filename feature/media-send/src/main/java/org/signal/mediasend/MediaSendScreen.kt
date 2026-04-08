/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.signal.core.ui.compose.theme.SignalTheme

@Composable
fun MediaSendScreen(
  contractArgs: MediaSendActivityContract.Args,
  modifier: Modifier = Modifier,
  cameraSlot: @Composable () -> Unit = {},
  textStoryEditorSlot: @Composable () -> Unit = {},
  videoEditorSlot: @Composable () -> Unit = {},
  sendSlot: @Composable (MediaSendState) -> Unit = {}
) {
  val viewModel = viewModel<MediaSendViewModel>(factory = MediaSendViewModel.Factory(args = contractArgs))

  val state by viewModel.state.collectAsStateWithLifecycle()
  val backStack = rememberNavBackStack(
    if (state.isCameraFirst) MediaSendNavKey.Capture.Camera else MediaSendNavKey.Select
  )

  SignalTheme {
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides LocalActivity.current as NavigationEventDispatcherOwner) {
      Surface {
        MediaSendNavDisplay(
          stateFlow = viewModel.state,
          backStack = backStack,
          callback = viewModel,
          modifier = modifier,
          cameraSlot = cameraSlot,
          textStoryEditorSlot = textStoryEditorSlot,
          videoEditorSlot = videoEditorSlot,
          sendSlot = sendSlot
        )
      }
    }
  }
}
