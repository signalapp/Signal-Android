/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.video.StreamingTranscoder
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.postprocessing.Mp4FaststartPostProcessor
import org.thoughtcrime.securesms.video.videoconverter.mediadatasource.InputStreamMediaDataSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant

/**
 * Repository that performs video transcoding using coroutines.
 */
class TranscodeTestRepository {

  data class TranscodeResult(val outputUri: Uri, val originalFile: File, val originalSize: Long, val outputSize: Long)

  /**
   * Transcode a video using a [TranscodingPreset] and save the result to the Downloads folder.
   */
  @RequiresApi(26)
  suspend fun transcodeWithPreset(
    context: Context,
    inputUri: Uri,
    preset: TranscodingPreset,
    enableFastStart: Boolean,
    enableAudioRemux: Boolean,
    onProgress: (Int) -> Unit
  ): TranscodeResult {
    return doTranscode(context, inputUri, enableFastStart, onProgress) { inputFile ->
      val dataSource = FileMediaDataSource(inputFile)
      StreamingTranscoder(dataSource, null, preset, DEFAULT_FILE_SIZE_LIMIT, enableAudioRemux)
    }
  }

  /**
   * Transcode a video using custom parameters and save the result to the Downloads folder.
   */
  @RequiresApi(26)
  suspend fun transcodeWithCustomOptions(
    context: Context,
    inputUri: Uri,
    options: CustomTranscodingOptions,
    onProgress: (Int) -> Unit
  ): TranscodeResult {
    return doTranscode(context, inputUri, options.enableFastStart, onProgress) { inputFile ->
      val dataSource = FileMediaDataSource(inputFile)
      StreamingTranscoder.createManuallyForTesting(
        dataSource,
        null,
        options.videoCodec,
        options.videoBitrate,
        options.audioBitrate,
        options.videoResolution.shortEdge,
        options.enableAudioRemux
      )
    }
  }

  @RequiresApi(26)
  private suspend fun doTranscode(
    context: Context,
    inputUri: Uri,
    enableFastStart: Boolean,
    onProgress: (Int) -> Unit,
    createTranscoder: (File) -> StreamingTranscoder
  ): TranscodeResult {
    val inputFilename = inputUri.lastPathSegment?.substringAfterLast('/') ?: "input"
    val baseName = inputFilename.substringBeforeLast('.')
    val filenameBase = "transcoded-${Instant.now()}-$baseName"
    val tempTranscodeFilename = "$filenameBase.tmp"
    val outputFilename = "$filenameBase.mp4"

    val inputFile = File(context.filesDir, "original-${System.currentTimeMillis()}.mp4")
    val tempFile = File(context.filesDir, tempTranscodeFilename)

    val coroutineContext = currentCoroutineContext()

    var success = false
    try {
      // Copy input to internal storage for random access
      Log.i(TAG, "Copying input to internal storage...")
      context.contentResolver.openInputStream(inputUri).use { inputStream ->
        requireNotNull(inputStream) { "Could not open input URI" }
        inputFile.outputStream().use { out ->
          inputStream.copyTo(out)
        }
      }
      Log.i(TAG, "Input copy complete. Size: ${inputFile.length()}")

      coroutineContext.ensureActive()

      // Transcode
      val transcoder = createTranscoder(inputFile)
      Log.i(TAG, "Starting transcode...")
      val mdatSize: Long
      tempFile.outputStream().use { outputStream ->
        mdatSize = transcoder.transcode(
          { percent -> onProgress(percent) },
          outputStream
        ) { !coroutineContext.isActive }
      }
      val originalSize = inputFile.length()
      val outputSize = tempFile.length()
      Log.i(TAG, "Transcode complete. Output size: $outputSize, mdat size: $mdatSize")

      coroutineContext.ensureActive()

      // Save to Downloads
      val outputUri = if (enableFastStart) {
        saveToDownloadsWithFastStart(context, tempFile, outputFilename, mdatSize.toInt())
      } else {
        saveToDownloads(context, tempFile, outputFilename)
      }

      Log.i(TAG, "Saved to Downloads: $outputUri")
      success = true
      return TranscodeResult(outputUri, inputFile, originalSize, outputSize)
    } finally {
      tempFile.delete()
      if (!success) {
        inputFile.delete()
      }
    }
  }

  private fun saveToDownloads(context: Context, sourceFile: File, filename: String): Uri {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return saveToDownloadsMediaStore(context, filename) { outputStream ->
        sourceFile.inputStream().use { it.copyTo(outputStream) }
      }
    } else {
      return saveToDownloadsLegacy(sourceFile, filename)
    }
  }

  private fun saveToDownloadsWithFastStart(context: Context, sourceFile: File, filename: String, mdatSize: Int): Uri {
    val inputStreamFactory = Mp4FaststartPostProcessor.InputStreamFactory { FileInputStream(sourceFile) }
    val processor = Mp4FaststartPostProcessor(inputStreamFactory)
    val sourceLength = sourceFile.length()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return saveToDownloadsMediaStore(context, filename) { outputStream ->
        processor.processWithMdatLength(sourceLength, mdatSize).use { it.copyTo(outputStream) }
      }
    } else {
      val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      val outputFile = File(downloadsDir, filename)
      outputFile.outputStream().use { outputStream ->
        processor.processWithMdatLength(sourceLength, mdatSize).use { it.copyTo(outputStream) }
      }
      return Uri.fromFile(outputFile)
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun saveToDownloadsMediaStore(context: Context, filename: String, writeContent: (java.io.OutputStream) -> Unit): Uri {
    val contentValues = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME, filename)
      put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
      put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
      put(MediaStore.Downloads.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
      ?: throw IOException("Failed to create MediaStore entry")

    resolver.openOutputStream(uri)?.use { outputStream ->
      writeContent(outputStream)
    } ?: throw IOException("Failed to open output stream for MediaStore entry")

    contentValues.clear()
    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, contentValues, null, null)

    return uri
  }

  @Suppress("DEPRECATION")
  private fun saveToDownloadsLegacy(sourceFile: File, filename: String): Uri {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val outputFile = File(downloadsDir, filename)
    sourceFile.inputStream().use { input ->
      outputFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    return Uri.fromFile(outputFile)
  }

  data class CustomTranscodingOptions(
    val videoCodec: String,
    val videoResolution: VideoResolution,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val enableFastStart: Boolean,
    val enableAudioRemux: Boolean
  )

  private class FileMediaDataSource(private val file: File) : InputStreamMediaDataSource() {
    private val size = file.length()

    override fun close() {
      // No persistent stream to close
    }

    override fun getSize(): Long = size

    override fun createInputStream(position: Long): InputStream {
      val stream = FileInputStream(file)
      stream.skip(position)
      return stream
    }
  }

  companion object {
    private val TAG = Log.tag(TranscodeTestRepository::class)
    private const val DEFAULT_FILE_SIZE_LIMIT: Long = 100 * 1024 * 1024
  }
}
