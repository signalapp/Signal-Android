package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.s3.S3
import org.thoughtcrime.securesms.util.ListenableFutureTask
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.UUID

/**
 * Text Story Fonts management
 *
 * Fonts are stored on S3 in a bucket called story-fonts, and are backed by a version number.
 * At that version, there is a manifest.json that contains information about which fonts are available for which script
 *
 * This utilizes a file structure like so:
 *
 * .version ( long -> UUID )
 * uuid/
 *   .manifest (manifest JSON)
 *   .map ( object name -> UUID )
 *   uuid1
 *   uuid2
 *   ...
 */
object Fonts {

  private val TAG = Log.tag(Fonts::class.java)

  private const val VERSION_URL = "https://updates.signal.org/dynamic/story-fonts/version.txt"
  private const val BASE_STATIC_BUCKET_URL = "https://updates.signal.org/static/story-fonts"
  private const val MANIFEST = "manifest.json"

  private val taskCache = Collections.synchronizedMap(mutableMapOf<FontDownloadKey, ListenableFutureTask<Typeface>>())

  /**
   * Returns a File which font data should be written to.
   */
  fun getDirectory(context: Context): File {
    return context.getDir("story-fonts", Context.MODE_PRIVATE)
  }

  /**
   * Attempts to retrieve a Typeface for the given font / locale combination
   *
   * @param context An application context
   * @param locale The locale the content will be displayed in
   * @param font The desired font
   * @return a FontResult that represents either a Typeface or a task retrieving a Typeface.
   */
  @WorkerThread
  fun resolveFont(context: Context, locale: Locale, font: TextFont): FontResult {
    synchronized(this) {
      val errorFallback = FontResult.Immediate(Typeface.create(font.fallbackFamily, font.fallbackStyle))
      val version = FontVersion.get(context)
      if (version == FontVersion.NONE) {
        return errorFallback
      }

      val manifest = FontManifest.get(context, version) ?: return errorFallback

      Log.d(TAG, "Loaded manifest.")

      val fontScript = resolveScriptNameFromLocale(locale, manifest) ?: return errorFallback

      Log.d(TAG, "Loaded script for locale.")

      val fontNetworkPath = getScriptPath(font, fontScript)

      val fontLocalPath = FontFileMap.getNameOnDisk(context, version, fontNetworkPath)

      if (fontLocalPath != null) {
        Log.d(TAG, "Local font version found, returning immediate.")
        return FontResult.Immediate(loadFontIntoTypeface(context, version, fontLocalPath) ?: errorFallback.typeface)
      }

      val fontDownloadKey = FontDownloadKey(
        version, locale, font
      )

      val taskInProgress = taskCache[fontDownloadKey]
      return if (taskInProgress != null) {
        Log.d(TAG, "Found a task in progress. Returning in-progress async.")
        FontResult.Async(
          future = taskInProgress,
          placeholder = errorFallback.typeface
        )
      } else {
        Log.d(TAG, "Could not find a task in progress. Returning new async.")
        val newTask = ListenableFutureTask {
          val newLocalPath = downloadFont(context, locale, font, version, manifest)
          Log.d(TAG, "Finished download, $newLocalPath")

          val typeface = newLocalPath?.let { loadFontIntoTypeface(context, version, it) } ?: errorFallback.typeface
          taskCache.remove(fontDownloadKey)
          typeface
        }

        taskCache[fontDownloadKey] = newTask
        SignalExecutors.BOUNDED.execute(newTask::run)

        FontResult.Async(
          future = newTask,
          placeholder = errorFallback.typeface
        )
      }
    }
  }

  @WorkerThread
  private fun loadFontIntoTypeface(context: Context, fontVersion: FontVersion, fontLocalPath: String): Typeface? {
    return try {
      Typeface.createFromFile(File(getDirectory(context), "${fontVersion.path}/$fontLocalPath"))
    } catch (e: Exception) {
      Log.w(TAG, "Could not load typeface from disk.")
      null
    }
  }

  /**
   * Downloads the latest version code.
   */
  @WorkerThread
  fun downloadLatestVersionLong(): Long {
    return S3.getLong(VERSION_URL)
  }

  /**
   * Downloads and verifies the latest manifest.
   */
  @WorkerThread
  fun downloadAndVerifyLatestManifest(context: Context, version: FontVersion, manifestPath: String): Boolean {
    return S3.verifyAndWriteToDisk(
      context,
      "$BASE_STATIC_BUCKET_URL/${version.id}/$MANIFEST",
      File(getDirectory(context), manifestPath)
    )
  }

  /**
   * Downloads the given font file from S3
   */
  @WorkerThread
  private fun downloadFont(context: Context, locale: Locale, font: TextFont, fontVersion: FontVersion, fontManifest: FontManifest): String? {
    val script: FontManifest.FontScript = resolveScriptNameFromLocale(locale, fontManifest) ?: return null
    val path = getScriptPath(font, script)
    val networkPath = "$BASE_STATIC_BUCKET_URL/${fontVersion.id}/$path"
    val localUUID = UUID.randomUUID().toString()
    val localPath = "${fontVersion.path}/" + localUUID

    return if (S3.verifyAndWriteToDisk(context, networkPath, File(getDirectory(context), localPath), doNotEncrypt = true)) {
      FontFileMap.put(context, fontVersion, localUUID, path)
      localUUID
    } else {
      Log.w(TAG, "Failed to download and verify font.")
      null
    }
  }

  private fun getScriptPath(font: TextFont, script: FontManifest.FontScript): String {
    return when (font) {
      TextFont.REGULAR -> script.regular
      TextFont.BOLD -> script.bold
      TextFont.SERIF -> script.serif
      TextFont.SCRIPT -> script.script
      TextFont.CONDENSED -> script.condensed
    }
  }

  private fun resolveScriptNameFromLocale(locale: Locale, fontManifest: FontManifest): FontManifest.FontScript? {
    val fontScript: FontManifest.FontScript = when (ScriptUtil.getScript(locale).apply { Log.d(TAG, "Getting Script for $this") }) {
      ScriptUtil.LATIN -> fontManifest.scripts.latinExtended
      ScriptUtil.ARABIC -> fontManifest.scripts.arabic
      ScriptUtil.CHINESE_SIMPLIFIED -> fontManifest.scripts.chineseSimplified
      ScriptUtil.CHINESE_TRADITIONAL -> fontManifest.scripts.chineseTraditional
      ScriptUtil.CYRILLIC -> fontManifest.scripts.cyrillicExtended
      ScriptUtil.DEVANAGARI -> fontManifest.scripts.devanagari
      ScriptUtil.JAPANESE -> fontManifest.scripts.japanese
      else -> return null
    }

    return if (fontScript == fontManifest.scripts.chineseSimplified && locale.isO3Country == "HKG") {
      fontManifest.scripts.chineseTraditionalHk
    } else {
      fontScript
    }
  }

  /**
   * A Typeface or an Async future retrieving a typeface with a placeholder.
   */
  sealed class FontResult {
    data class Immediate(val typeface: Typeface) : FontResult()
    data class Async(val future: ListenableFutureTask<Typeface>, val placeholder: Typeface) : FontResult()
  }

  private data class FontDownloadKey(
    val version: FontVersion,
    val locale: Locale,
    val font: TextFont
  )
}
