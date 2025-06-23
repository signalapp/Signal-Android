/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.text.TextUtils
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.InvalidAttachmentException
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.io.IOException
import java.util.Optional

object DatabaseAttachmentArchiveUtil {
  @JvmStatic
  fun requireMediaName(attachment: DatabaseAttachment): MediaName {
    require(isDigestValidated(attachment))
    return MediaName.fromDigest(attachment.remoteDigest!!)
  }

  /**
   * For java, since it struggles with value classes.
   */
  @JvmStatic
  fun requireMediaNameAsString(attachment: DatabaseAttachment): String {
    require(isDigestValidated(attachment))
    return MediaName.fromDigest(attachment.remoteDigest!!).name
  }

  @JvmStatic
  fun getMediaName(attachment: DatabaseAttachment): MediaName? {
    return if (isDigestValidated(attachment)) {
      attachment.remoteDigest?.let { MediaName.fromDigest(it) }
    } else {
      null
    }
  }

  @JvmStatic
  fun requireThumbnailMediaName(attachment: DatabaseAttachment): MediaName {
    require(isDigestValidated(attachment))
    return MediaName.fromDigestForThumbnail(attachment.remoteDigest!!)
  }

  private fun isDigestValidated(attachment: DatabaseAttachment): Boolean {
    return when (attachment.transferState) {
      AttachmentTable.TRANSFER_PROGRESS_DONE,
      AttachmentTable.TRANSFER_NEEDS_RESTORE,
      AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS,
      AttachmentTable.TRANSFER_RESTORE_OFFLOADED -> true

      else -> false
    }
  }
}

fun DatabaseAttachment.requireMediaName(): MediaName {
  return DatabaseAttachmentArchiveUtil.requireMediaName(this)
}

fun DatabaseAttachment.getMediaName(): MediaName? {
  return DatabaseAttachmentArchiveUtil.getMediaName(this)
}

fun DatabaseAttachment.requireThumbnailMediaName(): MediaName {
  return DatabaseAttachmentArchiveUtil.requireThumbnailMediaName(this)
}

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
      val mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey
      val mediaCdnPath = BackupRepository.getArchivedMediaCdnPath().successOrThrow()

      val id = SignalServiceAttachmentRemoteId.Backup(
        mediaCdnPath = mediaCdnPath,
        mediaId = this.requireMediaName().toMediaId(mediaRootBackupKey).encode()
      )

      id to (archiveCdn ?: RemoteConfig.backupFallbackArchiveCdn)
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

  val mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey
  val mediaCdnPath = BackupRepository.getArchivedMediaCdnPath().successOrThrow()
  return try {
    val key = mediaRootBackupKey.deriveThumbnailTransitKey(requireThumbnailMediaName())
    val mediaId = mediaRootBackupKey.deriveMediaId(requireThumbnailMediaName()).encode()
    SignalServiceAttachmentPointer(
      cdnNumber = archiveCdn ?: RemoteConfig.backupFallbackArchiveCdn,
      remoteId = SignalServiceAttachmentRemoteId.Backup(
        mediaCdnPath = mediaCdnPath,
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
