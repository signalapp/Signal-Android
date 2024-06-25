package org.thoughtcrime.securesms.jobs

import okhttp3.ResponseBody
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.AutoDownloadEmojiConstraint
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.s3.S3
import org.thoughtcrime.securesms.transport.RetryLaterException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetch an image associated with a remote megaphone.
 */
class FetchRemoteMegaphoneImageJob(parameters: Parameters, private val uuid: String, private val imageUrl: String) : BaseJob(parameters) {

  constructor(uuid: String, imageUrl: String) : this(
    parameters = Parameters.Builder()
      .setQueue(KEY)
      .addConstraint(AutoDownloadEmojiConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(TimeUnit.DAYS.toMillis(7))
      .build(),
    uuid = uuid,
    imageUrl = imageUrl
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_UUID, uuid)
      .putString(KEY_IMAGE_URL, imageUrl)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    try {
      S3.getObject(imageUrl).use { response ->
        val body: ResponseBody? = response.body
        if (body != null) {
          val uri = BlobProvider.getInstance()
            .forData(body.byteStream(), body.contentLength())
            .createForMultipleSessionsOnDisk(context)

          SignalDatabase.remoteMegaphones.setImageUri(uuid, uri)
        }
      }
    } catch (e: IOException) {
      Log.i(TAG, "Encountered unknown IO error while fetching image for $uuid", e)
      throw RetryLaterException(e)
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = e is RetryLaterException

  override fun onFailure() {
    Log.i(TAG, "Failed to fetch image for $uuid, clearing to present without one")
    SignalDatabase.remoteMegaphones.clearImageUrl(uuid)
  }

  class Factory : Job.Factory<FetchRemoteMegaphoneImageJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): FetchRemoteMegaphoneImageJob {
      val data = JsonJobData.deserialize(serializedData)
      return FetchRemoteMegaphoneImageJob(parameters, data.getString(KEY_UUID), data.getString(KEY_IMAGE_URL))
    }
  }

  companion object {
    const val KEY = "FetchRemoteMegaphoneImageJob"

    private val TAG = Log.tag(FetchRemoteMegaphoneImageJob::class.java)

    private const val KEY_UUID = "uuid"
    private const val KEY_IMAGE_URL = "image_url"
  }
}
