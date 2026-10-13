/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages

import org.whispersystems.signalservice.internal.crypto.AttachmentDigest
import org.whispersystems.signalservice.internal.push.PushAttachmentData
import java.io.IOException

/**
 * Uploads an attachment to the location in [PushAttachmentData.resumableUploadSpec].
 */
fun interface AttachmentUploader {
  @Throws(IOException::class)
  fun uploadAttachmentBlocking(attachmentData: PushAttachmentData): AttachmentDigest
}
