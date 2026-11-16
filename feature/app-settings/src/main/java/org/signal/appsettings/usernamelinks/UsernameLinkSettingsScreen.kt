/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object UsernameLinkSettingsTestTags {
  const val TAB_CODE = "tab-code"
  const val TAB_SCAN = "tab-scan"
  const val BUTTON_CLOSE = "button-close"
  const val QR_CODE = "qr-code"
  const val BUTTON_LINK = "button-link"
  const val BUTTON_SHARE = "button-share"
  const val BUTTON_COLOR = "button-color"
  const val BUTTON_RESET = "button-reset"
}

/**
 * The user's username QR code and link, with ways to share, recolor and reset them, beside a scanner for other people's
 * codes.
 *
 * @param cameraPreview the scanner's camera viewfinder, drawn to fill the modifier it's handed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameLinkSettingsScreen(
  state: UsernameLinkSettingsState,
  onEvent: (UsernameLinkSettingsEvent) -> Unit,
  modifier: Modifier = Modifier,
  cameraPreview: @Composable (Modifier) -> Unit = {}
) {
  LifecycleResumeEffect(Unit) {
    onEvent(UsernameLinkSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(UsernameLinkSettingsEvent.SnackbarDismissed) }
      )
    },
    topBar = {
      TopBar(
        activeTab = state.activeTab,
        onEvent = onEvent,
        scrollBehavior = scrollBehavior
      )
    },
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { contentPadding ->
    AnimatedVisibility(
      visible = state.activeTab == UsernameLinkSettingsState.Tab.CODE,
      enter = slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }),
      exit = slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth })
    ) {
      CodeTab(
        state = state,
        onEvent = onEvent,
        modifier = Modifier.padding(contentPadding)
      )
    }

    AnimatedVisibility(
      visible = state.activeTab == UsernameLinkSettingsState.Tab.SCAN,
      enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
      exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
    ) {
      UsernameQrScanTab(
        hasCameraPermission = state.hasCameraPermission,
        onAllowCameraClicked = { onEvent(UsernameLinkSettingsEvent.AllowCameraClicked) },
        onOpenGalleryClicked = { onEvent(UsernameLinkSettingsEvent.OpenGalleryClicked) },
        cameraPreview = cameraPreview,
        modifier = Modifier.padding(contentPadding)
      )
    }
  }

  if (state.busy) {
    Dialogs.IndeterminateProgressDialog()
  }

  UsernameLinkSettingsDialog(dialog = state.dialog, onEvent = onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
  activeTab: UsernameLinkSettingsState.Tab,
  onEvent: (UsernameLinkSettingsEvent) -> Unit,
  scrollBehavior: TopAppBarScrollBehavior
) {
  CenterAlignedTopAppBar(
    title = {
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        TabButton(
          label = stringResource(R.string.UsernameLinkSettings_code_tab_name),
          active = activeTab == UsernameLinkSettingsState.Tab.CODE,
          onClick = { onEvent(UsernameLinkSettingsEvent.CodeTabSelected) },
          modifier = Modifier
            .padding(end = 8.dp)
            .testTag(UsernameLinkSettingsTestTags.TAB_CODE)
        )
        TabButton(
          label = stringResource(R.string.UsernameLinkSettings_scan_tab_name),
          active = activeTab == UsernameLinkSettingsState.Tab.SCAN,
          onClick = { onEvent(UsernameLinkSettingsEvent.ScanTabSelected) },
          modifier = Modifier
            .padding(end = 8.dp)
            .testTag(UsernameLinkSettingsTestTags.TAB_SCAN)
        )
      }
    },
    navigationIcon = {
      IconButton(
        onClick = { onEvent(UsernameLinkSettingsEvent.NavigateBackClicked) },
        modifier = Modifier.testTag(UsernameLinkSettingsTestTags.BUTTON_CLOSE)
      ) {
        Icon(
          imageVector = SignalIcons.X.imageVector,
          contentDescription = stringResource(android.R.string.cancel)
        )
      }
    },
    scrollBehavior = scrollBehavior,
    modifier = Modifier.fillMaxWidth()
  )
}

@Composable
private fun TabButton(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = if (active) {
    ButtonDefaults.filledTonalButtonColors()
  } else {
    ButtonDefaults.buttonColors(
      containerColor = SignalTheme.colors.colorSurface2,
      contentColor = MaterialTheme.colorScheme.onSurface
    )
  }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = colors.containerColor,
    contentColor = colors.contentColor,
    modifier = modifier
      .defaultMinSize(minWidth = 100.dp, minHeight = 32.dp)
      .semantics { role = Role.Tab; selected = active }
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .wrapContentSize()
        .padding(horizontal = 16.dp, vertical = 6.dp)
    )
  }
}

@Composable
private fun CodeTab(
  state: UsernameLinkSettingsState,
  onEvent: (UsernameLinkSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.verticalScroll(rememberScrollState())
  ) {
    QrCodeBadge(
      data = state.qrCodeState,
      colorScheme = state.colorScheme,
      username = state.username,
      usernameCopyable = true,
      onClick = { onEvent(UsernameLinkSettingsEvent.UsernameClicked) },
      modifier = Modifier
        .padding(horizontal = 58.dp, vertical = 24.dp)
        .testTag(UsernameLinkSettingsTestTags.QR_CODE)
    )

    Row(
      horizontalArrangement = Arrangement.spacedBy(space = 32.dp, alignment = Alignment.CenterHorizontally),
      modifier = Modifier.fillMaxWidth()
    ) {
      Buttons.ActionButton(
        enabled = state.linkState is UsernameLinkSettingsState.LinkState.Present,
        onClick = { onEvent(UsernameLinkSettingsEvent.LinkClicked) },
        imageVector = SignalIcons.Link.imageVector,
        label = stringResource(R.string.UsernameLinkSettings_link_button_label),
        modifier = Modifier.testTag(UsernameLinkSettingsTestTags.BUTTON_LINK)
      )
      Buttons.ActionButton(
        onClick = { onEvent(UsernameLinkSettingsEvent.ShareQrCodeClicked) },
        imageVector = SignalIcons.Share.imageVector,
        label = stringResource(R.string.UsernameLinkSettings_share_button_label),
        modifier = Modifier.testTag(UsernameLinkSettingsTestTags.BUTTON_SHARE)
      )
      Buttons.ActionButton(
        onClick = { onEvent(UsernameLinkSettingsEvent.ColorClicked) },
        imageVector = SignalIcons.Color.imageVector,
        label = stringResource(R.string.UsernameLinkSettings_color_button_label),
        modifier = Modifier.testTag(UsernameLinkSettingsTestTags.BUTTON_COLOR)
      )
    }

    Text(
      text = stringResource(R.string.UsernameLinkSettings_qr_description),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 42.dp, bottom = 19.dp, start = 43.dp, end = 43.dp)
    )

    Buttons.Small(
      onClick = { onEvent(UsernameLinkSettingsEvent.ResetClicked) },
      modifier = Modifier
        .padding(bottom = 24.dp)
        .testTag(UsernameLinkSettingsTestTags.BUTTON_RESET)
    ) {
      Text(text = stringResource(R.string.UsernameLinkSettings_reset_button_label))
    }
  }
}

@Composable
private fun UsernameLinkSettingsDialog(
  dialog: UsernameLinkSettingsState.Dialog,
  onEvent: (UsernameLinkSettingsEvent) -> Unit
) {
  when (dialog) {
    UsernameLinkSettingsState.Dialog.None -> Unit

    UsernameLinkSettingsState.Dialog.ConfirmReset -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.UsernameLinkSettings_reset_link_dialog_title),
        body = stringResource(R.string.UsernameLinkSettings_reset_link_dialog_body),
        confirm = stringResource(R.string.UsernameLinkSettings_reset_link_dialog_confirm_button),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(UsernameLinkSettingsEvent.ResetConfirmed) },
        onDeny = { onEvent(UsernameLinkSettingsEvent.DialogDismissed) },
        onDismissRequest = { onEvent(UsernameLinkSettingsEvent.DialogDismissed) }
      )
    }

    is UsernameLinkSettingsState.Dialog.ResetResult -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(
          when (dialog.outcome) {
            UsernameLinkSettingsState.ResetOutcome.SUCCESS -> R.string.UsernameLinkSettings_reset_link_result_success
            UsernameLinkSettingsState.ResetOutcome.NETWORK_ERROR -> R.string.UsernameLinkSettings_reset_link_result_network_error
            UsernameLinkSettingsState.ResetOutcome.NETWORK_UNAVAILABLE -> R.string.UsernameLinkSettings_reset_link_result_network_unavailable
            UsernameLinkSettingsState.ResetOutcome.UNEXPECTED_ERROR -> R.string.UsernameLinkSettings_reset_link_result_unknown_error
          }
        ),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { onEvent(UsernameLinkSettingsEvent.DialogDismissed) }
      )
    }

    is UsernameLinkSettingsState.Dialog.ScanFailed -> {
      QrScanProblemDialog(problem = dialog.problem, onDismiss = { onEvent(UsernameLinkSettingsEvent.DialogDismissed) })
    }
  }
}

@Composable
private fun UsernameLinkSettingsState.Snackbar.message(): String? {
  return when (this) {
    UsernameLinkSettingsState.Snackbar.None -> null
    UsernameLinkSettingsState.Snackbar.UsernameCopied -> stringResource(R.string.UsernameLinkSettings_username_copied_toast)
    UsernameLinkSettingsState.Snackbar.LinkCopied -> stringResource(R.string.UsernameLinkSettings_link_copied_toast)
    UsernameLinkSettingsState.Snackbar.CameraPermissionDenied -> stringResource(R.string.UsernameLinkSettingsScreen__camera_needed)
  }
}

private val previewState = UsernameLinkSettingsState(
  username = "PeterParker.42",
  linkState = UsernameLinkSettingsState.LinkState.Present("https://signal.me/#eu/abc"),
  qrCodeState = QrCodeState.Present(QrCodeData.forData("https://signal.me/#eu/abc")),
  colorScheme = UsernameQrCodeColorScheme.Orange
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameLinkSettingsScreenPreview() {
  UsernameLinkSettingsScreen(state = previewState, onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameLinkSettingsScreenScanPreview() {
  UsernameLinkSettingsScreen(state = previewState.copy(activeTab = UsernameLinkSettingsState.Tab.SCAN), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameLinkSettingsScreenResetPreview() {
  UsernameLinkSettingsScreen(state = previewState.copy(dialog = UsernameLinkSettingsState.Dialog.ConfirmReset), onEvent = {})
}
