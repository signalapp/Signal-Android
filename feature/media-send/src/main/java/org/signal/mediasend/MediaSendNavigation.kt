package org.signal.mediasend

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.signal.core.ui.compose.DialogController
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.showSnackbar
import org.signal.core.ui.navigation.TransitionSpecs
import org.signal.mediasend.screens.capture.MediaCaptureScreen
import org.signal.mediasend.screens.capture.MediaCaptureScreenEvents
import org.signal.mediasend.screens.capture.MediaCaptureViewModel
import org.signal.mediasend.screens.edit.MediaEditScreen
import org.signal.mediasend.screens.edit.MediaEditScreenDialogs
import org.signal.mediasend.screens.edit.MediaEditViewModel
import org.signal.mediasend.screens.select.MediaSelectScreen
import org.signal.mediasend.screens.select.MediaSelectViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Enforces the following flow of:
 *
 * Capture -> Edit -> Send
 * Select -> Edit -> Send
 */
@Composable
internal fun MediaSendNavigation(
  viewModel: MediaSendFlowViewModel,
  modifier: Modifier = Modifier,
  textStoryEditorSlot: @Composable () -> Unit = {},
  sendSlot: @Composable (MediaSendFlowState) -> Unit = {}
) {
  Box {
    NavDisplay(
      backStack = viewModel.backStack,
      modifier = modifier.fillMaxSize(),
      // Each select screen owns a view model of its own, so entries need their own stores rather than the activity's.
      entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
      ),
      transitionSpec = { TransitionSpecs.Fade.transitionSpec },
      popTransitionSpec = { TransitionSpecs.Fade.popTransitionSpec },
      predictivePopTransitionSpec = { TransitionSpecs.Fade.predictivePopTransitionSpec }
    ) { key ->
      when (key) {
        is MediaSendRoute.Capture -> NavEntry(MediaSendRoute.Capture.Chrome) {
          val captureViewModel: MediaCaptureViewModel = viewModel(
            factory = MediaCaptureViewModel.Factory(
              parentState = viewModel.state,
              parentEventEmitter = viewModel::onEvent,
              selectedCaptureScreen = key
            )
          )
          val state by captureViewModel.state.collectAsStateWithLifecycle()

          // Toggling between the camera and the text story editor is navigation, so it arrives as a new key on an
          // entry that is deliberately not recreated by it.
          LaunchedEffect(key) {
            captureViewModel.onEvent(MediaCaptureScreenEvents.SelectedCaptureScreenChanged(key))
          }

          MediaCaptureScreen(
            state = state,
            onEvent = captureViewModel::onEvent,
            textStoryEditorSlot = textStoryEditorSlot
          )
        }

        MediaSendRoute.Select.Folders -> NavEntry(key) {
          val selectViewModel: MediaSelectViewModel = viewModel(
            factory = MediaSelectViewModel.Factory(
              parentState = viewModel.state,
              parentEventEmitter = viewModel::onEvent,
              mediaFolder = null,
              selectionAdditions = viewModel.selectionAdditions
            )
          )
          val state by selectViewModel.state.collectAsStateWithLifecycle()

          selectViewModel.readMediaPermission.Content()

          MediaSelectScreen(
            state = state,
            onEvent = selectViewModel::onEvent,
            selectionAdditions = selectViewModel.selectionAdditions
          )
        }

        is MediaSendRoute.Select.Files -> NavEntry(key) {
          val selectViewModel: MediaSelectViewModel = viewModel(
            factory = MediaSelectViewModel.Factory(
              parentState = viewModel.state,
              parentEventEmitter = viewModel::onEvent,
              mediaFolder = key.folder,
              selectionAdditions = viewModel.selectionAdditions
            )
          )
          val state by selectViewModel.state.collectAsStateWithLifecycle()

          selectViewModel.readMediaPermission.Content()

          MediaSelectScreen(
            state = state,
            onEvent = selectViewModel::onEvent,
            selectionAdditions = selectViewModel.selectionAdditions
          )
        }

        is MediaSendRoute.Edit -> NavEntry(MediaSendRoute.Edit) {
          val editViewModel: MediaEditViewModel = viewModel(
            factory = MediaEditViewModel.Factory(
              parentState = viewModel.state,
              parentEventEmitter = viewModel::onEvent
            )
          )
          val state by editViewModel.state.collectAsStateWithLifecycle()

          SaveToStorageDialog(editViewModel)
          editViewModel.writeStoragePermission.Content()

          MediaEditScreen(
            state = state,
            onEvent = editViewModel::onEvent,
            imageControllers = viewModel.imageControllers,
            mediaInputFactory = MediaSendDependencies.mediaInputFactory
          )
        }

        is MediaSendRoute.Send -> NavEntry(key) {
          val state by viewModel.state.collectAsStateWithLifecycle()
          sendSlot(state)
        }

        else -> error("Unknown key: $key")
      }
    }

    // NavDisplay only consumes back while there is somewhere left to go back to. Composed after it to catch the press
    // at the root, which would otherwise fall through to the activity and finish it.
    BackHandler(enabled = viewModel.backStack.size == 1) {
      viewModel.onCloseRequested()
    }

    DiscardMediaDialog(viewModel.discardMediaDialog)
    Snackbar(viewModel.snackbarEvents)
    Toast(viewModel.toastEvents)
    SendProgress(viewModel.state)
  }
}

private val TOAST_DURATION = 3.seconds
private val SEND_PROGRESS_DELAY = 300.milliseconds

/**
 * Warns that saving a copy to shared storage leaves it outside of Signal, before the first save of a session.
 */
@Composable
private fun SaveToStorageDialog(viewModel: MediaEditViewModel) {
  viewModel.saveToStorageDialog.Content { _, onDismissRequest, onConfirm, _, _ ->
    MediaEditScreenDialogs.SaveToStorageConfirmationDialog(
      onSave = { doNotShowAgain ->
        if (doNotShowAgain) {
          viewModel.markSaveToStorageWarningDismissed()
        }
        onConfirm()
      },
      onDismissRequest = onDismissRequest
    )
  }
}

/**
 * Dialog displayed when the user tries to close out of media send, to warn them that they'll discard media.
 */
@Composable
private fun DiscardMediaDialog(
  controller: DialogController<Unit>
) {
  controller.Content { _, onDismissRequest, onConfirm, _, onDeny ->
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.MediaSendDialogs__discard_media),
      body = stringResource(R.string.MediaSendDialogs__you_will_lose_any_media),
      confirm = stringResource(R.string.MediaSendDialogs__discard),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = onConfirm,
      onDeny = onDeny,
      onDismissRequest = onDismissRequest
    )
  }
}

/**
 * Covers the whole flow while a send is in flight, so that the media on its way out cannot be edited or resent. Sends
 * that resolve immediately never show it.
 */
@Composable
private fun SendProgress(
  state: StateFlow<MediaSendFlowState>
) {
  val isSending by remember(state) { state.map { it.isSending }.distinctUntilChanged() }
    .collectAsStateWithLifecycle(initialValue = false)

  Dialogs.IndeterminateProgressDialog(
    visible = isSending,
    delayDuration = SEND_PROGRESS_DELAY
  )
}

/**
 * Shows each [ToastEvent] over the middle of the screen for [TOAST_DURATION]. A new event replaces whatever is showing
 * and restarts that countdown.
 */
@Composable
private fun BoxScope.Toast(
  toastEvents: Flow<ToastEvent>
) {
  var event: ToastEvent? by remember { mutableStateOf(null) }
  var visible: Boolean by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    toastEvents.collectLatest {
      event = it
      visible = true
      delay(TOAST_DURATION)
      visible = false
    }
  }

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = Modifier.align(Alignment.Center)
  ) {
    // Held past the hide so that it is still there to fade out.
    event?.let { MediaSendToast(event = it) }
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
