package org.signal.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.ParcelFileDescriptor
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.FileUtils
import java.io.Closeable
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class MemoryFileDescriptor private constructor(
  private val parcelFileDescriptor: ParcelFileDescriptor,
  private val sizeEstimate: AtomicLong
) : Closeable {

  val fileDescriptor: FileDescriptor
    get() = parcelFileDescriptor.fileDescriptor

  val parcelFd: ParcelFileDescriptor
    get() = parcelFileDescriptor

  @Throws(IOException::class)
  fun seek(position: Long) {
    FileInputStream(fileDescriptor).use { stream ->
      stream.channel.position(position)
    }
  }

  @Throws(IOException::class)
  fun size(): Long {
    FileInputStream(fileDescriptor).use { stream ->
      return stream.channel.size()
    }
  }

  @Throws(IOException::class)
  override fun close() {
    try {
      clearAndRemoveAllocation()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to clear data in MemoryFileDescriptor", e)
    } finally {
      parcelFileDescriptor.close()
    }
  }

  @Throws(IOException::class)
  private fun clearAndRemoveAllocation() {
    clear()

    val oldEstimate = sizeEstimate.getAndSet(0)

    synchronized(MemoryFileDescriptor::class.java) {
      sizeOfAllMemoryFileDescriptors -= oldEstimate
    }
  }

  @Throws(IOException::class)
  private fun clear() {
    val size: Long
    FileInputStream(fileDescriptor).use { stream ->
      val channel = stream.channel
      size = channel.size()
      if (size == 0L) return
      channel.position(0)
    }

    val zeros = ByteArray(16 * 1024)
    var remaining = size

    FileOutputStream(fileDescriptor).use { output ->
      while (remaining > 0) {
        val limit = remaining.coerceAtMost(zeros.size.toLong()).toInt()
        output.write(zeros, 0, limit)
        remaining -= limit
      }
    }
  }

  open class MemoryFileException : IOException()
  private class MemoryLimitException : MemoryFileException()
  private class MemoryFileCreationException : MemoryFileException()

  companion object {
    private val TAG = Log.tag(MemoryFileDescriptor::class.java)

    private var sizeOfAllMemoryFileDescriptors: Long = 0

    @JvmStatic
    @Synchronized
    fun supported(): Boolean {
      return try {
        val fd = FileUtils.createMemoryFileDescriptor("CHECK")
        if (fd < 0) {
          Log.w(TAG, "MemoryFileDescriptor is not available.")
          false
        } else {
          ParcelFileDescriptor.adoptFd(fd).close()
          true
        }
      } catch (e: IOException) {
        Log.w(TAG, e)
        false
      }
    }

    @JvmStatic
    @Throws(MemoryFileException::class)
    fun newMemoryFileDescriptor(context: Context, debugName: String, sizeEstimate: Long): MemoryFileDescriptor {
      require(sizeEstimate >= 0)

      if (sizeEstimate > 0) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()

        synchronized(MemoryFileDescriptor::class.java) {
          activityManager.getMemoryInfo(memoryInfo)

          val remainingRam = memoryInfo.availMem - memoryInfo.threshold - sizeEstimate - sizeOfAllMemoryFileDescriptors

          if (remainingRam <= 0) {
            val numberFormat = NumberFormat.getInstance(Locale.US)
            Log.w(
              TAG,
              String.format(
                "Not enough RAM available without taking the system into a low memory state.%n" +
                  "Available: %s%n" +
                  "Low memory threshold: %s%n" +
                  "Requested: %s%n" +
                  "Total MemoryFileDescriptor limit: %s%n" +
                  "Shortfall: %s",
                numberFormat.format(memoryInfo.availMem),
                numberFormat.format(memoryInfo.threshold),
                numberFormat.format(sizeEstimate),
                numberFormat.format(sizeOfAllMemoryFileDescriptors),
                numberFormat.format(remainingRam)
              )
            )
            throw MemoryLimitException()
          }

          sizeOfAllMemoryFileDescriptors += sizeEstimate
        }
      }

      val fd = FileUtils.createMemoryFileDescriptor(debugName)

      if (fd < 0) {
        Log.w(TAG, "Failed to create file descriptor: $fd")
        throw MemoryFileCreationException()
      }

      return MemoryFileDescriptor(ParcelFileDescriptor.adoptFd(fd), AtomicLong(sizeEstimate))
    }
  }
}
