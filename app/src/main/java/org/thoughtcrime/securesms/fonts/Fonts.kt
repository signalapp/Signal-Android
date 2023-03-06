package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.WorkerThread
import org.signal.core.util.ThreadUtil
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

  private const val VERSION_URI = "${S3.DYNAMIC_PATH}/story-fonts/version.txt"
  private const val BASE_STATIC_BUCKET_URI = "${S3.STATIC_PATH}/story-fonts"
  private const val MANIFEST = "manifest.json"

  private val taskCache = Collections.synchronizedMap(mutableMapOf<FontDownloadKey, ListenableFutureTask<Typeface>>())

  /**
   * Returns a File which font data should be written to.
   */
  fun getDirectory(context: Context): File {
    return context.getDir("story-fonts", Context.MODE_PRIVATE)
  }

  /**
   * Attempts to retrieve a Typeface for the given font / guessed script and default locales combination
   *
   * @param context An application context
   * @param font The desired font
   * @param supportedScript The script likely being used based on text content
   *
   * @return a FontResult that represents either a Typeface or a task retrieving a Typeface.
   */
  @WorkerThread
  fun resolveFont(context: Context, font: TextFont, supportedScript: SupportedScript): FontResult {
    ThreadUtil.assertNotMainThread()
    synchronized(this) {
      val errorFallback = FontResult.Immediate(Typeface.create(font.fallbackFamily, font.fallbackStyle))
      val version = FontVersion.get(context)
      if (version == FontVersion.NONE) {
        return errorFallback
      }

      val manifest = FontManifest.get(context, version) ?: return errorFallback

      Log.d(TAG, "Loaded manifest.")

      val fontScript = resolveFontScriptFromScriptName(supportedScript, manifest)
      if (fontScript == null) {
        Log.d(TAG, "Manifest does not have an entry for $supportedScript. Using default.")
        return FontResult.Immediate(getDefaultFontForScriptAndStyle(supportedScript, font))
      }

      Log.d(TAG, "Loaded script for locale.")

      val fontNetworkPath = getScriptPath(font, fontScript)
      if (fontNetworkPath == null) {
        Log.d(TAG, "Manifest does not contain a network path for $supportedScript. Using default.")
        return FontResult.Immediate(getDefaultFontForScriptAndStyle(supportedScript, font))
      }

      val fontLocalPath = FontFileMap.getNameOnDisk(context, version, fontNetworkPath)

      if (fontLocalPath != null) {
        Log.d(TAG, "Local font version found, returning immediate.")
        return FontResult.Immediate(loadFontIntoTypeface(context, version, fontLocalPath) ?: errorFallback.typeface)
      }

      val fontDownloadKey = FontDownloadKey(
        version,
        supportedScript,
        font
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
          val newLocalPath = downloadFont(context, supportedScript, font, version, manifest)
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

  private fun getDefaultFontForScriptAndStyle(supportedScript: SupportedScript, font: TextFont): Typeface {
    return when (supportedScript) {
      SupportedScript.CYRILLIC -> {
        when (font) {
          TextFont.REGULAR -> Typeface.SANS_SERIF
          TextFont.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          TextFont.SERIF -> Typeface.SERIF
          TextFont.SCRIPT -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SERIF, "semibold", TypefaceHelper.Weight.SEMI_BOLD)
          TextFont.CONDENSED -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "light", TypefaceHelper.Weight.LIGHT)
        }
      }
      SupportedScript.DEVANAGARI -> {
        when (font) {
          TextFont.REGULAR -> Typeface.SANS_SERIF
          TextFont.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          TextFont.SERIF -> Typeface.SANS_SERIF
          TextFont.SCRIPT -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          TextFont.CONDENSED -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "light", TypefaceHelper.Weight.LIGHT)
        }
      }
      SupportedScript.CHINESE_TRADITIONAL_HK,
      SupportedScript.CHINESE_TRADITIONAL,
      SupportedScript.CHINESE_SIMPLIFIED,
      SupportedScript.UNKNOWN_CJK -> {
        when (font) {
          TextFont.REGULAR -> Typeface.SANS_SERIF
          TextFont.BOLD -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "semibold", TypefaceHelper.Weight.SEMI_BOLD)
          TextFont.SERIF -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "thin", TypefaceHelper.Weight.THIN)
          TextFont.SCRIPT -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "light", TypefaceHelper.Weight.LIGHT)
          TextFont.CONDENSED -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "demilight", TypefaceHelper.Weight.DEMI_LIGHT)
        }
      }
      SupportedScript.ARABIC -> {
        when (font) {
          TextFont.REGULAR -> Typeface.SANS_SERIF
          TextFont.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          TextFont.SERIF -> Typeface.SERIF
          TextFont.SCRIPT -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
          TextFont.CONDENSED -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "black", TypefaceHelper.Weight.BLACK)
        }
      }
      SupportedScript.JAPANESE -> {
        when (font) {
          TextFont.REGULAR -> Typeface.SANS_SERIF
          TextFont.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          TextFont.SERIF -> Typeface.SERIF
          TextFont.SCRIPT -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
          TextFont.CONDENSED -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "medium", TypefaceHelper.Weight.MEDIUM)
        }
      }
      SupportedScript.LATIN,
      SupportedScript.UNKNOWN -> {
        when (font) {
          TextFont.REGULAR -> Typeface.SANS_SERIF
          TextFont.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
          TextFont.SERIF -> Typeface.SERIF
          TextFont.SCRIPT -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
          TextFont.CONDENSED -> TypefaceHelper.typefaceFor(TypefaceHelper.Family.SANS_SERIF, "black", TypefaceHelper.Weight.BLACK)
        }
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
    return S3.getLong(VERSION_URI)
  }

  /**
   * Downloads and verifies the latest manifest.
   */
  @WorkerThread
  fun downloadAndVerifyLatestManifest(context: Context, version: FontVersion, manifestPath: String): Boolean {
    return S3.verifyAndWriteToDisk(
      context,
      "$BASE_STATIC_BUCKET_URI/${version.id}/$MANIFEST",
      File(getDirectory(context), manifestPath)
    )
  }

  /**
   * Downloads the given font file from S3
   */
  @WorkerThread
  private fun downloadFont(context: Context, supportedScript: SupportedScript?, font: TextFont, fontVersion: FontVersion, fontManifest: FontManifest): String? {
    if (supportedScript == null) {
      return null
    }

    val script: FontManifest.FontScript = resolveFontScriptFromScriptName(supportedScript, fontManifest) ?: return null
    val path = getScriptPath(font, script) ?: return null
    val networkPath = "$BASE_STATIC_BUCKET_URI/${fontVersion.id}/$path"
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

  private fun getScriptPath(font: TextFont, script: FontManifest.FontScript): String? {
    return when (font) {
      TextFont.REGULAR -> script.regular
      TextFont.BOLD -> script.bold
      TextFont.SERIF -> script.serif
      TextFont.SCRIPT -> script.script
      TextFont.CONDENSED -> script.condensed
    }
  }

  fun getSupportedScript(locales: List<Locale>, guessedScript: SupportedScript): SupportedScript {
    if (guessedScript != SupportedScript.UNKNOWN && guessedScript != SupportedScript.UNKNOWN_CJK) {
      return guessedScript
    } else if (guessedScript == SupportedScript.UNKNOWN_CJK) {
      val likelyScript: SupportedScript? = locales.mapNotNull {
        try {
          when (it.isO3Country) {
            "HKG" -> SupportedScript.CHINESE_TRADITIONAL_HK
            "CHN" -> SupportedScript.CHINESE_SIMPLIFIED
            "TWN" -> SupportedScript.CHINESE_TRADITIONAL
            "JPN" -> SupportedScript.JAPANESE
            else -> null
          }
        } catch (e: java.util.MissingResourceException) {
          Log.w(TAG, "Unable to get ISO-3 country code for: $it")
          null
        }
      }.firstOrNull()

      if (likelyScript != null) {
        return likelyScript
      }
    }

    val locale = locales.first()
    val supportedScript: SupportedScript = when (ScriptUtil.getScript(locale).also { Log.d(TAG, "Getting Script for $it") }) {
      ScriptUtil.LATIN -> SupportedScript.LATIN
      ScriptUtil.ARABIC -> SupportedScript.ARABIC
      ScriptUtil.CHINESE_SIMPLIFIED -> SupportedScript.CHINESE_SIMPLIFIED
      ScriptUtil.CHINESE_TRADITIONAL -> SupportedScript.CHINESE_TRADITIONAL
      ScriptUtil.CYRILLIC -> SupportedScript.CYRILLIC
      ScriptUtil.DEVANAGARI -> SupportedScript.DEVANAGARI
      ScriptUtil.JAPANESE -> SupportedScript.JAPANESE
      else -> SupportedScript.UNKNOWN
    }

    return if (supportedScript == SupportedScript.CHINESE_SIMPLIFIED && locale.isO3Country == "HKG") {
      SupportedScript.CHINESE_TRADITIONAL_HK
    } else {
      supportedScript
    }
  }

  private fun resolveFontScriptFromScriptName(supportedScript: SupportedScript?, fontManifest: FontManifest): FontManifest.FontScript? {
    return when (supportedScript.also { Log.d(TAG, "Getting Script for $it") }) {
      SupportedScript.LATIN -> fontManifest.scripts.latinExtended
      SupportedScript.ARABIC -> fontManifest.scripts.arabic
      SupportedScript.CHINESE_SIMPLIFIED -> fontManifest.scripts.chineseSimplified
      SupportedScript.CHINESE_TRADITIONAL -> fontManifest.scripts.chineseTraditional
      SupportedScript.CYRILLIC -> fontManifest.scripts.cyrillicExtended
      SupportedScript.DEVANAGARI -> fontManifest.scripts.devanagari
      SupportedScript.JAPANESE -> fontManifest.scripts.japanese
      SupportedScript.CHINESE_TRADITIONAL_HK -> fontManifest.scripts.chineseTraditionalHk
      SupportedScript.UNKNOWN_CJK -> null
      SupportedScript.UNKNOWN -> null
      null -> null
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
    val script: SupportedScript,
    val font: TextFont
  )
}
