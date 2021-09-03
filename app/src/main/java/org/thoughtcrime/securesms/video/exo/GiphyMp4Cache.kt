package org.thoughtcrime.securesms.video.exo

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.storage.FileStorage
import java.io.IOException
import java.io.InputStream

/**
 * A simple disk cache for MP4 GIFS. While entries are stored on disk, the data has lifecycle of a single application session and will be cleared every app
 * start. This lets us keep stuff simple and maintain all of our metadata and state in memory.
 *
 * Features
 * - Write entire files into the cache
 * - Keep entries that are actively being read in the cache by maintaining locks on entries
 * - When the cache is over the size limit, inactive entries will be evicted in LRU order.
 */
class GiphyMp4Cache(private val maxSize: Long) {

  companion object {
    private val TAG = Log.tag(GiphyMp4Cache::class.java)
    private val DATA_LOCK = Object()
    private const val DIRECTORY = "mp4gif_cache"
    private const val PREFIX = "entry_"
    private const val EXTENSION = "mp4"
  }

  private val lockedUris: MutableSet<Uri> = mutableSetOf()
  private val uriToEntry: MutableMap<Uri, Entry> = mutableMapOf()

  @WorkerThread
  fun onAppStart(context: Context) {
    synchronized(DATA_LOCK) {
      lockedUris.clear()
      for (file in FileStorage.getAllFiles(context, DIRECTORY, PREFIX)) {
        if (!file.delete()) {
          Log.w(TAG, "Failed to delete: " + file.name)
        }
      }
    }
  }

  @Throws(IOException::class)
  fun write(context: Context, uri: Uri, inputStream: InputStream): ReadData {
    synchronized(DATA_LOCK) {
      lockedUris.add(uri)
    }

    val filename: String = FileStorage.save(context, inputStream, DIRECTORY, PREFIX, EXTENSION)
    val size = FileStorage.getFile(context, DIRECTORY, filename).length()

    synchronized(DATA_LOCK) {
      uriToEntry[uri] = Entry(
        uri = uri,
        filename = filename,
        size = size,
        lastAccessed = System.currentTimeMillis()
      )
    }

    return readFromStorage(context, uri) ?: throw IOException("Could not find file immediately after writing!")
  }

  fun read(context: Context, uri: Uri): ReadData? {
    synchronized(DATA_LOCK) {
      lockedUris.add(uri)
    }

    return try {
      readFromStorage(context, uri)
    } catch (e: IOException) {
      null
    }
  }

  @Throws(IOException::class)
  fun readFromStorage(context: Context, uri: Uri): ReadData? {
    val entry: Entry = synchronized(DATA_LOCK) {
      uriToEntry[uri]
    } ?: return null

    val length: Long = FileStorage.getFile(context, DIRECTORY, entry.filename).length()
    val inputStream: InputStream = FileStorage.read(context, DIRECTORY, entry.filename)
    return ReadData(inputStream, length) { onEntryReleased(context, uri) }
  }

  private fun onEntryReleased(context: Context, uri: Uri) {
    synchronized(DATA_LOCK) {
      lockedUris.remove(uri)

      var totalSize: Long = calculateTotalSize(uriToEntry)

      if (totalSize > maxSize) {
        val evictCandidatesInLruOrder: MutableList<Entry> = ArrayList(
          uriToEntry.entries
            .filter { e -> !lockedUris.contains(e.key) }
            .map { e -> e.value }
            .sortedBy { e -> e.lastAccessed }
        )

        while (totalSize > maxSize && evictCandidatesInLruOrder.isNotEmpty()) {
          val toEvict: Entry = evictCandidatesInLruOrder.removeAt(0)

          if (!FileStorage.getFile(context, DIRECTORY, toEvict.filename).delete()) {
            Log.w(TAG, "Failed to delete ${toEvict.filename}")
          }

          uriToEntry.remove(toEvict.uri)

          totalSize = calculateTotalSize(uriToEntry)
        }
      }
    }
  }

  private fun calculateTotalSize(data: Map<Uri, Entry>): Long {
    return data.values.map { e -> e.size }.reduceOrNull { sum, size -> sum + size } ?: 0
  }

  fun interface Lease {
    fun release()
  }

  private data class Entry(val uri: Uri, val filename: String, val size: Long, val lastAccessed: Long)

  data class ReadData(val inputStream: InputStream, val length: Long, val lease: Lease) {
    fun release() {
      StreamUtil.close(inputStream)
      lease.release()
    }
  }
}
