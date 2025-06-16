/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.crypto

import org.signal.core.util.stream.LimitedInputStream
import org.signal.core.util.stream.LimitedInputStream.Companion.withoutLimits
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice
import org.signal.libsignal.protocol.incrementalmac.IncrementalMacInputStream
import org.signal.libsignal.protocol.kdf.HKDF
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey.MediaKeyMaterial
import org.whispersystems.signalservice.internal.util.Util
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.annotation.Nonnull
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.Mac
import javax.crypto.ShortBufferException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Class for streaming an encrypted push attachment off disk.
 *
 * @author Moxie Marlinspike
 */
class AttachmentCipherInputStream private constructor(
  inputStream: InputStream,
  aesKey: ByteArray,
  private val totalDataSize: Long
) : FilterInputStream(inputStream) {

  private val cipher: Cipher

  private var done = false
  private var totalRead: Long = 0
  private var overflowBuffer: ByteArray? = null

  init {
    val iv = ByteArray(BLOCK_SIZE)
    readFullyWithoutDecrypting(iv)

    this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
  }

  @Throws(IOException::class)
  override fun read(): Int {
    val buffer = ByteArray(1)
    var read: Int = read(buffer)
    while (read == 0) {
      read = read(buffer)
    }

    if (read == -1) {
      return read
    }

    return buffer[0].toInt() and 0xFF
  }

  @Throws(IOException::class)
  override fun read(@Nonnull buffer: ByteArray): Int {
    return read(buffer, 0, buffer.size)
  }

  @Throws(IOException::class)
  override fun read(@Nonnull buffer: ByteArray, offset: Int, length: Int): Int {
    return if (totalRead != totalDataSize) {
      readIncremental(buffer, offset, length)
    } else if (!done) {
      readFinal(buffer, offset, length)
    } else {
      -1
    }
  }

  override fun markSupported(): Boolean = false

  @Throws(IOException::class)
  override fun skip(byteCount: Long): Long {
    var skipped = 0L
    while (skipped < byteCount) {
      val remaining = byteCount - skipped
      val buffer = ByteArray(min(4096, remaining.toInt()))
      val read = read(buffer)

      skipped += read.toLong()
    }

    return skipped
  }

  @Throws(IOException::class)
  private fun readIncremental(outputBuffer: ByteArray, originalOffset: Int, originalLength: Int): Int {
    var offset = originalOffset
    var length = originalLength
    var readLength = 0

    overflowBuffer?.let { overflow ->
      if (overflow.size > length) {
        overflow.copyInto(destination = outputBuffer, destinationOffset = offset, endIndex = length)
        overflowBuffer = overflow.copyOfRange(fromIndex = length, toIndex = overflow.size)
        return length
      } else if (overflow.size == length) {
        overflow.copyInto(destination = outputBuffer, destinationOffset = offset)
        overflowBuffer = null
        return length
      } else {
        overflow.copyInto(destination = outputBuffer, destinationOffset = offset)
        readLength += overflow.size
        offset += readLength
        length -= readLength
        overflowBuffer = null
      }
    }

    if (length + totalRead > totalDataSize) {
      length = (totalDataSize - totalRead).toInt()
    }

    val ciphertextBuffer = ByteArray(length)
    val ciphertextReadLength = if (ciphertextBuffer.size <= cipher.blockSize) {
      ciphertextBuffer.size
    } else {
      // Ensure we leave the final block for readFinal()
      ciphertextBuffer.size - cipher.blockSize
    }
    val ciphertextRead = super.read(ciphertextBuffer, 0, ciphertextReadLength)
    totalRead += ciphertextRead.toLong()

    try {
      var plaintextLength = cipher.getOutputSize(ciphertextRead)

      if (plaintextLength <= length) {
        readLength += cipher.update(ciphertextBuffer, 0, ciphertextRead, outputBuffer, offset)
        return readLength
      }

      val plaintextBuffer = ByteArray(plaintextLength)
      plaintextLength = cipher.update(ciphertextBuffer, 0, ciphertextRead, plaintextBuffer, 0)
      if (plaintextLength <= length) {
        plaintextBuffer.copyInto(destination = outputBuffer, destinationOffset = offset, endIndex = plaintextLength)
        readLength += plaintextLength
      } else {
        plaintextBuffer.copyInto(destination = outputBuffer, destinationOffset = offset, endIndex = length)
        overflowBuffer = plaintextBuffer.copyOfRange(fromIndex = length, toIndex = plaintextLength)
        readLength += length
      }
      return readLength
    } catch (e: ShortBufferException) {
      throw AssertionError(e)
    }
  }

  @Throws(IOException::class)
  private fun readFinal(buffer: ByteArray, offset: Int, length: Int): Int {
    try {
      val internal = ByteArray(buffer.size)
      val actualLength = min(length, cipher.doFinal(internal, 0))
      internal.copyInto(destination = buffer, destinationOffset = offset, endIndex = actualLength)

      done = true
      return actualLength
    } catch (e: IllegalBlockSizeException) {
      throw IOException(e)
    } catch (e: BadPaddingException) {
      throw IOException(e)
    } catch (e: ShortBufferException) {
      throw IOException(e)
    }
  }

  @Throws(IOException::class)
  private fun readFullyWithoutDecrypting(buffer: ByteArray) {
    var offset = 0

    while (true) {
      val read = super.read(buffer, offset, buffer.size - offset)

      if (read + offset < buffer.size) {
        offset += read
      } else {
        return
      }
    }
  }

  fun interface StreamSupplier {
    @Nonnull
    @Throws(IOException::class)
    fun openStream(): InputStream
  }

  companion object {
    private const val BLOCK_SIZE = 16
    private const val CIPHER_KEY_SIZE = 32
    private const val MAC_KEY_SIZE = 32

    /**
     * Passing in a null incrementalDigest and/or 0 for the chunk size at the call site disables incremental mac validation.
     *
     * Passing in true for ignoreDigest DOES NOT VERIFY THE DIGEST
     */
    @JvmStatic
    @JvmOverloads
    @Throws(InvalidMessageException::class, IOException::class)
    fun createForAttachment(file: File, plaintextLength: Long, combinedKeyMaterial: ByteArray?, digest: ByteArray?, incrementalDigest: ByteArray?, incrementalMacChunkSize: Int, ignoreDigest: Boolean = false): LimitedInputStream {
      return createForAttachment({ FileInputStream(file) }, file.length(), plaintextLength, combinedKeyMaterial, digest, incrementalDigest, incrementalMacChunkSize, ignoreDigest)
    }

    /**
     * Passing in a null incrementalDigest and/or 0 for the chunk size at the call site disables incremental mac validation.
     *
     * Passing in true for ignoreDigest DOES NOT VERIFY THE DIGEST
     */
    @JvmStatic
    @Throws(InvalidMessageException::class, IOException::class)
    fun createForAttachment(
      streamSupplier: StreamSupplier,
      streamLength: Long,
      plaintextLength: Long,
      combinedKeyMaterial: ByteArray?,
      digest: ByteArray?,
      incrementalDigest: ByteArray?,
      incrementalMacChunkSize: Int,
      ignoreDigest: Boolean
    ): LimitedInputStream {
      val parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE)
      val mac = initMac(parts[1])

      if (streamLength <= BLOCK_SIZE + mac.macLength) {
        throw InvalidMessageException("Message shorter than crypto overhead! length: $streamLength")
      }

      if (!ignoreDigest && digest == null) {
        throw InvalidMessageException("Missing digest!")
      }

      val wrappedStream: InputStream
      val hasIncrementalMac = incrementalDigest != null && incrementalDigest.isNotEmpty() && incrementalMacChunkSize > 0

      if (!hasIncrementalMac) {
        streamSupplier.openStream().use { macVerificationStream ->
          verifyMac(macVerificationStream, streamLength, mac, digest)
        }
        wrappedStream = streamSupplier.openStream()
      } else {
        wrappedStream = IncrementalMacInputStream(
          IncrementalMacAdditionalValidationsInputStream(
            streamSupplier.openStream(),
            streamLength,
            mac,
            digest!!
          ),
          parts[1],
          ChunkSizeChoice.everyNthByte(incrementalMacChunkSize),
          incrementalDigest
        )
      }
      val inputStream: InputStream = AttachmentCipherInputStream(wrappedStream, parts[0], streamLength - BLOCK_SIZE - mac.macLength)

      return LimitedInputStream(inputStream, plaintextLength)
    }

    /**
     * Decrypt archived media to it's original attachment encrypted blob.
     */
    @JvmStatic
    @Throws(InvalidMessageException::class, IOException::class)
    fun createForArchivedMedia(archivedMediaKeyMaterial: MediaKeyMaterial, file: File, originalCipherTextLength: Long): LimitedInputStream {
      val mac = initMac(archivedMediaKeyMaterial.macKey)

      if (file.length() <= BLOCK_SIZE + mac.macLength) {
        throw InvalidMessageException("Message shorter than crypto overhead!")
      }

      FileInputStream(file).use { macVerificationStream ->
        verifyMac(macVerificationStream, file.length(), mac, null)
      }
      val inputStream: InputStream = AttachmentCipherInputStream(FileInputStream(file), archivedMediaKeyMaterial.aesKey, file.length() - BLOCK_SIZE - mac.macLength)

      return LimitedInputStream(inputStream, originalCipherTextLength)
    }

    @JvmStatic
    @Throws(InvalidMessageException::class, IOException::class)
    fun createStreamingForArchivedAttachment(
      archivedMediaKeyMaterial: MediaKeyMaterial,
      file: File,
      originalCipherTextLength: Long,
      plaintextLength: Long,
      combinedKeyMaterial: ByteArray?,
      digest: ByteArray,
      incrementalDigest: ByteArray?,
      incrementalMacChunkSize: Int
    ): LimitedInputStream {
      val archiveStream: InputStream = createForArchivedMedia(archivedMediaKeyMaterial, file, originalCipherTextLength)

      val parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE)
      val mac = initMac(parts[1])

      if (originalCipherTextLength <= BLOCK_SIZE + mac.macLength) {
        throw InvalidMessageException("Message shorter than crypto overhead!")
      }

      val wrappedStream: InputStream = IncrementalMacInputStream(
        IncrementalMacAdditionalValidationsInputStream(
          wrapped = archiveStream,
          fileLength = file.length(),
          mac = mac,
          theirDigest = digest
        ),
        parts[1],
        ChunkSizeChoice.everyNthByte(incrementalMacChunkSize),
        incrementalDigest
      )

      val inputStream: InputStream = AttachmentCipherInputStream(
        inputStream = wrappedStream,
        aesKey = parts[0],
        totalDataSize = file.length() - BLOCK_SIZE - mac.macLength
      )

      return if (plaintextLength != 0L) {
        LimitedInputStream(inputStream, plaintextLength)
      } else {
        withoutLimits(inputStream)
      }
    }

    @JvmStatic
    @Throws(InvalidMessageException::class, IOException::class)
    fun createForStickerData(data: ByteArray, packKey: ByteArray?): InputStream {
      val combinedKeyMaterial = HKDF.deriveSecrets(packKey, "Sticker Pack".toByteArray(), 64)
      val parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE)
      val mac = initMac(parts[1])

      if (data.size <= BLOCK_SIZE + mac.macLength) {
        throw InvalidMessageException("Message shorter than crypto overhead!")
      }

      ByteArrayInputStream(data).use { inputStream ->
        verifyMac(inputStream, data.size.toLong(), mac, null)
      }
      return AttachmentCipherInputStream(ByteArrayInputStream(data), parts[0], (data.size - BLOCK_SIZE - mac.macLength).toLong())
    }

    private fun initMac(key: ByteArray): Mac {
      try {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac
      } catch (e: NoSuchAlgorithmException) {
        throw AssertionError(e)
      } catch (e: InvalidKeyException) {
        throw AssertionError(e)
      }
    }

    @Throws(InvalidMessageException::class)
    private fun verifyMac(@Nonnull inputStream: InputStream, length: Long, @Nonnull mac: Mac, theirDigest: ByteArray?) {
      try {
        val digest = MessageDigest.getInstance("SHA256")
        var remainingData = Util.toIntExact(length) - mac.macLength
        val buffer = ByteArray(4096)

        while (remainingData > 0) {
          val read = inputStream.read(buffer, 0, min(buffer.size, remainingData))
          mac.update(buffer, 0, read)
          digest.update(buffer, 0, read)
          remainingData -= read
        }

        val ourMac = mac.doFinal()
        val theirMac = ByteArray(mac.macLength)
        Util.readFully(inputStream, theirMac)

        if (!MessageDigest.isEqual(ourMac, theirMac)) {
          throw InvalidMessageException("MAC doesn't match!")
        }

        val ourDigest = digest.digest(theirMac)

        if (theirDigest != null && !MessageDigest.isEqual(ourDigest, theirDigest)) {
          throw InvalidMessageException("Digest doesn't match!")
        }
      } catch (e: IOException) {
        throw InvalidMessageException(e)
      } catch (e: ArithmeticException) {
        throw InvalidMessageException(e)
      } catch (e: NoSuchAlgorithmException) {
        throw AssertionError(e)
      }
    }
  }
}
