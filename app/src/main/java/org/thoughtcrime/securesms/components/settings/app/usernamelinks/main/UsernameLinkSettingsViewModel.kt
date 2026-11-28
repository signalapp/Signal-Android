/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.Manifest
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsAction
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsEvent
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.Dialog
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.LinkState
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.ResetOutcome
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.Snackbar
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.Tab
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.compose.PermissionController
import org.signal.core.util.logging.Log
import org.signal.appsettings.R as AppSettingsR

/**
 * Drives the user's username QR code and link, and the scanner for other people's codes.
 */
class UsernameLinkSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<UsernameLinkSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(UsernameLinkSettingsViewModel::class)
  }

  /** The host renders its [PermissionController.Content]. */
  val cameraPermission = PermissionController(Manifest.permission.CAMERA, permanentDenialMessage = AppSettingsR.string.UsernameLinkSettingsScreen__camera_needed)

  private val _state = MutableStateFlow(
    UsernameLinkSettingsState(
      username = UsernameLinkSettingsRepository.getUsername(),
      linkState = UsernameLinkSettingsRepository.getUsernameLink()?.let { LinkState.Present(it) } ?: LinkState.NotSet,
      colorScheme = UsernameLinkSettingsRepository.getColorScheme(),
      hasCameraPermission = UsernameLinkSettingsRepository.hasCameraPermission()
    )
  )
  private val _actions = Channel<UsernameLinkSettingsAction>(Channel.BUFFERED)

  private var chatOpenedFromScan = false

  val state: StateFlow<UsernameLinkSettingsState> = _state.asStateFlow()
  val actions: Flow<UsernameLinkSettingsAction> = _actions.receiveAsFlow()

  init {
    val link = UsernameLinkSettingsRepository.getUsernameLink()
    viewModelScope.launch {
      if (link != null) {
        val qrCodeData = UsernameLinkSettingsRepository.createQrCodeData(link)
        _state.update { it.copy(qrCodeState = QrCodeState.Present(qrCodeData)) }
      } else {
        resetLink()
      }
    }
  }

  override suspend fun processEvent(event: UsernameLinkSettingsEvent) {
    when (event) {
      UsernameLinkSettingsEvent.ScreenResumed -> {
        chatOpenedFromScan = false
        _state.update {
          it.copy(colorScheme = UsernameLinkSettingsRepository.getColorScheme(), hasCameraPermission = UsernameLinkSettingsRepository.hasCameraPermission())
        }
      }
      UsernameLinkSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      UsernameLinkSettingsEvent.CodeTabSelected -> _state.update { it.copy(activeTab = Tab.CODE) }
      UsernameLinkSettingsEvent.ScanTabSelected -> _state.update { it.copy(activeTab = Tab.SCAN) }
      UsernameLinkSettingsEvent.UsernameClicked -> {
        _actions.send(UsernameLinkSettingsAction.CopyToClipboard(_state.value.username))
        _state.update { it.copy(snackbar = Snackbar.UsernameCopied) }
      }
      UsernameLinkSettingsEvent.LinkClicked -> _state.update { it.copy(showShareSheet = it.linkState is LinkState.Present) }
      UsernameLinkSettingsEvent.LinkCopied -> _state.update { it.copy(snackbar = Snackbar.LinkCopied) }
      UsernameLinkSettingsEvent.ShareSheetDismissed -> _state.update { it.copy(showShareSheet = false) }
      UsernameLinkSettingsEvent.ShareQrCodeClicked -> shareQrCode()
      UsernameLinkSettingsEvent.ColorClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.UsernameLinkRoute.QRColorPicker)
      UsernameLinkSettingsEvent.ResetClicked -> _state.update { it.copy(dialog = Dialog.ConfirmReset) }
      UsernameLinkSettingsEvent.ResetConfirmed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        resetLink()
      }
      UsernameLinkSettingsEvent.AllowCameraClicked -> requestCamera()
      UsernameLinkSettingsEvent.OpenGalleryClicked -> _actions.send(UsernameLinkSettingsAction.OpenGallery)
      is UsernameLinkSettingsEvent.QrCodeScanned -> onQrCodeScanned(event.url)
      is UsernameLinkSettingsEvent.ImageSelected -> onImageSelected(event.uri)
      UsernameLinkSettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = Dialog.None) }
      UsernameLinkSettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = Snackbar.None) }
    }
  }

  private suspend fun shareQrCode() {
    val qrCodeState = _state.value.qrCodeState
    if (qrCodeState !is QrCodeState.Present) {
      Log.w(TAG, "No QR code to share yet. ${qrCodeState.javaClass.simpleName}")
      return
    }

    val uri = UsernameLinkSettingsRepository.createQrCodeShareImage(qrCodeState.data, _state.value.colorScheme, _state.value.username)
    _actions.send(UsernameLinkSettingsAction.ShareQrCodeImage(uri))
  }

  /**
   * Replaces the link and redraws the QR code for it. When the service can't be reached the old link stays, and so
   * does its QR code.
   */
  private suspend fun resetLink() {
    val previousQrCode = _state.value.qrCodeState as? QrCodeState.Present
    _state.update { it.copy(linkState = LinkState.Resetting, qrCodeState = QrCodeState.Loading) }

    val result = UsernameLinkSettingsRepository.resetUsernameLink()
    val link = when (result) {
      is UsernameLinkResetResult.Success -> UsernameLinkSettingsRepository.toLink(result.components)
      UsernameLinkResetResult.NetworkError -> null
      else -> UsernameLinkSettingsRepository.getUsernameLink()
    }

    val qrCodeState = when {
      link != null -> QrCodeState.Present(UsernameLinkSettingsRepository.createQrCodeData(link))
      previousQrCode != null -> previousQrCode
      else -> QrCodeState.NotSet
    }

    _state.update {
      it.copy(
        linkState = link?.let { LinkState.Present(it) } ?: LinkState.NotSet,
        qrCodeState = qrCodeState,
        dialog = Dialog.ResetResult(result.toOutcome())
      )
    }
  }

  private fun requestCamera() {
    viewModelScope.launch {
      val granted = cameraPermission.request()
      _state.update {
        it.copy(
          hasCameraPermission = granted,
          snackbar = if (granted || cameraPermission.isPermanentlyDenied) it.snackbar else Snackbar.CameraPermissionDenied
        )
      }
    }
  }

  /** The camera reports a code on every frame it's in view, so only the first is looked up until that's resolved. */
  private suspend fun onQrCodeScanned(url: String) {
    if (chatOpenedFromScan || _state.value.busy || _state.value.dialog != Dialog.None) {
      return
    }

    _state.update { it.copy(busy = true) }
    onScanResult(UsernameLinkSettingsRepository.lookUpUsernameLink(url))
  }

  private suspend fun onImageSelected(uri: Uri) {
    _state.update { it.copy(busy = true) }
    onScanResult(UsernameLinkSettingsRepository.scanImage(uri))
  }

  private suspend fun onScanResult(result: QrScanResult) {
    val problem = result.toProblem()
    _state.update { it.copy(busy = false, dialog = problem?.let { Dialog.ScanFailed(it) } ?: it.dialog) }

    if (result is QrScanResult.Success) {
      chatOpenedFromScan = true
      _actions.send(UsernameLinkSettingsAction.StartConversation(result.recipient.id))
    }
  }

  private fun UsernameLinkResetResult.toOutcome(): ResetOutcome {
    return when (this) {
      is UsernameLinkResetResult.Success -> ResetOutcome.SUCCESS
      UsernameLinkResetResult.NetworkError -> ResetOutcome.NETWORK_ERROR
      UsernameLinkResetResult.NetworkUnavailable -> ResetOutcome.NETWORK_UNAVAILABLE
      UsernameLinkResetResult.UnexpectedError -> ResetOutcome.UNEXPECTED_ERROR
    }
  }
}
