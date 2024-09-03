/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.androidx

import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.isTreeDocumentFile
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Duration.Companion.seconds

/**
 * Collection of helper and optimizized operations for working with [DocumentFile]s.
 */
object DocumentFileUtil {

  private val TAG = Log.tag(DocumentFileUtil::class)

  private val FILE_PROJECTION = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)
  private const val FILE_SELECTION = "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?"

  private const val LIST_FILES_SELECTION = "${DocumentsContract.Document.COLUMN_MIME_TYPE} != ?"
  private val LIST_FILES_SELECTION_ARGS = arrayOf(DocumentsContract.Document.MIME_TYPE_DIR)

  private const val MAX_STORAGE_ATTEMPTS: Int = 5
  private val WAIT_FOR_SCOPED_STORAGE: LongArray = longArrayOf(0, 2.seconds.inWholeMilliseconds, 10.seconds.inWholeMilliseconds, 20.seconds.inWholeMilliseconds, 30.seconds.inWholeMilliseconds)

  /** Returns true if the directory represented by the [DocumentFile] has a child with [name]. */
  fun DocumentFile.hasFile(name: String): Boolean {
    return findFile(name) != null
  }

  /** Returns the [DocumentFile] for a newly created binary file or null if unable or it already exists */
  fun DocumentFile.newFile(name: String): DocumentFile? {
    return if (hasFile(name)) {
      Log.w(TAG, "Attempt to create new file ($name) but it already exists")
      null
    } else {
      createFile("application/octet-stream", name)
    }
  }

  /** Returns a [DocumentFile] for directory by [name], creating it if it doesn't already exist */
  fun DocumentFile.mkdirp(name: String): DocumentFile? {
    return findFile(name) ?: createDirectory(name)
  }

  /** Open an [OutputStream] to the file represented by the [DocumentFile] */
  fun DocumentFile.outputStream(context: Context): OutputStream? {
    return context.contentResolver.openOutputStream(uri)
  }

  /** Open an [InputStream] to the file represented by the [DocumentFile] */
  @JvmStatic
  fun DocumentFile.inputStream(context: Context): InputStream? {
    return context.contentResolver.openInputStream(uri)
  }

  /**
   * Will attempt to find the named [file] in the [root] directory and delete it if found.
   *
   * @return true if found and deleted, false if the file couldn't be deleted, and null if not found
   */
  fun DocumentFile.delete(context: Context, file: String): Boolean? {
    return findFile(context, file)?.documentFile?.delete()
  }

  /**
   * Will attempt to find the name [fileName] in the [root] directory and return useful information if found using
   * a single [Context.getContentResolver] query.
   *
   * Recommend using this over [DocumentFile.findFile] to prevent excess queries for all files and names.
   *
   * If direct queries fail to find the file, will fallback to using [DocumentFile.findFile].
   */
  fun DocumentFile.findFile(context: Context, fileName: String): DocumentFileInfo? {
    val child: List<DocumentFileInfo> = if (isTreeDocumentFile()) {
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))

      try {
        context
          .contentResolver
          .query(childrenUri, FILE_PROJECTION, FILE_SELECTION, arrayOf(fileName), null)
          ?.readToList(predicate = { it.name == fileName }) { cursor ->
            val uri = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.requireString(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
            val displayName = cursor.requireNonNullString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val length = cursor.requireLong(DocumentsContract.Document.COLUMN_SIZE)

            DocumentFileInfo(DocumentFile.fromSingleUri(context, uri)!!, displayName, length)
          } ?: emptyList()
      } catch (e: Exception) {
        Log.d(TAG, "Unable to find file directly on ${javaClass.simpleName}, falling back to OS", e)
        emptyList()
      }
    } else {
      emptyList()
    }

    return if (child.size == 1) {
      child[0]
    } else {
      Log.w(TAG, "Did not find single file, found (${child.size}), falling back to OS")
      this.findFile(fileName)?.let { DocumentFileInfo(it, it.name!!, it.length()) }
    }
  }

  /**
   * List file names and sizes in the [DocumentFile] by directly querying the content resolver ourselves. The system
   * implementation makes a separate query for each name and length method call and gets expensive over 1000's of files.
   *
   * Will fallback to the provided document file's implementation of [DocumentFile.listFiles] if unable to do it directly.
   */
  fun DocumentFile.listFiles(context: Context): List<DocumentFileInfo> {
    if (isTreeDocumentFile()) {
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))

      try {
        val results = context
          .contentResolver
          .query(childrenUri, FILE_PROJECTION, LIST_FILES_SELECTION, LIST_FILES_SELECTION_ARGS, null)
          ?.use { cursor ->
            val results = ArrayList<DocumentFileInfo>(cursor.count)
            while (cursor.moveToNext()) {
              val uri = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.requireString(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
              val displayName = cursor.requireString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
              val length = cursor.requireLong(DocumentsContract.Document.COLUMN_SIZE)
              if (displayName != null) {
                results.add(DocumentFileInfo(DocumentFile.fromSingleUri(context, uri)!!, displayName, length))
              }
            }

            results
          }

        if (results != null) {
          return results
        } else {
          Log.w(TAG, "Content provider returned null for query on ${javaClass.simpleName}, falling back to OS")
        }
      } catch (e: Exception) {
        Log.d(TAG, "Unable to query files directly on ${javaClass.simpleName}, falling back to OS", e)
      }
    }

    return listFiles()
      .asSequence()
      .filter { it.isFile }
      .mapNotNull { file -> file.name?.let { DocumentFileInfo(file, it, file.length()) } }
      .toList()
  }

  /**
   * System implementation swallows the exception and we are having problems with the rename. This inlines the
   * same call and logs the exception. Note this implementation does not update the passed in document file like
   * the system implementation. Do not use the provided document file after calling this method.
   *
   * @return true if rename successful
   */
  @JvmStatic
  fun DocumentFile.renameTo(context: Context, displayName: String): Boolean {
    if (isTreeDocumentFile()) {
      Log.d(TAG, "Renaming document directly")
      try {
        val result = DocumentsContract.renameDocument(context.contentResolver, uri, displayName)
        return result != null
      } catch (e: Exception) {
        Log.w(TAG, "Unable to rename document file, falling back to OS", e)
        return renameTo(displayName)
      }
    } else {
      return renameTo(displayName)
    }
  }

  /**
   * Historically, we've seen issues with [DocumentFile] operations not working on the first try. This
   * retry loop will retry those operations with a varying backoff in attempt to make them work.
   */
  @JvmStatic
  fun <T> retryDocumentFileOperation(operation: DocumentFileOperation<T>): OperationResult {
    var attempts = 0

    var operationResult = operation.operation(attempts, MAX_STORAGE_ATTEMPTS)
    while (attempts < MAX_STORAGE_ATTEMPTS && !operationResult.isSuccess()) {
      ThreadUtil.sleep(WAIT_FOR_SCOPED_STORAGE[attempts])
      attempts++

      operationResult = operation.operation(attempts, MAX_STORAGE_ATTEMPTS)
    }

    return operationResult
  }

  /** Operation to perform in a retry loop via [retryDocumentFileOperation] that could fail based on timing */
  fun interface DocumentFileOperation<T> {
    fun operation(attempt: Int, maxAttempts: Int): OperationResult
  }

  /** Result of a single operation in a retry loop via [retryDocumentFileOperation] */
  sealed interface OperationResult {
    fun isSuccess(): Boolean {
      return this is Success
    }

    /** The operation completed successful */
    data class Success(val value: Boolean) : OperationResult

    /** Retry the operation */
    data object Retry : OperationResult
  }
}
