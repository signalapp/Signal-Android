package org.whispersystems.signalservice.internal.push.http

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest
import java.io.IOException
import java.io.InputStream

/**
 * This [RequestBody] encrypts the data written to it before it is sent.
 */
class DigestingRequestBody(
  inputStream: InputStream,
  outputStreamFactory: OutputStreamFactory,
  private val contentType: String,
  private val contentLength: Long,
  incremental: Boolean,
  progressListener: SignalServiceAttachment.ProgressListener?,
  cancelationSignal: CancelationSignal?,
  private val contentStart: Long
) : RequestBody() {

  private val uploadStream: DigestingUploadStream = DigestingUploadStream(inputStream, outputStreamFactory, contentType, contentLength, incremental, progressListener, cancelationSignal, contentStart)
  private val body: RequestBody = uploadStream.nextChunk(contentLength - contentStart)

  val attachmentDigest: AttachmentDigest?
    get() = uploadStream.attachmentDigest

  override fun contentType(): MediaType? {
    return contentType.toMediaTypeOrNull()
  }

  @Throws(IOException::class)
  override fun writeTo(sink: BufferedSink) {
    body.writeTo(sink)
  }

  override fun contentLength(): Long {
    return if (contentLength > 0) contentLength - contentStart else -1
  }

  override fun isOneShot(): Boolean {
    return true
  }
}
