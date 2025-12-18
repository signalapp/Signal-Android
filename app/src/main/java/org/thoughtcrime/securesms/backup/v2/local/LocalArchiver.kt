/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.local

import okio.ByteString.Companion.toByteString
import org.greenrobot.eventbus.EventBus
import org.signal.core.models.backup.BackupId
import org.signal.core.models.backup.MediaName
import org.signal.core.util.Stopwatch
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.readFully
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.LocalBackupV2Event
import org.thoughtcrime.securesms.backup.v2.local.proto.FilesFrame
import org.thoughtcrime.securesms.backup.v2.local.proto.Metadata
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

typealias ArchiveResult = org.signal.core.util.Result<LocalArchiver.ArchiveSuccess, LocalArchiver.ArchiveFailure>
typealias RestoreResult = org.signal.core.util.Result<LocalArchiver.RestoreSuccess, LocalArchiver.RestoreFailure>

/**
 * Handle importing and exporting folder-based archives using backupv2 format.
 */
object LocalArchiver {

  private val TAG = Log.tag(LocalArchiver::class)
  private const val VERSION = 1

  private const val MAX_CREATE_FAILURES = 10

  /**
   * Export archive to the provided [snapshotFileSystem] and store new files in [filesFileSystem].
   */
  fun export(snapshotFileSystem: SnapshotFileSystem, filesFileSystem: FilesFileSystem, stopwatch: Stopwatch, cancellationSignal: () -> Boolean = { false }): ArchiveResult {
    Log.i(TAG, "Starting export")

    var metadataStream: OutputStream? = null
    var mainStream: OutputStream? = null
    var filesStream: OutputStream? = null

    val createFailures: MutableSet<AttachmentId> = Collections.synchronizedSet(HashSet())
    val readWriteFailures: MutableSet<AttachmentId> = Collections.synchronizedSet(HashSet())

    try {
      metadataStream = snapshotFileSystem.metadataOutputStream() ?: return ArchiveResult.failure(ArchiveFailure.MetadataStream)
      metadataStream.use { it.write(Metadata(version = VERSION, backupId = getEncryptedBackupId()).encode()) }
      stopwatch.split("metadata")

      mainStream = snapshotFileSystem.mainOutputStream() ?: return ArchiveResult.failure(ArchiveFailure.MainStream)

      Log.i(TAG, "Listing all current files")
      val allFiles = filesFileSystem.allFiles()
      stopwatch.split("files-list")

      val mediaNames: MutableSet<MediaName> = Collections.synchronizedSet(HashSet())

      Log.i(TAG, "Starting frame export")
      BackupRepository.exportForLocalBackup(mainStream, LocalExportProgressListener(), cancellationSignal) { attachment, source ->
        if (cancellationSignal() || createFailures.size > MAX_CREATE_FAILURES) {
          return@exportForLocalBackup
        }

        val mediaName = MediaName.forLocalBackupFilename(attachment.plaintextHash, attachment.localBackupKey.key)

        mediaNames.add(mediaName)

        if (allFiles[mediaName.name]?.size != attachment.cipherLength) {
          if (allFiles.containsKey(mediaName.name)) {
            filesFileSystem.delete(mediaName)
          }

          source()?.use { sourceStream ->
            val destination: OutputStream? = filesFileSystem.fileOutputStream(mediaName)

            if (destination == null) {
              Log.w(TAG, "Unable to create output file for ${attachment.attachmentId}")
              createFailures.add(attachment.attachmentId)
            } else {
              try {
                PaddingInputStream(sourceStream, attachment.size).use { input ->
                  AttachmentCipherOutputStream(attachment.localBackupKey.key, null, destination).use { output ->
                    StreamUtil.copy(input, output, false, false)
                  }
                }
              } catch (e: IOException) {
                Log.w(TAG, "Unable to save ${attachment.attachmentId}", e)
                readWriteFailures.add(attachment.attachmentId)
              }
            }
          }
        }
      }
      stopwatch.split("frames-and-files")

      if (createFailures.size > MAX_CREATE_FAILURES) {
        return ArchiveResult.failure(ArchiveFailure.TooManyCreateFailures(createFailures))
      }

      filesStream = snapshotFileSystem.filesOutputStream() ?: return ArchiveResult.failure(ArchiveFailure.FilesStream)
      ArchivedFilesWriter(filesStream).use { writer ->
        mediaNames.forEach { name -> writer.write(FilesFrame(mediaName = name.name)) }
      }
      stopwatch.split("files-metadata")
    } finally {
      metadataStream?.close()
      mainStream?.close()
      filesStream?.close()
    }

    if (cancellationSignal()) {
      return ArchiveResult.failure(ArchiveFailure.Cancelled)
    }

    return if (createFailures.isNotEmpty() || readWriteFailures.isNotEmpty()) {
      ArchiveResult.success(ArchiveSuccess.PartialSuccess(createFailures, readWriteFailures))
    } else {
      ArchiveResult.success(ArchiveSuccess.FullSuccess)
    }
  }

  private fun getEncryptedBackupId(): Metadata.EncryptedBackupId {
    val metadataKey = SignalStore.backup.messageBackupKey.deriveLocalBackupMetadataKey()
    val iv = Util.getSecretBytes(12)
    val backupId = SignalStore.backup.messageBackupKey.deriveBackupId(SignalStore.account.requireAci())

    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(metadataKey, "AES"), IvParameterSpec(iv))
    val cipherText = cipher.doFinal(backupId.value)

    return Metadata.EncryptedBackupId(iv = iv.toByteString(), encryptedId = cipherText.toByteString())
  }

  /**
   * Import archive data from a folder on the system. Does not restore attachments.
   */
  fun import(snapshotFileSystem: SnapshotFileSystem, selfData: BackupRepository.SelfData): RestoreResult {
    var metadataStream: InputStream? = null

    try {
      metadataStream = snapshotFileSystem.metadataInputStream() ?: return RestoreResult.failure(RestoreFailure.MetadataStream)
      val metadata = Metadata.ADAPTER.decode(metadataStream.readFully(autoClose = false))

      if (metadata.version > VERSION) {
        Log.w(TAG, "Local backup version does not match, bailing supported: $VERSION backup: ${metadata.version}")
        return RestoreResult.failure(RestoreFailure.VersionMismatch(metadata.version, VERSION))
      }

      if (metadata.backupId == null) {
        Log.w(TAG, "Local backup metadata missing encrypted backup id")
        return RestoreResult.failure(RestoreFailure.BackupIdMissing)
      }

      val backupId = decryptBackupId(metadata.backupId)

      if (!backupId.value.contentEquals(SignalStore.backup.messageBackupKey.deriveBackupId(SignalStore.account.requireAci()).value)) {
        Log.w(TAG, "Local backup metadata backup id does not match derived backup id, likely from another account")
        return RestoreResult.failure(RestoreFailure.BackupIdMismatch)
      }

      val mainStreamLength = snapshotFileSystem.mainLength() ?: return ArchiveResult.failure(RestoreFailure.MainStream)

      BackupRepository.importLocal(
        mainStreamFactory = { snapshotFileSystem.mainInputStream()!! },
        mainStreamLength = mainStreamLength,
        selfData = selfData
      )
    } finally {
      metadataStream?.close()
    }

    return RestoreResult.success(RestoreSuccess.FullSuccess)
  }

  private fun decryptBackupId(encryptedBackupId: Metadata.EncryptedBackupId): BackupId {
    val metadataKey = SignalStore.backup.messageBackupKey.deriveLocalBackupMetadataKey()
    val iv = encryptedBackupId.iv.toByteArray()
    val backupIdCipher = encryptedBackupId.encryptedId.toByteArray()

    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(metadataKey, "AES"), IvParameterSpec(iv))
    val plaintext = cipher.doFinal(backupIdCipher)

    return BackupId(plaintext)
  }

  private val AttachmentTable.LocalArchivableAttachment.cipherLength: Long
    get() = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(size))

  sealed interface ArchiveSuccess {
    data object FullSuccess : ArchiveSuccess
    data class PartialSuccess(val createFailures: Set<AttachmentId>, val readWriteFailures: Set<AttachmentId>) : ArchiveSuccess
  }

  sealed interface ArchiveFailure {
    data object MetadataStream : ArchiveFailure
    data object MainStream : ArchiveFailure
    data object FilesStream : ArchiveFailure
    data object Cancelled : ArchiveFailure
    data class TooManyCreateFailures(val attachmentId: Set<AttachmentId>) : ArchiveFailure
  }

  sealed interface RestoreSuccess {
    data object FullSuccess : RestoreSuccess
  }

  sealed interface RestoreFailure {
    data object MetadataStream : RestoreFailure
    data object MainStream : RestoreFailure
    data object Cancelled : RestoreFailure
    data object BackupIdMissing : RestoreFailure
    data object BackupIdMismatch : RestoreFailure
    data class VersionMismatch(val backupVersion: Int, val supportedVersion: Int) : RestoreFailure
  }

  private class LocalExportProgressListener : BackupRepository.ExportProgressListener {
    private var lastVerboseUpdate: Long = 0

    override fun onAccount() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_ACCOUNT))
    }

    override fun onRecipient() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_RECIPIENT))
    }

    override fun onThread() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_THREAD))
    }

    override fun onCall() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_CALL))
    }

    override fun onSticker() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_STICKER))
    }

    override fun onNotificationProfile() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.NOTIFICATION_PROFILE))
    }

    override fun onChatFolder() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.CHAT_FOLDER))
    }

    override fun onMessage(currentProgress: Long, approximateCount: Long) {
      if (lastVerboseUpdate > System.currentTimeMillis() || lastVerboseUpdate + 1000 < System.currentTimeMillis() || currentProgress >= approximateCount) {
        EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_MESSAGE, currentProgress, approximateCount))
        lastVerboseUpdate = System.currentTimeMillis()
      }
    }

    override fun onAttachment(currentProgress: Long, totalCount: Long) {
      if (lastVerboseUpdate > System.currentTimeMillis() || lastVerboseUpdate + 1000 < System.currentTimeMillis() || currentProgress >= totalCount) {
        EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_ATTACHMENT, currentProgress, totalCount))
        lastVerboseUpdate = System.currentTimeMillis()
      }
    }
  }
}
