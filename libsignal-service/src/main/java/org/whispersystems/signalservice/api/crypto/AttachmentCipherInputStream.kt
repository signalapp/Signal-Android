/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.crypto

import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.stream.LimitedInputStream
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice
import org.signal.libsignal.protocol.incrementalmac.IncrementalMacInputStream
import org.signal.libsignal.protocol.kdf.HKDF
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey.MediaKeyMaterial
import org.whispersystems.signalservice.internal.util.Util
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.annotation.Nonnull
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Decrypts an attachment stream that has been encrypted with AES/CBC/PKCS5Padding.
 *
 * It assumes that the first 16 bytes of the stream are the IV, and that the rest of the stream is encrypted data.
 */
object AttachmentCipherInputStream {

  private const val BLOCK_SIZE = 16
  private const val CIPHER_KEY_SIZE = 32
  private const val MAC_KEY_SIZE = 32

  /**
   * Creates a stream to decrypt a typical attachment via a [File].
   *
   * @param incrementalDigest If null, incremental mac validation is disabled.
   * @param incrementalMacChunkSize If 0, incremental mac validation is disabled.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  fun createForAttachment(
    file: File,
    plaintextLength: Long,
    combinedKeyMaterial: ByteArray,
    digest: ByteArray,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int
  ): LimitedInputStream {
    return create(
      streamSupplier = { FileInputStream(file) },
      streamLength = file.length(),
      plaintextLength = plaintextLength,
      combinedKeyMaterial = combinedKeyMaterial,
      digest = digest,
      incrementalDigest = incrementalDigest,
      incrementalMacChunkSize = incrementalMacChunkSize,
      ignoreDigest = false
    )
  }

  /**
   * Creates a stream to decrypt a typical attachment via a [StreamSupplier].
   *
   * @param incrementalDigest If null, incremental mac validation is disabled.
   * @param incrementalMacChunkSize If 0, incremental mac validation is disabled.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  fun createForAttachment(
    streamSupplier: StreamSupplier,
    streamLength: Long,
    plaintextLength: Long,
    combinedKeyMaterial: ByteArray,
    digest: ByteArray,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int
  ): LimitedInputStream {
    return create(
      streamSupplier = streamSupplier,
      streamLength = streamLength,
      plaintextLength = plaintextLength,
      combinedKeyMaterial = combinedKeyMaterial,
      digest = digest,
      incrementalDigest = incrementalDigest,
      incrementalMacChunkSize = incrementalMacChunkSize,
      ignoreDigest = false
    )
  }

  /**
   * When you archive an attachment, you give the server an encrypted attachment, and the server wraps it in *another* layer of encryption.
   *
   * This creates a stream decrypt both the inner and outer layers of an archived attachment at the same time by basically double-decrypting it.
   *
   * @param incrementalDigest If null, incremental mac validation is disabled.
   * @param incrementalMacChunkSize If 0, incremental mac validation is disabled.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  fun createForArchivedMedia(
    archivedMediaKeyMaterial: MediaKeyMaterial,
    file: File,
    originalCipherTextLength: Long,
    plaintextLength: Long,
    combinedKeyMaterial: ByteArray,
    digest: ByteArray,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int
  ): LimitedInputStream {
    val keyMaterial = CombinedKeyMaterial.from(combinedKeyMaterial)
    val mac = initMac(keyMaterial.macKey)

    if (originalCipherTextLength <= BLOCK_SIZE + mac.macLength) {
      throw InvalidMessageException("Message shorter than crypto overhead!")
    }

    return create(
      streamSupplier = { createForArchivedMediaOuterLayer(archivedMediaKeyMaterial, file, originalCipherTextLength) },
      streamLength = originalCipherTextLength,
      plaintextLength = plaintextLength,
      combinedKeyMaterial = combinedKeyMaterial,
      digest = digest,
      incrementalDigest = incrementalDigest,
      incrementalMacChunkSize = incrementalMacChunkSize,
      ignoreDigest = false
    )
  }

  /**
   * When you archive an attachment, you give the server an encrypted attachment, and the server wraps it in *another* layer of encryption.
   *
   * This creates a stream decrypt both the inner and outer layers of an archived attachment at the same time by basically double-decrypting it.
   *
   * @param incrementalDigest If null, incremental mac validation is disabled.
   * @param incrementalMacChunkSize If 0, incremental mac validation is disabled.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  fun createForArchivedThumbnail(
    archivedMediaKeyMaterial: MediaKeyMaterial,
    file: File,
    originalCipherTextLength: Long,
    plaintextLength: Long,
    combinedKeyMaterial: ByteArray
  ): LimitedInputStream {
    val keyMaterial = CombinedKeyMaterial.from(combinedKeyMaterial)
    val mac = initMac(keyMaterial.macKey)

    if (originalCipherTextLength <= BLOCK_SIZE + mac.macLength) {
      throw InvalidMessageException("Message shorter than crypto overhead!")
    }

    return create(
      streamSupplier = { createForArchivedMediaOuterLayer(archivedMediaKeyMaterial, file, originalCipherTextLength) },
      streamLength = originalCipherTextLength,
      plaintextLength = plaintextLength,
      combinedKeyMaterial = combinedKeyMaterial,
      digest = null,
      incrementalDigest = null,
      incrementalMacChunkSize = 0,
      ignoreDigest = true
    )
  }

  /**
   * Creates a stream to decrypt sticker data. Stickers have a special path because the key material is derived from the pack key.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  fun createForStickerData(data: ByteArray, packKey: ByteArray): InputStream {
    val keyMaterial = CombinedKeyMaterial.from(HKDF.deriveSecrets(packKey, "Sticker Pack".toByteArray(), 64))
    val mac = initMac(keyMaterial.macKey)

    if (data.size <= BLOCK_SIZE + mac.macLength) {
      throw InvalidMessageException("Message shorter than crypto overhead!")
    }

    ByteArrayInputStream(data).use { inputStream ->
      verifyMac(inputStream, data.size.toLong(), mac, null)
    }

    val encryptedStream = ByteArrayInputStream(data)
    val encryptedStreamExcludingMac = LimitedInputStream(encryptedStream, data.size.toLong() - mac.macLength)
    val cipher = createCipher(encryptedStreamExcludingMac, keyMaterial.aesKey)

    return BetterCipherInputStream(encryptedStreamExcludingMac, cipher)
  }

  /**
   * When you archive an attachment, you give the server an encrypted attachment, and the server wraps it in *another* layer of encryption.
   * This will return a stream that unwraps the server's layer of encryption, giving you a stream that contains a "normally-encrypted" attachment.
   *
   * Because we're validating the encryptedDigest/plaintextHash of the inner layer, there's no additional out-of-band validation of this outer layer.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  private fun createForArchivedMediaOuterLayer(archivedMediaKeyMaterial: MediaKeyMaterial, file: File, originalCipherTextLength: Long): LimitedInputStream {
    val mac = initMac(archivedMediaKeyMaterial.macKey)

    if (file.length() <= BLOCK_SIZE + mac.macLength) {
      throw InvalidMessageException("Message shorter than crypto overhead!")
    }

    FileInputStream(file).use { macVerificationStream ->
      verifyMac(macVerificationStream, file.length(), mac, null)
    }

    val encryptedStream = FileInputStream(file)
    val encryptedStreamExcludingMac = LimitedInputStream(encryptedStream, file.length() - mac.macLength)
    val cipher = createCipher(encryptedStreamExcludingMac, archivedMediaKeyMaterial.aesKey)
    val inputStream: InputStream = BetterCipherInputStream(encryptedStreamExcludingMac, cipher)

    return LimitedInputStream(inputStream, originalCipherTextLength)
  }

  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  private fun create(
    streamSupplier: StreamSupplier,
    streamLength: Long,
    plaintextLength: Long,
    combinedKeyMaterial: ByteArray,
    digest: ByteArray?,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int,
    ignoreDigest: Boolean
  ): LimitedInputStream {
    val keyMaterial = CombinedKeyMaterial.from(combinedKeyMaterial)
    val mac = initMac(keyMaterial.macKey)

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
      if (digest == null) {
        throw InvalidMessageException("Missing digest for incremental mac validation!")
      }

      wrappedStream = IncrementalMacInputStream(
        IncrementalMacAdditionalValidationsInputStream(
          wrapped = streamSupplier.openStream(),
          fileLength = streamLength,
          mac = mac,
          theirDigest = digest
        ),
        keyMaterial.macKey,
        ChunkSizeChoice.everyNthByte(incrementalMacChunkSize),
        incrementalDigest
      )
    }

    val encryptedStreamExcludingMac = LimitedInputStream(wrappedStream, streamLength - mac.macLength)
    val cipher = createCipher(encryptedStreamExcludingMac, keyMaterial.aesKey)
    val decryptingStream: InputStream = BetterCipherInputStream(encryptedStreamExcludingMac, cipher)

    return LimitedInputStream(decryptingStream, plaintextLength)
  }

  private fun createCipher(inputStream: InputStream, aesKey: ByteArray): Cipher {
    val iv = inputStream.readNBytesOrThrow(BLOCK_SIZE)

    return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
      init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
    }
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

  private class CombinedKeyMaterial(val aesKey: ByteArray, val macKey: ByteArray) {
    companion object {
      fun from(combinedKeyMaterial: ByteArray): CombinedKeyMaterial {
        val parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE)
        return CombinedKeyMaterial(parts[0], parts[1])
      }
    }
  }

  fun interface StreamSupplier {
    @Nonnull
    @Throws(IOException::class)
    fun openStream(): InputStream
  }
}
