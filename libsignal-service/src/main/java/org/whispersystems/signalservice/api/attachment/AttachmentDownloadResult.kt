/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.attachment

import java.io.InputStream

/**
 * Holds the result of an attachment download.
 */
class AttachmentDownloadResult(
  val dataStream: InputStream,
  val iv: ByteArray
)
