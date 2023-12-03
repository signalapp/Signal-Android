package org.whispersystems.signalservice.internal.push.http

import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice
import org.signal.libsignal.protocol.incrementalmac.IncrementalMacOutputStream
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Creates [AttachmentCipherOutputStream] using the provided [key] and [iv].
 *
 * [createFor] is straightforward, and is the legacy behavior.
 * [createIncrementalFor] first wraps the stream in an [IncrementalMacOutputStream] to calculate MAC digests on chunks as the stream is written to.
 *
 * @property key
 * @property iv
 */
class AttachmentCipherOutputStreamFactory(private val key: ByteArray, private val iv: ByteArray) : OutputStreamFactory {
  companion object {
    private const val AES_KEY_LENGTH = 32
  }

  @Throws(IOException::class)
  override fun createFor(wrap: OutputStream): DigestingOutputStream {
    return AttachmentCipherOutputStream(key, iv, wrap)
  }

  @Throws(IOException::class)
  fun createIncrementalFor(wrap: OutputStream?, length: Long, sizeChoice: ChunkSizeChoice, incrementalDigestOut: OutputStream?): DigestingOutputStream {
    if (length > Int.MAX_VALUE) {
      throw IllegalArgumentException("Attachment length overflows int!")
    }

    val privateKey = key.sliceArray(AES_KEY_LENGTH until key.size)
    val incrementalStream = IncrementalMacOutputStream(wrap, privateKey, sizeChoice, incrementalDigestOut)
    return createFor(incrementalStream)
  }
}
