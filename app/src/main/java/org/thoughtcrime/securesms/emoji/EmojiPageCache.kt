package org.thoughtcrime.securesms.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.ListenableFutureTask
import org.thoughtcrime.securesms.util.SoftHashMap
import org.thoughtcrime.securesms.util.concurrent.SimpleTask
import java.io.IOException
import java.io.InputStream

object EmojiPageCache {

  private val TAG = Log.tag(EmojiPageCache::class.java)

  private val cache: SoftHashMap<EmojiPageRequest, Bitmap> = SoftHashMap()
  private val tasks: HashMap<EmojiPageRequest, ListenableFutureTask<Bitmap>> = hashMapOf()

  @MainThread
  fun load(context: Context, emojiPage: EmojiPage, inSampleSize: Int): LoadResult {
    val applicationContext = context.applicationContext
    val emojiPageRequest = EmojiPageRequest(emojiPage, inSampleSize)
    val bitmap: Bitmap? = cache[emojiPageRequest]
    val task: ListenableFutureTask<Bitmap>? = tasks[emojiPageRequest]

    return when {
      bitmap != null -> LoadResult.Immediate(bitmap)
      task != null -> LoadResult.Async(task)
      else -> {
        val newTask = ListenableFutureTask<Bitmap> {
          try {
            Log.i(TAG, "Loading page $emojiPageRequest")
            loadInternal(applicationContext, emojiPageRequest)
          } catch (e: IOException) {
            Log.w(TAG, e)
            null
          }
        }

        tasks[emojiPageRequest] = newTask

        SimpleTask.run(newTask::run) {
          try {
            val newBitmap: Bitmap? = newTask.get()
            if (newBitmap == null) {
              Log.w(TAG, "Failed to load emoji bitmap for request $emojiPageRequest")
            } else {
              cache[emojiPageRequest] = newBitmap
            }
          } finally {
            tasks.remove(emojiPageRequest)
          }
        }

        LoadResult.Async(newTask)
      }
    }
  }

  fun clear() {
    cache.clear()
  }

  @WorkerThread
  private fun loadInternal(context: Context, emojiPageRequest: EmojiPageRequest): Bitmap? {
    val inputStream: InputStream = when (emojiPageRequest.emojiPage) {
      is EmojiPage.Asset -> context.assets.open(emojiPageRequest.emojiPage.uri.toString().replace("file:///android_asset/", ""))
      is EmojiPage.Disk -> EmojiFiles.openForReading(context, PartAuthority.getEmojiFilename(emojiPageRequest.emojiPage.uri))
    }

    val bitmapOptions = BitmapFactory.Options()
    bitmapOptions.inSampleSize = emojiPageRequest.inSampleSize

    return inputStream.use { BitmapFactory.decodeStream(it, null, bitmapOptions) }
  }

  private data class EmojiPageRequest(val emojiPage: EmojiPage, val inSampleSize: Int)

  sealed class LoadResult {
    data class Immediate(val bitmap: Bitmap) : LoadResult()
    data class Async(val task: ListenableFutureTask<Bitmap>) : LoadResult()
  }
}
