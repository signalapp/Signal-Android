/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.local

import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Base64
import org.signal.core.util.Stopwatch
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.LocalBackupV2Event
import org.thoughtcrime.securesms.backup.v2.local.proto.FilesFrame
import org.thoughtcrime.securesms.backup.v2.local.proto.Metadata
import org.thoughtcrime.securesms.database.AttachmentTable
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections

typealias ArchiveResult = org.signal.core.util.Result<Unit, LocalArchiver.FailureCause>

/**
 * Handle importing and exporting folder-based archives using backupv2 format.
 */
object LocalArchiver {

  private val TAG = Log.tag(LocalArchiver::class)
  private const val VERSION = 1

  /**
   * Export archive to the provided [snapshotFileSystem] and store new files in [filesFileSystem].
   */
  fun export(snapshotFileSystem: SnapshotFileSystem, filesFileSystem: FilesFileSystem, stopwatch: Stopwatch): ArchiveResult {
    Log.i(TAG, "Starting export")

    var metadataStream: OutputStream? = null
    var mainStream: OutputStream? = null
    var filesStream: OutputStream? = null

    try {
      metadataStream = snapshotFileSystem.metadataOutputStream() ?: return ArchiveResult.failure(FailureCause.METADATA_STREAM)
      metadataStream.use { it.write(Metadata(VERSION).encode()) }
      stopwatch.split("metadata")

      mainStream = snapshotFileSystem.mainOutputStream() ?: return ArchiveResult.failure(FailureCause.MAIN_STREAM)

      Log.i(TAG, "Listing all current files")
      val allFiles = filesFileSystem.allFiles()
      stopwatch.split("files-list")

      val mediaNames: MutableSet<MediaName> = Collections.synchronizedSet(HashSet())

      Log.i(TAG, "Starting frame export")
      BackupRepository.localExport(mainStream, LocalExportProgressListener()) { attachment, source ->
        val mediaName = MediaName.fromDigest(attachment.remoteDigest)

        mediaNames.add(mediaName)

        if (allFiles[mediaName.name]?.size != attachment.cipherLength) {
          if (allFiles.containsKey(mediaName.name)) {
            filesFileSystem.delete(mediaName)
          }

          source()?.use { sourceStream ->
            val iv = attachment.remoteIv
            val combinedKey = Base64.decode(attachment.remoteKey)
            val destination: OutputStream? = filesFileSystem.fileOutputStream(mediaName)

            if (destination == null) {
              Log.w(TAG, "Unable to create output file for attachment")
              // todo [local-backup] should we abort here?
            } else {
              // todo [local-backup] but deal with attachment disappearing/deleted by normal app use
              try {
                PaddingInputStream(sourceStream, attachment.size).use { input ->
                  AttachmentCipherOutputStream(combinedKey, iv, destination).use { output ->
                    StreamUtil.copy(input, output)
                  }
                }
              } catch (e: IOException) {
                Log.w(TAG, "Unable to save attachment", e)
                // todo [local-backup] should we abort here?
              }
            }
          }
        }
      }
      stopwatch.split("frames-and-files")

      filesStream = snapshotFileSystem.filesOutputStream() ?: return ArchiveResult.failure(FailureCause.FILES_STREAM)
      ArchivedFilesWriter(filesStream).use { writer ->
        mediaNames.forEach { name -> writer.write(FilesFrame(mediaName = name.name)) }
      }
      stopwatch.split("files-metadata")
    } finally {
      metadataStream?.close()
      mainStream?.close()
      filesStream?.close()
    }

    return ArchiveResult.success(Unit)
  }

  /**
   * Import archive data from a folder on the system. Does not restore attachments.
   */
  fun import(snapshotFileSystem: SnapshotFileSystem, selfData: BackupRepository.SelfData): ArchiveResult {
    var metadataStream: InputStream? = null

    try {
      metadataStream = snapshotFileSystem.metadataInputStream() ?: return ArchiveResult.failure(FailureCause.METADATA_STREAM)

      val mainStreamLength = snapshotFileSystem.mainLength() ?: return ArchiveResult.failure(FailureCause.MAIN_STREAM)

      BackupRepository.localImport(
        mainStreamFactory = { snapshotFileSystem.mainInputStream()!! },
        mainStreamLength = mainStreamLength,
        selfData = selfData
      )
    } finally {
      metadataStream?.close()
    }

    return ArchiveResult.success(Unit)
  }

  private val AttachmentTable.LocalArchivableAttachment.cipherLength: Long
    get() = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(size))

  enum class FailureCause {
    METADATA_STREAM, MAIN_STREAM, FILES_STREAM
  }

  private class LocalExportProgressListener : BackupRepository.ExportProgressListener {
    private var lastAttachmentUpdate: Long = 0

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

    override fun onMessage() {
      EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_MESSAGE))
    }

    override fun onAttachment(currentProgress: Long, totalCount: Long) {
      if (lastAttachmentUpdate > System.currentTimeMillis() || lastAttachmentUpdate + 1000 < System.currentTimeMillis() || currentProgress >= totalCount) {
        EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_ATTACHMENT, currentProgress, totalCount))
        lastAttachmentUpdate = System.currentTimeMillis()
      }
    }
  }
}
