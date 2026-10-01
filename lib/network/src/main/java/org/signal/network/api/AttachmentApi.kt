/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.coroutines.runBlocking
import org.signal.libsignal.net.AuthMessagesService
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UploadTooLargeException
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm

/**
 * Class to interact with various attachment-related endpoints.
 */
class AttachmentApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * Gets a v4 attachment upload form, which provides the necessary information to upload an attachment.
   */
  fun getAttachmentV4UploadForm(uploadSizeBytes: Long): RequestResult<AttachmentUploadForm, UploadTooLargeException> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { chatConnection ->
        AuthMessagesService(chatConnection).getUploadForm(uploadSizeBytes)
      }.map { form ->
        AttachmentUploadForm(
          cdn = form.cdn,
          key = form.key,
          headers = form.headers,
          signedUploadLocation = form.signedUploadUrl.toString()
        )
      }
    }
  }
}
