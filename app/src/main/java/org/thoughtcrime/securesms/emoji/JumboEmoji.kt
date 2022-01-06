package org.thoughtcrime.securesms.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.MainThread
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.impl.AutoDownloadEmojiConstraint
import org.thoughtcrime.securesms.util.ListenableFutureTask
import org.thoughtcrime.securesms.util.SoftHashMap
import org.thoughtcrime.securesms.util.concurrent.SimpleTask
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutionException

private val TAG = Log.tag(JumboEmoji::class.java)

/**
 * For Jumbo Emojis, will download, add to in-memory cache, and load from disk.
 */
object JumboEmoji {

  const val MAX_JUMBOJI_COUNT = 5

  private val cache: MutableMap<String, Bitmap> = SoftHashMap(16)
  private val tasks: MutableMap<String, ListenableFutureTask<Bitmap>> = hashMapOf()
  private val versionToFormat: MutableMap<UUID, String?> = hashMapOf()

  @Suppress("FoldInitializerAndIfToElvis")
  @JvmStatic
  @MainThread
  fun loadJumboEmoji(context: Context, rawEmoji: String): LoadResult {
    val applicationContext: Context = context.applicationContext

    val name: String = "jumbo/$rawEmoji"
    val bitmap: Bitmap? = cache[name]
    val task: ListenableFutureTask<Bitmap>? = tasks[name]

    if (bitmap != null) {
      return LoadResult.Immediate(bitmap)
    }

    if (task != null) {
      return LoadResult.Async(task)
    }

    val newTask = ListenableFutureTask<Bitmap> {
      val version: EmojiFiles.Version? = EmojiFiles.Version.readVersion(applicationContext, true)
      if (version == null) {
        throw NoVersionData()
      }

      val format: String? = versionToFormat.getOrPut(version.uuid) {
        EmojiFiles.getLatestEmojiData(context, version)?.format
      }

      if (format == null) {
        throw NoVersionData()
      }

      var jumbos: EmojiFiles.JumboCollection = EmojiFiles.JumboCollection.read(applicationContext, version)

      val uuid = jumbos.getUUIDForName(name)

      if (uuid == null) {
        if (!AutoDownloadEmojiConstraint.canAutoDownloadEmoji(applicationContext)) {
          throw CannotAutoDownload()
        }

        Log.i(TAG, "No file for emoji, downloading jumbo")
        val emojiFilesName: EmojiFiles.Name = EmojiDownloader.downloadAndVerifyImageFromRemote(applicationContext, version, version.density, name, format)
        jumbos = EmojiFiles.JumboCollection.append(applicationContext, jumbos, emojiFilesName)
      }

      val inputStream = EmojiFiles.openForReadingJumbo(applicationContext, version, jumbos, name)
      inputStream.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options()) }
    }

    tasks[name] = newTask

    SimpleTask.run(SignalExecutors.SERIAL, newTask::run) {
      try {
        val newBitmap: Bitmap? = newTask.get()
        if (newBitmap == null) {
          Log.w(TAG, "Failed to load jumbo emoji")
        } else {
          cache[name] = newBitmap
        }
      } catch (e: ExecutionException) {
        Log.d(TAG, "Failed to load jumbo emoji", e.cause)
      } finally {
        tasks.remove(name)
      }
    }

    return LoadResult.Async(newTask)
  }

  class NoVersionData : Throwable()
  class CannotAutoDownload : IOException()

  sealed class LoadResult {
    data class Immediate(val bitmap: Bitmap) : LoadResult()
    data class Async(val task: ListenableFutureTask<Bitmap>) : LoadResult()
  }
}
