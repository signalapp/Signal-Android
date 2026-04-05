/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.content.Context
import android.graphics.Bitmap
import org.signal.blurhash.BlurHashEncoder
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Objects

/**
 * A place collect common attachment upload operations to allow for code reuse.
 */
object AttachmentUploadUtil {

  private val TAG = Log.tag(AttachmentUploadUtil::class.java)

  /**
   * Foreground notification shows while uploading attachments larger than this.
   */
  val FOREGROUND_LIMIT_BYTES: Long = 10.mebiBytes.inWholeBytes

  /**
   * Computes the base64-encoded SHA-256 checksum of the ciphertext that would result from encrypting [plaintextStream]
   * with the given [key] and [iv], including padding, IV prefix, and HMAC suffix.
   */
  fun computeCiphertextChecksum(key: ByteArray, iv: ByteArray, plaintextStream: InputStream, plaintextSize: Long): String {
    val paddedStream = PaddingInputStream(plaintextStream, plaintextSize)
    return Base64.encodeWithPadding(AttachmentCipherStreamUtil.computeCiphertextSha256(key, iv, paddedStream))
  }

  /**
   * Computes the base64-encoded SHA-256 checksum of the raw bytes in [inputStream].
   * Used for pre-encrypted uploads where the data is already in its final form.
   */
  fun computeRawChecksum(inputStream: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16 * 1024)
    var read: Int
    while (inputStream.read(buffer).also { read = it } != -1) {
      digest.update(buffer, 0, read)
    }
    return Base64.encodeWithPadding(digest.digest())
  }

  /**
   * Builds a [SignalServiceAttachmentStream] from the provided data, which can then be provided to various upload methods.
   */
  @Throws(IOException::class)
  fun buildSignalServiceAttachmentStream(
    context: Context,
    attachment: Attachment,
    cancellationSignal: (() -> Boolean)? = null,
    progressListener: ProgressListener? = null
  ): SignalServiceAttachmentStream {
    val inputStream = PartAuthority.getAttachmentStream(context, attachment.uri!!)
    val builder = SignalServiceAttachment.newStreamBuilder()
      .withStream(inputStream)
      .withContentType(attachment.contentType)
      .withLength(attachment.size)
      .withFileName(attachment.fileName)
      .withVoiceNote(attachment.voiceNote)
      .withBorderless(attachment.borderless)
      .withGif(attachment.videoGif)
      .withFaststart(attachment.transformProperties?.mp4FastStart ?: false)
      .withWidth(attachment.width)
      .withHeight(attachment.height)
      .withUploadTimestamp(System.currentTimeMillis())
      .withCaption(attachment.caption)
      .withCancelationSignal(cancellationSignal)
      .withListener(progressListener)
      .withUuid(attachment.uuid)

    if (MediaUtil.isImageType(attachment.contentType)) {
      builder.withBlurHash(getImageBlurHash(context, attachment))
    } else if (MediaUtil.isVideoType(attachment.contentType)) {
      builder.withBlurHash(getVideoBlurHash(context, attachment))
    }

    return builder.build()
  }

  @Throws(IOException::class)
  private fun getImageBlurHash(context: Context, attachment: Attachment): String? {
    if (attachment.blurHash != null) {
      return attachment.blurHash.hash
    }

    if (attachment.uri == null) {
      return null
    }

    return PartAuthority.getAttachmentStream(context, attachment.uri!!).use { inputStream ->
      BlurHashEncoder.encode(inputStream)
    }
  }

  @Throws(IOException::class)
  private fun getVideoBlurHash(context: Context, attachment: Attachment): String? {
    if (attachment.blurHash != null) {
      return attachment.blurHash.hash
    }

    return MediaUtil.getVideoThumbnail(context, Objects.requireNonNull(attachment.uri), 1000)?.let { bitmap ->
      val thumb = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
      bitmap.recycle()

      Log.i(TAG, "Generated video thumbnail...")
      val hash = BlurHashEncoder.encode(thumb)
      thumb.recycle()

      hash
    }
  }
}
