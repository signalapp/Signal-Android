/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.content.Context
import okio.IOException
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.s3.S3
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

/**
 * Manages downloading and registering calling assets (e.g. DRED weights).
 */
object CallingAssets {
  private val TAG = Log.tag(CallingAssets::class)

  private const val BASE_DIRECTORY = "calling-assets"

  /** Increment this whenever an asset is added, removed, or updated. */
  const val CURRENT_VERSION = 1

  private val ASSETS: List<ManifestEntry> = listOf(
    ManifestEntry(
      assetGroup = "opus-dred",
      name = "calling-dred_weights-1_6_1-f4aed08a.bin",
      digest = "sdfpdb/u3wiTfBr2s0gx1LJX6jii4tquyax/UBThTGWTEXyOCSKjYmYV+9tKQZcO+Q1B1ReoGSW3VbvzeMGKaQ==",
      url = "https://updates2.signal.org/static/android/calling/deep_plc-dred_weights-1_6_1-f4aed08a.bin",
      size = 1998208
    )
  )

  private val registeredLog = HashSet<String>(ASSETS.size)

  /**
   * Registers any downloaded assets with the call manager that haven't been registered yet this session.
   * Safe to call multiple times -- assets already registered are skipped.
   */
  @JvmStatic
  fun registerAssetsIfNeeded() {
    ASSETS.forEach { entry ->
      if (registeredLog.contains(entry.name)) {
        return@forEach
      }

      try {
        val content = getFromFile(entry.name) ?: return@forEach
        if (verify(content, entry)) {
          AppDependencies.signalCallManager.addAsset(entry.assetGroup, content)
          registeredLog.add(entry.name)
          Log.i(TAG, "Registered calling asset: ${entry.name}")
        } else {
          Log.w(TAG, "Invalid calling asset on disk, skipping registration: ${entry.name}")
        }
      } catch (e: IOException) {
        Log.e(TAG, "Failed to register calling asset ${entry.name}", e)
      }
    }
  }

  /**
   * Downloads any assets not yet present on disk.
   * @return true if all assets are present on disk after this call.
   */
  fun downloadMissingAssets(): Boolean {
    var allDownloaded = true

    ASSETS.forEach { entry ->
      try {
        val dataOnDisk = getFromFile(entry.name)
        if (dataOnDisk != null) {
          if (verify(dataOnDisk, entry)) {
            Log.i(TAG, "Calling asset already on disk: ${entry.name}")
            return@forEach
          } else {
            Log.w(TAG, "Invalid calling asset found on disk: ${entry.name}")
          }
        }

        val remoteData = getFromRemote(entry.url)
        if (remoteData != null) {
          if (verify(remoteData, entry)) {
            Log.i(TAG, "Calling asset successfully downloaded: ${entry.name}")
            val dir = AppDependencies.application.getDir(BASE_DIRECTORY, Context.MODE_PRIVATE)
            File(dir, entry.name).writeBytes(remoteData)
            return@forEach
          } else {
            Log.w(TAG, "Failed to verify calling asset: ${entry.name}")
          }
        }

        Log.w(TAG, "Unable to find or download calling asset ${entry.name}")
        allDownloaded = false
      } catch (e: Exception) {
        Log.e(TAG, "Unexpected exception while trying to find calling asset ${entry.name}", e)
        allDownloaded = false
      }
    }

    if (allDownloaded) {
      deleteStaleAssets()
    }

    return allDownloaded
  }

  private fun getFromFile(assetName: String): ByteArray? {
    try {
      val file = File(AppDependencies.application.getDir(BASE_DIRECTORY, Context.MODE_PRIVATE), assetName)
      return if (file.exists()) file.readBytes() else null
    } catch (e: Exception) {
      Log.e(TAG, "Exception while checking files for calling asset $assetName", e)
      return null
    }
  }

  private fun getFromRemote(url: String): ByteArray? {
    try {
      val path = URI(url).path
      S3.getObject(path).use { response ->
        if (!response.isSuccessful) {
          throw RuntimeException("Failed to download calling asset from $url: HTTP ${response.code}")
        }
        return response.body.bytes()
      }
    } catch (e: IOException) {
      Log.e(TAG, "Exception while downloading calling asset from $url", e)
      return null
    }
  }

  private fun verify(content: ByteArray, entry: ManifestEntry): Boolean {
    if (content.size != entry.size) {
      Log.w(TAG, "Unexpected size for calling asset ${entry.name}: expected=${entry.name},actual=${content.size}")
      return false
    }
    val hash = MessageDigest.getInstance("SHA-512").digest(content)
    val encodedHash = Base64.getEncoder().encodeToString(hash)
    return encodedHash == entry.digest
  }

  private fun deleteStaleAssets() {
    try {
      val expectedNames = ASSETS.map { it.name }.toSet()
      val dir = AppDependencies.application.getDir(BASE_DIRECTORY, Context.MODE_PRIVATE)
      dir.listFiles()?.forEach { file ->
        if (file.name !in expectedNames) {
          Log.i(TAG, "Deleting stale calling asset: ${file.name}")
          file.delete()
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to clean up stale calling assets", e)
    }
  }

  data class ManifestEntry(
    val assetGroup: String,
    val name: String,
    val digest: String,
    val url: String,
    val size: Int
  )
}
