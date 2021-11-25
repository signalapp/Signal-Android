package org.thoughtcrime.securesms.glide.cache

import org.signal.glide.common.io.Reader
import java.io.IOException

/**
 * Restrict the number of bytes that can be read to prevent breaking the input stream in Glide's
 * eyes.
 */
class LimitedReader(private val reader: Reader, private val readLimit: Int) : Reader by reader {
  @Throws(IOException::class)
  override fun read(buffer: ByteArray?, start: Int, byteCount: Int): Int {
    if (position() + byteCount >= readLimit) {
      throw IOException("Read limit exceeded")
    }
    return reader.read(buffer, start, byteCount)
  }

  @Throws(IOException::class)
  override fun skip(total: Long): Long {
    if (position() + total >= readLimit) {
      throw IOException("Read limit exceeded")
    }
    return reader.skip(total)
  }
}
