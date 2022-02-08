package org.thoughtcrime.securesms.s3

import android.content.Context
import androidx.annotation.WorkerThread
import okhttp3.Request
import okhttp3.Response
import okio.HashingSink
import okio.sink
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.EncryptedStreamUtils
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.websocket.DefaultErrorMapper
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Generic methods for communicating with S3
 */
object S3 {
  private val TAG = Log.tag(S3::class.java)

  private val okHttpClient = ApplicationDependencies.getOkHttpClient()

  /**
   * Fetches the content at the given endpoint and attempts to convert it into a long.
   *
   * @param endpoint The endpoint at which to get the long
   * @return the long value of the body
   * @throws IOException if the call fails or the response body cannot be parsed as a long
   */
  @WorkerThread
  fun getLong(endpoint: String): Long {
    val request = Request.Builder()
      .get()
      .url(endpoint)
      .build()

    try {
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException()
        }

        return response.body()?.bytes()?.let { String(it).trim().toLongOrNull() } ?: throw IOException()
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to retreive long value from S3")
      throw e
    }
  }

  /**
   * Retrieves an S3 object from the given endpoint.
   */
  @WorkerThread
  fun getObject(endpoint: String): Response {
    val request = Request.Builder()
      .get()
      .url(endpoint)
      .build()

    return okHttpClient.newCall(request).execute()
  }

  /**
   * Retrieves an S3 object from the given endpoint and verifies the contents against the S3 MD5 ETag that is retrieved separately.
   */
  @WorkerThread
  fun <T> getAndVerifyObject(endpoint: String, clazz: Class<T>, md5: ByteArray? = getObjectMD5(endpoint)): ServiceResponse<T> {
    if (md5 == null) {
      Log.w(TAG, "Failed to download s3 object MD5.")
      return ServiceResponse.forExecutionError(Md5FailureException())
    }

    try {
      getObject(endpoint).use { response ->
        if (!response.isSuccessful) {
          return ServiceResponse.forApplicationError(
            DefaultErrorMapper.getDefault().parseError(response.code()),
            response.code(),
            ""
          )
        }

        val source = response.body()?.source()

        val outputStream = ByteArrayOutputStream()

        val md5Result = outputStream.sink().use { sink ->
          val hash = HashingSink.md5(sink)
          source?.readAll(hash)
          hash.hash.toByteArray()
        }

        if (!MessageDigest.isEqual(md5, md5Result)) {
          Log.w(TAG, "Content mismatch when downloading s3 object. Deleting.")
          return ServiceResponse.forExecutionError(Md5FailureException())
        }

        return DefaultResponseMapper.extend(clazz)
          .withResponseMapper { status, body, _, _ -> ServiceResponse.forResult(JsonUtils.fromJson(body, clazz), status, body) }
          .build()
          .map(200, String(outputStream.toByteArray(), Charset.forName("UTF-8")), { "" }, false)
      }
    } catch (e: IOException) {
      Log.w(TAG, "Unable to get and verify", e)
      return ServiceResponse.forUnknownError(e)
    }
  }

  /**
   * This method will download content from the given network path, and store it at the given disk path. In addition, it will check and verify that the
   * body's content MD5 matches the MD5 embedded in the S3 ETAG. If there is a mismatch, the local content will be deleted.
   *
   * @param context Application context. This may be long-lived so it's important that the caller does not pass an Activity.
   * @param objectPathOnNetwork A fully formed URL to an S3 object containing the content to write to disk
   * @param objectFileOnDisk A File on disk that can be written to.
   * @param doNotEncrypt Defaults to false. It is generally an error to set this to true, and should only be used for writing font data.
   * @return true on success, false otherwise.
   */
  @WorkerThread
  fun verifyAndWriteToDisk(context: Context, objectPathOnNetwork: String, objectFileOnDisk: File, doNotEncrypt: Boolean = false): Boolean {
    val md5 = getObjectMD5(objectPathOnNetwork)
    if (md5 == null) {
      Log.w(TAG, "Failed to download s3 object MD5.")
      return false
    }

    try {
      if (objectFileOnDisk.exists()) {
        objectFileOnDisk.delete()
      }

      getObject(objectPathOnNetwork).use { response ->
        val source = response.body()?.source()

        val outputStream: OutputStream = if (doNotEncrypt) {
          FileOutputStream(objectFileOnDisk)
        } else {
          EncryptedStreamUtils.getOutputStream(context, objectFileOnDisk)
        }

        val md5Result = outputStream.sink().use { sink ->
          val hash = HashingSink.md5(sink)
          source?.readAll(hash)
          hash.hash.toByteArray()
        }

        if (!md5.contentEquals(md5Result)) {
          Log.w(TAG, "Content mismatch when downloading s3 object. Deleting.")
          objectFileOnDisk.delete()
          return false
        }
      }

      return true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to download s3 object", e)
      return false
    }
  }

  /**
   * Downloads and parses the ETAG from an S3 object, utilizing a HEAD request.
   */
  @WorkerThread
  fun getObjectMD5(endpoint: String): ByteArray? {
    val request = Request.Builder()
      .head()
      .url(endpoint)
      .build()

    try {
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return null
        }

        val md5 = getMD5FromResponse(response)
        return md5?.let { Hex.fromStringCondensed(md5) }
      }
    } catch (e: IOException) {
      Log.w(TAG, "Could not retrieve md5", e)
      return null
    }
  }

  /**
   * Parses the MD5 from a response.
   */
  private fun getMD5FromResponse(response: Response): String? {
    val pattern: Pattern = Pattern.compile(".*([a-f0-9]{32}).*")
    val header = response.header("etag") ?: return null
    val matcher: Matcher = pattern.matcher(header)

    return if (matcher.find()) {
      matcher.group(1)
    } else {
      null
    }
  }

  class Md5FailureException : IOException("Failed to getting or comparing MD5")
}
