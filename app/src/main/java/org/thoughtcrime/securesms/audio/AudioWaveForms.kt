package org.thoughtcrime.securesms.audio

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.annotation.AnyThread
import androidx.annotation.RequiresApi
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Uses [AudioWaveFormGenerator] to generate audio wave forms.
 *
 * Maintains an in-memory cache of recently requested wave forms.
 */
@RequiresApi(23)
object AudioWaveForms {

  private val TAG = Log.tag(AudioWaveForms::class.java)

  private val cache = ThreadSafeLruCache(200)

  @AnyThread
  @JvmStatic
  fun getWaveForm(context: Context, attachment: Attachment): Single<AudioFileInfo> {
    val uri = attachment.uri
    if (uri == null) {
      Log.i(TAG, "No uri")
      return Single.error(IllegalArgumentException("No uri from attachment"))
    }

    val cacheKey = uri.toString()
    val cachedInfo = cache.get(cacheKey)
    if (cachedInfo != null) {
      Log.i(TAG, "Loaded wave form from cache $cacheKey")
      return Single.just(cachedInfo)
    }

    val databaseCache = Single.fromCallable {
      val audioHash = attachment.audioHash
      return@fromCallable if (audioHash != null) {
        checkDatabaseCache(cacheKey, audioHash.audioWaveForm)
      } else {
        Miss
      }
    }.subscribeOn(Schedulers.io())

    val generateWaveForm: Single<CacheCheckResult> = if (attachment is DatabaseAttachment) {
      Single.fromCallable { generateWaveForm(context, uri, cacheKey, attachment.attachmentId) }
    } else {
      Single.fromCallable { generateWaveForm(context, uri, cacheKey) }
    }.subscribeOn(Schedulers.io())

    return databaseCache
      .flatMap { r ->
        if (r is Miss) {
          generateWaveForm
        } else {
          Single.just(r)
        }
      }
      .map { r ->
        if (r is Success) {
          r.audioFileInfo
        } else {
          throw IOException("Unable to generate wave form")
        }
      }
  }

  private fun checkDatabaseCache(cacheKey: String, audioWaveForm: AudioWaveFormData): CacheCheckResult {
    val audioFileInfo = AudioFileInfo.fromDatabaseProtobuf(audioWaveForm)
    if (audioFileInfo.waveForm.isEmpty()) {
      Log.w(TAG, "Recovering from a wave form generation error  $cacheKey")
      return Failure
    } else if (audioFileInfo.waveForm.size != AudioWaveFormGenerator.BAR_COUNT) {
      Log.w(TAG, "Wave form from database does not match bar count, regenerating $cacheKey")
    } else {
      cache.put(cacheKey, audioFileInfo)
      Log.i(TAG, "Loaded wave form from DB $cacheKey")
      return Success(audioFileInfo)
    }

    return Miss
  }

  private fun generateWaveForm(context: Context, uri: Uri, cacheKey: String, attachmentId: AttachmentId): CacheCheckResult {
    try {
      val startTime = System.currentTimeMillis()
      SignalDatabase.attachments.writeAudioHash(attachmentId, AudioWaveFormData.getDefaultInstance())

      Log.i(TAG, "Starting wave form generation ($cacheKey)")
      val fileInfo: AudioFileInfo = AudioWaveFormGenerator.generateWaveForm(context, uri)
      Log.i(TAG, "Audio wave form generation time ${System.currentTimeMillis() - startTime} ms ($cacheKey)")

      SignalDatabase.attachments.writeAudioHash(attachmentId, fileInfo.toDatabaseProtobuf())
      cache.put(cacheKey, fileInfo)

      return Success(fileInfo)
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to create audio wave form for $cacheKey", e)
      return Failure
    }
  }

  private fun generateWaveForm(context: Context, uri: Uri, cacheKey: String): CacheCheckResult {
    try {
      Log.i(TAG, "Not in database and not cached. Generating wave form on-the-fly.")

      val startTime = System.currentTimeMillis()

      Log.i(TAG, "Starting wave form generation ($cacheKey)")
      val fileInfo: AudioFileInfo = AudioWaveFormGenerator.generateWaveForm(context, uri)
      Log.i(TAG, "Audio wave form generation time ${System.currentTimeMillis() - startTime} ms ($cacheKey)")

      cache.put(cacheKey, fileInfo)

      return Success(fileInfo)
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to create audio wave form for $cacheKey", e)
      return Failure
    }
  }

  private class ThreadSafeLruCache(maxSize: Int) {
    private val cache = LruCache<String, AudioFileInfo>(maxSize)
    private val lock = ReentrantReadWriteLock()

    fun put(key: String, info: AudioFileInfo) {
      lock.write { cache.put(key, info) }
    }

    fun get(key: String): AudioFileInfo? {
      return lock.read { cache.get(key) }
    }
  }

  private sealed class CacheCheckResult
  private class Success(val audioFileInfo: AudioFileInfo) : CacheCheckResult()
  private object Failure : CacheCheckResult()
  private object Miss : CacheCheckResult()
}
