/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.util

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.attachments.TombstoneAttachment
import org.thoughtcrime.securesms.backup.v2.BackupRepository.getMediaName
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.Optional

/**
 * Converts a [FilePointer] to a local [Attachment] object for inserting into the database.
 */
fun FilePointer?.toLocalAttachment(
  importState: ImportState,
  voiceNote: Boolean = false,
  borderless: Boolean = false,
  gif: Boolean = false,
  wasDownloaded: Boolean = false,
  stickerLocator: StickerLocator? = null,
  contentType: String? = this?.contentType,
  fileName: String? = this?.fileName,
  uuid: ByteString? = null
): Attachment? {
  if (this == null) return null

  if (this.attachmentLocator != null) {
    val signalAttachmentPointer = SignalServiceAttachmentPointer(
      cdnNumber = this.attachmentLocator.cdnNumber,
      remoteId = SignalServiceAttachmentRemoteId.from(attachmentLocator.cdnKey),
      contentType = contentType,
      key = this.attachmentLocator.key.toByteArray(),
      size = Optional.ofNullable(attachmentLocator.size),
      preview = Optional.empty(),
      width = this.width ?: 0,
      height = this.height ?: 0,
      digest = Optional.ofNullable(this.attachmentLocator.digest.toByteArray()),
      incrementalDigest = Optional.ofNullable(this.incrementalMac?.toByteArray()),
      incrementalMacChunkSize = this.incrementalMacChunkSize ?: 0,
      fileName = Optional.ofNullable(fileName),
      voiceNote = voiceNote,
      isBorderless = borderless,
      isGif = gif,
      caption = Optional.ofNullable(this.caption),
      blurHash = Optional.ofNullable(this.blurHash),
      uploadTimestamp = this.attachmentLocator.uploadTimestamp,
      uuid = UuidUtil.fromByteStringOrNull(uuid)
    )
    return PointerAttachment.forPointer(
      pointer = Optional.of(signalAttachmentPointer),
      stickerLocator = stickerLocator,
      transferState = if (wasDownloaded) AttachmentTable.TRANSFER_NEEDS_RESTORE else AttachmentTable.TRANSFER_PROGRESS_PENDING
    ).orNull()
  } else if (this.invalidAttachmentLocator != null) {
    return TombstoneAttachment(
      contentType = contentType,
      incrementalMac = this.incrementalMac?.toByteArray(),
      incrementalMacChunkSize = this.incrementalMacChunkSize,
      width = this.width,
      height = this.height,
      caption = this.caption,
      fileName = this.fileName,
      blurHash = this.blurHash,
      voiceNote = voiceNote,
      borderless = borderless,
      gif = gif,
      quote = false,
      stickerLocator = stickerLocator,
      uuid = UuidUtil.fromByteStringOrNull(uuid)
    )
  } else if (this.backupLocator != null) {
    return ArchivedAttachment(
      contentType = contentType,
      size = this.backupLocator.size.toLong(),
      cdn = this.backupLocator.transitCdnNumber ?: Cdn.CDN_0.cdnNumber,
      key = this.backupLocator.key.toByteArray(),
      iv = null,
      cdnKey = this.backupLocator.transitCdnKey,
      archiveCdn = this.backupLocator.cdnNumber,
      archiveMediaName = this.backupLocator.mediaName,
      archiveMediaId = importState.backupKey.deriveMediaId(MediaName(this.backupLocator.mediaName)).encode(),
      archiveThumbnailMediaId = importState.backupKey.deriveMediaId(MediaName.forThumbnailFromMediaName(this.backupLocator.mediaName)).encode(),
      digest = this.backupLocator.digest.toByteArray(),
      incrementalMac = this.incrementalMac?.toByteArray(),
      incrementalMacChunkSize = this.incrementalMacChunkSize,
      width = this.width,
      height = this.height,
      caption = this.caption,
      blurHash = this.blurHash,
      voiceNote = voiceNote,
      borderless = borderless,
      gif = gif,
      quote = false,
      stickerLocator = stickerLocator,
      uuid = UuidUtil.fromByteStringOrNull(uuid),
      fileName = fileName
    )
  }
  return null
}

/**
 * @param mediaArchiveEnabled True if this user has enable media backup, otherwise false.
 */
fun DatabaseAttachment.toRemoteFilePointer(mediaArchiveEnabled: Boolean, contentTypeOverride: String? = null): FilePointer {
  val builder = FilePointer.Builder()
  builder.contentType = contentTypeOverride ?: this.contentType?.takeUnless { it.isBlank() }
  builder.incrementalMac = this.incrementalDigest?.toByteString()
  builder.incrementalMacChunkSize = this.incrementalMacChunkSize.takeIf { it > 0 }
  builder.fileName = this.fileName
  builder.width = this.width.takeIf { it > 0 }
  builder.height = this.height.takeIf { it > 0 }
  builder.caption = this.caption
  builder.blurHash = this.blurHash?.hash

  if (this.remoteKey.isNullOrBlank() || this.remoteDigest == null || this.size == 0L) {
    builder.invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator()
    return builder.build()
  }

  if (this.transferState == AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE && this.archiveTransferState != AttachmentTable.ArchiveTransferState.FINISHED) {
    builder.invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator()
    return builder.build()
  }

  val pending = this.archiveTransferState != AttachmentTable.ArchiveTransferState.FINISHED && (this.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE && this.transferState != AttachmentTable.TRANSFER_RESTORE_OFFLOADED)

  if (mediaArchiveEnabled && !pending) {
    builder.backupLocator = FilePointer.BackupLocator(
      mediaName = this.archiveMediaName ?: this.getMediaName().toString(),
      cdnNumber = if (this.archiveMediaName != null) this.archiveCdn else Cdn.CDN_3.cdnNumber, // TODO [backup]: Update when new proto with optional cdn is landed
      key = Base64.decode(remoteKey).toByteString(),
      size = this.size.toInt(),
      digest = this.remoteDigest.toByteString(),
      transitCdnNumber = this.cdn.cdnNumber.takeIf { this.remoteLocation != null },
      transitCdnKey = this.remoteLocation
    )
    return builder.build()
  }

  if (this.remoteLocation.isNullOrBlank()) {
    builder.invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator()
    return builder.build()
  }

  builder.attachmentLocator = FilePointer.AttachmentLocator(
    cdnKey = this.remoteLocation,
    cdnNumber = this.cdn.cdnNumber,
    uploadTimestamp = this.uploadTimestamp,
    key = Base64.decode(remoteKey).toByteString(),
    size = this.size.toInt(),
    digest = this.remoteDigest.toByteString()
  )
  return builder.build()
}
