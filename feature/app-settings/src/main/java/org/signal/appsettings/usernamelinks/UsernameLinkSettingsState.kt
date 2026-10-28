/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

data class UsernameLinkSettingsState(
  val activeTab: Tab = Tab.CODE,
  /** The user's username, written `nickname.discriminator`. */
  val username: String = "",
  val linkState: LinkState = LinkState.NotSet,
  val qrCodeState: QrCodeState = QrCodeState.Loading,
  val colorScheme: UsernameQrCodeColorScheme = UsernameQrCodeColorScheme.Blue,
  val hasCameraPermission: Boolean = false,
  /** True while a scanned code is being looked up. */
  val busy: Boolean = false,
  /** Whether the sheet for copying or sharing the link is open. */
  val showShareSheet: Boolean = false,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  override fun toString(): String {
    return "UsernameLinkSettingsState(activeTab=$activeTab, usernameLength=${username.length}, linkState=$linkState, qrCodeState=${qrCodeState.javaClass.simpleName}, colorScheme=$colorScheme, hasCameraPermission=$hasCameraPermission, busy=$busy, showShareSheet=$showShareSheet, dialog=$dialog, snackbar=$snackbar)"
  }

  enum class Tab {
    /** The user's own QR code and link. */
    CODE,

    /** The camera, for scanning someone else's code. */
    SCAN
  }

  sealed interface LinkState {
    data class Present(val link: String) : LinkState {
      override fun toString(): String = "Present"
    }

    data object NotSet : LinkState

    data object Resetting : LinkState
  }

  sealed interface Dialog {
    data object None : Dialog

    /** Asks the user to confirm replacing their link and QR code. */
    data object ConfirmReset : Dialog

    /** Says how resetting the link went. */
    data class ResetResult(val outcome: ResetOutcome) : Dialog

    /** Says why a scanned code didn't lead to a chat. */
    data class ScanFailed(val problem: QrScanProblem) : Dialog
  }

  enum class ResetOutcome {
    SUCCESS,
    NETWORK_ERROR,
    NETWORK_UNAVAILABLE,
    UNEXPECTED_ERROR
  }

  sealed interface Snackbar {
    data object None : Snackbar

    data object UsernameCopied : Snackbar

    data object LinkCopied : Snackbar

    /** The camera can't be opened because the user didn't allow it. */
    data object CameraPermissionDenied : Snackbar
  }
}

/** Why a scanned or imported QR code couldn't be turned into a chat. */
sealed interface QrScanProblem {
  /** The code wasn't a username link. */
  data object InvalidData : QrScanProblem

  data object NetworkError : QrScanProblem

  /** The chosen image didn't contain a code at all. */
  data object QrNotFound : QrScanProblem

  /** No one has the username the link points to, which is named when the link included it. */
  data class NotFound(val username: String?) : QrScanProblem {
    override fun toString(): String = "NotFound(hasUsername=${username != null})"
  }
}
