/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.text.TextUtils
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.InvalidAttachmentException
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.BackupRepository.getThumbnailMediaName
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.io.IOException
import java.util.Optional

/**
 * Creates a [SignalServiceAttachmentPointer] for the archived attachment of the given [DatabaseAttachment].
 */
@Throws(InvalidAttachmentException::class)
fun DatabaseAttachment.createArchiveAttachmentPointer(useArchiveCdn: Boolean): SignalServiceAttachmentPointer {
  if (remoteKey.isNullOrBlank()) {
    throw InvalidAttachmentException("empty encrypted key")
  }

  if (remoteDigest == null) {
    throw InvalidAttachmentException("no digest")
  }

  return try {
    val (remoteId, cdnNumber) = if (useArchiveCdn) {
      val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()
      val backupDirectories = BackupRepository.getCdnBackupDirectories().successOrThrow()

      val id = SignalServiceAttachmentRemoteId.Backup(
        backupDir = backupDirectories.backupDir,
        mediaDir = backupDirectories.mediaDir,
        mediaId = backupKey.deriveMediaId(MediaName(archiveMediaName!!)).encode()
      )

      id to archiveCdn
    } else {
      if (remoteLocation.isNullOrEmpty()) {
        throw InvalidAttachmentException("empty content id")
      }

      SignalServiceAttachmentRemoteId.from(remoteLocation) to cdn.cdnNumber
    }

    val key = Base64.decode(remoteKey)

    SignalServiceAttachmentPointer(
      cdnNumber = cdnNumber,
      remoteId = remoteId,
      contentType = null,
      key = key,
      size = Optional.of(Util.toIntExact(size)),
      preview = Optional.empty(),
      width = 0,
      height = 0,
      digest = Optional.ofNullable(remoteDigest),
      incrementalDigest = Optional.ofNullable(getIncrementalDigest()),
      incrementalMacChunkSize = incrementalMacChunkSize,
      fileName = Optional.ofNullable(fileName),
      voiceNote = voiceNote,
      isBorderless = borderless,
      isGif = videoGif,
      caption = Optional.empty(),
      blurHash = Optional.ofNullable(blurHash).map { it.hash },
      uploadTimestamp = uploadTimestamp,
      uuid = uuid
    )
  } catch (e: IOException) {
    throw InvalidAttachmentException(e)
  } catch (e: ArithmeticException) {
    throw InvalidAttachmentException(e)
  }
}

/**
 * Creates a [SignalServiceAttachmentPointer] for an archived thumbnail of the given [DatabaseAttachment].
 */
@Throws(InvalidAttachmentException::class)
fun DatabaseAttachment.createArchiveThumbnailPointer(): SignalServiceAttachmentPointer {
  if (TextUtils.isEmpty(remoteKey)) {
    throw InvalidAttachmentException("empty encrypted key")
  }

  val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()
  val backupDirectories = BackupRepository.getCdnBackupDirectories().successOrThrow()
  return try {
    val key = backupKey.deriveThumbnailTransitKey(getThumbnailMediaName())
    val mediaId = backupKey.deriveMediaId(getThumbnailMediaName()).encode()
    SignalServiceAttachmentPointer(
      cdnNumber = archiveCdn,
      remoteId = SignalServiceAttachmentRemoteId.Backup(
        backupDir = backupDirectories.backupDir,
        mediaDir = backupDirectories.mediaDir,
        mediaId = mediaId
      ),
      contentType = null,
      key = key,
      size = Optional.empty(),
      preview = Optional.empty(),
      width = 0,
      height = 0,
      digest = Optional.empty(),
      incrementalDigest = Optional.empty(),
      incrementalMacChunkSize = incrementalMacChunkSize,
      fileName = Optional.empty(),
      voiceNote = voiceNote,
      isBorderless = borderless,
      isGif = videoGif,
      caption = Optional.empty(),
      blurHash = Optional.ofNullable(blurHash).map { it.hash },
      uploadTimestamp = uploadTimestamp,
      uuid = uuid
    )
  } catch (e: IOException) {
    throw InvalidAttachmentException(e)
  } catch (e: ArithmeticException) {
    throw InvalidAttachmentException(e)
  }
}
