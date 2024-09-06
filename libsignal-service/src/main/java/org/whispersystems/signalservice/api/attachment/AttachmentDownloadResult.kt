/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.attachment

import org.signal.core.util.stream.LimitedInputStream

/**
 * Holds the result of an attachment download.
 */
class AttachmentDownloadResult(
  val dataStream: LimitedInputStream,
  val iv: ByteArray
)
