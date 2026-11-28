/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ShareCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsAction
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsEvent
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsScreen
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState
import org.signal.camera.CameraScreenViewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.Util
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Hosts [UsernameLinkSettingsScreen] with its camera, gallery picker and share sheet, and carries out its
 * [UsernameLinkSettingsAction]s.
 */
@Composable
fun UsernameLinkSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: UsernameLinkSettingsViewModel = viewModel { UsernameLinkSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val cameraViewModel: CameraScreenViewModel = viewModel { CameraScreenViewModel() }
  val cameraState by cameraViewModel.state

  LaunchedEffect(cameraViewModel) {
    cameraViewModel.qrCodeDetected.collect { url -> viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(url)) }
  }

  val galleryLauncher = rememberLauncherForActivityResult(QrImageSelectionActivity.Contract()) { uri ->
    if (uri != null) {
      viewModel.onEvent(UsernameLinkSettingsEvent.ImageSelected(uri))
    }
  }

  viewModel.cameraPermission.Content()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is UsernameLinkSettingsAction.CopyToClipboard -> Util.copyToClipboard(context, action.text)
      is UsernameLinkSettingsAction.ShareQrCodeImage -> {
        val intent = ShareCompat.IntentBuilder(context)
          .setType("image/png")
          .setStream(action.uri)
          .createChooserIntent()
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(intent)
      }
      UsernameLinkSettingsAction.OpenGallery -> galleryLauncher.launch(Unit)
      is UsernameLinkSettingsAction.StartConversation -> scope.launch {
        val recipient = withContext(Dispatchers.Default) { Recipient.resolved(action.recipientId) }
        CommunicationActions.startConversation(context, recipient, null)
      }
    }
  }

  UsernameLinkSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent,
    cameraPreview = { modifier ->
      UsernameQrCameraPreview(cameraState = cameraState, cameraEmitter = cameraViewModel::onEvent, modifier = modifier)
    }
  )

  val linkState = state.linkState
  if (state.showShareSheet && linkState is UsernameLinkSettingsState.LinkState.Present) {
    UsernameLinkShareSheet(
      usernameLink = linkState.link,
      onLinkCopied = { viewModel.onEvent(UsernameLinkSettingsEvent.LinkCopied) },
      onDismissRequest = { viewModel.onEvent(UsernameLinkSettingsEvent.ShareSheetDismissed) }
    )
  }
}
