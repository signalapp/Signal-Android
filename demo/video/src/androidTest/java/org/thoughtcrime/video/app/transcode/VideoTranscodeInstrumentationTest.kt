/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.video.StreamingTranscoder
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.exceptions.VideoSourceException
import org.thoughtcrime.securesms.video.videoconverter.exceptions.CodecUnavailableException
import org.thoughtcrime.securesms.video.videoconverter.exceptions.EncodingException
import org.thoughtcrime.securesms.video.videoconverter.exceptions.HdrDecoderUnavailableException
import org.thoughtcrime.securesms.video.videoconverter.mediadatasource.InputStreamMediaDataSource
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Instrumentation test that transcodes all sample video files found in the androidTest assets.
 *
 * To use, set `sample.videos.dir` in `local.properties` to a directory containing video files.
 * Those files will be packaged as test assets and transcoded with each [TranscodingPreset].
 *
 * If no sample videos are configured, the tests will be skipped (not failed).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class VideoTranscodeInstrumentationTest {

  companion object {
    private const val TAG = "VideoTranscodeTest"
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "3gp", "mov")
  }

  private lateinit var testContext: Context
  private lateinit var appContext: Context
  private val tempFiles = mutableListOf<File>()

  @Before
  fun setUp() {
    testContext = InstrumentationRegistry.getInstrumentation().context
    appContext = InstrumentationRegistry.getInstrumentation().targetContext
  }

  @After
  fun tearDown() {
    tempFiles.forEach { it.delete() }
    tempFiles.clear()
  }

  @Test
  fun transcodeAllVideos_level1() {
    transcodeAllVideos(TranscodingPreset.LEVEL_1)
  }

  @Test
  fun transcodeAllVideos_level2() {
    transcodeAllVideos(TranscodingPreset.LEVEL_2)
  }

  @Test
  fun transcodeAllVideos_level3() {
    transcodeAllVideos(TranscodingPreset.LEVEL_3)
  }

  @Test
  fun transcodeAllVideos_level3H265() {
    transcodeAllVideos(TranscodingPreset.LEVEL_3_H265)
  }

  private fun transcodeAllVideos(preset: TranscodingPreset) {
    val videoFiles = getVideoFileNames()
    Assume.assumeTrue(
      "No sample videos found in test assets. Set 'sample.videos.dir' in local.properties to a directory containing video files.",
      videoFiles.isNotEmpty()
    )

    Log.i(TAG, "Found ${videoFiles.size} sample video(s): $videoFiles")

    val failures = mutableListOf<String>()

    val deviceWarnings = mutableListOf<String>()

    for (videoFileName in videoFiles) {
      Log.i(TAG, "Transcoding '$videoFileName' with preset ${preset.name}...")
      try {
        transcodeVideo(videoFileName, preset)
        Log.i(TAG, "Successfully transcoded '$videoFileName' with preset ${preset.name}")
      } catch (e: HdrDecoderUnavailableException) {
        Log.w(TAG, "No decoder available for HDR video '$videoFileName' with preset ${preset.name} (device limitation)", e)
        deviceWarnings.add("$videoFileName [${preset.name}]: ${e::class.simpleName}: ${e.message}")
      } catch (e: FileNotFoundException) {
        Log.w(TAG, "Skipping '$videoFileName' with preset ${preset.name}: encoder not available on this device", e)
      } catch (e: EncodingException) {
        val isHdrDeviceLimitation = e.isHdrInput && !e.toneMapApplied
        if (isHdrDeviceLimitation) {
          Log.w(TAG, "Video '$videoFileName' failed with preset ${preset.name} (HDR device limitation, toneMap=${e.toneMapApplied}, decoder=${e.decoderName}, encoder=${e.encoderName})", e)
          deviceWarnings.add("$videoFileName [${preset.name}]: ${e::class.simpleName}: ${e.message}")
        } else {
          Log.e(TAG, "Failed to transcode '$videoFileName' with preset ${preset.name} (hdr=${e.isHdrInput}, toneMap=${e.toneMapApplied}, decoder=${e.decoderName}, encoder=${e.encoderName})", e)
          failures.add("$videoFileName: ${e::class.simpleName}: ${e.message}")
        }
      } catch (e: VideoSourceException) {
        Log.w(TAG, "Device cannot read video source '$videoFileName' with preset ${preset.name} (device limitation)", e)
        deviceWarnings.add("$videoFileName [${preset.name}]: ${e::class.simpleName}: ${e.message}")
      } catch (e: CodecUnavailableException) {
        Log.w(TAG, "All codecs exhausted for '$videoFileName' with preset ${preset.name} (device limitation)", e)
        deviceWarnings.add("$videoFileName [${preset.name}]: ${e::class.simpleName}: ${e.message}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to transcode '$videoFileName' with preset ${preset.name}", e)
        failures.add("$videoFileName: ${e::class.simpleName}: ${e.message}")
      }
    }

    if (deviceWarnings.isNotEmpty()) {
      Log.w(TAG, "${deviceWarnings.size} video(s) could not be transcoded (device limitation, not a bug):\n${deviceWarnings.joinToString("\n")}")
    }

    if (failures.isNotEmpty()) {
      Assert.fail(
        "${failures.size}/${videoFiles.size} video(s) failed transcoding with ${preset.name}:\n" +
          failures.joinToString("\n")
      )
    }
  }

  private fun transcodeVideo(videoFileName: String, preset: TranscodingPreset) {
    val inputFile = createTempFile("input-", "-$videoFileName")
    val outputFile = createTempFile("output-${preset.name}-", "-$videoFileName")

    testContext.assets.open(videoFileName).use { input ->
      inputFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }

    Log.i(TAG, "Copied '$videoFileName' to temp file (${inputFile.length()} bytes)")

    val dataSource = FileMediaDataSource(inputFile)
    val transcoder = StreamingTranscoder.createManuallyForTesting(
      dataSource,
      null,
      preset.videoCodec,
      preset.videoBitRate,
      preset.audioBitRate,
      preset.videoShortEdge,
      true
    )

    outputFile.outputStream().use { outputStream ->
      transcoder.transcode(
        { percent -> Log.d(TAG, "  $videoFileName [${preset.name}]: $percent%") },
        outputStream,
        null
      )
    }

    Assert.assertTrue(
      "Transcoded output for '$videoFileName' with ${preset.name} is empty",
      outputFile.length() > 0
    )

    Log.i(TAG, "Output for '$videoFileName' with ${preset.name}: ${outputFile.length()} bytes")
  }

  private fun getVideoFileNames(): List<String> {
    val allFiles = testContext.assets.list("") ?: emptyArray()
    return allFiles.filter { fileName ->
      val ext = fileName.substringAfterLast('.', "").lowercase()
      ext in VIDEO_EXTENSIONS
    }.sorted()
  }

  private fun createTempFile(prefix: String, suffix: String): File {
    val file = File.createTempFile(prefix, suffix, appContext.cacheDir)
    tempFiles.add(file)
    return file
  }

  private class FileMediaDataSource(private val file: File) : InputStreamMediaDataSource() {
    override fun close() {}
    override fun getSize(): Long = file.length()
    override fun createInputStream(position: Long): InputStream {
      val stream = FileInputStream(file)
      stream.skip(position)
      return stream
    }
  }
}
