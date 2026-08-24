package org.signal.emoji

import okhttp3.Response
import java.io.IOException

private val baseStaticBucketUri: String
  get() = "${EmojiDependencies.remote.staticPath}/android/emoji"

/**
 * Responsible for communicating with the CDN to download Emoji related objects.
 */
object EmojiRemote {

  private val versionUri: String
    get() = "${EmojiDependencies.remote.dynamicPath}/android/emoji/version_v3.txt"

  @JvmStatic
  @Throws(IOException::class)
  fun getVersion(): Int {
    return EmojiDependencies.remote.getLong(versionUri).toInt()
  }

  /**
   * Downloads and returns the MD5 hash stored in an object's ETag
   */
  @JvmStatic
  fun getMd5(emojiRequest: EmojiRequest): ByteArray? {
    return EmojiDependencies.remote.getObjectMd5(emojiRequest.uri)
  }

  /**
   * Downloads an object for the specified name.
   */
  @JvmStatic
  fun getObject(emojiRequest: EmojiRequest): Response {
    return EmojiDependencies.remote.getObject(emojiRequest.uri)
  }
}

interface EmojiRequest {
  val uri: String
}

class EmojiJsonRequest(version: Int) : EmojiRequest {
  override val uri: String = "$baseStaticBucketUri/$version/emoji_data.json"
}

class EmojiImageRequest(
  version: Int,
  density: String,
  name: String,
  format: String
) : EmojiRequest {
  override val uri: String = "$baseStaticBucketUri/$version/$density/$name.$format"
}

class EmojiFileRequest(
  version: Int,
  density: String,
  name: String
) : EmojiRequest {
  override val uri: String = "$baseStaticBucketUri/$version/$density/$name"
}
