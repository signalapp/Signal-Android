package org.signal.mediasend

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.showSnackbar
import org.signal.mediasend.capture.MediaCaptureScreen
import org.signal.mediasend.edit.MediaEditScreen
import org.signal.mediasend.select.MediaSelectScreen
import org.signal.mediasend.select.MediaSelectScreenState

/**
 * Enforces the following flow of:
 *
 * Capture -> Edit -> Send
 * Select -> Edit -> Send
 */
@Composable
internal fun MediaSendNavDisplay(
  stateFlow: StateFlow<MediaSendState>,
  snackbarEvents: Flow<SnackbarEvent>,
  backStack: NavBackStack<NavKey>,
  eventHandler: MediaSendEventHandler,
  modifier: Modifier = Modifier,
  textStoryEditorSlot: @Composable () -> Unit = {},
  sendSlot: @Composable (MediaSendState) -> Unit = {}
) {
  Box {
    NavDisplay(
      backStack = backStack,
      modifier = modifier.fillMaxSize()
    ) { key ->
      when (key) {
        is MediaSendNavKey.Capture -> NavEntry(MediaSendNavKey.Capture.Chrome) {
          val state by stateFlow.collectAsStateWithLifecycle()

          MediaCaptureScreen(
            backStack = backStack,
            state = state,
            onEvent = eventHandler::onMediaCaptureScreenEvent,
            textStoryEditorSlot = textStoryEditorSlot
          )
        }

        MediaSendNavKey.Select.Folders -> NavEntry(key) {
          val state by stateFlow.collectAsStateWithLifecycle()
          val screenState = remember(state.mediaFolders, state.selectedMedia) {
            MediaSelectScreenState.Folders(
              mediaFolders = state.mediaFolders,
              selectedMedia = state.selectedMedia
            )
          }

          MediaSelectScreen(
            state = screenState,
            onEvent = eventHandler::onMediaSelectScreenEvent
          )
        }

        is MediaSendNavKey.Select.Files -> NavEntry(key) {
          val state by stateFlow.collectAsStateWithLifecycle()
          val screenState = remember(state.selectedMedia, state.selectedMediaFolderItems) {
            MediaSelectScreenState.Files(
              selectedMediaFolder = key.folder,
              selectedMediaFolderItems = state.selectedMediaFolderItems,
              selectedMedia = state.selectedMedia
            )
          }

          MediaSelectScreen(
            state = screenState,
            onEvent = eventHandler::onMediaSelectScreenEvent
          )
        }

        is MediaSendNavKey.Edit -> NavEntry(MediaSendNavKey.Edit) {
          val state by stateFlow.collectAsStateWithLifecycle()
          MediaEditScreen(
            state = state,
            onEvent = eventHandler::onMediaEditScreenEvent
          )
        }

        is MediaSendNavKey.Send -> NavEntry(key) {
          val state by stateFlow.collectAsStateWithLifecycle()
          sendSlot(state)
        }

        else -> error("Unknown key: $key")
      }
    }

    Snackbar(snackbarEvents)
  }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun BoxScope.Snackbar(
  snackbarEvents: Flow<SnackbarEvent>
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarHostState) {
    snackbarEvents.collect { event ->
      snackbarHostState.showSnackbar(
        message = context.getString(event.message),
        duration = event.duration
      )
    }
  }

  Snackbars.Host(
    snackbarHostState,
    modifier = Modifier.align(Alignment.BottomCenter)
  )
}

@AllDevicePreviews
@Composable
private fun MediaSendNavDisplayPreview() {
  Previews.Preview {
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null)) {
      MediaSendNavDisplay(
        stateFlow = MutableStateFlow(MediaSendState(isCameraFirst = true)),
        snackbarEvents = emptyFlow(),
        backStack = rememberNavBackStack(MediaSendNavKey.Edit),
        eventHandler = MediaSendEventHandler.Empty,
        textStoryEditorSlot = { BoxWithText("Text Story Editor Slot") },
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
