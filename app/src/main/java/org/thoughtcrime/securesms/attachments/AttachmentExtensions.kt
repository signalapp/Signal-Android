/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.content.Context
import android.text.TextUtils
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.util.toByteArray
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import java.io.IOException

/**
 * Converts an [Attachment] to an [AttachmentPointer]. Will return null if any essential data is invalid.
 */
fun Attachment.toAttachmentPointer(context: Context): AttachmentPointer? {
  val attachment = this

  if (TextUtils.isEmpty(attachment.remoteLocation)) {
    return null
  }

  if (TextUtils.isEmpty(attachment.remoteKey)) {
    return null
  }

  try {
    val remoteId = SignalServiceAttachmentRemoteId.from(attachment.remoteLocation!!)

    var attachmentWidth = attachment.width
    var attachmentHeight = attachment.height

    if ((attachmentWidth == 0 || attachmentHeight == 0) && MediaUtil.hasVideoThumbnail(context, attachment.uri)) {
      val thumbnail = MediaUtil.getVideoThumbnail(context, attachment.uri, 1000)

      if (thumbnail != null) {
        attachmentWidth = thumbnail.width
        attachmentHeight = thumbnail.height
      }
    }

    return AttachmentPointer.Builder().apply {
      cdnNumber = attachment.cdn.cdnNumber
      contentType = attachment.contentType
      key = Base64.decode(attachment.remoteKey!!).toByteString()
      digest = attachment.remoteDigest?.toByteString()
      size = Util.toIntExact(attachment.size)
      uploadTimestamp = attachment.uploadTimestamp
      width = attachmentWidth.takeIf { it > 0 }
      height = attachmentHeight.takeIf { it > 0 }
      fileName = attachment.fileName
      incrementalMac = attachment.incrementalDigest?.toByteString()
      chunkSize = attachment.incrementalMacChunkSize.takeIf { it > 0 }
      flags = attachment.toFlags()
      caption = attachment.caption
      blurHash = attachment.blurHash?.hash
      clientUuid = attachment.uuid?.toByteArray()?.toByteString()

      if (remoteId is SignalServiceAttachmentRemoteId.V2) {
        cdnId = remoteId.cdnId
      }

      if (remoteId is SignalServiceAttachmentRemoteId.V4) {
        cdnKey = remoteId.cdnKey
      }
    }.build()
  } catch (e: IOException) {
    return null
  } catch (e: ArithmeticException) {
    return null
  }
}

private fun Attachment.toFlags(): Int {
  var flags = 0

  if (this.voiceNote) {
    flags = flags or AttachmentPointer.Flags.VOICE_MESSAGE.value
  }

  if (this.borderless) {
    flags = flags or AttachmentPointer.Flags.BORDERLESS.value
  }

  if (this.videoGif) {
    flags = flags or AttachmentPointer.Flags.GIF.value
  }

  return flags
}
