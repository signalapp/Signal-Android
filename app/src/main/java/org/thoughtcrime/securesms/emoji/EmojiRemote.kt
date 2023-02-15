package org.thoughtcrime.securesms.emoji

import okhttp3.Response
import org.thoughtcrime.securesms.s3.S3
import java.io.IOException

private const val BASE_STATIC_BUCKET_URI = "${S3.STATIC_PATH}/android/emoji"

/**
 * Responsible for communicating with S3 to download Emoji related objects.
 */
object EmojiRemote {

  private const val VERSION_URI = "${S3.DYNAMIC_PATH}/android/emoji/version_v3.txt"

  @JvmStatic
  @Throws(IOException::class)
  fun getVersion(): Int {
    return S3.getLong(VERSION_URI).toInt()
  }

  /**
   * Downloads and returns the MD5 hash stored in an S3 object's ETag
   */
  @JvmStatic
  fun getMd5(emojiRequest: EmojiRequest): ByteArray? {
    return S3.getObjectMD5(emojiRequest.uri)
  }

  /**
   * Downloads an object for the specified name.
   */
  @JvmStatic
  fun getObject(emojiRequest: EmojiRequest): Response {
    return S3.getObject(emojiRequest.uri)
  }
}

interface EmojiRequest {
  val uri: String
}

class EmojiJsonRequest(version: Int) : EmojiRequest {
  override val uri: String = "$BASE_STATIC_BUCKET_URI/$version/emoji_data.json"
}

class EmojiImageRequest(
  version: Int,
  density: String,
  name: String,
  format: String
) : EmojiRequest {
  override val uri: String = "$BASE_STATIC_BUCKET_URI/$version/$density/$name.$format"
}

class EmojiFileRequest(
  version: Int,
  density: String,
  name: String
) : EmojiRequest {
  override val uri: String = "$BASE_STATIC_BUCKET_URI/$version/$density/$name"
}
