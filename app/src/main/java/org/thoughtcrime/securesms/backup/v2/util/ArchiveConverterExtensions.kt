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
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.Optional
import org.thoughtcrime.securesms.backup.v2.proto.AvatarColor as RemoteAvatarColor

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
  uuid: ByteString? = null,
  quote: Boolean = false
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
      uploadTimestamp = this.attachmentLocator.uploadTimestamp?.clampToValidBackupRange() ?: 0,
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
      quote = quote,
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
      archiveMediaId = importState.mediaRootBackupKey.deriveMediaId(MediaName(this.backupLocator.mediaName)).encode(),
      archiveThumbnailMediaId = importState.mediaRootBackupKey.deriveMediaId(MediaName.forThumbnailFromMediaName(this.backupLocator.mediaName)).encode(),
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
      quote = quote,
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
  builder.incrementalMac = this.incrementalDigest?.takeIf { it.isNotEmpty() && this.incrementalMacChunkSize > 0 }?.toByteString()
  builder.incrementalMacChunkSize = this.incrementalMacChunkSize.takeIf { it > 0 && builder.incrementalMac != null }
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
    uploadTimestamp = this.uploadTimestamp.takeIf { it > 0 }?.clampToValidBackupRange(),
    key = Base64.decode(remoteKey).toByteString(),
    size = this.size.toInt(),
    digest = this.remoteDigest.toByteString()
  )
  return builder.build()
}

fun Long.clampToValidBackupRange(): Long {
  return this.coerceIn(0, 8640000000000000)
}

fun AvatarColor.toRemote(): RemoteAvatarColor {
  return when (this) {
    AvatarColor.A100 -> RemoteAvatarColor.A100
    AvatarColor.A110 -> RemoteAvatarColor.A110
    AvatarColor.A120 -> RemoteAvatarColor.A120
    AvatarColor.A130 -> RemoteAvatarColor.A130
    AvatarColor.A140 -> RemoteAvatarColor.A140
    AvatarColor.A150 -> RemoteAvatarColor.A150
    AvatarColor.A160 -> RemoteAvatarColor.A160
    AvatarColor.A170 -> RemoteAvatarColor.A170
    AvatarColor.A180 -> RemoteAvatarColor.A180
    AvatarColor.A190 -> RemoteAvatarColor.A190
    AvatarColor.A200 -> RemoteAvatarColor.A200
    AvatarColor.A210 -> RemoteAvatarColor.A210
    AvatarColor.UNKNOWN -> RemoteAvatarColor.A100
    AvatarColor.ON_SURFACE_VARIANT -> RemoteAvatarColor.A100
  }
}

fun RemoteAvatarColor.toLocal(): AvatarColor {
  return when (this) {
    RemoteAvatarColor.A100 -> AvatarColor.A100
    RemoteAvatarColor.A110 -> AvatarColor.A110
    RemoteAvatarColor.A120 -> AvatarColor.A120
    RemoteAvatarColor.A130 -> AvatarColor.A130
    RemoteAvatarColor.A140 -> AvatarColor.A140
    RemoteAvatarColor.A150 -> AvatarColor.A150
    RemoteAvatarColor.A160 -> AvatarColor.A160
    RemoteAvatarColor.A170 -> AvatarColor.A170
    RemoteAvatarColor.A180 -> AvatarColor.A180
    RemoteAvatarColor.A190 -> AvatarColor.A190
    RemoteAvatarColor.A200 -> AvatarColor.A200
    RemoteAvatarColor.A210 -> AvatarColor.A210
  }
}
