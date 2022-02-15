package org.thoughtcrime.securesms.emoji

import okhttp3.Request
import okhttp3.Response
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import java.io.IOException

private const val VERSION_URL = "https://updates.signal.org/dynamic/android/emoji/version_v3.txt"
private const val BASE_STATIC_BUCKET_URL = "https://updates.signal.org/static/android/emoji"

/**
 * Responsible for communicating with S3 to download Emoji related objects.
 */
object EmojiRemote {

  private const val TAG = "EmojiRemote"

  private val okHttpClient = ApplicationDependencies.getOkHttpClient()

  @JvmStatic
  @Throws(IOException::class)
  fun getVersion(): Int {
    val request = Request.Builder()
      .get()
      .url(VERSION_URL)
      .build()

    try {
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException()
        }

        return response.body()?.bytes()?.let { String(it).trim().toIntOrNull() } ?: throw IOException()
      }
    } catch (e: IOException) {
      throw e
    }
  }

  /**
   * Downloads and returns the MD5 hash stored in an S3 object's ETag
   */
  @JvmStatic
  fun getMd5(emojiRequest: EmojiRequest): ByteArray? {
    val request = Request.Builder()
      .head()
      .url(emojiRequest.url)
      .build()

    try {
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException()
        }

        return response.header("ETag")?.toByteArray()
      }
    } catch (e: IOException) {
      Log.w(TAG, "Could not retrieve md5", e)
      return null
    }
  }

  /**
   * Downloads an object for the specified name.
   */
  @JvmStatic
  fun getObject(emojiRequest: EmojiRequest): Response {
    val request = Request.Builder()
      .get()
      .url(emojiRequest.url)
      .build()

    return okHttpClient.newCall(request).execute()
  }
}

interface EmojiRequest {
  val url: String
}

class EmojiJsonRequest(version: Int) : EmojiRequest {
  override val url: String = "$BASE_STATIC_BUCKET_URL/$version/emoji_data.json"
}

class EmojiImageRequest(
  version: Int,
  density: String,
  name: String,
  format: String
) : EmojiRequest {
  override val url: String = "$BASE_STATIC_BUCKET_URL/$version/$density/$name.$format"
}

class EmojiFileRequest(
  version: Int,
  density: String,
  name: String,
) : EmojiRequest {
  override val url: String = "$BASE_STATIC_BUCKET_URL/$version/$density/$name"
}
