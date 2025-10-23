/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.crypto

import org.signal.core.util.Base64
import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.stream.LimitedInputStream
import org.signal.core.util.stream.TrimmingInputStream
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
    integrityCheck: IntegrityCheck,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int
  ): InputStream {
    return create(
      streamSupplier = { FileInputStream(file) },
      streamLength = file.length(),
      plaintextLength = plaintextLength,
      combinedKeyMaterial = combinedKeyMaterial,
      integrityCheck = integrityCheck,
      incrementalDigest = incrementalDigest,
      incrementalMacChunkSize = incrementalMacChunkSize
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
    integrityCheck: IntegrityCheck,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int
  ): InputStream {
    return create(
      streamSupplier = streamSupplier,
      streamLength = streamLength,
      plaintextLength = plaintextLength,
      combinedKeyMaterial = combinedKeyMaterial,
      integrityCheck = integrityCheck,
      incrementalDigest = incrementalDigest,
      incrementalMacChunkSize = incrementalMacChunkSize
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
    plaintextHash: ByteArray,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int
  ): InputStream {
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
      integrityCheck = IntegrityCheck(plaintextHash = plaintextHash, encryptedDigest = null),
      incrementalDigest = incrementalDigest,
      incrementalMacChunkSize = incrementalMacChunkSize
    )
  }

  /**
   * When you archive an attachment thumbnail, you give the server an encrypted attachment, and the server wraps it in *another* layer of encryption.
   *
   * This creates a stream decrypt both the inner and outer layers of an archived attachment at the same time by basically double-decrypting it.
   *
   * Archive thumbnails are also special in that we:
   * - don't know how long they are (meaning you'll get them back with padding at the end, image viewers are ok with this)
   * - don't care about external integrity checks (we still validate the MACs)
   *
   * So there's some code duplication here just to avoid mucking up the reusable functions with special cases.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  fun createForArchivedThumbnail(
    archivedMediaKeyMaterial: MediaKeyMaterial,
    file: File,
    innerCombinedKeyMaterial: ByteArray
  ): InputStream {
    val outerMac = initMac(archivedMediaKeyMaterial.macKey)

    if (file.length() <= BLOCK_SIZE + outerMac.macLength) {
      throw InvalidMessageException("Message shorter than crypto overhead! Expected at least ${BLOCK_SIZE + outerMac.macLength} bytes, got ${file.length()}")
    }

    FileInputStream(file).use { macVerificationStream ->
      verifyMacAndMaybeEncryptedDigest(macVerificationStream, file.length(), outerMac, null)
    }

    val outerEncryptedStreamExcludingMac = LimitedInputStream(FileInputStream(file), maxBytes = file.length() - outerMac.macLength)
    val outerCipher = createCipher(outerEncryptedStreamExcludingMac, archivedMediaKeyMaterial.aesKey)
    val innerEncryptedStream = BetterCipherInputStream(outerEncryptedStreamExcludingMac, outerCipher)

    val innerKeyMaterial = CombinedKeyMaterial.from(innerCombinedKeyMaterial)
    val innerMac = initMac(innerKeyMaterial.macKey)

    val innerEncryptedStreamWithMac = MacValidatingInputStream(innerEncryptedStream, innerMac)
    val innerEncryptedStreamExcludingMac = TrimmingInputStream(innerEncryptedStreamWithMac, trimSize = innerMac.macLength, drain = true)
    val innerCipher = createCipher(innerEncryptedStreamExcludingMac, innerKeyMaterial.aesKey)

    return BetterCipherInputStream(innerEncryptedStreamExcludingMac, innerCipher)
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
      verifyMacAndMaybeEncryptedDigest(inputStream, data.size.toLong(), mac, null)
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
      throw InvalidMessageException("Message shorter than crypto overhead! Expected at least ${BLOCK_SIZE + mac.macLength} bytes, got ${file.length()}")
    }

    FileInputStream(file).use { macVerificationStream ->
      verifyMacAndMaybeEncryptedDigest(macVerificationStream, file.length(), mac, null)
    }

    val encryptedStream = FileInputStream(file)
    val encryptedStreamExcludingMac = LimitedInputStream(encryptedStream, file.length() - mac.macLength)
    val cipher = createCipher(encryptedStreamExcludingMac, archivedMediaKeyMaterial.aesKey)
    val inputStream: InputStream = BetterCipherInputStream(encryptedStreamExcludingMac, cipher)

    return LimitedInputStream(inputStream, originalCipherTextLength)
  }

  /**
   * @param integrityCheck If null, no integrity check is performed! This is a private method, so it's assumed that care has been taken to ensure that this is
   *   the correct course of action. Public methods should properly enforce when integrity checks are required.
   */
  @JvmStatic
  @Throws(InvalidMessageException::class, IOException::class)
  private fun create(
    streamSupplier: StreamSupplier,
    streamLength: Long,
    plaintextLength: Long,
    combinedKeyMaterial: ByteArray,
    integrityCheck: IntegrityCheck?,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int
  ): InputStream {
    val keyMaterial = CombinedKeyMaterial.from(combinedKeyMaterial)
    val mac = initMac(keyMaterial.macKey)

    if (streamLength <= BLOCK_SIZE + mac.macLength) {
      throw InvalidMessageException("Message shorter than crypto overhead! length: $streamLength")
    }

    val wrappedStream: InputStream
    val hasIncrementalMac = incrementalDigest != null && incrementalDigest.isNotEmpty() && incrementalMacChunkSize > 0

    if (hasIncrementalMac) {
      if (integrityCheck == null) {
        throw InvalidMessageException("Missing integrityCheck for incremental mac validation!")
      }

      val digestValidatingStream = if (integrityCheck.encryptedDigest != null) {
        DigestValidatingInputStream(streamSupplier.openStream(), sha256Digest(), integrityCheck.encryptedDigest)
      } else {
        streamSupplier.openStream()
      }

      wrappedStream = IncrementalMacInputStream(
        IncrementalMacAdditionalValidationsInputStream(
          wrapped = digestValidatingStream,
          fileLength = streamLength,
          mac = mac
        ),
        keyMaterial.macKey,
        ChunkSizeChoice.everyNthByte(incrementalMacChunkSize),
        incrementalDigest
      )
    } else {
      streamSupplier.openStream().use { macVerificationStream ->
        verifyMacAndMaybeEncryptedDigest(macVerificationStream, streamLength, mac, integrityCheck?.encryptedDigest)
      }
      wrappedStream = streamSupplier.openStream()
    }

    val encryptedStreamExcludingMac = LimitedInputStream(wrappedStream, streamLength - mac.macLength)
    val cipher = createCipher(encryptedStreamExcludingMac, keyMaterial.aesKey)
    val decryptingStream: InputStream = BetterCipherInputStream(encryptedStreamExcludingMac, cipher)
    val paddinglessDecryptingStream = LimitedInputStream(decryptingStream, plaintextLength)

    return if (integrityCheck?.plaintextHash != null) {
      if (integrityCheck.plaintextHash.size != MessageDigest.getInstance("SHA-256").digestLength) {
        throw InvalidMessageException("Invalid plaintext hash size: ${integrityCheck.plaintextHash.size}")
      }

      DigestValidatingInputStream(paddinglessDecryptingStream, sha256Digest(), integrityCheck.plaintextHash)
    } else {
      paddinglessDecryptingStream
    }
  }

  private fun createCipher(inputStream: InputStream, aesKey: ByteArray): Cipher {
    val iv = inputStream.readNBytesOrThrow(BLOCK_SIZE)

    return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
      init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
    }
  }

  private fun sha256Digest(): MessageDigest {
    try {
      return MessageDigest.getInstance("SHA-256")
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
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
  private fun verifyMacAndMaybeEncryptedDigest(@Nonnull inputStream: InputStream, length: Long, @Nonnull mac: Mac, theirDigest: ByteArray?) {
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
      @Throws(InvalidMessageException::class)
      fun from(combinedKeyMaterial: ByteArray): CombinedKeyMaterial {
        if (combinedKeyMaterial.size != CIPHER_KEY_SIZE + MAC_KEY_SIZE) {
          throw InvalidMessageException("Invalid combined key material size: ${combinedKeyMaterial.size}")
        }
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

  class IntegrityCheck(
    val encryptedDigest: ByteArray?,
    val plaintextHash: ByteArray?
  ) {
    init {
      if (encryptedDigest == null && plaintextHash == null) {
        throw IllegalArgumentException("At least one of encryptedDigest or plaintextHash must be provided")
      }
    }

    companion object {
      @JvmStatic
      fun forEncryptedDigest(encryptedDigest: ByteArray): IntegrityCheck {
        return IntegrityCheck(encryptedDigest, null)
      }

      @JvmStatic
      fun forPlaintextHash(plaintextHash: ByteArray): IntegrityCheck {
        return IntegrityCheck(null, plaintextHash)
      }

      @JvmStatic
      fun forEncryptedDigestAndPlaintextHash(encryptedDigest: ByteArray?, plaintextHash: String?): IntegrityCheck {
        val plaintextHashBytes = plaintextHash?.let { Base64.decode(it) }
        return IntegrityCheck(encryptedDigest, plaintextHashBytes)
      }
    }
  }
}
