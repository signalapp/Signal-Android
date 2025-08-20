package org.signal.core.util.stream

import org.signal.core.util.logging.Log
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Input stream that reads a file that is actively being written to.
 * Will read or wait to read (for the bytes to be available) until it reaches the end [bytesLength]
 * A use case is streamable video where we want to play the video while the file is still downloading
 */
class TailerInputStream(private val streamFactory: StreamFactory, private val bytesLength: Long) : FilterInputStream(streamFactory.openStream()) {

  private val TAG = Log.tag(TailerInputStream::class)

  /** Tracks where we are in the file */
  private var position: Long = 0

  private var currentStream: InputStream
    get() = this.`in`
    set(input) {
      this.`in` = input
    }

  override fun skip(requestedSkipCount: Long): Long {
    val bytesSkipped = this.currentStream.skip(requestedSkipCount)
    this.position += bytesSkipped

    return bytesSkipped
  }

  override fun read(): Int {
    val bytes = ByteArray(1)
    var result = this.read(bytes)
    while (result == 0) {
      result = this.read(bytes)
    }

    if (result == -1) {
      return result
    }

    return bytes[0].toInt() and 0xFF
  }

  override fun read(destination: ByteArray): Int {
    return this.read(destination = destination, offset = 0, length = destination.size)
  }

  override fun read(destination: ByteArray, offset: Int, length: Int): Int {
    // Checking if we reached the end of the file (bytesLength)
    if (position >= bytesLength) {
      return -1
    }

    var bytesRead = this.currentStream.read(destination, offset, length)

    // If we haven't read any bytes, but we aren't at the end of the file,
    // we close the stream, wait, and then try again
    while (bytesRead < 0 && position < bytesLength) {
      this.currentStream.close()
      try {
        Thread.sleep(100)
      } catch (e: InterruptedException) {
        Log.w(TAG, "Ignoring interrupted exception while waiting for input stream", e)
      }
      this.currentStream = streamFactory.openStream()
      // After reopening the file, we skip to the position we were at last time
      this.currentStream.skip(this.position)

      bytesRead = this.currentStream.read(destination, offset, length)
    }

    // Update current position with bytes read
    if (bytesRead > 0) {
      position += bytesRead
    }

    return bytesRead
  }
}

fun interface StreamFactory {
  @Throws(IOException::class)
  fun openStream(): InputStream
}
