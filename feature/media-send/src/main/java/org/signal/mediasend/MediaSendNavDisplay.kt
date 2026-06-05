package org.signal.mediasend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.mediasend.edit.MediaEditScreen
import org.signal.mediasend.select.MediaSelectScreen

/**
 * Enforces the following flow of:
 *
 * Capture -> Edit -> Send
 * Select -> Edit -> Send
 */
@Composable
fun MediaSendNavDisplay(
  stateFlow: StateFlow<MediaSendState>,
  backStack: NavBackStack<NavKey>,
  callback: MediaSendCallback,
  modifier: Modifier = Modifier,
  cameraSlot: @Composable () -> Unit = {},
  textStoryEditorSlot: @Composable () -> Unit = {},
  videoEditorSlot: @Composable () -> Unit = {},
  sendSlot: @Composable (MediaSendState) -> Unit = {}
) {
  NavDisplay(
    backStack = backStack,
    modifier = modifier.fillMaxSize()
  ) { key ->
    when (key) {
      is MediaSendNavKey.Capture -> NavEntry(MediaSendNavKey.Capture.Chrome) {
        MediaCaptureScreen(
          backStack = backStack,
          cameraSlot = cameraSlot,
          textStoryEditorSlot = textStoryEditorSlot
        )
      }

      MediaSendNavKey.Select -> NavEntry(key) {
        val state by stateFlow.collectAsStateWithLifecycle()
        MediaSelectScreen(
          state = state,
          backStack = backStack,
          callback = callback
        )
      }

      is MediaSendNavKey.Edit -> NavEntry(MediaSendNavKey.Edit) {
        val state by stateFlow.collectAsStateWithLifecycle()
        MediaEditScreen(
          state = state,
          backStack = backStack,
          videoEditorSlot = videoEditorSlot,
          callback = callback
        )
      }

      is MediaSendNavKey.Send -> NavEntry(key) {
        val state by stateFlow.collectAsStateWithLifecycle()
        sendSlot(state)
      }

      else -> error("Unknown key: $key")
    }
  }
}

@AllDevicePreviews
@Composable
private fun MediaSendNavDisplayPreview() {
  Previews.Preview {
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null)) {
      MediaSendNavDisplay(
        stateFlow = MutableStateFlow(MediaSendState(isCameraFirst = true)),
        backStack = rememberNavBackStack(MediaSendNavKey.Edit),
        callback = MediaSendCallback.Empty,
        cameraSlot = { BoxWithText("Camera Slot") },
        textStoryEditorSlot = { BoxWithText("Text Story Editor Slot") },
        videoEditorSlot = { BoxWithText("Video Editor Slot") },
        sendSlot = { _ -> BoxWithText("Send Slot") }
      )
    }
  }
}

@Composable
private fun BoxWithText(text: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = text)
  }
}
