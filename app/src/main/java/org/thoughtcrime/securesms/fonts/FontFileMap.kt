package org.thoughtcrime.securesms.fonts

import android.content.Context
import androidx.annotation.WorkerThread
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.EncryptedStreamUtils
import java.io.File

/**
 * FontFileMap links a network font name (e.g. Inter-Bold.ttf) to a UUID used as an on-disk filename.
 * These mappings are encoded into JSON and stored on disk in a file called .map
 */
data class FontFileMap(val map: Map<String, String>) {

  companion object {

    private val TAG = Log.tag(FontFileMap::class.java)
    private const val PATH = ".map"
    private val objectMapper = ObjectMapper().registerKotlinModule()

    /**
     * Adds the given mapping to the .map file.
     *
     * @param context A context
     * @param fontVersion The font version from which to get the parent directory
     * @param nameOnDisk The name written to disk
     * @param nameOnNetwork The network name from the manifest
     */
    @WorkerThread
    fun put(context: Context, fontVersion: FontVersion, nameOnDisk: String, nameOnNetwork: String) {
      val fontFileMap = getMap(context, fontVersion)

      @Suppress("IfThenToElvis")
      val newMap = if (fontFileMap == null) {
        Log.d(TAG, "Creating a new font file map.")
        FontFileMap(mapOf(nameOnNetwork to nameOnDisk))
      } else {
        Log.d(TAG, "Modifying existing font file map.")
        fontFileMap.copy(map = fontFileMap.map.plus(nameOnNetwork to nameOnDisk))
      }

      setMap(context, fontVersion, newMap)
    }

    /**
     * Retrieves the on-disk name for a given network name
     *
     * @param context a Context
     * @param fontVersion The version from which to get the parent directory
     * @param nameOnNetwork The name of the font from the manifest
     * @return The name of the file on disk, or null
     */
    @WorkerThread
    fun getNameOnDisk(context: Context, fontVersion: FontVersion, nameOnNetwork: String): String? {
      val fontFileMap = getMap(context, fontVersion) ?: return null

      return fontFileMap.map[nameOnNetwork]
    }

    @WorkerThread
    private fun getMap(context: Context, fontVersion: FontVersion): FontFileMap? {
      return try {
        EncryptedStreamUtils.getInputStream(context, File(Fonts.getDirectory(context), "${fontVersion.path}/$PATH")).use {
          objectMapper.readValue(it, FontFileMap::class.java)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Couldn't read names file.")
        return null
      }
    }

    @WorkerThread
    private fun setMap(context: Context, fontVersion: FontVersion, fontFileMap: FontFileMap) {
      try {
        EncryptedStreamUtils.getOutputStream(context, File(Fonts.getDirectory(context), "${fontVersion.path}/$PATH")).use {
          objectMapper.writeValue(it, fontFileMap)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Couldn't write names file.")
      }
    }
  }
}
