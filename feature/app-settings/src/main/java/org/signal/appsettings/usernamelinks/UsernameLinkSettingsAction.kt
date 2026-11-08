/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import android.net.Uri
import org.signal.core.models.recipients.RecipientId

/**
 * One-shot side effects the host carries out for [UsernameLinkSettingsScreen].
 */
sealed interface UsernameLinkSettingsAction {

  /** Put [text] on the clipboard. */
  data class CopyToClipboard(val text: String) : UsernameLinkSettingsAction {
    override fun toString(): String = "CopyToClipboard"
  }

  /** Offer the QR code image at [uri] to other apps. */
  data class ShareQrCodeImage(val uri: Uri) : UsernameLinkSettingsAction {
    override fun toString(): String = "ShareQrCodeImage"
  }

  /** Let the user pick an image of a QR code. */
  data object OpenGallery : UsernameLinkSettingsAction

  /** Open a chat with whoever the scanned code belongs to. */
  data class StartConversation(val recipientId: RecipientId) : UsernameLinkSettingsAction
}
