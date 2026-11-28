/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.navigation.ResultEffect
import org.signal.core.ui.util.ViewUtil
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.photo.PhotoEditorActivity
import org.thoughtcrime.securesms.mediasend.AvatarSelectionActivity

/** What the user chose in the avatar picker. */
sealed interface AvatarPickerResult {
  data class Selected(val media: Media) : AvatarPickerResult {
    override fun toString(): String = "Selected"
  }

  data object Cleared : AvatarPickerResult
}

/** An avatar the user finished making in the text or vector editor, sent back to the picker. */
data class AvatarEditorResult(val avatar: Avatar) {
  override fun toString(): String = "AvatarEditorResult"
}

/**
 * Hosts the avatar picker and carries out its [AvatarPickerActions]. Edited avatars come back from the editors through
 * the host's result bus as an [AvatarEditorResult].
 *
 * @param onResult called with what the user picked, just before the picker navigates back.
 * @param viewModelFactory picks whose avatar is being chosen.
 */
@Composable
fun AvatarPickerEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  onResult: (AvatarPickerResult) -> Unit,
  viewModelFactory: ViewModelProvider.Factory
) {
  val viewModel: AvatarPickerViewModel = viewModel(factory = viewModelFactory)
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val view = LocalView.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val saveFailedMessage = stringResource(R.string.AvatarPickerRepository__failed_to_save_avatar)

  LaunchedEffect(Unit) {
    ViewUtil.hideKeyboard(context, view)
  }

  ResultEffect<AvatarEditorResult> { result ->
    viewModel.onEvent(AvatarPickerEvents.AvatarEdited(result.avatar))
  }

  val photoSelectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val media = result.data?.getParcelableExtraCompat(AvatarSelectionActivity.EXTRA_MEDIA, Media::class.java)
    if (result.resultCode == Activity.RESULT_OK && media != null) {
      viewModel.onEvent(AvatarPickerEvents.PhotoSelected(media))
    }
  }

  val photoEditorLauncher = rememberLauncherForActivityResult(PhotoEditorActivity.Contract()) { photo ->
    if (photo != null) {
      viewModel.onEvent(AvatarPickerEvents.AvatarEdited(photo))
    }
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      AvatarPickerActions.Close -> parentEventEmitter(AppSettingsNavigationEvent.NavigateBack())
      AvatarPickerActions.ShowSaveFailed -> scope.launch { snackbarHostState.showSnackbar(saveFailedMessage) }
      AvatarPickerActions.LaunchCameraCapture -> photoSelectionLauncher.launch(AvatarSelectionActivity.getIntentForCameraCapture(context))
      AvatarPickerActions.LaunchPhotoSelection -> photoSelectionLauncher.launch(AvatarSelectionActivity.getIntentForGallery(context))
      AvatarPickerActions.LaunchTextAvatarCreation -> parentEventEmitter(AppSettingsNavigationEvent.NavigateToScreen(textAvatarEditorRoute(null)))
      is AvatarPickerActions.LaunchAvatarEditor -> when (val avatar = action.avatar) {
        is Avatar.Photo -> photoEditorLauncher.launch(avatar)
        is Avatar.Resource -> throw UnsupportedOperationException()
        is Avatar.Text -> parentEventEmitter(AppSettingsNavigationEvent.NavigateToScreen(textAvatarEditorRoute(avatar)))
        is Avatar.Vector -> parentEventEmitter(AppSettingsNavigationEvent.NavigateToScreen(vectorAvatarEditorRoute(avatar)))
      }
      is AvatarPickerActions.FinishWithAvatar -> {
        onResult(AvatarPickerResult.Selected(action.media))
        parentEventEmitter(AppSettingsNavigationEvent.NavigateBack())
      }
      AvatarPickerActions.FinishWithClearedAvatar -> {
        onResult(AvatarPickerResult.Cleared)
        parentEventEmitter(AppSettingsNavigationEvent.NavigateBack())
      }
    }
  }

  AvatarPickerScreen(
    state = state,
    onEvent = viewModel::onEvent,
    snackbarHostState = snackbarHostState
  )
}
