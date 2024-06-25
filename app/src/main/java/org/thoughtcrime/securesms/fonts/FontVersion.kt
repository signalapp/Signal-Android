package org.thoughtcrime.securesms.fonts

import android.content.Context
import androidx.annotation.WorkerThread
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.EncryptedStreamUtils
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Represents a single version of fonts.
 *
 * @param id The numeric ID of this version, retrieved from the server
 * @param path The UUID path of this version on disk, where supporting files will be stored.
 */
data class FontVersion(val id: Long, val path: String) {

  companion object {
    val NONE = FontVersion(-1, "")

    private val TAG = Log.tag(FontVersion::class.java)
    private val VERSION_CHECK_INTERVAL = TimeUnit.DAYS.toMillis(7)

    private const val PATH = ".version"

    private val objectMapper = ObjectMapper().registerKotlinModule()

    /**
     * Retrieves the latest font version. This may hit the disk, network, or both, depending on when we last checked for a font version.
     */
    @WorkerThread
    fun get(context: Context): FontVersion {
      val fromDisk = fromDisk(context)
      val version: FontVersion = if (System.currentTimeMillis() - SignalStore.story.lastFontVersionCheck > VERSION_CHECK_INTERVAL) {
        Log.i(TAG, "Timeout interval exceeded, checking network for new font version.")

        val fromNetwork = fromNetwork()
        if (fromDisk == null && fromNetwork == null) {
          Log.i(TAG, "Couldn't download font version and none present on disk.")
          return NONE
        } else if (fromDisk == null && fromNetwork != null) {
          Log.i(TAG, "Found initial font version.")
          return writeVersionToDisk(context, fromNetwork) ?: NONE
        } else if (fromDisk != null && fromNetwork != null) {
          if (fromDisk.id < fromNetwork.id) {
            Log.i(TAG, "Found a new font version. Replacing old version")
            writeVersionToDisk(context, fromNetwork) ?: NONE
          } else {
            Log.i(TAG, "Network version is the same as our local version.")
            SignalStore.story.lastFontVersionCheck = System.currentTimeMillis()
            fromDisk
          }
        } else {
          Log.i(TAG, "Couldn't download font version, using what we have.")
          fromDisk ?: NONE
        }
      } else {
        Log.i(TAG, "Timeout interval not exceeded, using what we have.")
        fromDisk ?: NONE
      }

      cleanOldVersions(context, version.path)
      return version
    }

    @WorkerThread
    private fun writeVersionToDisk(context: Context, fontVersion: FontVersion): FontVersion? {
      return try {
        val versionPath = File(Fonts.getDirectory(context), PATH)
        if (versionPath.exists()) {
          versionPath.delete()
        }

        EncryptedStreamUtils.getOutputStream(context, versionPath).use {
          objectMapper.writeValue(it, fontVersion)
        }

        File(Fonts.getDirectory(context), fontVersion.path).mkdir()

        Log.i(TAG, "Wrote version ${fontVersion.id} to disk.")
        SignalStore.story.lastFontVersionCheck = System.currentTimeMillis()
        fontVersion
      } catch (e: Exception) {
        Log.e(TAG, "Failed to write new font version to disk", e)
        null
      }
    }

    @WorkerThread
    private fun fromDisk(context: Context): FontVersion? {
      return try {
        EncryptedStreamUtils.getInputStream(context, File(Fonts.getDirectory(context), PATH)).use {
          objectMapper.readValue(it, FontVersion::class.java)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Could not read font version from disk.")
        null
      }
    }

    @WorkerThread
    private fun fromNetwork(): FontVersion? {
      return try {
        FontVersion(Fonts.downloadLatestVersionLong(), UUID.randomUUID().toString()).apply {
          Log.i(TAG, "Downloaded version $id")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Could not read font version from network.", e)
        null
      }
    }

    @WorkerThread
    private fun cleanOldVersions(context: Context, path: String) {
      if (path.isEmpty()) {
        Log.i(TAG, "No versions downloaded. Skipping cleanup.")
        return
      }

      Fonts.getDirectory(context)
        .listFiles { _, name -> name != path && name != PATH }
        ?.apply { Log.i(TAG, "Deleting $size files") }
        ?.forEach { it.delete() }
    }
  }
}
