/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.signal.archive.local.ArchivedFilesReader
import org.signal.core.models.backup.MediaName
import org.signal.core.util.Stopwatch
import org.signal.core.util.androidx.DocumentFileInfo
import org.signal.core.util.androidx.DocumentFileUtil.delete
import org.signal.core.util.androidx.DocumentFileUtil.hasFile
import org.signal.core.util.androidx.DocumentFileUtil.inputStream
import org.signal.core.util.androidx.DocumentFileUtil.listFiles
import org.signal.core.util.androidx.DocumentFileUtil.mkdirp
import org.signal.core.util.androidx.DocumentFileUtil.newFile
import org.signal.core.util.androidx.DocumentFileUtil.outputStream
import org.signal.core.util.androidx.DocumentFileUtil.renameTo
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Provide a domain-specific interface to the root file system backing a local directory based archive.
 */
@Suppress("JoinDeclarationAndAssignment")
class ArchiveFileSystem private constructor(private val context: Context, root: DocumentFile, readOnly: Boolean = false) {

  companion object {
    val TAG = Log.tag(ArchiveFileSystem::class.java)

    const val MAIN_DIRECTORY_NAME = "SignalBackups"
    const val BACKUP_DIRECTORY_PREFIX: String = "signal-backup"
    const val TEMP_BACKUP_DIRECTORY_SUFFIX: String = "tmp"

    /**
     * Attempt to create an [ArchiveFileSystem] from a tree [Uri], creating the necessary directory
     * structure if it does not already exist. Use this when writing backups.
     *
     * Should likely only be called on API29+
     */
    fun fromUri(context: Context, uri: Uri): ArchiveFileSystem? {
      val root = DocumentFile.fromTreeUri(context, uri)

      if (root == null || !root.canWrite()) {
        return null
      }

      return ArchiveFileSystem(context, root, readOnly = false)
    }

    /**
     * Attempt to open an existing [ArchiveFileSystem] from a tree [Uri] without creating any
     * directories or files. Use this when reading/restoring backups.
     *
     * Should likely only be called on API29+
     */
    fun openForRestore(context: Context, uri: Uri): ArchiveFileSystem? {
      val root = DocumentFile.fromTreeUri(context, uri) ?: return null
      if (!root.canRead()) return null
      if (root.findFile(MAIN_DIRECTORY_NAME) == null) return null
      return try {
        ArchiveFileSystem(context, root, readOnly = true)
      } catch (e: IOException) {
        Log.w(TAG, "Unable to open backup directory for restore: $uri", e)
        null
      }
    }

    /**
     * Attempt to create an [ArchiveFileSystem] from a regular [File].
     *
     * Should likely only be called on < API29.
     */
    fun fromFile(context: Context, backupDirectory: File): ArchiveFileSystem {
      return ArchiveFileSystem(context, DocumentFile.fromFile(backupDirectory), readOnly = false)
    }

    fun openInputStream(context: Context, uri: Uri): InputStream? {
      return context.contentResolver.openInputStream(uri)
    }
  }

  private val signalBackups: DocumentFile

  /** File access to shared super-set of archive related files (e.g., media + attachments) */
  val filesFileSystem: FilesFileSystem

  init {
    if (readOnly) {
      signalBackups = root.findFile(MAIN_DIRECTORY_NAME) ?: throw IOException("SignalBackups directory not found in $root")
      val filesDirectory = signalBackups.findFile("files") ?: throw IOException("files directory not found in $signalBackups")
      filesFileSystem = FilesFileSystem(context, filesDirectory, readOnly = true)
    } else {
      signalBackups = root.mkdirp(MAIN_DIRECTORY_NAME) ?: throw IOException("Unable to create main backups directory")
      val filesDirectory = signalBackups.mkdirp("files") ?: throw IOException("Unable to create files directory")
      filesFileSystem = FilesFileSystem(context, filesDirectory)

      val hintFileName = context.getString(R.string.ArchiveFileSystem__select_this_folder_hint_name)
      if (!root.hasFile(hintFileName)) {
        root.createFile("text/plain", hintFileName)
          ?.outputStream(context)
          ?.use { out -> out.write(context.getString(R.string.ArchiveFileSystem__select_this_folder_hint_body).toByteArray()) }
      }
    }
  }

  /**
   * Delete all folders that match the temp/in-progress backup directory naming convention. Used to clean-up
   * previous catastrophic backup failures.
   */
  fun deleteOldTemporaryBackups() {
    for (file in signalBackups.listFiles()) {
      if (file.isDirectory) {
        val name = file.name
        if (name != null && name.startsWith(BACKUP_DIRECTORY_PREFIX) && name.endsWith(TEMP_BACKUP_DIRECTORY_SUFFIX)) {
          if (file.delete()) {
            Log.w(TAG, "Deleted old temporary backup folder")
          } else {
            Log.w(TAG, "Could not delete old temporary backup folder")
          }
        }
      }
    }
  }

  /**
   * Retain up to [limit] most recent backups and delete all others.
   */
  fun deleteOldBackups(limit: Int = 2) {
    Log.i(TAG, "Deleting old backups")

    listSnapshots()
      .drop(limit)
      .forEach { it.file.delete() }
  }

  /**
   * Attempt to create a [SnapshotFileSystem] to represent a single backup snapshot.
   */
  fun createSnapshot(): SnapshotFileSystem? {
    val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    val snapshotDirectoryName = "${BACKUP_DIRECTORY_PREFIX}-$timestamp"

    if (signalBackups.hasFile(snapshotDirectoryName)) {
      Log.w(TAG, "Backup directory already exists!")
      return null
    }

    val workingSnapshotDirectoryName = "$snapshotDirectoryName-$TEMP_BACKUP_DIRECTORY_SUFFIX"
    val workingSnapshotDirectory = signalBackups.createDirectory(workingSnapshotDirectoryName) ?: return null

    return SnapshotFileSystem(context, snapshotDirectoryName, workingSnapshotDirectoryName, workingSnapshotDirectory)
  }

  /**
   * Delete an in-progress snapshot folder after a handled backup failure.
   *
   * @return true if the snapshot was deleted
   */
  fun cleanupSnapshot(snapshotFileSystem: SnapshotFileSystem): Boolean {
    check(snapshotFileSystem.workingSnapshotDirectoryName.isNotEmpty()) { "Cannot call cleanup on unnamed snapshot" }
    return signalBackups.findFile(snapshotFileSystem.workingSnapshotDirectoryName)?.delete() ?: false
  }

  /**
   * List all snapshots found in this directory sorted by creation timestamp, newest first.
   */
  fun listSnapshots(): List<SnapshotInfo> {
    return signalBackups
      .listFiles()
      .asSequence()
      .filter { it.isDirectory }
      .mapNotNull { f -> f.name?.let { it to f } }
      .filter { (name, _) -> name.startsWith(BACKUP_DIRECTORY_PREFIX) }
      .map { (name, file) ->
        val timestamp = name.replace(BACKUP_DIRECTORY_PREFIX, "").toMilliseconds()
        SnapshotInfo(timestamp, name, file)
      }
      .sortedByDescending { it.timestamp }
      .toList()
  }

  /**
   * Clean up unused files in the shared files directory leveraged across all current snapshots. A file
   * is unused if it is not referenced directly by any current snapshots.
   */
  fun deleteUnusedFiles(allFilesProgressListener: AllFilesProgressListener? = null) {
    Log.i(TAG, "Deleting unused files")

    val allFiles: MutableMap<String, DocumentFileInfo> = filesFileSystem.allFiles(allFilesProgressListener).toMutableMap()
    val snapshots: List<SnapshotInfo> = listSnapshots()

    snapshots
      .mapNotNull { SnapshotFileSystem.filesInputStream(context, it.file) }
      .forEach { input ->
        ArchivedFilesReader(input).use { reader ->
          reader.forEach { f -> f.mediaName?.let { allFiles.remove(it) } }
        }
      }

    var deleted = 0
    allFiles
      .values
      .forEach {
        if (it.documentFile.delete()) {
          deleted++
        }
      }

    Log.d(TAG, "Cleanup removed $deleted/${allFiles.size} files")
  }

  /** Useful metadata for a given archive snapshot */
  data class SnapshotInfo(val timestamp: Long, val name: String, val file: DocumentFile)
}

/**
 * Domain specific file system for dealing with individual snapshot data.
 */
class SnapshotFileSystem(private val context: Context, private val snapshotDirectoryName: String, val workingSnapshotDirectoryName: String, private val root: DocumentFile) {
  companion object {
    const val MAIN_NAME = "main"
    const val METADATA_NAME = "metadata"
    const val FILES_NAME = "files"

    /**
     * Get the files metadata file directly for a snapshot.
     */
    fun filesInputStream(context: Context, snapshotDirectory: DocumentFile): InputStream? {
      return snapshotDirectory.findFile(FILES_NAME)?.inputStream(context)
    }
  }

  /**
   * Creates an unnamed snapshot file system for use in importing.
   */
  constructor(context: Context, root: DocumentFile) : this(context, "", "", root)

  fun mainOutputStream(): OutputStream? {
    return root.newFile(MAIN_NAME)?.outputStream(context)
  }

  fun mainInputStream(): InputStream? {
    return root.findFile(MAIN_NAME)?.inputStream(context)
  }

  fun mainLength(): Long? {
    return root.findFile(MAIN_NAME)?.length()
  }

  fun metadataOutputStream(): OutputStream? {
    return root.newFile(METADATA_NAME)?.outputStream(context)
  }

  fun metadataInputStream(): InputStream? {
    return root.findFile(METADATA_NAME)?.inputStream(context)
  }

  fun filesOutputStream(): OutputStream? {
    return root.newFile(FILES_NAME)?.outputStream(context)
  }

  /**
   * Rename the snapshot from the working temporary name to final name.
   */
  fun finalize(): Boolean {
    check(snapshotDirectoryName.isNotEmpty()) { "Cannot call finalize on unnamed snapshot" }
    return root.renameTo(context, snapshotDirectoryName)
  }
}

/**
 * Domain specific file system access for accessing backup files (e.g., attachments, media, etc.).
 */
class FilesFileSystem(private val context: Context, private val root: DocumentFile, readOnly: Boolean = false) {

  companion object {
    private val TAG = Log.tag(FilesFileSystem::class.java)
  }

  private val subFolders: Map<String, DocumentFile>

  init {
    val existingFolders = root.listFiles()
      .mapNotNull { f -> f.name?.let { name -> name to f } }
      .toMap()

    subFolders = if (readOnly) {
      existingFolders
    } else {
      (0..255)
        .map { i -> i.toString(16).padStart(2, '0') }
        .associateWith { name ->
          existingFolders[name] ?: root.createDirectory(name)!!
        }
    }
  }

  /**
   * Enumerate all files in the directory.
   */
  fun allFiles(allFilesProgressListener: AllFilesProgressListener? = null): Map<String, DocumentFileInfo> {
    val stopwatch = Stopwatch("allFiles")

    val asyncResult = runBlocking { allFilesAsync(allFilesProgressListener) }
    stopwatch.split("async")
    stopwatch.stop(TAG)

    return asyncResult
  }

  private suspend fun allFilesAsync(allFilesProgressListener: AllFilesProgressListener? = null, batchCount: Int = Runtime.getRuntime().availableProcessors()): Map<String, DocumentFileInfo> {
    val allFiles = ConcurrentHashMap<String, DocumentFileInfo>()
    val total = subFolders.values.size
    val completed = AtomicInteger(0)
    val chunkSize = (total + batchCount - 1) / batchCount

    Log.d(TAG, "allFilesAsync: $batchCount")

    coroutineScope {
      subFolders.values.chunked(chunkSize).map { chunk ->
        async(Dispatchers.IO) {
          for (subfolder in chunk) {
            val subFiles = subfolder.listFiles(context)
            for (file in subFiles) {
              allFiles[file.name] = file
            }

            allFilesProgressListener?.onProgress(completed.incrementAndGet(), total)
          }
        }
      }.awaitAll()
    }

    return allFiles
  }

  /**
   * Creates a new file for the given [mediaName] and returns the output stream for writing to it. The caller
   * is responsible for determining if the file already exists (see [allFiles]) and deleting it (see [delete]).
   *
   * Calling this with a pre-existing file will likely create a second file with a modified name, but is generally
   * undefined and should be avoided.
   */
  fun fileOutputStream(mediaName: MediaName): OutputStream? {
    val subFileDirectory = subFileDirectoryFor(mediaName)
    val file = subFileDirectory.createFile("application/octet-stream", mediaName.name)
    return file?.outputStream(context)
  }

  /**
   * Delete a file for the given [mediaName] if it exists.
   *
   * @return true if deleted, false if not, null if not found
   */
  fun delete(mediaName: MediaName): Boolean? {
    return subFileDirectoryFor(mediaName).delete(context, mediaName.name)
  }

  private fun subFileDirectoryFor(mediaName: MediaName): DocumentFile {
    return subFolders[mediaName.name.substring(0..1)]!!
  }
}

private fun String.toMilliseconds(): Long {
  val parts: List<String> = split("-").dropLastWhile { it.isEmpty() }

  if (parts.size == 7) {
    try {
      val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
      calendar[Calendar.YEAR] = parts[1].toInt()
      calendar[Calendar.MONTH] = parts[2].toInt() - 1
      calendar[Calendar.DAY_OF_MONTH] = parts[3].toInt()
      calendar[Calendar.HOUR_OF_DAY] = parts[4].toInt()
      calendar[Calendar.MINUTE] = parts[5].toInt()
      calendar[Calendar.SECOND] = parts[6].toInt()
      calendar[Calendar.MILLISECOND] = 0

      return calendar.timeInMillis
    } catch (e: NumberFormatException) {
      Log.w(ArchiveFileSystem.TAG, "Unable to parse timestamp from file name", e)
    }
  }

  return -1
}

fun interface AllFilesProgressListener {
  fun onProgress(completed: Int, total: Int)
}
