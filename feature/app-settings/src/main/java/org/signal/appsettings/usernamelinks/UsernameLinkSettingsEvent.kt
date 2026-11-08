/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import android.net.Uri

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface UsernameLinkSettingsEvent {

  /** The screen came back to the foreground, so the QR code's colors or the camera permission may have changed. */
  data object ScreenResumed : UsernameLinkSettingsEvent

  /** The user tapped the close icon. */
  data object NavigateBackClicked : UsernameLinkSettingsEvent

  /** The user switched to their own QR code. */
  data object CodeTabSelected : UsernameLinkSettingsEvent

  /** The user switched to the scanner. */
  data object ScanTabSelected : UsernameLinkSettingsEvent

  /** The user tapped the username on their QR code. */
  data object UsernameClicked : UsernameLinkSettingsEvent

  /** The user tapped the link button. */
  data object LinkClicked : UsernameLinkSettingsEvent

  /** The link was copied from the share sheet. */
  data object LinkCopied : UsernameLinkSettingsEvent

  /** The share sheet was closed. */
  data object ShareSheetDismissed : UsernameLinkSettingsEvent

  /** The user tapped the button to share their QR code as an image. */
  data object ShareQrCodeClicked : UsernameLinkSettingsEvent

  /** The user tapped the button to change the QR code's colors. */
  data object ColorClicked : UsernameLinkSettingsEvent

  /** The user tapped the button to reset their link. */
  data object ResetClicked : UsernameLinkSettingsEvent

  /** The user confirmed, in [UsernameLinkSettingsState.Dialog.ConfirmReset], that they want a new link. */
  data object ResetConfirmed : UsernameLinkSettingsEvent

  /** The user tapped the button that asks for the camera. */
  data object AllowCameraClicked : UsernameLinkSettingsEvent

  /** The user tapped the button to pick an image of a QR code. */
  data object OpenGalleryClicked : UsernameLinkSettingsEvent

  /** The camera read a QR code. */
  data class QrCodeScanned(val url: String) : UsernameLinkSettingsEvent {
    override fun toString(): String = "QrCodeScanned"
  }

  /** The user picked an image that may contain a QR code. */
  data class ImageSelected(val uri: Uri) : UsernameLinkSettingsEvent {
    override fun toString(): String = "ImageSelected"
  }

  /** Dismisses whatever is in [UsernameLinkSettingsState.dialog]. */
  data object DialogDismissed : UsernameLinkSettingsEvent

  /** Whatever was in [UsernameLinkSettingsState.snackbar] has gone. */
  data object SnackbarDismissed : UsernameLinkSettingsEvent
}
