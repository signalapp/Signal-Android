/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.internal.push

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.push.http.CancelationSignal
import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.InputStream

/**
 * A bundle of data needed to start an attachment upload.
 */
data class PushAttachmentData(
  val contentType: String?,
  val data: InputStream,
  val dataSize: Long,
  val incremental: Boolean,
  val outputStreamFactory: OutputStreamFactory,
  val listener: SignalServiceAttachment.ProgressListener?,
  val cancelationSignal: CancelationSignal?,
  val resumableUploadSpec: ResumableUploadSpec
)
