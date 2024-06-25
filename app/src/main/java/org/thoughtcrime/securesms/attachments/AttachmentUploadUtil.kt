/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import org.signal.core.util.logging.Log
import org.signal.protos.resumableuploads.ResumableUpload
import org.thoughtcrime.securesms.blurhash.BlurHashEncoder
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.IOException
import java.util.Objects

/**
 * A place collect common attachment upload operations to allow for code reuse.
 */
object AttachmentUploadUtil {

  private val TAG = Log.tag(AttachmentUploadUtil::class.java)

  /**
   * Builds a [SignalServiceAttachmentStream] from the provided data, which can then be provided to various upload methods.
   */
  @Throws(IOException::class)
  fun buildSignalServiceAttachmentStream(
    context: Context,
    attachment: Attachment,
    uploadSpec: ResumableUpload,
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
      .withResumableUploadSpec(ResumableUploadSpec.from(uploadSpec))
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
      return attachment.blurHash!!.hash
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

    if (Build.VERSION.SDK_INT < 23) {
      Log.w(TAG, "Video thumbnails not supported...")
      return null
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
